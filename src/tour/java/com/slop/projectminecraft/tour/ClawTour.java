package com.slop.projectminecraft.tour;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.arsenal.ClawServer;
import com.slop.projectminecraft.boss.Claw;
import com.slop.projectminecraft.boss.ClawMoves;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.client.boss.ClawWarpClient;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.warp.ClawWarp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * ./gradlew runTourClient -DtourClaw=true [-DtourOnly=sheet,gear,summon,looks,trail,extirpation,serum_r,serum_w,nosell,serum_k,ult,
 * ult_safety,tri,retreat,lose,handmade]: the Black Silence's book brings the Claw, each of his moves forced in turn, the
 * trip through every floor (dimensions entered, vanilla terrain frames, back where they stood), death and a logout mid-trip,
 * winning (drops, the book torn) and losing (the book taken), and the Handmade Claw. Logs "TOUR claw ...". Delete
 * run-tour/saves/es_test first.
 */
@EventBusSubscriber(modid = ProjectMinecraft.MODID, value = Dist.CLIENT)
public final class ClawTour {
    static final boolean ENABLED = Boolean.getBoolean("projectminecraft.tour") && Boolean.getBoolean("projectminecraft.tour.claw");
    private static final String WORLD = "es_test";

    private record Stop(String name, int length) {}

    private static final List<Stop> STOPS = new ArrayList<>();

    static {
        STOPS.add(new Stop("sheet", 50));
        STOPS.add(new Stop("gear", 160));
        STOPS.add(new Stop("summon", 230));
        STOPS.add(new Stop("looks", 160));
        STOPS.add(new Stop("trail", 140));
        STOPS.add(new Stop("extirpation", 140));
        STOPS.add(new Stop("serum_r", 180));
        STOPS.add(new Stop("serum_w", 180));
        STOPS.add(new Stop("nosell", 100));
        STOPS.add(new Stop("serum_k", 260));
        STOPS.add(new Stop("ult", 340));
        STOPS.add(new Stop("ult_safety", 320));
        STOPS.add(new Stop("tri", 340));
        STOPS.add(new Stop("retreat", 250));
        STOPS.add(new Stop("lose", 240));
        STOPS.add(new Stop("handmade", 330));
        String only = System.getProperty("projectminecraft.tour.only", "");
        if (!only.isEmpty()) STOPS.removeIf(st -> Arrays.stream(only.split(",")).noneMatch(st.name::equals));
    }

    private static boolean opened;
    private static int ticks = -1, quitting = -1;
    private static Claw boss;
    /** Stop-local scratch: a saved position, a number, a mob. */
    private static Vec3 mark = Vec3.ZERO;
    private static float num;
    private static String lastDim = "";
    private static UUID mob;
    private static boolean watchDims;
    /** The client tick it last landed in another dimension, and how many landings this stop. */
    private static int landedAt = -1, landings, lastShot = -1;

    static void log(String s) {
        ProjectMinecraft.LOGGER.info("TOUR claw " + s);
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
        if (!mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261101L, false, false),
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
        // every dimension the player passes through, as the client sees it
        String dim = mc.level.dimension().location().toString();
        if (watchDims && !dim.equals(lastDim)) {
            log("  client now in " + dim + " | screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
            landedAt = ticks;
            landings++;
        }
        lastDim = dim;
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

    // =================================================================== stops

    private static void run(Minecraft mc, MinecraftServer server, Stop s, int w, int index) {
        String shot = "tour_claw_" + String.format("%02d", index) + "_" + s.name;
        boolean fights = !s.name.equals("sheet") && !s.name.equals("gear") && !s.name.equals("summon") && !s.name.equals("lose") && !s.name.equals("handmade");
        if (w == 0) {
            mc.options.setCameraType(s.name.startsWith("ult") || s.name.equals("looks") ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
            if (fights) onServer(server, ClawTour::ensureClaw);
        }
        // kept alive and facing him, except where the stop is about dying or being dragged off
        if (fights && !s.name.startsWith("ult") && !s.name.equals("tri") && !(s.name.equals("serum_r") && w > 85 && w < 105) && w % 10 == 5) onServer(server, p -> {
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.clearFire();
        });
        if (fights && w == 58) onServer(server, p -> {
            if (boss == null) return;
            p.lookAt(EntityAnchorArgument.Anchor.EYES, boss.position().add(0, 1.4, 0));
            boss.log.clear();
        });
        switch (s.name) {
            case "sheet" -> {
                // the model on grey, next to the user's reference
                if (w == 2) mc.setScreen(new ClawSheet());
                if (w == 30) grab(mc, shot);
                if (w == 45) mc.setScreen(null);
            }
            case "gear" -> {
                // the trophies on the player: the mask worn, the Handmade Claw on the arm, the wheel
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.CLAW_MASK.get()));
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HANDMADE_CLAW.get()));
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), 200, 10);
                });
                if (w == 4) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (w == 24) grab(mc, shot + "_front");
                if (w == 26) mc.setScreen(new ClawSheet(true));
                if (w == 40) grab(mc, shot + "_sheet");
                if (w == 42) mc.setScreen(null);
                if (w == 46) onServer(server, p -> {
                    ClawServer.select(p, ClawServer.TRI);
                    ClawServer.use(p);
                });
                if (w == 56) mc.setScreen(new ClawSheet(true));
                if (w == 66) grab(mc, shot + "_tri_lit");
                if (w == 67) mc.setScreen(null);
                if (w == 68) mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (w == 86) grab(mc, shot + "_first_person");
                if (w == 90) mc.player.swing(InteractionHand.MAIN_HAND);
                if (w == 93) grab(mc, shot + "_first_person_swing");
                if (w == 100) mc.setScreen(new com.slop.projectminecraft.client.boss.ClawWheel());
                if (w == 125) grab(mc, shot + "_wheel");
                if (w == 140) {
                    mc.setScreen(null);
                    onServer(server, p -> {
                        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                        p.removeAllEffects();
                    });
                }
            }
            case "summon" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    p.teleportTo(p.serverLevel(), 0.5, p.getY(), 0.5, 180, 0);
                });
                if (w == 4) onServer(server, p -> {
                    useBook(p);
                    log("summon: claws near " + claws(p).size() + " | state " + (boss == null ? "none" : boss.state()) + " | book sought " + ClawMoves.sought(p.getMainHandItem())
                            + " | in front " + (boss == null ? "-" : String.format("%.1f", boss.distanceTo(p))));
                    // a second use while he's coming: refused
                    useBook(p);
                    log("summon: used again -> claws " + claws(p).size());
                });
                // the entrance, beat by beat (w = entrance tick + 4)
                int[] beats = {Claw.E_CRACK + 3, Claw.E_OPEN + 3, Claw.E_CLAW + 14, Claw.E_GRIP + 5, Claw.E_LEG + 14, Claw.E_STEP + 2,
                        Claw.E_BODY + 14, Claw.E_OUT + 4, Claw.E_HEAD + 12, Claw.E_SNAP + 1, Claw.E_BANNER + 6};
                String[] names = {"crack", "open", "claw", "grip", "leg", "step", "lean", "out", "head", "snap", "banner"};
                for (int i = 0; i < beats.length; i++) if (w == beats[i] + 4) grab(mc, shot + "_" + String.format("%02d", i) + "_" + names[i]);
                if (w == 220) onServer(server, p -> log("summon: state " + boss.state() + " (1 = fight) | hp " + boss.getHealth() + "/" + boss.getMaxHealth() + " | moves " + boss.log));
            }
            case "looks" -> {
                if (w == 60) onServer(server, p -> {
                    boss.setNoAi(true);
                    boss.setYRot(0);
                    boss.yBodyRot = boss.yHeadRot = 0;
                    p.setGameMode(GameType.SPECTATOR);
                    look(p, boss.position().add(1.0, 1.7, 3.6), boss.position().add(0, 1.1, 0));
                });
                if (w == 80) grab(mc, shot + "_front");
                if (w == 82) onServer(server, p -> look(p, boss.position().add(-1.0, 1.8, -3.6), boss.position().add(0, 1.1, 0)));
                if (w == 100) grab(mc, shot + "_back");
                if (w == 102) onServer(server, p -> look(p, boss.position().add(3.6, 1.6, 0.4), boss.position().add(0, 1.1, 0)));
                if (w == 120) grab(mc, shot + "_side");
                if (w == 130) onServer(server, p -> {
                    boss.setNoAi(false);
                    survival(p);
                    p.teleportTo(p.serverLevel(), boss.getX(), boss.getY(), boss.getZ() - 5, 0, 0);
                });
            }
            case "trail", "extirpation" -> {
                if (w == 59) onServer(server, p -> num = p.getHealth());
                if (w == 60) onServer(server, p -> {
                    p.setHealth(p.getMaxHealth());
                    num = p.getHealth();
                    log(s.name + ": forced " + ClawMoves.force(boss, s.name, 200) + " | distance " + String.format("%.1f", boss.distanceTo(p)));
                });
                if (w == 61) onServer(server, p -> mark = Vec3.ZERO);
                if (w == 64) onServer(server, p -> mark = new Vec3(p.getHealth(), 0, 0));
                if (w == 66 || w == 74) grab(mc, shot + "_" + w);
                if (w == 70 || w == 80 || w == 90) onServer(server, p -> log(s.name + ": player hp " + p.getHealth() + "/" + p.getMaxHealth() + " at " + w + " | pose " + boss.bodyPose()));
                if (w == 130) onServer(server, p -> log(s.name + ": moves " + boss.log));
            }
            case "serum_r" -> {
                if (w == 40) onServer(server, p -> {
                    // a wall behind the player, on the line he charges along
                    Vec3 dir = p.position().subtract(boss.position()).multiply(1, 0, 1).normalize();
                    BlockPos c = BlockPos.containing(p.position().add(dir.scale(5)));
                    for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy < 3; dy++) p.serverLevel().setBlockAndUpdate(c.offset((int) Math.round(-dir.z * dx), dy, (int) Math.round(dir.x * dx)), Blocks.STONE.defaultBlockState());
                    mark = Vec3.atCenterOf(c);
                    log("serum_r: wall of stone at " + c + " -> " + stone(p.serverLevel(), c) + " blocks");
                });
                if (w == 60) onServer(server, p -> log("serum_r: forced " + ClawMoves.force(boss, "serum_r", 200) + " | player hp " + p.getHealth()));
                if (w == 80) grab(mc, shot + "_line");
                if (w == 96) grab(mc, shot + "_charge");
                if (w == 101) onServer(server, p -> log("serum_r: right after the charge player hp " + p.getHealth() + " on fire " + p.isOnFire()));
                if (w == 120) onServer(server, p -> log("serum_r: claw at " + boss.blockPosition() + " | stone left " + stone(p.serverLevel(), BlockPos.containing(mark)) + " | moves " + boss.log));
                if (w == 170) onServer(server, p -> {
                    BlockPos c = BlockPos.containing(mark);
                    for (BlockPos b : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, 3, 3))) if (p.serverLevel().getBlockState(b).is(Blocks.STONE)) p.serverLevel().removeBlock(b, false);
                });
            }
            case "serum_w" -> {
                if (w == 50) onServer(server, p -> {
                    // a pet to mark too
                    var wolf = EntityType.WOLF.create(p.serverLevel());
                    wolf.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
                    wolf.tame(p);
                    p.serverLevel().addFreshEntity(wolf);
                    mob = wolf.getUUID();
                });
                if (w == 60) onServer(server, p -> log("serum_w: forced " + ClawMoves.force(boss, "serum_w", 200)));
                if (w == 80) grab(mc, shot + "_marks");
                if (w == 124) grab(mc, shot + "_cuts");
                if (w == 160) onServer(server, p -> {
                    Entity wolf = p.serverLevel().getEntity(mob);
                    log("serum_w: wolf " + (wolf instanceof net.minecraft.world.entity.LivingEntity l ? "hp " + l.getHealth() + "/" + l.getMaxHealth() : "gone") + " | moves " + boss.log);
                    if (wolf != null) wolf.discard();
                });
            }
            case "nosell" -> {
                if (w == 60) onServer(server, p -> {
                    boss.setNoSellUsed(false);
                    float before = boss.getHealth();
                    boss.invulnerableTime = 0;
                    boss.hurt(p.damageSources().playerAttack(p), 50);
                    log("nosell: 50 damage -> hp " + before + " -> " + boss.getHealth() + " (negated if equal)");
                });
                if (w == 62) grab(mc, shot);
                if (w == 80) onServer(server, p -> {
                    float before = boss.getHealth();
                    boss.invulnerableTime = 0;
                    boss.hurt(p.damageSources().playerAttack(p), 50);
                    log("nosell: again 50 -> hp " + before + " -> " + boss.getHealth() + " (should drop now)");
                });
            }
            case "serum_k" -> {
                if (w == 60) onServer(server, p -> {
                    num = boss.getHealth();
                    log("serum_k: forced " + ClawMoves.force(boss, "serum_k", 400) + " | hp " + num);
                });
                if (w == 66) grab(mc, shot + "_orb");
                if (w == 70 || w == 74 || w == 78) onServer(server, p -> {
                    boss.invulnerableTime = 0;
                    boss.hurt(p.damageSources().playerAttack(p), 16);
                });
                if (w == 82) onServer(server, p -> log("serum_k: 48 taken in the window -> groggy " + boss.groggyTicks() + " pose " + boss.bodyPose() + " (5 = groggy) | hp " + boss.getHealth()));
                if (w == 84) grab(mc, shot + "_groggy");
                if (w == 90) onServer(server, p -> {
                    float before = boss.getHealth();
                    boss.invulnerableTime = 0;
                    boss.hurt(p.damageSources().playerAttack(p), 10);
                    log("serum_k: groggy takes more: 10 -> " + (before - boss.getHealth()));
                });
                if (w == 120) onServer(server, p -> {
                    boss.setHealth(boss.getMaxHealth() * 0.6f);
                    num = boss.getHealth();
                    log("serum_k: forced again (left alone) " + ClawMoves.force(boss, "serum_k", 400) + " | hp " + num);
                });
                if (w == 215) onServer(server, p -> log("serum_k: healed " + num + " -> " + boss.getHealth() + " (+15% = " + boss.getMaxHealth() * 0.15f + ") | moves " + boss.log));
            }
            case "ult" -> {
                if (w == 20) onServer(server, p -> {
                    Config.WARP_DIMENSIONS.set(List.of("minecraft:the_nether", "somemod:nowhere", "minecraft:the_end", "minecraft:overworld"));
                    boss.setHealth(boss.getMaxHealth() * 0.9f);
                });
                if (w == 59) onServer(server, p -> {
                    p.setHealth(p.getMaxHealth());
                    mark = p.position();
                    num = p.getHealth();
                    watchDims = true;
                    log("ult: from " + p.level().dimension().location() + " " + fmt(mark));
                });
                if (w == 60) onServer(server, p -> log("ult: forced " + ClawMoves.force(boss, "serum_w_floors", 400)));
                if (w == 58) lastShot = -1;
                // the scene, by its own clock: the grab, then each place (cut in, held up, the rush, the slash), then the finale
                int st = ClawWarpClient.time();
                if (st >= 0) {
                    if (st == 2) log("ult: scene backdrops " + ClawWarpClient.stages() + " | busy " + (mc.player != null) + " | camera " + CinematicCamera.active());
                    int[] at = {7, 15, ClawWarp.VOID_AT + 8, ClawWarp.BARRAGE_AT + 5, ClawWarp.SHATTER_AT + 4};
                    String[] names = {"grab_dive", "grab_held", "void", "barrage", "shatter"};
                    // the scene's clock can jump a tick or two when it resyncs: take each shot the first tick at or past its time
                    for (int i = 0; i < at.length; i++) if (st >= at[i] && lastShot < at[i]) grab(mc, shot + "_" + names[i]);
                    for (int h = 0; h < ClawWarp.HOPS; h++) {
                        int b = ClawWarp.GRAB + h * ClawWarp.HOP;
                        int[] beats = {b + 5, b + ClawWarp.RUSH_FROM + 4, b + ClawWarp.RUSH_TO + 2};
                        String[] what = {"in", "rush", "slash"};
                        for (int i = 0; i < beats.length; i++) if (st >= beats[i] && lastShot < beats[i]) grab(mc, shot + "_place" + (h + 1) + "_" + what[i]);
                    }
                    lastShot = st;
                }
                if (w == 120) onServer(server, p -> log("ult: mid-run server side in " + p.level().dimension().location() + " moved " + String.format("%.3f", p.position().distanceTo(mark))
                        + " | busy " + ClawWarp.busy(p) + " | claw invisible " + boss.isInvisible()));
                if (w == 330) onServer(server, p -> {
                    watchDims = false;
                    log("ult: after in " + p.level().dimension().location() + " | moved " + String.format("%.3f", p.position().distanceTo(mark))
                            + " | hp " + num + " -> " + p.getHealth() + " | busy " + ClawWarp.busy(p) + " | scene over " + !ClawWarpClient.active() + " | camera " + CinematicCamera.active()
                            + " | claw visible " + !boss.isInvisible() + " | moves " + boss.log);
                    Config.WARP_DIMENSIONS.set(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
                });
            }
            case "ult_safety" -> {
                // dying mid-run: it just ends
                if (w == 60) onServer(server, p -> {
                    p.getInventory().clearContent();
                    mark = p.position();
                    log("ult_safety: start " + ClawWarp.start(p, boss, null) + " from " + fmt(mark));
                });
                if (w == 60 + 50) onServer(server, p -> {
                    log("ult_safety: killed mid-run, busy " + ClawWarp.busy(p));
                    p.kill();
                });
                if (w == 170) onServer(server, p -> log("ult_safety: after death and respawn | alive " + p.isAlive() + " | busy " + ClawWarp.busy(p) + " | scene " + ClawWarpClient.active()
                        + " | camera " + CinematicCamera.active()));
                if (w == 300) onServer(server, p -> {
                    survival(p);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BLACK_SILENCE_BOOK.get()));
                    ClawMoves.setSought(p.getMainHandItem(), true);
                });
            }
            case "tri" -> {
                if (w == 59) onServer(server, p -> {
                    p.setHealth(p.getMaxHealth());
                    boss.setHealth(boss.getMaxHealth() * 0.24f);
                    num = boss.getHealth();
                    watchDims = true;
                });
                if (w == 60) onServer(server, p -> log("tri: forced " + ClawMoves.force(boss, "tri_serum", 400) + " | claw hp " + num));
                if (w == 70) onServer(server, p -> {
                    float before = boss.getHealth();
                    boss.invulnerableTime = 0;
                    boss.hurt(p.damageSources().playerAttack(p), 20);
                    log("tri: hit while charging -> " + before + " -> " + boss.getHealth() + " (immune if equal)");
                });
                if (w == 76) grab(mc, shot + "_charge");
                if (w == 104) grab(mc, shot + "_dashes");
                if (w == 136) onServer(server, p -> log("tri: after the dashes player hp " + p.getHealth() + " | claw hp " + num + " -> " + boss.getHealth() + " | busy " + ClawWarp.busy(p)));
                if (w == 330) onServer(server, p -> {
                    watchDims = false;
                    log("tri: end in " + p.level().dimension().location() + " | player hp " + p.getHealth() + " | moves " + boss.log);
                });
            }
            case "retreat" -> {
                if (w == 60) onServer(server, p -> {
                    boss.setNoSellUsed(true);
                    boss.invulnerableTime = 0;
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                    log("retreat: hp " + boss.getHealth() + " state " + boss.state() + " (2 = retreat) | book now: " + books(p));
                });
                // the exit, beat by beat (w = exit tick + 60)
                int[] beats = {Claw.R_HIT + 2, Claw.R_KNEEL + 8, Claw.R_BREAK + 3, Claw.R_BREAK + 10, Claw.R_MASK + 3, Claw.R_LOOK + 8,
                        Claw.R_INJECT + 6, Claw.R_RIFT + 10, Claw.R_RISE + 10, Claw.R_BACK + 14, Claw.R_BACK + 28, Claw.R_SNAP + 1};
                String[] names = {"hit", "kneel", "break", "broken", "mask", "look", "inject", "rift", "rise", "back", "inside", "snap"};
                for (int i = 0; i < beats.length; i++) if (w == beats[i] + 60) grab(mc, shot + "_" + String.format("%02d", i) + "_" + names[i]);
                if (w == 60 + Claw.R_SNAP + 4) onServer(server, p -> log("retreat: drops left behind " + drops(p)));
                if (w == 60 + Claw.RETREAT_TICKS + 10) onServer(server, p -> {
                    log("retreat: claw " + (boss.isRemoved() ? "gone" : "still here, state " + boss.state()) + " | sought " + ClawMoves.ACTIVE.containsKey(p.getUUID()));
                    // the loot, picked up, for the crafting stop
                    for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(20))) it.discard();
                    boss = null;
                });
            }
            case "lose" -> {
                if (w == 4) onServer(server, p -> {
                    survival(p);
                    p.getInventory().clearContent();
                    useBook(p);
                    log("lose: claw " + (boss != null));
                });
                if (w == 70) onServer(server, p -> {
                    log("lose: state " + boss.state() + " | killing the player");
                    p.kill();
                });
                if (w == 74) grab(mc, shot);
                if (w == 90) onServer(server, p -> log("lose: claw state " + boss.state() + " (3 = leave) | book on the floor " + books(p.serverLevel(), boss.position())));
                if (w == 200) onServer(server, p -> {
                    log("lose: claw " + (boss.isRemoved() ? "gone" : "still here") + " | book in inventory " + books(p) + " | sought " + ClawMoves.ACTIVE.containsKey(p.getUUID()));
                    for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(40))) it.discard();
                    boss = null;
                });
            }
            case "handmade" -> handmade(mc, server, w, shot);
            default -> {}
        }
    }

    private static void handmade(Minecraft mc, MinecraftServer server, int w, String shot) {
        if (w == 2) onServer(server, p -> {
            survival(p);
            p.getInventory().clearContent();
            ServerLevel level = p.serverLevel();
            craft(level, "claw_arm", ModItems.BROKEN_CLAW.get(), Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.IRON_INGOT, Items.IRON_INGOT);
            craft(level, "serum_k", ModItems.BROKEN_SERUM_K.get(), Items.GLASS_BOTTLE, Items.GOLDEN_APPLE);
            craft(level, "serum_r", ModItems.BROKEN_SERUM_R.get(), Items.GLASS_BOTTLE, Items.BLAZE_POWDER);
            craft(level, "serum_w", ModItems.BROKEN_SERUM_W.get(), Items.GLASS_BOTTLE, Items.ENDER_PEARL);
            craft(level, "handmade_claw", ModItems.CLAW_ARM.get(), ModItems.SERUM_K.get(), ModItems.SERUM_R.get(), ModItems.SERUM_W.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HANDMADE_CLAW.get()));
            p.removeAllEffects();
        });
        if (w == 10) onServer(server, p -> {
            ClawServer.select(p, ClawServer.K);
            ClawServer.use(p);
            log("handmade K: " + effects(p));
        });
        if (w == 20) grab(mc, shot + "_k");
        if (w == 30) onServer(server, p -> {
            ClawServer.select(p, ClawServer.R);
            ClawServer.use(p);
            log("handmade R: " + effects(p));
        });
        if (w == 40) grab(mc, shot + "_r");
        if (w == 50) onServer(server, p -> {
            mark = p.position();
            p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), 0, 0);
            ClawServer.select(p, ClawServer.W);
            ClawServer.use(p);
            log("handmade W: blinked " + String.format("%.1f", p.position().distanceTo(mark)) + " blocks");
            ClawServer.use(p);
            log("handmade W: again at once (cooldown) -> " + String.format("%.1f", p.position().distanceTo(mark)));
        });
        if (w == 52) grab(mc, shot + "_w");
        if (w == 70) onServer(server, p -> {
            p.removeAllEffects();
            Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
            Vec3 at = p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(3));
            z.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
            z.setNoAi(true);
            z.setPersistenceRequired();
            p.serverLevel().addFreshEntity(z);
            mob = z.getUUID();
            ClawServer.select(p, ClawServer.TRI);
            ClawServer.use(p);
            log("handmade Tri: " + effects(p));
        });
        if (w == 72) grab(mc, shot + "_tri");
        if (w == 80) onServer(server, p -> {
            Entity z = find(server, mob);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1.2, 0));
            mark = z.position();
            num = (float) p.position().distanceTo(Vec3.ZERO);
            watchDims = true;
            ClawServer.use(p);
            log("handmade dimension slash: zombie busy " + ClawWarp.busy(z) + " | player busy " + ClawWarp.busy(p) + " | effects after " + effects(p));
        });
        // the scene, by its own clock: you doing the dragging this time
        if (w == 78) lastShot = -1;
        int st = ClawWarpClient.time();
        if (st >= 0) {
            if (st >= 2 && lastShot < 2) log("handmade scene: backdrops " + ClawWarpClient.stages() + " | camera " + CinematicCamera.active());
            int[] at = {7, 15, ClawWarp.VOID_AT + 8, ClawWarp.SHATTER_AT + 4};
            String[] names = {"grab_dive", "grab_held", "void", "shatter"};
            for (int i = 0; i < at.length; i++) if (st >= at[i] && lastShot < at[i]) grab(mc, shot + "_" + names[i]);
            for (int h = 0; h < ClawWarp.HOPS; h++) {
                int b = ClawWarp.GRAB + h * ClawWarp.HOP;
                int[] beats = {b + ClawWarp.RUSH_FROM + 6, b + ClawWarp.RUSH_TO + 2};
                String[] what = {"rush", "slash"};
                for (int i = 0; i < beats.length; i++) if (st >= beats[i] && lastShot < beats[i]) grab(mc, shot + "_place" + (h + 1) + "_" + what[i]);
            }
            if (st >= 60 && lastShot < 60) onServer(server, p -> {
                Entity z = find(server, mob);
                log("handmade mid-scene: player moved " + String.format("%.3f", p.position().distanceTo(mark.add(0, 0, 0)) - 0) + " busy " + ClawWarp.busy(p)
                        + " | zombie " + (z == null ? "gone" : "moved " + String.format("%.3f", z.position().distanceTo(mark)) + " busy " + ClawWarp.busy(z) + " hp " + ((Zombie) z).getHealth()));
            });
            lastShot = st;
        }
        if (w == 300) onServer(server, p -> {
            watchDims = false;
            Entity z = find(server, mob);
            log("handmade end: player in " + p.level().dimension().location() + " | zombie " + (z == null ? "gone (killed?)" : "in " + z.level().dimension().location() + " moved " + String.format("%.2f", z.position().distanceTo(mark))
                    + " hp " + ((Zombie) z).getHealth()) + " | busy " + ClawWarp.busy(p));
            if (z != null) z.discard();
        });
    }

    // =================================================================== helpers

    private static void ensureClaw(ServerPlayer p) {
        if (boss != null && boss.isAlive() && !boss.isRemoved()) return;
        survival(p);
        useBook(p);
        if (boss != null) boss.skipEntrance();
        log("new claw " + (boss != null));
    }

    private static void useBook(ServerPlayer p) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BLACK_SILENCE_BOOK.get()));
        if (!p.getMainHandItem().isEmpty()) p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
        UUID id = ClawMoves.ACTIVE.get(p.getUUID());
        Entity e = id == null ? null : p.serverLevel().getEntity(id);
        if (e instanceof Claw c) boss = c;
    }

    private static List<Claw> claws(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Claw.class, p.getBoundingBox().inflate(40), Claw::isAlive);
    }

    private static void craft(ServerLevel level, String name, Item... ings) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < ings.length; i++) grid.set(i, new ItemStack(ings[i]));
        var found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), level);
        log("craft " + name + ": " + found.map(r -> r.id() + " -> " + r.value().assemble(CraftingInput.of(3, 3, grid), level.registryAccess()).getHoverName().getString()).orElse("NO RECIPE"));
    }

    private static String effects(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (MobEffectInstance e : p.getActiveEffects()) sb.append(e.getEffect().getRegisteredName()).append(' ').append(e.getAmplifier()).append(' ').append(e.getDuration()).append(", ");
        return sb.toString();
    }

    private static String books(ServerPlayer p) {
        int black = 0, plain = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.BLACK_SILENCE_BOOK.get())) black += s.getCount();
            if (s.is(Items.BOOK)) plain += s.getCount();
        }
        return "black silence " + black + ", plain book " + plain;
    }

    private static int books(ServerLevel level, Vec3 at) {
        int n = 0;
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(30))) if (it.getItem().is(ModItems.BLACK_SILENCE_BOOK.get())) n++;
        return n;
    }

    private static String drops(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(20)))
            sb.append(it.getItem().getHoverName().getString()).append(" x").append(it.getItem().getCount()).append(", ");
        return sb.toString();
    }

    private static int stone(ServerLevel level, BlockPos c) {
        int n = 0;
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, 3, 3))) if (level.getBlockState(b).is(Blocks.STONE)) n++;
        return n;
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void look(ServerPlayer p, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        p.teleportTo(p.serverLevel(), from.x, from.y - p.getEyeHeight(), from.z, yaw, pitch);
    }

    private static void survival(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100000, 2, false, false));
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

    private ClawTour() {}
}
