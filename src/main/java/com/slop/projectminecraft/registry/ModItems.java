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

    // the Claw
    public static final DeferredItem<Item> BLACK_SILENCE_BOOK = ITEMS.register("black_silence_book", com.slop.projectminecraft.item.BlackSilenceBook::new);
    public static final DeferredItem<Item> CLAW_MASK = ITEMS.register("claw_mask", com.slop.projectminecraft.item.ClawItems.Mask::new);
    public static final DeferredItem<Item> BROKEN_CLAW = part("broken_claw", net.minecraft.world.item.Rarity.RARE);
    public static final DeferredItem<Item> BROKEN_SERUM_K = part("broken_serum_k", net.minecraft.world.item.Rarity.RARE);
    public static final DeferredItem<Item> BROKEN_SERUM_R = part("broken_serum_r", net.minecraft.world.item.Rarity.RARE);
    public static final DeferredItem<Item> BROKEN_SERUM_W = part("broken_serum_w", net.minecraft.world.item.Rarity.RARE);
    public static final DeferredItem<Item> CLAW_ARM = part("claw_arm", net.minecraft.world.item.Rarity.EPIC);
    public static final DeferredItem<Item> SERUM_K = part("serum_k", net.minecraft.world.item.Rarity.EPIC);
    public static final DeferredItem<Item> SERUM_R = part("serum_r", net.minecraft.world.item.Rarity.EPIC);
    public static final DeferredItem<Item> SERUM_W = part("serum_w", net.minecraft.world.item.Rarity.EPIC);
    public static final DeferredItem<Item> HANDMADE_CLAW = ITEMS.register("handmade_claw", com.slop.projectminecraft.item.ClawItems.HandmadeClaw::new);
    public static final DeferredItem<Item> CLAW_SPAWN_EGG = ITEMS.register("claw_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.CLAW, 0x1A1A1C, 0x3A6CFF, new Item.Properties()));

    // Vergilius
    public static final DeferredItem<Item> HITLIST = ITEMS.register("hitlist", com.slop.projectminecraft.item.Hitlist::new);
    public static final DeferredItem<Item> WHITE_HOT_GLADIUS = ITEMS.register("white_hot_gladius", com.slop.projectminecraft.item.WhiteHotGladius::new);
    public static final DeferredItem<Item> VERGILIUS_EYES = ITEMS.register("vergilius_eyes", com.slop.projectminecraft.item.VergiliusEyes::new);
    public static final DeferredItem<Item> VERGILIUS_SPAWN_EGG = ITEMS.register("vergilius_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.VERGILIUS, 0x2A2A2E, 0xC81C22, new Item.Properties()));

    private static DeferredItem<Item> part(String id, net.minecraft.world.item.Rarity rarity) {
        return ITEMS.register(id, () -> new com.slop.projectminecraft.item.ClawItems.Part(id, rarity));
    }

    static {
        // these lived in Random Slop (effectslop) before Project Minecraft: keep them in old worlds
        for (String id : new String[]{"solemn_lament", "magic_bullet", "fourth_match_flame", "black_silence", "library_invitation", "black_silence_mask", "roland_spawn_egg"}) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath("effectslop", id), ProjectMinecraft.id(id));
        }
    }
}
