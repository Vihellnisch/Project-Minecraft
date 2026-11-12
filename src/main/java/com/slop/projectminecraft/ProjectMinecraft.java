package com.slop.projectminecraft;

import com.mojang.logging.LogUtils;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModEntities;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.registry.ModLoot;
import com.slop.projectminecraft.registry.ModSounds;
import com.slop.projectminecraft.registry.ModTabs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ProjectMinecraft.MODID)
public class ProjectMinecraft {
    public static final String MODID = "projectminecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProjectMinecraft(IEventBus modBus, ModContainer container) {
        ModSounds.SOUNDS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModLoot.MODIFIERS.register(modBus);
        ModTabs.TABS.register(modBus);
        com.slop.projectminecraft.arsenal.EyeSlot.ATTACHMENTS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(Network::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> {
            if (com.slop.projectminecraft.arsenal.Gloves.CURIOS) e.enqueueWork(() -> com.slop.projectminecraft.compat.CuriosCompat.setup());
        });

        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.apocalypse.ApocalypseServer.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.apocalypse.Destruction.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.apocalypse.Stasis.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.arsenal.ArsenalServer.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.boss.LibraryArena.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.boss.RolandMoves.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.item.BlackSilenceMask.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.boss.ClawMoves.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.warp.ClawWarp.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.arsenal.ClawServer.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.quest.HitlistTrail.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.arsenal.EyeSlot.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.arsenal.GladiusServer.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.command.ProjectMinecraftCommand.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
