package com.slop.projectminecraft.client;

import com.slop.projectminecraft.Config;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Takes over the camera for cutscenes. A Director describes the shot for any point in time; while one runs, the
 * player can't look or move, the HUD and hand are hidden, and letterbox bars / fades are drawn on top.
 * The camera position is applied in CameraMixin, input is blocked in MouseHandlerMixin and ClientFx.
 */
public final class CinematicCamera {
    private CinematicCamera() {}

    /** What the camera does at one moment of a cutscene. */
    public static final class Shot {
        public Vec3 pos = Vec3.ZERO;
        public float yaw, pitch, roll;
        /** Field of view in degrees, or <= 0 for the normal one. */
        public float fov;
        /** 0 = no bars, 1 = full cinemascope bars. */
        public float letterbox = 1;
        /** Full-screen fade colour (rgb) and amount. */
        public int fadeColor;
        public float fade;
        /** Hide the vanilla world entirely (the space scene draws over everything). */
        public boolean space;
        /** Radius (gui pixels) of a glowing white dot in the middle of the screen, drawn above the fade. */
        public float dot;
        /** Camera is (nearly) back in the player's eyes: stop drawing their own body. */
        public boolean firstPerson;

        public Shot lookAt(Vec3 from, Vec3 at) {
            pos = from;
            Vec3 d = at.subtract(from);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
            pitch = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
            return this;
        }
    }

    /** A cutscene. frame() is called every render frame with the time in ticks since it started (with partials). */
    public interface Director {
        int duration();

        void frame(float t, Shot shot);

        /** Soft cutscenes keep mouse aim and the HUD (over-the-shoulder charge cam). */
        default boolean allowLook() {
            return false;
        }
    }

    @Nullable private static Director director;
    private static int ticks;
    private static final Shot SHOT = new Shot();
    private static float lastT = -1;

    /** Start a cutscene (replaces any running one). Ignored if cinematics are turned off in the config. */
    public static void play(Director d) {
        if (!Config.CINEMATICS.get()) return;
        director = d;
        ticks = 0;
        lastT = -1;
    }

    public static void stop() {
        director = null;
    }

    public static boolean active() {
        return director != null;
    }

    /** True while a cutscene owns the controls (no look, no move, no HUD). */
    public static boolean locksInput() {
        return director != null && !director.allowLook();
    }

    static void tick() {
        if (director == null) return;
        if (++ticks > director.duration()) director = null;
    }

    /** Called from CameraMixin at the end of Camera.setup; null when no cutscene runs. */
    @Nullable
    public static Shot shot(float partial) {
        if (director == null) return null;
        float t = Math.min(director.duration(), ticks + partial);
        if (t != lastT) {
            SHOT.fov = 0;
            SHOT.roll = 0;
            SHOT.fade = 0;
            SHOT.space = false;
            SHOT.dot = 0;
            SHOT.firstPerson = false;
            SHOT.letterbox = 1;
            director.frame(t, SHOT);
            // cutscenes still get shaken by explosions
            Minecraft mc = Minecraft.getInstance();
            float[] o = CameraShake.offsets(ticks + partial, partial);
            if (o != null) {
                SHOT.yaw += o[0];
                SHOT.pitch += o[1];
                SHOT.roll += o[2];
            }
            lastT = t;
        }
        return SHOT;
    }

    public static boolean inSpace() {
        return director != null && SHOT.space;
    }

    public static float fov() {
        return director != null ? SHOT.fov : 0;
    }

    /** Letterbox and fades, drawn above everything else (and even with F1). */
    public static void renderOverlay(GuiGraphics g) {
        if (director == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = (int) (h * 0.12f * Mth.clamp(SHOT.letterbox, 0, 1));
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (SHOT.fade > 0.003f) {
            int a = (int) (255 * Mth.clamp(SHOT.fade, 0, 1));
            g.fill(0, 0, w, h, (a << 24) | (SHOT.fadeColor & 0xFFFFFF));
        }
        if (SHOT.dot > 0.2f) {
            int cx = w / 2, cy = h / 2;
            for (int ring = 4; ring >= 0; ring--) {
                float r = SHOT.dot * (1 + ring * 0.9f);
                int a = ring == 0 ? 255 : (int) (70 / ring);
                for (int y = (int) -r; y <= r; y++) {
                    int half = (int) Math.sqrt(Math.max(0, r * r - y * y));
                    g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, (a << 24) | 0xFFFFFF);
                }
            }
        }
    }

    // ------------------------------------------------------------------ path helpers

    /** 0..1 progress of t through [a, b], smoothstepped. */
    public static float ease(float t, float a, float b) {
        float k = Mth.clamp((t - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }

    public static float lin(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return a.add(b.subtract(a).scale(k));
    }

    /** A point on a horizontal circle around c. */
    public static Vec3 orbit(Vec3 c, double radius, double height, double angleDeg) {
        double a = Math.toRadians(angleDeg);
        return c.add(Math.cos(a) * radius, height, Math.sin(a) * radius);
    }

    /** Smoothly hand the camera back to the player's own eyes over [a, b]. */
    public static void returnToPlayer(Shot s, float t, float a, float b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || t < a) return;
        float k = ease(t, a, b);
        Vec3 eye = mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
        Vec3 from = s.pos;
        s.pos = lerp(from, eye, k);
        s.yaw = Mth.rotLerp(k, s.yaw, mc.player.getYRot());
        s.pitch = Mth.lerp(k, s.pitch, mc.player.getXRot());
        s.letterbox *= 1 - k;
        if (k > 0.75f) s.firstPerson = true;
    }
}
