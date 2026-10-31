package com.slop.projectminecraft.registry;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.arsenal.ArsenalEvents;
import com.slop.projectminecraft.item.ArsenalItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectMinecraft.MODID);

    public static final DeferredItem<Item> SOLEMN_LAMENT = ITEMS.register("solemn_lament", () -> new ArsenalItems.Ego(ArsenalEvents.SOLEMN, 20));
    public static final DeferredItem<Item> MAGIC_BULLET = ITEMS.register("magic_bullet", () -> new ArsenalItems.Ego(ArsenalEvents.MAGIC_BULLET, 16));
    public static final DeferredItem<Item> FOURTH_MATCH_FLAME = ITEMS.register("fourth_match_flame", () -> new ArsenalItems.Ego(ArsenalEvents.MATCH_FLAME, 20));
    public static final DeferredItem<Item> BLACK_SILENCE = ITEMS.register("black_silence", ArsenalItems.BlackSilence::new);
    public static final DeferredItem<Item> LIBRARY_INVITATION = ITEMS.register("library_invitation", com.slop.projectminecraft.item.LibraryInvitation::new);
    public static final DeferredItem<Item> BLACK_SILENCE_MASK = ITEMS.register("black_silence_mask", com.slop.projectminecraft.item.BlackSilenceMask::new);
    public static final DeferredItem<Item> ROLAND_SPAWN_EGG = ITEMS.register("roland_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.ROLAND, 0x111111, 0xD8D8D8, new Item.Properties()));

    static {
        // these lived in Random Slop (effectslop) before Project Minecraft: keep them in old worlds
        for (String id : new String[]{"solemn_lament", "magic_bullet", "fourth_match_flame", "black_silence", "library_invitation", "black_silence_mask", "roland_spawn_egg"}) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath("effectslop", id), ProjectMinecraft.id(id));
        }
    }
}
