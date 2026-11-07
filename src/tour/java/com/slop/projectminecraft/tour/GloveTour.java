package com.slop.projectminecraft.tour;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.arsenal.GloveServer;
import com.slop.projectminecraft.client.arsenal.GloveLayer;
import com.slop.projectminecraft.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * ./gradlew runTourClient -DtourGloves=true: creates run-tour/saves/es_test, puts the Black Silence on (offhand, no
 * Curios in the tour), and for each of the twelve weapons screenshots it held in first person, held in third person
 * from the front, and mid-swing; then the crosshair cooldown with the HUD on. Screenshots go to run-tour/screenshots.
 * Delete the save before each run.
 */
@EventBusSubscriber(modid = ProjectMinecraft.MODID, value = Dist.CLIENT)
public final class GloveTour {
    static final boolean ENABLED = Boolean.getBoolean("projectminecraft.tour") && Boolean.getBoolean("projectminecraft.tour.gloves");
    private static final String WORLD = "es_test";
    private static final int STAGE_AT = 20, FIRST_STOP = 80, STOP_LENGTH = 50, SWING_AT = 30;
    private static final int Y = 151;

    /** name, weapon; names end in _fp (first person, hand only), _front (third person), _swing (third person, mid-attack), _hud. */
    private record Stop(String name, int weapon) {}

    private static final List<Stop> STOPS = new ArrayList<>();

    static {
        for (int w = 0; w < GloveLayer.WEAPON_MODELS.length; w++) {
            String n = GloveLayer.WEAPON_MODELS[w];
            STOPS.add(new Stop(n + "_fp", w));
            STOPS.add(new Stop(n + "_front", w));
            STOPS.add(new Stop(n + "_side", w));
            STOPS.add(new Stop(n + "_swing", w));
        }
        STOPS.add(new Stop("greatsword_cooldown_hud", 8));
        // vanilla items in the main hand, to compare the holds against
        String[] refs = {"vanilla_sword", "vanilla_mace", "vanilla_axe"};
        for (int i = 0; i < refs.length; i++) {
            STOPS.add(new Stop(refs[i] + "_fp", -1 - i));
            STOPS.add(new Stop(refs[i] + "_side", -1 - i));
        }
        // every model laid out like the reference sheet, flat and turned a little
        STOPS.add(new Stop("lineup_sheet", -100));
        STOPS.add(new Stop("lineup_angled_sheet", -100));
        // last: a real Furioso on a dummy, shot at the sheathed draws (runs until it's over, then quits)
        STOPS.add(new Stop("furioso", -200));
        // -DtourOnly=katana,pistol keeps just the stops whose names contain one of those
        String only = System.getProperty("projectminecraft.tour.only", "");
        if (!only.isEmpty()) STOPS.removeIf(st -> java.util.Arrays.stream(only.split(",")).noneMatch(st.name::contains));
    }

    /** Stop "weapon" for the model sheet screen. */
    private static final int SHEET = -100;
    private static boolean opened, bare;
    /** What the stop puts in the main hand (empty for the gloves' own weapons). */
    private static volatile ItemStack mainHand = ItemStack.EMPTY;
    private static int ticks = -1, shot = -1;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) return;
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0D);
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        if (!mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261025L, false, false), WorldPresets::createNormalWorldDimensions, event.getScreen());
    }

    /** "_side" stops: the camera stays in front of the face, the body turns a quarter so the arms are seen from the side. */
    private static boolean side;

    @SubscribeEvent
    public static void onRenderPlayer(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre event) {
        if (!ENABLED || !side) return;
        var p = event.getEntity();
        p.yBodyRot = p.yBodyRotO = p.getYRot() + 90;
        p.yHeadRot = p.yHeadRotO = p.getYRot();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (ENABLED && bare) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        // on the way out the world is gone, so this runs before the checks below
        if (ENABLED && quitting > 0) {
            finish(mc);
            return;
        }
        if (!ENABLED || mc.player == null || mc.level == null || server == null) return;
        ticks++;
        if (ticks == 1) onServer(server, p -> {
            placePlayer(p);
            // the tour tries every weapon and Furioso: the gloves have to answer fully
            com.slop.projectminecraft.arsenal.GloveServer.master(p);
        });
        if (ticks == STAGE_AT) onServer(server, GloveTour::buildStage);
        int since = ticks - FIRST_STOP;
        if (since < 0) {
            mc.options.hideGui = true;
            return;
        }
        if (furioso >= 0) {
            furiosoTick(mc);
            return;
        }
        int stop = since / STOP_LENGTH, within = since % STOP_LENGTH;
        if (stop >= STOPS.size()) {
            finish(mc);
            return;
        }
        Stop s = STOPS.get(stop);
        boolean fp = s.name.endsWith("_fp"), hud = s.name.endsWith("_hud"), swing = s.name.endsWith("_swing");
        // the hand needs hideGui off; "bare" then cancels the HUD itself
        mc.options.hideGui = !(fp || hud);
        bare = fp;
        side = s.name.endsWith("_side");
        if (s.weapon == -200) {
            furioso = 0;
            mc.options.hideGui = true;
            onServer(server, p -> {
                placePlayer(p);
                var dummy = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (dummy == null) return;
                dummy.moveTo(sx + 0.5, Y, sz + 4.5, 180, 0);
                dummy.setNoAi(true);
                p.serverLevel().addFreshEntity(dummy);
                com.slop.projectminecraft.apocalypse.ApocalypseServer.start(new com.slop.projectminecraft.arsenal.ArsenalEvents.Furioso(p.serverLevel(), p, dummy));
            });
            return;
        }
        if (s.weapon == SHEET) {
            if (within == 0) mc.setScreen(new ModelSheet(s.name.contains("angled") ? 35 : 0));
            if (shot != stop && within >= 30) {
                shot = stop;
                Screenshot.grab(mc.gameDirectory, "tour_" + String.format("%02d", stop) + "_" + s.name + ".png", mc.getMainRenderTarget(), message -> {});
                mc.setScreen(null);
            }
            return;
        }
        if (within == 0) {
            mc.options.setCameraType(fp || hud ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
            mc.options.fov().set(fp || hud ? 70 : 40);
            mainHand = s.weapon >= 0 ? ItemStack.EMPTY : new ItemStack(new net.minecraft.world.item.Item[]{
                    net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.MACE, net.minecraft.world.item.Items.IRON_AXE}[-1 - s.weapon]);
            onServer(server, p -> {
                placePlayer(p);
                if (s.weapon >= 0) GloveServer.select(p, s.weapon);
            });
        }
        if (within == 20) onServer(server, GloveTour::placePlayer);
        if ((swing && within == SWING_AT) || (hud && within == 30)) {
            KeyMapping.click(mc.options.keyAttack.getKey());
        }
        int shootAt = swing ? SWING_AT + 3 : hud ? 38 : 40;
        if (shot != stop && within >= shootAt) {
            shot = stop;
            Screenshot.grab(mc.gameDirectory, "tour_" + String.format("%02d", stop) + "_" + s.name + ".png", mc.getMainRenderTarget(), message -> {});
        }
    }

    /** Ticks into the Furioso stop, or -1; and which of its moments have been shot. */
    private static int furioso = -1, furiosoShots;
    private static final String[] MOMENTS = {"katana_drawn", "iai", "durandal_draw", "durandal_out", "durandal_cut1", "durandal_cut2"};

    private static void furiosoTick(Minecraft mc) {
        furioso++;
        var r = com.slop.projectminecraft.client.arsenal.FuriosoClient.mine();
        if (r == null) {
            if (furioso > 60) finish(mc);
            return;
        }
        double t = r.st(0);
        double[] at = {com.slop.projectminecraft.arsenal.FuriosoScript.KATANA_T + 3, com.slop.projectminecraft.arsenal.FuriosoScript.IAI - 1,
                com.slop.projectminecraft.arsenal.FuriosoScript.DURANDAL_T + 5, com.slop.projectminecraft.arsenal.FuriosoScript.DRAW_END - 1,
                com.slop.projectminecraft.arsenal.FuriosoScript.CUT1 + 0.5, com.slop.projectminecraft.arsenal.FuriosoScript.CUT2 + 0.5};
        if (furiosoShots < at.length && t >= at[furiosoShots]) {
            Screenshot.grab(mc.gameDirectory, "tour_furioso_" + furiosoShots + "_" + MOMENTS[furiosoShots] + ".png", mc.getMainRenderTarget(), message -> {});
            furiosoShots++;
        }
    }

    /** The stage sits over world spawn, whose chunks exist from the start (no fresh terrain generating at exit). */
    private static volatile int sx = Integer.MIN_VALUE, sz;
    private static int quitting = -1;

    /**
     * Quits like a player would: let the world settle, save and quit to the title screen, then close. Stopping the game
     * outright while chunks were still generating could leave 1.21.1's shutdown spinning forever on "Saving worlds".
     */
    private static void finish(Minecraft mc) {
        if (quitting < 0) quitting = 0;
        quitting++;
        if (quitting == 40 && mc.level != null) {
            mc.level.disconnect();
            mc.disconnect();
        }
        if (quitting > 40 && mc.level == null && mc.getSingleplayerServer() == null) mc.stop();
    }

    private static void placePlayer(ServerPlayer p) {
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BLACK_SILENCE.get()));
        p.setItemInHand(InteractionHand.MAIN_HAND, mainHand.copy());
        if (sx == Integer.MIN_VALUE) {
            sx = p.serverLevel().getSharedSpawnPos().getX();
            sz = p.serverLevel().getSharedSpawnPos().getZ();
        }
        p.teleportTo(p.serverLevel(), sx + 0.5, Y, sz + 0.5, 0.0F, 8.0F);
    }

    private static void buildStage(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(12000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 12; z++) {
                level.setBlockAndUpdate(new BlockPos(sx + x, Y - 1, sz + z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = Y; y < Y + 6; y++) level.setBlockAndUpdate(new BlockPos(sx + x, y, sz + z), Blocks.AIR.defaultBlockState());
            }
        }
        placePlayer(p);
    }

    private static void onServer(MinecraftServer server, Consumer<ServerPlayer> action) {
        server.execute(() -> {
            if (!server.getPlayerList().getPlayers().isEmpty()) action.accept(server.getPlayerList().getPlayers().get(0));
        });
    }

    private GloveTour() {}
}
