package com.slop.projectminecraft.client.juice;

import com.slop.projectminecraft.Config;

/**
 * Anime impact frame: for a few frames the whole screen turns into a stark two-tone silhouette with speed lines
 * bursting from the hit point. Styles: 0 = black on white, 1 = black on red, 2 = gold, 3 = cyan, 4 = violet.
 */
public final class ImpactFrame {
    private ImpactFrame() {}

    public static final int WHITE = 0, RED = 1, GOLD = 2, CYAN = 3, VIOLET = 4;

    private static long start;
    private static long durationNanos;
    private static int style;
    private static float originX = 0.5f, originY = 0.5f;

    public static void trigger(int style, float durationMs) {
        trigger(style, durationMs, 0.5f, 0.5f);
    }

    public static void trigger(int style, float durationMs, float x, float y) {
        if (!Config.IMPACT_FRAMES.get()) return;
        ImpactFrame.style = style;
        start = System.nanoTime();
        durationNanos = (long) (durationMs * 1_000_000L);
        originX = x;
        originY = y;
    }

    /** 1 while the frame is held, then a very fast fade. */
    public static float strength() {
        if (durationNanos == 0) return 0;
        float t = (System.nanoTime() - start) / (float) durationNanos;
        if (t >= 1) return 0;
        return t < 0.55f ? 1.0f : (1.0f - t) / 0.45f;
    }

    public static int style() { return style; }
    public static float originX() { return originX; }
    public static float originY() { return originY; }
}
