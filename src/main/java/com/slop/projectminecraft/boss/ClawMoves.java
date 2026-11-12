package com.slop.projectminecraft.boss;

import com.slop.projectminecraft.registry.ModDamageTypes;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.registry.ModSounds;
import com.slop.projectminecraft.warp.ClawWarp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Everything the Claw does. One move at a time (Claw.move), each a little timeline. Between moves he barely walks:
 * small doses of Serum W close the distance in a blink. Nothing here touches a block.
 * <ul>
 * <li>Trail: two claw swipes. Extirpation: a blink in, a thrust, the claw yanked back (hits on the way out and back).</li>
 * <li>Serum R: kneels, flicks the claw, an orange line shows the path, then a straight charge through everything on it.</li>
 * <li>Serum W: blue circles mark you (and your pets); then he blinks to each mark that he can still see and cuts.</li>
 * <li>Serum K: a green orb that heals him, unless he takes enough damage holding it: then he staggers.</li>
 * <li>Below half health, once: Serum W "through every floor" (ClawWarp). Below a quarter: the Tri-serum Cocktail,
 * immune while it takes hold, then dashes through armour that heal him, and the dimension slash again.</li>
 * </ul>
 * Passive: the first single hit of 30 or more in the fight is negated (Furioso included).
 */
public final class ClawMoves {
    private ClawMoves() {}

    /** ArsenalFxPayload.CLAW sub-kinds (ints[0]); ints[1] is the Claw's id. */
    public static final int FX_RIFT_OPEN = 1, FX_RIFT_CLOSE = 2, FX_BLINK = 3, FX_SWIPE = 4, FX_THRUST = 5, FX_R_LINE = 6, FX_R_CHARGE = 7,
            FX_MARK = 8, FX_MARK_HIT = 9, FX_K_ORB = 10, FX_K_HEAL = 11, FX_NEGATE = 12, FX_GROGGY = 13, FX_TRI = 14, FX_TRI_DASH = 15, FX_BREAK = 16,
            FX_INJECT = 17,
            /** His exit: [kind, claw, the player who beat him (their camera follows it)]. */
            FX_EXIT = 18;
    /** Cooldown slots (Claw.cd). */
    static final int CD_R = 0, CD_W = 1, CD_K = 2, CD_TRI = 3, CD_BLINK = 4, CD_THRUST = 5;
    static final float NO_SELL = 30, STAGGER = 0.10f;
    /** Book-holder UUID -> the Claw after them (one at a time). */
    public static final Map<UUID, UUID> ACTIVE = new HashMap<>();
    /** On the Black Silence's book: the Claw is after its holder. */
    public static final String SOUGHT = "ClawSought";

    // =================================================================== moves

    abstract static class Move {
        final Claw c;
        int age = -1;
        /** The quarry as it was when the move began (a respawn makes a new one: the move is dropped). */
        @Nullable ServerPlayer quarry;

        Move(Claw c) {
            this.c = c;
        }

        void tick() {
            age++;
            step((ServerLevel) c.level());
        }

        abstract void step(ServerLevel level);

        abstract boolean done();

        /** Untouchable for now. */
        boolean immune() {
            return false;
        }
    }

    static void start(Claw c, Move m, String name) {
        m.quarry = c.quarryPlayer();
        c.move = m;
        c.note(name);
    }

    // =================================================================== the brain

    static void tick(Claw c, ServerLevel level, ServerPlayer p) {
        // rebuilt: stronger doses, back sooner (cooldowns run a third faster)
        int ticks = c.rebuilt() && c.tickCount % 3 == 0 ? 2 : 1;
        for (int i = 0; i < c.cd.length; i++) c.cd[i] = Math.max(0, c.cd[i] - ticks);
        if (c.groggy > 0) {
            c.groggy--;
            c.getNavigation().stop();
            if (c.groggy == 0) c.setBodyPose(Claw.POSE_STAND);
            return;
        }
        if (c.move != null && c.move.quarry != p && !(c.move instanceof Warp)) {
            c.move = null;
            c.setBodyPose(Claw.POSE_STAND);
            c.setSerums(0);
            c.staggerTaken = -1;
        }
        if (c.move != null) {
            c.move.tick();
            if (!c.move.done()) return;
            c.move = null;
            c.setBodyPose(Claw.POSE_STAND);
            c.setSerums(0);
            c.staggerTaken = -1;
        }
        // the rematch: now and then he goes for Vergilius instead of you (unless he's down)
        Vergilius ally = c.allyEntity();
        LivingEntity t = ally != null && !ally.isDowned() && c.getRandom().nextFloat() < 0.35f ? ally : p;
        c.getLookControl().setLookAt(t, 30, 30);
        double dist = c.distanceTo(t);
        // the big ones first: the trip through every floor at half health, the cocktail below a quarter
        if (!c.warpedOnce && c.hp() <= 0.5f) {
            c.warpedOnce = true;
            start(c, new Warp(c, p), "serum_w_floors");
            return;
        }
        if (c.hp() <= (c.rebuilt() ? 0.35f : 0.25f) && c.cd[CD_TRI] == 0) {
            c.cd[CD_TRI] = 400;
            start(c, new Tri(c, p), "tri_serum");
            return;
        }
        if (c.cooldown > 0) {
            c.cooldown--;
            approach(c, t, dist);
            return;
        }
        if (c.hp() < 0.7f && c.cd[CD_K] == 0 && c.getRandom().nextFloat() < 0.4f) {
            c.cd[CD_K] = 500;
            start(c, new SerumK(c), "serum_k");
        } else if (c.cd[CD_R] == 0 && dist > 5 && dist < 26 && c.getRandom().nextFloat() < 0.5f) {
            c.cd[CD_R] = 260;
            start(c, new SerumR(c, t), "serum_r");
        } else if (c.cd[CD_W] == 0 && c.getRandom().nextFloat() < 0.4f) {
            c.cd[CD_W] = 320;
            start(c, new SerumW(c, p), "serum_w");
        } else if (dist > 4.5 || c.cd[CD_THRUST] == 0 && c.getRandom().nextFloat() < 0.35f) {
            c.cd[CD_THRUST] = 60;
            start(c, new Extirpation(c, t), "extirpation");
        } else if (dist < 4.5) {
            start(c, new Trail(c, t), "trail");
        } else {
            approach(c, t, dist);
            return;
        }
        c.cooldown = 14 + c.getRandom().nextInt(14);
    }

    /** He doesn't walk far: a short walk, or a blink. */
    static void approach(Claw c, LivingEntity t, double dist) {
        if (dist > 7 && c.cd[CD_BLINK] == 0) {
            c.cd[CD_BLINK] = 50;
            blinkTo(c, t, 2.4);
            return;
        }
        if (dist > 3) c.getNavigation().moveTo(t, 1.0);
        else c.getNavigation().stop();
    }

    /** A small dose of W: from where he is to `dist` blocks in front of the target, facing it. */
    static void blinkTo(Claw c, LivingEntity t, double dist) {
        Vec3 from = c.position();
        Vec3 dir = c.position().subtract(t.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, t.getYRot()) : dir.normalize();
        Vec3 to = safeSpot(c, t.position().add(dir.scale(dist)), t.position());
        c.teleportTo(to.x, to.y, to.z);
        c.face(t.position());
        c.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_BLINK, c.getId());
        sound(c, ModSounds.CLAW_BLINK.get(), 1.4f, 1.0f + c.getRandom().nextFloat() * 0.2f);
    }

    /** `want` if he fits there, else the nearest spot toward `fallback` that he does. */
    static Vec3 safeSpot(Claw c, Vec3 want, Vec3 fallback) {
        ServerLevel level = (ServerLevel) c.level();
        for (int i = 0; i <= 6; i++) {
            Vec3 at = want.lerp(fallback, i / 6.0);
            for (int dy : new int[]{0, 1, -1, 2}) {
                Vec3 q = new Vec3(at.x, Math.floor(at.y) + dy, at.z);
                if (level.noCollision(c, c.getBoundingBox().move(q.subtract(c.position())))) return q;
            }
        }
        return c.position();
    }

    static void sound(Claw c, SoundEvent s, float volume, float pitch) {
        c.level().playSound(null, c.getX(), c.getY(), c.getZ(), s, SoundSource.HOSTILE, volume, pitch);
    }

    static boolean hit(Claw c, LivingEntity t, float amount, boolean throughArmour) {
        t.invulnerableTime = 0;
        DamageSource src = throughArmour ? ModDamageTypes.source(c.level(), ModDamageTypes.CLAW_WARP, c, c) : c.damageSources().mobAttack(c);
        return t.hurt(src, amount);
    }

    /** Everything he can hurt in a cone in front of him. */
    static List<LivingEntity> inFront(Claw c, double range, double minDot) {
        Vec3 eye = c.position().add(0, 1, 0), look = Vec3.directionFromRotation(0, c.getYRot());
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, c.getBoundingBox().inflate(range), e -> e != c && e.isAlive() && !(e instanceof Claw))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye).multiply(1, 0, 1);
            if (d.length() > range + e.getBbWidth() / 2) continue;
            if (d.lengthSqr() > 1e-3 && d.normalize().dot(look) < minDot) continue;
            out.add(e);
        }
        return out;
    }

    // ---- Trail: two swipes
    static final class Trail extends Move {
        final LivingEntity t;

        Trail(Claw c, LivingEntity t) {
            super(c);
            this.t = t;
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age < 14) c.face(t.position());
            if (age == 2 || age == 12) c.setBodyPose(Claw.POSE_SWIPE);
            if (age == 6 || age == 16) {
                c.fx(c.position().add(0, 1.2, 0), Vec3.directionFromRotation(0, c.getYRot()), FX_SWIPE, c.getId(), age == 6 ? 0 : 1);
                sound(c, ModSounds.CLAW_SLASH.get(), 1.4f, age == 6 ? 1.1f : 0.9f);
                for (LivingEntity e : inFront(c, 3.6, 0.2)) hit(c, e, 7, false);
            }
            if (age == 9) c.setBodyPose(Claw.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 24;
        }
    }

    // ---- Extirpation: blink in, thrust, yank back
    static final class Extirpation extends Move {
        final LivingEntity t;

        Extirpation(Claw c, LivingEntity t) {
            super(c);
            this.t = t;
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age == 0 && c.distanceTo(t) > 4) blinkTo(c, t, 3.0);
            if (age < 6) c.face(t.position());
            if (age == 4) c.setBodyPose(Claw.POSE_THRUST);
            if (age == 8 || age == 15) {
                Vec3 look = Vec3.directionFromRotation(0, c.getYRot());
                c.fx(c.position().add(0, 1.2, 0), look, FX_THRUST, c.getId(), age == 8 ? 0 : 1);
                sound(c, age == 8 ? ModSounds.SPEAR.get() : ModSounds.CLAW_SLASH.get(), 1.5f, age == 8 ? 1.0f : 0.8f);
                for (LivingEntity e : inFront(c, 5.5, 0.75)) {
                    hit(c, e, age == 8 ? 9 : 6, false);
                    // the yank: dragged a little toward him
                    if (age == 15) e.setDeltaMovement(e.getDeltaMovement().add(c.position().subtract(e.position()).normalize().scale(0.6)).add(0, 0.15, 0));
                    e.hurtMarked = true;
                }
            }
        }

        @Override
        boolean done() {
            return age >= 26;
        }
    }

    // ---- Serum R: a straight charge through everything
    static final class SerumR extends Move {
        final LivingEntity t;
        Vec3 from, to;
        final List<Entity> struck = new ArrayList<>();

        SerumR(Claw c, LivingEntity t) {
            super(c);
            this.t = t;
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age == 0) {
                c.setBodyPose(Claw.POSE_INJECT);
                c.setSerums(Claw.SERUM_R);
                c.fx(c.position().add(0, 1.6, 0), Vec3.ZERO, FX_INJECT, c.getId(), Claw.SERUM_R);
                sound(c, ModSounds.CLAW_INJECT.get(), 1.5f, 1.0f);
            }
            if (age == 14) {
                // kneel, flick: the path is set now, out to a wall (no further than 30 blocks)
                c.setBodyPose(Claw.POSE_KNEEL);
                c.face(t.position());
                from = c.position();
                Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, c.getYRot()) : dir.normalize();
                Vec3 end = from.add(dir.scale(30));
                var hitRes = level.clip(new ClipContext(from.add(0, 1, 0), end.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
                to = hitRes.getType() == HitResult.Type.MISS ? end : hitRes.getLocation().subtract(0, 1, 0).subtract(dir.scale(0.8));
                to = safeSpot(c, to, from);
                c.fx(from.add(0, 0.1, 0), to.add(0, 0.1, 0), FX_R_LINE, c.getId());
                sound(c, SoundEvents.FIRECHARGE_USE, 1.5f, 0.6f);
            }
            if (age >= 34 && age < 40 && to != null) {
                // six ticks across: whatever is on the line in this stretch gets hit, once
                Vec3 a = from.lerp(to, (age - 34) / 6.0), b = from.lerp(to, (age - 33) / 6.0);
                if (age == 34) {
                    c.setBodyPose(Claw.POSE_DASH);
                    c.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_R_CHARGE, c.getId());
                    sound(c, ModSounds.CLAW_CHARGE.get(), 2.5f, 1.0f);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.6, 2, 1.6), e -> e != c && e.isAlive() && !struck.contains(e))) {
                    if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                    struck.add(e);
                    hit(c, e, 18, false);
                    e.igniteForSeconds(3);
                    Vec3 push = to.subtract(from).normalize().scale(1.1).add(0, 0.5, 0);
                    e.setDeltaMovement(push);
                    e.hurtMarked = true;
                }
                c.teleportTo(b.x, b.y, b.z);
            }
            if (age == 40) c.setBodyPose(Claw.POSE_STAND);
        }

        @Override
        boolean done() {
            return age >= 50;
        }
    }

    // ---- Serum W: marks, then a blink-cut to each mark still in sight
    static final class SerumW extends Move {
        final List<LivingEntity> marked = new ArrayList<>();
        int cut;

        SerumW(Claw c, LivingEntity t) {
            super(c);
            marked.add(t);
            for (TamableAnimal pet : c.level().getEntitiesOfClass(TamableAnimal.class, t.getBoundingBox().inflate(16), pet -> t instanceof Player pl && pet.isOwnedBy(pl) && pet.isAlive())) {
                if (marked.size() < 5) marked.add(pet);
            }
            // and Vergilius, in the rematch
            Vergilius ally = c.allyEntity();
            if (ally != null && !marked.contains(ally)) marked.add(ally);
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age == 0) {
                c.setBodyPose(Claw.POSE_INJECT);
                c.setSerums(Claw.SERUM_W);
                c.staggerTaken = 0;
                c.fx(c.position().add(0, 1.6, 0), Vec3.ZERO, FX_INJECT, c.getId(), Claw.SERUM_W);
                sound(c, ModSounds.CLAW_INJECT.get(), 1.5f, 1.2f);
                for (LivingEntity e : marked) c.fx(e.position(), Vec3.ZERO, FX_MARK, c.getId(), e.getId(), 60);
            }
            if (age == 60) {
                c.staggerTaken = -1;
                // a mark he can't see any more is lost
                marked.removeIf(e -> !e.isAlive() || !c.hasLineOfSight(e) || e.distanceTo(c) > 32);
            }
            if (age >= 60 && (age - 60) % 6 == 0 && cut < marked.size()) {
                LivingEntity e = marked.get(cut++);
                if (!e.isAlive() || e.level() != level) return;
                blinkTo(c, e, 1.6);
                c.fx(e.getBoundingBox().getCenter(), Vec3.ZERO, FX_MARK_HIT, c.getId(), e.getId());
                sound(c, ModSounds.CLAW_SLASH.get(), 1.6f, 1.2f);
                hit(c, e, 10, false);
            }
        }

        @Override
        boolean done() {
            return age >= 60 && cut >= marked.size() && age >= 66 + marked.size() * 6;
        }
    }

    // ---- Serum K: the green orb
    static final class SerumK extends Move {
        SerumK(Claw c) {
            super(c);
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age == 0) {
                c.setBodyPose(Claw.POSE_INJECT);
                c.setSerums(Claw.SERUM_K);
                c.staggerTaken = 0;
                c.fx(c.position().add(0, 1.6, 0), Vec3.ZERO, FX_K_ORB, c.getId(), 80);
                sound(c, ModSounds.CLAW_INJECT.get(), 1.5f, 0.8f);
            }
            if (age == 80) {
                c.heal(c.getMaxHealth() * 0.15f);
                c.fx(c.position().add(0, 1, 0), Vec3.ZERO, FX_K_HEAL, c.getId());
                sound(c, SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.6f);
            }
        }

        @Override
        boolean done() {
            return age >= 88;
        }
    }

    // ---- Serum W, "through every floor"
    static final class Warp extends Move {
        final ServerPlayer t;
        boolean started, back;

        Warp(Claw c, ServerPlayer t) {
            super(c);
            this.t = t;
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age == 0) {
                c.setBodyPose(Claw.POSE_INJECT);
                c.setSerums(Claw.SERUM_W);
                c.fx(c.position().add(0, 1.6, 0), Vec3.ZERO, FX_INJECT, c.getId(), Claw.SERUM_W);
                sound(c, ModSounds.CLAW_INJECT.get(), 2.0f, 1.3f);
            }
            if (age == 16) {
                blinkTo(c, t, 1.2);
                started = ClawWarp.start(t, c, null);
                // he goes with them: here, nothing but a rift
                if (started) c.setInvisible(true);
            }
            if (started && age > 18 && !back && !ClawWarp.busy(t)) {
                back = true;
                c.setInvisible(false);
                if (t.level() == c.level()) blinkTo(c, t, 2.5);
            }
        }

        @Override
        boolean done() {
            return age > 16 && (!started || back) && age >= 24;
        }

        @Override
        boolean immune() {
            return true;
        }
    }

    // ---- Tri-serum Cocktail
    static final class Tri extends Move {
        final ServerPlayer t;
        int dashes;

        Tri(Claw c, ServerPlayer t) {
            super(c);
            this.t = t;
        }

        @Override
        void step(ServerLevel level) {
            c.getNavigation().stop();
            if (age == 0) {
                c.setBodyPose(Claw.POSE_INJECT);
                c.setSerums(Claw.SERUM_K | Claw.SERUM_R | Claw.SERUM_W);
                c.fx(c.position().add(0, 1.2, 0), Vec3.ZERO, FX_TRI, c.getId());
                sound(c, ModSounds.CLAW_INJECT.get(), 2.0f, 0.7f);
            }
            if (age == 20) c.setBodyPose(Claw.POSE_KNEEL);
            // five dashes through you, each one healing him a little
            if (age >= 40 && (age - 40) % 7 == 0 && dashes < 5) {
                dashes++;
                Vec3 from = c.position();
                Vec3 side = Vec3.directionFromRotation(0, c.getRandom().nextFloat() * 360).scale(3.5);
                Vec3 to = safeSpot(c, t.position().add(side), t.position());
                c.teleportTo(to.x, to.y, to.z);
                c.face(t.position());
                c.setBodyPose(Claw.POSE_DASH);
                c.fx(from.add(0, 1, 0), to.add(0, 1, 0), FX_TRI_DASH, c.getId(), dashes);
                sound(c, ModSounds.CLAW_SLASH.get(), 1.8f, 0.8f + dashes * 0.08f);
                if (hit(c, t, 5, true)) c.heal(c.getMaxHealth() * 0.02f);
            }
            // and the trip through every floor once more, if the cocktail didn't finish it
            if (age == 80 && t.isAlive()) {
                c.setBodyPose(Claw.POSE_STAND);
                start(c, new Warp(c, t), "serum_w_floors");
            }
        }

        @Override
        boolean done() {
            return age >= 80;
        }

        @Override
        boolean immune() {
            return age < 40;
        }
    }

    /** Tours: start one move by name on his quarry now, the brain quiet for `quiet` ticks after it. */
    public static boolean force(Claw c, String name, int quiet) {
        ServerPlayer p = c.quarryPlayer();
        if (p == null) return false;
        Move m = switch (name) {
            case "trail" -> new Trail(c, p);
            case "extirpation" -> new Extirpation(c, p);
            case "serum_r" -> new SerumR(c, p);
            case "serum_w" -> new SerumW(c, p);
            case "serum_k" -> new SerumK(c);
            case "serum_w_floors" -> new Warp(c, p);
            case "tri_serum" -> new Tri(c, p);
            default -> null;
        };
        if (m == null) return false;
        c.groggy = 0;
        c.cooldown = quiet;
        c.warpedOnce = true;
        java.util.Arrays.fill(c.cd, quiet);
        start(c, m, name);
        return true;
    }

    // =================================================================== damage

    static boolean canBeHurt(Claw c, DamageSource source) {
        return c.move == null || !c.move.immune();
    }

    /** The No-Sell, the stagger windows, and groggy taking more. */
    static float adjustIncoming(Claw c, ServerLevel level, DamageSource source, float amount) {
        if (!c.noSellUsed && amount >= NO_SELL) {
            c.noSellUsed = true;
            c.fx(c.position().add(0, 1.2, 0), Vec3.ZERO, FX_NEGATE, c.getId(), Math.round(amount));
            sound(c, SoundEvents.SHIELD_BLOCK, 2.0f, 0.5f);
            c.note("no_sell " + Math.round(amount));
            return 0;
        }
        if (c.groggy > 0) amount *= 1.5f;
        if (c.staggerTaken >= 0) {
            c.staggerTaken += amount;
            if (c.staggerTaken >= c.getMaxHealth() * STAGGER) {
                // knocked out of the serum: down on one knee for a while
                c.staggerTaken = -1;
                c.move = null;
                c.setSerums(0);
                c.groggy = 120;
                c.setBodyPose(Claw.POSE_GROGGY);
                c.fx(c.position().add(0, 1, 0), Vec3.ZERO, FX_GROGGY, c.getId());
                sound(c, SoundEvents.ANVIL_LAND, 1.4f, 0.6f);
                c.note("groggy");
            }
        }
        return amount;
    }

    // =================================================================== the book

    public static boolean sought(ItemStack stack) {
        return stack.is(ModItems.BLACK_SILENCE_BOOK.get()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(SOUGHT);
    }

    public static void setSought(ItemStack stack, boolean on) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (on) tag.putBoolean(SOUGHT, true);
            else tag.remove(SOUGHT);
        });
    }

    /** Won: the book he came for didn't survive the fight. */
    static void tearBook(ServerPlayer p) {
        ItemStack carried = p.containerMenu.getCarried();
        if (sought(carried)) {
            p.containerMenu.setCarried(new ItemStack(Items.BOOK, carried.getCount()));
            p.displayClientMessage(Component.translatable("message.projectminecraft.claw.torn").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!sought(s)) continue;
            inv.setItem(i, new ItemStack(Items.BOOK, s.getCount()));
            p.displayClientMessage(Component.translatable("message.projectminecraft.claw.torn").withStyle(ChatFormatting.GRAY), false);
            return;
        }
    }

    /** He left without it: it's just the book again. */
    static void releaseBook(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (sought(s)) setSought(s, false);
        for (ItemStack s : p.getInventory().offhand) if (sought(s)) setSought(s, false);
    }

    /** Lost: he takes the book (from the drops, or from the inventory with keepInventory) and goes. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UUID id = ACTIVE.get(p.getUUID());
        boolean took = event.getDrops().removeIf(it -> sought(it.getItem()));
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (sought(p.getInventory().getItem(i))) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                took = true;
            }
        }
        if (id == null || !took) return;
        for (ServerLevel level : p.server.getAllLevels()) {
            if (level.getEntity(id) instanceof Claw c) {
                c.leave(level, true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
