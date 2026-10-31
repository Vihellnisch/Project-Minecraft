package com.slop.projectminecraft.boss;

import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.FxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Roland, the Black Silence, as the Library receives him. Five phases, after the reception in Library of Ruina:
 * <ol>
 * <li>The Black Silence: every one of his workshop weapons, one page at a time, and once all nine are spent, Furioso.</li>
 * <li>Distorted: he breaks into a crawling beast of flesh, tendrils and smoke, and drags the fight into a factory,
 * with four Smouldering Byproducts and a haze that suffocates.</li>
 * <li>The distortion settles: Roland, wreathed in smoke, and Angelica out of white mist behind him, bound by Soul Link.
 * Bring one down and the other holds them here, unless you bring both down together.</li>
 * <li>The Black Silence again, alone: memories of every guest he's fought, and his weapons rising behind him. The ones
 * you don't break are nailed into the floor and feed his next Furioso.</li>
 * <li>The Images of the Bygones: he crouches, untouchable, while four images of old guests fight for him, then
 * screams.</li>
 * </ol>
 * Never breaks a block. Resets when the guests leave.
 */
public class Roland extends Monster {
    public static final byte IDLE = 0, INTRO = 1, FIGHT = 2, SHIFT = 3;
    public static final byte BLACK_SILENCE = 1, DISTORTED = 2, BOND = 3, WEAPONS = 4, BYGONES = 5;
    /** What his body is doing, for the renderer. */
    public static final byte POSE_STAND = 0, POSE_CROUCH = 1, POSE_MIST = 2, POSE_GUARD = 3, POSE_PARALYSED = 4;
    /** Subtitle lines (lang: projectminecraft.boss.roland.line.N); BANNER is the name card. */
    public static final int LINE_WELCOME = 0, LINE_BEGIN = 1, LINE_DISTORT = 2, LINE_SETTLE = 3, LINE_WEAPONS = 4, LINE_BYGONES = 5,
            LINE_FURIOSO = 6, LINE_FALL = 7, LINE_DEATH = 8, BANNER = 9, LINE_RESET = 10, LINE_SCREAM = 11;
    public static final int SPEAKER = 0;
    static final float[] PHASE_HEALTH = {0, 300, 500, 300, 400, 400};
    static final float[] PHASE_SPEED = {0, 0.34f, 0.24f, 0.36f, 0.36f, 0.36f};
    public static final int INTRO_TICKS = 80, SHIFT_TICKS = 110, DEATH_TICKS = 110;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Roland.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(Roland.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(Roland.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WEAPON = SynchedEntityData.defineId(Roland.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_OFF = SynchedEntityData.defineId(Roland.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_SHIFT_AT = SynchedEntityData.defineId(Roland.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projectminecraft.roland"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    int slot = -1;
    @Nullable
    BlockPos home;
    private float homeYaw;
    @Nullable
    LivingEntity target;
    private int introT, shiftT, noGuestTicks;
    private byte shiftFrom;
    private long lastActive = -1;

    // ---- moves (RolandMoves)
    @Nullable
    RolandMoves.Move move;
    final List<RolandMoves.Move> background = new ArrayList<>();
    int cooldown = 30;
    /** Per-move cooldowns for the phase he's in (RolandMoves names the slots). */
    final int[] cd = new int[12];
    /** Phase 1: which of the nine pages have been used, and how many pages so far. */
    int pagesUsed, pageCount;
    /** Phase 2: Suffocating Haze stacks per guest, when each next chokes them; the byproducts' respawns left. */
    final Map<UUID, Integer> haze = new HashMap<>();
    final Map<UUID, Long> hazeNext = new HashMap<>();
    final Map<UUID, Long> fragile = new HashMap<>();
    /** Phase 3: Soul Link stacks, Dark Mark, whether he's fallen to mist and when he comes back, moves made. */
    int links = 4, moveCount;
    long fallenUntil = -1;
    boolean paralysedOnce;
    int paralysed;
    final Map<UUID, Long> darkMark = new HashMap<>();
    @Nullable
    UUID angelica;
    /** Phase 4: the scene in the current cycle (0-3), when the next one comes, weapons nailed into the floor. */
    int scene;
    long nextScene;
    int nailed;
    /** Phase 5: Ready to Act until this tick (or the images are gone); then the Scream / Rage / Rage cycle. */
    long readyUntil = -1;
    int screamStep;
    final List<UUID> kin = new ArrayList<>();
    /** Recorded moves (tours read them): "tick name hp%". */
    public final List<String> log = new ArrayList<>();

    public Roland(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 800;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, PHASE_HEALTH[1])
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.MOVEMENT_SPEED, PHASE_SPEED[1])
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, IDLE).define(DATA_PHASE, BLACK_SILENCE).define(DATA_POSE, POSE_STAND).define(DATA_WEAPON, (byte) FuriosoScript.DURANDAL)
                .define(DATA_OFF, (byte) -1).define(DATA_SHIFT_AT, -1000);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ------------------------------------------------------------------ queries

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public byte phase() {
        return entityData.get(DATA_PHASE);
    }

    public byte bodyPose() {
        return entityData.get(DATA_POSE);
    }

    void setBodyPose(byte p) {
        entityData.set(DATA_POSE, p);
    }

    /** The workshop weapon in his right hand (both hands for the pairs), or -1. */
    public int weapon() {
        return entityData.get(DATA_WEAPON);
    }

    /** What his left hand holds when it isn't the right hand's pair, or -1. */
    public int offhand() {
        return entityData.get(DATA_OFF);
    }

    void hold(int w, int off) {
        if (w != weapon() && level() instanceof ServerLevel level) {
            // a weapon seeps out of the black mist
            Vec3 hand = position().add(0, 1.1, 0).add(Vec3.directionFromRotation(0, getYRot() + 40).scale(0.5));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y, hand.z, 8, 0.15, 0.15, 0.15, 0.01);
            level.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 6, 0.15, 0.15, 0.15, 0.02);
        }
        entityData.set(DATA_WEAPON, (byte) w);
        entityData.set(DATA_OFF, (byte) off);
    }

    public float shiftTime(float partial) {
        return state() != SHIFT ? -1 : tickCount - entityData.get(DATA_SHIFT_AT) + partial;
    }

    public byte shiftFrom() {
        return shiftFrom;
    }

    public float hp() {
        return getHealth() / getMaxHealth();
    }

    public void setHome(int slot, BlockPos pos, float yaw) {
        this.slot = slot;
        home = pos;
        homeYaw = yaw;
    }

    /** The middle of the floor the fight is on now. */
    BlockPos center() {
        if (slot < 0) return home != null ? home.offset(0, -3, 16) : blockPosition();
        return phase() == DISTORTED ? LibraryArena.factory(slot) : LibraryArena.center(slot);
    }

    List<ServerPlayer> guests() {
        return LibraryArena.guests((ServerLevel) level(), center());
    }

    void note(String what) {
        log.add(tickCount + " " + what + " " + Math.round(hp() * 100) + "% p" + phase());
        if (log.size() > 400) log.remove(0);
    }

    void face(float yaw) {
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yHeadRot = yaw;
    }

    void faceTarget() {
        if (target == null) return;
        Vec3 d = target.position().subtract(position());
        face((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
    }

    /** Straight into a phase, fighting (tours). */
    public void forcePhase(byte p) {
        endKin();
        noGuestTicks = 0;
        if (slot >= 0) {
            // onto the floor that phase is fought on
            Vec3 to = p == DISTORTED ? LibraryArena.factoryRoland(slot) : Vec3.atBottomCenterOf(LibraryArena.center(slot)).add(0, 1, -5);
            teleportTo(to.x, to.y, to.z);
        }
        setPhase(p);
        entityData.set(DATA_STATE, FIGHT);
        if (p == BOND) RolandMoves.spawnAngelica(this);
        if (p == DISTORTED) RolandMoves.spawnByproducts(this, 4);
        if (p == WEAPONS) nextScene = level().getGameTime() + 60;
    }

    /** Tours: put a weapon in his hand, and bring up a scene of weapons behind him now. */
    public void showWeapon(int w) {
        hold(w, -1);
    }

    public void sceneNowAndSpawn() {
        nextScene = 0;
        RolandMoves.scenes(this, (ServerLevel) level(), level().getGameTime());
    }

    /** Tours: Soul Link stacks (Roland's, then Angelica's), set directly. */
    public void setLinks(int roland, int angelicaLinks) {
        links = roland;
        RolandKin a = angelicaEntity();
        if (a != null) a.links = angelicaLinks;
    }

    public int links() {
        return links;
    }

    /** Tours: the number of the reception this Roland belongs to, how many weapons are nailed down, the next scene. */
    public int slot() {
        return slot;
    }

    public int nailedCount() {
        return nailed;
    }

    public void sceneNow() {
        nextScene = level().getGameTime();
    }

    public List<RolandKin> kinList() {
        List<RolandKin> out = new ArrayList<>();
        if (level() instanceof ServerLevel level) for (UUID id : kin) if (level.getEntity(id) instanceof RolandKin k && k.isAlive()) out.add(k);
        return out;
    }

    @Nullable
    public RolandKin angelicaEntity() {
        if (angelica == null || !(level() instanceof ServerLevel level)) return null;
        return level.getEntity(angelica) instanceof RolandKin k && k.isAlive() ? k : null;
    }

    public boolean fallen() {
        return fallenUntil >= 0;
    }

    // ------------------------------------------------------------------ the size of him

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        // the beast is a lot more of him
        if (phase() == DISTORTED) return EntityDimensions.scalable(4.6f, 3.9f).withEyeHeight(3.2f);
        return super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PHASE.equals(key)) refreshDimensions();
    }

    // ------------------------------------------------------------------ brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) home = blockPosition();
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        // a long gap: nobody kept his chunk ticking (they left or died); a short one is just a cinematic attack holding him
        if (state() != IDLE && lastActive >= 0 && now - lastActive > 600) reset(level);
        lastActive = now;
        background.removeIf(m -> {
            m.tick();
            return m.done();
        });
        switch (state()) {
            case IDLE -> {
                getNavigation().stop();
                face(homeYaw);
                if (tickCount % 5 == 0) {
                    for (ServerPlayer p : guests()) {
                        if (!p.isCreative() && p.distanceToSqr(Vec3.atBottomCenterOf(center())) < 15 * 15) {
                            startIntro(p);
                            break;
                        }
                    }
                }
            }
            case INTRO -> tickIntro(level);
            case FIGHT -> tickFight(level, now);
            case SHIFT -> tickShift(level);
            default -> {}
        }
        if (state() != IDLE && tickCount % 10 == 0) syncBossBar(level);
        bossBar.setProgress(state() == FIGHT || state() == SHIFT ? Math.max(0, hp()) : 1);
    }

    private void startIntro(Player p) {
        entityData.set(DATA_STATE, INTRO);
        introT = 0;
        target = p;
        setPhase(BLACK_SILENCE);
        note("intro");
    }

    private void tickIntro(ServerLevel level) {
        introT++;
        getNavigation().stop();
        if (target != null) getLookControl().setLookAt(target, 30, 30);
        if (introT == 4) say(LINE_WELCOME);
        if (introT == 36) {
            // he steps down from the desk
            say(LINE_BEGIN);
            Vec3 d = Vec3.atBottomCenterOf(center()).subtract(position()).multiply(1, 0, 1);
            Vec3 v = d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(0.5);
            setDeltaMovement(v.x, 0.45, v.z);
            hasImpulse = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        }
        if (introT == 60) say(BANNER);
        if (introT >= INTRO_TICKS) {
            entityData.set(DATA_STATE, FIGHT);
            cooldown = 10;
        }
    }

    private void tickFight(ServerLevel level, long now) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            reset(level);
            return;
        }
        List<ServerPlayer> guests = guests();
        guests.removeIf(Player::isCreative);
        if (guests.isEmpty()) {
            getNavigation().stop();
            if (++noGuestTicks > 200) reset(level);
            return;
        }
        noGuestTicks = 0;
        if (target == null || !target.isAlive() || !guests.contains(target) || tickCount % 40 == 0) {
            ServerPlayer near = null;
            for (ServerPlayer p : guests) if (near == null || p.distanceToSqr(this) < near.distanceToSqr(this)) near = p;
            target = near;
        }
        RolandMoves.tickPhase(this, level, now, guests);
    }

    // ------------------------------------------------------------------ phases

    void setPhase(byte p) {
        entityData.set(DATA_PHASE, p);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(PHASE_HEALTH[p]);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(PHASE_SPEED[p]);
        if (p != BYGONES) setHealth(getMaxHealth());
        bossBar.setName(Component.translatable("projectminecraft.boss.roland.phase." + p));
        bossBar.setColor(p == DISTORTED ? BossEvent.BossBarColor.RED : p == BOND ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.PURPLE);
        refreshDimensions();
        move = null;
        cooldown = 30;
        setBodyPose(POSE_STAND);
        hold(p == DISTORTED ? -1 : FuriosoScript.DURANDAL, -1);
    }

    /** Between phases: untouchable while the next one takes hold. */
    void beginShift(ServerLevel level) {
        entityData.set(DATA_STATE, SHIFT);
        entityData.set(DATA_SHIFT_AT, tickCount);
        shiftT = 0;
        shiftFrom = phase();
        move = null;
        background.clear();
        getNavigation().stop();
        note("shift" + shiftFrom);
        RolandMoves.shiftStart(this, level, shiftFrom);
    }

    private void tickShift(ServerLevel level) {
        shiftT++;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        RolandMoves.shiftTick(this, level, shiftFrom, shiftT);
        if (shiftT >= SHIFT_TICKS) {
            entityData.set(DATA_STATE, FIGHT);
            cooldown = 20;
        }
    }

    // ------------------------------------------------------------------ resets, bar, lines

    void endKin() {
        if (!(level() instanceof ServerLevel level)) return;
        for (UUID id : kin) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        kin.clear();
        angelica = null;
    }

    void reset(ServerLevel level) {
        say(LINE_RESET);
        endKin();
        move = null;
        background.clear();
        haze.clear();
        hazeNext.clear();
        fragile.clear();
        darkMark.clear();
        links = 4;
        fallenUntil = -1;
        paralysed = 0;
        paralysedOnce = false;
        pagesUsed = pageCount = moveCount = scene = nailed = screamStep = 0;
        readyUntil = -1;
        target = null;
        entityData.set(DATA_STATE, IDLE);
        setPhase(BLACK_SILENCE);
        bossBar.removeAllPlayers();
        setInvisible(false);
        if (home != null) {
            getNavigation().stop();
            teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            face(homeYaw);
        }
    }

    private void syncBossBar(ServerLevel level) {
        List<ServerPlayer> in = guests();
        for (ServerPlayer p : level.players()) {
            if (in.contains(p)) bossBar.addPlayer(p);
            else bossBar.removePlayer(p);
        }
    }

    void say(int line) {
        Network.sendNear((ServerLevel) level(), position(), 96, FxPayload.at(FxPayload.BOSS_SAY, getEyePosition()).to(new Vec3(SPEAKER, 0, 0)).data(line));
    }

    void fx(Vec3 a, Vec3 b, int... ints) {
        Network.sendNear((ServerLevel) level(), position(), 128, ArsenalFxPayload.of(ArsenalFxPayload.ROLAND, a, b, ints));
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        byte s = state();
        if (s == IDLE) {
            if (source.getEntity() instanceof Player p && !p.isSpectator() && !p.isCreative()) startIntro(p);
            return false;
        }
        if (s != FIGHT) return false;
        if (!RolandMoves.canBeHurt(this, source)) return false;
        amount = RolandMoves.adjustIncoming(this, source, amount);
        byte p = phase();
        // a phase can't be ended in one blow past its last sliver: the next one takes over instead. Judged before his
        // armour, so a blow that would have finished the phase does, even if the armour soaks part of it
        boolean lethal = p != BYGONES && amount >= getHealth() - 1;
        if (lethal) amount = Math.max(0, getHealth() - 1);
        boolean hit = super.hurt(source, amount);
        if (lethal && isAlive()) setHealth(Math.min(getHealth(), 1));
        RolandMoves.onHurt(this, (ServerLevel) level(), amount);
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !isRemoved()) {
            ServerLevel level = (ServerLevel) level();
            say(LINE_DEATH);
            bossBar.removeAllPlayers();
            endKin();
            hold(FuriosoScript.DURANDAL, -1);
            note("death");
            LibraryArena.victory(level, center());
            LibraryArena.rolandGone(level, slot);
        }
        super.die(source);
    }

    /** He kneels, Durandal down, the smoke falls away from his face, and he comes apart into black pages. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel level) {
            if (deathTime % 3 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 3, 0.4, 0.6, 0.4, 0.01);
            if (deathTime > DEATH_TICKS - 30) level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1, getZ(), 30, 0.5, 0.8, 0.5, 0.8);
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 120, 0.5, 0.9, 0.5, 0.15);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 3.0f, 0.5f);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
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
        return state() == FIGHT;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putLong("Home", home.asLong());
        tag.putFloat("HomeYaw", homeYaw);
        tag.putInt("Slot", slot);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = BlockPos.of(tag.getLong("Home"));
        homeYaw = tag.getFloat("HomeYaw");
        slot = tag.contains("Slot") ? tag.getInt("Slot") : -1;
        // a reception interrupted by a reload starts over, back at the desk
        entityData.set(DATA_STATE, IDLE);
        setPhase(BLACK_SILENCE);
        if (home != null) setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    }

    static EntityType<RolandKin> kinType() {
        return ModEntities.ROLAND_KIN.get();
    }
}
