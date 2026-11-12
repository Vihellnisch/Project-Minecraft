package com.slop.projectminecraft.arsenal;

import static com.slop.projectminecraft.arsenal.ArsenalTiming.*;

import com.slop.projectminecraft.apocalypse.ApocalypseServer;
import com.slop.projectminecraft.apocalypse.Destruction;
import com.slop.projectminecraft.apocalypse.Stasis;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModDamageTypes;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The cinematic moves: Furioso and the E.G.O. */
public final class ArsenalEvents {
    private ArsenalEvents() {}

    static int id(ServerPlayer p) {
        return p == null ? -1 : p.getId();
    }

    static void hurt(LivingEntity e, DamageSource src, float amount) {
        e.invulnerableTime = 0;
        e.hurt(src, amount);
    }

    /** Where a dash of up to `len` along flat `dir` ends: runs along the ground, steps up one block or down a few, stops at walls. */
    public static Vec3 groundPath(ServerLevel level, ServerPlayer p, Vec3 dir, double len) {
        Vec3 start = p.position();
        Vec3 end = start;
        for (double s = 0.5; s <= len; s += 0.5) {
            Vec3 c = end.add(dir.scale(0.5));
            Vec3 found = null;
            for (double dy : new double[]{0, -1, -2, -3, 1, 2}) {
                Vec3 q = c.add(0, dy, 0);
                if (!level.noCollision(p, p.getBoundingBox().move(q.subtract(start)))) continue;
                if (!level.noCollision(p, p.getBoundingBox().move(q.subtract(start)).move(0, -0.6, 0))) {
                    found = q;
                    break;
                }
            }
            // over a gap: keep going level
            if (found == null && level.noCollision(p, p.getBoundingBox().move(c.subtract(start)))) found = c;
            if (found == null) break;
            end = found;
        }
        return end;
    }

    // =================================================================== the guard

    private static final Set<LivingEntity> GUARD = new HashSet<>();

    /** Victims mid-Furioso can't die before the finale (the whole combo always plays out). */
    static void guard(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (!GUARD.contains(e)) return;
        if (event.getAmount() >= e.getHealth()) event.setAmount(Math.max(0, e.getHealth() - 1));
    }

    // =================================================================== FURIOSO

    public static final class Furioso extends ApocalypseServer.Event {
        private final LivingEntity victim;
        private final FuriosoScript s;
        private double lastS = -1;

        public Furioso(ServerLevel level, ServerPlayer p, LivingEntity victim) {
            super(level, p);
            this.victim = victim;
            double gy = victim.getY();
            if (!victim.onGround()) {
                BlockPos.MutableBlockPos m = BlockPos.containing(victim.position()).mutable();
                for (int i = 0; i < 40 && level.getBlockState(m.below()).getCollisionShape(level, m.below()).isEmpty(); i++) m.move(0, -1, 0);
                gy = m.getY();
            }
            this.s = new FuriosoScript(p.position(), victim.position(), gy);
            Stasis.holdOne(level, victim, FuriosoScript.REAL_TOTAL + 4);
            GUARD.add(victim);
            ApocalypseServer.protect(p, FuriosoScript.REAL_TOTAL + 30);
            fx(p.position(), 200, ArsenalFxPayload.of(ArsenalFxPayload.FURIOSO, p.position(), victim.position(), p.getId(), victim.getId(), (int) Math.round(gy * 16)));
        }

        @Override
        protected void tick() {
            double now = FuriosoScript.scriptTime(age + 1);
            if (victim.isAlive() && now <= FuriosoScript.KILL) {
                Vec3 m = s.victim(now);
                victim.setPos(m.x, m.y, m.z);
                victim.setDeltaMovement(Vec3.ZERO);
                victim.fallDistance = 0;
            }
            ServerPlayer p = owner();
            for (FuriosoScript.Hit h : FuriosoScript.HITS) {
                if (h.time() <= lastS || h.time() > now || !victim.isAlive()) continue;
                if (h.time() >= FuriosoScript.KILL) {
                    GUARD.remove(victim);
                    Stasis.release(victim);
                    hurt(victim, ModDamageTypes.source(level, ModDamageTypes.ULTIMATE, p, p), h.damage() * com.slop.projectminecraft.Config.GLOVE_DAMAGE.get().floatValue());
                    // whatever survives Furioso bleeds and is bound, as the page says
                    if (victim.isAlive()) {
                        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 200, 2));
                        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
                    }
                    continue;
                }
                hurt(victim, p == null ? level.damageSources().generic() : level.damageSources().playerAttack(p), h.damage() * com.slop.projectminecraft.Config.GLOVE_DAMAGE.get().floatValue());
                victim.setDeltaMovement(Vec3.ZERO);
            }
            lastS = now;
        }

        @Override
        protected boolean done() {
            if (age > FuriosoScript.REAL_TOTAL) {
                GUARD.remove(victim);
                if (victim.isAlive()) Stasis.release(victim);
                return true;
            }
            return false;
        }
    }

    // =================================================================== E.G.O

    public static final int SOLEMN = 0, MAGIC_BULLET = 1, MATCH_FLAME = 2;
    private static final Map<UUID, Integer> BULLETS = new HashMap<>();

    public static int coins(int ego) {
        return ego == SOLEMN ? SOLEMN_COINS : 1;
    }

    public static int total(int ego) {
        return EGO_CUTIN + (ego == SOLEMN ? SOLEMN_TOTAL : ego == MAGIC_BULLET ? BULLET_TOTAL : MATCH_TOTAL);
    }

    public static final class Ego extends ApocalypseServer.Event {
        private final int ego, coins, bullet;
        private final boolean[] heads;
        private final List<LivingEntity> targets = new ArrayList<>();
        private final int[] order;
        private final Vec3 dir, start;

        public Ego(ServerLevel level, ServerPlayer p, int ego) {
            super(level, p);
            this.ego = ego;
            this.coins = coins(ego);
            this.dir = WeaponMath.flat(p.getLookAngle());
            this.start = p.position();
            Vec3 eye = p.getEyePosition();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(26), e -> Destruction.canHit(p, e))) {
                Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
                if (d.length() > 26 || d.normalize().dot(p.getLookAngle()) < 0.35) continue;
                if (!WeaponMath.visible(level, eye, e.getBoundingBox().getCenter())) continue;
                targets.add(e);
            }
            targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            while (targets.size() > 6) targets.remove(targets.size() - 1);
            // the coin toss: sanity (here, how healthy you are) tips the odds
            double chance = 0.5 + 0.15 * (p.getHealth() / p.getMaxHealth() - 0.5);
            heads = new boolean[coins];
            int bits = 0;
            for (int i = 0; i < coins; i++) {
                heads[i] = level.random.nextDouble() < chance;
                if (heads[i]) bits |= 1 << i;
            }
            int b = BULLETS.getOrDefault(p.getUUID(), 0) % 7 + 1;
            this.bullet = ego == MAGIC_BULLET ? b : 0;
            if (ego == MAGIC_BULLET) BULLETS.put(p.getUUID(), b);
            order = new int[coins];
            for (int i = 0; i < coins; i++) order[i] = targets.isEmpty() ? -1 : level.random.nextInt(targets.size());
            int[] ints = new int[6 + targets.size() + coins];
            ints[0] = p.getId();
            ints[1] = ego;
            ints[2] = coins;
            ints[3] = bits;
            ints[4] = bullet;
            ints[5] = targets.size();
            for (int i = 0; i < targets.size(); i++) ints[6 + i] = targets.get(i).getId();
            for (int i = 0; i < coins; i++) ints[6 + targets.size() + i] = order[i];
            for (LivingEntity t : targets) Stasis.holdOne(level, t, EGO_CUTIN + 12);
            ApocalypseServer.protect(p, total(ego) + 10);
            fx(start, 160, ArsenalFxPayload.of(ArsenalFxPayload.EGO, start, dir, ints));
            sound(start, ModSounds.EGO_ACTIVATE.get(), 2.5f, 1.0f);
        }

        int headsUpTo(int coin) {
            int n = 0;
            for (int i = 0; i <= coin && i < coins; i++) if (heads[i]) n++;
            return n;
        }

        DamageSource src() {
            ServerPlayer p = owner();
            return p == null ? level.damageSources().magic() : level.damageSources().playerAttack(p);
        }

        @Override
        protected void tick() {
            ServerPlayer p = owner();
            switch (ego) {
                case SOLEMN -> {
                    for (int i = 0; i < coins; i++) {
                        if (age != solemnShot(i) || order[i] < 0) continue;
                        LivingEntity t = targets.get(order[i]);
                        sound(start, ModSounds.SOLEMN_SHOT.get(), 2.0f, i % 2 == 0 ? 1.15f : 0.85f);
                        if (!t.isAlive()) continue;
                        Stasis.release(t);
                        hurt(t, src(), (6 + 3 * headsUpTo(i)) * 1.3f);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                        if (p != null) p.heal(1);
                    }
                    if (age == EGO_CUTIN + SOLEMN_FUNERAL) {
                        sound(start, ModSounds.BUTTERFLIES.get(), 3.0f, 1.0f);
                        for (LivingEntity t : targets) if (t.isAlive()) hurt(t, src(), 6 + 2.5f * headsUpTo(coins - 1));
                    }
                }
                case MAGIC_BULLET -> {
                    if (age == EGO_CUTIN + 2) sound(start, ModSounds.MAGIC_CIRCLE.get(), 2.0f, 1.0f);
                    if (age == EGO_CUTIN + BULLET_FIRE) {
                        Vec3 eye = start.add(0, 1.5, 0);
                        Vec3 aim = targets.isEmpty() ? dir : targets.get(0).getBoundingBox().getCenter().subtract(eye).normalize();
                        Vec3 end = eye.add(aim.scale(72));
                        float dmg = (22 + 14 * headsUpTo(0)) * 0.6f * (1 + 0.15f * bullet);
                        if (bullet == 7) dmg *= 4;
                        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(2.5), e -> Destruction.canHit(p, e))) {
                            if (WeaponMath.segDist(e.getBoundingBox().getCenter(), eye, end) > 1.4 + e.getBbWidth() * 0.5 + (bullet == 7 ? 1.2 : 0)) continue;
                            Stasis.release(e);
                            hurt(e, src(), dmg);
                            e.igniteForSeconds(bullet == 7 ? 10 : 5);
                            e.setDeltaMovement(aim.scale(0.8).add(0, 0.25, 0));
                            e.hasImpulse = true;
                        }
                        if (bullet == 7) {
                            for (double s = 3; s < 72; s += 1) {
                                Vec3 c = eye.add(aim.scale(s));
                                for (int[] o : Destruction.sphere(2)) {
                                    if (o[3] > 3) break;
                                    Destruction.remove(level, BlockPos.containing(c.x + o[0], c.y + o[1], c.z + o[2]));
                                }
                            }
                        }
                        sound(eye, ModSounds.MAGIC_BULLET.get(), bullet == 7 ? 6.0f : 3.0f, bullet == 7 ? 0.8f : 1.0f);
                    }
                }
                case MATCH_FLAME -> {
                    LivingEntity t = targets.isEmpty() ? null : targets.get(0);
                    if (age == EGO_CUTIN + MATCH_DASH && t != null && p != null) {
                        Vec3 to = t.position().subtract(dir.scale(2.2 + t.getBbWidth() * 0.5));
                        if (level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) {
                            p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
                        }
                        sound(to, ModSounds.IAI_DASH.get(), 1.5f, 1.2f);
                    }
                    if (age >= EGO_CUTIN + MATCH_STROKE0 && age < EGO_CUTIN + MATCH_STROKE0 + 10 * MATCH_STROKE_GAP && (age - EGO_CUTIN - MATCH_STROKE0) % MATCH_STROKE_GAP == 0) {
                        sound(start, ModSounds.MATCH_STROKE.get(), 1.4f, 0.9f + level.random.nextFloat() * 0.3f);
                        if (t != null && t.isAlive()) hurt(t, src(), 1.5f);
                    }
                    if (age == EGO_CUTIN + MATCH_IGNITE) {
                        Vec3 at = t != null ? t.getBoundingBox().getCenter() : start.add(dir.scale(4)).add(0, 1.5, 0);
                        if (t != null) Stasis.release(t);
                        sound(at, ModSounds.MATCH_IGNITE.get(), 4.0f, 1.0f);
                        Destruction.hurtArea(level, p, at, 5, (21 + 16 * headsUpTo(0)) * 1.3f, 0.5, 1.3, 8);
                        Destruction.explosionFx(level, at, 4.5f);
                    }
                }
                default -> {}
            }
        }

        @Override
        protected boolean done() {
            return age > total(ego);
        }
    }

    static void clear(UUID id) {
        BULLETS.remove(id);
    }

    static void clearAll() {
        BULLETS.clear();
        GUARD.clear();
    }

    /** Players aren't valid Furioso victims (their client owns their position). */
    public static boolean validTarget(LivingEntity e) {
        return !(e instanceof Player);
    }
}
