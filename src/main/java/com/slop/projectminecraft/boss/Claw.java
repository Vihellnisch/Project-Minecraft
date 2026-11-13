package com.slop.projectminecraft.boss;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.FxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.warp.ClawWarp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.slop.projectminecraft.registry.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Baral, an Executioner of the Claw. The Head sends him after whoever holds the Black Silence's book: he cuts his way in
 * through space with Serum W and the fight starts wherever you are. He doesn't die: brought down, his claw and serums
 * break and he warps out, and the book is torn apart in the fight. If you fall, he takes the book and leaves.
 * Never breaks a block. The moves are in ClawMoves.
 */
public class Claw extends Monster {
    public static final byte ARRIVE = 0, FIGHT = 1, RETREAT = 2, LEAVE = 3;
    /** What his body is doing, for the renderer. */
    public static final byte POSE_STAND = 0, POSE_SWIPE = 1, POSE_THRUST = 2, POSE_INJECT = 3, POSE_KNEEL = 4, POSE_GROGGY = 5, POSE_DASH = 6;
    /** Which vials are lit (bits), for the renderer. */
    public static final int SERUM_K = 1, SERUM_R = 2, SERUM_W = 4;
    /** Subtitle lines (lang: projectminecraft.boss.claw.line.N); BANNER is the name card (Roland.BANNER). */
    public static final int LINE_ARRIVE = 0, LINE_TAUNT = 1, LINE_RETREAT = 2, LINE_LEAVE = 3, SPEAKER = 2;
    /** The rebuilt Claw's name card (BossClient: "Baral / Rebuilt"). */
    public static final int REBUILT_BANNER = 90;
    public static final int ARRIVE_TICKS = 160, RETREAT_TICKS = 156, LEAVE_TICKS = 40;
    /**
     * The entrance, in ticks of ARRIVE: space tears open (to E_OPEN), the claw reaches out of it (E_CLAW), grips the edge
     * and pulls it wider (E_GRIP), a leg steps through and plants (E_LEG, E_STEP), he leans out of the dark (E_BODY, out by
     * E_OUT), lifts his head (E_HEAD) as the vials light one by one, and the rift snaps shut behind him (E_SNAP). The client
     * draws all of it from the synced clock (ClawEntrance).
     */
    public static final int E_CRACK = 12, E_OPEN = 30, E_CLAW = 34, E_GRIP = 58, E_LEG = 70, E_STEP = 92, E_BODY = 95, E_OUT = 125,
            E_HEAD = 128, E_SNAP = 142, E_LINE = 146, E_BANNER = 154;
    /**
     * The exit, in ticks of RETREAT: the last blow staggers him (R_HIT), he drops to a knee (R_KNEEL), the claw and the
     * vials burst off him (R_BREAK), the mask splits and a chunk of it breaks away (R_MASK), he looks up at you (R_LOOK), says his line, jams
     * what's left of W into the stump (R_INJECT), a rift tears open behind him (R_RIFT), he gets up (R_RISE) and limps
     * backwards into it (R_BACK), and it snaps shut (R_SNAP). Same clock as the entrance (ClawEntrance draws it).
     */
    public static final int R_HIT = 0, R_KNEEL = 8, R_BREAK = 26, R_MASK = 40, R_LOOK = 52, R_LINE = 58, R_INJECT = 66, R_RIFT = 80,
            R_RISE = 92, R_BACK = 106, R_SNAP = 144;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Claw.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(Claw.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SERUMS = SynchedEntityData.defineId(Claw.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_BROKEN = SynchedEntityData.defineId(Claw.class, EntityDataSerializers.BOOLEAN);
    /** The rematch (the warn path): broken parts replaced with stronger ones, heavier serum doses. */
    private static final EntityDataAccessor<Boolean> DATA_REBUILT = SynchedEntityData.defineId(Claw.class, EntityDataSerializers.BOOLEAN);
    /** Ticks into the entrance (ARRIVE), for the client's cutscene. */
    private static final EntityDataAccessor<Integer> DATA_ENTRANCE = SynchedEntityData.defineId(Claw.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projectminecraft.claw"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    /** Who holds the book he came for. */
    @Nullable
    UUID quarry;
    /** The rematch: Vergilius, fighting beside his quarry. */
    @Nullable
    public UUID ally;
    int stateT, lostTicks;
    boolean loaded;

    // ---- moves (ClawMoves)
    @Nullable
    ClawMoves.Move move;
    int cooldown = 20;
    final int[] cd = new int[8];
    boolean noSellUsed, warpedOnce;
    /** Tri-serum Cocktails used this fight. */
    int triUsed;
    /** Damage taken since a stagger window opened (Serum K, Serum W marks), or -1 when none is open. */
    float staggerTaken = -1;
    int groggy;
    /** Recorded moves (tours read them): "tick name hp%". */
    public final List<String> log = new ArrayList<>();

    public Claw(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 600;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, FIGHT).define(DATA_POSE, POSE_STAND).define(DATA_SERUMS, (byte) 0).define(DATA_BROKEN, false).define(DATA_REBUILT, false).define(DATA_ENTRANCE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ------------------------------------------------------------------ queries

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public byte bodyPose() {
        return entityData.get(DATA_POSE);
    }

    void setBodyPose(byte p) {
        entityData.set(DATA_POSE, p);
    }

    public int serums() {
        return entityData.get(DATA_SERUMS);
    }

    void setSerums(int bits) {
        entityData.set(DATA_SERUMS, (byte) bits);
    }

    /** His claw and vials are gone (retreating). */
    public boolean broken() {
        return entityData.get(DATA_BROKEN);
    }

    /** The rebuilt Claw of the rematch. */
    public boolean rebuilt() {
        return entityData.get(DATA_REBUILT);
    }

    public void setRebuilt(boolean r) {
        entityData.set(DATA_REBUILT, r);
    }

    public int groggyTicks() {
        return groggy;
    }

    /** The No-Sell is spent (tours reset it). */
    public void setNoSellUsed(boolean used) {
        noSellUsed = used;
    }

    /** Tours: straight to the end of the entrance (it only plays out in the summon stop). */
    public void skipEntrance() {
        if (state() != ARRIVE) return;
        entityData.set(DATA_STATE, FIGHT);
        entityData.set(DATA_ENTRANCE, ARRIVE_TICKS);
        setNoGravity(false);
        stateT = 0;
        cooldown = 10;
    }

    /** Ticks into the exit, with partials (-1 when he isn't leaving that way). */
    public float exit(float partial) {
        return state() == RETREAT ? entityData.get(DATA_ENTRANCE) + partial : -1;
    }

    /** Ticks into the entrance, with partials (ARRIVE_TICKS once he's out). */
    public float entrance(float partial) {
        return state() == ARRIVE ? entityData.get(DATA_ENTRANCE) + partial : ARRIVE_TICKS;
    }

    /** Vergilius beside his quarry, if this is the rematch and he's here. */
    @Nullable
    public Vergilius allyEntity() {
        return ally != null && level() instanceof ServerLevel sl && sl.getEntity(ally) instanceof Vergilius v && v.isAlive() ? v : null;
    }

    public float hp() {
        return getHealth() / getMaxHealth();
    }

    @Nullable
    public ServerPlayer quarryPlayer() {
        return quarry == null || getServer() == null ? null : getServer().getPlayerList().getPlayer(quarry);
    }

    @Nullable
    public UUID quarry() {
        return quarry;
    }

    void note(String what) {
        log.add(tickCount + " " + what + " " + Math.round(hp() * 100) + "%");
        if (log.size() > 400) log.remove(0);
    }

    void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        // right on top of it: no heading to take
        if (d.x * d.x + d.z * d.z < 1e-4) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yHeadRot = yaw;
    }

    private void sound(ServerLevel level, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
        level.playSound(null, getX(), getY() + 1, getZ(), s, SoundSource.HOSTILE, volume, pitch);
    }

    void say(int line) {
        Network.sendNear((ServerLevel) level(), position(), 96, FxPayload.at(FxPayload.BOSS_SAY, getEyePosition()).to(new Vec3(SPEAKER, 0, 0)).data(line));
    }

    void fx(Vec3 a, Vec3 b, int... ints) {
        Network.sendNear((ServerLevel) level(), position(), 128, ArsenalFxPayload.of(ArsenalFxPayload.CLAW, a, b, ints));
    }

    // ------------------------------------------------------------------ arrival

    /** He tears through space in front of `p`, after the book in their hands. */
    public void arrive(ServerPlayer p) {
        quarry = p.getUUID();
        loaded = true;
        entityData.set(DATA_STATE, ARRIVE);
        entityData.set(DATA_ENTRANCE, 0);
        stateT = 0;
        // the rift is set where he stands: he doesn't drift or fall out of it while he comes through
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        // the renderer keeps him inside the rift until each part comes out
        setInvisible(false);
        setNoAi(false);
        float hp = Config.CLAW_HEALTH.get().floatValue();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth(hp);
        face(p.position());
        ClawMoves.ACTIVE.put(p.getUUID(), getUUID());
        // [kind, claw, 1 = the entrance rift, the player who opened the book (their camera follows it)]
        fx(position().add(0, 1.2, 0), Vec3.directionFromRotation(0, getYRot()), ClawMoves.FX_RIFT_OPEN, getId(), 1, p.getId());
        note("arrive");
    }

    // ------------------------------------------------------------------ brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!loaded || quarry == null) {
            // loaded from a save: the Head doesn't wait for a reload; he's gone
            discard();
            return;
        }
        stateT++;
        switch (state()) {
            case ARRIVE -> tickArrive(level);
            case FIGHT -> tickFight(level);
            case RETREAT -> tickRetreat(level);
            case LEAVE -> tickLeave(level);
            default -> {}
        }
        if (tickCount % 10 == 0) syncBossBar(level);
        bossBar.setProgress(Math.max(0, hp()));
    }

    private void tickArrive(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_ENTRANCE, stateT);
        // he keeps the heading he came in on: the rift is set in it
        switch (stateT) {
            case E_CRACK -> sound(level, SoundEvents.GLASS_BREAK, 1.6f, 0.45f);
            case E_OPEN -> {
                sound(level, SoundEvents.PORTAL_TRIGGER, 0.7f, 1.6f);
                sound(level, SoundEvents.BEACON_DEACTIVATE, 2.0f, 0.5f);
            }
            case E_CLAW + 6 -> sound(level, ModSounds.CLAW_SLASH.get(), 1.4f, 0.5f);
            case E_GRIP -> {
                sound(level, ModSounds.CLAW_CHARGE.get(), 1.6f, 1.3f);
                sound(level, SoundEvents.GLASS_BREAK, 1.4f, 0.7f);
                if (rebuilt()) {
                    sound(level, ModSounds.CLAW_ROAR.get(), 2.5f, 0.9f);
                    sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
                }
            }
            case E_STEP -> {
                sound(level, SoundEvents.WARDEN_STEP, 2.5f, 0.6f);
                sound(level, SoundEvents.ANVIL_LAND, rebuilt() ? 1.6f : 0.6f, 0.5f);
                if (rebuilt()) sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
            }
            case E_BODY -> sound(level, SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.7f);
            case E_HEAD -> setSerums(SERUM_K);
            case E_HEAD + 5 -> setSerums(SERUM_K | SERUM_W);
            case E_HEAD + 10 -> setSerums(SERUM_K | SERUM_W | SERUM_R);
            case E_SNAP -> {
                setSerums(0);
                sound(level, ModSounds.CLAW_SHATTER.get(), 2.0f, 1.3f);
                sound(level, ModSounds.CLAW_RIFT.get(), 1.5f, 1.8f);
            }
            case E_SNAP + 3 -> sound(level, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 2.0f, 0.5f);
            case E_LINE -> say(LINE_ARRIVE);
            case E_BANNER -> say(rebuilt() ? REBUILT_BANNER : Roland.BANNER);
            default -> {}
        }
        // a chime as each vial lights
        if (stateT == E_HEAD || stateT == E_HEAD + 5 || stateT == E_HEAD + 10) sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 1.1f + (stateT - E_HEAD) * 0.05f);
        if (stateT >= ARRIVE_TICKS) {
            setNoGravity(false);
            entityData.set(DATA_STATE, FIGHT);
            stateT = 0;
            cooldown = 10;
        }
    }

    private void tickFight(ServerLevel level) {
        ServerPlayer p = quarryPlayer();
        // the quarry gone (logged out, another dimension, far away) for half a minute: he leaves, empty-handed
        boolean here = p != null && p.isAlive() && p.level() == level && p.distanceToSqr(this) < 64 * 64 && !p.isCreative() && !p.isSpectator();
        if (!here) {
            getNavigation().stop();
            // dragged off by his own Serum W: only the trip goes on
            if (p != null && ClawWarp.busy(p) && move instanceof ClawMoves.Warp) {
                move.tick();
                return;
            }
            if (move != null) {
                move = null;
                setInvisible(false);
                setSerums(0);
                setBodyPose(POSE_STAND);
            }
            // the rematch: no book to take; his quarry down is the end of it
            if (rebuilt() && (p == null || !p.isAlive())) {
                leave(level, false);
                return;
            }
            if (++lostTicks > 600) leave(level, false);
            return;
        }
        lostTicks = 0;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            leave(level, false);
            return;
        }
        ClawMoves.tick(this, level, p);
    }

    // ------------------------------------------------------------------ endings

    /** Brought down: his claw and serums break, he warps out. */
    void retreat(ServerLevel level, @Nullable net.minecraft.world.entity.Entity by) {
        entityData.set(DATA_STATE, RETREAT);
        entityData.set(DATA_ENTRANCE, 0);
        stateT = 0;
        move = null;
        groggy = 0;
        setInvisible(false);
        // the book goes first, before anything he drops can be picked up
        ServerPlayer quarryP = quarryPlayer();
        if (quarryP != null) ClawMoves.tearBook(quarryP);
        // the one who sees him off (their camera, and he faces them): his quarry if they're here, else whoever beat him
        boolean here = quarryP != null && quarryP.isAlive() && quarryP.level() == level && quarryP.distanceToSqr(this) < 64 * 64;
        ServerPlayer p = here ? quarryP : by instanceof ServerPlayer sp && sp.level() == level ? sp : null;
        if (p != null) face(p.position());
        // the one whose book it was, and whoever brought him down, have learned the gloves
        if (quarryP != null) com.slop.projectminecraft.arsenal.GloveServer.master(quarryP);
        if (by instanceof ServerPlayer sp) com.slop.projectminecraft.arsenal.GloveServer.master(sp);
        // the rematch won: Vergilius walks with you now
        Vergilius v = allyEntity();
        if (v != null) v.onClawBeaten();
        // and he stays where his rift opens: no falling, floating or sliding off
        setNoGravity(true);
        setSerums(0);
        setBodyPose(POSE_STAND);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        // [kind, claw, the one who beat him]: the rift behind him and their camera
        fx(position().add(0, 1.2, 0), Vec3.directionFromRotation(0, getYRot()), ClawMoves.FX_EXIT, getId(), p == null ? -1 : p.getId());
        sound(level, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
        sound(level, SoundEvents.PLAYER_ATTACK_CRIT, 2.0f, 0.6f);
        note("retreat");
    }

    /** One of his broken pieces, flung off him from `at` (feet-relative: along his left, up, ahead). */
    private void fling(ServerLevel level, net.minecraft.world.item.Item item, double left, double up, double ahead, Vec3 push) {
        Vec3 f = Vec3.directionFromRotation(0, getYRot()), l = new Vec3(f.z, 0, -f.x);
        Vec3 at = position().add(l.scale(left)).add(f.scale(ahead)).add(0, up, 0);
        ItemEntity it = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item));
        it.setDeltaMovement(l.scale(push.x).add(f.scale(push.z)).add(0, push.y, 0).add((random.nextDouble() - 0.5) * 0.08, 0, (random.nextDouble() - 0.5) * 0.08));
        // long enough to see it land
        it.setPickUpDelay(30);
        level.addFreshEntity(it);
    }

    private void tickRetreat(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_ENTRANCE, stateT);
        switch (stateT) {
            case R_KNEEL -> {
                sound(level, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 2.0f, 0.5f);
                sound(level, SoundEvents.WARDEN_STEP, 1.6f, 0.5f);
            }
            case R_BREAK -> {
                // the claw snaps off the arm and the vials burst: the pieces fly off him and stay where they land
                entityData.set(DATA_BROKEN, true);
                fx(position().add(0, 1.1, 0), Vec3.ZERO, ClawMoves.FX_BREAK, getId());
                sound(level, SoundEvents.GLASS_BREAK, 2.0f, 0.6f);
                sound(level, SoundEvents.ANVIL_BREAK, 1.4f, 0.8f);
                sound(level, SoundEvents.ITEM_BREAK, 1.6f, 0.6f);
                if (rebuilt()) {
                    // the rebuilt parts hold together better: one comes off whole (the claw arm, or one of the serums)
                    net.minecraft.world.item.Item[] whole = {ModItems.CLAW_ARM.get(), ModItems.SERUM_K.get(), ModItems.SERUM_R.get(), ModItems.SERUM_W.get()};
                    fling(level, whole[random.nextInt(whole.length)], 0.6, 1.0, 0.2, new Vec3(0.2, 0.28, 0.14));
                } else {
                    fling(level, ModItems.BROKEN_CLAW.get(), 0.6, 0.6, 0.3, new Vec3(0.22, 0.28, 0.12));
                    fling(level, ModItems.BROKEN_SERUM_K.get(), 0.15, 1.9, 0.1, new Vec3(-0.08, 0.32, 0.1));
                    fling(level, ModItems.BROKEN_SERUM_W.get(), 0.5, 1.45, 0.0, new Vec3(0.16, 0.3, 0.04));
                    fling(level, ModItems.BROKEN_SERUM_R.get(), 0.6, 1.0, 0.1, new Vec3(0.2, 0.22, 0.14));
                }
            }
            case R_MASK -> {
                // the mask splits and a chunk breaks away (his face stays hidden; what's left of the mask is the trophy)
                sound(level, SoundEvents.WOOD_BREAK, 2.0f, 0.7f);
                sound(level, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.5f);
                fling(level, ModItems.CLAW_MASK.get(), 0, 1.3, 0.4, new Vec3(0, 0.18, 0.2));
                // and from his coat, a folded page: the Head's hitlist (not from the rebuilt Claw, and not if you're still on one)
                ServerPlayer q = quarryPlayer();
                if (!rebuilt() && (q == null || !com.slop.projectminecraft.item.Hitlist.hasOpen(q)))
                    fling(level, ModItems.HITLIST.get(), -0.3, 1.1, 0.3, new Vec3(-0.12, 0.2, 0.18));
            }
            case R_LINE -> say(LINE_RETREAT);
            case R_INJECT -> sound(level, ModSounds.CLAW_INJECT.get(), 1.4f, 0.7f);
            case R_RIFT -> sound(level, ModSounds.CLAW_RIFT.get(), 2.0f, 0.6f);
            case R_RISE -> sound(level, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.6f, 0.6f);
            case R_SNAP -> {
                sound(level, ModSounds.CLAW_SHATTER.get(), 2.0f, 1.2f);
                sound(level, ModSounds.CLAW_RIFT.get(), 1.4f, 1.8f);
            }
            default -> {}
        }
        // dragging his feet backwards into the rift
        if (stateT > R_BACK && stateT < R_SNAP && (stateT - R_BACK) % 9 == 0) sound(level, SoundEvents.WARDEN_STEP, 0.9f, 0.55f);
        if (stateT >= RETREAT_TICKS) gone(level);
    }

    /** Leaving without a fight to finish: with the book (`took`), or without it (the quarry got away). */
    void leave(ServerLevel level, boolean took) {
        if (state() == LEAVE || state() == RETREAT) return;
        entityData.set(DATA_STATE, LEAVE);
        stateT = 0;
        move = null;
        setInvisible(false);
        setSerums(SERUM_W);
        setBodyPose(POSE_INJECT);
        say(took ? LINE_TAUNT : LINE_LEAVE);
        fx(position().add(0, 1.2, 0), Vec3.directionFromRotation(0, getYRot()), ClawMoves.FX_RIFT_OPEN, getId());
        if (!took) {
            ServerPlayer p = quarryPlayer();
            if (p != null) ClawMoves.releaseBook(p);
        }
        Vergilius v = allyEntity();
        if (v != null) v.onRematchLost();
        note(took ? "took the book" : "left");
    }

    private void tickLeave(ServerLevel level) {
        getNavigation().stop();
        if (stateT >= LEAVE_TICKS) gone(level);
    }

    private void gone(ServerLevel level) {
        // the exit's rift has already slammed shut on him (ClawEntrance); leaving any other way, close his rift here
        if (state() != RETREAT) {
            fx(position().add(0, 1.2, 0), Vec3.ZERO, ClawMoves.FX_RIFT_CLOSE, getId());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 40, 0.4, 0.8, 0.4, 0.4);
        }
        if (quarry != null) ClawMoves.ACTIVE.remove(quarry, getUUID());
        bossBar.removeAllPlayers();
        discard();
    }

    // ------------------------------------------------------------------ bar

    private void syncBossBar(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 64 * 64 && state() != LEAVE) bossBar.addPlayer(p);
            else bossBar.removePlayer(p);
        }
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() != FIGHT || !ClawMoves.canBeHurt(this, source)) return false;
        // still flashing from the last hit: vanilla would ignore this one, so the No-Sell and stagger don't see it either
        if (invulnerableTime > 10 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN) && amount <= lastHurt) return false;
        amount = ClawMoves.adjustIncoming(this, (ServerLevel) level(), source, amount);
        if (amount <= 0) return false;
        // he doesn't go down: a hit that would take him to his last sliver (before armour) stops there, and he breaks and warps out
        boolean last = amount >= getHealth() - 1;
        amount = Math.min(amount, Math.max(0, getHealth() - 1));
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && (last || getHealth() <= 1.001f)) {
            setHealth(1);
            retreat((ServerLevel) level(), source.getEntity());
        }
        return hit;
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (quarry != null && !level().isClientSide) ClawMoves.ACTIVE.remove(quarry, getUUID());
        super.remove(reason);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (quarry != null) tag.putUUID("Quarry", quarry);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // a fight interrupted by a reload doesn't resume: customServerAiStep discards him
        loaded = false;
    }
}
