package com.slop.projectminecraft.registry;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.Roland;
import com.slop.projectminecraft.boss.RolandKin;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectMinecraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Roland>> ROLAND = ENTITIES.register("roland",
            () -> EntityType.Builder.of(Roland::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(12).fireImmune().build("roland"));

    public static final DeferredHolder<EntityType<?>, EntityType<RolandKin>> ROLAND_KIN = ENTITIES.register("roland_kin",
            () -> EntityType.Builder.of(RolandKin::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(12).build("roland_kin"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.slop.projectminecraft.boss.Claw>> CLAW = ENTITIES.register("claw",
            () -> EntityType.Builder.of(com.slop.projectminecraft.boss.Claw::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(12).fireImmune().build("claw"));

    static {
        for (String id : new String[]{"roland", "roland_kin"}) ENTITIES.addAlias(ResourceLocation.fromNamespaceAndPath("effectslop", id), ProjectMinecraft.id(id));
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ROLAND.get(), Roland.createAttributes().build());
        event.put(ROLAND_KIN.get(), RolandKin.createAttributes().build());
        event.put(CLAW.get(), com.slop.projectminecraft.boss.Claw.createAttributes().build());
    }
}
