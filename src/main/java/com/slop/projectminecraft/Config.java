package com.slop.projectminecraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private Config() {}

    // ---------------- COMMON (gameplay, server side) ----------------
    private static final ModConfigSpec.Builder C = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue COOLDOWN = C
            .comment("Multiplier for the cooldowns of the E.G.O weapons.")
            .defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);
    static { C.push("gloves"); }
    public static final ModConfigSpec.DoubleValue GLOVE_DAMAGE = C
            .comment("Multiplier for the Black Silence gloves' damage: every workshop weapon and Furioso (raise it for modpacks with tougher mobs).")
            .defineInRange("damage", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue RANDOM_SWAP_HITS = C
            .comment("Random mode: how many hits before the gloves switch to another weapon (1 = a new weapon after every hit).")
            .defineInRange("randomSwapHits", 3, 1, 50);
    public static final ModConfigSpec.IntValue FURIOSO_SWITCH_GRACE = C
            .comment("Furioso needs all twelve weapons on one enemy. Hitting a different enemy only makes it the new Furioso target",
                    "(and starts the count again) once your current target hasn't been hit for this many seconds. 0 = at once.")
            .defineInRange("furiosoSwitchGrace", 0, 0, 120);
    public static final ModConfigSpec.BooleanValue FURIOSO_KEEP_PROGRESS = C
            .comment("If true, the weapons you've used count no matter which enemies they hit: switching enemies (or the one you were",
                    "hitting dying) never resets the count, and Furioso goes on whoever you hit last.")
            .define("furiosoKeepProgress", false);
    static { C.pop(); }
    static { C.push("claw"); }
    public static final ModConfigSpec.DoubleValue CLAW_HEALTH = C
            .comment("The Claw's health.")
            .defineInRange("health", 400.0, 50.0, 4000.0);
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WARP_DIMENSIONS = C
            .comment("Dimensions Serum W drags its target through (3 are picked, never the one you're in if there are enough).",
                    "Any mod's dimension id works; ids that don't exist on the server are skipped.")
            .defineListAllowEmpty("warpDimensions", java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    () -> "minecraft:overworld", o -> o instanceof String s && net.minecraft.resources.ResourceLocation.tryParse(s) != null);
    public static final ModConfigSpec.DoubleValue WARP_DAMAGE = C
            .comment("Serum W's dimension slash: total damage as a fraction of the victim's max health (armour ignored).")
            .defineInRange("warpDamage", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue WARP_STILL_DAMAGE = C
            .comment("Keep perfectly still: the share of Serum W's dimension slash a player takes if they don't move, jump or crouch",
                    "while they're dragged through (1 = no difference).")
            .defineInRange("warpStillDamage", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.IntValue TRI_USES = C
            .comment("How many times a fight the Tri-serum Cocktail can be used. The first comes below a quarter of his health",
                    "(the rebuilt Claw: below 35%), each one after it at under half the health of the one before.")
            .defineInRange("triUses", 2, 0, 10);
    public static final ModConfigSpec.DoubleValue TRI_HEAL = C
            .comment("Health the Claw gets back for each Tri-serum dash that lands.")
            .defineInRange("triHeal", 6.0, 0.0, 1000.0);
    static { C.pop(); }
    static { C.push("vergilius"); }
    public static final ModConfigSpec.DoubleValue VERGILIUS_HEALTH = C
            .comment("Vergilius's health.")
            .defineInRange("health", 600.0, 50.0, 6000.0);
    public static final ModConfigSpec.DoubleValue VERGILIUS_DAMAGE = C
            .comment("Multiplier for the damage Vergilius deals as a boss (his companion damage is a share of it).")
            .defineInRange("damage", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue REBUILT_CLAW_HEALTH = C
            .comment("The rebuilt Claw (the rematch beside Vergilius): his health as a multiple of the Claw's.")
            .defineInRange("rebuiltClawHealth", 1.6, 0.5, 10.0);
    public static final ModConfigSpec.DoubleValue REBUILT_AGGRO = C
            .comment("The rematch: how often the rebuilt Claw goes after Vergilius instead of you (0 to 1). If Vergilius falls, the fight is lost.")
            .defineInRange("rebuiltClawAggro", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ALLY_DAMAGE = C
            .comment("Multiplier for Vergilius's damage beside you in the rematch. On top of it, his health and damage there grow with",
                    "the rebuilt Claw's health when that's raised above the default (by this config or by other mods).")
            .defineInRange("allyDamage", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.IntValue TRAIL_DISTANCE = C
            .comment("How far from where the hitlist is first opened the trail starts (blocks).")
            .defineInRange("trailDistance", 600, 64, 5000);
    static { C.pop(); }
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
