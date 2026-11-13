package com.slop.projectminecraft.arsenal;

import com.slop.projectminecraft.network.ArsenalActionPayload;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server hub of the worn Black Silence and the E.G.O: client actions, ticking, damage hooks. */
public final class ArsenalServer {
    private ArsenalServer() {}

    public static void onAction(ServerPlayer p, ArsenalActionPayload a) {
        switch (a.action()) {
            case ArsenalActionPayload.GLOVE_SELECT -> {
                if (a.ints().length > 0) GloveServer.select(p, a.ints()[0]);
            }
            case ArsenalActionPayload.GLOVE_ATTACK -> GloveServer.attack(p);
            case ArsenalActionPayload.CLAW_SELECT -> {
                if (a.ints().length > 0) ClawServer.select(p, a.ints()[0]);
            }
            case ArsenalActionPayload.GLADIUS_SELECT -> {
                if (a.ints().length > 0) GladiusServer.select(p, a.ints()[0]);
            }
            case ArsenalActionPayload.EYE_SLOT -> EyeSlot.click(p);
            case ArsenalActionPayload.EYE_LOOK -> EyeSlot.look(p, a.ints());
            case ArsenalActionPayload.WARP_STRUGGLE -> com.slop.projectminecraft.warp.ClawWarp.struggle(p);
            case ArsenalActionPayload.VERG_CHOICE -> {
                if (a.ints().length > 1 && p.level().getEntity(a.ints()[0]) instanceof com.slop.projectminecraft.boss.Vergilius v && v.distanceTo(p) < 32)
                    v.choose(p, a.ints()[1] == 1);
            }
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p) GloveServer.tick(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!event.isCanceled()) ArsenalEvents.guard(event);
    }

    /** The wheel needs to know from the start whether the gloves answer this player fully. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) GloveServer.sync(p, GloveServer.state(p), false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ArsenalEvents.clear(id);
        GloveServer.clear(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ArsenalEvents.clearAll();
        GloveServer.clearAll();
    }
}
