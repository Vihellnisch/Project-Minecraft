package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.boss.Roland;
import com.slop.projectminecraft.boss.RolandKin;
import com.slop.projectminecraft.boss.RolandMoves;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * The Black Silence's reception, client side: the black-out and white-out between phases, the Furioso and Scream
 * cards, the bonds' tethers and the waltz's streaks, the spikes, the shockwaves, and the smoke and mist that hang
 * off Roland and those with him.
 */
public final class RolandClient {
    private RolandClient() {}

    private static final RandomSource R = RandomSource.create();
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.06f), 1.8f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.97f, 0.97f, 1f), 1.4f);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(0.8f, 0.82f, 0.9f), 1.1f);

    /** The screen fade: colour (0 black, 1 white), how long, and how far in. */
    private static int fadeColor, fadeLife, fadeAge = 999;

    public static void handle(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 a = p.a(), b = p.b();
        double dist = mc.player == null ? 99 : mc.player.position().distanceTo(a);
        switch (p.i(0)) {
            case RolandMoves.FX_FADE -> {
                fadeColor = p.i(1);
                fadeLife = Math.max(10, p.i(2));
                fadeAge = 0;
            }
            case RolandMoves.FX_TITLE -> {
                boolean furioso = p.i(1) == RolandMoves.TITLE_FURIOSO;
                BossClient.bossBanner(Component.translatable("projectminecraft.boss.roland.card." + (furioso ? "furioso" : "scream")),
                        Component.translatable("projectminecraft.boss.roland.card." + (furioso ? "furioso" : "scream") + ".sub"), furioso ? 0xC8A040 : 0xE0E0E0);
                if (dist < 48) ImpactFrame.trigger(furioso ? ImpactFrame.WHITE : ImpactFrame.RED, 120);
            }
            case RolandMoves.FX_STREAK -> {
                int c = p.i(1);
                float w = p.i(2) / 100f;
                boolean dark = ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255) < 120;
                ArcFx.addBeam(a, b, 7, w, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, dark);
            }
            case RolandMoves.FX_SHOCK -> {
                float rad = p.i(1);
                int c = p.i(2);
                Shockwaves.add(a, rad, 500 + rad * 30, Math.min(2.2f, 0.6f + rad * 0.06f));
                RingFx.add(a, UP, 0.5f, rad, 12, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 0.85f);
                CameraShake.addAt(dist, Math.min(1.2f, 0.2f + rad * 0.04f), rad * 2 + 10);
            }
            case RolandMoves.FX_SPIKE -> {
                if (p.i(1) == 0) {
                    // a warning ring where the tendrils will come up
                    RingFx.add(a.add(0, 0.05, 0), UP, 1.5f, 1.7f, 26, 0.75f, 0.08f, 0.08f, 0.85f);
                } else {
                    for (int i = 0; i < 5; i++) {
                        Vec3 base = a.add(R.nextGaussian() * 0.5, 0, R.nextGaussian() * 0.5);
                        Vec3 tip = base.add(R.nextGaussian() * 0.4, 2.2 + R.nextDouble() * 1.4, R.nextGaussian() * 0.4);
                        ArcFx.addBeam(base, tip, 10, 0.16f, 0.3f, 0.04f, 0.04f, true);
                    }
                    RingFx.flash(a.add(0, 0.3, 0), 1.4f, 5, 0.7f, 0.1f, 0.1f, 0.9f);
                    CameraShake.addAt(dist, 0.25f, 20);
                }
            }
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (fadeAge < fadeLife) fadeAge++;
        if (mc.level == null || mc.player == null) return;
        // smoke off Roland once he's distorted, mist off Angelica, a pale haze off the images
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(mc.player) > 64 * 64) continue;
            if (e instanceof Roland r && r.isAlive()) {
                if (r.phase() == Roland.DISTORTED) {
                    for (int i = 0; i < 3; i++) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, r.getX() + R.nextGaussian() * 1.2, r.getY() + 2.4 + R.nextDouble(), r.getZ() + R.nextGaussian() * 1.2, 0, 0.04, 0);
                } else if (r.phase() >= Roland.BOND) {
                    mc.level.addParticle(BLACK, r.getX() + R.nextGaussian() * 0.3, r.getY() + 0.4 + R.nextDouble() * 1.6, r.getZ() + R.nextGaussian() * 0.3, 0, 0.02, 0);
                    if (r.weapon() >= 0) {
                        // black mist streaming off the weapon in his hand
                        float yaw = r.yBodyRot * Mth.DEG_TO_RAD;
                        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
                        Vec3 hand = r.position().add(right.scale(0.37)).add(fwd.scale(0.35)).add(0, 0.85, 0);
                        for (int i = 0; i < 3; i++) {
                            Vec3 at = hand.add(fwd.scale(R.nextDouble() * 1.1)).add(0, R.nextDouble() * 0.4, 0);
                            mc.level.addParticle(BLACK, at.x, at.y, at.z, -fwd.x * 0.03 + R.nextGaussian() * 0.01, 0.01, -fwd.z * 0.03 + R.nextGaussian() * 0.01);
                        }
                    }
                    if (R.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.SMOKE, r.getX() + R.nextGaussian() * 0.25, r.getY() + 1.7, r.getZ() + R.nextGaussian() * 0.25, 0, 0.03, 0);
                } else if (r.tickCount % 4 == 0) {
                    mc.level.addParticle(ParticleTypes.SMOKE, r.getX() + R.nextGaussian() * 0.2, r.getY() + 0.9, r.getZ() + R.nextGaussian() * 0.2, 0, 0.01, 0);
                }
            } else if (e instanceof RolandKin k && k.isAlive()) {
                if (k.kind() == RolandKin.ANGELICA) {
                    mc.level.addParticle(WHITE, k.getX() + R.nextGaussian() * 0.3, k.getY() + 0.2 + R.nextDouble() * 1.8, k.getZ() + R.nextGaussian() * 0.3, 0, 0.015, 0);
                    if (R.nextInt(4) == 0) mc.level.addParticle(ParticleTypes.END_ROD, k.getX() + R.nextGaussian() * 0.3, k.getY() + 1.5, k.getZ() + R.nextGaussian() * 0.3, 0, 0.01, 0);
                } else if (k.kind() == RolandKin.IMAGE && R.nextInt(2) == 0) {
                    mc.level.addParticle(PALE, k.getX() + R.nextGaussian() * 0.3, k.getY() + R.nextDouble() * 1.8, k.getZ() + R.nextGaussian() * 0.3, 0, 0.01, 0);
                } else if (k.kind() == RolandKin.WEAPON && !k.nailed() && R.nextInt(2) == 0) {
                    mc.level.addParticle(BLACK, k.getX() + R.nextGaussian() * 0.2, k.getY() + 0.4 + R.nextDouble() * 1.2, k.getZ() + R.nextGaussian() * 0.2, 0, 0.01, 0);
                }
            }
        }
    }

    /** The black-out into the factory and the white-out back to the Library. */
    public static void render(GuiGraphics g, DeltaTracker tracker) {
        if (fadeAge >= fadeLife) return;
        float t = (fadeAge + tracker.getGameTimeDeltaPartialTick(true)) / fadeLife;
        // in fast, hold, out slowly
        float a = t < 0.25f ? t / 0.25f : t < 0.55f ? 1 : 1 - (t - 0.55f) / 0.45f;
        int alpha = (int) (Mth.clamp(a, 0, 1) * 255);
        if (alpha <= 0) return;
        int rgb = fadeColor == 1 ? 0xF6F6F8 : 0x000000;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | rgb);
    }
}
