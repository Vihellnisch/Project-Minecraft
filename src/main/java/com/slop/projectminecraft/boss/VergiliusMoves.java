package com.slop.projectminecraft.boss;

import com.slop.projectminecraft.registry.ModDamageTypes;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything Vergilius does, one move at a time (Vergilius.move), each a small timeline; nothing here touches a block.
 * <ul>
 * <li>Heated Puncture: a lunge and a thrust that sets you alight. Scorching Incision: three burning cuts.</li>
 * <li>Following the Flow: a stance; strike him in it and he turns it aside and answers. I Shall Open the Path: a line shows,
 * he cuts through you along it, and a moment later the whole line splits open.</li>
 * <li>Red Gaze (every so often): his eyes flare; still in his sight when they do, you're slowed and weakened.</li>
 * <li>At half health, his E.G.O (Lavacrum Sanguinis): a lake of blood round him, and with it Internal Bleeding (spikes up
 * under you, one after another), Blinding Bloodcleaver (a huge overhead cut), Drown in Blood (pulled to the middle, then
 * the whole lake spikes), Blood Barrier (the next three hits do nothing), and from a quarter health Funeral for a Dead
 * Bloodfiend: perfectly still, a storm of blood spears, the last cut.</li>
 * </ul>
 * Beside you (the rematch, or following you) he uses the gladius only, for less, and never on a player.
 */
public final class VergiliusMoves {
    private VergiliusMoves() {}

    /** ArsenalFxPayload.VERGILIUS sub-kinds (ints[0]); ints[1] is his id. */
    public static final int FX_INTRO = 1, FX_CHOICE = 2, FX_SLASH = 3, FX_THRUST = 4, FX_LUNGE = 5, FX_COUNTER = 6, FX_PARRY = 7, FX_PATH_LINE = 8,
            FX_PATH_CUT = 9, FX_GAZE = 10, FX_GAZE_HIT = 11, FX_MANIFEST = 12, FX_LAKE_OFF = 13, FX_SPIKE_WARN = 14, FX_SPIKE = 15, FX_CLEAVER_WIND = 16,
            FX_CLEAVER = 17, FX_DROWN_PULL = 18, FX_DROWN = 19, FX_BARRIER = 20, FX_BARRIER_BREAK = 21, FX_FUNERAL = 22, FX_FUNERAL_SPEAR = 23,
            FX_FUNERAL_CUT = 24, FX_BUS = 25, FX_DOWNED = 26;
    /** Cooldown slots (Vergilius.cd). */
    static final int CD_PUNCTURE = 0, CD_INCISION = 1, CD_FLOW = 2, CD_PATH = 3, CD_GAZE = 4, CD_BLEED = 5, CD_CLEAVER = 6, CD_DROWN = 7,
            CD_BARRIER = 8, CD_FUNERAL = 9;
    static final float LAKE_RADIUS = 9;
    /** Beside you, his blows on the Claw land this much harder. */
    static final float ALLY_VS_CLAW = 1.5f;

    /** Boss damage of each hit, before Vergilius.dmg(). */
    static final class Damage {
        static final float PUNCTURE = 9, INCISION = 6, RIPOSTE = 12, PATH_THROUGH = 8, PATH_SPLIT = 14, SPIKE = 8, CLEAVER = 18, DROWN = 14,
                SPEAR = 3, FUNERAL_CUT = 20;
    }

    abstract static class Move {
        final Vergilius v;
        final LivingEntity t;
        int age = -1;

        Move(Vergilius v, LivingEntity t) {
            this.v = v;
            this.t = t;
        }

        void tick() {
            age++;
            step((ServerLevel) v.level());
        }

        abstract void step(ServerLevel level);

        abstract boolean done();

        boolean immune() {
            return false;
        }

        /** Called when it's cut short (he was beaten, downed, the fight ended). */
        void stop() {}
    }

    static void start(Vergilius v, Move m, String name) {
        v.move = m;
        v.note(name);
    }

    /** Whatever he was doing, dropped. */
    static void drop(Vergilius v) {
        if (v.move != null) v.move.stop();
        v.move = null;
        v.setBodyPose(Vergilius.POSE_STAND);
        v.setGaze(0);
    }

    // =================================================================== the brain

    static void tick(Vergilius v, ServerLevel level, LivingEntity t) {
        for (int i = 0; i < v.cd.length; i++) if (v.cd[i] > 0) v.cd[i]--;
        if (v.move != null && (v.move.t != t || !t.isAlive())) drop(v);
        if (v.move != null) {
            // a move can end everything mid-tick (its blow beats the Claw: he's a companion now, the move dropped)
            Move m = v.move;
            m.tick();
            if (v.move != m || !m.done()) return;
            v.move = null;
            v.setBodyPose(Vergilius.POSE_STAND);
        }
        v.getLookControl().setLookAt(t, 30, 30);
        double dist = v.distanceTo(t);
        boolean boss = v.state() == Vergilius.FIGHT, ally = v.state() == Vergilius.ALLY;
        // beside you: his E.G.O once the Claw is under half, and Funeral to finish him
        if (ally && t instanceof com.slop.projectminecraft.boss.Claw c) {
            if (!v.manifested && c.hp() <= 0.5f) {
                v.manifested = true;
                start(v, new Manifest(v, t), "manifest");
                return;
            }
            if (v.ego() && !v.allyFuneral && c.hp() <= 0.2f) {
                v.allyFuneral = true;
                start(v, new Funeral(v, t), "funeral");
                return;
            }
        }
        // the E.G.O at half health (as a boss), once
        if (boss && !v.manifested && v.hp() <= 0.5f) {
            v.manifested = true;
            start(v, new Manifest(v, t), "manifest");
            return;
        }
        if (boss && v.ego() && v.hp() <= 0.25f && v.cd[CD_FUNERAL] == 0) {
            v.cd[CD_FUNERAL] = 600;
            start(v, new Funeral(v, t), "funeral");
            return;
        }
        if (v.cooldown > 0) {
            v.cooldown--;
            approach(v, t, dist);
            return;
        }
        var r = v.getRandom();
        if (v.cd[CD_GAZE] == 0 && v.hasLineOfSight(t) && r.nextFloat() < 0.5f) {
            v.cd[CD_GAZE] = 240;
            start(v, new Gaze(v, t), "red_gaze");
        } else if (boss && v.ego() && v.cd[CD_BARRIER] == 0 && v.hp() < 0.45f && r.nextFloat() < 0.4f) {
            v.cd[CD_BARRIER] = 400;
            start(v, new Barrier(v, t), "blood_barrier");
        } else if ((boss || ally) && v.ego() && v.cd[CD_DROWN] == 0 && v.lake != null && t.position().distanceTo(v.lake) < LAKE_RADIUS && r.nextFloat() < 0.4f) {
            v.cd[CD_DROWN] = 360;
            start(v, new Drown(v, t), "drown_in_blood");
        } else if ((boss || ally) && v.ego() && v.cd[CD_BLEED] == 0 && dist > 3 && r.nextFloat() < 0.5f) {
            v.cd[CD_BLEED] = 160;
            start(v, new Bleed(v, t), "internal_bleeding");
        } else if ((boss || ally) && v.ego() && v.cd[CD_CLEAVER] == 0 && dist < 7 && r.nextFloat() < 0.45f) {
            v.cd[CD_CLEAVER] = 200;
            start(v, new Cleaver(v, t), "blinding_bloodcleaver");
        } else if (v.cd[CD_PATH] == 0 && dist > 4 && dist < 14 && r.nextFloat() < 0.45f) {
            v.cd[CD_PATH] = 220;
            start(v, new OpenPath(v, t), "open_the_path");
        } else if (boss && v.cd[CD_FLOW] == 0 && dist < 6 && r.nextFloat() < 0.3f) {
            v.cd[CD_FLOW] = 200;
            start(v, new Flow(v, t), "following_the_flow");
        } else if (v.cd[CD_PUNCTURE] == 0 && dist > 2.5 && dist < 9) {
            v.cd[CD_PUNCTURE] = 50;
            start(v, new Puncture(v, t), "heated_puncture");
        } else if (dist < 4) {
            v.cd[CD_INCISION] = 40;
            start(v, new Incision(v, t), "scorching_incision");
        } else {
            approach(v, t, dist);
            return;
        }
        v.cooldown = boss ? 12 + r.nextInt(14) : v.state() == Vergilius.ALLY ? 8 + r.nextInt(8) : 18 + r.nextInt(14);
    }

    static void approach(Vergilius v, LivingEntity t, double dist) {
        if (dist > 3) v.getNavigation().moveTo(t, dist > 8 ? 1.25 : 1.0);
        else v.getNavigation().stop();
    }

    static void sound(Vergilius v, SoundEvent s, float volume, float pitch) {
        v.level().playSound(null, v.getX(), v.getY() + 1, v.getZ(), s, SoundSource.HOSTILE, volume, pitch);
    }

    /** Can he hurt `e`? Not himself; beside you, not you, another player or your pets. */
    static boolean canHit(Vergilius v, LivingEntity e) {
        if (e == v || !e.isAlive()) return false;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return false;
        if (v.friendly()) {
            if (e instanceof Player || e instanceof Vergilius) return false;
            if (e instanceof TamableAnimal pet && pet.getOwnerUUID() != null && (pet.getOwnerUUID().equals(v.owner) || pet.getOwnerUUID().equals(v.quarry))) return false;
        }
        return true;
    }

    static boolean hit(Vergilius v, LivingEntity e, float amount, boolean throughArmour, boolean burn) {
        if (!canHit(v, e)) return false;
        e.invulnerableTime = 0;
        DamageSource src = throughArmour ? ModDamageTypes.source(v.level(), ModDamageTypes.CLAW_WARP, v, v) : v.damageSources().mobAttack(v);
        float k = v.state() == Vergilius.ALLY && e instanceof com.slop.projectminecraft.boss.Claw ? ALLY_VS_CLAW : 1;
        boolean ok = e.hurt(src, amount * v.dmg() * k);
        if (ok && burn) {
            e.igniteForSeconds(3);
            // the Claw won't catch fire from anything else; from this blade, he burns
            if (v.state() == Vergilius.ALLY && e instanceof com.slop.projectminecraft.boss.Claw) v.clawBurn = Math.max(v.clawBurn, 60);
        }
        return ok;
    }

    /** Everything he can hurt in a cone in front of him. */
    static List<LivingEntity> inFront(Vergilius v, double range, double minDot) {
        Vec3 eye = v.position().add(0, 1, 0), look = Vec3.directionFromRotation(0, v.getYRot());
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : v.level().getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(range), e -> canHit(v, e))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye).multiply(1, 0, 1);
            if (d.length() > range + e.getBbWidth() / 2) continue;
            if (d.lengthSqr() > 1e-3 && d.normalize().dot(look) < minDot) continue;
            out.add(e);
        }
        return out;
    }

    /** Everything he can hurt within `r` of `at`. */
    static List<LivingEntity> near(Vergilius v, Vec3 at, double r) {
        return v.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 2.5, r), e -> canHit(v, e) && e.position().distanceTo(at) < r + e.getBbWidth() / 2);
    }

    /** As far as `want` along a straight line from `from` before a wall, and somewhere he fits. */
    static Vec3 reach(Vergilius v, Vec3 from, Vec3 want) {
        ServerLevel level = (ServerLevel) v.level();
        var hr = level.clip(new ClipContext(from.add(0, 1, 0), want.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v));
        Vec3 to = hr.getType() == HitResult.Type.MISS ? want : hr.getLocation().subtract(0, 1, 0).subtract(want.subtract(from).normalize().scale(0.7));
        for (int i = 0; i <= 6; i++) {
            Vec3 at = to.lerp(from, i / 6.0);
            for (int dy : new int[]{0, 1, -1}) {
                Vec3 q = new Vec3(at.x, Math.floor(at.y) + dy, at.z);
                if (level.noCollision(v, v.getBoundingBox().move(q.subtract(v.position())))) return q;
            }
        }
        return from;
    }

    static int ego(Vergilius v) {
        return v.ego() ? 1 : 0;
    }

    // ---- Heated Puncture: a lunge and a burning thrust
    static final class Puncture extends Move {
        Puncture(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age < 8) v.face(t.position());
            if (age == 2) v.setBodyPose(Vergilius.POSE_THRUST);
            if (age == 8) {
                Vec3 from = v.position(), dir = t.position().subtract(from).multiply(1, 0, 1).normalize();
                Vec3 to = reach(v, from, t.position().subtract(dir.scale(1.4)));
                v.teleportTo(to.x, to.y, to.z);
                v.face(t.position());
                v.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_LUNGE, v.getId(), ego(v));
                sound(v, ModSounds.SPEAR.get(), 1.4f, 1.2f);
                sound(v, ModSounds.VERG_SIZZLE.get(), 1.0f, 1.0f);
                for (LivingEntity e : inFront(v, 3.4, 0.6)) hit(v, e, Damage.PUNCTURE, false, true);
                v.fx(v.position().add(0, 1.2, 0), Vec3.directionFromRotation(0, v.getYRot()), FX_THRUST, v.getId(), ego(v));
            }
            if (age == 16) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 22;
        }
    }

    // ---- Scorching Incision: three burning cuts
    static final class Incision extends Move {
        Incision(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age < 20) v.face(t.position());
            if (age == 1 || age == 9 || age == 17) v.setBodyPose(Vergilius.POSE_SLASH);
            if (age == 4 || age == 12 || age == 20) {
                int n = (age - 4) / 8;
                // a step in with each cut
                Vec3 f = Vec3.directionFromRotation(0, v.getYRot());
                if (v.distanceTo(t) > 2.2) v.setDeltaMovement(f.scale(0.35).add(0, v.getDeltaMovement().y, 0));
                v.fx(v.position().add(0, 1.2, 0), f, FX_SLASH, v.getId(), n % 2 == 0 ? 1 : -1, ego(v), n);
                sound(v, ModSounds.VERG_SLASH.get(), 1.4f, 0.9f + n * 0.12f);
                for (LivingEntity e : inFront(v, 3.8, 0.15)) hit(v, e, Damage.INCISION, false, true);
            }
            if (age == 24) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 30;
        }
    }

    // ---- Following the Flow: a stance that turns a blow aside and answers it
    static final class Flow extends Move {
        boolean answered;

        Flow(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age == 0) {
                v.setBodyPose(Vergilius.POSE_COUNTER);
                v.fx(v.position().add(0, 1.2, 0), Vec3.ZERO, FX_COUNTER, v.getId(), 1);
                sound(v, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2f, 1.4f);
            }
            if (!answered) v.face(t.position());
            if (age == 30 && !answered) v.fx(v.position().add(0, 1.2, 0), Vec3.ZERO, FX_COUNTER, v.getId(), 0);
        }

        /** Struck in the stance: turned aside; behind them and a cut. */
        void answer(LivingEntity by) {
            answered = true;
            v.fx(v.position().add(0, 1.2, 0), Vec3.ZERO, FX_PARRY, v.getId());
            sound(v, SoundEvents.SHIELD_BLOCK, 1.6f, 0.7f);
            sound(v, SoundEvents.ANVIL_LAND, 0.5f, 1.8f);
            Vec3 from = v.position(), behind = by.position().add(Vec3.directionFromRotation(0, by.getYRot()).scale(-1.6));
            Vec3 to = reach(v, from, behind);
            v.teleportTo(to.x, to.y, to.z);
            v.face(by.position());
            v.setBodyPose(Vergilius.POSE_SLASH);
            v.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_LUNGE, v.getId(), ego(v));
            v.fx(v.position().add(0, 1.2, 0), Vec3.directionFromRotation(0, v.getYRot()), FX_SLASH, v.getId(), 1, ego(v), 2);
            sound(v, ModSounds.VERG_SLASH.get(), 1.6f, 0.8f);
            if (by instanceof LivingEntity le) hit(v, le, Damage.RIPOSTE, false, true);
            age = Math.max(age, 30);
        }

        boolean open() {
            return !answered && age < 30;
        }

        @Override
        boolean done() {
            return age >= 40;
        }
    }

    // ---- I Shall Open the Path: the line, the cut through, the line splitting open
    static final class OpenPath extends Move {
        Vec3 from, to;
        final List<Entity> struck = new ArrayList<>();

        OpenPath(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age == 0) {
                v.face(t.position());
                v.setBodyPose(Vergilius.POSE_COUNTER);
                from = v.position();
                Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, v.getYRot()) : dir.normalize();
                to = reach(v, from, t.position().add(dir.scale(4)));
                v.fx(from.add(0, 0.1, 0), to.add(0, 0.1, 0), FX_PATH_LINE, v.getId(), 20);
                sound(v, ModSounds.VERG_SIZZLE.get(), 1.2f, 0.7f);
            }
            if (age >= 20 && age < 24) {
                if (age == 20) {
                    v.setBodyPose(Vergilius.POSE_DASH);
                    sound(v, ModSounds.VERG_SLASH.get(), 1.8f, 1.2f);
                }
                Vec3 a = from.lerp(to, (age - 20) / 4.0), b = from.lerp(to, (age - 19) / 4.0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.2, 2, 1.2), e -> canHit(v, e) && !struck.contains(e))) {
                    struck.add(e);
                    hit(v, e, Damage.PATH_THROUGH, false, true);
                }
                v.teleportTo(b.x, b.y, b.z);
                if (age == 23) v.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_LUNGE, v.getId(), ego(v));
            }
            if (age == 24) v.setBodyPose(Vergilius.POSE_STAND);
            if (age == 34) {
                // the path splits open behind him, all along its length
                v.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_PATH_CUT, v.getId(), ego(v));
                sound(v, ModSounds.VERG_SLASH.get(), 2.0f, 0.6f);
                sound(v, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
                Vec3 d = to.subtract(from);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.4, 2, 1.4), e -> canHit(v, e))) {
                    // close to the line itself
                    Vec3 p = e.position().subtract(from);
                    double k = Mth.clamp(p.dot(d) / Math.max(1e-3, d.lengthSqr()), 0, 1);
                    if (from.add(d.scale(k)).distanceTo(e.position()) < 1.6) hit(v, e, Damage.PATH_SPLIT, false, true);
                }
            }
        }

        @Override
        boolean done() {
            return age >= 40;
        }
    }

    // ---- Red Gaze: his eyes flare
    static final class Gaze extends Move {
        Gaze(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            v.face(t.position());
            v.setGaze(Math.min(1, age / 16f));
            if (age == 0) {
                v.fx(v.getEyePosition(), Vec3.ZERO, FX_GAZE, v.getId(), t.getId(), 16);
                sound(v, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.8f);
            }
            if (age == 16) {
                if (v.hasLineOfSight(t) && canHit(v, t)) {
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), v);
                    t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), v);
                    v.fx(t.getEyePosition(), Vec3.ZERO, FX_GAZE_HIT, v.getId(), t.getId());
                    sound(v, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
                }
            }
        }

        @Override
        boolean done() {
            return age >= 24;
        }
    }

    // ---- the E.G.O: Lavacrum Sanguinis
    static final class Manifest extends Move {
        Manifest(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age == 0) {
                v.lake = v.position();
                v.setBodyPose(Vergilius.POSE_STILL);
                // [kind, him, lake radius x10]
                v.fx(v.position(), Vec3.ZERO, FX_MANIFEST, v.getId(), Math.round(LAKE_RADIUS * 10));
                sound(v, ModSounds.VERG_LAKE.get(), 2.5f, 0.8f);
            }
            if (age == 30) {
                v.setEgo(true);
                v.say(v.state() == Vergilius.ALLY ? Vergilius.LINE_ALLY_EGO : Vergilius.LINE_MANIFEST);
                sound(v, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 0.7f);
            }
            if (age == 60) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 70;
        }

        @Override
        boolean immune() {
            return true;
        }
    }

    // ---- Internal Bleeding: spikes up under you, one after another along a line
    static final class Bleed extends Move {
        final List<Vec3> spots = new ArrayList<>();

        Bleed(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age == 0) {
                v.face(t.position());
                v.setBodyPose(Vergilius.POSE_OVERHEAD);
                Vec3 from = v.position(), dir = t.position().subtract(from).multiply(1, 0, 1).normalize();
                for (int i = 0; i < 6; i++) spots.add(from.add(dir.scale(2.5 + i * 1.8)));
                // the last two on wherever you were standing
                spots.set(4, t.position());
                spots.set(5, t.position().add(dir.scale(1.6)));
                sound(v, ModSounds.VERG_LAKE.get(), 1.2f, 1.4f);
            }
            for (int i = 0; i < spots.size(); i++) {
                int warnAt = 4 + i * 3, hitAt = warnAt + 10;
                Vec3 s = spots.get(i);
                if (age == warnAt) v.fx(s, Vec3.ZERO, FX_SPIKE_WARN, v.getId(), 10);
                if (age == hitAt) {
                    v.fx(s, Vec3.ZERO, FX_SPIKE, v.getId());
                    sound(v, ModSounds.VERG_SPIKE.get(), 1.3f, 0.9f + i * 0.05f);
                    for (LivingEntity e : near(v, s, 1.3)) {
                        if (hit(v, e, Damage.SPIKE, false, false)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                            e.hurtMarked = true;
                        }
                    }
                }
            }
            if (age == 18) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 4 + 6 * 3 + 12;
        }
    }

    // ---- Blinding Bloodcleaver: a huge overhead cut
    static final class Cleaver extends Move {
        Cleaver(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age < 16) v.face(t.position());
            if (age == 0) {
                v.setBodyPose(Vergilius.POSE_OVERHEAD);
                v.fx(v.position().add(0, 2.4, 0), Vec3.ZERO, FX_CLEAVER_WIND, v.getId(), 18);
                sound(v, ModSounds.VERG_LAKE.get(), 1.4f, 1.6f);
            }
            if (age == 18) {
                v.setBodyPose(Vergilius.POSE_SLASH);
                Vec3 f = Vec3.directionFromRotation(0, v.getYRot());
                v.fx(v.position().add(0, 1.2, 0), f, FX_CLEAVER, v.getId());
                sound(v, ModSounds.VERG_SLASH.get(), 2.5f, 0.5f);
                sound(v, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.8f);
                for (LivingEntity e : inFront(v, 7.5, 0.25)) {
                    if (hit(v, e, Damage.CLEAVER, false, false)) {
                        e.setDeltaMovement(f.scale(0.9).add(0, 0.35, 0));
                        e.hurtMarked = true;
                    }
                }
            }
            if (age == 26) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 32;
        }
    }

    // ---- Drown in Blood: dragged to the middle of the lake, then all of it spikes
    static final class Drown extends Move {
        Drown(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            Vec3 c = v.lake == null ? v.position() : v.lake;
            if (age == 0) {
                v.setBodyPose(Vergilius.POSE_STILL);
                v.fx(c, Vec3.ZERO, FX_DROWN_PULL, v.getId(), 30);
                sound(v, ModSounds.VERG_LAKE.get(), 2.0f, 0.6f);
            }
            if (age < 30) {
                for (LivingEntity e : near(v, c, LAKE_RADIUS)) {
                    Vec3 pull = c.subtract(e.position()).multiply(1, 0, 1);
                    if (pull.lengthSqr() < 1) continue;
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.normalize().scale(0.16)));
                    e.hurtMarked = true;
                }
            }
            if (age == 30) v.fx(c, Vec3.ZERO, FX_SPIKE_WARN, v.getId(), 16, Math.round(LAKE_RADIUS * 10));
            if (age == 46) {
                v.fx(c, Vec3.ZERO, FX_DROWN, v.getId(), Math.round(LAKE_RADIUS * 10));
                sound(v, ModSounds.VERG_SPIKE.get(), 2.5f, 0.6f);
                sound(v, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.7f);
                for (LivingEntity e : near(v, c, LAKE_RADIUS)) {
                    if (hit(v, e, Damage.DROWN, false, false)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                        e.hurtMarked = true;
                    }
                }
                v.setBodyPose(Vergilius.POSE_STAND);
            }
        }

        @Override
        boolean done() {
            return age >= 56;
        }
    }

    // ---- Blood Barrier: the next three hits do nothing
    static final class Barrier extends Move {
        Barrier(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            if (age == 0) {
                v.barrierHits = 3;
                v.setBarrier(true);
                v.setBodyPose(Vergilius.POSE_COUNTER);
                v.fx(v.position().add(0, 1, 0), Vec3.ZERO, FX_BARRIER, v.getId());
                sound(v, ModSounds.VERG_LAKE.get(), 1.4f, 1.2f);
            }
            if (age == 12) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 14;
        }
    }

    // ---- Funeral for a Dead Bloodfiend
    static final class Funeral extends Move {
        Funeral(Vergilius v, LivingEntity t) {
            super(v, t);
        }

        @Override
        void step(ServerLevel level) {
            v.getNavigation().stop();
            Vec3 c = v.lake == null ? v.position() : v.lake;
            if (age == 0) {
                v.setBodyPose(Vergilius.POSE_STILL);
                v.say(Vergilius.LINE_FUNERAL);
                v.fx(c, Vec3.ZERO, FX_FUNERAL, v.getId(), Math.round(LAKE_RADIUS * 10));
                sound(v, ModSounds.VERG_LAKE.get(), 3.0f, 0.5f);
                sound(v, SoundEvents.BELL_RESONATE, 2.0f, 0.5f);
            }
            // the storm of spears, falling round wherever you are
            if (age >= 30 && age < 80 && age % 3 == 0) {
                var r = v.getRandom();
                Vec3 s = t.position().add(r.nextGaussian() * 1.8, 0, r.nextGaussian() * 1.8);
                v.fx(s, Vec3.ZERO, FX_FUNERAL_SPEAR, v.getId());
                if (age % 6 == 0) sound(v, ModSounds.VERG_SPIKE.get(), 1.0f, 1.2f + r.nextFloat() * 0.3f);
                for (LivingEntity e : near(v, s, 1.2)) hit(v, e, Damage.SPEAR, false, false);
            }
            if (age == 92) {
                v.setBodyPose(Vergilius.POSE_SLASH);
                v.fx(c, Vec3.directionFromRotation(0, v.getYRot()), FX_FUNERAL_CUT, v.getId(), Math.round(LAKE_RADIUS * 10));
                sound(v, ModSounds.VERG_SLASH.get(), 3.0f, 0.5f);
                sound(v, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
                for (LivingEntity e : near(v, c, LAKE_RADIUS + 2)) hit(v, e, Damage.FUNERAL_CUT, true, false);
            }
            if (age == 104) v.setBodyPose(Vergilius.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 110;
        }

        @Override
        boolean immune() {
            return age < 92;
        }
    }

    /** Tours: one move by name on `t` now, the brain quiet for `quiet` ticks after it. */
    public static boolean force(Vergilius v, String name, LivingEntity t, int quiet) {
        Move m = switch (name) {
            case "heated_puncture" -> new Puncture(v, t);
            case "scorching_incision" -> new Incision(v, t);
            case "following_the_flow" -> new Flow(v, t);
            case "open_the_path" -> new OpenPath(v, t);
            case "red_gaze" -> new Gaze(v, t);
            case "manifest" -> new Manifest(v, t);
            case "internal_bleeding" -> new Bleed(v, t);
            case "blinding_bloodcleaver" -> new Cleaver(v, t);
            case "drown_in_blood" -> new Drown(v, t);
            case "blood_barrier" -> new Barrier(v, t);
            case "funeral" -> new Funeral(v, t);
            default -> null;
        };
        if (m == null) return false;
        if (m instanceof Manifest) v.manifested = true;
        v.cooldown = quiet;
        java.util.Arrays.fill(v.cd, quiet);
        start(v, m, name);
        return true;
    }

    // =================================================================== damage

    static boolean canBeHurt(Vergilius v, DamageSource source) {
        return v.move == null || !v.move.immune();
    }

    /** The counter stance, the barrier. */
    static float adjustIncoming(Vergilius v, ServerLevel level, DamageSource source, float amount) {
        @Nullable Entity by = source.getEntity();
        if (v.move instanceof Flow f && f.open() && by instanceof LivingEntity le && le.distanceTo(v) < 6) {
            f.answer(le);
            v.note("parried");
            return 0;
        }
        if (v.barrier() && v.barrierHits > 0) {
            if (--v.barrierHits <= 0) {
                v.setBarrier(false);
                v.fx(v.position().add(0, 1, 0), Vec3.ZERO, FX_BARRIER_BREAK, v.getId());
                sound(v, ModSounds.VERG_BARRIER.get(), 1.6f, 1.0f);
            } else sound(v, SoundEvents.SHIELD_BLOCK, 1.4f, 0.6f);
            return 0;
        }
        return amount;
    }
}
