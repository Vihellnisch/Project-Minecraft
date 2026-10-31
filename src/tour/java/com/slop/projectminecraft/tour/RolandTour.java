package com.slop.projectminecraft.tour;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.LibraryArena;
import com.slop.projectminecraft.boss.Roland;
import com.slop.projectminecraft.boss.RolandKin;
import com.slop.projectminecraft.registry.ModEntities;
import com.slop.projectminecraft.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
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
 * ./gradlew runTourClient -DtourRoland=true [-DtourOnly=invite,arena,looks,p1,p2,p3,p4,p5,death,reset]: the Library
 * Invitation into the reception, both floors, every form, a fight through all five phases (the player kept alive), the
 * drops and the trip home, losing (items kept), and the reset. Logs "TOUR roland ...". Delete run-tour/saves/es_test first.
 */
@EventBusSubscriber(modid = ProjectMinecraft.MODID, value = Dist.CLIENT)
public final class RolandTour {
    static final boolean ENABLED = Boolean.getBoolean("projectminecraft.tour") && Boolean.getBoolean("projectminecraft.tour.roland");
    private static final String WORLD = "es_test";

    private record Stop(String name, int length) {}

    private static final List<Stop> STOPS = new ArrayList<>();

    static {
        STOPS.add(new Stop("invite", 120));
        STOPS.add(new Stop("models", 300));
        STOPS.add(new Stop("mask", 320));
        STOPS.add(new Stop("kneel", 120));
        STOPS.add(new Stop("arena", 130));
        STOPS.add(new Stop("looks", 380));
        STOPS.add(new Stop("p1", 900));
        STOPS.add(new Stop("p2", 760));
        STOPS.add(new Stop("p3", 900));
        STOPS.add(new Stop("p4", 1000));
        STOPS.add(new Stop("p5", 760));
        STOPS.add(new Stop("death", 260));
        STOPS.add(new Stop("reset", 520));
        String only = System.getProperty("projectminecraft.tour.only", "");
        if (!only.isEmpty()) STOPS.removeIf(st -> Arrays.stream(only.split(",")).noneMatch(st.name::contains));
    }

    private static boolean opened;
    private static int ticks = -1, quitting = -1;
    private static Roland boss;
    private static int slot;

    static void log(String s) {
        ProjectMinecraft.LOGGER.info("TOUR roland " + s);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED) return;
        // lost the reception: straight back up
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
        com.slop.projectminecraft.Config.IMPACT_FRAMES.set(false);
        if (!mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261031L, false, false),
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

    // =================================================================== stops

    private static void run(Minecraft mc, MinecraftServer server, Stop s, int w, int index) {
        String shot = "tour_roland_" + String.format("%02d", index) + "_" + s.name;
        // every stop after the first starts in the Library with a Roland to fight
        if (s.name.equals("kneel")) {
            kneel(mc, server, w, shot);
            return;
        }
        if (s.name.equals("mask")) {
            mask(mc, server, w, shot);
            return;
        }
        if (w == 0 && !s.name.equals("invite")) onServer(server, RolandTour::ensureInLibrary);
        if (boss == null && w % 5 == 0 && !s.name.equals("invite")) onServer(server, RolandTour::find);
        // still loading: wait for him
        if (boss == null && !s.name.equals("invite") && !s.name.equals("arena") && !s.name.equals("death")) return;
        switch (s.name) {
            case "models" -> models(mc, server, w, shot);
            case "invite" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    ItemStack card = new ItemStack(ModItems.LIBRARY_INVITATION.get());
                    p.setItemInHand(InteractionHand.MAIN_HAND, card);
                    String from = p.level().dimension().location() + " " + p.blockPosition();
                    card.finishUsingItem(p.level(), p);
                    find(p);
                    ServerLevel lib = p.serverLevel();
                    BlockPos c = LibraryArena.center(slot);
                    log("invite: from " + from + " -> " + p.level().dimension().location() + " " + p.blockPosition() + " | slot " + slot
                            + " | floor centre " + lib.getBlockState(c).getBlock() + " | factory pipes " + lib.getBlockState(LibraryArena.factory(slot).offset(0, 5, -18)).getBlock()
                            + " | roland " + (boss != null ? boss.blockPosition() + " state " + boss.state() : "none"));
                });
                if (w == 4) mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (w == 30) grab(mc, shot + "_arrival");
                if (w == 40) onServer(server, p -> {
                    // and home again with the same card
                    p.getMainHandItem().finishUsingItem(p.level(), p);
                    log("invite: used in the Library -> " + p.level().dimension().location() + " " + p.blockPosition() + " | card still held " + p.getMainHandItem().is(ModItems.LIBRARY_INVITATION.get()));
                });
                if (w == 70) onServer(server, p -> {
                    p.getMainHandItem().finishUsingItem(p.level(), p);
                    find(p);
                    log("invite: back in -> " + p.level().dimension().location() + " same slot " + slot + " | rolands " + countRolands(p));
                });
            }
            case "arena" -> {
                if (w == 1) onServer(server, p -> p.setGameMode(GameType.SPECTATOR));
                if (w == 2) onServer(server, p -> look(p, center().add(0, 14, 22), center().add(0, 2, -6)));
                if (w == 20) grab(mc, shot + "_library_overview");
                if (w == 22) onServer(server, p -> look(p, center().add(0, 2.5, -8), Vec3.atCenterOf(LibraryArena.desk(slot)).add(0, 1, 0)));
                if (w == 40) grab(mc, shot + "_desk");
                if (w == 42) onServer(server, p -> look(p, center().add(-14, 4, 6), center().add(14, 6, -10)));
                if (w == 60) grab(mc, shot + "_shelves");
                if (w == 62) onServer(server, p -> look(p, factory().add(0, 6, 14), factory().add(0, 9, -18)));
                if (w == 85) grab(mc, shot + "_factory_pipes");
                if (w == 87) onServer(server, p -> look(p, factory().add(14, 10, 14), factory().add(-6, 2, -4)));
                if (w == 110) grab(mc, shot + "_factory_overview");
                if (w == 125) onServer(server, p -> {
                    survival(p);
                    Vec3 a = LibraryArena.arrival(slot);
                    p.teleportTo(p.serverLevel(), a.x, a.y, a.z, 180, 0);
                });
            }
            case "looks" -> {
                // each form up close, frozen: front, then back
                byte[] phases = {1, 2, 3, 4, 5};
                for (int i = 0; i < phases.length; i++) {
                    int base = 10 + i * 56;
                    byte ph = phases[i];
                    // the player goes first, so the floor he's put on is loaded
                    if (w == base + 1) onServer(server, p -> {
                        p.setGameMode(GameType.SPECTATOR);
                        Vec3 c = ph == 2 ? factory() : center();
                        p.teleportTo(p.serverLevel(), c.x, c.y + 2, c.z + 6, 180, 0);
                    });
                    if (w == base + 3) onServer(server, p -> {
                        Vec3 c = ph == 2 ? factory() : center();
                        boss.setNoAi(false);
                        boss.forcePhase(ph);
                        boss.teleportTo(c.x, c.y, c.z);
                    });
                    if (w == base + 6) onServer(server, p -> {
                        boss.setNoAi(true);
                        boss.setYRot(0);
                        boss.yBodyRot = boss.yHeadRot = 0;
                        Vec3 c = boss.position();
                        double d = ph == 2 ? 9 : 4.2;
                        look(p, c.add(1.2, ph == 2 ? 3.5 : 1.7, d), c.add(0, ph == 2 ? 1.2 : 1, 0));
                    });
                    if (w == base + 24) grab(mc, shot + "_phase" + ph + "_front");
                    if (w == base + 26) onServer(server, p -> {
                        Vec3 c = boss.position();
                        double d = ph == 2 ? 9 : 4.2;
                        look(p, c.add(ph == 3 ? 3 : -1.2, ph == 2 ? 3.5 : 1.8, -d), c.add(0, 1, 0));
                    });
                    if (w == base + 44) grab(mc, shot + "_phase" + ph + "_back");
                    if (w == base + 46) onServer(server, p -> log("looks phase " + ph + ": kin " + describe(boss.kinList()) + " | dims " + boss.getBbWidth() + "x" + boss.getBbHeight()));
                }
                // he falls: the kneel, his face
                if (w == 301) onServer(server, p -> p.teleportTo(p.serverLevel(), center().x, center().y + 1, center().z + 4, 180, 0));
                if (w == 302) onServer(server, p -> {
                    boss.setNoAi(false);
                    boss.teleportTo(center().x, center().y, center().z);
                    boss.forcePhase((byte) 5);
                    boss.teleportTo(center().x, center().y, center().z);
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                    look(p, center().add(0.8, 1.2, 3.2), center().add(0, 0.6, 0));
                });
                if (w == 340) grab(mc, shot + "_death_kneel");
                if (w == 360) onServer(server, p -> {
                    log("looks death: drops " + drops(p) + " | exits scheduled, player in " + p.level().dimension().location());
                    clearItems(p);
                });
            }
            case "p1" -> {
                if (w == 1) onServer(server, p -> {
                    boss.log.clear();
                    boss.forcePhase(Roland.BLACK_SILENCE);
                });
                fight(mc, server, w);
                for (int k = 1; k <= 7; k++) if (w == k * 100) grab(mc, shot + "_" + w);
                if (w == 740) onServer(server, p -> {
                    log("p1 moves: " + moves(boss.log));
                    log("p1 furioso only after all nine pages: " + furiosoAfterNine(boss.log) + " | player alive " + p.isAlive());
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                });
                if (w == 800) grab(mc, shot + "_distorting");
                if (w == 815) grab(mc, shot + "_blackout");
                if (w == 860) grab(mc, shot + "_beast_out");
                if (w == 890) onServer(server, p -> log("p1 -> phase " + boss.phase() + " state " + boss.state() + " in factory " + (boss.position().distanceTo(factory()) < 30)
                        + " player in factory " + (p.position().distanceTo(factory()) < 30) + " | kin " + describe(boss.kinList())));
            }
            case "p2" -> {
                if (w == 1) onServer(server, p -> {
                    boss.log.clear();
                    if (boss.phase() != Roland.DISTORTED) boss.forcePhase(Roland.DISTORTED);
                });
                fight(mc, server, w);
                for (int k = 1; k <= 6; k++) if (w == k * 100) grab(mc, shot + "_" + w);
                if (w == 300) onServer(server, p -> {
                    // one byproduct down: it should crawl back once
                    for (RolandKin k : boss.kinList()) if (k.kind() == RolandKin.BYPRODUCT) {
                        k.hurt(p.damageSources().playerAttack(p), 1000);
                        break;
                    }
                });
                if (w == 640) onServer(server, p -> {
                    log("p2 moves: " + moves(boss.log));
                    log("p2 byproducts now " + describe(boss.kinList()) + " | player effects " + p.getActiveEffectsMap().keySet().stream().map(e -> e.getRegisteredName()).toList());
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                });
                if (w == 690) grab(mc, shot + "_whiteout");
                if (w == 740) grab(mc, shot + "_settled");
                if (w == 755) onServer(server, p -> log("p2 -> phase " + boss.phase() + " in library " + (boss.position().distanceTo(center()) < 30) + " | kin " + describe(boss.kinList())));
            }
            case "p3" -> {
                if (w == 1) onServer(server, p -> {
                    boss.log.clear();
                    if (boss.phase() != Roland.BOND) boss.forcePhase(Roland.BOND);
                });
                fight(mc, server, w);
                for (int k = 1; k <= 8; k++) if (w == k * 100) grab(mc, shot + "_" + w);
                // bring him down alone: he should come back in 8 s with a link fewer
                if (w == 150) onServer(server, p -> {
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                    log("p3 roland down alone: fallen " + boss.fallen() + " links " + boss.links());
                });
                if (w == 330) onServer(server, p -> log("p3 after 9 s: fallen " + boss.fallen() + " links " + boss.links() + " health " + boss.getHealth()));
                if (w == 340) onServer(server, p -> {
                    RolandKin a = boss.angelicaEntity();
                    if (a != null) a.hurt(p.damageSources().playerAttack(p), 100000);
                    log("p3 angelica down alone: " + (a == null ? "none" : "fallen " + a.fallen() + " links " + a.links()));
                });
                if (w == 520) onServer(server, p -> {
                    RolandKin a = boss.angelicaEntity();
                    log("p3 after 9 s: angelica " + (a == null ? "none" : "fallen " + a.fallen() + " links " + a.links()));
                    // both out of links: the paralysis
                    boss.setLinks(0, 0);
                });
                if (w == 540) onServer(server, p -> log("p3 paralysis: " + boss.log.stream().anyMatch(l -> l.contains("paralysed")) + " pose " + boss.bodyPose()));
                if (w == 700) onServer(server, p -> {
                    log("p3 moves: " + moves(boss.log));
                    RolandKin a = boss.angelicaEntity();
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                    if (a != null) a.hurt(p.damageSources().playerAttack(p), 100000);
                    log("p3 both down: state " + boss.state());
                });
                if (w == 890) onServer(server, p -> log("p3 -> phase " + boss.phase() + " | kin " + describe(boss.kinList())));
            }
            case "p4" -> {
                if (w == 1) onServer(server, p -> {
                    boss.log.clear();
                    if (boss.phase() != Roland.WEAPONS) boss.forcePhase(Roland.WEAPONS);
                    boss.sceneNow();
                });
                fight(mc, server, w);
                for (int k = 1; k <= 9; k++) if (w == k * 100) grab(mc, shot + "_" + w);
                // break one of the first three weapons: two get nailed
                if (w == 60) onServer(server, p -> {
                    for (RolandKin k : boss.kinList()) if (k.kind() == RolandKin.WEAPON) {
                        k.hurt(p.damageSources().playerAttack(p), 1000);
                        break;
                    }
                    log("p4 scene weapons after breaking one: " + describe(boss.kinList()));
                });
                if (w == 900) onServer(server, p -> {
                    log("p4 moves: " + moves(boss.log));
                    log("p4 furioso hits match nailed: " + boss.log.stream().filter(l -> l.contains("weapons_furioso") || l.contains(" furioso_")).toList());
                    boss.hurt(p.damageSources().playerAttack(p), 250);
                });
                if (w == 930) onServer(server, p -> log("p4 -> phase " + boss.phase() + " hp " + Math.round(boss.hp() * 100) + "% | kin " + describe(boss.kinList())));
                if (w == 960) grab(mc, shot + "_ready_to_act");
            }
            case "p5" -> {
                if (w == 1) onServer(server, p -> {
                    boss.log.clear();
                    if (boss.phase() != Roland.BYGONES) {
                        boss.forcePhase(Roland.WEAPONS);
                        boss.hurt(p.damageSources().playerAttack(p), 250);
                    }
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                });
                fight(mc, server, w);
                if (w == 20) onServer(server, p -> {
                    float before = boss.getHealth();
                    boolean took = boss.hurt(p.damageSources().playerAttack(p), 20);
                    log("p5 ready to act: immune " + (!took && boss.getHealth() == before) + " | images " + describe(boss.kinList()));
                });
                if (w == 100) grab(mc, shot + "_images");
                if (w == 120) onServer(server, p -> {
                    for (RolandKin k : boss.kinList()) if (k.kind() == RolandKin.IMAGE) k.hurt(p.damageSources().playerAttack(p), 1000);
                });
                if (w == 145) grab(mc, shot + "_scream");
                for (int k = 2; k <= 5; k++) if (w == k * 100) grab(mc, shot + "_" + w);
                if (w == 560) onServer(server, p -> {
                    log("p5 moves: " + moves(boss.log) + " | sword still in hand " + p.getMainHandItem().is(Items.IRON_SWORD));
                    boss.hurt(p.damageSources().playerAttack(p), 100000);
                });
                if (w == 600) grab(mc, shot + "_fallen");
                if (w == 620) onServer(server, p -> log("p5 death drops left on the library floor: " + drops(p)));
                if (w == 750) onServer(server, p -> {
                    int cards = 0;
                    for (ItemStack it : p.getInventory().items) if (it.is(ModItems.LIBRARY_INVITATION.get())) cards += it.getCount();
                    log("p5 after the win: player in " + p.level().dimension().location() + " | invitations left " + cards);
                    StringBuilder home = new StringBuilder();
                    for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4)))
                        home.append(it.getItem().getHoverName().getString()).append(" ").append(String.format("%.1f", it.distanceTo(p))).append(" away, ");
                    log("p5 reward at home: " + home);
                    clearItems(p);
                });
            }
            case "death" -> {
                if (w == 2) onServer(server, p -> {
                    p.getInventory().clearContent();
                    p.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
                    p.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 5));
                    p.getInventory().add(new ItemStack(ModItems.LIBRARY_INVITATION.get()));
                    p.removeAllEffects();
                    p.setGameMode(GameType.SURVIVAL);
                    log("death: dying in " + p.level().dimension().location() + " with " + inventory(p));
                    p.hurt(p.damageSources().fellOutOfWorld(), 10000);
                });
                if (w == 80) onServer(server, p -> log("death: after respawn in " + p.level().dimension().location() + " alive " + p.isAlive() + " with " + inventory(p)
                        + " | items left on the library floor " + itemsOnFloor(server)));
            }
            case "reset" -> {
                if (w == 2) onServer(server, p -> {
                    survival(p);
                    Vec3 c = center();
                    p.teleportTo(p.serverLevel(), c.x, c.y, c.z + 6, 180, 0);
                });
                if (w == 120) onServer(server, p -> {
                    boss.hurt(p.damageSources().playerAttack(p), 60);
                    log("reset: fighting: state " + boss.state() + " phase " + boss.phase() + " health " + boss.getHealth());
                    // walk out on him with the card
                    ItemStack card = new ItemStack(ModItems.LIBRARY_INVITATION.get());
                    p.setItemInHand(InteractionHand.MAIN_HAND, card);
                    card.finishUsingItem(p.level(), p);
                    log("reset: left to " + p.level().dimension().location());
                });
                if (w == 400) onServer(server, p -> {
                    p.getMainHandItem().finishUsingItem(p.level(), p);
                    find(p);
                });
                if (w == 430) onServer(server, p -> log("reset: back after 14 s: state " + boss.state() + " phase " + boss.phase() + " health " + boss.getHealth() + "/" + boss.getMaxHealth()
                        + " at the desk " + (boss.blockPosition().distManhattan(LibraryArena.desk(slot)) <= 2) + " | rolands " + countRolands(p)));
            }
            default -> {}
        }
    }

    /** A Roland of its own, frozen for the camera: the beast and a byproduct, then his black weapons in hand and behind him. */
    private static Roland model;

    private static void models(Minecraft mc, MinecraftServer server, int w, String shot) {
        if (w == 2) onServer(server, p -> {
            p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(p.serverLevel(), factory().x, factory().y + 3, factory().z + 14, 180, 0);
        });
        if (w == 6) onServer(server, p -> {
            model = ModEntities.ROLAND.get().create(p.serverLevel());
            model.moveTo(factory().x, factory().y, factory().z, 0, 0);
            p.serverLevel().addFreshEntity(model);
            model.forcePhase(Roland.DISTORTED);
        });
        if (w == 9) onServer(server, p -> {
            model.setNoAi(true);
            model.setYRot(0);
            model.yBodyRot = model.yHeadRot = 0;
            look(p, factory().add(-9, 6, -12), factory().add(0, 3, -1));
        });
        if (w == 30) grab(mc, shot + "_beast_front");
        if (w == 32) onServer(server, p -> look(p, factory().add(15, 4, 1), factory().add(0, 3.2, 1)));
        if (w == 52) grab(mc, shot + "_beast_side");
        if (w == 54) onServer(server, p -> look(p, factory().add(-11, 7, -15), factory().add(0, 3, 1)));
        if (w == 74) grab(mc, shot + "_beast_back");
        if (w == 76) onServer(server, p -> {
            for (RolandKin k : model.kinList()) if (k.kind() == RolandKin.BYPRODUCT) {
                k.setNoAi(true);
                k.setYRot(0);
                k.yBodyRot = k.yHeadRot = 0;
                look(p, k.position().add(1.5, 2.2, 4.5), k.position().add(0, 1.6, 0));
                log("models byproduct at " + k.blockPosition() + " dims " + k.getBbWidth() + "x" + k.getBbHeight());
                break;
            }
            log("models beast dims " + model.getBbWidth() + "x" + model.getBbHeight());
        });
        if (w == 96) grab(mc, shot + "_byproduct_front");
        if (w == 98) onServer(server, p -> {
            for (RolandKin k : model.kinList()) if (k.kind() == RolandKin.BYPRODUCT) {
                look(p, k.position().add(4.5, 1.8, 0.5), k.position().add(0, 1.4, 0));
                break;
            }
        });
        if (w == 118) grab(mc, shot + "_byproduct_side");
        // into the library, settled: Durandal gone black in his hand
        if (w == 120) onServer(server, p -> p.teleportTo(p.serverLevel(), center().x, center().y + 1, center().z + 5, 180, 0));
        if (w == 124) onServer(server, p -> {
            for (RolandKin k : model.kinList()) k.discard();
            model.discard();
            model = ModEntities.ROLAND.get().create(p.serverLevel());
            model.moveTo(center().x, center().y, center().z, 0, 0);
            p.serverLevel().addFreshEntity(model);
            model.forcePhase(Roland.WEAPONS);
        });
        if (w == 127) onServer(server, p -> {
            model.setNoAi(true);
            model.setYRot(0);
            model.yBodyRot = model.yHeadRot = 0;
            look(p, center().add(1.2, 1.6, 2.6), center().add(0, 1.0, 0));
        });
        if (w == 150) grab(mc, shot + "_durandal_black");
        int[] ws = {com.slop.projectminecraft.arsenal.FuriosoScript.GREATSWORD, com.slop.projectminecraft.arsenal.FuriosoScript.HAMMER, com.slop.projectminecraft.arsenal.FuriosoScript.SHOTGUN};
        for (int i = 0; i < ws.length; i++) {
            int wpn = ws[i];
            if (w == 152 + i * 22) onServer(server, p -> model.showWeapon(wpn));
            if (w == 170 + i * 22) grab(mc, shot + "_weapon_" + wpn);
        }
        if (w == 220) onServer(server, p -> {
            model.setNoAi(false);
            model.sceneNowAndSpawn();
            model.setNoAi(true);
            look(p, center().add(3.5, 2.5, 4), center().add(0, 2.2, 1.5));
        });
        if (w == 250) onServer(server, p -> look(p, center().add(0, 2.6, -5.5), center().add(0, 2.4, 1)));
        if (w == 270) grab(mc, shot + "_weapons_behind");
        if (w == 290) onServer(server, p -> {
            log("models kin " + describe(model.kinList()));
            for (RolandKin k : model.kinList()) k.discard();
            model.discard();
        });
    }

    /** The Perception-Blocking Mask: worn among a zombie, a skeleton and an enderman (stared at); then the zombie is struck. */
    private static final List<net.minecraft.world.entity.Mob> MOBS = new ArrayList<>();

    private static void mask(Minecraft mc, MinecraftServer server, int w, String shot) {
        if (w == 2) onServer(server, p -> {
            if (LibraryArena.inLibrary(p.level())) LibraryArena.leave(p);
        });
        if (w == 10) onServer(server, p -> {
            ServerLevel level = p.serverLevel();
            level.setDayTime(18000);
            survival(p);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.BLACK_SILENCE_MASK.get()));
            MOBS.clear();
            var types = List.of(net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.world.entity.EntityType.SKELETON, net.minecraft.world.entity.EntityType.ENDERMAN);
            for (int i = 0; i < types.size(); i++) {
                var m = types.get(i).create(level);
                m.moveTo(p.getX() + (i - 1) * 3, p.getY(), p.getZ() + 6, 180, 0);
                level.addFreshEntity(m);
                MOBS.add(m);
            }
            look(p, p.getEyePosition(), MOBS.get(2).getEyePosition());
        });
        if (w == 12) mc.options.setCameraType(CameraType.FIRST_PERSON);
        // staring straight into the enderman's eyes
        if (w > 12 && w < 110 && w % 5 == 0) onServer(server, p -> look(p, p.getEyePosition(), MOBS.get(2).getEyePosition()));
        if (w == 110) onServer(server, p -> log("mask: after 5 s masked, staring at the enderman: " + targets(p)));
        if (w == 112) onServer(server, p -> {
            MOBS.get(0).hurt(p.damageSources().playerAttack(p), 1);
            log("mask: struck the zombie");
        });
        if (w == 180) onServer(server, p -> log("mask: 3 s after striking the zombie: " + targets(p)));
        if (w == 182) onServer(server, p -> {
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            look(p, p.getEyePosition(), MOBS.get(2).getEyePosition());
        });
        if (w > 182 && w < 250 && w % 5 == 0) onServer(server, p -> look(p, p.getEyePosition(), MOBS.get(2).getEyePosition()));
        if (w == 250) onServer(server, p -> log("mask: 3 s after taking it off: " + targets(p)));
        if (w == 252) onServer(server, p -> {
            MOBS.forEach(net.minecraft.world.entity.Entity::discard);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.BLACK_SILENCE_MASK.get()));
            p.setGameMode(GameType.CREATIVE);
            p.setYRot(0);
            p.setXRot(0);
        });
        if (w == 254) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (w == 280) grab(mc, shot + "_worn_front");
        if (w == 282) onServer(server, p -> p.setYRot(60));
        if (w == 300) grab(mc, shot + "_worn_side");
        if (w == 318) mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    /** A Roland of its own, out in the open, killed: the kneel from the front and the side. */
    private static void kneel(Minecraft mc, MinecraftServer server, int w, String shot) {
        if (w == 2) onServer(server, p -> {
            if (LibraryArena.inLibrary(p.level())) LibraryArena.leave(p);
        });
        if (w == 10) onServer(server, p -> {
            p.serverLevel().setDayTime(6000);
            p.setGameMode(GameType.SPECTATOR);
            model = ModEntities.ROLAND.get().create(p.serverLevel());
            model.moveTo(p.getX(), p.getY(), p.getZ() + 4, 180, 0);
            p.serverLevel().addFreshEntity(model);
            model.forcePhase(Roland.BYGONES);
        });
        if (w == 14) onServer(server, p -> {
            model.setYRot(180);
            model.yBodyRot = model.yHeadRot = 180;
            model.hurt(p.damageSources().playerAttack(p), 100000);
            look(p, model.position().add(0.4, 1.2, -2.6), model.position().add(0, 0.6, 0));
        });
        if (w == 50) grab(mc, shot + "_front");
        if (w == 52) onServer(server, p -> look(p, model.position().add(2.8, 1.0, 0.2), model.position().add(0, 0.6, 0)));
        if (w == 75) grab(mc, shot + "_side");
    }

    private static String targets(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (var m : MOBS) {
            sb.append(m.getType().toShortString()).append(" -> ").append(m.getTarget() == p ? "YOU" : m.getTarget() == null ? "nobody" : m.getTarget().getType().toShortString());
            if (m instanceof net.minecraft.world.entity.monster.EnderMan e) sb.append(" (creepy ").append(e.isCreepy()).append(")");
            sb.append(", ");
        }
        return sb.toString();
    }

    /** The player fights on: kept alive and in range, facing him, the camera behind. */
    private static void fight(Minecraft mc, MinecraftServer server, int w) {
        if (w == 2) {
            onServer(server, RolandTour::survival);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (w % 10 == 0) onServer(server, p -> {
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.clearFire();
            if (boss == null || !boss.isAlive()) return;
            Vec3 c = boss.phase() == Roland.DISTORTED ? factory() : center();
            if (p.position().distanceTo(c) > 17 && p.level() == boss.level()) p.teleportTo(p.serverLevel(), c.x, c.y, c.z + 6, 180, 0);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, boss.position().add(0, 1, 0));
        });
    }

    // =================================================================== helpers

    private static void ensureInLibrary(ServerPlayer p) {
        if (!LibraryArena.inLibrary(p.level())) {
            ItemStack card = new ItemStack(ModItems.LIBRARY_INVITATION.get());
            card.finishUsingItem(p.level(), p);
        }
        find(p);
        if ((boss == null || !boss.isAlive()) && !LibraryArena.hasRoland(p.serverLevel(), slot)) {
            Roland r = ModEntities.ROLAND.get().create(p.serverLevel());
            BlockPos d = LibraryArena.desk(slot);
            r.moveTo(d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 0, 0);
            r.setHome(slot, d, 0);
            p.serverLevel().addFreshEntity(r);
            LibraryArena.ensureRolandRecorded(p.serverLevel(), slot);
            boss = r;
            log("new roland at the desk");
        }
        if (boss != null) boss.setNoAi(false);
    }

    private static void find(ServerPlayer p) {
        if (!LibraryArena.inLibrary(p.level())) return;
        var all = p.serverLevel().getEntitiesOfClass(Roland.class, p.getBoundingBox().inflate(500, 200, 500), Roland::isAlive);
        Roland best = null;
        for (Roland r : all) if (best == null || r.distanceTo(p) < best.distanceTo(p)) best = r;
        boss = best;
        if (best != null) slot = best.slot();
    }

    private static int countRolands(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Roland.class, p.getBoundingBox().inflate(800, 200, 800), Roland::isAlive).size();
    }

    private static Vec3 center() {
        BlockPos c = LibraryArena.center(slot);
        return new Vec3(c.getX() + 0.5, LibraryArena.FLOOR + 1, c.getZ() + 0.5);
    }

    private static Vec3 factory() {
        BlockPos c = LibraryArena.factory(slot);
        return new Vec3(c.getX() + 0.5, LibraryArena.FLOOR + 1, c.getZ() + 0.5);
    }

    private static void look(ServerPlayer p, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        p.teleportTo(p.serverLevel(), from.x, from.y - p.getEyeHeight(), from.z, yaw, pitch);
    }

    private static String describe(List<RolandKin> kin) {
        StringBuilder sb = new StringBuilder();
        for (RolandKin k : kin) {
            sb.append(switch (k.kind()) {
                case RolandKin.BYPRODUCT -> "byproduct";
                case RolandKin.ANGELICA -> "angelica(links " + k.links() + (k.fallen() ? ",fallen" : "") + ")";
                case RolandKin.WEAPON -> "weapon" + k.variant() + (k.nailed() ? "(nailed)" : "");
                default -> "image" + k.variant();
            }).append(' ');
        }
        return sb.toString().trim();
    }

    private static String moves(List<String> log) {
        java.util.Map<String, Integer> n = new java.util.TreeMap<>();
        for (String l : log) {
            String[] parts = l.split(" ");
            if (parts.length > 1) n.merge(parts[1], 1, Integer::sum);
        }
        return n.toString();
    }

    /** Furioso never comes before every one of the nine pages has been used since the last. */
    private static String furiosoAfterNine(List<String> log) {
        java.util.Set<String> pages = new java.util.HashSet<>();
        int furiosos = 0;
        for (String l : log) {
            String[] parts = l.split(" ");
            if (parts.length < 2) continue;
            if (parts[1].startsWith("page_")) pages.add(parts[1]);
            if (parts[1].equals("furioso")) {
                furiosos++;
                if (pages.size() < 9) return "NO (furioso after " + pages.size() + " pages)";
                pages.clear();
            }
        }
        return "yes (" + furiosos + " furioso, " + pages.size() + " pages since)";
    }

    private static String drops(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(center())).inflate(40)))
            sb.append(it.getItem().getHoverName().getString()).append(" x").append(it.getItem().getCount()).append(", ");
        return sb.toString();
    }

    private static void clearItems(ServerPlayer p) {
        if (!LibraryArena.inLibrary(p.level())) return;
        p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(center())).inflate(60)).forEach(ItemEntity::discard);
    }

    private static int itemsOnFloor(MinecraftServer server) {
        ServerLevel lib = server.getLevel(LibraryArena.LIBRARY);
        return lib == null ? -1 : lib.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(center())).inflate(60)).size();
    }

    private static String inventory(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack it : p.getInventory().items) if (!it.isEmpty()) sb.append(it.getHoverName().getString()).append(" x").append(it.getCount()).append(", ");
        return sb.toString();
    }

    private static void survival(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100000, 3, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 2, false, false));
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

    private RolandTour() {}
}
