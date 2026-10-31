package com.slop.projectminecraft.apocalypse;

import com.slop.projectminecraft.network.Network;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs the cinematic events (Furioso, the E.G.O) and keeps their casters safe while a cinematic has their camera. */
public final class ApocalypseServer {
    private ApocalypseServer() {}

    /** One running event. Ticked every server tick until done() (age counts from 0 at start). */
    public abstract static class Event {
        public final ServerLevel level;
        public final UUID ownerId;
        public int age;

        protected Event(ServerLevel level, ServerPlayer owner) {
            this.level = level;
            this.ownerId = owner.getUUID();
        }

        @Nullable
        public ServerPlayer owner() {
            return level.getServer().getPlayerList().getPlayer(ownerId);
        }

        protected abstract void tick();

        protected abstract boolean done();

        protected void sound(Vec3 at, SoundEvent s, float volume, float pitch) {
            level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
        }

        protected void fx(Vec3 at, double range, net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
            Network.sendNear(level, at, range, p);
        }
    }

    private static final List<Event> EVENTS = new ArrayList<>();
    private static final Map<UUID, Long> PROTECTED = new HashMap<>();

    public static void start(Event e) {
        EVENTS.add(e);
    }

    public static List<Event> events() {
        return EVENTS;
    }

    /** No damage and no fall damage for a while (cinematics, recoil). */
    public static void protect(ServerPlayer p, int ticks) {
        long until = p.serverLevel().getGameTime() + ticks;
        PROTECTED.merge(p.getUUID(), until, Math::max);
    }

    private static boolean isProtected(LivingEntity e) {
        Long until = PROTECTED.get(e.getUUID());
        return until != null && e.level().getGameTime() < until;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (EVENTS.isEmpty()) return;
        // events may start other events (beam clashes), so tick a snapshot
        for (Event e : new ArrayList<>(EVENTS)) {
            try {
                e.tick();
            } catch (Exception ex) {
                com.slop.projectminecraft.ProjectMinecraft.LOGGER.error("Project Minecraft: event {} failed", e.getClass().getSimpleName(), ex);
                e.age = Integer.MAX_VALUE - 1;
            }
            e.age++;
        }
        EVENTS.removeIf(Event::done);
        if (event.getServer().getTickCount() % 200 == 0) PROTECTED.entrySet().removeIf(en -> en.getValue() < event.getServer().overworld().getGameTime() - 200);
    }

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (isProtected(event.getEntity()) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (isProtected(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EVENTS.clear();
        PROTECTED.clear();
    }
}
