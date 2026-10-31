package com.slop.projectminecraft.apocalypse;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Holds mobs and projectiles perfectly still for a while (no AI, no gravity, no motion), e.g. while a cinematic has
 * the caster's camera somewhere in orbit. Everything is restored exactly as it was when the hold expires.
 */
public final class Stasis {
    private Stasis() {}

    private static final String TAG = "projectminecraft_stasis";

    private static final class Held {
        final Entity entity;
        final Vec3 pos, motion;
        final boolean hadNoGravity, tookAi;
        long until;

        Held(Entity entity, long until, boolean tookAi) {
            this.entity = entity;
            this.pos = entity.position();
            this.motion = entity.getDeltaMovement();
            this.hadNoGravity = entity.isNoGravity();
            this.tookAi = tookAi;
            this.until = until;
        }
    }

    private static final Int2ObjectOpenHashMap<Held> HELD = new Int2ObjectOpenHashMap<>();

    public static void hold(ServerLevel level, Vec3 c, double radius, int ticks, @Nullable Entity spare) {
        long until = level.getGameTime() + ticks;
        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(radius), e -> e instanceof LivingEntity || e instanceof Projectile)) {
            if (e == spare || e instanceof Player || !e.isAlive() || e.distanceToSqr(c) > radius * radius) continue;
            Held h = HELD.get(e.getId());
            if (h != null) {
                h.until = Math.max(h.until, until);
                continue;
            }
            boolean took = false;
            if (e instanceof Mob mob && !mob.isNoAi()) {
                mob.setNoAi(true);
                mob.getPersistentData().putBoolean(TAG, true);
                took = true;
            }
            HELD.put(e.getId(), new Held(e, until, took));
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Hold a single entity (players excepted). */
    public static void holdOne(ServerLevel level, Entity e, int ticks) {
        if (e instanceof Player || !e.isAlive()) return;
        long until = level.getGameTime() + ticks;
        Held h = HELD.get(e.getId());
        if (h != null) {
            h.until = Math.max(h.until, until);
            return;
        }
        boolean took = false;
        if (e instanceof Mob mob && !mob.isNoAi()) {
            mob.setNoAi(true);
            mob.getPersistentData().putBoolean(TAG, true);
            took = true;
        }
        HELD.put(e.getId(), new Held(e, until, took));
        e.setNoGravity(true);
        e.setDeltaMovement(Vec3.ZERO);
    }

    /** Let one entity go early (it is about to be launched by something). */
    public static void release(Entity e) {
        Held h = HELD.remove(e.getId());
        if (h != null) restore(h, false);
    }

    public static void releaseAll(Vec3 c, double radius) {
        Iterator<Held> it = HELD.values().iterator();
        while (it.hasNext()) {
            Held h = it.next();
            if (h.entity.position().distanceToSqr(c) <= radius * radius) {
                it.remove();
                restore(h, true);
            }
        }
    }

    public static boolean held(Entity e) {
        return HELD.containsKey(e.getId());
    }

    private static void restore(Held h, boolean keepMotion) {
        Entity e = h.entity;
        if (h.tookAi && e instanceof Mob mob) {
            mob.setNoAi(false);
            mob.getPersistentData().remove(TAG);
        }
        if (!e.isAlive()) return;
        e.setNoGravity(h.hadNoGravity);
        if (keepMotion) e.setDeltaMovement(h.motion);
        e.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HELD.isEmpty()) return;
        Iterator<Held> it = HELD.values().iterator();
        while (it.hasNext()) {
            Held h = it.next();
            Entity e = h.entity;
            if (!e.isAlive() || e.level().getGameTime() >= h.until) {
                it.remove();
                restore(h, true);
                continue;
            }
            e.setDeltaMovement(Vec3.ZERO);
            if (!(e instanceof LivingEntity) && e.position().distanceToSqr(h.pos) > 1e-4) e.setPos(h.pos);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Held h : HELD.values()) restore(h, true);
        HELD.clear();
    }

    /** Crash safety: a mob saved mid-hold gets its AI back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !mob.getPersistentData().getBoolean(TAG)) return;
        if (HELD.containsKey(mob.getId())) return;
        mob.setNoAi(false);
        mob.setNoGravity(false);
        mob.getPersistentData().remove(TAG);
    }
}
