package com.slop.projectminecraft.client.juice;

import com.slop.projectminecraft.Config;
import net.minecraft.client.Minecraft;

/**
 * Fighting-game hit-stop: freezes every non-player entity on this client for a couple of ticks when you land a
 * big hit, so impacts feel heavy. Purely visual; the server keeps running and entities catch up afterwards.
 */
public final class HitStop {
    private HitStop() {}

    private static int ticksLeft;

    public static void trigger(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Config.HIT_STOP.get() || ticks <= 0) return;
        ticksLeft = Math.min(12, Math.max(ticksLeft, ticks));
        mc.level.tickRateManager().setFrozen(true);
    }

    public static boolean active() {
        return ticksLeft > 0;
    }

    public static void tick(Minecraft mc) {
        if (ticksLeft > 0 && --ticksLeft == 0) release(mc);
    }

    public static void clear() {
        ticksLeft = 0;
        release(Minecraft.getInstance());
    }

    private static void release(Minecraft mc) {
        if (mc.level != null) mc.level.tickRateManager().setFrozen(false);
    }
}
