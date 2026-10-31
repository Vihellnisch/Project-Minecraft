package com.slop.projectminecraft.boss;

import static com.slop.projectminecraft.arsenal.FuriosoScript.AXE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.GREATSWORD;
import static com.slop.projectminecraft.arsenal.FuriosoScript.LANCE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.MACE;
import static com.slop.projectminecraft.arsenal.FuriosoScript.PISTOLS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Those who fight beside the Black Silence: the Smouldering Byproducts his distortion spills out, Angelica out of white
 * mist, the weapons that rise behind him, and the Images of the Bygones. One entity, a kind for each.
 */
public class RolandKin extends Monster {
    public static final byte BYPRODUCT = 0, ANGELICA = 1, WEAPON = 2, IMAGE = 3;
    /** The four Images of the Bygones. */
    public static final int PULSATION = 0, ASPIRATION = 1, FERVENT = 2, LEARN = 3;
    static final float[] HEALTH = {40, 260, 50, 40};
    static final int F_NAILED = 1, F_FALLEN = 2, F_PARALYSED = 4;

    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(RolandKin.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(RolandKin.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(RolandKin.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WEAPON = SynchedEntityData.defineId(RolandKin.class, EntityDataSerializers.BYTE);

    @Nullable
    UUID ownerId;
    int links = 4, attackCd = 30, paralysed;
    long fallenUntil = -1;
    boolean respawned, saidFall;
    /** Angelica's part in a combo: {due tick, weapon, right hand}. */
    final List<long[]> orders = new ArrayList<>();
    /** Where a weapon was nailed. */
    @Nullable
    Vec3 nailedAt;

    public RolandKin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Nullable
    static RolandKin create(ServerLevel level, Roland owner, byte kind, int variant) {
        RolandKin k = Roland.kinType().create(level);
        if (k == null) return null;
        k.entityData.set(DATA_KIND, kind);
        k.entityData.set(DATA_VARIANT, (byte) variant);
        k.ownerId = owner.getUUID();
        k.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[kind]);
        k.setHealth(HEALTH[kind]);
        if (kind == ANGELICA) {
            k.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
            k.getAttribute(Attributes.ARMOR).setBaseValue(8);
            k.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.9);
        }
        if (kind == WEAPON) k.setNoGravity(true);
        k.refreshDimensions();
        owner.kin.add(k.getUUID());
        return k;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, BYPRODUCT).define(DATA_VARIANT, (byte) 0).define(DATA_FLAGS, (byte) 0).define(DATA_WEAPON, (byte) AXE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public byte kind() {
        return entityData.get(DATA_KIND);
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    private boolean flag(int f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    private void flag(int f, boolean on) {
        byte b = entityData.get(DATA_FLAGS);
        entityData.set(DATA_FLAGS, (byte) (on ? b | f : b & ~f));
    }

    public boolean nailed() {
        return flag(F_NAILED);
    }

    public boolean fallen() {
        return flag(F_FALLEN);
    }

    public boolean isParalysed() {
        return flag(F_PARALYSED);
    }

    public int links() {
        return links;
    }

    /** Angelica's weapon in hand. */
    public int weapon() {
        return entityData.get(DATA_WEAPON);
    }

    void hold(int w) {
        entityData.set(DATA_WEAPON, (byte) w);
    }

    @Nullable
    Roland owner() {
        if (ownerId == null || !(level() instanceof ServerLevel level)) return null;
        return level.getEntity(ownerId) instanceof Roland r && r.isAlive() ? r : null;
    }

    void say(int line) {
        RolandMoves.sayAs(this, RolandMoves.ANGELICA_SPEAKER, line);
    }

    void order(int[][] script, int delay) {
        long now = level().getGameTime();
        for (int[] s : script) orders.add(new long[]{now + delay + 8 + s[0], s[1], s[2]});
    }

    void paralyse(int ticks) {
        paralysed = ticks;
        flag(F_PARALYSED, true);
    }

    /** A weapon not broken in time is driven point-first into the floor somewhere on the stage. */
    void nail() {
        flag(F_NAILED, true);
        Roland r = owner();
        BlockPos c = r != null ? r.center() : blockPosition();
        double a = random.nextDouble() * Mth.TWO_PI, d = 6 + random.nextDouble() * 9;
        nailedAt = new Vec3(c.getX() + 0.5 + Math.cos(a) * d, c.getY() + 1, c.getZ() + 0.5 + Math.sin(a) * d);
        teleportTo(nailedAt.x, nailedAt.y, nailedAt.z);
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, nailedAt.x, nailedAt.y + 0.2, nailedAt.z, 12, 0.3, 0.1, 0.3, 0.02);
            level.playSound(null, nailedAt.x, nailedAt.y, nailedAt.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.6f);
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (kind()) {
            case BYPRODUCT -> EntityDimensions.scalable(1.4f, 2.6f).withEyeHeight(2.2f);
            case WEAPON -> EntityDimensions.scalable(0.9f, 1.8f).withEyeHeight(0.9f);
            default -> super.getDefaultDimensions(pose);
        };
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_KIND.equals(key)) refreshDimensions();
    }

    // ------------------------------------------------------------------ brains

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        Roland r = owner();
        // nothing of his outlives him, or a reset
        if (r == null || r.state() == Roland.IDLE) {
            discard();
            return;
        }
        if (attackCd > 0) attackCd--;
        LivingEntity t = r.target;
        switch (kind()) {
            case BYPRODUCT -> {
                if (t == null) return;
                double dist = distanceTo(t);
                getLookControl().setLookAt(t, 30, 30);
                if (dist > 2.2) {
                    if (tickCount % 5 == 0) getNavigation().moveTo(t, 1.2);
                } else if (attackCd <= 0) {
                    swing(InteractionHand.MAIN_HAND);
                    if (RolandMoves.damage(this, t, 5) && random.nextFloat() < 0.3f) RolandMoves.addHaze(r, t, 1);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.0f, 1.3f);
                    attackCd = 25;
                }
                if (tickCount % 6 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 2, 0.4, 0.3, 0.4, 0.01);
            }
            case ANGELICA -> angelica(level, r, t);
            case WEAPON -> {
                if (nailed()) {
                    if (nailedAt != null) setPos(nailedAt.x, nailedAt.y, nailedAt.z);
                    setDeltaMovement(Vec3.ZERO);
                    return;
                }
                // hovering behind him, turning slowly
                setYRot(getYRot() + 4);
                if (tickCount % 4 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.8, getZ(), 2, 0.15, 0.4, 0.15, 0.005);
                setDeltaMovement(0, Mth.sin(tickCount * 0.1f) * 0.02, 0);
            }
            case IMAGE -> image(level, r, t);
            default -> {}
        }
    }

    private void angelica(ServerLevel level, Roland r, @Nullable LivingEntity t) {
        long now = level.getGameTime();
        if (fallen()) {
            getNavigation().stop();
            if (now % 4 == 0) RolandMoves.dust(level, position().add(0, 0.8, 0), 0xF0F0F0, 8, 0.5, 2f);
            if (now >= fallenUntil) {
                if (links > 0) {
                    links--;
                    flag(F_FALLEN, false);
                    setHealth(getMaxHealth() * 0.5f);
                    level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.04);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0f, 1.2f);
                    r.note("angelica_revive " + links);
                } else fallenUntil = Long.MAX_VALUE;
            }
            return;
        }
        if (paralysed > 0) {
            paralysed--;
            getNavigation().stop();
            if (paralysed == 0) flag(F_PARALYSED, false);
            orders.clear();
            return;
        }
        if (now % 6 == 0) RolandMoves.dust(level, position().add(0, 1.4, 0), 0xF4F4F4, 2, 0.3, 1.2f);
        if (t == null) return;
        getLookControl().setLookAt(t, 30, 30);
        double dist = distanceTo(t);
        // her part in his combo
        if (!orders.isEmpty()) {
            long[] o = orders.get(0);
            int w = (int) o[1];
            double range = w == PISTOLS ? 12 : w == LANCE ? 5 : 3.2;
            hold(w);
            if (dist > range) {
                if (tickCount % 4 == 0) getNavigation().moveTo(t, 1.35);
            } else getNavigation().stop();
            if (now >= o[0] && (dist <= range + 1 || now > o[0] + 30)) {
                orders.remove(0);
                face(t);
                RolandMoves.strike(this, level, w, 1, t, o[2] == 1);
            }
            return;
        }
        // on her own: keep to his flank, and strike when you come close
        if (dist < 4.5 && attackCd <= 0) {
            int pick = random.nextInt(4);
            int[][] s = switch (pick) {
                case 0 -> new int[][]{{0, AXE, 1}, {8, MACE, 0}};
                case 1 -> new int[][]{{0, LANCE, 1}};
                case 2 -> new int[][]{{0, GREATSWORD, 1}};
                default -> new int[][]{{0, PISTOLS, 1}, {5, PISTOLS, 0}};
            };
            order(s, 0);
            attackCd = 70 + random.nextInt(30);
            return;
        }
        Vec3 fwd = Vec3.directionFromRotation(0, r.getYRot());
        Vec3 spot = r.position().add(fwd.yRot(-Mth.HALF_PI).scale(2.5)).subtract(fwd.scale(1.0));
        if (position().distanceTo(spot) > 2 && tickCount % 5 == 0) getNavigation().moveTo(spot.x, spot.y, spot.z, 1.2);
    }

    private void face(LivingEntity t) {
        Vec3 d = t.position().subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        setYRot(yaw);
        yBodyRot = yHeadRot = yaw;
    }

    private void image(ServerLevel level, Roland r, @Nullable LivingEntity t) {
        if (tickCount % 5 == 0) RolandMoves.dust(level, position().add(0, 1, 0), 0xD8D8E0, 2, 0.3, 1.2f);
        int v = variant();
        if (v == ASPIRATION) {
            // she stays by him, a shield
            if (distanceTo(r) > 3 && tickCount % 5 == 0) getNavigation().moveTo(r, 1.1);
            if (tickCount % 10 == 0) level.sendParticles(ParticleTypes.END_ROD, r.getX(), r.getY() + 1, r.getZ(), 2, 0.6, 0.8, 0.6, 0.01);
            return;
        }
        if (v == FERVENT && tickCount % 60 == 0) {
            // the beat that drives the others on
            for (RolandKin k : RolandMoves.kinOf(r, IMAGE)) if (k != this) k.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
        }
        if (t == null) return;
        double dist = distanceTo(t);
        getLookControl().setLookAt(t, 30, 30);
        if (dist > 2.2) {
            if (tickCount % 5 == 0) getNavigation().moveTo(t, 1.15);
        } else if (attackCd <= 0) {
            swing(InteractionHand.MAIN_HAND);
            if (RolandMoves.damage(this, t, 5)) {
                if (v == PULSATION) t.igniteForSeconds(3);
                if (v == LEARN) t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
            }
            attackCd = 30;
        }
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (source.getEntity() instanceof Roland || source.getEntity() instanceof RolandKin) return false;
        if (nailed() || fallen()) return false;
        if (kind() == ANGELICA) {
            if (isParalysed()) amount *= 1.5f;
            // Soul Link: she falls to mist instead, and comes back while the bond holds
            if (amount >= getHealth() - 1) {
                super.hurt(source, Math.max(0, getHealth() - 1));
                fall();
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    void fall() {
        flag(F_FALLEN, true);
        fallenUntil = level().getGameTime() + 160;
        orders.clear();
        getNavigation().stop();
        if (!saidFall) {
            say(RolandMoves.ANGELICA_LINE_FALL);
            saidFall = true;
        }
        if (level() instanceof ServerLevel level) RolandMoves.dust(level, position().add(0, 1, 0), 0xFFFFFF, 50, 0.5, 2.2f);
        Roland r = owner();
        if (r != null) {
            r.note("angelica_fallen " + links);
            RolandMoves.checkBothDown(r);
        }
    }

    @Override
    public void die(DamageSource source) {
        Roland r = owner();
        // a byproduct crawls back out of the smoke once, while he's still strong
        if (!level().isClientSide && kind() == BYPRODUCT && !respawned && r != null && r.phase() == Roland.DISTORTED && r.hp() > 0.4f) {
            Vec3 at = position();
            r.background.add(new RolandMoves.Move(r) {
                @Override
                void step(ServerLevel level) {
                    if (age == 60) {
                        RolandKin k = create(level, r, BYPRODUCT, 0);
                        if (k == null) return;
                        k.respawned = true;
                        k.setHealth(HEALTH[BYPRODUCT] * 0.5f);
                        k.moveTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
                        level.addFreshEntity(k);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 20, 0.4, 0.4, 0.4, 0.02);
                        r.note("byproduct_back");
                    }
                }

                @Override
                boolean done() {
                    return age >= 60;
                }
            });
        }
        if (!level().isClientSide && level() instanceof ServerLevel level) {
            level.sendParticles(kind() == WEAPON ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8, getZ(), 20, 0.3, 0.5, 0.3, 0.04);
            if (kind() == WEAPON) level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        // gone in a puff, no lying about
        if (++deathTime >= 2 && !isRemoved()) remove(RemovalReason.KILLED);
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
        return kind() != BYPRODUCT;
    }

    @Override
    public boolean isPushable() {
        return kind() != WEAPON;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", kind());
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, tag.getByte("Kind"));
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
    }
}
