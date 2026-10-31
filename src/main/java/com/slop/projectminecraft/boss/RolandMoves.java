package com.slop.projectminecraft.boss;

import static com.slop.projectminecraft.arsenal.FuriosoScript.AXE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.DURANDAL;
import static com.slop.projectminecraft.arsenal.FuriosoScript.GAUNTLETS;
import static com.slop.projectminecraft.arsenal.FuriosoScript.GREATSWORD;
import static com.slop.projectminecraft.arsenal.FuriosoScript.HAMMER;
import static com.slop.projectminecraft.arsenal.FuriosoScript.KATANA;
import static com.slop.projectminecraft.arsenal.FuriosoScript.KNIFE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.LANCE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.MACE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.PISTOLS;
import static com.slop.projectminecraft.arsenal.FuriosoScript.SHORTSWORDS;
import static com.slop.projectminecraft.arsenal.FuriosoScript.SHOTGUN;

import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.FxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.joml.Vector3f;

/**
 * Everything Roland does, phase by phase. Moves run one at a time (Roland.move), each a little timeline; the weapon
 * hits reuse the Black Silence gloves' visuals (GLOVE_ATTACK packets with Roland or Angelica as the wielder) and the
 * Furioso card. Nothing here touches a block.
 */
public final class RolandMoves {
    private RolandMoves() {}

    static final Vec3 UP = new Vec3(0, 1, 0);
    /** ArsenalFxPayload.ROLAND sub-kinds. */
    public static final int FX_FADE = 0, FX_TITLE = 1, FX_STREAK = 2, FX_SHOCK = 3, FX_SPIKE = 4;
    public static final int TITLE_FURIOSO = 0, TITLE_SCREAM = 1;
    /** Angelica's speaker index (BossClient). */
    public static final int ANGELICA_SPEAKER = 1;
    public static final int ANGELICA_LINE_HERE = 0, ANGELICA_LINE_TOGETHER = 1, ANGELICA_LINE_FALL = 2, ANGELICA_LINE_GONE = 3;

    // =================================================================== moves

    abstract static class Move {
        final Roland r;
        int age = -1;

        Move(Roland r) {
            this.r = r;
        }

        void tick() {
            age++;
            step((ServerLevel) r.level());
        }

        abstract void step(ServerLevel level);

        abstract boolean done();
    }

    static void start(Roland r, Move m, String name) {
        r.move = m;
        r.note(name);
    }

    // =================================================================== the phase brains

    static void tickPhase(Roland r, ServerLevel level, long now, List<ServerPlayer> guests) {
        for (int i = 0; i < r.cd.length; i++) if (r.cd[i] > 0) r.cd[i]--;
        if (r.phase() == Roland.DISTORTED) tickHaze(r, level, now, guests);
        if (r.phase() == Roland.BOND && bondTick(r, level, now, guests)) return;
        if (r.phase() == Roland.WEAPONS) scenes(r, level, now);
        if (r.phase() == Roland.BYGONES && readyToAct(r, level, now)) return;
        if (r.move != null) {
            r.move.tick();
            if (r.move.done()) {
                r.move = null;
                if (r.phase() != Roland.DISTORTED) r.hold(DURANDAL, -1);
                r.setBodyPose(Roland.POSE_STAND);
            } else return;
        }
        LivingEntity t = r.target;
        if (t == null) return;
        r.getLookControl().setLookAt(t, 30, 30);
        double dist = r.distanceTo(t);
        if (r.cooldown > 0) {
            r.cooldown--;
            approach(r, t, dist, r.phase() == Roland.DISTORTED ? 4.5 : 2.6);
            return;
        }
        switch (r.phase()) {
            case Roland.BLACK_SILENCE -> pickBlackSilence(r);
            case Roland.DISTORTED -> pickBeast(r, level, t, dist);
            case Roland.BOND -> pickBond(r, t, dist);
            case Roland.WEAPONS, Roland.BYGONES -> pickMemory(r, t, dist);
            default -> {}
        }
    }

    static void approach(Roland r, LivingEntity t, double dist, double keep) {
        if (dist > keep) {
            if (r.tickCount % 5 == 0) r.getNavigation().moveTo(t, 1.15);
        } else r.getNavigation().stop();
    }

    // =================================================================== hitting things

    static boolean canHit(Entity e) {
        return e instanceof LivingEntity le && le.isAlive() && !(e instanceof Roland) && !(e instanceof RolandKin) && !(e instanceof ArmorStand)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    static List<LivingEntity> line(ServerLevel level, Vec3 a, Vec3 b, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(radius + 1.5), RolandMoves::canHit)) {
            if (segDist(e.getBoundingBox().getCenter(), a, b) <= radius + e.getBbWidth() * 0.5) out.add(e);
        }
        return out;
    }

    static List<LivingEntity> around(ServerLevel level, Vec3 c, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 3, radius),
                e -> canHit(e) && e.position().subtract(c).horizontalDistance() <= radius + e.getBbWidth() * 0.5);
    }

    static List<LivingEntity> arc(ServerLevel level, Entity from, Vec3 flat, double range, double half) {
        double cos = Math.cos(Math.toRadians(half));
        Vec3 c = from.position();
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range, 2, range), e -> {
            if (!canHit(e)) return false;
            Vec3 d = e.position().subtract(c);
            double h = d.horizontalDistance();
            if (h > range + e.getBbWidth() * 0.5) return false;
            return h < 1 || flat(d).dot(flat) >= cos;
        });
    }

    static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double k = len < 1e-6 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len, 0, 1);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** A blow from Roland or one of his own. */
    static boolean damage(LivingEntity by, LivingEntity t, float dmg) {
        t.invulnerableTime = 0;
        return t.hurt(by.level().damageSources().mobAttack(by), dmg);
    }

    static void knock(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    static boolean sees(LivingEntity a, Vec3 to) {
        return a.level().clip(new ClipContext(a.getEyePosition(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a)).getType() == HitResult.Type.MISS;
    }

    /**
     * One workshop weapon, used on `t` by `by` (Roland or Angelica): the same shapes the gloves have, tuned to be
     * fought, and the gloves' own visuals and sounds.
     */
    static void strike(LivingEntity by, ServerLevel level, int w, float mult, @Nullable LivingEntity t, boolean right) {
        Vec3 eye = by.getEyePosition();
        Vec3 aim = t != null ? t.getBoundingBox().getCenter().subtract(eye).normalize() : Vec3.directionFromRotation(0, by.getYRot());
        Vec3 flat = flat(aim), side = flat.cross(UP).normalize();
        Vec3 from = eye.add(flat.scale(0.5)).add(side.scale(right ? 0.35 : -0.35)).add(0, -0.3, 0);
        Vec3 to = eye.add(aim.scale(3));
        List<LivingEntity> hits = new ArrayList<>();
        float dmg = 0;
        double dist = t == null ? 99 : by.distanceTo(t);
        switch (w) {
            case PISTOLS -> {
                dmg = 5;
                if (t != null && dist < 32 && sees(by, t.getBoundingBox().getCenter())) {
                    to = t.getBoundingBox().getCenter();
                    hits.add(t);
                } else to = eye.add(aim.scale(24));
            }
            case SHOTGUN -> {
                // eight pellets: the closer you are, the more of them find you
                to = eye.add(aim.scale(10));
                if (t != null && dist < 10 && sees(by, t.getBoundingBox().getCenter())) {
                    int n = 0;
                    for (int i = 0; i < 8; i++) if (by.getRandom().nextDouble() < 1.25 - dist / 8) n++;
                    if (n > 0) hits.add(t);
                    dmg = 2.2f * n;
                }
            }
            case LANCE -> {
                dmg = 9;
                knock(by, flat.scale(0.9).add(0, 0.1, 0));
                to = eye.add(aim.scale(5.5));
                hits.addAll(line(level, eye, to, 0.8));
            }
            case KATANA -> {
                // the draw: through them in one step, standing behind
                dmg = 11;
                Vec3 start = by.position();
                Vec3 stop = t != null ? t.position().add(flat.scale(2.6)) : start.add(flat.scale(4));
                if (!level.noCollision(by, by.getBoundingBox().move(stop.subtract(start)))) stop = start.add(flat.scale(Math.max(0, dist - 1.2)));
                from = start.add(0, 1, 0);
                to = stop.add(0, 1, 0);
                hits.addAll(line(level, from, to, 1.0));
                by.teleportTo(stop.x, stop.y, stop.z);
            }
            case DURANDAL -> {
                dmg = 9;
                to = eye.add(aim.scale(4.5));
                hits.addAll(line(level, eye, to, 1.0));
            }
            case HAMMER -> {
                dmg = 12;
                to = by.position().add(flat.scale(2));
                hits.addAll(around(level, to, 2.8));
            }
            case AXE -> {
                dmg = 8;
                hits.addAll(arc(level, by, flat, 3.8, 70));
            }
            case GREATSWORD -> {
                dmg = 14;
                to = by.position().add(flat.scale(2.5));
                hits.addAll(arc(level, by, flat, 4.5, 55));
            }
            case MACE, GAUNTLETS, KNIFE, SHORTSWORDS -> {
                dmg = w == MACE ? 8 : w == KNIFE ? 6 : 4;
                if (t != null && dist < 3.8) hits.add(t);
                if (w == SHORTSWORDS) knock(by, side.scale(right ? 0.35 : -0.35));
            }
            default -> {}
        }
        List<LivingEntity> landed = new ArrayList<>();
        for (LivingEntity e : hits) {
            if (!damage(by, e, dmg * mult * markBonus(by, e))) continue;
            landed.add(e);
            Vec3 push = flat(e.position().subtract(by.position()));
            switch (w) {
                case LANCE -> knock(e, push.scale(0.9).add(0, 0.15, 0));
                case SHOTGUN -> knock(e, push.scale(1.2).add(0, 0.25, 0));
                case HAMMER -> knock(e, push.scale(0.4).add(0, 0.4, 0));
                case MACE -> knock(e, push.scale(0.2).add(0, 1.0, 0));
                case GREATSWORD -> knock(e, push.scale(0.6).add(0, -0.3, 0));
                case KNIFE -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), by);
                default -> {}
            }
        }
        if (w != PISTOLS && w != SHOTGUN) by.swing(right ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        int[] ints = new int[3 + landed.size()];
        ints[0] = by.getId();
        ints[1] = w;
        ints[2] = right ? 1 : 0;
        for (int i = 0; i < landed.size(); i++) ints[3 + i] = landed.get(i).getId();
        Network.sendNear(level, eye, 96, ArsenalFxPayload.of(ArsenalFxPayload.GLOVE_ATTACK, from, to, ints));
    }

    /** Dark Mark: Roland hits a marked guest harder. */
    static float markBonus(LivingEntity by, LivingEntity t) {
        if (by instanceof Roland r) {
            Long until = r.darkMark.get(t.getUUID());
            if (until != null && until > r.level().getGameTime()) return 1.3f;
        }
        return 1;
    }

    /** Your weapon knocked out of your hand: it's pushed back into your pack, and your hand is empty for a moment. */
    static void disarm(LivingEntity t) {
        if (!(t instanceof ServerPlayer p)) return;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return;
        for (int i = 9; i < 36; i++) {
            if (p.getInventory().getItem(i).isEmpty()) {
                p.getInventory().setItem(i, held.copy());
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                p.displayClientMessage(Component.translatable("message.projectminecraft.roland.disarmed"), true);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
                return;
            }
        }
        p.getCooldowns().addCooldown(held.getItem(), 60);
    }

    static void dust(ServerLevel level, Vec3 at, int rgb, int n, double spread, float size) {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), size);
        level.sendParticles(d, at.x, at.y, at.z, n, spread, spread, spread, 0.01);
    }

    static void streak(Roland r, Vec3 a, Vec3 b, int rgb, float width) {
        r.fx(a, b, FX_STREAK, rgb, Math.round(width * 100));
    }

    static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, volume, pitch);
    }

    // =================================================================== phase 1: the Black Silence

    /** His nine combat pages: the weapons each one brings out. */
    enum Page {
        ATELIER(7, new int[][]{{0, PISTOLS, 1}, {6, PISTOLS, 0}, {18, SHOTGUN, 1}}),
        ZELKOVA(3, new int[][]{{0, AXE, 1}, {10, MACE, 0}}),
        ALLAS(5, new int[][]{{0, LANCE, 1}, {14, LANCE, 1}}),
        DURANDAL_PAGE(3.5, new int[][]{{0, DURANDAL, 1}, {10, DURANDAL, 1}}),
        OLD_BOYS(3, new int[][]{{16, HAMMER, 1}}),
        WHEELS(7, new int[][]{{14, GREATSWORD, 1}}),
        RANGA(3, new int[][]{{0, GAUNTLETS, 1}, {5, GAUNTLETS, 0}, {12, KNIFE, 1}}),
        CRYSTAL(3, new int[][]{{0, SHORTSWORDS, 1}, {4, SHORTSWORDS, 0}, {8, SHORTSWORDS, 1}, {12, SHORTSWORDS, 0}}),
        MOOK(6, new int[][]{{10, KATANA, 1}});

        final double range;
        final int[][] strikes;

        Page(double range, int[][] strikes) {
            this.range = range;
            this.strikes = strikes;
        }

        static final int ALL = (1 << values().length) - 1;
    }

    static void pickBlackSilence(Roland r) {
        if (r.pagesUsed == Page.ALL) {
            // every page spent: Furioso
            r.pagesUsed = 0;
            start(r, new Furioso(r, 12), "furioso");
            return;
        }
        List<Page> pool = new ArrayList<>();
        for (Page p : Page.values()) if ((r.pagesUsed & (1 << p.ordinal())) == 0) pool.add(p);
        Page p = pool.get(r.getRandom().nextInt(pool.size()));
        r.pagesUsed |= 1 << p.ordinal();
        // every third page hits harder
        float mult = ++r.pageCount % 3 == 0 ? 1.3f : 1;
        start(r, new PageMove(r, p, mult), "page_" + p.name().toLowerCase());
    }

    /** One combat page: close in (or not, for the guns), a moment's warning, then its strikes. */
    static final class PageMove extends Move {
        static final int TELEGRAPH = 8, APPROACH = 40;
        final Page page;
        final float mult;
        int engaged = -1;

        PageMove(Roland r, Page page, float mult) {
            super(r);
            this.page = page;
            this.mult = mult;
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            if (age == 0) {
                int w = page.strikes[0][1];
                r.hold(w, FuriosoScript.pair(w) ? -1 : page == Page.ZELKOVA ? MACE : -1);
                sound(level, r.position(), ModSounds.WEAPON_SWAP.get(), 1.0f, 1.0f);
            }
            if (engaged < 0) {
                double dist = t == null ? 0 : r.distanceTo(t);
                if (t != null && dist > page.range && age < APPROACH) {
                    // the greatsword leaps the gap; everything else closes in fast
                    if (page == Page.WHEELS && dist < 12 && r.onGround()) {
                        Vec3 d = flat(t.position().subtract(r.position()));
                        r.setDeltaMovement(d.x * dist * 0.13, 0.75, d.z * dist * 0.13);
                        r.hasImpulse = true;
                        engaged = age;
                    } else if (age % 5 == 0) r.getNavigation().moveTo(t, 1.35);
                    return;
                }
                engaged = age;
                r.getNavigation().stop();
                if (page == Page.OLD_BOYS) r.setBodyPose(Roland.POSE_GUARD);
            }
            r.faceTarget();
            int k = age - engaged - TELEGRAPH;
            if (k < 0) {
                dust(level, r.position().add(0, 1.2, 0), 0x202020, 3, 0.4, 1.4f);
                return;
            }
            for (int[] s : page.strikes) {
                if (k != s[0]) continue;
                int w = s[1];
                r.hold(w, page == Page.ZELKOVA && w == AXE ? MACE : page == Page.RANGA && w == KNIFE ? GAUNTLETS : -1);
                if (page == Page.OLD_BOYS) r.setBodyPose(Roland.POSE_STAND);
                strike(r, level, w, mult, t, s[2] == 1);
            }
        }

        @Override
        boolean done() {
            int last = page.strikes[page.strikes.length - 1][0];
            boolean over = engaged >= 0 && age - engaged > TELEGRAPH + last + 10;
            if (over) r.cooldown = 18 + r.getRandom().nextInt(16);
            return over;
        }
    }

    /**
     * Furioso: the title card, you're rooted, and he comes at you from every side with every weapon in turn, ending in
     * Durandal. `hits` blows in all (the last is Durandal).
     */
    static final class Furioso extends Move {
        static final int CARD = 22, EVERY = 5;
        static final int[] ORDER = {PISTOLS, LANCE, HAMMER, KATANA, GAUNTLETS, KNIFE, AXE, MACE, GREATSWORD, SHORTSWORDS, SHOTGUN};
        final int hits;

        Furioso(Roland r, int hits) {
            super(r);
            this.hits = Math.max(1, hits);
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            if (age == 0) {
                r.say(Roland.LINE_FURIOSO);
                r.fx(r.position(), r.position(), FX_TITLE, TITLE_FURIOSO);
                sound(level, r.position(), ModSounds.FURIOSO_STING.get(), 2.5f, 1.0f);
                for (ServerPlayer p : r.guests()) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CARD + hits * EVERY + 10, 6, false, false));
            }
            if (t == null || age < CARD) return;
            int k = age - CARD;
            if (k % EVERY != 0 || k / EVERY >= hits) return;
            int i = k / EVERY;
            boolean last = i == hits - 1;
            int w = last ? DURANDAL : ORDER[i % ORDER.length];
            // a blink to a new side of you
            double ang = i * 2.4 + r.getRandom().nextDouble() * 0.4;
            Vec3 at = t.position().add(Math.cos(ang) * 2.2, 0, Math.sin(ang) * 2.2);
            if (level.noCollision(r, r.getBoundingBox().move(at.subtract(r.position())))) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1, r.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
                r.teleportTo(at.x, at.y, at.z);
            }
            r.faceTarget();
            r.hold(w, -1);
            float dmg = last ? 10 : 3.5f;
            if (damage(r, t, dmg)) knock(t, flat(t.position().subtract(r.position())).scale(last ? 0.8 : 0.05).add(0, last ? 0.3 : 0, 0));
            Network.sendNear(level, r.getEyePosition(), 96, ArsenalFxPayload.of(ArsenalFxPayload.GLOVE_ATTACK, r.getEyePosition(), t.getBoundingBox().getCenter(), r.getId(), w, 1, t.getId()));
            r.swing(InteractionHand.MAIN_HAND);
            if (last) sound(level, t.position(), ModSounds.BIG_IMPACT.get(), 2.5f, 1.0f);
        }

        @Override
        boolean done() {
            boolean over = age > CARD + hits * EVERY + 8;
            if (over) r.cooldown = 40;
            return over;
        }
    }

    // =================================================================== phase 2: distorted

    static final int SWING = 0, TAIL = 1, BITE = 2, SINEWS = 3, SPIKES = 4, SMOKE = 5, FLAIL = 6;

    static void pickBeast(Roland r, ServerLevel level, LivingEntity t, double dist) {
        Vec3 fwd = Vec3.directionFromRotation(0, r.getYRot());
        boolean behind = flat(t.position().subtract(r.position())).dot(fwd) < -0.3;
        int[] cd = r.cd;
        if (cd[SPIKES] <= 0) {
            cd[SPIKES] = 180;
            start(r, new Spikes(r), "spiky_tendrils");
        } else if (cd[SMOKE] <= 0 && dist < 14) {
            cd[SMOKE] = 220;
            start(r, new Smoke(r), "spew_smoke");
        } else if (behind && dist < 7.5 && cd[TAIL] <= 0) {
            cd[TAIL] = 60;
            start(r, new BeastBlow(r, TAIL), "tail_swipe");
        } else if (dist < 7 && cd[FLAIL] <= 0) {
            cd[FLAIL] = 140;
            start(r, new BeastBlow(r, FLAIL), "flail");
        } else if (dist < 6.5 && cd[SWING] <= 0) {
            cd[SWING] = 40;
            start(r, new BeastBlow(r, SWING), "swing");
        } else if (dist >= 4 && dist < 11 && cd[BITE] <= 0) {
            cd[BITE] = 80;
            start(r, new BeastBlow(r, BITE), "bite_of_flesh");
        } else if (dist >= 9 && cd[SINEWS] <= 0 && sees(r, t.getBoundingBox().getCenter())) {
            cd[SINEWS] = 120;
            start(r, new Sinews(r), "amalgamated_sinews");
        } else approach(r, t, dist, 4.5);
    }

    /** The beast's close blows: a claw sweep, a tail swipe behind it, a lunging bite, a flailing spin. */
    static final class BeastBlow extends Move {
        final int kind;

        BeastBlow(Roland r, int kind) {
            super(r);
            this.kind = kind;
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            r.getNavigation().stop();
            Vec3 fwd = Vec3.directionFromRotation(0, r.getYRot());
            if (kind != TAIL && t != null && age < 10) r.faceTarget();
            if (age == 0) {
                sound(level, r.position(), SoundEvents.WARDEN_ANGRY, 1.6f, 0.7f);
                r.swing(InteractionHand.MAIN_HAND);
            }
            switch (kind) {
                case SWING -> {
                    if (age == 12) {
                        for (LivingEntity e : arc(level, r, flat(fwd), 6.8, 75)) {
                            if (damage(r, e, 10)) knock(e, flat(e.position().subtract(r.position())).scale(0.6).add(0, 0.2, 0));
                        }
                        sound(level, r.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 2.0f, 0.5f);
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, r.getX() + fwd.x * 2.5, r.getY() + 1, r.getZ() + fwd.z * 2.5, 3, 1, 0.2, 1, 0);
                    }
                }
                case TAIL -> {
                    if (age == 10) {
                        for (LivingEntity e : arc(level, r, flat(fwd.scale(-1)), 7.5, 80)) {
                            if (damage(r, e, 8)) knock(e, flat(e.position().subtract(r.position())).scale(1.6).add(0, 0.4, 0));
                        }
                        sound(level, r.position(), SoundEvents.RAVAGER_ATTACK, 2.0f, 0.6f);
                    }
                }
                case BITE -> {
                    if (age == 14 && t != null) {
                        Vec3 d = flat(t.position().subtract(r.position()));
                        r.setDeltaMovement(d.x * 1.3, 0.25, d.z * 1.3);
                        r.hasImpulse = true;
                        sound(level, r.position(), SoundEvents.RAVAGER_ROAR, 1.8f, 0.8f);
                    }
                    if (age == 20 && t != null && r.distanceTo(t) < 5.5) {
                        if (damage(r, t, 12)) {
                            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), r);
                            addHaze(r, t, 1);
                        }
                        sound(level, t.position(), SoundEvents.EVOKER_FANGS_ATTACK, 2.0f, 0.6f);
                    }
                }
                case FLAIL -> {
                    if (age >= 12 && (age - 12) % 8 == 0 && age <= 28) {
                        for (LivingEntity e : around(level, r.position(), 7)) if (damage(r, e, 5)) knock(e, flat(e.position().subtract(r.position())).scale(0.5).add(0, 0.25, 0));
                        r.fx(r.position().add(0, 1, 0), r.position(), FX_SHOCK, 6, 0x6A1010);
                        sound(level, r.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 2.0f, 0.4f);
                        dust(level, r.position().add(0, 1.2, 0), 0x501010, 30, 2.5, 2f);
                    }
                }
                default -> {}
            }
        }

        @Override
        boolean done() {
            boolean over = age > (kind == FLAIL ? 36 : kind == BITE ? 30 : 22);
            if (over) r.cooldown = 10 + r.getRandom().nextInt(12);
            return over;
        }
    }

    /** Amalgamated Sinews: a rope of sinew spat at you that drags you in. */
    static final class Sinews extends Move {
        Sinews(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            r.getNavigation().stop();
            if (t == null) return;
            r.faceTarget();
            Vec3 mouth = r.position().add(Vec3.directionFromRotation(0, r.getYRot()).scale(1.6)).add(0, 1.6, 0);
            if (age == 0) sound(level, mouth, SoundEvents.WARDEN_ROAR, 1.2f, 1.4f);
            if (age >= 10 && age <= 22 && age % 2 == 0) streak(r, mouth, t.getBoundingBox().getCenter(), 0x8A1A1A, 0.22f);
            if (age == 12 && sees(r, t.getBoundingBox().getCenter())) {
                if (damage(r, t, 6)) {
                    addHaze(r, t, 1);
                    Vec3 d = r.position().subtract(t.position());
                    knock(t, flat(d).scale(Math.min(2.0, d.length() * 0.18)).add(0, 0.35, 0));
                }
                sound(level, t.position(), SoundEvents.SLIME_ATTACK, 2.0f, 0.5f);
            }
        }

        @Override
        boolean done() {
            boolean over = age > 24;
            if (over) r.cooldown = 10;
            return over;
        }
    }

    /** Spiky Tendrils: rings of warning under every guest, then the tendrils burst out of the floor. */
    static final class Spikes extends Move {
        static final int ERUPT = 26;
        final List<Vec3> points = new ArrayList<>();

        Spikes(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {
            r.getNavigation().stop();
            if (age == 0) {
                sound(level, r.position(), SoundEvents.WARDEN_DIG, 2.0f, 0.6f);
                for (ServerPlayer p : r.guests()) {
                    points.add(p.position());
                    for (int i = 0; i < 3; i++) {
                        double a = r.getRandom().nextDouble() * Mth.TWO_PI, d = 2 + r.getRandom().nextDouble() * 3;
                        points.add(p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
                    }
                }
                for (Vec3 p : points) r.fx(p, p, FX_SPIKE, 0);
            }
            if (age < ERUPT && age % 4 == 0) for (Vec3 p : points) dust(level, p.add(0, 0.1, 0), 0x8A1010, 6, 0.6, 1.5f);
            if (age == ERUPT) {
                for (Vec3 p : points) {
                    r.fx(p, p, FX_SPIKE, 1);
                    for (LivingEntity e : around(level, p, 1.7)) if (damage(r, e, 10)) knock(e, new Vec3(0, 0.9, 0));
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.5, p.z, 6, 0.3, 0.6, 0.3, 0.02);
                }
                sound(level, r.position(), SoundEvents.EVOKER_FANGS_ATTACK, 3.0f, 0.5f);
            }
        }

        @Override
        boolean done() {
            boolean over = age > ERUPT + 8;
            if (over) r.cooldown = 16;
            return over;
        }
    }

    /** Spew Smoke: a wave of haze across the floor. */
    static final class Smoke extends Move {
        Smoke(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {
            r.getNavigation().stop();
            if (age == 0) sound(level, r.position(), SoundEvents.WARDEN_ROAR, 2.0f, 0.5f);
            if (age >= 8 && age <= 24 && age % 2 == 0) {
                double rad = (age - 8) * 0.8;
                for (int i = 0; i < 16; i++) {
                    double a = i * Mth.TWO_PI / 16;
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX() + Math.cos(a) * rad, r.getY() + 0.6, r.getZ() + Math.sin(a) * rad, 2, 0.3, 0.3, 0.3, 0.01);
                }
            }
            if (age == 16) {
                for (LivingEntity e : around(level, r.position(), 14)) if (damage(r, e, 4)) addHaze(r, e, 1);
            }
        }

        @Override
        boolean done() {
            boolean over = age > 28;
            if (over) r.cooldown = 14;
            return over;
        }
    }

    /** Suffocating Haze: up to three stacks; every six seconds each stack chokes you with something. */
    static void addHaze(Roland r, LivingEntity t, int n) {
        if (!(t instanceof ServerPlayer p)) return;
        int s = Math.min(3, r.haze.getOrDefault(p.getUUID(), 0) + n);
        r.haze.put(p.getUUID(), s);
        r.hazeNext.putIfAbsent(p.getUUID(), r.level().getGameTime() + 120);
    }

    static void tickHaze(Roland r, ServerLevel level, long now, List<ServerPlayer> guests) {
        for (ServerPlayer p : guests) {
            int s = r.haze.getOrDefault(p.getUUID(), 0);
            if (s <= 0) continue;
            if (now % 20 == 0) p.displayClientMessage(Component.translatable("message.projectminecraft.roland.haze", "●".repeat(s) + "○".repeat(3 - s)), true);
            if (now % 5 == 0) level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), s, 0.3, 0.5, 0.3, 0.01);
            if (now < r.hazeNext.getOrDefault(p.getUUID(), now)) continue;
            r.hazeNext.put(p.getUUID(), now + 120);
            for (int i = 0; i < s; i++) {
                switch (r.getRandom().nextInt(6)) {
                    case 0 -> p.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), r);                  // bleed
                    case 1 -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3), r);       // paralysis
                    case 2 -> p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), r);               // feeble
                    case 3 -> p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1), r);           // disarm
                    case 4 -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), r);      // bind
                    default -> r.fragile.put(p.getUUID(), now + 100);                                          // fragile
                }
            }
            sound(level, p.position(), SoundEvents.PLAYER_HURT_DROWN, 1.0f, 0.7f);
            r.note("haze" + s);
        }
    }

    static void spawnByproducts(Roland r, int n) {
        ServerLevel level = (ServerLevel) r.level();
        BlockPos c = r.center();
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n + 0.4;
            RolandKin k = RolandKin.create(level, r, RolandKin.BYPRODUCT, 0);
            if (k == null) continue;
            k.moveTo(c.getX() + 0.5 + Math.cos(a) * 9, LibraryArena.FLOOR + 1, c.getZ() + 0.5 + Math.sin(a) * 9, r.getRandom().nextFloat() * 360, 0);
            level.addFreshEntity(k);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, k.getX(), k.getY() + 0.6, k.getZ(), 20, 0.4, 0.4, 0.4, 0.02);
        }
    }

    // =================================================================== phase 3: Roland and Angelica

    static final int B_COMBO = 0, B_VIGIL = 1, B_FRENZY = 2, B_BOND = 3, B_WALTZ = 4;

    static void spawnAngelica(Roland r) {
        ServerLevel level = (ServerLevel) r.level();
        RolandKin a = RolandKin.create(level, r, RolandKin.ANGELICA, 0);
        if (a == null) return;
        Vec3 back = r.position().subtract(Vec3.directionFromRotation(0, r.getYRot()).scale(2.2));
        a.moveTo(back.x, back.y, back.z, r.getYRot(), 0);
        level.addFreshEntity(a);
        r.angelica = a.getUUID();
        dust(level, a.position().add(0, 1, 0), 0xF4F4F4, 60, 0.6, 2.2f);
        level.sendParticles(ParticleTypes.END_ROD, a.getX(), a.getY() + 1, a.getZ(), 30, 0.4, 0.8, 0.4, 0.04);
    }

    /** Soul Link, paralysis, the fallen. True when he can't act this tick. */
    static boolean bondTick(Roland r, ServerLevel level, long now, List<ServerPlayer> guests) {
        RolandKin a = r.angelicaEntity();
        if (now % 20 == 0) {
            int al = a != null ? a.links : 0;
            Component bar = Component.translatable("message.projectminecraft.roland.soul_link", pips(r.links), pips(al));
            for (ServerPlayer p : guests) p.displayClientMessage(bar, true);
        }
        if (r.fallen()) {
            r.getNavigation().stop();
            if (now % 4 == 0) dust(level, r.position().add(0, 0.8, 0), 0x181818, 8, 0.5, 2f);
            if (now >= r.fallenUntil) {
                if (r.links > 0) {
                    r.links--;
                    r.fallenUntil = -1;
                    r.setHealth(r.getMaxHealth() * 0.5f);
                    r.setBodyPose(Roland.POSE_STAND);
                    r.setInvisible(false);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1, r.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
                    sound(level, r.position(), SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.6f);
                    r.note("revive " + r.links);
                } else r.fallenUntil = Long.MAX_VALUE;
            }
            return true;
        }
        // both out of links: the bond can't hold them up any more, and for a moment neither can move
        if (!r.paralysedOnce && r.links == 0 && a != null && a.links == 0) {
            r.paralysedOnce = true;
            r.paralysed = 120;
            a.paralyse(120);
            r.note("paralysed");
            sound(level, r.position(), SoundEvents.CHAIN_BREAK, 2.5f, 0.5f);
        }
        if (r.paralysed > 0) {
            r.paralysed--;
            r.move = null;
            r.setBodyPose(r.paralysed > 0 ? Roland.POSE_PARALYSED : Roland.POSE_STAND);
            r.getNavigation().stop();
            if (now % 4 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, r.getX(), r.getY() + 1, r.getZ(), 4, 0.3, 0.6, 0.3, 0.05);
            return true;
        }
        return false;
    }

    static String pips(int n) {
        return "◆".repeat(Math.max(0, n)) + "◇".repeat(Math.max(0, 4 - n));
    }

    /** One of the two has fallen: if the other is already down, the bond breaks for good. */
    static void fall(Roland r) {
        ServerLevel level = (ServerLevel) r.level();
        r.fallenUntil = level.getGameTime() + 160;
        r.setBodyPose(Roland.POSE_MIST);
        r.move = null;
        r.note("fallen " + r.links);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1, r.getZ(), 40, 0.4, 0.8, 0.4, 0.03);
        checkBothDown(r);
    }

    static void checkBothDown(Roland r) {
        RolandKin a = r.angelicaEntity();
        if (r.fallen() && (a == null || a.fallen())) r.beginShift((ServerLevel) r.level());
    }

    static void pickBond(Roland r, LivingEntity t, double dist) {
        long now = r.level().getGameTime();
        int[] cd = r.cd;
        r.moveCount++;
        // every third move, the Dark Mark
        if (r.moveCount % 3 == 0) {
            r.darkMark.put(t.getUUID(), now + 100);
            dust((ServerLevel) r.level(), t.getBoundingBox().getCenter(), 0x101010, 40, 0.5, 2f);
            if (t instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.projectminecraft.roland.dark_mark"), true);
        }
        RolandKin a = r.angelicaEntity();
        if (a != null && !a.fallen() && cd[B_WALTZ] <= 0 && r.tickCount > 200) {
            cd[B_WALTZ] = 320;
            start(r, new Waltz(r), "waltz");
        } else if (a != null && !a.fallen() && cd[B_BOND] <= 0 && dist < 14) {
            cd[B_BOND] = 200;
            start(r, new Bond(r), "bond");
        } else if (cd[B_VIGIL] <= 0 && dist < 6) {
            cd[B_VIGIL] = 240;
            start(r, new BondStrikes(r, B_VIGIL), "vigilant_stand");
        } else if (cd[B_FRENZY] <= 0 && dist < 6) {
            cd[B_FRENZY] = 140;
            start(r, new BondStrikes(r, B_FRENZY), "frenzy");
        } else {
            start(r, new BondStrikes(r, B_COMBO), "combo");
        }
    }

    /** Combos with Angelica, Vigilant Stand, Frenzy: smoke-wrapped weapons, close in. */
    static final class BondStrikes extends Move {
        final int kind;
        final int combo;
        int engaged = -1;

        BondStrikes(Roland r, int kind) {
            super(r);
            this.kind = kind;
            this.combo = r.getRandom().nextInt(3);
        }

        int[][] script() {
            return switch (kind) {
                case B_VIGIL -> new int[][]{{0, DURANDAL, 1}};
                case B_FRENZY -> new int[][]{{0, DURANDAL, 1}, {4, DURANDAL, 1}, {8, DURANDAL, 1}, {12, DURANDAL, 1}};
                default -> combo == 0 ? new int[][]{{0, AXE, 1}, {8, MACE, 0}} : combo == 1 ? new int[][]{{0, LANCE, 1}} : new int[][]{{0, PISTOLS, 1}, {4, PISTOLS, 0}};
            };
        }

        double range() {
            return kind == B_COMBO && combo == 2 ? 12 : kind == B_COMBO && combo == 1 ? 5 : 3.2;
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            int[][] s = script();
            if (age == 0) {
                r.hold(s[0][1], s[0][1] == AXE ? MACE : -1);
                // Angelica joins the combo a beat behind
                RolandKin a = r.angelicaEntity();
                if (kind == B_COMBO && a != null && !a.fallen()) a.order(s, 10);
            }
            if (engaged < 0) {
                if (t != null && r.distanceTo(t) > range() && age < 40) {
                    if (age % 5 == 0) r.getNavigation().moveTo(t, 1.3);
                    return;
                }
                engaged = age;
                r.getNavigation().stop();
            }
            r.faceTarget();
            int k = age - engaged - 8;
            if (k < 0) {
                dust(level, r.position().add(0, 1.2, 0), 0x111111, 4, 0.4, 1.6f);
                return;
            }
            for (int[] st : s) {
                if (st[0] != k) continue;
                r.hold(st[1], st[1] == AXE ? MACE : -1);
                strike(r, level, st[1], kind == B_FRENZY ? 0.55f : 1, t, st[2] == 1);
                if (kind == B_VIGIL && t != null && r.distanceTo(t) < 5) t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), r);
            }
            // Vigilant Stand: then a guard that mends his partner
            if (kind == B_VIGIL && k == 6) {
                r.setBodyPose(Roland.POSE_GUARD);
                RolandKin a = r.angelicaEntity();
                if (a != null && !a.fallen()) {
                    a.heal(20);
                    dust(level, a.position().add(0, 1, 0), 0xFFFFFF, 20, 0.5, 1.5f);
                }
            }
        }

        @Override
        boolean done() {
            int last = script()[script().length - 1][0];
            boolean over = engaged >= 0 && age - engaged > 8 + last + (kind == B_VIGIL ? 36 : 10);
            if (over) r.cooldown = 16 + r.getRandom().nextInt(16);
            return over;
        }
    }

    /** Dark Bond and Ashen Bond: a black tether from him and a white one from her, and both pull tight. */
    static final class Bond extends Move {
        Bond(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            RolandKin a = r.angelicaEntity();
            r.getNavigation().stop();
            if (t == null) return;
            r.faceTarget();
            Vec3 tc = t.getBoundingBox().getCenter();
            if (age % 3 == 0 && age <= 18) {
                streak(r, r.position().add(0, 1.2, 0), tc, 0x0A0A0A, 0.12f + age * 0.006f);
                if (a != null) streak(r, a.position().add(0, 1.2, 0), tc, 0xF4F4F4, 0.12f + age * 0.006f);
            }
            if (age == 18) {
                if (sees(r, tc) && r.distanceTo(t) < 16 && damage(r, t, 6)) t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), r);
                if (a != null && a.distanceTo(t) < 16 && damage(a, t, 6)) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), a);
                sound(level, tc, SoundEvents.CHAIN_HIT, 2.0f, 0.6f);
            }
        }

        @Override
        boolean done() {
            boolean over = age > 26;
            if (over) r.cooldown = 16;
            return over;
        }
    }

    /**
     * The Waltz in Black and the Waltz in White: they take their places either side of you, turn once, and pass
     * through you crossing, a black streak and a white one. Whatever's in your hand goes flying.
     */
    static final class Waltz extends Move {
        static final int SPIN = 24;
        Vec3 left, right;

        Waltz(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            RolandKin a = r.angelicaEntity();
            r.getNavigation().stop();
            if (t == null || a == null) return;
            if (age == 0) {
                double ang = r.getRandom().nextDouble() * Mth.TWO_PI;
                Vec3 side = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                left = t.position().add(side.scale(5));
                right = t.position().subtract(side.scale(5));
                if (!level.noCollision(r, r.getBoundingBox().move(left.subtract(r.position())))) left = t.position().add(side.scale(2.5));
                if (!level.noCollision(a, a.getBoundingBox().move(right.subtract(a.position())))) right = t.position().subtract(side.scale(2.5));
                r.teleportTo(left.x, left.y, left.z);
                a.teleportTo(right.x, right.y, right.z);
                a.hold(DURANDAL);
                r.hold(DURANDAL, -1);
                sound(level, t.position(), ModSounds.SLOWMO.get(), 2.0f, 0.8f);
            }
            if (age < SPIN) {
                // they turn in place, gathering black and white
                r.face(r.getYRot() + 30);
                a.setYRot(a.getYRot() - 30);
                a.yBodyRot = a.getYRot();
                dust(level, r.position().add(0, 1, 0), 0x0A0A0A, 6, 0.6, 2f);
                dust(level, a.position().add(0, 1, 0), 0xF8F8F8, 6, 0.6, 2f);
                if (age % 6 == 0) {
                    streak(r, left.add(0, 1, 0), right.add(0, 1, 0), 0x404040, 0.05f);
                }
            }
            if (age == SPIN) {
                Vec3 a0 = left.add(0, 1, 0), b0 = right.add(0, 1, 0);
                List<LivingEntity> hit = line(level, a0, b0, 1.3);
                for (LivingEntity e : hit) {
                    boolean landed = damage(r, e, 12);
                    landed |= damage(a, e, 12);
                    if (landed) disarm(e);
                }
                r.teleportTo(right.x, right.y, right.z);
                a.teleportTo(left.x, left.y, left.z);
                streak(r, a0.add(0, 0.2, 0), b0.add(0, 0.2, 0), 0x050505, 0.5f);
                streak(r, b0.add(0, -0.2, 0), a0.add(0, -0.2, 0), 0xFFFFFF, 0.5f);
                sound(level, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 3.0f, 0.5f);
                sound(level, t.position(), ModSounds.BIG_IMPACT.get(), 2.0f, 1.3f);
                r.swing(InteractionHand.MAIN_HAND);
                a.swing(InteractionHand.MAIN_HAND);
            }
        }

        @Override
        boolean done() {
            boolean over = age > SPIN + 14;
            if (over) r.cooldown = 30;
            return over;
        }
    }

    // =================================================================== phase 4: the weapons

    /** The weapons each scene brings up behind him (hammer, mace and axe, dagger / gauntlets and revolver, lance, longsword / dual blades, greatsword, shotgun). */
    static final int[][] SCENE_WEAPONS = {{HAMMER, AXE, KNIFE}, {GAUNTLETS, LANCE, KATANA}, {SHORTSWORDS, GREATSWORD, SHOTGUN}};

    static void scenes(Roland r, ServerLevel level, long now) {
        if (now < r.nextScene || r.move instanceof Furioso) return;
        r.nextScene = now + 200;
        // whatever wasn't broken is driven into the floor
        for (RolandKin w : kinOf(r, RolandKin.WEAPON)) if (!w.nailed()) w.nail();
        r.nailed = kinOf(r, RolandKin.WEAPON).size();
        r.scene++;
        if (r.scene >= 4) {
            r.scene = 0;
            int hits = 4 + 2 * r.nailed;
            r.note("weapons_furioso " + r.nailed);
            start(r, new Furioso(r, hits), "furioso_" + hits);
            r.background.add(new ClearNailed(r));
            return;
        }
        r.note("scene " + r.scene);
        int[] ws = SCENE_WEAPONS[r.scene - 1];
        Vec3 back = Vec3.directionFromRotation(0, r.getYRot()).scale(-2.2);
        Vec3 side = Vec3.directionFromRotation(0, r.getYRot() + 90);
        for (int i = 0; i < 3; i++) {
            RolandKin k = RolandKin.create(level, r, RolandKin.WEAPON, ws[i]);
            if (k == null) continue;
            Vec3 at = r.position().add(back).add(side.scale((i - 1) * 1.8)).add(0, 1.8 + (i == 1 ? 0.6 : 0), 0);
            k.moveTo(at.x, at.y, at.z, r.getYRot(), 0);
            level.addFreshEntity(k);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 16, 0.3, 0.5, 0.3, 0.02);
        }
        sound(level, r.position(), ModSounds.WEAPON_SWAP.get(), 2.0f, 0.7f);
    }

    /** After the Furioso the nailed weapons are pulled back into the smoke. */
    static final class ClearNailed extends Move {
        ClearNailed(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {}

        @Override
        boolean done() {
            if (r.move instanceof Furioso) return false;
            for (RolandKin w : kinOf(r, RolandKin.WEAPON)) {
                ((ServerLevel) r.level()).sendParticles(ParticleTypes.LARGE_SMOKE, w.getX(), w.getY() + 0.5, w.getZ(), 10, 0.2, 0.4, 0.2, 0.02);
                w.discard();
            }
            r.nailed = 0;
            return true;
        }
    }

    static List<RolandKin> kinOf(Roland r, int kind) {
        List<RolandKin> out = new ArrayList<>();
        if (!(r.level() instanceof ServerLevel level)) return out;
        for (UUID id : r.kin) if (level.getEntity(id) instanceof RolandKin k && k.isAlive() && k.kind() == kind) out.add(k);
        return out;
    }

    static final int M_DAWN = 0, M_LIU = 1, M_ZWEI = 2, M_LOVE = 3, M_PURPLE = 4, M_BLUE = 5, M_HANA = 6, M_RAGE = 7;
    static final int[] MEMORY_COLORS = {0xFF8A20, 0xFF4A10, 0x3A6AFF, 0xC02050, 0x8A2AD0, 0x2AA0FF, 0xF0F0F0, 0x202020};

    static void pickMemory(Roland r, LivingEntity t, double dist) {
        if (r.phase() == Roland.BYGONES) {
            // the Scream, then Rage, then Rage again
            int step = r.screamStep++ % 3;
            if (step == 0) start(r, new Scream(r), "scream");
            else start(r, new Memory(r, M_RAGE), "rage");
            return;
        }
        int m = r.getRandom().nextInt(8);
        if (m == M_LOVE && r.hp() < 0.3f) m = M_RAGE;
        start(r, new Memory(r, m), "memory_" + m);
    }

    /** A memory of someone he fought: each with its own colour of smoke. */
    static final class Memory extends Move {
        final int kind;
        int engaged = -1;

        Memory(Roland r, int kind) {
            super(r);
            this.kind = kind;
        }

        double range() {
            return switch (kind) {
                case M_DAWN, M_HANA -> 4;
                case M_LIU, M_BLUE -> 16;
                case M_LOVE -> 6;
                default -> 3.4;
            };
        }

        @Override
        void step(ServerLevel level) {
            LivingEntity t = r.target;
            int color = MEMORY_COLORS[kind];
            if (engaged < 0) {
                if (t != null && r.distanceTo(t) > range() && age < 40) {
                    if (age % 5 == 0) r.getNavigation().moveTo(t, 1.3);
                    return;
                }
                engaged = age;
                r.getNavigation().stop();
            }
            r.faceTarget();
            int k = age - engaged;
            if (k < 10) {
                dust(level, r.position().add(0, 1.2, 0), color, 5, 0.5, 1.8f);
                return;
            }
            if (t == null) return;
            switch (kind) {
                case M_DAWN -> {
                    // a ring of fire rolling out from him
                    if (k >= 10 && k <= 20) {
                        double rad = (k - 9) * 0.9;
                        for (int i = 0; i < 20; i++) {
                            double a = i * Mth.TWO_PI / 20;
                            level.sendParticles(ParticleTypes.FLAME, r.getX() + Math.cos(a) * rad, r.getY() + 0.3, r.getZ() + Math.sin(a) * rad, 1, 0.05, 0.1, 0.05, 0.01);
                        }
                        for (LivingEntity e : around(level, r.position(), rad + 0.6)) {
                            if (e.position().distanceTo(r.position()) > rad - 1.2 && damage(r, e, 7)) e.igniteForSeconds(4);
                        }
                    }
                }
                case M_LIU -> {
                    if (k == 10) r.fx(t.position(), t.position(), FX_SPIKE, 0);
                    if (k == 24) {
                        level.sendParticles(ParticleTypes.FLAME, t.getX(), t.getY() + 0.5, t.getZ(), 60, 1.4, 0.6, 1.4, 0.05);
                        r.fx(t.position(), t.position(), FX_SHOCK, 3, 0xFF5010);
                        for (LivingEntity e : around(level, t.position(), 3)) if (damage(r, e, 9)) e.igniteForSeconds(5);
                        sound(level, t.position(), SoundEvents.BLAZE_SHOOT, 2.0f, 0.6f);
                    }
                }
                case M_ZWEI, M_PURPLE -> {
                    if (k == 10) {
                        r.hold(DURANDAL, -1);
                        strike(r, level, DURANDAL, 1, t, true);
                        streak(r, r.getEyePosition(), t.getBoundingBox().getCenter(), color, 0.3f);
                        if (r.distanceTo(t) < 5) {
                            if (kind == M_ZWEI) {
                                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), r);
                                if (r.getRandom().nextFloat() < 0.35f) disarm(t);
                            } else t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), r);
                        }
                    }
                }
                case M_LOVE -> {
                    // he hurts himself for it, then the draw
                    if (k == 10) {
                        r.setHealth(Math.max(1, r.getHealth() - 8));
                        dust(level, r.position().add(0, 1, 0), 0xC02050, 30, 0.5, 2f);
                        sound(level, r.position(), SoundEvents.PLAYER_HURT, 1.5f, 0.6f);
                    }
                    if (k == 18) {
                        r.hold(KATANA, -1);
                        strike(r, level, KATANA, 1.4f, t, true);
                    }
                }
                case M_BLUE -> {
                    // blades of sound, one after another
                    if (k == 10 || k == 16 || k == 22) {
                        Vec3 from = r.getEyePosition(), to = t.getBoundingBox().getCenter();
                        streak(r, from, to, color, 0.25f);
                        if (sees(r, to) && damage(r, t, 5)) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), r);
                        sound(level, from, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.5f + k * 0.03f);
                    }
                }
                case M_HANA -> {
                    if (k == 12) {
                        r.fx(r.position(), r.position(), FX_SHOCK, 5, 0xF0F0F0);
                        for (LivingEntity e : around(level, r.position(), 4.5)) if (damage(r, e, 10)) {
                            knock(e, flat(e.position().subtract(r.position())).scale(0.5).add(0, 0.7, 0));
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), r);
                        }
                        sound(level, r.position(), ModSounds.KAIJU_STOMP.get(), 1.5f, 1.4f);
                    }
                }
                default -> {
                    // Rage, Agony, Sorrow, Conflict: plain, heavy blows
                    if (k == 10 || k == 16 || k == 22) strike(r, level, DURANDAL, 1, t, true);
                    if (k == 30) {
                        r.hold(GREATSWORD, -1);
                        strike(r, level, GREATSWORD, 1, t, true);
                    }
                }
            }
        }

        @Override
        boolean done() {
            int len = switch (kind) {
                case M_DAWN -> 24;
                case M_LIU -> 30;
                case M_LOVE -> 24;
                case M_BLUE -> 28;
                case M_HANA -> 18;
                case M_RAGE -> 36;
                default -> 16;
            };
            boolean over = engaged >= 0 && age - engaged > len;
            if (over) r.cooldown = 20 + r.getRandom().nextInt(20);
            return over;
        }
    }

    // =================================================================== phase 5: the Images of the Bygones

    static void enterBygones(Roland r, ServerLevel level) {
        r.setPhase(Roland.BYGONES);
        r.setHealth(r.getMaxHealth() * 0.5f);
        r.say(Roland.LINE_BYGONES);
        r.note("bygones");
        for (RolandKin w : kinOf(r, RolandKin.WEAPON)) w.discard();
        r.nailed = 0;
        r.readyUntil = level.getGameTime() + 400;
        r.setBodyPose(Roland.POSE_CROUCH);
        BlockPos c = r.center();
        for (int i = 0; i < 4; i++) {
            double a = i * Mth.HALF_PI + Mth.PI / 4;
            RolandKin k = RolandKin.create(level, r, RolandKin.IMAGE, i);
            if (k == null) continue;
            k.moveTo(c.getX() + 0.5 + Math.cos(a) * 8, LibraryArena.FLOOR + 1, c.getZ() + 0.5 + Math.sin(a) * 8, 0, 0);
            level.addFreshEntity(k);
            dust(level, k.position().add(0, 1, 0), 0xD8D8E0, 40, 0.5, 2f);
        }
    }

    /** Ready to Act: crouched in his smoke, untouchable, while the images fight. True while it lasts. */
    static boolean readyToAct(Roland r, ServerLevel level, long now) {
        if (r.readyUntil < 0) return false;
        r.getNavigation().stop();
        r.setBodyPose(Roland.POSE_CROUCH);
        if (now % 3 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 0.8, r.getZ(), 3, 0.4, 0.5, 0.4, 0.01);
        if (now < r.readyUntil && !kinOf(r, RolandKin.IMAGE).isEmpty()) return true;
        r.readyUntil = -1;
        r.setBodyPose(Roland.POSE_STAND);
        r.screamStep = 0;
        r.move = null;
        r.cooldown = 0;
        r.note("act");
        return false;
    }

    /** The Scream: a black shockwave across the whole floor. It blinds you and tears your weapon from your hand. */
    static final class Scream extends Move {
        static final int AT = 22;

        Scream(Roland r) {
            super(r);
        }

        @Override
        void step(ServerLevel level) {
            r.getNavigation().stop();
            if (age == 0) {
                r.setBodyPose(Roland.POSE_CROUCH);
                r.say(Roland.LINE_SCREAM);
                sound(level, r.position(), SoundEvents.WARDEN_SONIC_CHARGE, 3.0f, 0.6f);
            }
            if (age < AT) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1, r.getZ(), 4, 0.6 + age * 0.05, 0.6, 0.6 + age * 0.05, 0.02);
                return;
            }
            if (age == AT) {
                r.setBodyPose(Roland.POSE_STAND);
                r.fx(r.position(), r.position(), FX_TITLE, TITLE_SCREAM);
                r.fx(r.position().add(0, 1, 0), r.position(), FX_SHOCK, 26, 0x080808);
                sound(level, r.position(), SoundEvents.WARDEN_SONIC_BOOM, 4.0f, 0.5f);
                sound(level, r.position(), SoundEvents.WITHER_SPAWN, 2.0f, 0.6f);
                for (LivingEntity e : around(level, r.position(), 26)) {
                    if (!damage(r, e, 14)) continue;
                    knock(e, flat(e.position().subtract(r.position())).scale(1.4).add(0, 0.4, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), r);
                    disarm(e);
                }
            }
        }

        @Override
        boolean done() {
            boolean over = age > AT + 16;
            if (over) r.cooldown = 30;
            return over;
        }
    }

    // =================================================================== changing phase

    static void onHurt(Roland r, ServerLevel level, float amount) {
        byte p = r.phase();
        float min = Math.max(2, r.getMaxHealth() * 0.02f);
        switch (p) {
            case Roland.BLACK_SILENCE, Roland.DISTORTED -> {
                if (r.getHealth() <= min) r.beginShift(level);
            }
            case Roland.BOND -> {
                if (r.getHealth() <= min && !r.fallen()) fall(r);
            }
            case Roland.WEAPONS -> {
                if (r.hp() <= 0.5f) enterBygones(r, level);
            }
            default -> {}
        }
    }

    static boolean canBeHurt(Roland r, DamageSource source) {
        if (r.fallen() || r.readyUntil >= 0) return false;
        return true;
    }

    static float adjustIncoming(Roland r, DamageSource source, float amount) {
        if (r.bodyPose() == Roland.POSE_GUARD) amount *= 0.3f;
        if (r.bodyPose() == Roland.POSE_PARALYSED) amount *= 1.5f;
        if (source.getEntity() instanceof Player p && r.haze.getOrDefault(p.getUUID(), 0) > 0) amount = Math.max(0, amount - 2);
        // Aspiration guards him while it lasts
        for (RolandKin k : kinOf(r, RolandKin.IMAGE)) if (k.variant() == RolandKin.ASPIRATION) amount *= 0.5f;
        return amount;
    }

    static void shiftStart(Roland r, ServerLevel level, byte from) {
        switch (from) {
            case Roland.BLACK_SILENCE -> {
                r.say(Roland.LINE_DISTORT);
                r.setBodyPose(Roland.POSE_CROUCH);
                r.hold(-1, -1);
            }
            case Roland.DISTORTED -> {
                r.setBodyPose(Roland.POSE_CROUCH);
                r.haze.clear();
                r.hazeNext.clear();
                r.fragile.clear();
                for (RolandKin k : kinOf(r, RolandKin.BYPRODUCT)) k.discard();
                sound(level, r.position(), SoundEvents.WARDEN_DEATH, 3.0f, 0.6f);
            }
            case Roland.BOND -> {
                RolandKin a = r.angelicaEntity();
                if (a != null) {
                    a.say(ANGELICA_LINE_GONE);
                    dust(level, a.position().add(0, 1, 0), 0xFFFFFF, 80, 0.7, 2.5f);
                    level.sendParticles(ParticleTypes.END_ROD, a.getX(), a.getY() + 1, a.getZ(), 40, 0.5, 1, 0.5, 0.05);
                    a.discard();
                }
                r.fallenUntil = -1;
                r.setInvisible(false);
                r.setBodyPose(Roland.POSE_CROUCH);
                r.say(Roland.LINE_FALL);
            }
            default -> {}
        }
    }

    static void shiftTick(Roland r, ServerLevel level, byte from, int t) {
        Vec3 c = r.position().add(0, 1, 0);
        int slot = r.slot;
        switch (from) {
            case Roland.BLACK_SILENCE -> {
                // the distortion: smoke pours off him, his heart pounds, the screen goes black, and the beast is out
                if (t % 2 == 0 && t < 70) level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 4 + t / 8, 0.4 + t * 0.01, 0.6, 0.4 + t * 0.01, 0.02);
                if (t == 15 || t == 30 || t == 42 || t == 52) {
                    sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 3.0f, 0.6f);
                    r.fx(c, c, FX_SHOCK, 4 + t / 10, 0x8A0A0A);
                }
                if (t == 60) for (ServerPlayer p : r.guests()) Network.sendTo(p, ArsenalFxPayload.of(ArsenalFxPayload.ROLAND, c, c, FX_FADE, 0, 40));
                if (t == 72 && slot >= 0) {
                    List<ServerPlayer> guests = r.guests();
                    r.setPhase(Roland.DISTORTED);
                    r.setHealth(r.getMaxHealth());
                    Vec3 at = LibraryArena.factoryRoland(slot);
                    r.teleportTo(at.x, at.y, at.z);
                    r.face(0);
                    for (int i = 0; i < guests.size(); i++) {
                        Vec3 g = LibraryArena.factoryGuest(slot, i);
                        guests.get(i).teleportTo(level, g.x, g.y, g.z, 180, 0);
                    }
                    r.setBodyPose(Roland.POSE_CROUCH);
                } else if (t == 72) r.setPhase(Roland.DISTORTED);
                if (t == 82) {
                    r.setBodyPose(Roland.POSE_STAND);
                    sound(level, r.position(), SoundEvents.WARDEN_ROAR, 4.0f, 0.5f);
                    sound(level, r.position(), SoundEvents.RAVAGER_ROAR, 3.0f, 0.4f);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1.5, r.getZ(), 80, 1.5, 1.2, 1.5, 0.05);
                    r.fx(r.position().add(0, 1, 0), r.position(), FX_SHOCK, 14, 0x5A0A0A);
                }
                if (t == 95) spawnByproducts(r, 4);
            }
            case Roland.DISTORTED -> {
                // the beast falls apart into smoke, the world goes white, and he gathers himself back together
                if (t % 2 == 0 && t < 55) level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 8, 1.4, 1.0, 1.4, 0.03);
                if (t == 45) for (ServerPlayer p : r.guests()) Network.sendTo(p, ArsenalFxPayload.of(ArsenalFxPayload.ROLAND, c, c, FX_FADE, 1, 40));
                if (t == 58) {
                    List<ServerPlayer> guests = r.guests();
                    r.setPhase(Roland.BOND);
                    if (slot >= 0) {
                        BlockPos lc = LibraryArena.center(slot);
                        r.teleportTo(lc.getX() + 0.5, LibraryArena.FLOOR + 1, lc.getZ() - 5.5);
                        r.face(0);
                        for (int i = 0; i < guests.size(); i++) {
                            Vec3 g = LibraryArena.libraryGuest(slot, i);
                            guests.get(i).teleportTo(level, g.x, g.y, g.z, 180, 0);
                        }
                    }
                    r.setBodyPose(Roland.POSE_CROUCH);
                    r.links = 4;
                }
                if (t == 70) {
                    r.setBodyPose(Roland.POSE_STAND);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1, r.getZ(), 40, 0.4, 1, 0.4, 0.03);
                    r.say(Roland.LINE_SETTLE);
                }
                if (t == 80) {
                    spawnAngelica(r);
                    sound(level, r.position(), SoundEvents.SOUL_ESCAPE.value(), 3.0f, 0.7f);
                }
                if (t == 96) {
                    RolandKin a = r.angelicaEntity();
                    if (a != null) a.say(ANGELICA_LINE_HERE);
                }
            }
            case Roland.BOND -> {
                if (t % 3 == 0 && t < 60) level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 3, 0.4, 0.6, 0.4, 0.01);
                if (t == 60) {
                    r.setPhase(Roland.WEAPONS);
                    r.say(Roland.LINE_WEAPONS);
                    r.scene = 0;
                    r.nextScene = level.getGameTime() + 80;
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 1, r.getZ(), 60, 0.5, 1, 0.5, 0.04);
                }
            }
            default -> {}
        }
    }

    // =================================================================== what his phases do to your blows and his

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !LibraryArena.inLibrary(p.level())) return;
        Entity by = event.getSource().getEntity();
        Roland r = by instanceof Roland ro ? ro : by instanceof RolandKin k ? k.owner() : null;
        if (r == null) return;
        long now = p.level().getGameTime();
        if (r.haze.getOrDefault(p.getUUID(), 0) > 0) event.setAmount(event.getAmount() + 2);
        Long frag = r.fragile.get(p.getUUID());
        if (frag != null && frag > now) event.setAmount(event.getAmount() * 1.25f);
    }

    static void sayAs(Entity e, int speaker, int line) {
        Network.sendNear((ServerLevel) e.level(), e.position(), 96, FxPayload.at(FxPayload.BOSS_SAY, e.getEyePosition()).to(new Vec3(speaker, 0, 0)).data(line));
    }

}
