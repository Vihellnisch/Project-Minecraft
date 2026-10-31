package com.slop.projectminecraft.client;

import com.slop.projectminecraft.Config;
import net.minecraft.util.Mth;

/** Trauma-based camera shake: shake = trauma^2, smooth multi-frequency noise, decays every tick. */
public final class CameraShake {
    private CameraShake() {}

    private static float trauma;
    private static float prevTrauma;

    public static void add(float amount) {
        trauma = Mth.clamp(trauma + amount, 0.0f, 1.0f);
    }

    /** Shake falling off with distance from the camera. */
    public static void addAt(double distance, float amount, double range) {
        if (distance < range) add(amount * (float) (1.0 - distance / range));
    }

    static void tick() {
        prevTrauma = trauma;
        trauma = Math.max(0.0f, trauma - 0.045f);
    }

    /** {yaw, pitch, roll} offsets in degrees. */
    static float[] offsets(float time, float partial) {
        float t = Mth.lerp(partial, prevTrauma, trauma);
        float s = t * t * Config.CAMERA_SHAKE.get().floatValue();
        if (s <= 0.0001f) return null;
        float yaw = (Mth.sin(time * 1.9f) + 0.5f * Mth.sin(time * 4.3f + 1.3f)) * 5.0f * s;
        float pitch = (Mth.sin(time * 2.3f + 2.1f) + 0.5f * Mth.sin(time * 5.1f)) * 5.0f * s;
        float roll = (Mth.sin(time * 1.7f + 4.0f) + 0.4f * Mth.sin(time * 3.7f + 0.7f)) * 6.0f * s;
        return new float[]{yaw, pitch, roll};
    }
}
