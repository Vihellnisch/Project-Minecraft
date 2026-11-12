package com.slop.projectminecraft.boss;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.item.Hitlist;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.FxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.quest.HitlistTrail;
import com.slop.projectminecraft.registry.ModEntities;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Vergilius, the Red Gaze: a Color, Limbus Company's guide, and a name on the Head's hitlist. He waits at the end of the
 * hitlist's trail (MEET). Draw on him and he fights (FIGHT; at half health his E.G.O, Lavacrum Sanguinis): brought down, he
 * doesn't die; the Limbus bus comes for him and he leaves his gladius and his eyes behind (RETREAT). Show him the hitlist
 * instead and the rebuilt Claw comes for you both (ALLY); win that, and he walks with you (COMPANION) until you turn on him.
 * The moves are in VergiliusMoves; the bus is LimbusBus, driven from here.
 */
public class Vergilius extends Monster {
    public static final byte MEET = 0, FIGHT = 1, RETREAT = 2, ALLY = 3, COMPANION = 4, WITHDRAW = 5;
    /** What his body is doing, for the renderer. */
    public static final byte POSE_STAND = 0, POSE_THRUST = 1, POSE_SLASH = 2, POSE_COUNTER = 3, POSE_DASH = 4, POSE_KNEEL = 5,
            POSE_OVERHEAD = 6, POSE_STILL = 7;
    /** Subtitle lines (lang projectminecraft.boss.vergilius.line.N); Roland.BANNER is his name card. */
    public static final int LINE_INTRO = 0, LINE_DRAW = 1, LINE_WARN = 2, LINE_RETREAT = 3, LINE_RESET = 4, LINE_BETRAY = 5, LINE_JOIN = 6,
            LINE_ALREADY = 7, LINE_WITHDRAW = 8, LINE_MANIFEST = 9, LINE_BARK = 10, BARKS = 4, LINE_FUNERAL = 14, LINE_DOWNED = 15,
            LINE_STAY = 16, LINE_FOLLOW = 17, LINE_WARNING = 18, SPEAKER = 3;
    /** The bus coming for him: when it's called, pulls up, opens, he gets on, it shuts, drives off, is gone (ticks of RETREAT/WITHDRAW). */
    static final int B_CALL = 20, B_STOP = 80, B_OPEN = 86, B_STAND = 92, B_LINE = 96, B_WALK = 104, B_IN = 122, B_SHUT = 128, B_GO = 140,
            B_FADE = 186, B_GONE = 206;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Vergilius.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(Vergilius.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_EGO = SynchedEntityData.defineId(Vergilius.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_GAZE = SynchedEntityData.defineId(Vergilius.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_BARRIER = SynchedEntityData.defineId(Vergilius.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projectminecraft.vergilius"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    /** Who he met (and fights, or fought beside); who he follows; the hitlist that led here; the rebuilt Claw of the rematch. */
    @Nullable UUID quarry, owner, hitlist, claw;
    /** Where he waits (the scene), to go back to after a fight that didn't end. */
    @Nullable Vec3 home;
    int stateT;
    boolean introduced, choosing, staying;
    int choiceT, lostTicks, downed, nextBark = 2400;
    /** The owner's hits on him, by tick (three close together is a betrayal). */
    final List<Integer> ownerHits = new ArrayList<>();

    // ---- the fight (VergiliusMoves)
    @Nullable VergiliusMoves.Move move;
    int cooldown = 20;
    final int[] cd = new int[12];
    boolean manifested;
    int barrierHits;
    @Nullable Vec3 lake;
    /** Recorded moves (tours read them). */
    public final List<String> log = new ArrayList<>();

    // ---- the bus
    @Nullable LimbusBus bus;
    @Nullable Vec3 busStop, lane;
    boolean dropped;

    public Vergilius(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 800;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, MEET).define(DATA_POSE, POSE_STAND).define(DATA_EGO, false).define(DATA_GAZE, (byte) 0).define(DATA_BARRIER, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Waiting at the end of a hitlist's trail, for whoever holds it. */
    @Nullable
    public static Vergilius atScene(ServerLevel level, Vec3 at, float yaw, UUID hitlist, UUID player) {
        Vergilius v = ModEntities.VERGILIUS.get().create(level);
        if (v == null) return null;
        v.moveTo(at.x, at.y, at.z, yaw, 0);
        v.yHeadRot = v.yBodyRot = yaw;
        v.hitlist = hitlist;
        v.quarry = player;
        v.home = at;
        level.addFreshEntity(v);
        return v;
    }

    // ------------------------------------------------------------------ queries

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    void setState(byte s) {
        entityData.set(DATA_STATE, s);
        stateT = 0;
    }

    public byte bodyPose() {
        return entityData.get(DATA_POSE);
    }

    public void setBodyPose(byte p) {
        entityData.set(DATA_POSE, p);
    }

    public boolean ego() {
        return entityData.get(DATA_EGO);
    }

    public void setEgo(boolean on) {
        entityData.set(DATA_EGO, on);
    }

    /** 0..1: how hard his eyes are burning (Red Gaze). */
    public float gaze() {
        return entityData.get(DATA_GAZE) / 10f;
    }

    public void setGaze(float g) {
        entityData.set(DATA_GAZE, (byte) Math.round(Mth.clamp(g, 0, 1) * 10));
    }

    public boolean barrier() {
        return entityData.get(DATA_BARRIER);
    }

    void setBarrier(boolean on) {
        entityData.set(DATA_BARRIER, on);
    }

    public float hp() {
        return getHealth() / getMaxHealth();
    }

    public boolean isDowned() {
        return downed > 0;
    }

    /** Tours: as if he'd already introduced himself. */
    public void introducedForTour() {
        introduced = true;
    }

    /** Tours: down for this many more ticks. */
    public void downedForTour(int ticks) {
        downed = ticks;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    ServerPlayer player(@Nullable UUID id) {
        return id == null || getServer() == null ? null : getServer().getPlayerList().getPlayer(id);
    }

    @Nullable
    public ServerPlayer quarryPlayer() {
        return player(quarry);
    }

    /** The share of his boss damage he deals now: all of it as a boss, less beside you. */
    float dmg() {
        float k = Config.VERGILIUS_DAMAGE.get().floatValue();
        return switch (state()) {
            case ALLY -> k * 0.6f;
            case COMPANION -> k * 0.5f;
            default -> k;
        };
    }

    /** Fighting for someone (the rematch, or following them): he doesn't hurt players then. */
    public boolean friendly() {
        return state() == ALLY || state() == COMPANION;
    }

    void note(String what) {
        log.add(tickCount + " " + what + " " + Math.round(hp() * 100) + "%");
        if (log.size() > 400) log.remove(0);
    }

    void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        if (d.x * d.x + d.z * d.z < 1e-4) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yHeadRot = yaw;
    }

    void sound(SoundEvent s, float volume, float pitch) {
        level().playSound(null, getX(), getY() + 1, getZ(), s, SoundSource.HOSTILE, volume, pitch);
    }

    void say(int line) {
        Network.sendNear((ServerLevel) level(), position(), 96, FxPayload.at(FxPayload.BOSS_SAY, getEyePosition()).to(new Vec3(SPEAKER, 0, 0)).data(line));
    }

    /** A line only `p` hears (a companion's barks). */
    void sayTo(ServerPlayer p, int line) {
        Network.sendTo(p, FxPayload.at(FxPayload.BOSS_SAY, getEyePosition()).to(new Vec3(SPEAKER, 0, 0)).data(line));
    }

    void fx(Vec3 a, Vec3 b, int... ints) {
        Network.sendNear((ServerLevel) level(), position(), 128, ArsenalFxPayload.of(ArsenalFxPayload.VERGILIUS, a, b, ints));
    }

    void fxTo(ServerPlayer p, Vec3 a, Vec3 b, int... ints) {
        Network.sendTo(p, ArsenalFxPayload.of(ArsenalFxPayload.VERGILIUS, a, b, ints));
    }

    ItemStack hitlistStack(@Nullable ServerPlayer p) {
        return p == null || hitlist == null ? ItemStack.EMPTY : Hitlist.find(p, hitlist);
    }

    void setHitlistStage(int stage) {
        ItemStack s = hitlistStack(quarryPlayer());
        if (!s.isEmpty()) Hitlist.setStage(s, stage);
    }

    // ------------------------------------------------------------------ brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        stateT++;
        if (getGaze() > 0 && (move == null || !(move instanceof VergiliusMoves.Gaze))) setGaze(Math.max(0, gaze() - 0.1f));
        switch (state()) {
            case MEET -> tickMeet(level);
            case FIGHT -> tickFight(level);
            case RETREAT, WITHDRAW -> tickBus(level);
            case ALLY -> tickAlly(level);
            case COMPANION -> tickCompanion(level);
            default -> {}
        }
        if (tickCount % 10 == 0) syncBossBar(level);
        bossBar.setProgress(Math.max(0, hp()));
    }

    private float getGaze() {
        return gaze();
    }

    // ---- MEET: waiting at the scene, hand in his pocket

    private void tickMeet(ServerLevel level) {
        getNavigation().stop();
        setBodyPose(POSE_STAND);
        // spawned from an egg or a command: whoever comes close is the one he waits for
        if (quarry == null && hitlist == null && tickCount % 10 == 0) {
            Player near = level.getNearestPlayer(this, 12);
            if (near != null && !near.isSpectator()) quarry = near.getUUID();
        }
        ServerPlayer p = quarryPlayer();
        // the hitlist that brought you here is gone (lost, thrown away): so is he
        if (tickCount % 200 == 0 && p != null && hitlistStack(p).isEmpty() && hitlist != null) {
            discard();
            return;
        }
        if (p == null || p.level() != level || p.isSpectator()) return;
        double d = p.distanceTo(this);
        if (d < 20) getLookControl().setLookAt(p, 20, 20);
        if (d > 64) {
            introduced = false;
            choosing = false;
            return;
        }
        if (!introduced && d < 9 && hasLineOfSight(p)) {
            introduced = true;
            choiceT = 0;
            face(p.position());
            setHitlistStage(Hitlist.MET);
            // [kind, him, the player whose camera it is]
            fx(position(), Vec3.directionFromRotation(0, getYRot()), VergiliusMoves.FX_INTRO, getId(), p.getId());
            note("intro");
        }
        if (introduced) {
            choiceT++;
            if (choiceT == 24) say(Roland.BANNER);
            if (choiceT == 40) say(LINE_INTRO);
            if (choiceT == 90) offerChoice(p);
            // you stood there without answering: he takes that as an answer
            if (choosing && choiceT > 90 + 1200 && d < 24) choose(p, true);
        }
    }

    void offerChoice(ServerPlayer p) {
        choosing = true;
        fxTo(p, position(), Vec3.ZERO, VergiliusMoves.FX_CHOICE, getId());
    }

    /** The answer from the choice screen: draw on him (`draw`) or show him the hitlist. */
    public void choose(ServerPlayer p, boolean draw) {
        if (state() != MEET || !p.getUUID().equals(quarry) || !introduced) return;
        choosing = false;
        face(p.position());
        if (draw) {
            say(LINE_DRAW);
            startFight(p);
            return;
        }
        // one Vergilius walks with you already: this one has nothing to fight beside you for
        if (companionOf(p) != null) {
            say(LINE_ALREADY);
            setHitlistStage(Hitlist.DONE);
            burnHitlist(p, false);
            completeTrail();
            callBus(WITHDRAW);
            return;
        }
        say(LINE_WARN);
        setState(ALLY);
        setHealth(getMaxHealth());
        Claw c = ModEntities.CLAW.get().create(level());
        if (c == null) return;
        // he comes out a little way off, between you, angrier than last time
        Vec3 mid = p.position().add(position()).scale(0.5), across = p.position().subtract(position()).multiply(1, 0, 1);
        across = across.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : new Vec3(-across.z, 0, across.x).normalize();
        Vec3 at = mid.add(across.scale(6));
        c.moveTo(at.x, p.getY(), at.z, 0, 0);
        ServerLevel level = (ServerLevel) level();
        for (int dy = 0; dy < 4 && !level.noCollision(c); dy++) c.moveTo(at.x, p.getY() + dy + 1, at.z, 0, 0);
        c.setRebuilt(true);
        c.ally = getUUID();
        c.arrive(p);
        c.face(p.position());
        float hp = (float) (Config.CLAW_HEALTH.get() * Config.REBUILT_CLAW_HEALTH.get());
        c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        c.setHealth(hp);
        level.addFreshEntity(c);
        claw = c.getUUID();
        note("warned");
    }

    void startFight(ServerPlayer p) {
        quarry = p.getUUID();
        setState(FIGHT);
        float hp = Config.VERGILIUS_HEALTH.get().floatValue();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth(hp);
        cooldown = 30;
        manifested = false;
        java.util.Arrays.fill(cd, 0);
        cd[VergiliusMoves.CD_FUNERAL] = 0;
        note("fight");
    }

    // ---- FIGHT

    private void tickFight(ServerLevel level) {
        ServerPlayer p = quarryPlayer();
        boolean here = p != null && p.isAlive() && p.level() == level && p.distanceToSqr(this) < 64 * 64 && !p.isCreative() && !p.isSpectator();
        if (!here) {
            getNavigation().stop();
            VergiliusMoves.drop(this);
            // the one he fought fell (or left): with a hitlist, he goes back to waiting; a betrayal, he simply leaves
            if (p != null && !p.isAlive() || ++lostTicks > 600) {
                lostTicks = 0;
                say(LINE_RESET);
                if (hitlist == null) callBus(WITHDRAW);
                else reset();
            }
            return;
        }
        lostTicks = 0;
        VergiliusMoves.tick(this, level, p);
    }

    /** Back to waiting at the scene, whole again (you can come back and choose again). */
    void reset() {
        setState(MEET);
        setEgo(false);
        setBarrier(false);
        lake = null;
        fx(position(), Vec3.ZERO, VergiliusMoves.FX_LAKE_OFF, getId());
        setHealth(getMaxHealth());
        introduced = false;
        choosing = false;
        move = null;
        setBodyPose(POSE_STAND);
        setHitlistStage(Hitlist.SCENE);
        if (home != null) teleportTo(home.x, home.y, home.z);
        note("reset");
    }

    // ---- ALLY: the rematch, side by side

    private void tickAlly(ServerLevel level) {
        Claw c = claw == null ? null : level.getEntity(claw) instanceof Claw found ? found : null;
        if (c == null || !c.isAlive()) {
            // gone without being beaten (lost, or unloaded): he leaves too
            onRematchLost();
            return;
        }
        if (tickDowned()) return;
        if (c.state() != Claw.FIGHT) {
            getNavigation().stop();
            if (c.state() == Claw.ARRIVE) getLookControl().setLookAt(c, 30, 30);
            return;
        }
        VergiliusMoves.tick(this, level, c);
    }

    /** The rebuilt Claw was beaten: he walks with you now. */
    public void onClawBeaten() {
        if (state() != ALLY) return;
        ServerPlayer p = quarryPlayer();
        VergiliusMoves.drop(this);
        setState(COMPANION);
        owner = quarry;
        setHealth(getMaxHealth());
        downed = 0;
        setBodyPose(POSE_STAND);
        say(LINE_JOIN);
        if (p != null) {
            setCompanion(p, getUUID());
            setHitlistStage(Hitlist.DONE);
            burnHitlist(p, true);
        }
        completeTrail();
        note("companion");
    }

    /** The rematch was lost (you fell, or the Claw left): he drives off; the hitlist leads back here later. */
    public void onRematchLost() {
        if (state() != ALLY) return;
        say(LINE_WITHDRAW);
        ServerPlayer p = quarryPlayer();
        ItemStack s = hitlistStack(p);
        if (!s.isEmpty()) Hitlist.edit(s, t -> {
            t.putInt("Stage", Hitlist.SCENE);
            t.putLong("Back", level().getGameTime() + 2400);
        });
        callBus(WITHDRAW);
    }

    /** Down on a knee instead of dying (ALLY, COMPANION): up again after a while. True while down. */
    boolean tickDowned() {
        if (downed <= 0) return false;
        getNavigation().stop();
        setBodyPose(POSE_KNEEL);
        if (--downed == 0) {
            setHealth(getMaxHealth() * (state() == ALLY ? 0.4f : 0.5f));
            setBodyPose(POSE_STAND);
            sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.7f);
        }
        return true;
    }

    // ---- COMPANION

    private void tickCompanion(ServerLevel level) {
        if (tickDowned()) return;
        ServerPlayer o = player(owner);
        if (o == null || o.isSpectator()) {
            getNavigation().stop();
            setBodyPose(POSE_STAND);
            return;
        }
        // somewhere else entirely: he catches up
        if (!staying && (o.level() != level || o.distanceToSqr(this) > 24 * 24) && o.onGround() && tickCount % 20 == 0) {
            followTo(o);
            return;
        }
        LivingEntity t = companionTarget(o);
        if (t != null) {
            VergiliusMoves.tick(this, level, t);
            return;
        }
        if (move != null) VergiliusMoves.drop(this);
        setBodyPose(POSE_STAND);
        double d = distanceTo(o);
        if (!staying && d > 5) getNavigation().moveTo(o, d > 10 ? 1.3 : 1.0);
        else getNavigation().stop();
        if (d < 12) getLookControl().setLookAt(o, 10, 10);
        if (--nextBark <= 0) {
            nextBark = 3000 + random.nextInt(3000);
            if (d < 16) sayTo(o, LINE_BARK + random.nextInt(BARKS));
        }
    }

    /** Whoever the owner is fighting: what hit them, or what they hit (never a player, a pet of theirs, or himself). */
    @Nullable
    LivingEntity companionTarget(ServerPlayer o) {
        for (LivingEntity t : new LivingEntity[]{o.getLastHurtByMob(), o.getLastHurtMob(), getLastHurtByMob()}) {
            if (t == null || !t.isAlive() || t == this || t == o || t instanceof Player || t.level() != level()) continue;
            if (t instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(o)) continue;
            if (t instanceof Vergilius) continue;
            if (t.distanceToSqr(o) > 24 * 24) continue;
            return t;
        }
        return null;
    }

    void followTo(ServerPlayer o) {
        Vec3 back = o.position().subtract(Vec3.directionFromRotation(0, o.getYRot()).scale(2.5));
        if (o.level() != level()) {
            changeDimension(new DimensionTransition((ServerLevel) o.level(), back, Vec3.ZERO, o.getYRot(), 0, DimensionTransition.DO_NOTHING));
            return;
        }
        teleportTo(back.x, o.getY(), back.z);
        getNavigation().stop();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        if (state() == MEET && introduced && sp.getUUID().equals(quarry)) {
            offerChoice(sp);
            return InteractionResult.SUCCESS;
        }
        if (state() == COMPANION && sp.getUUID().equals(owner) && sp.isShiftKeyDown()) {
            staying = !staying;
            getNavigation().stop();
            sayTo(sp, staying ? LINE_STAY : LINE_FOLLOW);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** A blow `o` meant for him: their own arrow or the like, or a swing with him right under their aim. */
    boolean aimedAt(ServerPlayer o, DamageSource source) {
        if (source.getDirectEntity() != o) return source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
        Vec3 eye = o.getEyePosition(), reach = eye.add(o.getLookAngle().scale(o.entityInteractionRange() + 1));
        return getBoundingBox().inflate(0.2).clip(eye, reach).isPresent();
    }

    /** Turned on by the one he walked with: a line, and the fight he'd hoped not to have. */
    void betray(ServerPlayer o) {
        VergiliusMoves.drop(this);
        setCompanion(o, null);
        owner = null;
        hitlist = null;
        staying = false;
        downed = 0;
        say(LINE_BETRAY);
        face(o.position());
        startFight(o);
        cooldown = 40;
        note("betrayed");
    }

    // ---- the bus: RETREAT (beaten: drops) and WITHDRAW (leaving)

    /** The Limbus bus is on its way for him. */
    void callBus(byte how) {
        VergiliusMoves.drop(this);
        setState(how);
        setBarrier(false);
        setGaze(0);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setBodyPose(how == RETREAT ? POSE_KNEEL : POSE_STAND);
        note(how == RETREAT ? "retreat" : "withdraw");
    }

    private void tickBus(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
        boolean beaten = state() == RETREAT;
        ServerPlayer p = quarryPlayer();
        if (beaten && stateT == 10 && !dropped) {
            dropped = true;
            // what he leaves behind: the gladius, dimmed, and the eyes
            fling(level, ModItems.WHITE_HOT_GLADIUS.get(), 0.5, 0.6, 0.6, new Vec3(0.12, 0.22, 0.16));
            fling(level, ModItems.VERGILIUS_EYES.get(), -0.2, 1.4, 0.4, new Vec3(-0.06, 0.24, 0.14));
            sound(SoundEvents.ANVIL_LAND, 0.6f, 1.4f);
            if (hitlist != null) {
                setHitlistStage(Hitlist.DONE);
                if (p != null) burnHitlist(p, false);
                completeTrail();
            }
        }
        if (stateT == B_CALL) {
            lane = clearLane(level);
            // stopped with its door to him, two and a half blocks out
            Vec3 door = doorSide(lane);
            busStop = position().subtract(door.scale(2.45)).subtract(lane.scale(0.42));
            bus = ModEntities.LIMBUS_BUS.get().create(level);
            if (bus != null) {
                Vec3 from = busStop.subtract(lane.scale(46));
                float yaw = (float) Math.toDegrees(Math.atan2(-lane.x, lane.z));
                bus.moveTo(from.x, busStop.y, from.z, yaw, 0);
                level.addFreshEntity(bus);
            }
            fx(position(), lane, VergiliusMoves.FX_BUS, getId(), bus == null ? -1 : bus.getId(), p == null ? -1 : p.getId());
            level.playSound(null, busStop.x - lane.x * 30, busStop.y + 1, busStop.z - lane.z * 30, ModSounds.BUS_HORN.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
        }
        if (bus != null && busStop != null && lane != null) driveBus(level);
        if (stateT == B_STAND) setBodyPose(POSE_STAND);
        if (stateT == B_LINE) say(beaten ? LINE_RETREAT : LINE_WITHDRAW);
        if (stateT >= B_WALK && stateT < B_IN && busStop != null && lane != null) {
            // to the door, and in
            Vec3 doorAt = busStop.add(doorSide(lane).scale(1.25)).add(lane.scale(0.42));
            Vec3 to = position().lerp(doorAt, 0.18);
            face(doorAt);
            setPos(to.x, getY(), to.z);
        }
        if (stateT == B_IN) setInvisible(true);
        if (stateT >= B_GONE) {
            if (bus != null) bus.discard();
            discard();
        }
    }

    /** This hitlist's trail is over: the clues come away, and no copy of it brings him back. */
    void completeTrail() {
        if (hitlist != null && getServer() != null) HitlistTrail.finish(getServer().overworld(), hitlist);
    }

    int hitlistStageNow() {
        ItemStack s = hitlistStack(quarryPlayer());
        return s.isEmpty() ? Hitlist.DONE : Hitlist.stage(s);
    }

    /** The bus, by the clock: rolls in slowing to a stop, the door, then off and away, fading. */
    private void driveBus(ServerLevel level) {
        int t = stateT;
        Vec3 at;
        if (t <= B_STOP) {
            float k = Mth.clamp((t - B_CALL) / (float) (B_STOP - B_CALL), 0, 1);
            // fast, then braking hard over the last stretch
            float e = 1 - (1 - k) * (1 - k) * (1 - k);
            at = busStop.subtract(lane.scale(46 * (1 - e)));
            if (t > B_STOP - 12 && t % 2 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 6, 1.4, 0.1, 1.4, 0.04);
            if (t == B_STOP - 8) level.playSound(null, at.x, at.y, at.z, SoundEvents.MINECART_RIDING, SoundSource.HOSTILE, 1.5f, 0.6f);
        } else if (t < B_GO) {
            at = busStop;
        } else {
            float k = (t - B_GO) / 20f;
            at = busStop.add(lane.scale(k * k * 6));
            if (t == B_GO) level.playSound(null, at.x, at.y, at.z, ModSounds.BUS_ENGINE.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        }
        bus.setPos(at.x, at.y, at.z);
        if (t >= B_OPEN && t < B_OPEN + 6) bus.setDoor((t - B_OPEN) / 5f);
        if (t >= B_SHUT && t < B_SHUT + 6) bus.setDoor(1 - (t - B_SHUT) / 5f);
        if (t >= B_FADE) bus.setFade((t - B_FADE) / (float) (B_GONE - B_FADE));
        if (t == B_OPEN) level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 1.2f, 0.7f);
        if (t == B_SHUT) level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.2f, 0.7f);
    }

    /** Which side of the bus its door is on, for a bus heading along `lane` (LimbusBusRenderer: the left flank). */
    static Vec3 doorSide(Vec3 lane) {
        return new Vec3(-lane.z, 0, lane.x);
    }

    /** The heading (flat, unit) with the fewest blocks in the way, over the bus's run through where he stands. */
    Vec3 clearLane(ServerLevel level) {
        Vec3 best = Vec3.directionFromRotation(0, getYRot() + 90);
        int bestCount = Integer.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            Vec3 d = Vec3.directionFromRotation(0, i * 22.5f);
            int count = 0;
            for (int k = -46; k <= 20; k += 2) {
                for (int y = 1; y <= 3; y += 2) {
                    BlockPos bp = BlockPos.containing(getX() + d.x * k, getY() + y, getZ() + d.z * k);
                    if (level.isLoaded(bp) && !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) count++;
                }
            }
            if (count < bestCount) {
                bestCount = count;
                best = d;
            }
        }
        return best;
    }

    private void fling(ServerLevel level, net.minecraft.world.item.Item item, double left, double up, double ahead, Vec3 push) {
        Vec3 f = Vec3.directionFromRotation(0, getYRot()), l = new Vec3(f.z, 0, -f.x);
        Vec3 at = position().add(l.scale(left)).add(f.scale(ahead)).add(0, up, 0);
        ItemEntity it = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item));
        it.setDeltaMovement(l.scale(push.x).add(f.scale(push.z)).add(0, push.y, 0));
        it.setPickUpDelay(30);
        level.addFreshEntity(it);
    }

    /** The trail is over: the hitlist burns away in your hands (`joined`: crossed out instead, he keeps you company). */
    void burnHitlist(ServerPlayer p, boolean joined) {
        ItemStack s = hitlistStack(p);
        if (s.isEmpty()) return;
        s.shrink(1);
        ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        p.level().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
        p.displayClientMessage(Component.translatable(joined ? "message.projectminecraft.hitlist.crossed" : "message.projectminecraft.hitlist.burned").withStyle(ChatFormatting.DARK_RED), false);
    }

    // ------------------------------------------------------------------ the companion, on the player

    static final String COMPANION_KEY = "ProjectMinecraftVergilius";

    /** Kept in the part of the player's data that survives death. */
    static CompoundTag kept(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    @Nullable
    public static UUID companionOf(ServerPlayer p) {
        CompoundTag t = kept(p);
        return t.hasUUID(COMPANION_KEY) ? t.getUUID(COMPANION_KEY) : null;
    }

    static void setCompanion(ServerPlayer p, @Nullable UUID id) {
        if (id == null) kept(p).remove(COMPANION_KEY);
        else kept(p).putUUID(COMPANION_KEY, id);
    }

    // ------------------------------------------------------------------ bar

    private void syncBossBar(ServerLevel level) {
        boolean show = state() == FIGHT;
        for (ServerPlayer p : level.players()) {
            if (show && p.distanceToSqr(this) < 64 * 64) bossBar.addPlayer(p);
            else bossBar.removePlayer(p);
        }
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        Entity by = source.getEntity();
        switch (state()) {
            case MEET -> {
                // waiting: a blow from the one he's waiting for is an answer; anything else he ignores
                if (by instanceof ServerPlayer sp && sp.getUUID().equals(quarry) && !sp.isCreative()) {
                    introduced = true;
                    choose(sp, true);
                }
                return false;
            }
            case RETREAT, WITHDRAW -> {
                return false;
            }
            case ALLY, COMPANION -> {
                if (downed > 0) return false;
                if (state() == COMPANION && by instanceof ServerPlayer sp && sp.getUUID().equals(owner)) {
                    // your blows do nothing to him. Only meant ones count (aimed at him, not a sweep that caught him, not
                    // something else's): three within five seconds is a betrayal; the second gets a warning
                    if (!aimedAt(sp, source)) return false;
                    ownerHits.add(tickCount);
                    ownerHits.removeIf(t -> tickCount - t > 100);
                    if (ownerHits.size() >= 3) betray(sp);
                    else if (ownerHits.size() == 2) sayTo(sp, LINE_WARNING);
                    return false;
                }
                // your side: players don't hurt him
                if (by instanceof Player) return false;
                boolean hit = super.hurt(source, amount);
                if (getHealth() <= getMaxHealth() * (state() == ALLY ? 0.2f : 0.1f)) {
                    VergiliusMoves.drop(this);
                    downed = state() == ALLY ? 160 : 600;
                    say(LINE_DOWNED);
                    fx(position().add(0, 1, 0), Vec3.ZERO, VergiliusMoves.FX_DOWNED, getId());
                }
                return hit;
            }
            default -> {}
        }
        // FIGHT
        if (!VergiliusMoves.canBeHurt(this, source)) return false;
        if (invulnerableTime > 10 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN) && amount <= lastHurt) return false;
        amount = VergiliusMoves.adjustIncoming(this, (ServerLevel) level(), source, amount);
        if (amount <= 0) return false;
        // he doesn't go down: actuallyHurt stops at his last point of health, after armour
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && getHealth() <= 1.001f) {
            setHealth(1);
            fx(position(), Vec3.ZERO, VergiliusMoves.FX_LAKE_OFF, getId());
            callBus(RETREAT);
        }
        return hit;
    }

    /** Whatever gets through his armour, he keeps his last point of health (he leaves instead of dying). */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (!level().isClientSide && getHealth() < 1 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) setHealth(1);
    }

    @Override
    public void remove(RemovalReason reason) {
        // a companion gone for good (killed, /kill, discarded): his owner may find another
        if (!level().isClientSide && reason.shouldDestroy() && state() == COMPANION) {
            ServerPlayer o = player(owner);
            if (o != null && getUUID().equals(companionOf(o))) setCompanion(o, null);
        }
        bossBar.removeAllPlayers();
        if (bus != null && !bus.isRemoved()) bus.discard();
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
        return state() == COMPANION;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return state() == COMPANION;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (friendly() && other instanceof Player pl && (pl.getUUID().equals(quarry) || pl.getUUID().equals(owner))) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // he picks his own fights (VergiliusMoves); vanilla targeting would have him chase the nearest player
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("State", state());
        if (quarry != null) tag.putUUID("Quarry", quarry);
        if (owner != null) tag.putUUID("Owner", owner);
        if (hitlist != null) tag.putUUID("Hitlist", hitlist);
        tag.putBoolean("Staying", staying);
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Quarry")) quarry = tag.getUUID("Quarry");
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        if (tag.hasUUID("Hitlist")) hitlist = tag.getUUID("Hitlist");
        staying = tag.getBoolean("Staying");
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        byte s = tag.getByte("State");
        // a fight, a rematch or a ride interrupted by a reload doesn't resume: waiting again (or following, or gone)
        switch (s) {
            case COMPANION -> entityData.set(DATA_STATE, COMPANION);
            case RETREAT, WITHDRAW -> discard();
            default -> {
                // a fight interrupted: back where he waits
                if (home != null) setPos(home.x, home.y, home.z);
                entityData.set(DATA_STATE, MEET);
            }
        }
    }
}
