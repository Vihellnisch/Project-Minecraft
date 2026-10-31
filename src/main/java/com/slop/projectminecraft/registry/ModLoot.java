package com.slop.projectminecraft.registry;

import com.mojang.serialization.MapCodec;
import com.slop.projectminecraft.ProjectMinecraft;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLoot {
    private ModLoot() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ProjectMinecraft.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddItemsModifier>> ADD_ITEMS = MODIFIERS.register("add_items", () -> AddItemsModifier.CODEC);
}
