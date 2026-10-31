package com.slop.projectminecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** A plain full-screen white flash (nukes, lasers, black holes collapsing), decaying over a few ticks. */
public final class ScreenFlash {
    private ScreenFlash() {}

    private static float amount, prev;

    public static void flash(float a) {
        amount = Math.max(amount, Mth.clamp(a, 0, 1));
    }

    static void tick() {
        prev = amount;
        amount = Math.max(0, amount * 0.82f - 0.02f);
    }

    static void clear() {
        amount = prev = 0;
    }

    static void render(GuiGraphics g, float partial) {
        float a = Mth.lerp(partial, prev, amount);
        if (a < 0.004f) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * a * 235) << 24) | 0xFFFFFF);
    }
}
