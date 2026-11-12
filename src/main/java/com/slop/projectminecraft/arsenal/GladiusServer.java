package com.slop.projectminecraft.arsenal;

import static com.slop.projectminecraft.boss.VergiliusMoves.*;

import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The White-hot Gladius in a player's hand: his skills on the wheel (B), used with right-click.
 * <ul>
 * <li>The gladius alone: Heated Puncture, Scorching Incision, Following the Flow, I Shall Open the Path.</li>
 * <li>With his eyes worn: Red Gaze, and Manifest E.G.O (a minute of Lavacrum Sanguinis: a lake of blood that goes where you go,
 * the crown, pauldron and cape on you, and the wheel turns to Internal Bleeding, Blinding Bloodcleaver, Drown in Blood and
 * Funeral for a Dead Bloodfiend; Funeral costs four hearts and ends the E.G.O).</li>
 * </ul>
 */
public final class GladiusServer {
    private GladiusServer() {}

    /** Skills: the basic four, the eyes' two; in E.G.O the same four slots hold the E.G.O skills. */
    public static final int PUNCTURE = 0, INCISION = 1, FLOW = 2, PATH = 3, GAZE = 4, MANIFEST = 5, SKILLS = 6;
    public static final int BLEED = 0, CLEAVER = 1, DROWN = 2, FUNERAL = 3;
    public static final int[] COOLDOWN = {80, 120, 200, 240, 400, 3600}, EGO_COOLDOWN = {100, 160, 300, 0};
    static final int EGO_TICKS = 1200;
    static final float LAKE = 7;
    /** ArsenalFxPayload.VERGILIUS sub-kind: [FX_HAND, player, selected, flags (1 eyes, 2 E.G.O), E.G.O ticks left, cooldowns x6, E.G.O cooldowns x4]. */
    public static final int FX_HAND = 40;

    record Later(long at, Runnable run) {}

    static final class State {
        int selected;
        final long[] ready = new long[SKILLS], egoReady = new long[4];
        long egoUntil, flowUntil;
        @Nullable Vec3 lake;
        final List<Later> later = new ArrayList<>();
        int lastKey = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean holding(LivingEntity e) {
        return e.getMainHandItem().is(ModItems.WHITE_HOT_GLADIUS.get());
    }

    public static boolean ego(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && p.level().getGameTime() < s.egoUntil;
    }

    public static void select(ServerPlayer p, int skill) {
        int max = ego(p) ? 4 : EyeSlot.worn(p) ? SKILLS : 4;
        if (skill < 0 || skill >= max) return;
        state(p).selected = skill;
        sync(p, true);
    }

    /** /projectminecraft cooldowns: every skill (and every E.G.O skill) ready again. */
    public static void resetCooldowns(ServerPlayer p) {
        State s = state(p);
        java.util.Arrays.fill(s.ready, 0);
        java.util.Arrays.fill(s.egoReady, 0);
        sync(p, true);
    }

    // =================================================================== using

    /** Right-click with the gladius: the selected skill, if it's ready. */
    public static void use(ServerPlayer p) {
        State s = state(p);
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (!s.later.isEmpty()) return;
        boolean ego = now < s.egoUntil;
        int sel = s.selected;
        if (ego) {
            if (sel >= 4 || now < s.egoReady[sel]) return;
            s.egoReady[sel] = now + EGO_COOLDOWN[sel];
            switch (sel) {
                case BLEED -> bleed(p, s, now);
                case CLEAVER -> cleaver(p, s, now);
                case DROWN -> drown(p, s, now);
                case FUNERAL -> funeral(p, s, now);
                default -> {}
            }
        } else {
            if ((sel >= 4 && !EyeSlot.worn(p)) || now < s.ready[sel]) return;
            s.ready[sel] = now + COOLDOWN[sel];
            switch (sel) {
                case PUNCTURE -> puncture(p);
                case INCISION -> incision(p, s, now);
                case FLOW -> {
                    s.flowUntil = now + 24;
                    fx(p, p.position().add(0, 1.2, 0), Vec3.ZERO, FX_COUNTER, 1);
                    sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 1.4f);
                }
                case PATH -> path(p, s, now);
                case GAZE -> gaze(p);
                case MANIFEST -> manifest(p, s, now);
                default -> {}
            }
        }
        sync(p, true);
    }

    static Vec3 flat(Player p) {
        Vec3 f = p.getLookAngle().multiply(1, 0, 1);
        return f.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
    }

    /** Can the player's skill hurt `e`? Not themselves or their pets; players only where PvP is on. */
    static boolean canHit(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive()) return false;
        if (e instanceof Player other && (!p.server.isPvpAllowed() || other.isCreative() || other.isSpectator())) return false;
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(p)) return false;
        return !(e instanceof com.slop.projectminecraft.boss.Vergilius v && p.getUUID().equals(v.owner()));
    }

    static boolean hit(ServerPlayer p, LivingEntity e, float amount, boolean burn) {
        if (!canHit(p, e)) return false;
        e.invulnerableTime = 0;
        boolean ok = e.hurt(p.damageSources().playerAttack(p), amount);
        if (ok && burn) e.igniteForSeconds(3);
        return ok;
    }

    static List<LivingEntity> cone(ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.position().add(0, 1, 0), look = flat(p);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> canHit(p, e))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye).multiply(1, 0, 1);
            if (d.length() > range + e.getBbWidth() / 2) continue;
            if (d.lengthSqr() > 1e-3 && d.normalize().dot(look) < minDot) continue;
            out.add(e);
        }
        return out;
    }

    static List<LivingEntity> near(ServerPlayer p, Vec3 at, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 2.5, r), e -> canHit(p, e) && e.position().distanceTo(at) < r + e.getBbWidth() / 2);
    }

    /** As far as `dist` along `dir` before a wall, somewhere the player fits. */
    static Vec3 reach(ServerPlayer p, Vec3 dir, double dist) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position(), want = from.add(dir.scale(dist));
        var hr = level.clip(new ClipContext(from.add(0, 1, 0), want.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = hr.getType() == HitResult.Type.MISS ? want : hr.getLocation().subtract(0, 1, 0).subtract(dir.scale(0.6));
        for (int i = 0; i <= 6; i++) {
            Vec3 at = to.lerp(from, i / 6.0);
            for (int dy : new int[]{0, 1, -1}) {
                Vec3 q = new Vec3(at.x, Math.floor(at.y) + dy, at.z);
                if (level.noCollision(p, p.getBoundingBox().move(q.subtract(p.position())))) return q;
            }
        }
        return from;
    }

    static void puncture(ServerPlayer p) {
        Vec3 from = p.position(), dir = flat(p);
        // up to the first enemy in front (stopping just short of it), or five blocks on
        double dist = 5;
        for (LivingEntity e : cone(p, 8, 0.8)) dist = Math.min(dist, Math.max(0, e.position().subtract(from).multiply(1, 0, 1).length() - 1.4));
        Vec3 to = reach(p, dir, dist);
        p.teleportTo(to.x, to.y, to.z);
        fx(p, from.add(0, 1, 0), to.add(0, 1, 0), FX_LUNGE, 0);
        fx(p, to.add(0, 1.2, 0), dir, FX_THRUST, 0);
        sound(p, ModSounds.SPEAR.get(), 1.2f, 1.2f);
        sound(p, ModSounds.VERG_SIZZLE.get(), 1.0f, 1.1f);
        for (LivingEntity e : cone(p, 3.4, 0.55)) hit(p, e, 10, true);
    }

    static void incision(ServerPlayer p, State s, long now) {
        for (int n = 0; n < 3; n++) {
            int k = n;
            s.later.add(new Later(now + n * 6, () -> {
                Vec3 f = flat(p);
                fx(p, p.position().add(0, 1.2, 0), f, FX_SLASH, k % 2 == 0 ? 1 : -1, 0, k);
                sound(p, ModSounds.VERG_SLASH.get(), 1.2f, 0.9f + k * 0.12f);
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                for (LivingEntity e : cone(p, 3.8, 0.15)) hit(p, e, 7, true);
            }));
        }
    }

    static void path(ServerPlayer p, State s, long now) {
        Vec3 from = p.position(), dir = flat(p), to = reach(p, dir, 10);
        fx(p, from.add(0, 0.1, 0), to.add(0, 0.1, 0), FX_PATH_LINE, 8);
        sound(p, ModSounds.VERG_SIZZLE.get(), 1.0f, 0.7f);
        List<Entity> struck = new ArrayList<>();
        s.later.add(new Later(now + 8, () -> {
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.2, 2, 1.2), e -> canHit(p, e))) {
                struck.add(e);
                hit(p, e, 8, true);
            }
            p.teleportTo(to.x, to.y, to.z);
            fx(p, from.add(0, 1, 0), to.add(0, 1, 0), FX_LUNGE, 0);
            sound(p, ModSounds.VERG_SLASH.get(), 1.6f, 1.2f);
        }));
        s.later.add(new Later(now + 20, () -> {
            fx(p, from.add(0, 1, 0), to.add(0, 1, 0), FX_PATH_CUT, 0);
            sound(p, ModSounds.VERG_SLASH.get(), 1.8f, 0.6f);
            for (Entity e : struck) if (e instanceof LivingEntity le && le.isAlive()) hit(p, le, 14, true);
        }));
    }

    static void gaze(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        fx(p, eye, Vec3.ZERO, FX_GAZE, -1, 0);
        sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20), e -> canHit(p, e))) {
            Vec3 d = e.getEyePosition().subtract(eye);
            if (d.length() > 20 || d.normalize().dot(look) < 0.9 || !p.hasLineOfSight(e)) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), p);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), p);
            fx(p, e.getEyePosition(), Vec3.ZERO, FX_GAZE_HIT, e.getId());
        }
    }

    static void manifest(ServerPlayer p, State s, long now) {
        s.egoUntil = now + EGO_TICKS;
        s.lake = p.position();
        s.selected = 0;
        java.util.Arrays.fill(s.egoReady, 0);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EGO_TICKS, 0, false, false, true));
        fx(p, s.lake, Vec3.ZERO, FX_MANIFEST, Math.round(LAKE * 10));
        sound(p, ModSounds.VERG_LAKE.get(), 2.0f, 0.9f);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.8f);
    }

    static void bleed(ServerPlayer p, State s, long now) {
        Vec3 from = p.position(), dir = flat(p);
        sound(p, ModSounds.VERG_LAKE.get(), 1.0f, 1.4f);
        for (int i = 0; i < 6; i++) {
            Vec3 spot = from.add(dir.scale(2.5 + i * 1.8));
            int k = i;
            fx(p, spot, Vec3.ZERO, FX_SPIKE_WARN, 6 + i * 3);
            s.later.add(new Later(now + 6 + i * 3, () -> {
                fx(p, spot, Vec3.ZERO, FX_SPIKE);
                sound(p, ModSounds.VERG_SPIKE.get(), 1.0f, 0.9f + k * 0.05f);
                for (LivingEntity e : near(p, spot, 1.4)) {
                    if (hit(p, e, 9, false)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
                        e.hurtMarked = true;
                    }
                }
            }));
        }
    }

    static void cleaver(ServerPlayer p, State s, long now) {
        fx(p, p.position().add(0, 2.4, 0), Vec3.ZERO, FX_CLEAVER_WIND, 10);
        sound(p, ModSounds.VERG_LAKE.get(), 1.2f, 1.6f);
        s.later.add(new Later(now + 10, () -> {
            Vec3 f = flat(p);
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            fx(p, p.position().add(0, 1.2, 0), f, FX_CLEAVER);
            sound(p, ModSounds.VERG_SLASH.get(), 2.0f, 0.5f);
            for (LivingEntity e : cone(p, 7.5, 0.25)) {
                if (hit(p, e, 20, false)) {
                    e.setDeltaMovement(f.scale(0.9).add(0, 0.35, 0));
                    e.hurtMarked = true;
                }
            }
        }));
    }

    static void drown(ServerPlayer p, State s, long now) {
        // the lake is wherever you are (it follows you): its middle is where you stand now
        Vec3 c = p.position();
        fx(p, c, Vec3.ZERO, FX_DROWN_PULL, 30);
        sound(p, ModSounds.VERG_LAKE.get(), 1.6f, 0.6f);
        for (int t = 0; t < 30; t += 2) {
            s.later.add(new Later(now + t, () -> {
                for (LivingEntity e : near(p, c, LAKE)) {
                    Vec3 pull = c.subtract(e.position()).multiply(1, 0, 1);
                    if (pull.lengthSqr() < 1) continue;
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.normalize().scale(0.2)));
                    e.hurtMarked = true;
                }
            }));
        }
        s.later.add(new Later(now + 30, () -> fx(p, c, Vec3.ZERO, FX_SPIKE_WARN, 12, Math.round(LAKE * 10))));
        s.later.add(new Later(now + 42, () -> {
            fx(p, c, Vec3.ZERO, FX_DROWN, Math.round(LAKE * 10));
            sound(p, ModSounds.VERG_SPIKE.get(), 2.0f, 0.6f);
            for (LivingEntity e : near(p, c, LAKE)) {
                if (hit(p, e, 16, false)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                    e.hurtMarked = true;
                }
            }
        }));
    }

    static void funeral(ServerPlayer p, State s, long now) {
        Vec3 c = p.position();
        // the blood is yours this time
        p.hurt(p.damageSources().magic(), 8);
        fx(p, c, Vec3.ZERO, FX_FUNERAL, Math.round(LAKE * 10));
        sound(p, ModSounds.VERG_LAKE.get(), 2.4f, 0.5f);
        sound(p, SoundEvents.BELL_RESONATE, 1.6f, 0.5f);
        for (int t = 10; t < 50; t += 3) {
            s.later.add(new Later(now + t, () -> {
                List<LivingEntity> in = near(p, c, LAKE + 1);
                if (in.isEmpty()) return;
                LivingEntity e = in.get(p.getRandom().nextInt(in.size()));
                Vec3 spot = e.position().add(p.getRandom().nextGaussian() * 0.6, 0, p.getRandom().nextGaussian() * 0.6);
                fx(p, spot, Vec3.ZERO, FX_FUNERAL_SPEAR);
                for (LivingEntity x : near(p, spot, 1.3)) hit(p, x, 3, false);
            }));
        }
        // the E.G.O ends with it, whatever happens to the queued cut
        s.egoUntil = Math.min(s.egoUntil, now + 61);
        s.later.add(new Later(now + 60, () -> {
            fx(p, c, Vec3.directionFromRotation(0, p.getYRot()), FX_FUNERAL_CUT, Math.round(LAKE * 10));
            sound(p, ModSounds.VERG_SLASH.get(), 2.6f, 0.5f);
            for (LivingEntity e : near(p, c, LAKE + 2)) hit(p, e, 26, false);
            endEgo(p, s);
        }));
    }

    static void endEgo(ServerPlayer p, State s) {
        s.egoUntil = 0;
        s.selected = 0;
        s.lake = null;
        p.removeEffect(MobEffects.DAMAGE_BOOST);
        fx(p, p.position(), Vec3.ZERO, FX_LAKE_OFF);
        sync(p, true);
    }

    // =================================================================== events

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        long now = p.serverLevel().getGameTime();
        if (!s.later.isEmpty()) {
            // the queued strikes of a skill in progress (dropped if the gladius leaves the hand or the player dies)
            if (!holding(p) || !p.isAlive()) s.later.clear();
            else {
                for (Later l : new ArrayList<>(s.later)) if (now >= l.at) {
                    s.later.remove(l);
                    l.run.run();
                }
            }
        }
        if (s.egoUntil > 0 && now >= s.egoUntil) endEgo(p, s);
        if (p.tickCount % 10 == 0) sync(p, false);
    }

    /** Following the Flow: struck while in the stance, the blow goes aside and you answer it. */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || p.serverLevel().getGameTime() >= s.flowUntil || !(event.getSource().getEntity() instanceof LivingEntity by) || by.distanceTo(p) > 6) return;
        if (!holding(p) || by == p || !canHit(p, by)) return;
        event.setCanceled(true);
        s.flowUntil = 0;
        Vec3 behind = by.position().add(Vec3.directionFromRotation(0, by.getYRot()).scale(-1.6)).subtract(p.position());
        Vec3 to = reach(p, behind.normalize(), behind.length());
        p.teleportTo(to.x, to.y, to.z);
        fx(p, p.position().add(0, 1.2, 0), Vec3.ZERO, FX_PARRY);
        sound(p, SoundEvents.SHIELD_BLOCK, 1.4f, 0.7f);
        hit(p, by, 12, true);
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

    static void sync(ServerPlayer p, boolean force) {
        State s = state(p);
        long now = p.serverLevel().getGameTime();
        boolean ego = now < s.egoUntil;
        // the eyes taken out with an eye skill selected: back to the first skill
        if (!ego && !EyeSlot.worn(p) && s.selected >= 4) s.selected = 0;
        int flags = (EyeSlot.worn(p) ? 1 : 0) | (ego ? 2 : 0);
        int key = s.selected | flags << 4;
        if (!force && key == s.lastKey && p.tickCount % 40 != 0) return;
        s.lastKey = key;
        int[] ints = new int[5 + SKILLS + 4];
        ints[0] = FX_HAND;
        ints[1] = p.getId();
        ints[2] = s.selected;
        ints[3] = flags;
        ints[4] = ego ? (int) (s.egoUntil - now) : 0;
        for (int i = 0; i < SKILLS; i++) ints[5 + i] = (int) Math.max(0, s.ready[i] - now);
        for (int i = 0; i < 4; i++) ints[5 + SKILLS + i] = (int) Math.max(0, s.egoReady[i] - now);
        Network.sendTracking(p, ArsenalFxPayload.of(ArsenalFxPayload.VERGILIUS, ints));
    }

    static void fx(ServerPlayer p, Vec3 a, Vec3 b, int kind, int... more) {
        int[] ints = new int[2 + more.length];
        ints[0] = kind;
        ints[1] = p.getId();
        System.arraycopy(more, 0, ints, 2, more.length);
        Network.sendNear(p.serverLevel(), p.position(), 96, ArsenalFxPayload.of(ArsenalFxPayload.VERGILIUS, a, b, ints));
    }

    static void sound(Entity e, SoundEvent s, float volume, float pitch) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundSource.PLAYERS, volume, pitch);
    }
}
