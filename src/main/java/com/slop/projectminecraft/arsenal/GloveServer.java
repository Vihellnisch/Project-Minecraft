package com.slop.projectminecraft.arsenal;

import static com.slop.projectminecraft.arsenal.FuriosoScript.*;

import com.slop.projectminecraft.apocalypse.ApocalypseServer;
import com.slop.projectminecraft.apocalypse.Destruction;
import com.slop.projectminecraft.item.ArsenalItems;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.Network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The worn Black Silence: an empty-handed attack uses the workshop weapon picked on the wheel, and once all twelve have
 * struck the same enemy, Furioso can be unleashed on it. No attack touches the terrain.
 */
public final class GloveServer {
    private GloveServer() {}

    /** Ticks between attacks, per weapon. */
    public static final int[] COOLDOWN = {6, 16, 24, 20, 5, 8, 18, 18, 36, 10, 26, 22};
    public static final int ALL = (1 << WEAPONS) - 1;
    static final int FORGET = 600, DRAW_EVERY = 3;
    static final double FURIOSO_RANGE = 16;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static final class State {
        int sel = PISTOLS, mask, target = -1, hits;
        long last;
        boolean random, right;
        final long[] ready = new long[WEAPONS];
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    // =================================================================== mastery

    /** On a player once they've won against the Claw (kept through death): the gloves answer them fully. */
    static final String MASTERED = "projectminecraft:gloves_mastered";

    /**
     * Whether `p` has mastered the gloves. Until then they only half answer: stuck on Random mode, no picking a weapon,
     * no Furioso. Reading the Black Silence's Book and beating the Claw that comes for it is how they learn.
     */
    public static boolean mastered(Player p) {
        CompoundTag root = p.getPersistentData();
        return root.getCompound(Player.PERSISTED_NBT_TAG).getBoolean(MASTERED);
    }

    /** They've won: full access from now on. */
    public static void master(ServerPlayer p) {
        if (mastered(p)) return;
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        root.getCompound(Player.PERSISTED_NBT_TAG).putBoolean(MASTERED, true);
        p.displayClientMessage(Component.translatable("message.projectminecraft.gloves.mastered").withStyle(ChatFormatting.GOLD), false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.slop.projectminecraft.registry.ModSounds.FURIOSO_STING.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        State s = state(p);
        s.random = false;
        sync(p, s, false);
    }

    // =================================================================== the wheel

    public static void select(ServerPlayer p, int w) {
        if (!Gloves.worn(p)) return;
        State s = state(p);
        if (!mastered(p)) {
            // they won't be steered yet: Random mode, and no Furioso
            p.displayClientMessage(Component.translatable("message.projectminecraft.gloves.locked").withStyle(ChatFormatting.DARK_RED), true);
            s.random = true;
            sync(p, s, false);
            return;
        }
        if (w == -2) {
            furioso(p, s);
            return;
        }
        if (w == -1) s.random = !s.random;
        else if (w >= 0 && w < WEAPONS) s.sel = w;
        else return;
        sync(p, s, false);
    }

    static void furioso(ServerPlayer p, State s) {
        if (s.mask != ALL || running(p) || !mastered(p)) return;
        Entity e = p.serverLevel().getEntity(s.target);
        if (!(e instanceof LivingEntity v) || !v.isAlive() || v.distanceTo(p) > FURIOSO_RANGE) {
            p.displayClientMessage(Component.translatable("item.projectminecraft.black_silence.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        ApocalypseServer.start(new ArsenalEvents.Furioso(p.serverLevel(), p, v));
        forget(s);
        sync(p, s, false);
    }

    static boolean running(ServerPlayer p) {
        for (ApocalypseServer.Event e : ApocalypseServer.events()) if (e instanceof ArsenalEvents.Furioso && e.ownerId.equals(p.getUUID())) return true;
        return false;
    }

    // =================================================================== the attacks

    public static void attack(ServerPlayer p) {
        if (!Gloves.armed(p) || p.isSpectator() || running(p)) return;
        State s = state(p);
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        int w = s.sel;
        if (now < s.ready[w]) return;
        s.ready[w] = now + COOLDOWN[w];
        s.right = !pair(w) || !s.right;

        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), flat = WeaponMath.flat(look), side = flat.cross(UP).normalize();
        Vec3 from = eye.add(look.scale(0.5)).add(side.scale(s.right ? 0.35 : -0.35)).add(0, -0.3, 0);
        Vec3 to = eye.add(look.scale(3));
        Map<LivingEntity, Float> hits = new LinkedHashMap<>();
        switch (w) {
            case PISTOLS -> {
                WeaponMath.Hit h = WeaponMath.trace(level, p, eye, look, 32, 0.25);
                to = h.end();
                if (h.victim() != null && Destruction.canHit(p, h.victim())) hits.put(h.victim(), h.head() ? 9f : 6f);
            }
            case SHOTGUN -> {
                // eight pellets in a cone, summed per victim
                to = eye.add(look.scale(10));
                for (int i = 0; i < 8; i++) {
                    Vec3 d = look.add(p.getRandom().nextGaussian() * 0.09, p.getRandom().nextGaussian() * 0.09, p.getRandom().nextGaussian() * 0.09).normalize();
                    WeaponMath.Hit h = WeaponMath.trace(level, p, eye, d, 10, 0.2);
                    if (h.victim() != null && Destruction.canHit(p, h.victim())) hits.merge(h.victim(), 3f, Float::sum);
                }
            }
            case LANCE -> {
                lunge(p, flat, 0.9);
                to = eye.add(look.scale(6));
                for (LivingEntity e : line(p, eye, to, 0.6)) hits.put(e, 9f);
            }
            case KATANA -> {
                // iai: through them in one step
                Vec3 start = p.position(), stop = ArsenalEvents.groundPath(level, p, flat, 4);
                from = start.add(0, 1, 0);
                to = stop.add(0, 1, 0);
                for (LivingEntity e : line(p, from, to, 0.9)) hits.put(e, 10f);
                p.connection.teleport(stop.x, stop.y, stop.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
            }
            case DURANDAL -> {
                to = eye.add(look.scale(4));
                for (LivingEntity e : line(p, eye, to, 1.0)) hits.put(e, 14f);
            }
            case HAMMER -> {
                to = p.position().add(flat.scale(2));
                for (LivingEntity e : around(p, to, 2.5)) hits.put(e, 12f);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 1, false, false));
            }
            case AXE -> {
                for (LivingEntity e : arc(p, flat, 3.5, 60)) hits.put(e, 10f);
            }
            case GREATSWORD -> {
                to = p.position().add(flat.scale(2.5));
                for (LivingEntity e : arc(p, flat, 4.5, 45)) hits.put(e, 18f);
            }
            case SHORTSWORDS -> {
                single(p, 3.5, 10f, hits);
                // a little sidestep with every pair of cuts
                p.setDeltaMovement(p.getDeltaMovement().add(side.scale(s.right ? 0.5 : -0.5)));
                p.hurtMarked = true;
            }
            case GAUNTLETS -> single(p, 3.5, 5f, hits);
            case KNIFE -> single(p, 3.5, 6f, hits);
            case MACE -> single(p, 3.5, 9f, hits);
            default -> {}
        }

        List<LivingEntity> landed = new ArrayList<>();
        for (Map.Entry<LivingEntity, Float> en : hits.entrySet()) {
            LivingEntity e = en.getKey();
            e.invulnerableTime = 0;
            if (!e.hurt(p.damageSources().playerAttack(p), en.getValue())) continue;
            landed.add(e);
            Vec3 push = WeaponMath.flat(e.position().subtract(p.position()));
            switch (w) {
                case LANCE -> knock(e, push.scale(0.9).add(0, 0.15, 0));
                case SHOTGUN -> knock(e, push.scale(1.4).add(0, 0.25, 0));
                case HAMMER -> knock(e, push.scale(0.4).add(0, 0.3, 0));
                case MACE -> knock(e, push.scale(0.2).add(0, 1.1, 0));
                case GREATSWORD -> knock(e, push.scale(0.6).add(0, -0.4, 0));
                case KNIFE -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), p);
                default -> {}
            }
        }

        int[] ints = new int[3 + landed.size()];
        ints[0] = p.getId();
        ints[1] = w;
        ints[2] = s.right ? 1 : 0;
        for (int i = 0; i < landed.size(); i++) ints[3 + i] = landed.get(i).getId();
        Network.sendNear(level, eye, 96, ArsenalFxPayload.of(ArsenalFxPayload.GLOVE_ATTACK, from, to, ints));
        if (!landed.isEmpty()) track(p, s, w, landed);
    }

    static void knock(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    static void lunge(ServerPlayer p, Vec3 flat, double speed) {
        p.setDeltaMovement(p.getDeltaMovement().add(flat.scale(speed)).add(0, 0.1, 0));
        p.hurtMarked = true;
    }

    static void single(ServerPlayer p, double range, float damage, Map<LivingEntity, Float> hits) {
        LivingEntity e = ArsenalItems.aimTarget(p, range);
        if (e != null) hits.put(e, damage);
    }

    /** Everything within `radius` of the segment a-b. */
    static List<LivingEntity> line(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(radius + 1.5), e -> Destruction.canHit(p, e))) {
            if (WeaponMath.segDist(e.getBoundingBox().getCenter(), a, b) <= radius + e.getBbWidth() * 0.5) out.add(e);
        }
        return out;
    }

    static List<LivingEntity> around(ServerPlayer p, Vec3 c, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 2, radius),
                e -> Destruction.canHit(p, e) && e.position().subtract(c).horizontalDistance() <= radius + e.getBbWidth() * 0.5);
    }

    /** Everything in front within `range`, inside `half` degrees either side of where we face. */
    static List<LivingEntity> arc(ServerPlayer p, Vec3 flat, double range, double half) {
        double cos = Math.cos(Math.toRadians(half));
        Vec3 c = p.position();
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range, 1.5, range), e -> {
            if (!Destruction.canHit(p, e)) return false;
            Vec3 d = e.position().subtract(c);
            double h = d.horizontalDistance();
            if (h > range + e.getBbWidth() * 0.5) return false;
            return h < 0.8 || WeaponMath.flat(d).dot(flat) >= cos;
        });
    }

    // =================================================================== Furioso progress

    static void track(ServerPlayer p, State s, int w, List<LivingEntity> landed) {
        LivingEntity t = null;
        for (LivingEntity e : landed) if (e.getId() == s.target) t = e;
        if (t == null) {
            for (LivingEntity e : landed) {
                if (ArsenalEvents.validTarget(e)) {
                    t = e;
                    break;
                }
            }
            if (t == null) return;
            forget(s);
            s.target = t.getId();
        }
        s.mask |= 1 << w;
        s.last = p.level().getGameTime();
        boolean drawn = false;
        // not mastered: always Random mode
        if (!mastered(p)) s.random = true;
        if (s.random && ++s.hits % DRAW_EVERY == 0) {
            s.sel = draw(p, s);
            drawn = true;
        }
        sync(p, s, drawn);
    }

    /** Random mode: a weapon not yet used on this enemy (any other one once all are used). */
    static int draw(ServerPlayer p, State s) {
        List<Integer> pool = new ArrayList<>();
        for (int w = 0; w < WEAPONS; w++) if (w != s.sel && (s.mask & (1 << w)) == 0) pool.add(w);
        if (pool.isEmpty()) for (int w = 0; w < WEAPONS; w++) if (w != s.sel) pool.add(w);
        return pool.get(p.getRandom().nextInt(pool.size()));
    }

    static void forget(State s) {
        s.mask = 0;
        s.target = -1;
        s.hits = 0;
    }

    static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.target < 0) return;
        Entity e = p.serverLevel().getEntity(s.target);
        if (p.level().getGameTime() - s.last > FORGET || !(e instanceof LivingEntity le) || !le.isAlive()) {
            forget(s);
            sync(p, s, false);
        }
    }

    /** [player, weapon, mask, target, random, drawn, mastered]. */
    static void sync(ServerPlayer p, State s, boolean drawn) {
        boolean m = mastered(p);
        if (!m) s.random = true;
        Network.sendTracking(p, ArsenalFxPayload.of(ArsenalFxPayload.GLOVES, p.getId(), s.sel, s.mask, s.target, s.random ? 1 : 0, drawn ? 1 : 0, m ? 1 : 0));
    }

    static void clear(UUID id) {
        STATES.remove(id);
    }

    static void clearAll() {
        STATES.clear();
    }
}
