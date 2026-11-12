package com.slop.projectminecraft.registry;

import com.slop.projectminecraft.ProjectMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    private ModTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectMinecraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.projectminecraft"))
            .icon(() -> new ItemStack(ModItems.BLACK_SILENCE.get()))
            .displayItems((params, out) -> {
                for (var item : java.util.List.of(ModItems.SOLEMN_LAMENT, ModItems.MAGIC_BULLET, ModItems.FOURTH_MATCH_FLAME, ModItems.BLACK_SILENCE,
                        ModItems.LIBRARY_INVITATION, ModItems.BLACK_SILENCE_MASK, ModItems.ROLAND_SPAWN_EGG,
                        ModItems.BLACK_SILENCE_BOOK, ModItems.CLAW_MASK, ModItems.BROKEN_CLAW, ModItems.BROKEN_SERUM_K, ModItems.BROKEN_SERUM_R, ModItems.BROKEN_SERUM_W,
                        ModItems.CLAW_ARM, ModItems.SERUM_K, ModItems.SERUM_R, ModItems.SERUM_W, ModItems.HANDMADE_CLAW, ModItems.CLAW_SPAWN_EGG,
                        ModItems.HITLIST, ModItems.WHITE_HOT_GLADIUS, ModItems.VERGILIUS_EYES, ModItems.VERGILIUS_SPAWN_EGG)) {
                    out.accept(item.get());
                }
            })
            .build());
}
