package com.slop.projectminecraft.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Mephistopheles, the Limbus bus, come to pick Vergilius up: a scripted effect, not a vehicle. It never collides, never
 * breaks a block and is never saved; whoever spawned it drives it (moves it each tick) and discards it.
 */
public class LimbusBus extends Entity {
    /** How far open the side door is, 0..10. */
    private static final EntityDataAccessor<Byte> DATA_DOOR = SynchedEntityData.defineId(LimbusBus.class, EntityDataSerializers.BYTE);
    /** 0..10: how faded out it is as it drives off. */
    private static final EntityDataAccessor<Byte> DATA_FADE = SynchedEntityData.defineId(LimbusBus.class, EntityDataSerializers.BYTE);
    /** Client: how far the wheels have turned (radians), from how far it moved. */
    public float wheelSpin, wheelSpinO;

    public LimbusBus(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_DOOR, (byte) 0).define(DATA_FADE, (byte) 0);
    }

    public float door() {
        return entityData.get(DATA_DOOR) / 10f;
    }

    public void setDoor(float d) {
        entityData.set(DATA_DOOR, (byte) Math.round(Math.max(0, Math.min(1, d)) * 10));
    }

    public float fade() {
        return entityData.get(DATA_FADE) / 10f;
    }

    public void setFade(float f) {
        entityData.set(DATA_FADE, (byte) Math.round(Math.max(0, Math.min(1, f)) * 10));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            wheelSpinO = wheelSpin;
            double moved = Math.sqrt(Math.pow(getX() - xo, 2) + Math.pow(getZ() - zo, 2));
            // wheel radius ~0.55 blocks
            wheelSpin += (float) (moved / 0.55);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
