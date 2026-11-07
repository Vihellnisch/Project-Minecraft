package com.slop.projectminecraft.warp;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.ClawStagePayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModDamageTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Serum W, "through every floor", the way Library of Ruina shows it: seen from the side, he has the victim by the throat
 * and rushes them across one place, then the next, then the next, a slash at the end of each, then through a void and
 * home again for the last cut. Used by the Claw on its target, and by a player with the Handmade Claw's Tri-serum on a mob.
 * <p>
 * Nobody actually leaves: the places are backdrops. When the run starts, a slab of blocks is copied out of where it began
 * and out of each dimension it visits (any from the config, mod dimensions too) and sent to the players in the scene,
 * who see it played out on those backdrops (ClawWarpClient). Meanwhile the victim (and the Handmade Claw's user) stay put,
 * held still and safe from anything but the run's own slashes. No loading screens, nothing to get lost in.
 */
public final class ClawWarp {
    private ClawWarp() {}

    /**
     * Timeline, in ticks: the grab (in a side view of where it began), then each stage HOP long: cut in already moving, the
     * rush right across it from RUSH_FROM, the slash landing mid-stride at RUSH_TO, still going when it cuts to the next;
     * then the void, the slash barrage, the shatter (back for real).
     */
    public static final int GRAB = 24, HOP = 22, HOPS = 3, RUSH_FROM = 2, RUSH_TO = 16, VOID_AT = GRAB + HOPS * HOP, BARRAGE_AT = VOID_AT + 18,
            SHATTER_AT = BARRAGE_AT + 22, TOTAL = SHATTER_AT + 16, WATCHDOG = TOTAL + 100;
    /** ArsenalFxPayload.CLAW sub-kinds sent by the run (ints[0]); then [victim, step, rider, 1 if the Claw drags them, attacker]. */
    public static final int FX_WARP_GRAB = 20, FX_WARP_HOP = 21, FX_WARP_VOID = 22, FX_WARP_SHATTER = 23, FX_WARP_END = 24;
    /** The backdrop slabs: SX along the rush, SY high (BELOW of it under their feet), SZ deep (FRONT of it in front of them). */
    public static final int SX = 32, SY = 16, SZ = 11, BELOW = 5, FRONT = 2;
    /**
     * Ticks the victim is held before the scene's clock starts. Copying the backdrops can load chunks in other dimensions
     * and make that tick long; the server then runs the ticks after it back to back to catch up, which would race the
     * timeline ahead of what the players see. So these only count at the normal pace (PREP_NANOS apart): the scene starts
     * once the server has caught up, however long that takes.
     */
    static final int PREP = 10;
    static final long PREP_NANOS = 40_000_000L;

    private static final class Run {
        final UUID victim;
        @Nullable final UUID attacker;
        /** The Handmade Claw's user, who does the dragging in their own scene, or null. */
        @Nullable final UUID rider;
        final float hopDamage, finalDamage;
        final int victimId;
        final ServerLevel level;
        /** Where everyone in it is held. */
        final Map<UUID, Vec3> pins = new LinkedHashMap<>();
        @Nullable Entity mob;
        int t = -PREP;
        long lastPrep;

        Run(UUID victim, int victimId, ServerLevel level, @Nullable UUID attacker, @Nullable UUID rider, float hopDamage, float finalDamage) {
            this.victim = victim;
            this.victimId = victimId;
            this.level = level;
            this.attacker = attacker;
            this.rider = rider;
            this.hopDamage = hopDamage;
            this.finalDamage = finalDamage;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** Everyone currently in a run (victims and riders), for the damage guard. */
    private static final Map<UUID, Run> INSIDE = new HashMap<>();

    public static boolean busy(Entity e) {
        return INSIDE.containsKey(e.getUUID());
    }

    /**
     * Start a run on `victim`, from `attacker` (may be null). `rider`: the player who does the dragging (the Handmade
     * Claw's user) or null. Returns false if nothing could be done (already in one, a boss).
     */
    public static boolean start(LivingEntity victim, @Nullable LivingEntity attacker, @Nullable ServerPlayer rider) {
        if (!(victim.level() instanceof ServerLevel from) || busy(victim) || (rider != null && busy(rider))) return false;
        if (!(victim instanceof ServerPlayer) && !canDrag(victim)) return false;
        float total = (float) (victim.getMaxHealth() * Config.WARP_DAMAGE.get());
        Run run = new Run(victim.getUUID(), victim.getId(), from, attacker == null ? null : attacker.getUUID(), rider == null ? null : rider.getUUID(), total * 0.12f, total * 0.64f);
        // held on foot: off whatever they ride
        victim.stopRiding();
        if (rider != null) rider.stopRiding();
        // the backdrops: where it began, then each dimension
        List<ClawStagePayload> stages = new ArrayList<>();
        stages.add(capture(victim.getId(), 0, from, victim.position()));
        List<ServerLevel> picks = pick(from.getServer(), from);
        for (int i = 0; i < picks.size(); i++) stages.add(capture(victim.getId(), i + 1, picks.get(i), landing(picks.get(i), victim.position(), from)));
        run.pins.put(victim.getUUID(), victim.position());
        if (rider != null) run.pins.put(rider.getUUID(), rider.position());
        run.mob = victim instanceof ServerPlayer ? null : victim;
        for (UUID id : run.pins.keySet()) INSIDE.put(id, run);
        RUNS.put(victim.getUUID(), run);
        // the scene is for the ones in it: the victim if it's a player, and whoever drags them
        for (UUID id : run.pins.keySet()) {
            if (from.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p) for (ClawStagePayload s : stages) Network.sendTo(p, s);
        }
        // the scene starts (FX_WARP_GRAB) once the server has caught up: step(), at t = 0
        return true;
    }

    /** Bosses aren't dragged anywhere (the dimension slash still hits them, without the scene). */
    static boolean canDrag(LivingEntity e) {
        return e instanceof Mob && !e.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES);
    }

    // ------------------------------------------------------------------ the places

    /** Three distinct dimensions from the config, the current one only if there aren't enough others. */
    static List<ServerLevel> pick(MinecraftServer server, ServerLevel current) {
        List<ServerLevel> found = new ArrayList<>(), here = new ArrayList<>();
        for (String id : Config.WARP_DIMENSIONS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            ServerLevel l = rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
            if (l == null || found.contains(l) || here.contains(l)) continue;
            (l == current ? here : found).add(l);
        }
        Collections.shuffle(found, new java.util.Random(current.random.nextLong()));
        while (found.size() < HOPS && !here.isEmpty()) found.add(here.remove(0));
        // nothing usable configured: where it began, then
        if (found.isEmpty()) found.add(current);
        // fewer than three: visit them again rather than skip the beats; more: just three
        for (int i = 0; found.size() < HOPS; i++) found.add(found.get(i));
        return new ArrayList<>(found.subList(0, HOPS));
    }

    /** Where to stand in `level`: the same spot scaled into it, on the ground there (below the roof in the Nether, on the End's island). */
    static Vec3 landing(ServerLevel level, Vec3 from, ServerLevel fromLevel) {
        double scale = fromLevel.dimensionType().coordinateScale() / level.dimensionType().coordinateScale();
        double x = from.x * scale, z = from.z * scale;
        if (level.dimension() == Level.END) {
            // the main island, somewhere near the middle
            x = (level.random.nextDouble() - 0.5) * 40;
            z = 40 + level.random.nextDouble() * 20;
        }
        var border = level.getWorldBorder();
        x = Math.max(border.getMinX() + 2, Math.min(border.getMaxX() - 2, x));
        z = Math.max(border.getMinZ() + 2, Math.min(border.getMaxZ() - 2, z));
        BlockPos col = BlockPos.containing(x, 0, z);
        level.getChunk(col); // loads it if it isn't (once per place, as the run starts)
        int y;
        if (level.dimensionType().hasCeiling()) {
            // below the roof: the first three blocks of air over something solid, from the middle down
            int top = Math.min(level.getMaxBuildHeight() - 4, level.getMinBuildHeight() + level.getLogicalHeight() - 8);
            y = Integer.MIN_VALUE;
            for (int yy = top; yy > level.getMinBuildHeight() + 4; yy--) {
                BlockPos p = new BlockPos(col.getX(), yy, col.getZ());
                if (level.isEmptyBlock(p) && level.isEmptyBlock(p.above()) && level.isEmptyBlock(p.above(2))
                        && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                    y = yy;
                    break;
                }
            }
            if (y == Integer.MIN_VALUE) y = Math.min(top, 70);
        } else {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
            if (y <= level.getMinBuildHeight()) y = Math.max(level.getSeaLevel(), level.getMinBuildHeight() + 64);
        }
        return new Vec3(col.getX() + 0.5, y, col.getZ() + 0.5);
    }

    /** Copy the slab of blocks around `feet` in `level` (SX x SY x SZ, the rush along x), and the colours of the place. */
    static ClawStagePayload capture(int victim, int index, ServerLevel level, Vec3 feet) {
        BlockPos base = BlockPos.containing(feet.x, feet.y + 0.01, feet.z);
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        palette.put(Blocks.AIR.defaultBlockState(), 0);
        byte[] blocks = new byte[SX * SY * SZ];
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int j = 0; j < SY; j++) {
            for (int k = 0; k < SZ; k++) {
                for (int i = 0; i < SX; i++) {
                    at.set(base.getX() + i - SX / 2, base.getY() + j - BELOW, base.getZ() + k - (SZ - 1 - FRONT));
                    BlockState s = level.isInWorldBounds(at) ? level.getBlockState(at) : Blocks.AIR.defaultBlockState();
                    Integer id = palette.get(s);
                    if (id == null) {
                        // more kinds than fit in a byte (rare): the rest shows as air
                        if (palette.size() >= 255) id = 0;
                        else palette.put(s, id = palette.size());
                    }
                    blocks[n++] = (byte) (int) id;
                }
            }
        }
        int[] ids = palette.keySet().stream().mapToInt(Block::getId).toArray();
        Biome biome = level.getBiome(base).value();
        return new ClawStagePayload(victim, index, level.dimension().location().toString(), SX, SY, SZ, ids, blocks, biome.getSkyColor(), biome.getFogColor(),
                level.dimensionType().hasCeiling());
    }

    // ------------------------------------------------------------------ the run

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RUNS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Run run : new ArrayList<>(RUNS.values())) {
            try {
                step(server, run);
            } catch (Exception ex) {
                ProjectMinecraft.LOGGER.error("Project Minecraft: Serum W run failed, ending it", ex);
                finish(server, run, false);
            }
        }
    }

    private static void step(MinecraftServer server, Run run) {
        if (run.t < 0) {
            // holding before the scene: a tick only counts if it came at the normal pace (not a catch-up tick)
            long now = System.nanoTime();
            boolean paced = now - run.lastPrep >= PREP_NANOS;
            run.lastPrep = now;
            if (!paced) {
                LivingEntity v = victim(server, run);
                if (v != null) hold(server, run, v);
                if (v == null || !v.isAlive()) finish(server, run, false);
                return;
            }
        }
        run.t++;
        LivingEntity victim = victim(server, run);
        ServerPlayer rider = run.rider == null ? null : server.getPlayerList().getPlayer(run.rider);
        // gone, dead, or somehow out of the world it began in: it's over
        if (victim == null || !victim.isAlive() || victim.level() != run.level || run.t > WATCHDOG
                || (run.rider != null && (rider == null || !rider.isAlive() || rider.level() != run.level))) {
            finish(server, run, false);
            return;
        }
        int t = run.t;
        if (t == 0) fx(run, FX_WARP_GRAB, 0, victim);
        // each stage: the slash lands mid-rush
        for (int i = 0; i < HOPS; i++) {
            int at = GRAB + i * HOP;
            if (t == at) fx(run, FX_WARP_HOP, i, victim);
            if (t == at + RUSH_TO) hurt(server, run, victim, run.hopDamage, false);
        }
        if (t == VOID_AT) fx(run, FX_WARP_VOID, 0, victim);
        if (t == SHATTER_AT) {
            fx(run, FX_WARP_SHATTER, 0, victim);
            hurt(server, run, victim, run.finalDamage, true);
        }
        hold(server, run, victim);
        if (t >= TOTAL) finish(server, run, true);
    }

    @Nullable
    private static LivingEntity victim(MinecraftServer server, Run run) {
        if (run.mob != null) return run.mob instanceof LivingEntity l && !run.mob.isRemoved() ? l : null;
        return server.getPlayerList().getPlayer(run.victim);
    }

    /** Nobody moves, falls or burns while they're in it: held on the spot it began. */
    private static void hold(MinecraftServer server, Run run, LivingEntity victim) {
        for (Map.Entry<UUID, Vec3> pin : run.pins.entrySet()) {
            Entity e = pin.getKey().equals(run.victim) ? victim : server.getPlayerList().getPlayer(pin.getKey());
            if (e == null) continue;
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
            e.clearFire();
            Vec3 p = pin.getValue();
            if (e.position().distanceToSqr(p) > 0.01) {
                if (e instanceof ServerPlayer sp) sp.teleportTo(p.x, p.y, p.z);
                else e.teleportTo(p.x, p.y, p.z);
            }
        }
    }

    /** The slashes ignore armour; only the last one (back home) can kill. */
    private static void hurt(MinecraftServer server, Run run, LivingEntity victim, float amount, boolean lethal) {
        if (amount <= 0) return;
        Entity attacker = run.attacker == null ? null : findAttacker(server, run.attacker);
        if (!lethal) amount = Math.min(amount, victim.getHealth() - 1);
        if (amount <= 0) return;
        victim.invulnerableTime = 0;
        victim.hurt(ModDamageTypes.source(victim.level(), ModDamageTypes.CLAW_WARP, attacker, attacker), amount);
    }

    @Nullable
    private static Entity findAttacker(MinecraftServer server, UUID id) {
        Entity p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** End of a run (or a failure, or someone gone): everyone is let go, and the scene ends. */
    private static void finish(MinecraftServer server, Run run, boolean clean) {
        if (RUNS.get(run.victim) != run) return;
        RUNS.remove(run.victim);
        for (UUID id : run.pins.keySet()) INSIDE.remove(id, run);
        // everyone in it hears it's over, even with the victim gone
        int rider = -1;
        if (run.rider != null && server.getPlayerList().getPlayer(run.rider) instanceof ServerPlayer r) rider = r.getId();
        Vec3 at = run.pins.get(run.victim);
        ArsenalFxPayload end = ArsenalFxPayload.of(ArsenalFxPayload.CLAW, at, Vec3.ZERO, FX_WARP_END, run.victimId, clean ? 1 : 0, rider, 0, -1);
        for (UUID id : run.pins.keySet()) {
            if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer p) Network.sendTo(p, end);
        }
        for (ServerPlayer near : run.level.players()) {
            if (!run.pins.containsKey(near.getUUID()) && near.position().distanceToSqr(at) < 64 * 64) Network.sendTo(near, end);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Run run = INSIDE.get(event.getEntity().getUUID());
        if (run != null && event.getEntity().getServer() != null) finish(event.getEntity().getServer(), run, false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RUNS.clear();
        INSIDE.clear();
    }

    /** While in it, only the run's own slashes hurt, and only the victim (/kill still works). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        Run run = INSIDE.get(event.getEntity().getUUID());
        if (run == null) return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!event.getEntity().getUUID().equals(run.victim) || !event.getSource().is(ModDamageTypes.CLAW_WARP)) event.setCanceled(true);
    }

    /** And only the last slash can kill: capped after everyone else has had their say on the amount. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingLast(LivingIncomingDamageEvent event) {
        Run run = INSIDE.get(event.getEntity().getUUID());
        if (run == null || event.isCanceled() || run.t >= SHATTER_AT || !event.getSource().is(ModDamageTypes.CLAW_WARP)) return;
        event.setAmount(Math.min(event.getAmount(), Math.max(0, event.getEntity().getHealth() - 1)));
    }

    // ------------------------------------------------------------------ fx

    /** To everyone in the run and to whoever stands near the victim. ints: kind, victim, step, rider, claw?, attacker. */
    private static void fx(Run run, int kind, int step, LivingEntity victim) {
        MinecraftServer server = victim.getServer();
        int rider = -1;
        if (run.rider != null && server.getPlayerList().getPlayer(run.rider) instanceof ServerPlayer r) rider = r.getId();
        Entity attacker = run.attacker == null ? null : findAttacker(server, run.attacker);
        boolean claw = attacker instanceof com.slop.projectminecraft.boss.Claw;
        ArsenalFxPayload p = ArsenalFxPayload.of(ArsenalFxPayload.CLAW, victim.position(), Vec3.ZERO, kind, victim.getId(), step, rider, claw ? 1 : 0,
                attacker == null ? -1 : attacker.getId());
        for (UUID id : run.pins.keySet()) {
            if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer p2) Network.sendTo(p2, p);
        }
        for (ServerPlayer near : ((ServerLevel) victim.level()).players()) {
            if (!run.pins.containsKey(near.getUUID()) && near.distanceToSqr(victim) < 64 * 64) Network.sendTo(near, p);
        }
    }
}
