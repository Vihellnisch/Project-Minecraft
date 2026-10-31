package com.slop.projectminecraft.arsenal;

/**
 * Timelines of the E.G.O, in ticks. The server (damage, positions) and the client
 * (cinematics, visuals) both read these, so a single start packet keeps them in step.
 */
public final class ArsenalTiming {
    private ArsenalTiming() {}

    // ---- E.G.O
    public static final int EGO_CUTIN = 28;
    public static final int SOLEMN_COINS = 5, SOLEMN_FIRST = 8, SOLEMN_GAP = 7, SOLEMN_FUNERAL = 52, SOLEMN_TOTAL = 82;
    public static final int BULLET_CIRCLES = 4, BULLET_FIRE = 26, BULLET_TOTAL = 64;
    public static final int MATCH_DASH = 4, MATCH_STROKE0 = 8, MATCH_STROKE_GAP = 2, MATCH_IGNITE = 36, MATCH_TOTAL = 70;

    public static int solemnShot(int coin) {
        return EGO_CUTIN + SOLEMN_FIRST + coin * SOLEMN_GAP;
    }
}
