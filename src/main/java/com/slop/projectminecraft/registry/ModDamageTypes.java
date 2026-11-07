package com.slop.projectminecraft.registry;

import com.slop.projectminecraft.ProjectMinecraft;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Damage types are data driven: see data/projectminecraft/damage_type/*.json. */
public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> ULTIMATE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectMinecraft.id("ultimate"));
    /** Serum W's slashes: through armour. */
    public static final ResourceKey<DamageType> CLAW_WARP = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectMinecraft.id("claw_warp"));

    public static DamageSource source(Level level, ResourceKey<DamageType> key, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), direct, causing);
    }
}
