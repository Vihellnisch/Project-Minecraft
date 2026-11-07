package com.slop.projectminecraft.client.boss;

import static com.slop.projectminecraft.boss.ClawMoves.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.arsenal.ClawServer;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.ScreenFlash;
import com.slop.projectminecraft.client.arsenal.GloveClient;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalActionPayload;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModSounds;
import com.slop.projectminecraft.warp.ClawWarp;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Claw on screen: the rift he cuts his way in through, blinks, swipes and thrusts, Serum R's orange line and charge,
 * Serum W's marks, Serum K's orb, the No-Sell, the stagger, the Tri-serum, the break. Also the Handmade Claw: its state,
 * the B key (tap: next serum, hold: the wheel), auras and the HUD. Serum W's trip through the dimensions is ClawWarpClient.
 */
public final class ClawClient {
    private ClawClient() {}

    static final RandomSource R = RandomSource.create();
    static final float[][] SERUM_RGB = {{0.25f, 0.9f, 0.3f}, {1.0f, 0.6f, 0.15f}, {0.25f, 0.5f, 1.0f}, {0.85f, 0.85f, 0.85f}};
    static final String[] SERUM_NAMES = {"K", "R", "W", "Tri"};

    // ---- things in the world that last a while
    record Rift(Vec3 at, Vec3 normal, int born, boolean[] closing) {}
    record Line(Vec3 from, Vec3 to, int born, int life, float r, float g, float b) {}
    record Mark(int entity, int born, int life) {}
    record Orb(int claw, int born, int life, float r, float g, float b) {}

    static final List<Rift> RIFTS = new ArrayList<>();
    static final List<Line> LINES = new ArrayList<>();
    static final List<Mark> MARKS = new ArrayList<>();
    static final List<Orb> ORBS = new ArrayList<>();
    static int clock;

    // ---- the Handmade Claw
    public static int selected;
    static int aura;
    static final int[] COOLDOWN_LEFT = new int[ClawServer.SERUMS];
    static final Int2IntOpenHashMap AURAS = new Int2IntOpenHashMap();
    private static int wheelHeld;

    public static void handle(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        int kind = p.i(0);
        if (kind >= ClawWarp.FX_WARP_GRAB && kind <= ClawWarp.FX_WARP_END) {
            ClawWarpClient.handle(p);
            return;
        }
        if (kind >= ClawServer.FX_HAND_STATE) {
            hand(p, level);
            return;
        }
        Vec3 a = p.a(), b = p.b();
        double dist = mc.player == null ? 99 : mc.player.position().distanceTo(a);
        switch (kind) {
            case FX_RIFT_OPEN -> {
                if (p.i(2) == 1) {
                    // his entrance: its own scene
                    ClawEntrance.start(p.i(1), a, b, p.i(3), false);
                    sound(level, a, ModSounds.CLAW_RIFT.get(), 2.5f, 0.8f);
                    break;
                }
                RIFTS.add(new Rift(a, b.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : b, clock, new boolean[1]));
                sound(level, a, ModSounds.CLAW_RIFT.get(), 2.5f, 1.0f);
                CameraShake.addAt(dist, 0.35f, 24);
                for (int i = 0; i < 30; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x, a.y + R.nextGaussian() * 0.8, a.z, R.nextGaussian() * 0.3, R.nextGaussian() * 0.3, R.nextGaussian() * 0.3);
            }
            case FX_EXIT -> {
                // beaten mid-move: whatever he was holding up or marking goes with it
                ORBS.removeIf(o -> o.claw == p.i(1));
                MARKS.clear();
                LINES.clear();
                ClawEntrance.start(p.i(1), a, b, p.i(2), true);
            }
            case FX_RIFT_CLOSE -> {
                for (Rift r : RIFTS) if (r.at.distanceToSqr(a) < 4) r.closing[0] = true;
                RingFx.flash(a, 1.2f, 6, 0.4f, 0.6f, 1.0f, 1.0f);
            }
            case FX_BLINK -> {
                ArcFx.addBeam(a, b, 5, 0.18f, 0.35f, 0.6f, 1.0f, false);
                RingFx.flash(a, 0.8f, 4, 0.4f, 0.65f, 1.0f, 0.9f);
                RingFx.flash(b, 1.0f, 5, 0.4f, 0.65f, 1.0f, 1.0f);
                for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, R.nextGaussian() * 0.2, R.nextGaussian() * 0.2, R.nextGaussian() * 0.2);
            }
            case FX_SWIPE -> {
                Vec3 c = a.add(b.scale(1.4));
                ArcFx.addColoredSlash(c, b, p.i(2) == 0 ? 0.7f : -0.7f, 0.85f, 0.25f, 0.2f);
                CameraShake.addAt(dist, 0.2f, 16);
            }
            case FX_THRUST -> {
                ArcFx.addBeam(a, a.add(b.scale(5.5)), 4, 0.12f, 0.6f, 0.2f, 0.18f, false);
                if (p.i(2) == 1) RingFx.add(a.add(b.scale(5)), b, 0.2f, 1.4f, 7, 0.7f, 0.2f, 0.2f, 0.9f);
                CameraShake.addAt(dist, 0.3f, 20);
            }
            case FX_R_LINE -> LINES.add(new Line(a, b, clock, 22, 1.0f, 0.55f, 0.12f));
            case FX_R_CHARGE -> {
                ArcFx.addBeam(a, b, 10, 0.55f, 1.0f, 0.45f, 0.12f, false);
                ArcFx.addBeam(a, b, 6, 0.18f, 1.0f, 0.9f, 0.7f, false);
                for (int i = 0; i < 40; i++) {
                    Vec3 q = a.lerp(b, R.nextDouble());
                    level.addParticle(ParticleTypes.FLAME, q.x, q.y + R.nextGaussian() * 0.4, q.z, R.nextGaussian() * 0.05, 0.05, R.nextGaussian() * 0.05);
                }
                Shockwaves.add(b, 8, 500, 1.4f);
                CameraShake.addAt(Math.min(dist, mc.player == null ? 99 : mc.player.position().distanceTo(b)), 1.0f, 40);
                if (dist < 30) ImpactFrame.trigger(ImpactFrame.RED, 110);
            }
            case FX_MARK -> MARKS.add(new Mark(p.i(2), clock, p.i(3)));
            case FX_MARK_HIT -> {
                RingFx.flash(a, 1.2f, 5, 0.4f, 0.65f, 1.0f, 1.0f);
                ArcFx.addColoredSlash(a, Vec3.directionFromRotation(0, R.nextFloat() * 360), R.nextFloat() * 3, 0.5f, 0.7f, 1.0f);
                MARKS.removeIf(m -> m.entity == p.i(2));
                if (mc.player != null && p.i(2) == mc.player.getId()) ScreenFlash.flash(0.25f);
            }
            case FX_K_ORB -> ORBS.add(new Orb(p.i(1), clock, p.i(2), 0.3f, 1.0f, 0.35f));
            case FX_K_HEAL -> {
                ORBS.removeIf(o -> o.claw == p.i(1));
                RingFx.add(a.subtract(0, 0.9, 0), new Vec3(0, 1, 0), 0.3f, 3.5f, 14, 0.3f, 1.0f, 0.4f, 0.9f);
                for (int i = 0; i < 25; i++) level.addParticle(ParticleTypes.HAPPY_VILLAGER, a.x + R.nextGaussian() * 0.6, a.y + R.nextGaussian() * 0.8, a.z + R.nextGaussian() * 0.6, 0, 0.05, 0);
            }
            case FX_NEGATE -> {
                RingFx.flash(a, 3.0f, 8, 0.5f, 0.75f, 1.0f, 1.0f);
                RingFx.add(a, new Vec3(0, 0, 1).yRot(R.nextFloat() * 6), 0.4f, 4.5f, 12, 0.5f, 0.75f, 1.0f, 1.0f);
                if (dist < 24) ImpactFrame.trigger(ImpactFrame.CYAN, 140);
                if (mc.player != null && dist < 24) mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projectminecraft.claw.negated", p.i(2)), true);
            }
            case FX_GROGGY -> {
                ORBS.removeIf(o -> o.claw == p.i(1));
                for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.CRIT, a.x, a.y + 1, a.z, R.nextGaussian() * 0.4, 0.3, R.nextGaussian() * 0.4);
                RingFx.flash(a, 1.5f, 6, 1, 1, 1, 0.8f);
            }
            case FX_TRI -> {
                for (int s = 0; s < 3; s++) ORBS.add(new Orb(p.i(1), clock + s * 4, 40, SERUM_RGB[s][0], SERUM_RGB[s][1], SERUM_RGB[s][2]));
                ScreenFlash.flash(0.2f);
                CameraShake.addAt(dist, 0.5f, 30);
            }
            case FX_TRI_DASH -> {
                float[] c = SERUM_RGB[p.i(2) % 3];
                ArcFx.addBeam(a, b, 6, 0.3f, c[0], c[1], c[2], false);
                ArcFx.addColoredSlash(b, b.subtract(a), R.nextFloat() * 3, c[0], c[1], c[2]);
                CameraShake.addAt(dist, 0.4f, 24);
            }
            case FX_BREAK -> {
                BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
                for (int i = 0; i < 40; i++) level.addParticle(glass, a.x, a.y, a.z, R.nextGaussian() * 0.3, R.nextDouble() * 0.4, R.nextGaussian() * 0.3);
                for (int s = 0; s < 3; s++) for (int i = 0; i < 12; i++) {
                    level.addParticle(new DustParticleOptions(new Vector3f(SERUM_RGB[s]), 1.4f), a.x + R.nextGaussian() * 0.4, a.y + R.nextGaussian() * 0.4, a.z + R.nextGaussian() * 0.4, 0, 0, 0);
                }
                RingFx.flash(a, 2.0f, 8, 1, 1, 1, 1);
                if (dist < 24) ImpactFrame.trigger(ImpactFrame.WHITE, 150);
                sound(level, a, ModSounds.CLAW_SHATTER.get(), 2.0f, 1.0f);
            }
            case FX_INJECT -> {
                int bits = p.i(2);
                for (int s = 0; s < 3; s++) {
                    if ((bits & (1 << (s == 0 ? 0 : s == 1 ? 1 : 2))) == 0) continue;
                    for (int i = 0; i < 12; i++) level.addParticle(new DustParticleOptions(new Vector3f(SERUM_RGB[s]), 1.0f), a.x + R.nextGaussian() * 0.3, a.y + R.nextGaussian() * 0.3, a.z + R.nextGaussian() * 0.3, 0, 0.02, 0);
                }
            }
            default -> {}
        }
    }

    /** The Handmade Claw's packets: its state (for the HUD and auras), injections, blinks, the charged slash. */
    static void hand(ArsenalFxPayload p, ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        int kind = p.i(0), who = p.i(1);
        boolean mine = mc.player != null && who == mc.player.getId();
        switch (kind) {
            case ClawServer.FX_HAND_STATE -> {
                AURAS.put(who, p.i(3));
                if (mine) {
                    selected = p.i(2);
                    aura = p.i(3);
                    for (int i = 0; i < ClawServer.SERUMS; i++) COOLDOWN_LEFT[i] = p.i(4 + i);
                }
            }
            case ClawServer.FX_HAND_INJECT -> {
                Entity e = level.getEntity(who);
                if (e == null) break;
                int s = p.i(2);
                for (int i = 0; i < 16; i++) {
                    float[] c = SERUM_RGB[s == ClawServer.TRI ? i % 3 : s];
                    level.addParticle(new DustParticleOptions(new Vector3f(c), 1.0f), e.getX() + R.nextGaussian() * 0.4, e.getY() + 1 + R.nextGaussian() * 0.4, e.getZ() + R.nextGaussian() * 0.4, 0, 0.03, 0);
                }
                if (s == ClawServer.TRI && mine) ScreenFlash.flash(0.15f);
            }
            case ClawServer.FX_HAND_BLINK -> {
                ArcFx.addBeam(p.a().add(0, 1, 0), p.b().add(0, 1, 0), 5, 0.15f, 0.35f, 0.6f, 1.0f, false);
                RingFx.flash(p.b().add(0, 1, 0), 0.9f, 4, 0.4f, 0.65f, 1.0f, 0.9f);
            }
            case ClawServer.FX_HAND_SLASH -> {
                ArcFx.addColoredSlash(p.a(), p.b().lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : p.b(), R.nextFloat() * 3, 0.45f, 0.65f, 1.0f);
                RingFx.flash(p.a(), 1.3f, 5, 0.45f, 0.65f, 1.0f, 1.0f);
                if (mine) ImpactFrame.trigger(ImpactFrame.CYAN, 90);
            }
            default -> {}
        }
    }

    static void sound(ClientLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, s, SoundSource.HOSTILE, volume, pitch, false);
    }

    // =================================================================== ticking, the B key

    public static boolean holdingClaw(Player p) {
        return p != null && ClawServer.holding(p);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clock++;
        // the server only syncs on a change: count the cooldowns down here in between
        for (int i = 0; i < COOLDOWN_LEFT.length; i++) if (COOLDOWN_LEFT[i] > 0) COOLDOWN_LEFT[i]--;
        RIFTS.removeIf(r -> clock - r.born > 200 || r.closing[0] && clock - r.born > 10 && closeT(r) > 12);
        LINES.removeIf(l -> clock - l.born > l.life);
        MARKS.removeIf(m -> clock - m.born > m.life + 40);
        ORBS.removeIf(o -> clock - o.born > o.life);
        // auras on everyone holding a live claw
        for (Player p : mc.level.players()) {
            int bits = AURAS.getOrDefault(p.getId(), 0);
            if (bits == 0 || !holdingClaw(p) || p.tickCount % 2 != 0) continue;
            Vec3 hand = p.position().add(0, 1.0, 0).add(Vec3.directionFromRotation(0, p.getYRot() + 40).scale(0.45));
            for (int s = 0; s < 3; s++) {
                if ((bits & (1 << s)) == 0) continue;
                float[] c = SERUM_RGB[s];
                mc.level.addParticle(new DustParticleOptions(new Vector3f(c), 0.8f), hand.x + R.nextGaussian() * 0.15, hand.y + R.nextGaussian() * 0.2, hand.z + R.nextGaussian() * 0.15, 0, 0.02, 0);
            }
        }
        // the B key while the claw is in hand: tap = next serum, hold = the wheel
        boolean usable = mc.screen == null && mc.player != null && holdingClaw(mc.player);
        if (!usable) {
            wheelHeld = 0;
            return;
        }
        while (GloveClient.WHEEL_KEY.consumeClick()) {}
        if (GloveClient.WHEEL_KEY.isDown()) {
            if (++wheelHeld == 5) mc.setScreen(new ClawWheel());
        } else {
            if (wheelHeld > 0 && wheelHeld < 5) select((selected + 1) % ClawServer.SERUMS);
            wheelHeld = 0;
        }
    }

    public static void select(int serum) {
        selected = serum;
        PacketDistributor.sendToServer(new ArsenalActionPayload(ArsenalActionPayload.CLAW_SELECT, 0, 0, 0, new int[]{serum}, new float[0]));
    }

    static float closeT(Rift r) {
        return r.closing[0] ? clock - r.born - 32 : 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RIFTS.clear();
        LINES.clear();
        MARKS.clear();
        ORBS.clear();
        AURAS.clear();
        ClawWarpClient.clear();
    }

    // =================================================================== rendering

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (RIFTS.isEmpty() && LINES.isEmpty() && MARKS.isEmpty() && ORBS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = clock + partial;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = new Matrix4f();
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.GLOW);
        for (Rift r : RIFTS) rift(vc, m, r, now, cam);
        for (Line l : LINES) {
            float age = now - l.born, a = Mth.clamp(1 - age / l.life, 0, 1) * (0.6f + 0.4f * Mth.sin(age * 1.3f));
            FxRenderTypes.ribbon(vc, m, l.from.subtract(cam), l.to.subtract(cam), 0.7f, l.r, l.g, l.b, a, a * 0.6f);
            FxRenderTypes.ribbon(vc, m, l.from.subtract(cam), l.to.subtract(cam), 0.18f, 1, 0.95f, 0.8f, a, a);
        }
        for (Mark k : MARKS) {
            Entity e = mc.level.getEntity(k.entity);
            if (e == null) continue;
            float age = now - k.born, grow = Mth.clamp(age / 8, 0, 1), a = age > k.life ? Mth.clamp(1 - (age - k.life) / 40, 0, 1) : 1;
            Vec3 feet = e.getPosition(partial).subtract(cam).add(0, 0.05, 0), top = feet.add(0, e.getBbHeight() + 0.5, 0);
            float spin = age * 0.15f;
            FxRenderTypes.annulus(vc, m, feet, new Vec3(0, 1, 0), 0.9f * grow, 1.05f * grow, 32, 0.35f, 0.6f, 1.0f, 0.9f * a, 0.0f);
            FxRenderTypes.annulus(vc, m, top, new Vec3(Mth.sin(spin) * 0.2, 1, Mth.cos(spin) * 0.2), 0.35f * grow, 0.45f * grow, 24, 0.45f, 0.7f, 1.0f, a, 0.0f);
        }
        for (Orb o : ORBS) {
            Entity c = mc.level.getEntity(o.claw);
            if (c == null) continue;
            float age = now - o.born;
            Vec3 hand = c.getPosition(partial).add(0, 1.3, 0).add(Vec3.directionFromRotation(0, c.getYRot() - 50).scale(0.6)).subtract(cam);
            if (o.life <= 40) hand = hand.add(Mth.cos(age * 0.4f + o.r * 7) * 0.9, Mth.sin(age * 0.2f) * 0.5, Mth.sin(age * 0.4f + o.r * 7) * 0.9);
            float size = 0.25f + 0.08f * Mth.sin(age * 0.5f) + Math.min(0.2f, age * 0.004f);
            FxRenderTypes.sphere(vc, m, hand, size * 1.8f, 6, 10, o.r, o.g, o.b, 0.3f);
            FxRenderTypes.sphere(vc, m, hand, size, 6, 10, Math.min(1, o.r + 0.4f), Math.min(1, o.g + 0.4f), Math.min(1, o.b + 0.4f), 0.9f);
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    /** A thin vertical cut that bows open into a lens of blue light, then pinches shut. */
    static void rift(VertexConsumer vc, Matrix4f m, Rift r, float now, Vec3 cam) {
        float age = now - r.born;
        float open = Mth.clamp(age / 18f, 0, 1);
        float close = r.closing[0] ? Mth.clamp(1 - (age - 32) / 10f, 0, 1) : 1;
        float width = 0.05f + 0.85f * open * open * close, height = 1.8f * Math.min(1, age / 4f);
        int segs = 12;
        for (int i = 0; i < segs; i++) {
            double y0 = -height + 2 * height * i / segs, y1 = -height + 2 * height * (i + 1) / segs;
            float w0 = (float) Math.sin(Math.PI * i / segs) * width, w1 = (float) Math.sin(Math.PI * (i + 1) / segs) * width;
            Vec3 a = r.at.add(0, y0, 0).subtract(cam), b = r.at.add(0, y1, 0).subtract(cam);
            float flick = 0.8f + 0.2f * Mth.sin(now * 2.3f + i);
            FxRenderTypes.ribbon(vc, m, a, b, Math.max(w0, w1) * 2.4f + 0.1f, 0.25f, 0.45f, 1.0f, 0.35f * flick * close, 0.35f * flick * close);
            FxRenderTypes.ribbon(vc, m, a, b, Math.max(w0, w1) * 1.1f + 0.04f, 0.55f, 0.8f, 1.0f, 0.85f * close, 0.85f * close);
            FxRenderTypes.ribbon(vc, m, a, b, 0.05f, 1, 1, 1, close, close);
        }
    }

    // =================================================================== HUD

    /** Holding the Handmade Claw: the four serums under the crosshair, the selected one framed, cooldowns filling. */
    public static void renderHud(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !holdingClaw(mc.player) || com.slop.projectminecraft.client.CinematicCamera.locksInput()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int size = 16, gap = 4, x0 = w / 2 - (ClawServer.SERUMS * (size + gap) - gap) / 2, y = h - 64;
        for (int i = 0; i < ClawServer.SERUMS; i++) {
            int x = x0 + i * (size + gap);
            float[] c = SERUM_RGB[i];
            int col = 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            boolean live = i == ClawServer.TRI ? (aura & 8) != 0 : (aura & (1 << i)) != 0 && i != ClawServer.W;
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, i == selected ? 0xFFFFFFFF : 0xFF202020);
            g.fill(x, y, x + size, y + size, live ? col : (col & 0x00FFFFFF) | 0x90000000);
            int cd = COOLDOWN_LEFT[i];
            if (cd > 0) {
                int fillH = Math.round(size * Math.min(1, cd / (float) ClawServer.COOLDOWN[i]));
                g.fill(x, y, x + size, y + fillH, 0xB0000000);
            }
            String s = SERUM_NAMES[i];
            g.drawString(mc.font, s, x + size / 2 - mc.font.width(s) / 2, y + 4, 0xFFFFFFFF, true);
        }
        if ((aura & 4) != 0) g.drawString(mc.font, "W", x0 - 12, y + 4, 0xFF66A0FF, true);
    }
}
