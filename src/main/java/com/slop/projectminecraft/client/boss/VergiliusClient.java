package com.slop.projectminecraft.client.boss;

import static com.slop.projectminecraft.boss.VergiliusMoves.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.boss.Vergilius;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.client.ScreenFlash;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Vergilius on screen: his cuts and lunges (orange, or blood in E.G.O), the stance and the parry, the Path's line and the
 * split, the Red Gaze, the blood lake of Lavacrum Sanguinis with its spikes, the Bloodcleaver, the barrier, Funeral's
 * storm; the camera for meeting him and for the bus that takes him away; the choice; smoke off the gladius.
 */
public final class VergiliusClient {
    private VergiliusClient() {}

    static final RandomSource R = RandomSource.create();
    static final float[] HEAT = {1.0f, 0.42f, 0.12f}, BLOOD = {0.85f, 0.06f, 0.08f};

    record Lake(int verg, Vec3 at, float radius, int born, int[] state) {
        /** His lake stays where he manifested it; a player's goes with them. */
        Vec3 center(float partial) {
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(verg);
            return e instanceof Player ? e.getPosition(partial) : at;
        }
    }
    record Warn(Vec3 at, int born, int life, float radius) {}
    record Spike(Vec3 at, int born, float height) {}
    record Line(Vec3 from, Vec3 to, int born, int life) {}
    record Spear(Vec3 at, int born) {}

    static final List<Lake> LAKES = new ArrayList<>();
    static final List<Warn> WARNS = new ArrayList<>();
    static final List<Spike> SPIKES = new ArrayList<>();
    static final List<Line> LINES = new ArrayList<>();
    static final List<Spear> SPEARS = new ArrayList<>();
    static final List<Integer> BARRIERS = new ArrayList<>();
    static int clock, gazeUntil, funeralBorn = -999;

    static float[] col(int ego) {
        return ego == 1 ? BLOOD : HEAT;
    }

    public static void handle(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 a = p.a(), b = p.b();
        double dist = mc.player == null ? 99 : mc.player.position().distanceTo(a);
        int kind = p.i(0);
        if (kind == com.slop.projectminecraft.arsenal.GladiusServer.FX_HAND || kind == com.slop.projectminecraft.arsenal.EyeSlot.FX_EYES) {
            GladiusClient.handle(p);
            return;
        }
        switch (kind) {
            case FX_INTRO -> {
                if (mc.player != null && p.i(2) == mc.player.getId()) intro(p.i(1), a, b);
            }
            case FX_CHOICE -> mc.setScreen(new VergiliusChoiceScreen(p.i(1)));
            case FX_SLASH -> {
                float[] c = col(p.i(3));
                Vec3 at = a.add(b.scale(1.5));
                ArcFx.addColoredSlash(at, b, p.i(2) * (0.6f + p.i(4) * 0.25f), c[0], c[1], c[2]);
                for (int i = 0; i < 8; i++) level.addParticle(p.i(3) == 1 ? new DustParticleOptions(new Vector3f(BLOOD), 1.2f) : ParticleTypes.FLAME,
                        at.x + R.nextGaussian() * 0.6, at.y + R.nextGaussian() * 0.3, at.z + R.nextGaussian() * 0.6, b.x * 0.1, 0.02, b.z * 0.1);
                CameraShake.addAt(dist, 0.2f, 16);
            }
            case FX_THRUST -> {
                float[] c = col(p.i(2));
                ArcFx.addBeam(a, a.add(b.scale(3.6)), 4, 0.14f, c[0], c[1], c[2], false);
                RingFx.add(a.add(b.scale(3.2)), b, 0.2f, 1.2f, 6, c[0], c[1], c[2], 0.9f);
                CameraShake.addAt(dist, 0.3f, 18);
            }
            case FX_LUNGE -> {
                float[] c = col(p.i(2));
                ArcFx.addBeam(a, b, 6, 0.35f, c[0], c[1], c[2], false);
                ArcFx.addBeam(a, b, 4, 0.1f, 1, 0.9f, 0.7f, false);
                for (int i = 0; i < 14; i++) {
                    Vec3 q = a.lerp(b, R.nextDouble());
                    level.addParticle(ParticleTypes.SMOKE, q.x, q.y, q.z, 0, 0.03, 0);
                }
            }
            case FX_COUNTER -> {
                if (p.i(2) == 1) RingFx.add(a, new Vec3(0, 1, 0), 0.4f, 1.6f, 10, HEAT[0], HEAT[1], HEAT[2], 0.6f);
            }
            case FX_PARRY -> {
                RingFx.flash(a, 2.2f, 7, 1, 0.7f, 0.3f, 1);
                if (dist < 20) ImpactFrame.trigger(ImpactFrame.GOLD, 110);
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.LAVA, a.x, a.y, a.z, R.nextGaussian() * 0.2, R.nextDouble() * 0.3, R.nextGaussian() * 0.2);
            }
            case FX_PATH_LINE -> LINES.add(new Line(a, b, clock, p.i(2)));
            case FX_PATH_CUT -> {
                float[] c = col(p.i(2));
                ArcFx.addBeam(a, b, 10, 0.6f, c[0], c[1], c[2], false);
                ArcFx.addBeam(a, b, 7, 0.16f, 1, 0.95f, 0.8f, false);
                Vec3 d = b.subtract(a);
                for (int i = 0; i < 4; i++) ArcFx.addColoredSlash(a.add(d.scale((i + 0.5) / 4)), d, (i % 2 == 0 ? 1 : -1) * 0.9f, c[0], c[1], c[2]);
                Shockwaves.add(a.lerp(b, 0.5), 6, 400, 1.0f);
                CameraShake.addAt(dist, 0.8f, 30);
                if (dist < 26) ImpactFrame.trigger(ImpactFrame.RED, 110);
            }
            case FX_GAZE -> {
                Entity v = level.getEntity(p.i(1));
                if (v != null) for (int i = 0; i < 2; i++) ArcFx.addBeam(a, a.add(Vec3.directionFromRotation(0, v.getYRot()).scale(0.4)), 12, 0.05f, 1, 0.1f, 0.1f, false);
                if (mc.player != null && p.i(2) == mc.player.getId()) gazeUntil = clock + p.i(3) + 30;
            }
            case FX_GAZE_HIT -> {
                if (mc.player != null && p.i(2) == mc.player.getId()) {
                    ScreenFlash.flash(0.2f);
                    gazeUntil = clock + 80;
                }
                for (int i = 0; i < 10; i++) level.addParticle(new DustParticleOptions(new Vector3f(BLOOD), 1.0f), a.x + R.nextGaussian() * 0.4, a.y + R.nextGaussian() * 0.4, a.z + R.nextGaussian() * 0.4, 0, 0, 0);
            }
            case FX_MANIFEST -> {
                LAKES.removeIf(l -> l.verg == p.i(1));
                LAKES.add(new Lake(p.i(1), a, p.i(2) / 10f, clock, new int[1]));
                ScreenFlash.flash(0.3f);
                CameraShake.addAt(dist, 0.7f, 40);
                if (dist < 32) ImpactFrame.trigger(ImpactFrame.RED, 160);
                Shockwaves.add(a, 10, 700, 1.4f);
            }
            case FX_LAKE_OFF -> LAKES.removeIf(l -> l.verg == p.i(1));
            case FX_SPIKE_WARN -> WARNS.add(new Warn(a, clock, p.i(2), p.i(3) > 0 ? p.i(3) / 10f : 1.1f));
            case FX_SPIKE -> {
                SPIKES.add(new Spike(a, clock, 2.2f + R.nextFloat() * 0.8f));
                for (int i = 0; i < 10; i++) level.addParticle(new DustParticleOptions(new Vector3f(BLOOD), 1.5f), a.x + R.nextGaussian() * 0.5, a.y + 0.2, a.z + R.nextGaussian() * 0.5, 0, 0.1, 0);
                CameraShake.addAt(dist, 0.25f, 14);
            }
            case FX_CLEAVER_WIND -> {
                for (int i = 0; i < 20; i++) level.addParticle(new DustParticleOptions(new Vector3f(BLOOD), 1.6f), a.x + R.nextGaussian() * 0.8, a.y + R.nextGaussian() * 0.8, a.z + R.nextGaussian() * 0.8, 0, 0.05, 0);
            }
            case FX_CLEAVER -> {
                Vec3 at = a.add(b.scale(3.5));
                for (int i = 0; i < 3; i++) ArcFx.addColoredSlash(at.add(0, i * 0.2, 0), b, 1.45f + i * 0.08f, BLOOD[0], BLOOD[1], BLOOD[2]);
                Shockwaves.add(at, 9, 500, 1.6f);
                CameraShake.addAt(dist, 1.2f, 40);
                if (dist < 30) ImpactFrame.trigger(ImpactFrame.RED, 150);
                for (int i = 0; i < 40; i++) level.addParticle(new DustParticleOptions(new Vector3f(BLOOD), 2.0f), at.x + R.nextGaussian() * 2, at.y + R.nextGaussian() * 0.5, at.z + R.nextGaussian() * 2, 0, 0.1, 0);
            }
            case FX_DROWN_PULL -> {
                for (Lake l : LAKES) if (l.verg == p.i(1)) l.state[0] = 1;
                RingFx.add(a.add(0, 0.1, 0), new Vec3(0, 1, 0), 9f, 1f, p.i(2), BLOOD[0], BLOOD[1], BLOOD[2], 0.8f);
            }
            case FX_DROWN -> {
                float r = p.i(2) / 10f;
                for (Lake l : LAKES) if (l.verg == p.i(1)) l.state[0] = 0;
                for (int i = 0; i < 26; i++) {
                    double ang = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * r;
                    SPIKES.add(new Spike(a.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr), clock + R.nextInt(3), 2.5f + R.nextFloat() * 1.4f));
                }
                CameraShake.addAt(dist, 1.4f, 40);
                if (dist < 30) ImpactFrame.trigger(ImpactFrame.RED, 160);
            }
            case FX_BARRIER -> {
                if (!BARRIERS.contains(p.i(1))) BARRIERS.add(p.i(1));
            }
            case FX_BARRIER_BREAK -> {
                BARRIERS.remove((Integer) p.i(1));
                RingFx.flash(a, 2.5f, 8, BLOOD[0], BLOOD[1], BLOOD[2], 1);
                for (int i = 0; i < 30; i++) level.addParticle(new DustParticleOptions(new Vector3f(BLOOD), 1.4f), a.x, a.y, a.z, R.nextGaussian() * 0.4, R.nextDouble() * 0.3, R.nextGaussian() * 0.4);
            }
            case FX_FUNERAL -> {
                funeralBorn = clock;
                for (Lake l : LAKES) if (l.verg == p.i(1)) l.state[0] = 2;
                CameraShake.addAt(dist, 0.5f, 40);
            }
            case FX_FUNERAL_SPEAR -> SPEARS.add(new Spear(a, clock));
            case FX_FUNERAL_CUT -> {
                funeralBorn = -999;
                for (Lake l : LAKES) if (l.verg == p.i(1)) l.state[0] = 0;
                float r = p.i(2) / 10f;
                for (int i = 0; i < 4; i++) ArcFx.addColoredSlash(a.add(0, 1.2, 0), Vec3.directionFromRotation(0, i * 45f), 0.2f + i * 0.4f, BLOOD[0], BLOOD[1], BLOOD[2]);
                RingFx.add(a.add(0, 0.2, 0), new Vec3(0, 1, 0), 0.5f, r + 2, 10, 1, 0.2f, 0.2f, 1);
                Shockwaves.add(a, r + 4, 700, 2.0f);
                ScreenFlash.flash(0.5f);
                CameraShake.addAt(dist, 1.8f, 50);
                if (dist < 34) ImpactFrame.trigger(ImpactFrame.RED, 220);
            }
            case FX_BUS -> {
                if (mc.player != null && p.i(3) == mc.player.getId() && dist < 40) busShot(a, b);
            }
            case FX_DOWNED -> {
                for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.CRIT, a.x, a.y + 0.5, a.z, R.nextGaussian() * 0.3, 0.2, R.nextGaussian() * 0.3);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ the camera

    /** Meeting him: from over your shoulder toward him, slowly in to his face, the card, back to you. */
    static void intro(int verg, Vec3 at, Vec3 facing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        CinematicCamera.play(new CinematicCamera.Director() {
            @Override
            public int duration() {
                return 90;
            }

            @Override
            public void frame(float t, CinematicCamera.Shot s) {
                Entity v = mc.level == null ? null : mc.level.getEntity(verg);
                Vec3 head = (v == null ? at : v.position()).add(0, 1.6, 0);
                Vec3 d = head.subtract(eye).multiply(1, 0, 1).normalize(), side = new Vec3(-d.z, 0, d.x);
                Vec3 shoulder = eye.add(side.scale(0.7)).add(0, 0.1, 0).subtract(d.scale(0.6));
                Vec3 close = head.subtract(d.scale(2.4)).add(side.scale(0.5)).add(0, -0.1, 0);
                s.lookAt(CinematicCamera.lerp(shoulder, close, CinematicCamera.ease(t, 10, 60)), head);
                s.fov = 60;
                CinematicCamera.returnToPlayer(s, t, 72, 90);
            }
        });
    }

    /** The bus coming for him: low behind him, looking down the road it comes along; then from the side as he gets on. */
    static void busShot(Vec3 at, Vec3 lane) {
        Vec3 side = new Vec3(-lane.z, 0, lane.x);
        CinematicCamera.play(new CinematicCamera.Director() {
            @Override
            public int duration() {
                return 140;
            }

            @Override
            public void frame(float t, CinematicCamera.Shot s) {
                if (t < 64) {
                    // low, behind him, the road ahead: the lights coming, it pulls up
                    Vec3 from = at.add(side.scale(4.5)).add(lane.scale(3)).add(0, 0.5, 0);
                    s.lookAt(from, at.subtract(lane.scale(10)).add(0, 1.2, 0));
                    s.fov = 55;
                } else {
                    // from the other side of him as the door opens and he walks to it
                    Vec3 from = at.add(side.scale(6)).subtract(lane.scale(5)).add(0, 1.4, 0);
                    s.lookAt(from, at.subtract(side.scale(1.2)).add(0, 1.4, 0));
                    s.fov = 50;
                }
                CinematicCamera.returnToPlayer(s, t, 120, 140);
            }
        });
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        clock++;
        WARNS.removeIf(w -> clock - w.born > w.life + 2);
        SPIKES.removeIf(s -> clock - s.born > 16);
        LINES.removeIf(l -> clock - l.born > l.life + 6);
        SPEARS.removeIf(s -> clock - s.born > 10);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        // his lake goes with his E.G.O; a player's with theirs
        LAKES.removeIf(l -> {
            Entity e = mc.level.getEntity(l.verg);
            // (a grace period: the E.G.O flag arrives a little after the lake starts rising)
            return e == null || clock - l.born > 40 && (e instanceof Vergilius v && !v.ego() || e instanceof Player && !GladiusClient.EGO.contains(e.getId()));
        });
        BARRIERS.removeIf(id -> !(mc.level.getEntity(id) instanceof Vergilius v) || !v.barrier());
        // smoke off the white-hot blade, his and any player's
        if (clock % 3 == 0) {
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(40),
                    e -> e instanceof Vergilius v && !v.isInvisible() || e instanceof Player pl && pl.getMainHandItem().is(ModItems.WHITE_HOT_GLADIUS.get()))) {
                if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                Vec3 tip = bladeTip(e);
                boolean ego = e instanceof Vergilius v && v.ego();
                mc.level.addParticle(ego ? new DustParticleOptions(new Vector3f(BLOOD), 0.8f) : ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0, 0.02, 0);
                if (!ego && R.nextInt(4) == 0) mc.level.addParticle(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z, 0, 0.01, 0);
            }
        }
        for (Lake l : LAKES) {
            if (R.nextInt(3) == 0) {
                double ang = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * l.radius;
                Vec3 at = l.center(0);
                mc.level.addParticle(new DustParticleOptions(new Vector3f(BLOOD), 1.0f), at.x + Math.cos(ang) * rr, at.y + 0.1, at.z + Math.sin(ang) * rr, 0, 0.04, 0);
            }
        }
    }

    /** About where the blade is: low and forward of the right hand, partway down its length. */
    static Vec3 bladeTip(LivingEntity e) {
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return e.position().add(right.scale(0.38)).add(f.scale(0.45 + R.nextDouble() * 0.35)).add(0, 0.35 + R.nextDouble() * 0.25, 0);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LAKES.clear();
        WARNS.clear();
        SPIKES.clear();
        LINES.clear();
        SPEARS.clear();
        BARRIERS.clear();
        funeralBorn = -999;
        gazeUntil = 0;
    }

    // ------------------------------------------------------------------ the world

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (LAKES.isEmpty() && WARNS.isEmpty() && SPIKES.isEmpty() && LINES.isEmpty() && SPEARS.isEmpty() && BARRIERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = clock + partial;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = new Matrix4f();
        // the lake: a dark pool, a lit rim, ripples
        VertexConsumer shade = buffers.getBuffer(FxRenderTypes.SHADE);
        for (Lake l : LAKES) {
            float grow = Mth.clamp((now - l.born) / 24, 0, 1), r = l.radius * grow;
            Vec3 c = l.center(partial).subtract(cam).add(0, 0.04, 0);
            boolean black = l.state[0] == 2;
            FxRenderTypes.annulus(shade, m, c, new Vec3(0, 1, 0), 0, r, 40, black ? 0.08f : 0.42f, 0.0f, 0.02f, 0.75f, 0.6f);
        }
        buffers.endBatch(FxRenderTypes.SHADE);
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.GLOW);
        for (Lake l : LAKES) {
            float grow = Mth.clamp((now - l.born) / 24, 0, 1), r = l.radius * grow;
            Vec3 c = l.center(partial).subtract(cam).add(0, 0.06, 0);
            float pulse = 0.5f + 0.5f * Mth.sin(now * 0.15f);
            FxRenderTypes.annulus(vc, m, c, new Vec3(0, 1, 0), r - 0.35f, r, 40, 0.9f, 0.08f, 0.08f, 0.0f, 0.5f + 0.3f * pulse);
            float rip = (now * 0.08f) % 1;
            FxRenderTypes.annulus(vc, m, c, new Vec3(0, 1, 0), r * rip, r * rip + 0.15f, 40, 0.8f, 0.1f, 0.1f, 0.3f * (1 - rip), 0.3f * (1 - rip));
            if (l.state[0] == 1) FxRenderTypes.annulus(vc, m, c, new Vec3(0, 1, 0), 0, r, 40, 0.5f, 0.02f, 0.02f, 0.25f * pulse, 0.05f);
        }
        for (Warn w : WARNS) {
            float k = Mth.clamp((now - w.born) / Math.max(1, w.life), 0, 1);
            Vec3 c = w.at.subtract(cam).add(0, 0.08, 0);
            FxRenderTypes.annulus(vc, m, c, new Vec3(0, 1, 0), w.radius * k * 0.9f, w.radius * k, 28, 1, 0.15f, 0.12f, 0.9f, 0.9f);
            FxRenderTypes.annulus(vc, m, c, new Vec3(0, 1, 0), 0, w.radius, 28, 0.6f, 0.05f, 0.05f, 0.15f + 0.25f * k, 0.05f);
        }
        for (Spike s : SPIKES) {
            float age = now - s.born;
            if (age < 0) continue;
            float up = Mth.clamp(age / 2.5f, 0, 1) * Mth.clamp((16 - age) / 6, 0, 1);
            Vec3 base = s.at.subtract(cam), tip = base.add(0, s.height * up, 0);
            FxRenderTypes.tube(vc, m, base, tip, 0.32f * up, 0.0f, 6, BLOOD[0], BLOOD[1], BLOOD[2], 0.95f, 0.9f);
            FxRenderTypes.tube(vc, m, base, tip.add(0, -0.2, 0), 0.12f * up, 0.0f, 6, 1, 0.5f, 0.5f, 0.8f, 0.6f);
        }
        for (Line l : LINES) {
            float age = now - l.born, a = age < l.life ? 0.55f + 0.45f * Mth.sin(age * 1.2f) : Mth.clamp(1 - (age - l.life) / 6, 0, 1);
            FxRenderTypes.ribbon(vc, m, l.from.subtract(cam), l.to.subtract(cam), 0.6f, HEAT[0], HEAT[1], HEAT[2], a, a * 0.6f);
            FxRenderTypes.ribbon(vc, m, l.from.subtract(cam), l.to.subtract(cam), 0.14f, 1, 0.9f, 0.7f, a, a);
        }
        for (Spear s : SPEARS) {
            float age = now - s.born, fall = Mth.clamp(age / 4, 0, 1);
            Vec3 top = s.at.add(0.6, 7, 0.3), hit = s.at;
            Vec3 tip = top.lerp(hit, fall).subtract(cam), tail = top.lerp(hit, Math.max(0, fall - 0.35f)).subtract(cam);
            float a = age < 6 ? 1 : Mth.clamp(1 - (age - 6) / 4, 0, 1);
            FxRenderTypes.ribbon(vc, m, tail, tip, 0.22f, BLOOD[0], BLOOD[1], BLOOD[2], a * 0.4f, a);
            if (fall >= 1) FxRenderTypes.annulus(vc, m, hit.subtract(cam).add(0, 0.06, 0), new Vec3(0, 1, 0), 0, 0.9f, 16, 0.9f, 0.1f, 0.1f, a * 0.7f, 0);
        }
        for (int id : BARRIERS) {
            Entity e = mc.level.getEntity(id);
            if (e == null) continue;
            Vec3 c = e.getPosition(partial).add(0, 1.0, 0).subtract(cam);
            FxRenderTypes.sphere(vc, m, c, 1.35f + 0.04f * Mth.sin(now * 0.3f), 8, 16, BLOOD[0], BLOOD[1], BLOOD[2], 0.18f);
            FxRenderTypes.annulus(vc, m, c.add(0, -0.95, 0), new Vec3(0, 1, 0), 1.2f, 1.4f, 32, 1, 0.2f, 0.2f, 0.7f, 0.7f);
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    // ------------------------------------------------------------------ the screen

    /** The Red Gaze on you (a red vignette), and Funeral's dark. */
    public static void renderHud(GuiGraphics g, DeltaTracker tracker) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (clock < gazeUntil) {
            float a = Mth.clamp((gazeUntil - clock) / 20f, 0, 1) * 0.55f;
            int c = ((int) (a * 255) << 24) | 0x900000;
            int band = h / 5;
            g.fillGradient(0, 0, w, band, c, 0);
            g.fillGradient(0, h - band, w, h, 0, c);
        }
        if (clock - funeralBorn < 100) {
            float a = Mth.clamp((clock - funeralBorn) / 30f, 0, 1) * 0.5f;
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0x080000);
        }
    }

    /** Tours: is the lake up (and how many spikes are showing)? */
    public static String debug() {
        return "lakes " + LAKES.size() + " spikes " + SPIKES.size() + " warns " + WARNS.size() + " barriers " + BARRIERS.size();
    }
}
