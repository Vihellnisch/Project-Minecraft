package com.slop.projectminecraft.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * The clues on a hitlist's trail, built in the world: the burns his gladius left, the blood trail, the place he waits.
 * Blocks only ever go into air or replaceable plants (never breaking anything), and every one is remembered per hitlist
 * so it can all be taken away again when the trail ends.
 */
public final class SceneBuilder extends SavedData {
    private static final String NAME = "projectminecraft_hitlist_scenes";

    /** A block we placed: where, and what it replaced (air or a plant). */
    record Placed(BlockPos pos, BlockState was, BlockState put) {}

    private final Map<UUID, List<Placed>> placed = new HashMap<>();
    /** Trails that ended, with blocks still left (in chunks that weren't loaded then): taken away when they load. */
    private final java.util.Set<UUID> finished = new java.util.HashSet<>();
    /** Hitlists whose trail is over (he's been dealt with): a copy picked up again later is finished too. */
    private final java.util.Set<UUID> completed = new java.util.HashSet<>();

    public static SceneBuilder get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SceneBuilder::new, SceneBuilder::load, null), NAME);
    }

    private static SceneBuilder load(CompoundTag tag, HolderLookup.Provider reg) {
        SceneBuilder s = new SceneBuilder();
        CompoundTag all = tag.getCompound("Scenes");
        for (String k : all.getAllKeys()) {
            List<Placed> list = new ArrayList<>();
            for (Tag t : all.getList(k, Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                var lookup = reg.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
                list.add(new Placed(BlockPos.of(c.getLong("P")), NbtUtils.readBlockState(lookup, c.getCompound("W")), NbtUtils.readBlockState(lookup, c.getCompound("N"))));
            }
            s.placed.put(UUID.fromString(k), list);
        }
        for (Tag t : tag.getList("Finished", Tag.TAG_STRING)) s.finished.add(UUID.fromString(t.getAsString()));
        for (Tag t : tag.getList("Completed", Tag.TAG_STRING)) s.completed.add(UUID.fromString(t.getAsString()));
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        CompoundTag all = new CompoundTag();
        placed.forEach((id, list) -> {
            ListTag l = new ListTag();
            for (Placed p : list) {
                CompoundTag c = new CompoundTag();
                c.putLong("P", p.pos.asLong());
                c.put("W", NbtUtils.writeBlockState(p.was));
                c.put("N", NbtUtils.writeBlockState(p.put));
                l.add(c);
            }
            all.put(id.toString(), l);
        });
        tag.put("Scenes", all);
        ListTag fin = new ListTag();
        for (UUID id : finished) fin.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("Finished", fin);
        ListTag comp = new ListTag();
        for (UUID id : completed) comp.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("Completed", comp);
        return tag;
    }

    // ------------------------------------------------------------------ placing

    /** The ground at x/z: the first solid surface (leaves and plants don't count). The chunk must be loaded. */
    public static BlockPos ground(ServerLevel level, BlockPos xz) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(xz.getX(), 0, xz.getZ()));
    }

    /** Puts `state` at `pos` if that's air or a replaceable plant with solid ground under it; remembers it. */
    boolean put(ServerLevel level, UUID id, BlockPos pos, BlockState state, boolean needsGround) {
        BlockState was = level.getBlockState(pos);
        if (!was.isAir() && !was.canBeReplaced()) return false;
        if (!was.getFluidState().isEmpty()) return false;
        if (needsGround && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        placed.computeIfAbsent(id, k -> new ArrayList<>()).add(new Placed(pos.immutable(), was, state));
        setDirty();
        return true;
    }

    /** On the ground at x/z (one block above the surface). */
    boolean putOnGround(ServerLevel level, UUID id, double x, double z, BlockState state) {
        BlockPos g = ground(level, BlockPos.containing(x, 0, z));
        return put(level, id, g, state, true);
    }

    /** The burns: three slash lines of scorched ground side by side, the way a blade of white-hot metal leaves them. */
    public void scorch(ServerLevel level, UUID id, BlockPos at, RandomSource r) {
        float yaw = r.nextFloat() * 360;
        Vec3 along = Vec3.directionFromRotation(0, yaw), across = new Vec3(-along.z, 0, along.x);
        // three long thin streaks, a block wide, side by side two apart, a little ash at their ends
        java.util.Set<BlockPos> done = new java.util.HashSet<>();
        for (int line = -1; line <= 1; line++) {
            double len = 6 + r.nextInt(4);
            for (double k = -len / 2; k <= len / 2; k += 0.4) {
                Vec3 q = Vec3.atCenterOf(at).add(along.scale(k)).add(across.scale(line * 2.2));
                BlockPos xz = BlockPos.containing(q.x, 0, q.z);
                if (!done.add(xz)) continue;
                boolean end = Math.abs(k) > len / 2 - 0.8;
                putOnGround(level, id, q.x, q.z, end ? Blocks.GRAY_CARPET.defaultBlockState() : Blocks.BLACK_CARPET.defaultBlockState());
            }
        }
    }

    /** The blood: drops and smears every few blocks, from `from` toward `to`, about `length` blocks of it. */
    public void bloodTrail(ServerLevel level, UUID id, BlockPos from, BlockPos to, int length, RandomSource r) {
        Vec3 a = Vec3.atCenterOf(from), d = Vec3.atCenterOf(to).subtract(a).multiply(1, 0, 1).normalize();
        for (int k = 0; k <= length; k += 2 + r.nextInt(2)) {
            Vec3 q = a.add(d.scale(k)).add(r.nextGaussian() * 0.6, 0, r.nextGaussian() * 0.6);
            putOnGround(level, id, q.x, q.z, Blocks.RED_CARPET.defaultBlockState());
            if (r.nextFloat() < 0.35f) putOnGround(level, id, q.x + 1, q.z, Blocks.RED_CARPET.defaultBlockState());
        }
    }

    /**
     * Where he waits: a street lamp (a dark post, a lantern hanging from its arm) and a bench beside it, facing `facing`.
     * Returns where he stands.
     */
    public Vec3 scene(ServerLevel level, UUID id, BlockPos at, Direction facing) {
        BlockPos g = ground(level, at);
        Direction side = facing.getClockWise();
        // the lamp: a three-high post, an arm out over the street, the lantern hanging off it
        BlockPos post = g.relative(side, 2);
        BlockState wall = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
        boolean postOk = put(level, id, post, wall, true);
        if (postOk) {
            put(level, id, post.above(), wall, false);
            put(level, id, post.above(2), wall, false);
            put(level, id, post.above(3), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), false);
            BlockPos arm = post.above(3).relative(facing);
            put(level, id, arm, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), false);
            put(level, id, arm.below(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), false);
        }
        // the bench: two dark oak stairs side by side, backs to the lamp's side, seats toward the street
        BlockPos bench = g.relative(side.getOpposite(), 2);
        BlockState seat = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite());
        put(level, id, bench, seat, true);
        put(level, id, bench.relative(facing.getClockWise()), seat, true);
        return Vec3.atBottomCenterOf(g);
    }

    /** Takes away everything placed for this hitlist that's still as we left it. */
    public void clear(ServerLevel level, UUID id) {
        List<Placed> list = placed.get(id);
        if (list == null) return;
        // top down, so hanging things go before what they hang from
        list.sort((p, q) -> Integer.compare(q.pos.getY(), p.pos.getY()));
        list.removeIf(p -> {
            if (!level.isLoaded(p.pos)) return false;
            // no neighbour updates: the lantern mustn't drop as its arm goes
            if (level.getBlockState(p.pos).is(p.put.getBlock())) level.setBlock(p.pos, p.was, Block.UPDATE_CLIENTS);
            return true;
        });
        if (list.isEmpty()) {
            placed.remove(id);
            finished.remove(id);
        } else finished.add(id);
        setDirty();
    }

    /** Now and then: what's left of ended trails, where it has loaded since. */
    public void sweep(ServerLevel level) {
        for (UUID id : new ArrayList<>(finished)) clear(level, id);
    }

    /** The trail of this hitlist is over: its clues come away, and any copy of it is finished. */
    public void complete(ServerLevel level, UUID id) {
        completed.add(id);
        setDirty();
        clear(level, id);
    }

    public boolean completed(UUID id) {
        return completed.contains(id);
    }

    public boolean has(UUID id) {
        return placed.containsKey(id);
    }
}
