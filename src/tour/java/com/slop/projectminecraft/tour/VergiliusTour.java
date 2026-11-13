package com.slop.projectminecraft.tour;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.Claw;
import com.slop.projectminecraft.boss.LimbusBus;
import com.slop.projectminecraft.boss.Vergilius;
import com.slop.projectminecraft.boss.VergiliusMoves;
import com.slop.projectminecraft.arsenal.EyeSlot;
import com.slop.projectminecraft.arsenal.GladiusServer;
import com.slop.projectminecraft.client.HitlistScreen;
import com.slop.projectminecraft.item.Hitlist;
import com.slop.projectminecraft.quest.SceneBuilder;
import com.slop.projectminecraft.registry.ModItems;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import com.slop.projectminecraft.registry.ModEntities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * ./gradlew runTourClient -DtourVergilius=true [-DtourOnly=looks]: Vergilius on a grey sheet (normal, E.G.O), the rebuilt
 * Claw, and in the world beside the Limbus bus (door shut and open). Logs "TOUR vergilius ...". Delete run-tour/saves/es_test
 * first.
 */
@EventBusSubscriber(modid = ProjectMinecraft.MODID, value = Dist.CLIENT)
public final class VergiliusTour {
    static final boolean ENABLED = Boolean.getBoolean("projectminecraft.tour") && Boolean.getBoolean("projectminecraft.tour.vergilius");
    private static final String WORLD = "es_test";

    private record Stop(String name, int length) {}

    private static final List<Stop> STOPS = new ArrayList<>();

    static {
        STOPS.add(new Stop("looks", 330));
        STOPS.add(new Stop("hitlist", 260));
        STOPS.add(new Stop("trail", 420));
        STOPS.add(new Stop("meet", 200));
        STOPS.add(new Stop("kill_p1", 420));
        STOPS.add(new Stop("ego", 560));
        STOPS.add(new Stop("retreat", 300));
        STOPS.add(new Stop("warn", 420));
        STOPS.add(new Stop("companion", 380));
        STOPS.add(new Stop("betray", 120));
        STOPS.add(new Stop("gladius", 360));
        STOPS.add(new Stop("eyes", 480));
        STOPS.add(new Stop("eye_look", 140));
        STOPS.add(new Stop("idle", 60));
        STOPS.add(new Stop("shaders", 200));
        STOPS.add(new Stop("rematch", 1040));
        STOPS.add(new Stop("rematch_w", 760));
        STOPS.add(new Stop("verg_kills_claw", 360));
        STOPS.add(new Stop("rush", 420));
        String only = System.getProperty("projectminecraft.tour.only", "");
        if (!only.isEmpty()) STOPS.removeIf(st -> Arrays.stream(only.split(",")).noneMatch(st.name::equals));
    }

    private static boolean opened;
    private static int ticks = -1, quitting = -1;
    private static Vergilius verg;
    private static LimbusBus bus;
    private static Claw claw;
    private static ItemStack list = ItemStack.EMPTY;
    private static UUID mob;
    private static Vec3 mark = Vec3.ZERO;

    static void log(String s) {
        ProjectMinecraft.LOGGER.info("TOUR vergilius " + s);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED) return;
        if (event.getScreen() instanceof DeathScreen) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.respawn();
            mc.setScreen(null);
            return;
        }
        if (opened || !(event.getScreen() instanceof TitleScreen)) return;
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0D);
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        Config.IMPACT_FRAMES.set(false);
        mc.options.pauseOnLostFocus = false;
        if (!mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261107L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), event.getScreen());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (ENABLED && quitting >= 0) {
            finish(mc);
            return;
        }
        if (!ENABLED || mc.player == null || mc.level == null || server == null) return;
        ticks++;
        if (ticks == 1) onServer(server, p -> {
            ServerLevel level = p.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.setDayTime(6000);
        });
        int since = ticks - 40;
        if (since < 0) return;
        int at = 0;
        for (Stop s : STOPS) {
            if (since < at + s.length) {
                run(mc, server, s, since - at, STOPS.indexOf(s));
                return;
            }
            at += s.length;
        }
        log("done");
        finish(mc);
    }

    private static void run(Minecraft mc, MinecraftServer server, Stop s, int w, int index) {
        String shot = "tour_verg_" + String.format("%02d", index) + "_" + s.name;
        switch (s.name) {
            case "looks" -> {
                if (w == 2) mc.setScreen(new MobSheet(l -> ModEntities.VERGILIUS.get().create(l)));
                if (w == 20) grab(mc, shot + "_sheet_normal");
                if (w == 22) mc.setScreen(new MobSheet(l -> {
                    Vergilius v = ModEntities.VERGILIUS.get().create(l);
                    v.setEgo(true);
                    return v;
                }));
                if (w == 40) grab(mc, shot + "_sheet_ego");
                if (w == 42) mc.setScreen(new MobSheet(l -> {
                    Claw c = ModEntities.CLAW.get().create(l);
                    c.setRebuilt(true);
                    return c;
                }));
                if (w == 60) grab(mc, shot + "_sheet_rebuilt_claw");
                if (w == 62) mc.setScreen(new MobSheet(l -> {
                    Vergilius v = ModEntities.VERGILIUS.get().create(l);
                    v.setEgo(true);
                    v.setBodyPose(Vergilius.POSE_THRUST);
                    return v;
                }));
                if (w == 78) grab(mc, shot + "_sheet_ego_thrust");
                if (w == 80) {
                    mc.setScreen(null);
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    onServer(server, p -> {
                        ServerLevel level = p.serverLevel();
                        Vec3 c = new Vec3(0.5, p.getY(), 0.5);
                        verg = ModEntities.VERGILIUS.get().create(level);
                        verg.moveTo(c.x + 5, c.y, c.z, 0, 0);
                        verg.setNoAi(true);
                        verg.setEgo(true);
                        level.addFreshEntity(verg);
                        bus = ModEntities.LIMBUS_BUS.get().create(level);
                        bus.moveTo(c.x, c.y, c.z, 0, 0);
                        level.addFreshEntity(bus);
                        p.setGameMode(GameType.SPECTATOR);
                        look(p, c.add(16, 5, 12), c.add(0, 1.5, 0));
                    });
                }
                if (w == 110) grab(mc, shot + "_bus_front34");
                if (w == 112) onServer(server, p -> look(p, bus.position().add(18, 3, 0), bus.position().add(0, 1.6, 0)));
                if (w == 135) grab(mc, shot + "_bus_side_left");
                if (w == 137) onServer(server, p -> look(p, bus.position().add(-18, 3, 0), bus.position().add(0, 1.6, 0)));
                if (w == 160) grab(mc, shot + "_bus_side_right");
                if (w == 162) onServer(server, p -> look(p, bus.position().add(0, 2.6, 13), bus.position().add(0, 2.0, 0)));
                if (w == 185) grab(mc, shot + "_bus_front");
                if (w == 187) onServer(server, p -> {
                    bus.setDoor(1);
                    look(p, bus.position().add(-12, 4, -14), bus.position().add(0, 1.6, 0));
                });
                if (w == 210) grab(mc, shot + "_bus_back34_door_open");
                if (w == 212) onServer(server, p -> {
                    verg.moveTo(bus.getX() + 3.5, bus.getY(), bus.getZ() + 0.6, 90, 0);
                    verg.setYHeadRot(90);
                    verg.yBodyRot = 90;
                    look(p, verg.position().add(4.5, 1.8, -2.5), verg.position().add(0, 1.1, 0));
                });
                if (w == 240) grab(mc, shot + "_verg_ego_world_front");
                if (w == 242) onServer(server, p -> look(p, verg.position().add(-0.5, 1.9, 4), verg.position().add(0, 1.0, 0)));
                if (w == 265) grab(mc, shot + "_verg_ego_world_back");
                if (w == 267) onServer(server, p -> {
                    verg.setEgo(false);
                    look(p, verg.position().add(3.4, 1.7, -1.6), verg.position().add(0, 1.2, 0));
                });
                if (w == 290) grab(mc, shot + "_verg_normal_world");
                if (w == 320) onServer(server, p -> {
                    for (Entity e : new Entity[]{verg, bus}) if (e != null) e.discard();
                    log("looks: done");
                });
            }
            case "hitlist" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.teleportTo(p.serverLevel(), 0.5, p.getY(), 0.5, 0, 0);
                    p.getInventory().clearContent();
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BLACK_SILENCE_BOOK.get()));
                    p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
                    claw = nearest(p, Claw.class);
                    if (claw != null) claw.skipEntrance();
                    log("hitlist: claw " + (claw != null));
                });
                if (w == 30) onServer(server, p -> {
                    claw.setNoSellUsed(true);
                    claw.invulnerableTime = 0;
                    claw.hurt(p.damageSources().playerAttack(p), 100000);
                    log("hitlist: claw state " + claw.state() + " (2 = retreat)");
                });
                if (w == 30 + Claw.R_MASK + 12) grab(mc, shot + "_dropped");
                if (w == 30 + Claw.R_MASK + 20) onServer(server, p -> log("hitlist: on the ground " + p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12))
                        .stream().map(it -> it.getItem().getItem().toString()).toList()));
                if (w == 200) onServer(server, p -> {
                    for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(20))) it.discard();
                    Config.TRAIL_DISTANCE.set(120);
                    p.getInventory().clearContent();
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HITLIST.get()));
                    p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
                    list = p.getMainHandItem();
                    log("hitlist: opened -> stage " + Hitlist.stage(list) + " stops " + Hitlist.stop(list, 0) + " " + Hitlist.stop(list, 1) + " " + Hitlist.stop(list, 2));
                });
                if (w == 204) HitlistScreen.open(InteractionHand.MAIN_HAND);
                if (w == 218) grab(mc, shot + "_page");
                if (w == 220) mc.setScreen(null);
            }
            case "trail" -> {
                // to each stop in turn: built as you come near, found up close; the page after each
                for (int i = 0; i < 3; i++) {
                    int at = 10 + i * 120, idx = i;
                    if (w == at) onServer(server, p -> {
                        p.setGameMode(GameType.CREATIVE);
                        list = p.getMainHandItem();
                        var s0 = Hitlist.stop(list, idx);
                        int y = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, s0.getX() + 30, s0.getZ());
                        p.teleportTo(p.serverLevel(), s0.getX() + 30.5, y, s0.getZ() + 0.5, 90, 30);
                    });
                    if (w == at + 40) onServer(server, p -> {
                        var s0 = Hitlist.stop(list, idx);
                        var g = SceneBuilder.ground(p.serverLevel(), s0);
                        p.teleportTo(p.serverLevel(), s0.getX() + 7.5, g.getY(), s0.getZ() + 0.5, 90, 35);
                        log("trail: stop " + idx + " stage before " + Hitlist.stage(list));
                    });
                    if (w == at + 70) grab(mc, shot + "_" + idx);
                    if (w == at + 72) onServer(server, p -> log("trail: stop " + idx + " stage after " + Hitlist.stage(list) + " | scene blocks kept " + SceneBuilder.get(p.serverLevel()).has(Hitlist.id(list))));
                    if (w == at + 80 && idx < 2) HitlistScreen.open(InteractionHand.MAIN_HAND);
                    if (w == at + 92 && idx < 2) grab(mc, shot + "_" + idx + "_page");
                    if (w == at + 94) mc.setScreen(null);
                }
                if (w == 400) onServer(server, p -> {
                    verg = nearest(p, Vergilius.class);
                    log("trail: vergilius at the scene " + (verg != null) + " state " + (verg == null ? "-" : verg.state()) + " | stage " + Hitlist.stage(list));
                });
            }
            case "meet" -> {
                if (w == 2) onServer(server, p -> {
                    if (verg == null) return;
                    look(p, verg.position().add(Vec3.directionFromRotation(0, verg.getYRot()).scale(6)).add(0, 1.6, 0), verg.position().add(0, 1.5, 0));
                    p.setGameMode(GameType.SURVIVAL);
                });
                if (w == 40) grab(mc, shot + "_intro");
                if (w == 70) grab(mc, shot + "_card");
                if (w == 110) grab(mc, shot + "_choice");
                if (w == 112) log("meet: screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
                if (w == 114) onServer(server, p -> log("meet: stage " + Hitlist.stage(list) + " | log " + verg.log));
                if (w == 120) {
                    mc.setScreen(null);
                    onServer(server, p -> {
                        verg.choose(p, true);
                        log("meet: drew -> state " + verg.state() + " (1 = fight) hp " + verg.getHealth());
                    });
                }
            }
            case "kill_p1" -> {
                String[] moves = {"heated_puncture", "scorching_incision", "following_the_flow", "open_the_path", "red_gaze"};
                for (int i = 0; i < moves.length; i++) {
                    int at = 10 + i * 80, idx = i;
                    if (w == at) onServer(server, p -> {
                        p.setHealth(p.getMaxHealth());
                        p.removeAllEffects();
                        survival(p);
                        p.teleportTo(p.serverLevel(), verg.getX() + 5, verg.getY(), verg.getZ(), 90, 10);
                        log("kill_p1: forced " + VergiliusMoves.force(verg, moves[idx], p, 200));
                    });
                    if (idx == 2 && w == at + 6) onServer(server, p -> {
                        verg.invulnerableTime = 0;
                        verg.hurt(p.damageSources().playerAttack(p), 6);
                    });
                    if (w == at + (idx == 3 ? 22 : idx == 4 ? 16 : 9)) grab(mc, shot + "_" + moves[idx]);
                    if (w == at + 50) onServer(server, p -> log("kill_p1: " + moves[idx] + " player hp " + p.getHealth() + " fire " + p.isOnFire()
                            + " slow " + p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + " | log " + tail(verg.log)));
                }
            }
            case "ego" -> {
                String[] moves = {"manifest", "internal_bleeding", "blinding_bloodcleaver", "drown_in_blood", "blood_barrier", "funeral"};
                int[] at = {10, 100, 180, 260, 340, 400};
                for (int i = 0; i < moves.length; i++) {
                    int idx = i;
                    if (w == at[i]) onServer(server, p -> {
                        p.setHealth(p.getMaxHealth());
                        p.removeAllEffects();
                        survival(p);
                        if (idx > 0) p.teleportTo(p.serverLevel(), verg.getX() + 5, verg.getY(), verg.getZ() + 1, 90, 10);
                        log("ego: forced " + VergiliusMoves.force(verg, moves[idx], p, 250));
                    });
                }
                if (w == 2) onServer(server, VergiliusTour::ensureFight);
                if (w == 12) onServer(server, p -> look(p, verg.position().add(4, 1.62, 5), verg.position().add(0, 1, 0)));
                if (w == 10 + 32) grab(mc, shot + "_manifest");
                if (w == 10 + 70) {
                    grab(mc, shot + "_ego_look");
                    log("ego: " + com.slop.projectminecraft.client.boss.VergiliusClient.debug());
                }
                if (w == 82) onServer(server, p -> log("ego: ego " + verg.ego()));
                if (w == 100 + 20) grab(mc, shot + "_bleeding");
                if (w == 180 + 19) grab(mc, shot + "_cleaver");
                if (w == 260 + 20) grab(mc, shot + "_drown_pull");
                if (w == 260 + 47) grab(mc, shot + "_drown_spikes");
                if (w == 340 + 6) grab(mc, shot + "_barrier");
                if (w == 348) onServer(server, p -> {
                    for (int k = 0; k < 4; k++) {
                        verg.invulnerableTime = 0;
                        float before = verg.getHealth();
                        verg.hurt(p.damageSources().playerAttack(p), 10);
                        log("ego: barrier hit " + k + " hp " + before + " -> " + verg.getHealth());
                    }
                });
                if (w == 402) onServer(server, p -> look(p, verg.position().add(6, 1.62, 6), verg.position().add(0, 1, 0)));
                if (w == 400 + 50) grab(mc, shot + "_funeral_storm");
                if (w == 400 + 94) grab(mc, shot + "_funeral_cut");
                if (w == 540) onServer(server, p -> log("ego: log " + tail(verg.log)));
            }
            case "retreat" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.teleportTo(p.serverLevel(), verg.getX() + 6, verg.getY(), verg.getZ() + 2, 90, 5);
                    verg.setHealth(3);
                    verg.invulnerableTime = 0;
                    verg.hurt(p.damageSources().playerAttack(p), 50);
                    log("retreat: state " + verg.state() + " (2 = retreat) | stage " + Hitlist.stage(list));
                });
                int[] beats = {14, 60, 84, 96, 110, 124, 150, 190};
                String[] names = {"kneel", "bus_coming", "bus_stopped", "door", "walk", "in", "drive_off", "fade"};
                for (int i = 0; i < beats.length; i++) if (w == beats[i]) grab(mc, shot + "_" + String.format("%02d", i) + "_" + names[i]);
                if (w == 40) onServer(server, p -> log("retreat: drops " + p.serverLevel().getEntitiesOfClass(ItemEntity.class, verg.getBoundingBox().inflate(6))
                        .stream().map(it -> it.getItem().getItem().toString()).toList() + " | hitlist in inventory " + !Hitlist.find(p, Hitlist.id(list)).isEmpty()));
                if (w == 240) onServer(server, p -> {
                    log("retreat: vergilius " + (verg.isRemoved() ? "gone" : "still here " + verg.state()) + " | bus " + (nearest(p, LimbusBus.class) == null ? "gone" : "still here")
                            + " | scene kept " + SceneBuilder.get(p.serverLevel()).has(Hitlist.id(list)));
                    for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(30))) {
                        if (!it.getItem().isEmpty()) p.getInventory().add(it.getItem().copy());
                        it.discard();
                    }
                    log("retreat: picked up gladius " + p.getInventory().contains(new ItemStack(ModItems.WHITE_HOT_GLADIUS.get()))
                            + " eyes " + p.getInventory().contains(new ItemStack(ModItems.VERGILIUS_EYES.get())));
                });
            }
            case "warn" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.teleportTo(p.serverLevel(), 40.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, 40, 40), 40.5, 0, 0);
                    UUID id = UUID.randomUUID();
                    ItemStack stack = new ItemStack(ModItems.HITLIST.get());
                    Hitlist.edit(stack, t -> {
                        t.putUUID("Id", id);
                        t.putInt("Stage", Hitlist.MET);
                    });
                    p.getInventory().add(stack);
                    list = Hitlist.find(p, id);
                    verg = Vergilius.atScene(p.serverLevel(), p.position().add(0, 0, 5), 180, id, p.getUUID());
                });
                if (w == 50) onServer(server, p -> {
                    p.setGameMode(GameType.SPECTATOR);
                    verg.choose(p, false);
                    log("warn: before intro, state " + verg.state() + " (choose needs the intro)");
                    verg.introducedForTour();
                    verg.choose(p, false);
                    claw = nearest(p, Claw.class);
                    log("warn: state " + verg.state() + " (3 = ally) | rebuilt claw " + (claw != null && claw.rebuilt()) + " hp " + (claw == null ? 0 : claw.getMaxHealth()));
                    look(p, claw.position().add(Vec3.directionFromRotation(0, claw.getYRot()).scale(5)).add(1.5, 1.8, 0), claw.position().add(0, 1.3, 0));
                });
                int[] beats = {Claw.E_OPEN + 4, Claw.E_GRIP + 4, Claw.E_STEP + 2, Claw.E_HEAD + 12, Claw.E_BANNER + 6};
                String[] names = {"open", "grip_torn", "slam", "eyes", "card"};
                for (int i = 0; i < beats.length; i++) if (w == 50 + beats[i]) grab(mc, shot + "_" + String.format("%02d", i) + "_" + names[i]);
                if (w == 50 + Claw.ARRIVE_TICKS + 2) onServer(server, p -> {
                    survival(p);
                    p.teleportTo(p.serverLevel(), verg.getX() - 4, verg.getY(), verg.getZ(), 0, 10);
                });
                if (w == 50 + Claw.ARRIVE_TICKS + 120) {
                    grab(mc, shot + "_side_by_side");
                    onServer(server, p -> log("warn: claw moves " + tail(claw.log) + " | verg hp " + verg.getHealth() + "/" + verg.getMaxHealth() + " downed " + verg.isDowned() + " | verg log " + tail(verg.log)));
                }
                if (w == 50 + Claw.ARRIVE_TICKS + 130) onServer(server, p -> {
                    p.setHealth(p.getMaxHealth());
                    claw.setNoSellUsed(true);
                    claw.invulnerableTime = 0;
                    claw.hurt(p.damageSources().playerAttack(p), 100000);
                    claw.setRebuilt(true);
                    log("warn: claw beaten -> verg state " + verg.state() + " (4 = companion) owner " + p.getUUID().equals(verg.owner())
                            + " | companion tag " + Vergilius.companionOf(p) + " | hitlist " + (Hitlist.find(p, Hitlist.id(list)).isEmpty() ? "gone" : "kept"));
                });
            }
            case "companion" -> {
                if (w == 1) onServer(server, p -> log("warn: rebuilt claw dropped " + p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(30))
                        .stream().map(it -> it.getItem().getItem().toString()).toList()));
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(30))) it.discard();
                    p.teleportTo(p.serverLevel(), verg.getX() + 40, verg.getY(), verg.getZ(), 90, 0);
                });
                if (w == 60) onServer(server, p -> log("companion: after walking off, distance " + String.format("%.1f", verg.distanceTo(p)) + " state " + verg.state()));
                if (w == 70) onServer(server, p -> {
                    var z = EntityType.ZOMBIE.create(p.serverLevel());
                    z.moveTo(p.getX() + 4, p.getY(), p.getZ() + 2, 0, 0);
                    z.setNoAi(true);
                    p.serverLevel().addFreshEntity(z);
                    mob = z.getUUID();
                    z.hurt(p.damageSources().playerAttack(p), 1);
                    p.setLastHurtMob(z);
                });
                if (w == 100) grab(mc, shot + "_fights");
                if (w == 170) onServer(server, p -> {
                    Entity z = p.serverLevel().getEntity(mob);
                    log("companion: zombie " + (z instanceof LivingEntity l && l.isAlive() ? "hp " + l.getHealth() : "dead") + " | log " + tail(verg.log));
                    if (z != null) z.discard();
                });
                if (w == 180) onServer(server, p -> {
                    var z = EntityType.ZOMBIE.create(p.serverLevel());
                    z.moveTo(verg.getX() + 1, verg.getY(), verg.getZ(), 0, 0);
                    p.serverLevel().addFreshEntity(z);
                    verg.setHealth(2);
                    verg.invulnerableTime = 0;
                    verg.hurt(z.damageSources().mobAttack(z), 10);
                    z.discard();
                    log("companion: downed " + verg.isDowned() + " hp " + verg.getHealth() + " alive " + verg.isAlive());
                });
                if (w == 190) grab(mc, shot + "_downed");
                if (w == 200) onServer(server, p -> {
                    verg.downedForTour(1);
                    p.setShiftKeyDown(true);
                    verg.interact(p, InteractionHand.MAIN_HAND);
                    p.setShiftKeyDown(false);
                    p.teleportTo(p.serverLevel(), verg.getX() + 12, verg.getY(), verg.getZ(), 90, 0);
                });
                if (w == 300) onServer(server, p -> log("companion: told to stay, after walking 12 away distance " + String.format("%.1f", verg.distanceTo(p))));
                if (w == 310) onServer(server, p -> {
                    p.setShiftKeyDown(true);
                    verg.interact(p, InteractionHand.MAIN_HAND);
                    p.setShiftKeyDown(false);
                });
                if (w == 370) onServer(server, p -> log("companion: told to follow, distance " + String.format("%.1f", verg.distanceTo(p))));
            }
            case "betray" -> {
                if (w == 10) onServer(server, p -> {
                    for (int k = 0; k < 3; k++) {
                        verg.invulnerableTime = 0;
                        verg.hurt(p.damageSources().playerAttack(p), 2);
                    }
                    log("betray: state " + verg.state() + " (1 = fight) | companion tag " + Vergilius.companionOf(p) + " | hp " + verg.getHealth() + "/" + verg.getMaxHealth());
                });
                if (w == 30) grab(mc, shot);
                if (w == 110) onServer(server, p -> {
                    verg.discard();
                    verg = null;
                });
            }
            case "gladius" -> {
                String[] skills = {"puncture", "incision", "flow", "path"};
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.getInventory().clearContent();
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_HOT_GLADIUS.get()));
                    p.teleportTo(p.serverLevel(), 80.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, 80, 80), 80.5, 0, 0);
                });
                if (w == 6) mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (w == 26) grab(mc, shot + "_first_person");
                if (w == 28) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (w == 48) grab(mc, shot + "_third_person");
                if (w == 50) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                for (int i = 0; i < skills.length; i++) {
                    int at = 60 + i * 70, idx = i;
                    if (w == at) onServer(server, p -> {
                        var z = EntityType.ZOMBIE.create(p.serverLevel());
                        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
                        z.moveTo(p.getX() + f.x * 4, p.getY(), p.getZ() + f.z * 4, 0, 0);
                        z.setNoAi(true);
                        p.serverLevel().addFreshEntity(z);
                        mob = z.getUUID();
                        GladiusServer.select(p, idx);
                        GladiusServer.use(p);
                        if (idx == 2) z.doHurtTarget(p);
                    });
                    if (w == at + (idx == 1 ? 10 : idx == 3 ? 12 : 4)) grab(mc, shot + "_" + skills[idx]);
                    if (w == at + 40) onServer(server, p -> {
                        Entity z = p.serverLevel().getEntity(mob);
                        log("gladius: " + skills[idx] + " zombie " + (z instanceof LivingEntity l && l.isAlive() ? "hp " + l.getHealth() + " fire " + l.isOnFire() : "dead"));
                        if (z != null) z.discard();
                    });
                }
                if (w == 345) mc.setScreen(new com.slop.projectminecraft.client.boss.GladiusWheel());
                if (w == 355) grab(mc, shot + "_wheel");
                if (w == 358) mc.setScreen(null);
            }
            case "eyes" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    ItemStack eyes = new ItemStack(ModItems.VERGILIUS_EYES.get());
                    log("eyes: inserted " + EyeSlot.insert(p, eyes) + " worn " + EyeSlot.worn(p) + " left " + eyes.getCount());
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_HOT_GLADIUS.get()));
                });
                if (w == 6) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (w == 30) grab(mc, shot + "_face");
                if (w == 32) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                if (w == 46) grab(mc, shot + "_slot");
                if (w == 48) onServer(server, p -> {
                    EyeSlot.click(p);
                    log("eyes: clicked slot -> worn " + EyeSlot.worn(p) + " carried " + p.containerMenu.getCarried());
                    EyeSlot.click(p);
                    log("eyes: clicked again -> worn " + EyeSlot.worn(p) + " carried " + p.containerMenu.getCarried());
                });
                if (w == 60) mc.setScreen(null);
                if (w == 70) onServer(server, p -> {
                    GladiusServer.select(p, GladiusServer.MANIFEST);
                    GladiusServer.use(p);
                    log("eyes: manifest -> ego " + GladiusServer.ego(p));
                });
                if (w == 100) grab(mc, shot + "_ego_front");
                if (w == 102) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (w == 120) grab(mc, shot + "_ego_back");
                if (w == 125) mc.setScreen(new com.slop.projectminecraft.client.boss.GladiusWheel());
                if (w == 135) grab(mc, shot + "_ego_wheel");
                if (w == 137) mc.setScreen(null);
                String[] skills = {"bleeding", "cleaver", "drown", "funeral"};
                for (int i = 0; i < 4; i++) {
                    int at = 150 + i * 80, idx = i;
                    if (w == at) onServer(server, p -> {
                        var z = EntityType.ZOMBIE.create(p.serverLevel());
                        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
                        z.moveTo(p.getX() + f.x * 4, p.getY(), p.getZ() + f.z * 4, 0, 0);
                        z.setNoAi(true);
                        p.serverLevel().addFreshEntity(z);
                        mob = z.getUUID();
                        p.setHealth(p.getMaxHealth());
                        GladiusServer.select(p, idx);
                        GladiusServer.use(p);
                    });
                    if (w == at + (idx == 3 ? 40 : 18)) grab(mc, shot + "_" + skills[idx]);
                    if (w == at + 70) onServer(server, p -> {
                        Entity z = p.serverLevel().getEntity(mob);
                        log("eyes: " + skills[idx] + " zombie " + (z instanceof LivingEntity l && l.isAlive() ? "hp " + l.getHealth() : "dead") + " | player hp " + p.getHealth()
                                + " | ego " + GladiusServer.ego(p));
                        if (z != null) z.discard();
                    });
                }
            }
            case "eye_look" -> {
                if (w == 2) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (w == 4) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                if (w == 18) grab(mc, shot + "_buttons");
                if (w == 20) mc.setScreen(new com.slop.projectminecraft.client.boss.EyeEditorScreen(null) {});
                if (w == 34) grab(mc, shot + "_editor");
                if (w == 36) {
                    mc.setScreen(null);
                    // one wide glowing stripe across the eye row instead of the two eyes
                    com.slop.projectminecraft.client.boss.GladiusClient.sendLook(true, 0xFFL << 32);
                }
                if (w == 46) onServer(server, p -> log("eye_look: server mask " + Long.toHexString(p.getData(EyeSlot.MASK)) + " glow " + p.getData(EyeSlot.GLOW)));
                if (w == 70) grab(mc, shot + "_stripe");
                if (w == 72) com.slop.projectminecraft.client.boss.GladiusClient.sendLook(false, 0xFFL << 32);
                if (w == 90) grab(mc, shot + "_off");
                if (w == 92) onServer(server, p -> log("eye_look: glow now " + p.getData(EyeSlot.GLOW)));
                if (w == 94) com.slop.projectminecraft.client.boss.GladiusClient.sendLook(true, EyeSlot.DEFAULT_MASK);
            }
            case "shaders" -> {
                // Roland in his first look and his third (with Angelica), and the player wearing his mask, side by side
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.getInventory().clearContent();
                    p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.BLACK_SILENCE_MASK.get()));
                    ServerLevel level = p.serverLevel();
                    Vec3 c = p.position();
                    com.slop.projectminecraft.boss.Roland r1 = ModEntities.ROLAND.get().create(level);
                    r1.moveTo(c.x - 2.5, c.y, c.z + 4, 180, 0);
                    r1.setNoAi(true);
                    level.addFreshEntity(r1);
                    com.slop.projectminecraft.boss.Roland r3 = ModEntities.ROLAND.get().create(level);
                    r3.moveTo(c.x + 2.5, c.y, c.z + 4, 180, 0);
                    level.addFreshEntity(r3);
                    r3.forcePhase(com.slop.projectminecraft.boss.Roland.BOND);
                    r3.setNoAi(true);
                    for (var k : level.getEntitiesOfClass(com.slop.projectminecraft.boss.RolandKin.class, r3.getBoundingBox().inflate(8))) {
                        k.setNoAi(true);
                        k.moveTo(c.x + 5, c.y, c.z + 5, 180, 0);
                    }
                    look(p, c.add(0, 1.62, 0), c.add(0, 1.4, 4));
                });
                if (w >= 4 && w < 190 && w % 5 == 0) onServer(server, p -> {
                    // no AI: keep them turned to the camera
                    for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(12))) {
                        m.setYRot(180);
                        m.setYBodyRot(180);
                        m.setYHeadRot(180);
                    }
                });
                if (w == 60) grab(mc, shot + "_bosses");
                if (w == 62) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (w == 90) grab(mc, shot + "_player_mask");
                if (w == 92) onServer(server, p -> look(p, p.position().add(0, 1.62, 0).add(Vec3.directionFromRotation(0, 180).scale(-1)), p.position().add(0, 1.4, 4)));
                if (w == 95) mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (w == 96) onServer(server, p -> {
                    // close on phase 3 Roland's face (he's at +2.5, +4)
                    Vec3 c = p.position();
                    mark = c;
                    look(p, c.add(2.5, 1.62, 0.6), c.add(2.5, 1.45, 4));
                });
                if (w == 120) grab(mc, shot + "_close_roland");
                if (w == 122) onServer(server, p -> look(p, mark.add(5, 1.62, 1.6), mark.add(5, 1.45, 5)));
                if (w == 146) grab(mc, shot + "_close_angelica");
                if (w == 190) onServer(server, p -> {
                    for (Entity e : p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(16), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
                    p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                });
            }
            case "rematch" -> {
                // 1.2.1: Tri-serum twice at most, its dashes dodgeable, the trip softer if you keep still, Vergilius scaled,
                // and the rematch lost when he falls
                int T1 = 80, T2 = 360, LOSS = 960;
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.removeAllEffects();
                    p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                    p.setHealth(100);
                    p.teleportTo(p.serverLevel(), 40.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, 40, 40), 40.5, 0, 0);
                    UUID id = UUID.randomUUID();
                    ItemStack stack = new ItemStack(ModItems.HITLIST.get());
                    Hitlist.edit(stack, t -> {
                        t.putUUID("Id", id);
                        t.putInt("Stage", Hitlist.MET);
                    });
                    p.getInventory().add(stack);
                    list = Hitlist.find(p, id);
                    verg = Vergilius.atScene(p.serverLevel(), p.position().add(0, 0, 5), 180, id, p.getUUID());
                });
                if (w == 20) onServer(server, p -> {
                    verg.introducedForTour();
                    verg.choose(p, false);
                    claw = nearest(p, Claw.class);
                    claw.skipEntrance();
                    log("rematch: claw max hp " + claw.getMaxHealth());
                });
                if (w == 40) onServer(server, p -> log("rematch: verg hp " + verg.getHealth() + "/" + verg.getMaxHealth() + " (scaled to the claw) | log " + tail(verg.log)));
                // a Tri on you, standing still: the dashes land; then the trip, kept still
                if (w == T1) onServer(server, p -> {
                    p.setHealth(100);
                    claw.setHealth(claw.getMaxHealth() * 0.55f);
                    log("rematch: forced tri " + com.slop.projectminecraft.boss.ClawMoves.force(claw, "tri_serum", 300) + " claw hp " + claw.getHealth());
                });
                if (w == T1 + 4) grab(mc, shot + "_tri_line");
                if (w == T1 + 102) onServer(server, p -> {
                    log("rematch: after standing in the dashes: player " + p.getHealth() + " claw " + claw.getHealth());
                    p.setHealth(100);
                });
                if (w == T1 + 260) onServer(server, p -> log("rematch: after the trip kept still: player " + p.getHealth() + " (100 before; 35% of max would be 65)"));
                // a Tri dodged: stepped off each line; then the trip, struggling
                if (w == T2) onServer(server, p -> {
                    p.setHealth(100);
                    claw.setHealth(claw.getMaxHealth() * 0.55f);
                    log("rematch: forced tri " + com.slop.projectminecraft.boss.ClawMoves.force(claw, "tri_serum", 300) + " claw hp " + claw.getHealth());
                });
                for (int k = 0; k < 5; k++) if (w == T2 + 35 + 13 * k) onServer(server, p -> {
                    Vec3 side = new Vec3(p.getX() - claw.getX(), 0, p.getZ() - claw.getZ()).normalize();
                    Vec3 off = new Vec3(-side.z, 0, side.x).scale(3.2);
                    p.teleportTo(p.getX() + off.x, p.getY(), p.getZ() + off.z);
                });
                if (w == T2 + 102) onServer(server, p -> {
                    log("rematch: after dodging the dashes: player " + p.getHealth());
                    p.setHealth(100);
                });
                if (w == T2 + 150) onServer(server, com.slop.projectminecraft.warp.ClawWarp::struggle);
                if (w == T2 + 270) onServer(server, p -> log("rematch: after the trip struggling: player " + p.getHealth() + " | claw log " + tail(claw.log)));
                // low on health: no more than two Tri-serums from the brain (two forced already, so none)
                if (w == T2 + 290) onServer(server, p -> {
                    p.setHealth(100);
                    claw.setHealth(claw.getMaxHealth() * 0.08f);
                });
                if (w == LOSS - 40) onServer(server, p -> log("rematch: low hp a while: tri count in log " + claw.log.stream().filter(n -> n.contains("tri_serum")).count() + " | " + tail(claw.log)));
                // Vergilius falls: the rematch is lost
                if (w == LOSS) onServer(server, p -> {
                    verg.setHealth(verg.getMaxHealth() * 0.12f);
                    verg.invulnerableTime = 0;
                    verg.hurt(claw.damageSources().mobAttack(claw), verg.getMaxHealth() * 0.05f);
                    log("rematch: vergilius fell -> verg state " + verg.state() + " (5 = withdraw) | claw state " + claw.state() + " | hitlist stage " + Hitlist.stage(list));
                });
                if (w == LOSS + 20) grab(mc, shot + "_lost");
            }
            case "rematch_w" -> {
                // 1.2.1: Serum W's marks lock to the ground (out of the circle, it misses); Vergilius's harder blows, burns,
                // his E.G.O at the Claw's half and Funeral at a fifth
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.removeAllEffects();
                    p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                    p.setHealth(100);
                    p.teleportTo(p.serverLevel(), 80.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, 80, 80), 80.5, 0, 0);
                    UUID id = UUID.randomUUID();
                    ItemStack stack = new ItemStack(ModItems.HITLIST.get());
                    Hitlist.edit(stack, t -> {
                        t.putUUID("Id", id);
                        t.putInt("Stage", Hitlist.MET);
                    });
                    p.getInventory().add(stack);
                    list = Hitlist.find(p, id);
                    verg = Vergilius.atScene(p.serverLevel(), p.position().add(0, 0, 5), 180, id, p.getUUID());
                });
                if (w == 20) onServer(server, p -> {
                    verg.introducedForTour();
                    verg.choose(p, false);
                    claw = nearest(p, Claw.class);
                    claw.skipEntrance();
                });
                // W, standing in the circle
                if (w == 60) onServer(server, p -> {
                    p.setHealth(100);
                    log("rematch_w: forced w " + com.slop.projectminecraft.boss.ClawMoves.force(claw, "serum_w", 200));
                });
                if (w == 60 + 50) grab(mc, shot + "_locked");
                if (w == 60 + 90) onServer(server, p -> log("rematch_w: stood in the circle: player " + p.getHealth()));
                // W, stepping out once it locks
                if (w == 170) onServer(server, p -> {
                    p.setHealth(100);
                    com.slop.projectminecraft.boss.ClawMoves.force(claw, "serum_w", 200);
                });
                if (w == 170 + 50) onServer(server, p -> p.teleportTo(p.getX() + 3, p.getY(), p.getZ()));
                if (w == 170 + 90) onServer(server, p -> log("rematch_w: stepped out: player " + p.getHealth()));
                // Vergilius on the Claw for a while
                if (w == 270) onServer(server, p -> {
                    p.setHealth(100);
                    claw.setHealth(claw.getMaxHealth());
                    p.teleportTo(p.getX() + 20, p.getY(), p.getZ());
                });
                if (w == 470) onServer(server, p -> log("rematch_w: 10 s of Vergilius: claw " + claw.getHealth() + "/" + claw.getMaxHealth() + " | verg log " + tail(verg.log)));
                // under half: his E.G.O
                if (w == 480) onServer(server, p -> claw.setHealth(claw.getMaxHealth() * 0.45f));
                if (w == 560) {
                    grab(mc, shot + "_ego");
                    onServer(server, p -> log("rematch_w: claw under half: verg ego " + verg.ego() + " | " + tail(verg.log)));
                }
                // a fifth: Funeral
                if (w == 580) onServer(server, p -> claw.setHealth(claw.getMaxHealth() * 0.18f));
                if (w == 720) onServer(server, p -> log("rematch_w: claw at a fifth: " + tail(verg.log) + " | claw " + claw.getHealth() + " state " + claw.state()));
            }
            case "verg_kills_claw" -> {
                // the crash from testing: Vergilius's own blow beats the rebuilt Claw (the move ends with him a companion)
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.teleportTo(p.serverLevel(), 120.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, 120, 120), 120.5, 0, 0);
                    UUID id = UUID.randomUUID();
                    ItemStack stack = new ItemStack(ModItems.HITLIST.get());
                    Hitlist.edit(stack, t -> {
                        t.putUUID("Id", id);
                        t.putInt("Stage", Hitlist.MET);
                    });
                    p.getInventory().add(stack);
                    list = Hitlist.find(p, id);
                    verg = Vergilius.atScene(p.serverLevel(), p.position().add(0, 0, 5), 180, id, p.getUUID());
                });
                if (w == 20) onServer(server, p -> {
                    verg.introducedForTour();
                    verg.choose(p, false);
                    claw = nearest(p, Claw.class);
                    claw.skipEntrance();
                    claw.setNoSellUsed(true);
                    p.teleportTo(p.getX() + 25, p.getY(), p.getZ());
                });
                if (w == 30) onServer(server, p -> claw.setHealth(claw.getMaxHealth() * 0.6f));
                if (w >= 40 && w < 318 && w % 5 == 0) onServer(server, p -> {
                    if (claw.state() == Claw.FIGHT) claw.setHealth(Math.min(claw.getHealth(), 2));
                });
                if (w == 322) onServer(server, p -> {
                    Vec3 f = verg.position().add(Vec3.directionFromRotation(0, verg.getYRot()).scale(3));
                    claw.teleportTo(f.x, f.y, f.z);
                    claw.setHealth(2);
                    log("verg_kills_claw: forced puncture " + VergiliusMoves.force(verg, "heated_puncture", claw, 100) + " claw hp " + claw.getHealth());
                });
                if (w == 350) onServer(server, p -> log("verg_kills_claw: claw state " + claw.state() + " (2 = retreat) | verg state " + verg.state() + " (4 = companion) | " + tail(verg.log)));
            }
            case "rush" -> {
                // 1.2.1: the rush (Serum W through every floor): kneel, a line, a flash, the lunge; on it you're dragged, off it he staggers
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.removeAllEffects();
                    p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                    p.setHealth(100);
                    p.teleportTo(p.serverLevel(), 160.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, 160, 160), 160.5, 0, 0);
                    p.getInventory().add(new ItemStack(ModItems.BLACK_SILENCE_BOOK.get()));
                    claw = ModEntities.CLAW.get().create(p.serverLevel());
                    claw.moveTo(p.getX(), p.getY(), p.getZ() + 6, 180, 0);
                    claw.arrive(p);
                    p.serverLevel().addFreshEntity(claw);
                    claw.skipEntrance();
                    claw.setNoSellUsed(true);
                });
                if (w == 30) onServer(server, p -> log("rush: forced, standing on the line " + com.slop.projectminecraft.boss.ClawMoves.force(claw, "serum_w_floors", 300)));
                if (w == 30 + 14) grab(mc, shot + "_line");
                if (w == 30 + 32) grab(mc, shot + "_flash");
                if (w == 30 + 40) onServer(server, p -> log("rush: stood still -> in the trip " + com.slop.projectminecraft.warp.ClawWarp.busy(p)));
                if (w == 260) onServer(server, p -> {
                    p.setHealth(100);
                    log("rush: forced again, stepping off " + com.slop.projectminecraft.boss.ClawMoves.force(claw, "serum_w_floors", 300));
                });
                if (w == 260 + 20) onServer(server, p -> {
                    Vec3 side = new Vec3(p.getX() - claw.getX(), 0, p.getZ() - claw.getZ()).normalize();
                    p.teleportTo(p.getX() - side.z * 3, p.getY(), p.getZ() + side.x * 3);
                });
                if (w == 260 + 44) {
                    grab(mc, shot + "_missed");
                    onServer(server, p -> log("rush: stepped off -> in the trip " + com.slop.projectminecraft.warp.ClawWarp.busy(p) + " | claw groggy " + claw.groggyTicks() + " hp " + p.getHealth() + " | " + tail(claw.log)));
                }
            }
            case "idle" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    verg = Vergilius.atScene(p.serverLevel(), p.position().add(0, 0, 3.5), 180, UUID.randomUUID(), p.getUUID());
                    verg.setNoAi(true);
                    look(p, p.position().add(0, 1.62, 0), verg.position().add(0, 1.2, 0));
                });
                if (w == 4) mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (w == 40) grab(mc, shot + "_free_hand");
                if (w == 55) onServer(server, p -> verg.discard());
            }
            default -> {}
        }
    }

    /** A Vergilius fighting the player (made fresh if there isn't one), for stops run on their own. */
    static void ensureFight(ServerPlayer p) {
        survival(p);
        if (verg != null && verg.isAlive() && verg.state() == Vergilius.FIGHT) return;
        verg = Vergilius.atScene(p.serverLevel(), p.position().add(5, 0, 0), 90, UUID.randomUUID(), p.getUUID());
        verg.introducedForTour();
        verg.choose(p, true);
        log("new vergilius, state " + verg.state());
    }

    static String tail(List<String> l) {
        return l.subList(Math.max(0, l.size() - 6), l.size()).toString();
    }

    static <T extends Entity> T nearest(ServerPlayer p, Class<T> type) {
        return p.serverLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(96)).stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    static void survival(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100000, 2, false, false));
    }

    private static void look(ServerPlayer p, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        p.teleportTo(p.serverLevel(), from.x, from.y - p.getEyeHeight(), from.z, yaw, pitch);
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
    }

    private static void finish(Minecraft mc) {
        if (quitting < 0) quitting = 0;
        quitting++;
        if (quitting == 40 && mc.level != null) {
            mc.level.disconnect();
            mc.disconnect();
        }
        if (quitting > 40 && mc.level == null && mc.getSingleplayerServer() == null) mc.stop();
    }

    private static void onServer(MinecraftServer server, Consumer<ServerPlayer> action) {
        server.execute(() -> {
            if (!server.getPlayerList().getPlayers().isEmpty()) action.accept(server.getPlayerList().getPlayers().get(0));
        });
    }

    private VergiliusTour() {}
}
