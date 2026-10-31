package com.slop.projectminecraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private Config() {}

    // ---------------- COMMON (gameplay, server side) ----------------
    private static final ModConfigSpec.Builder C = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue COOLDOWN = C
            .comment("Multiplier for the cooldowns of the E.G.O weapons.")
            .defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);
    public static final ModConfigSpec COMMON_SPEC = C.build();

    // ---------------- CLIENT (visuals) ----------------
    private static final ModConfigSpec.Builder K = new ModConfigSpec.Builder();

    static { K.push("visuals"); }
    public static final ModConfigSpec.BooleanValue SCREEN_SHADERS = K
            .comment("Full-screen post-processing for the effects. Turn off if a shader pack (Iris/Oculus) conflicts.")
            .define("screenShaders", true);
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE = K
            .comment("Camera shake strength multiplier (0 = off).")
            .defineInRange("cameraShake", 1.0, 0.0, 3.0);
    public static final ModConfigSpec.BooleanValue HIT_STOP = K
            .comment("Brief freeze-frame when big hits land.")
            .define("hitStop", true);
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES = K
            .comment("Anime-style two-tone impact frames on huge hits.")
            .define("impactFrames", true);
    public static final ModConfigSpec.BooleanValue CINEMATICS = K
            .comment("Furioso and the E.G.O play cinematic camera sequences. Off = you keep control of your camera.")
            .define("cinematics", true);
    static { K.pop(); }
    public static final ModConfigSpec CLIENT_SPEC = K.build();
}
