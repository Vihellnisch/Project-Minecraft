package com.slop.projectminecraft.apocalypse;

import com.slop.projectminecraft.registry.ModDamageTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Terrain damage and area hurts for the cinematic moves. Block removals go through one queue drained with a per-tick
 * budget. Blocks with block entities (chests, furnaces...) and unbreakable blocks are never touched.
 */
public final class Destruction {
    private Destruction() {}

    private record Job(ServerLevel level, BlockPos pos) {}

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int BLOCKS_PER_TICK = 400;

    public static boolean allowed(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    public static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness < 60;
    }

    /** Queue a block for removal. */
    public static void remove(ServerLevel level, BlockPos pos) {
        if (allowed(level)) QUEUE.add(new Job(level, pos.immutable()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int budget = BLOCKS_PER_TICK;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            Job j = QUEUE.poll();
            if (j.level.isLoaded(j.pos) && breakable(j.level, j.pos, j.level.getBlockState(j.pos))) j.level.setBlock(j.pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }

    private static final Map<Integer, int[][]> SPHERES = new HashMap<>();

    /** Offsets of every block within radius r, sorted from the centre outward (cached). */
    public static int[][] sphere(int r) {
        return SPHERES.computeIfAbsent(r, k -> {
            List<int[]> out = new ArrayList<>();
            for (int x = -k; x <= k; x++) for (int y = -k; y <= k; y++) for (int z = -k; z <= k; z++) {
                if (x * x + y * y + z * z <= k * k + k) out.add(new int[]{x, y, z, x * x + y * y + z * z});
            }
            out.sort(Comparator.comparingInt(a -> a[3]));
            return out.toArray(new int[0][]);
        });
    }

    /**
     * Hurt everything hostile to owner within radius (falloff to minFrac at the edge) and throw it outward.
     * Owner-less (null) hurts every living thing except players in creative/spectator.
     */
    public static void hurtArea(ServerLevel level, @Nullable LivingEntity owner, Vec3 c, double radius, float damage, double minFrac, double knock, int fireSeconds) {
        DamageSource src = ModDamageTypes.source(level, ModDamageTypes.ULTIMATE, owner, owner);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius))) {
            if (e == owner || !e.isAlive()) continue;
            if (!canHit(owner, e)) continue;
            Vec3 d = e.getBoundingBox().getCenter().subtract(c);
            double dist = d.length();
            if (dist > radius) continue;
            double f = 1 - (1 - minFrac) * dist / radius;
            e.invulnerableTime = 0;
            e.hurt(src, (float) (damage * f));
            if (fireSeconds > 0) e.igniteForSeconds(fireSeconds);
            if (knock > 0) {
                Vec3 push = (dist < 0.1 ? new Vec3(0, 1, 0) : d.normalize()).scale(knock * f).add(0, 0.4 * knock * f, 0);
                e.setDeltaMovement(e.getDeltaMovement().add(push));
                e.hasImpulse = true;
                if (e instanceof ServerPlayer) e.hurtMarked = true;
            }
        }
    }

    /** Explosion visuals (vanilla particles). */
    public static void explosionFx(ServerLevel level, Vec3 c, float size) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, (int) (size * 20), size * 0.4, size * 0.4, size * 0.4, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, (int) (size * 10), size * 0.5, size * 0.3, size * 0.5, 0.05);
    }

    /**
     * Can a cinematic move hurt this? Everything living is fair game, except the caster, their own pets and team,
     * creative/spectator players, and other players when PvP is off.
     */
    public static boolean canHit(@Nullable LivingEntity owner, LivingEntity e) {
        if (!e.isAlive() || e == owner || e instanceof ArmorStand || spared(owner, e)) return false;
        if (owner == null) return true;
        if (e.isAlliedTo(owner) || (e instanceof TamableAnimal pet && pet.isOwnedBy(owner))) return false;
        return !(e instanceof Player) || e.getServer() == null || e.getServer().isPvpAllowed();
    }

    /** Is this entity something a cinematic should leave alone (the caster, creative players...)? */
    public static boolean spared(@Nullable Entity owner, Entity e) {
        return e == owner || (e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }
}
