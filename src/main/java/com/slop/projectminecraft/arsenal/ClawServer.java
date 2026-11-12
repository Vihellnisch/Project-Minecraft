package com.slop.projectminecraft.arsenal;

import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModDamageTypes;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.registry.ModSounds;
import com.slop.projectminecraft.warp.ClawWarp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.core.Holder;

/**
 * The Handmade Claw: the Claw's serums, weaker, in your own hand. Picked on the wheel (B), injected with right-click.
 * <ul>
 * <li>K: a green aura, hits heal you (life steal), and a slow regeneration.</li>
 * <li>R: a red aura, more attack, defence and speed, and hits set things on fire.</li>
 * <li>W: a short blink that cuts whatever it passes through; the claw then glows blue, and the next hit is a big slash.</li>
 * <li>Tri-serum: all three at once, stronger. While it lasts, right-click a target for Serum W's dimension slash
 * (ClawWarp, you travel with it); that ends the boost.</li>
 * </ul>
 */
public final class ClawServer {
    private ClawServer() {}

    public static final int K = 0, R = 1, W = 2, TRI = 3, SERUMS = 4;
    /** Cooldowns and durations, in ticks. */
    public static final int[] COOLDOWN = {600, 600, 60, 2400};
    static final int BUFF = 200, TRI_BUFF = 400;
    static final double BLINK = 8, WARP_REACH = 10;
    /** ArsenalFxPayload.CLAW sub-kinds for the Handmade Claw. */
    public static final int FX_HAND_STATE = 40, FX_HAND_INJECT = 41, FX_HAND_BLINK = 42, FX_HAND_SLASH = 43;

    static final class State {
        int selected;
        long kUntil, rUntil, triUntil;
        final long[] ready = new long[SERUMS];
        boolean charged;
        int lastSent = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean holding(LivingEntity e) {
        return e.getMainHandItem().is(ModItems.HANDMADE_CLAW.get());
    }

    public static void select(ServerPlayer p, int serum) {
        if (serum < 0 || serum >= SERUMS) return;
        state(p).selected = serum;
        sync(p, true);
    }

    /** /projectminecraft cooldowns: every serum ready again. */
    public static void resetCooldowns(ServerPlayer p) {
        java.util.Arrays.fill(state(p).ready, 0);
        sync(p, true);
    }

    // =================================================================== injecting

    /** Right-click with the claw: the selected serum, or the dimension slash while the cocktail runs. */
    public static void use(ServerPlayer p) {
        State s = state(p);
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (ClawWarp.busy(p)) return;
        int sel = s.selected;
        if (now < s.triUntil) {
            LivingEntity t = target(p);
            if (t != null) {
                dimensionSlash(p, t, s);
                return;
            }
            // the cocktail already holds K and R (stronger): only W's blink still does anything
            if (sel != W) return;
        }
        if (now < s.ready[sel]) return;
        s.ready[sel] = now + COOLDOWN[sel];
        fx(p, FX_HAND_INJECT, sel);
        sound(p, ModSounds.CLAW_INJECT.get(), 1.0f, sel == R ? 0.9f : sel == W ? 1.3f : sel == K ? 1.0f : 0.7f);
        switch (sel) {
            case K -> {
                s.kUntil = now + BUFF;
                effect(p, MobEffects.REGENERATION, BUFF, 0);
            }
            case R -> {
                s.rUntil = now + BUFF;
                effect(p, MobEffects.DAMAGE_BOOST, BUFF, 0);
                effect(p, MobEffects.DAMAGE_RESISTANCE, BUFF, 0);
                effect(p, MobEffects.MOVEMENT_SPEED, BUFF, 0);
            }
            case W -> blink(p, s);
            case TRI -> {
                s.triUntil = s.kUntil = s.rUntil = now + TRI_BUFF;
                effect(p, MobEffects.REGENERATION, TRI_BUFF, 1);
                effect(p, MobEffects.DAMAGE_BOOST, TRI_BUFF, 1);
                effect(p, MobEffects.DAMAGE_RESISTANCE, TRI_BUFF, 1);
                effect(p, MobEffects.MOVEMENT_SPEED, TRI_BUFF, 1);
                s.charged = true;
            }
            default -> {}
        }
        sync(p, true);
    }

    /** W: a short blink along your look, cutting whatever is in the way; the next hit is a big slash. */
    static void blink(ServerPlayer p, State s) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position(), look = WeaponMath.flat(p.getLookAngle());
        Vec3 end = from.add(look.scale(BLINK));
        var hit = level.clip(new ClipContext(from.add(0, 0.5, 0), end.add(0, 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(0, 0.5, 0).subtract(look.scale(0.6));
        for (int i = 0; i < 8 && !level.noCollision(p, p.getBoundingBox().move(to.subtract(from))); i++) to = to.subtract(look.scale(0.5));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.2, 1.5, 1.2), e -> e != p && com.slop.projectminecraft.apocalypse.Destruction.canHit(p, e))) {
            if (WeaponMath.segDist(e.getBoundingBox().getCenter(), from.add(0, 1, 0), to.add(0, 1, 0)) > 1.4 + e.getBbWidth() / 2) continue;
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(p), 8);
        }
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        s.charged = true;
        fx(p, FX_HAND_BLINK, from, to);
        sound(p, ModSounds.CLAW_BLINK.get(), 1.2f, 1.1f);
    }

    /** During the cocktail: the dimension slash on what you're looking at. It ends the boost. */
    static void dimensionSlash(ServerPlayer p, LivingEntity t, State s) {
        s.triUntil = s.kUntil = s.rUntil = 0;
        for (Holder<MobEffect> e : new Holder[]{MobEffects.REGENERATION, MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.MOVEMENT_SPEED}) p.removeEffect(e);
        sound(p, ModSounds.CLAW_INJECT.get(), 1.5f, 1.4f);
        if (!ClawWarp.start(t, p, t instanceof ServerPlayer ? null : p)) {
            // something that can't be moved (a boss): the slash without the trip
            t.invulnerableTime = 0;
            t.hurt(com.slop.projectminecraft.registry.ModDamageTypes.source(p.level(), ModDamageTypes.CLAW_WARP, p, p), Math.min(40, t.getMaxHealth() * 0.2f));
            fx(p, FX_HAND_SLASH, t.getBoundingBox().getCenter(), Vec3.ZERO);
        }
        sync(p, true);
    }

    @Nullable
    static LivingEntity target(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(WARP_REACH)), new AABB(eye, eye.add(look.scale(WARP_REACH))).inflate(1.5),
                e -> e instanceof LivingEntity le && le.isAlive() && com.slop.projectminecraft.apocalypse.Destruction.canHit(p, le), WARP_REACH * WARP_REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    // =================================================================== the buffs at work

    /** Your hits with the claw: life steal (K), fire (R), the charged big slash (after W). */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer p) || !holding(p) || event.getSource().is(ModDamageTypes.CLAW_WARP)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        long now = p.serverLevel().getGameTime();
        if (s.charged) {
            s.charged = false;
            event.setAmount(event.getAmount() + (now < s.triUntil ? 18 : 12));
            fx(p, FX_HAND_SLASH, event.getEntity().getBoundingBox().getCenter(), p.getLookAngle());
            sound(p, ModSounds.CLAW_SLASH.get(), 1.4f, 0.8f);
            sync(p, true);
        }
        if (now < s.rUntil) event.getEntity().igniteForSeconds(now < s.triUntil ? 6 : 4);
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer p) || !holding(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || p.serverLevel().getGameTime() >= s.kUntil) return;
        p.heal(event.getNewDamage() * (p.serverLevel().getGameTime() < s.triUntil ? 0.45f : 0.3f));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 10 == 0 && (holding(p) || STATES.containsKey(p.getUUID()))) sync(p, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    // =================================================================== to the client

    /** [FX_HAND_STATE, player, selected, aura bits (1 K, 2 R, 4 W charged, 8 tri), ticks left on each cooldown...]. */
    static void sync(ServerPlayer p, boolean force) {
        State s = state(p);
        long now = p.serverLevel().getGameTime();
        int aura = (now < s.kUntil ? 1 : 0) | (now < s.rUntil ? 2 : 0) | (s.charged ? 4 : 0) | (now < s.triUntil ? 8 : 0);
        int key = s.selected | aura << 4;
        if (!force && key == s.lastSent && p.tickCount % 40 != 0) return;
        s.lastSent = key;
        int[] ints = {FX_HAND_STATE, p.getId(), s.selected, aura, 0, 0, 0, 0};
        for (int i = 0; i < SERUMS; i++) ints[4 + i] = (int) Math.max(0, s.ready[i] - now);
        Network.sendTracking(p, ArsenalFxPayload.of(ArsenalFxPayload.CLAW, ints));
    }

    static void fx(ServerPlayer p, int kind, int serum) {
        Network.sendNear(p.serverLevel(), p.position(), 64, ArsenalFxPayload.of(ArsenalFxPayload.CLAW, p.position(), Vec3.ZERO, kind, p.getId(), serum));
    }

    static void fx(ServerPlayer p, int kind, Vec3 a, Vec3 b) {
        Network.sendNear(p.serverLevel(), p.position(), 64, ArsenalFxPayload.of(ArsenalFxPayload.CLAW, a, b, kind, p.getId()));
    }

    static void effect(ServerPlayer p, Holder<MobEffect> e, int ticks, int amp) {
        p.addEffect(new MobEffectInstance(e, ticks, amp, false, false, true));
    }

    static void sound(Entity e, SoundEvent s, float volume, float pitch) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundSource.PLAYERS, volume, pitch);
    }
}
