package com.slop.projectminecraft.quest;

import com.slop.projectminecraft.boss.Vergilius;
import com.slop.projectminecraft.item.Hitlist;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * A hitlist's trail, in the Overworld: where he was last seen (the burns), which way he went (the blood), where he waits.
 * Each stop is built when its holder comes near enough for its chunk to be loaded, and found (the page updates) at about
 * a dozen blocks. Each hitlist has a trail of its own.
 */
public final class HitlistTrail {
    private HitlistTrail() {}

    /** Built at, found at (blocks, flat distance). */
    static final double BUILD = 64, FOUND = 11, SCENE_BUILD = 64;
    /** Gaps between the stops. */
    static final int TO_BLOOD = 150, TO_SCENE = 80, BLOOD_LENGTH = 22;

    /** First opened: the trail is laid out from here (or from spawn, outside the Overworld). False if no land was found. */
    public static boolean begin(ServerPlayer p, ItemStack stack, int distance) {
        ServerLevel over = p.server.overworld();
        Vec3 origin = p.level().dimension() == Level.OVERWORLD ? p.position() : Vec3.atCenterOf(over.getSharedSpawnPos());
        var r = p.getRandom();
        for (int attempt = 0; attempt < 32; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            double d = distance * (0.85 + r.nextDouble() * 0.3);
            BlockPos s0 = BlockPos.containing(origin.x + Mth.cos(a) * d, 0, origin.z + Mth.sin(a) * d);
            float a1 = a + (r.nextFloat() - 0.5f) * 2.4f;
            BlockPos s1 = s0.offset(Math.round(Mth.cos(a1) * TO_BLOOD), 0, Math.round(Mth.sin(a1) * TO_BLOOD));
            float a2 = a1 + (r.nextBoolean() ? 1 : -1) * (0.9f + r.nextFloat() * 0.9f);
            BlockPos s2 = s1.offset(Math.round(Mth.cos(a2) * TO_SCENE), 0, Math.round(Mth.sin(a2) * TO_SCENE));
            if (!land(over, s0) || !land(over, s1) || !land(over, s2)) continue;
            Hitlist.edit(stack, t -> {
                t.putUUID("Id", UUID.randomUUID());
                t.putString("Target", "vergilius");
                t.putInt("Stage", Hitlist.SCORCH);
                t.putLongArray("Stops", new long[]{s0.asLong(), s1.asLong(), s2.asLong()});
            });
            return true;
        }
        return false;
    }

    /** Dry ground, going by the biome there (no chunk is loaded or generated to check). */
    static boolean land(ServerLevel level, BlockPos xz) {
        Holder<Biome> b = level.getUncachedNoiseBiome(xz.getX() >> 2, 63 >> 2, xz.getZ() >> 2);
        return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_DEEP_OCEAN) && !b.is(BiomeTags.IS_RIVER) && !b.is(BiomeTags.IS_BEACH);
    }

    static double flat(Vec3 a, BlockPos b) {
        double dx = a.x - (b.getX() + 0.5), dz = a.z - (b.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0 || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        if (p.tickCount % 400 == 0) SceneBuilder.get(level).sweep(level);
        if (level.dimension() != Level.OVERWORLD) return;
        for (ItemStack s : p.getInventory().items) if (Hitlist.isHitlist(s)) tick(p, level, s);
        for (ItemStack s : p.getInventory().offhand) if (Hitlist.isHitlist(s)) tick(p, level, s);
    }

    static void tick(ServerPlayer p, ServerLevel level, ItemStack stack) {
        int stage = Hitlist.stage(stack);
        UUID id = Hitlist.id(stack);
        if (id == null || stage < Hitlist.SCORCH || stage > Hitlist.MET || Hitlist.stop(stack, 2) == null) return;
        SceneBuilder scenes = SceneBuilder.get(level);
        // a copy of a list whose trail already ended (dropped mid-fight and picked up again): finished, no second Vergilius
        if (scenes.completed(id)) {
            Hitlist.setStage(stack, Hitlist.DONE);
            return;
        }
        var t = Hitlist.data(stack);
        switch (stage) {
            case Hitlist.SCORCH -> {
                BlockPos s0 = Hitlist.stop(stack, 0);
                double d = flat(p.position(), s0);
                if (d < BUILD && level.isLoaded(s0) && !t.getBoolean("Built0")) {
                    scenes.scorch(level, id, s0, p.getRandom());
                    leftovers(level, s0, p);
                    Hitlist.edit(stack, x -> x.putBoolean("Built0", true));
                }
                if (d < 48 && t.getBoolean("Built0")) embers(level, s0, p);
                if (d < FOUND && t.getBoolean("Built0")) advance(p, stack, Hitlist.BLOOD);
            }
            case Hitlist.BLOOD -> {
                BlockPos s1 = Hitlist.stop(stack, 1), s2 = Hitlist.stop(stack, 2);
                double d = flat(p.position(), s1);
                if (d < BUILD && level.isLoaded(s1) && !t.getBoolean("Built1")) {
                    scenes.bloodTrail(level, id, s1, s2, BLOOD_LENGTH, p.getRandom());
                    Hitlist.edit(stack, x -> x.putBoolean("Built1", true));
                }
                if (d < 32 && t.getBoolean("Built1")) drips(level, s1, s2);
                if (d < FOUND && t.getBoolean("Built1")) advance(p, stack, Hitlist.SCENE);
            }
            case Hitlist.SCENE, Hitlist.MET -> {
                BlockPos s2 = Hitlist.stop(stack, 2);
                double d = flat(p.position(), s2);
                if (d > SCENE_BUILD || !level.isLoaded(s2)) return;
                Vec3 at;
                if (!t.getBoolean("Built2")) {
                    // he waits facing back the way you came
                    Direction facing = Direction.getNearest(p.getX() - s2.getX(), 0, p.getZ() - s2.getZ());
                    at = scenes.scene(level, id, s2, facing);
                    Vec3 put = at;
                    Hitlist.edit(stack, x -> {
                        x.putBoolean("Built2", true);
                        x.putDouble("SX", put.x);
                        x.putDouble("SY", put.y);
                        x.putDouble("SZ", put.z);
                        x.putFloat("SYaw", facing.toYRot());
                    });
                    t = Hitlist.data(stack);
                }
                at = new Vec3(t.getDouble("SX"), t.getDouble("SY"), t.getDouble("SZ"));
                // he's there while you're near (and back there if he left: you can meet him again)
                Vergilius v = t.hasUUID("Verg") && level.getEntity(t.getUUID("Verg")) instanceof Vergilius found && found.isAlive() ? found : null;
                // after he's driven off (a lost rematch) he's back a while later, not at once
                if (v == null && level.getGameTime() < t.getLong("Back")) return;
                // not before the entities there have loaded (he may be there already, just not registered yet)
                if (v == null && !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(s2))) return;
                if (v == null) {
                    v = Vergilius.atScene(level, at, t.getFloat("SYaw"), id, p.getUUID());
                    if (v != null) {
                        UUID vid = v.getUUID();
                        Hitlist.edit(stack, x -> {
                            x.putUUID("Verg", vid);
                            x.putInt("Stage", Hitlist.SCENE);
                        });
                    }
                }
            }
            default -> {}
        }
    }

    /** A clue found: the page turns to the next one. */
    static void advance(ServerPlayer p, ItemStack stack, int stage) {
        Hitlist.setStage(stack, stage);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.displayClientMessage(Component.translatable("message.projectminecraft.hitlist.found." + stage).withStyle(ChatFormatting.DARK_RED), false);
    }

    /** What's left of whoever got in his way: a few scraps on the ground (they go the way items go). */
    static void leftovers(ServerLevel level, BlockPos at, ServerPlayer p) {
        BlockPos g = SceneBuilder.ground(level, at.offset(3, 0, 2));
        for (ItemStack s : new ItemStack[]{new ItemStack(Items.ROTTEN_FLESH, 2), new ItemStack(Items.BONE), new ItemStack(Items.IRON_NUGGET, 3)}) {
            ItemEntity it = new ItemEntity(level, g.getX() + 0.5 + p.getRandom().nextGaussian() * 0.6, g.getY() + 0.2, g.getZ() + 0.5 + p.getRandom().nextGaussian() * 0.6, s);
            it.setDeltaMovement(0, 0.05, 0);
            level.addFreshEntity(it);
        }
    }

    /** The burns still smoulder: smoke and the odd ember over them. */
    static void embers(ServerLevel level, BlockPos at, ServerPlayer p) {
        BlockPos g = SceneBuilder.ground(level, at);
        level.sendParticles(p, ParticleTypes.SMOKE, false, g.getX() + 0.5, g.getY() + 0.2, g.getZ() + 0.5, 6, 2.0, 0.1, 2.0, 0.01);
        level.sendParticles(p, ParticleTypes.SMALL_FLAME, false, g.getX() + 0.5, g.getY() + 0.1, g.getZ() + 0.5, 2, 2.0, 0.05, 2.0, 0.005);
    }

    /** The blood is fresh: a few drips along the trail. */
    static void drips(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from), d = Vec3.atCenterOf(to).subtract(a).multiply(1, 0, 1).normalize();
        var r = level.getRandom();
        Vec3 q = a.add(d.scale(r.nextDouble() * BLOOD_LENGTH));
        BlockPos g = SceneBuilder.ground(level, BlockPos.containing(q));
        level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.05f, 0.05f), 1.2f), q.x, g.getY() + 0.15, q.z, 4, 0.3, 0.02, 0.3, 0);
    }

    /** The trail ends (he's gone, one way or another): the clues come away. */
    public static void finish(ServerLevel level, UUID hitlist) {
        SceneBuilder.get(level).complete(level, hitlist);
    }
}
