package com.slop.projectminecraft.client;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.client.arsenal.ArsenalHud;
import com.slop.projectminecraft.client.arsenal.GloveClient;
import com.slop.projectminecraft.client.arsenal.GloveLayer;
import com.slop.projectminecraft.client.boss.BossClient;
import com.slop.projectminecraft.client.boss.ByproductModel;
import com.slop.projectminecraft.client.boss.RolandBeastModel;
import com.slop.projectminecraft.client.boss.RolandClient;
import com.slop.projectminecraft.client.boss.RolandKinRenderer;
import com.slop.projectminecraft.client.boss.RolandRenderer;
import com.slop.projectminecraft.registry.ModEntities;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ProjectMinecraft.MODID, dist = Dist.CLIENT)
public class ProjectMinecraftClient {
    public ProjectMinecraftClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ProjectMinecraftClient::onReloadListeners);
        modBus.addListener(ProjectMinecraftClient::onKeys);
        modBus.addListener(ProjectMinecraftClient::onGuiLayers);
        modBus.addListener(ProjectMinecraftClient::onLayerDefinitions);
        modBus.addListener(ProjectMinecraftClient::onAddLayers);
        modBus.addListener(ProjectMinecraftClient::onAdditionalModels);
        modBus.addListener(ProjectMinecraftClient::onRenderers);
        NeoForge.EVENT_BUS.register(ClientFx.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.client.arsenal.ArsenalClient.class);
        NeoForge.EVENT_BUS.register(BossClient.class);
        NeoForge.EVENT_BUS.register(RolandClient.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.client.arsenal.GloveHands.class);
        NeoForge.EVENT_BUS.register(com.slop.projectminecraft.client.arsenal.FuriosoClient.class);
    }

    /** The Black Silence's gloves (and Furioso's weapons) on every player model. */
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new GloveLayer(pr, event.getEntityModels().bakeLayer(GloveLayer.LAYER)));
            }
        }
    }

    /** Furioso's weapons have no item: load their models on their own. */
    private static void onAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (int w = 0; w < GloveLayer.MODELS; w++) {
            event.register(GloveLayer.location(w, false));
            event.register(GloveLayer.location(w, true));
        }
    }

    private static void onLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GloveLayer.LAYER, GloveLayer::create);
        event.registerLayerDefinition(RolandBeastModel.LAYER, RolandBeastModel::create);
        event.registerLayerDefinition(ByproductModel.LAYER, ByproductModel::create);
        event.registerLayerDefinition(RolandKinRenderer.HAIR_LAYER, RolandKinRenderer::createHair);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROLAND.get(), RolandRenderer::new);
        event.registerEntityRenderer(ModEntities.ROLAND_KIN.get(), RolandKinRenderer::new);
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        // shaders are rebuilt lazily after F3+T / resource pack changes
        event.registerReloadListener((ResourceManagerReloadListener) rm -> PostFx.invalidate());
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(GloveClient.WHEEL_KEY);
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ProjectMinecraft.id("hud"), ProjectMinecraftClient::renderHud);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ProjectMinecraft.id("arsenal_hud"), (g, t) -> ArsenalHud.render(g, t.getGameTimeDeltaPartialTick(true)));
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ProjectMinecraft.id("roland_fade"), RolandClient::render);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ProjectMinecraft.id("boss_subtitles"), BossClient::renderSubtitle);
    }

    /** Cutscene letterbox, name cards and screen flashes. */
    private static void renderHud(GuiGraphics g, DeltaTracker t) {
        CinematicCamera.renderOverlay(g);
        BossClient.renderBanners(g, t);
        ScreenFlash.render(g, t.getGameTimeDeltaPartialTick(true));
    }
}
