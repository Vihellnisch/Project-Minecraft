package com.slop.projectminecraft.boss;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.registry.ModEntities;
import com.slop.projectminecraft.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Library: a dimension of its own, reached with a Library Invitation. Every guest gets their own reception: a round
 * floor of the Floor of General Works, ringed by bookshelves, with Roland waiting at the desk, and a factory floor
 * (rust, red light, a tower of pipes like a twisted piano) far off to the east, where his distortion takes the fight.
 * <p>
 * A guest who dies here keeps everything they carried and wakes at home, invitation still in hand. A guest who wins is
 * sent home after the last scene and the invitation is spent.
 */
public final class LibraryArena {
    private LibraryArena() {}

    public static final ResourceKey<Level> LIBRARY = ResourceKey.create(Registries.DIMENSION, ProjectMinecraft.id("library"));
    /** Floor top (you stand on y = FLOOR + 1), radius of the stage, how far apart guests' receptions are. */
    public static final int FLOOR = 64, RADIUS = 20, SPACING = 1024, FACTORY_OFFSET = 320;
    private static final String RETURN = "projectminecraft_library_return";

    public static boolean inLibrary(Level level) {
        return level.dimension() == LIBRARY;
    }

    /** Centre of a reception's library floor (the block you stand on). */
    public static BlockPos center(int slot) {
        return new BlockPos(slot * SPACING, FLOOR, 0);
    }

    public static BlockPos factory(int slot) {
        return center(slot).offset(FACTORY_OFFSET, 0, 0);
    }

    /** Where Roland waits: behind his desk at the north end. */
    public static BlockPos desk(int slot) {
        return center(slot).offset(0, 3, -16);
    }

    /** Where guests arrive: the south end of the floor, looking at the desk. */
    public static Vec3 arrival(int slot) {
        BlockPos c = center(slot);
        return new Vec3(c.getX() + 0.5, FLOOR + 1, c.getZ() + 16.5);
    }

    // =================================================================== guests

    /** Uses an invitation: in the Library it takes you home, anywhere else it takes you to your reception. */
    public static void use(ServerPlayer p) {
        if (inLibrary(p.level())) leave(p);
        else enter(p);
    }

    static void enter(ServerPlayer p) {
        MinecraftServer server = p.server;
        ServerLevel library = server.getLevel(LIBRARY);
        if (library == null) return;
        CompoundTag back = new CompoundTag();
        back.putString("Dim", p.level().dimension().location().toString());
        back.putDouble("X", p.getX());
        back.putDouble("Y", p.getY());
        back.putDouble("Z", p.getZ());
        back.putFloat("Yaw", p.getYRot());
        persisted(p).put(RETURN, back);
        Slots slots = Slots.get(library);
        int slot = slots.slotOf(p.getUUID());
        if (slots.built.add(slot)) {
            build(library, slot);
            slots.setDirty();
        }
        ensureRoland(library, slot);
        Vec3 at = arrival(slot);
        p.teleportTo(library, at.x, at.y, at.z, 180, 0);
        library.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 2.0f, 0.7f);
        library.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1, at.z, 80, 0.6, 1, 0.6, 0.6);
        p.displayClientMessage(Component.translatable("message.projectminecraft.library.arrive"), true);
    }

    /** Back to where the invitation was used (or world spawn). */
    public static void leave(ServerPlayer p) {
        MinecraftServer server = p.server;
        CompoundTag back = persisted(p).getCompound(RETURN);
        ServerLevel to = null;
        if (back.contains("Dim")) to = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(back.getString("Dim"))));
        if (to == null || to.dimension() == LIBRARY) {
            to = server.overworld();
            BlockPos s = to.getSharedSpawnPos();
            p.teleportTo(to, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0);
        } else {
            p.teleportTo(to, back.getDouble("X"), back.getDouble("Y"), back.getDouble("Z"), back.getFloat("Yaw"), 0);
        }
        persisted(p).remove(RETURN);
        to.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 1, 0.5, 0.6);
        to.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 2.0f, 1.2f);
    }

    /** NeoForge copies this sub-tag across death, so a return point survives dying in the Library. */
    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /**
     * Spawns Roland at the desk if this reception has none (first visit, or he was beaten before). The saved data says
     * whether he's there: entities load after their chunks, so looking for him on arrival would miss him.
     */
    static void ensureRoland(ServerLevel level, int slot) {
        Slots slots = Slots.get(level);
        if (slots.rolands.contains(slot)) return;
        Roland r = ModEntities.ROLAND.get().create(level);
        if (r == null) return;
        BlockPos d = desk(slot);
        r.moveTo(d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 0, 0);
        r.setHome(slot, d, 0);
        level.addFreshEntity(r);
        slots.rolands.add(slot);
        slots.setDirty();
    }

    /** Roland of this reception is gone for good: the next guest gets a new one. */
    static void rolandGone(ServerLevel level, int slot) {
        if (slot < 0 || !inLibrary(level)) return;
        Slots slots = Slots.get(level);
        if (slots.rolands.remove(slot)) slots.setDirty();
    }

    /** Tours: a Roland they spawned themselves counts as the reception's own. */
    public static void ensureRolandRecorded(ServerLevel level, int slot) {
        Slots slots = Slots.get(level);
        if (slots.rolands.add(slot)) slots.setDirty();
    }

    /** Whether a reception has its Roland (tours). */
    public static boolean hasRoland(ServerLevel level, int slot) {
        return Slots.get(level).rolands.contains(slot);
    }

    /** Guests standing in a reception (either floor). */
    public static List<ServerPlayer> guests(ServerLevel level, BlockPos center) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(Vec3.atBottomCenterOf(center)) < (RADIUS + 10) * (RADIUS + 10)) out.add(p);
        }
        return out;
    }

    // =================================================================== the end of a reception

    /** A guest on the way home: when, whether they won, and what Roland left them (handed over at home). */
    private record Exit(UUID player, long at, boolean won, List<ItemStack> reward) {}

    private static final List<Exit> EXITS = new ArrayList<>();
    /** What dead guests carried, handed back when they respawn. */
    private static final Map<UUID, List<ItemStack>> KEPT = new HashMap<>();

    /** Roland has fallen: everyone in the reception goes home in a few seconds, and their invitation is spent. */
    static void victory(ServerLevel level, BlockPos center) {
        for (ServerPlayer p : guests(level, center)) {
            EXITS.add(new Exit(p.getUUID(), level.getGameTime() + 140, true, new ArrayList<>()));
            p.displayClientMessage(Component.translatable("message.projectminecraft.library.won"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (EXITS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel library = server.getLevel(LIBRARY);
        if (library == null) return;
        long now = library.getGameTime();
        for (Iterator<Exit> it = EXITS.iterator(); it.hasNext(); ) {
            Exit e = it.next();
            if (now < e.at) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.player);
                // a doorway of light opens around you
                if (p != null && p.level() == library && (e.at - now) < 60 && now % 2 == 0)
                    library.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 6, 0.6, 1.0, 0.6, 0.02);
                continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.player);
            if (p == null || p.level() != library) continue;
            if (e.won) spendInvitation(p);
            leave(p);
            if (!e.reward.isEmpty()) reward(p, e.reward);
        }
    }

    static void spendInvitation(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModItems.LIBRARY_INVITATION.get())) {
                s.shrink(1);
                return;
            }
        }
    }

    /** What Roland drops isn't left on a floor you're about to be taken from: each guest who won gets it at home. */
    @SubscribeEvent
    public static void onRolandDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Roland) || !inLibrary(event.getEntity().level())) return;
        boolean given = false;
        for (Exit e : EXITS) {
            if (!e.won || !e.reward.isEmpty()) continue;
            for (ItemEntity it : event.getDrops()) e.reward.add(it.getItem().copy());
            given = true;
        }
        if (given) event.setCanceled(true);
    }

    /** The reward, laid down in front of you as you arrive home. */
    static void reward(ServerPlayer p, List<ItemStack> items) {
        ServerLevel level = p.serverLevel();
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 at = p.position().add(f.scale(2)).add(0, 0.6, 0);
        for (int i = 0; i < items.size(); i++) {
            Vec3 side = f.yRot(net.minecraft.util.Mth.HALF_PI).scale((i - (items.size() - 1) / 2.0) * 0.8);
            ItemEntity it = new ItemEntity(level, at.x + side.x, at.y, at.z + side.z, items.get(i).copy());
            it.setDeltaMovement(0, 0.15, 0);
            it.setPickUpDelay(20);
            level.addFreshEntity(it);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 40, 0.5, 0.4, 0.5, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.5, at.z, 60, 0.6, 0.6, 0.6, 0.6);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
        p.displayClientMessage(Component.translatable("message.projectminecraft.library.reward"), true);
    }

    /** A guest who loses becomes a book, not a pile of loot: nothing drops, and it all comes back at home. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !inLibrary(p.level())) return;
        List<ItemStack> kept = KEPT.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        for (ItemEntity it : event.getDrops()) kept.add(it.getItem().copy());
        event.setCanceled(true);
        // written down too, in case they log out before respawning
        ListTag list = new ListTag();
        for (ItemStack s : kept) if (!s.isEmpty()) list.add(s.save(p.registryAccess()));
        persisted(p).put("projectminecraft_library_kept", list);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isEndConquered()) return;
        List<ItemStack> kept = KEPT.remove(p.getUUID());
        CompoundTag pd = persisted(p);
        if (kept == null && pd.contains("projectminecraft_library_kept")) {
            kept = new ArrayList<>();
            ListTag list = pd.getList("projectminecraft_library_kept", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) ItemStack.parse(p.registryAccess(), list.getCompound(i)).ifPresent(kept::add);
        }
        pd.remove("projectminecraft_library_kept");
        pd.remove(RETURN);
        if (kept == null) return;
        for (ItemStack s : kept) if (!p.getInventory().add(s)) p.drop(s, false);
        p.displayClientMessage(Component.translatable("message.projectminecraft.library.lost"), true);
    }

    // =================================================================== who gets which reception

    static final class Slots extends SavedData {
        final Map<UUID, Integer> slots = new HashMap<>();
        final Set<Integer> built = new HashSet<>();
        /** Receptions whose Roland is alive (loaded or not). */
        final Set<Integer> rolands = new HashSet<>();
        int next = 1;

        static Slots get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Slots::new, Slots::load, null), "projectminecraft_library");
        }

        int slotOf(UUID id) {
            Integer s = slots.get(id);
            if (s == null) {
                s = next++;
                slots.put(id, s);
                setDirty();
            }
            return s;
        }

        static Slots load(CompoundTag tag, HolderLookup.Provider lookup) {
            Slots s = new Slots();
            s.next = Math.max(1, tag.getInt("Next"));
            CompoundTag m = tag.getCompound("Slots");
            for (String k : m.getAllKeys()) s.slots.put(UUID.fromString(k), m.getInt(k));
            for (int b : tag.getIntArray("Built")) s.built.add(b);
            for (int b : tag.getIntArray("Rolands")) s.rolands.add(b);
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
            tag.putInt("Next", next);
            CompoundTag m = new CompoundTag();
            slots.forEach((k, v) -> m.putInt(k.toString(), v));
            tag.put("Slots", m);
            tag.putIntArray("Built", built.stream().mapToInt(Integer::intValue).toArray());
            tag.putIntArray("Rolands", rolands.stream().mapToInt(Integer::intValue).toArray());
            return tag;
        }
    }

    // =================================================================== building

    static void build(ServerLevel level, int slot) {
        BlockPos c = center(slot), f = factory(slot);
        for (BlockPos at : new BlockPos[]{c, f})
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
        buildLibrary(level, c);
        buildFactory(level, f);
    }

    private static void set(ServerLevel level, BlockPos c, int dx, int y, int dz, BlockState s) {
        level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), s, 2);
    }

    private static double dist(int dx, int dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The Floor of General Works: warm parquet in a ring of bookshelves, lamps in the pillars, a desk at the north end. */
    static void buildLibrary(ServerLevel level, BlockPos c) {
        int top = FLOOR + 16;
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) {
            double d = dist(dx, dz);
            if (d > 23.5) continue;
            set(level, c, dx, FLOOR - 1, dz, Blocks.BLACKSTONE.defaultBlockState());
            set(level, c, dx, top, dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            if (d <= RADIUS) {
                // parquet in a herringbone of dark oak and spruce, an inlaid ring, and a gilded centre
                BlockState floor = ((dx + 64) / 2 + (dz + 64) / 2) % 2 == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
                if (d > 19.2) floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                else if (d > 11.5 && d <= 12.5) floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                else if (d <= 1.5) floor = Blocks.GILDED_BLACKSTONE.defaultBlockState();
                else if (d <= 2.5) floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                set(level, c, dx, FLOOR, dz, floor);
                for (int y = FLOOR + 1; y < top; y++) set(level, c, dx, y, dz, Blocks.AIR.defaultBlockState());
            } else {
                set(level, c, dx, FLOOR, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                // the walls: tall bookshelves between dark oak pillars, lamps set into the pillars
                double ang = Math.toDegrees(Math.atan2(dz, dx)) + 360;
                boolean pillar = (ang % 30) < 4.5;
                for (int y = FLOOR + 1; y < top; y++) {
                    BlockState s = pillar ? log : Blocks.BOOKSHELF.defaultBlockState();
                    if (pillar && (y == FLOOR + 3 || y == FLOOR + 6 || y == FLOOR + 11 || y == FLOOR + 14) && d < 21.6) s = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
                    if (!pillar && y == FLOOR + 8) s = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    set(level, c, dx, y, dz, s);
                }
            }
        }
        // a gallery all the way round, too high to reach, with a railing
        for (int dx = -21; dx <= 21; dx++) for (int dz = -21; dz <= 21; dz++) {
            double d = dist(dx, dz);
            if (d > 18.6 && d <= RADIUS + 0.5) {
                set(level, c, dx, FLOOR + 9, dz, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                if (d <= 19.4) set(level, c, dx, FLOOR + 10, dz, Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
        }
        // the chandelier: chains down from the ceiling, lanterns hanging in a ring
        for (int y = top - 1; y >= top - 3; y--) set(level, c, 0, y, 0, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        for (int i = 0; i < 8; i++) {
            int dx = Math.round(Mth.cos(i * Mth.TWO_PI / 8) * 3), dz = Math.round(Mth.sin(i * Mth.TWO_PI / 8) * 3);
            set(level, c, dx, top - 1, dz, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            set(level, c, dx, top - 2, dz, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            set(level, c, dx, top - 3, dz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        set(level, c, 0, top - 4, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // the desk: a dais two steps up, a long desk with books and candles, a lectern
        for (int dx = -5; dx <= 5; dx++) for (int dz = -19; dz <= -14; dz++) {
            if (dist(dx, dz) > RADIUS - 0.5) continue;
            set(level, c, dx, FLOOR + 1, dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            set(level, c, dx, FLOOR + 2, dz, dz <= -15 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        for (int dx = -5; dx <= 5; dx++) set(level, c, dx, FLOOR + 1, -13, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        for (int dx = -3; dx <= 3; dx++) {
            set(level, c, dx, FLOOR + 3, -18, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            set(level, c, dx, FLOOR + 4, -18, dx % 2 == 0 ? Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                    : Blocks.AIR.defaultBlockState());
        }
        set(level, c, -3, FLOOR + 4, -18, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        set(level, c, 3, FLOOR + 4, -18, Blocks.LECTERN.defaultBlockState());
    }

    /**
     * The factory his distortion drags the fight into: rusted floor plates, soot-black walls lit red, smoke pouring
     * from vents, and at the north end a great tower of pipes over a buckled keyboard, like a piano twisted out of shape.
     */
    static void buildFactory(ServerLevel level, BlockPos c) {
        int top = FLOOR + 22;
        for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) {
            double d = dist(dx, dz);
            if (d > 23.5) continue;
            set(level, c, dx, FLOOR - 1, dz, Blocks.BLACKSTONE.defaultBlockState());
            set(level, c, dx, FLOOR - 2, dz, Blocks.BLACKSTONE.defaultBlockState());
            set(level, c, dx, top, dz, (dx + dz) % 5 == 0 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
            if (d <= RADIUS) {
                long h = (dx * 73428767L) ^ (dz * 912931L);
                // rusted seams every six blocks, between soot-dark plates
                BlockState floor = (Math.abs(dx) % 6 == 0 || Math.abs(dz) % 6 == 0) ? Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState()
                        : (h & 7) == 0 ? Blocks.WAXED_WEATHERED_COPPER.defaultBlockState() : (h & 7) == 1 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                set(level, c, dx, FLOOR, dz, floor);
                for (int y = FLOOR + 1; y < top; y++) set(level, c, dx, y, dz, Blocks.AIR.defaultBlockState());
            } else {
                set(level, c, dx, FLOOR, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                double ang = Math.toDegrees(Math.atan2(dz, dx)) + 360;
                boolean vent = (ang % 45) < 5 && d < 21.8;
                for (int y = FLOOR + 1; y < top; y++) {
                    BlockState s = (y + dx + dz) % 7 == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                    // a band of red-lit windows, and lamps glowing behind red glass
                    if (y >= FLOOR + 6 && y <= FLOOR + 8) s = d < 21.8 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
                    if (y >= FLOOR + 6 && y <= FLOOR + 8 && d >= 21.8 && d < 22.8) s = Blocks.RED_STAINED_GLASS.defaultBlockState();
                    if (vent && y <= FLOOR + 2) s = Blocks.AIR.defaultBlockState();
                    set(level, c, dx, y, dz, s);
                }
                if (vent) {
                    // smoke vents: a smouldering fire in a niche in the wall, hay under it so the smoke climbs high
                    set(level, c, dx, FLOOR, dz, Blocks.HAY_BLOCK.defaultBlockState());
                    set(level, c, dx, FLOOR + 1, dz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
                }
            }
        }
        // red light from above
        for (int i = 0; i < 6; i++) {
            int dx = Math.round(Mth.cos(i * Mth.TWO_PI / 6) * 11), dz = Math.round(Mth.sin(i * Mth.TWO_PI / 6) * 11);
            set(level, c, dx, top - 1, dz, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            set(level, c, dx, top - 2, dz, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            set(level, c, dx, top - 3, dz, Blocks.RED_STAINED_GLASS.defaultBlockState());
        }
        // the pipe tower: copper and iron pipes of every height, leaning and twisting, over a buckled keyboard
        BlockState[] pipes = {Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState(), Blocks.WAXED_EXPOSED_COPPER.defaultBlockState(),
                Blocks.WAXED_WEATHERED_COPPER.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState()};
        for (int dx = -8; dx <= 8; dx++) {
            int height = 10 + (int) (Math.abs(Math.sin(dx * 0.9) * 8) + (8 - Math.abs(dx)) * 1.3);
            BlockState pipe = pipes[Math.floorMod(dx, pipes.length)];
            for (int y = FLOOR + 1; y <= Math.min(top - 1, FLOOR + height); y++) {
                int lean = (int) Math.round(Math.sin((y - FLOOR) * 0.22 + dx * 0.7) * 1.4);
                int dz = -18 + lean;
                set(level, c, dx, y, dz, pipe);
                if (y == FLOOR + height) set(level, c, dx, y + 1 <= top - 1 ? y + 1 : y, dz, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            }
            // the keys: white and black, some pushed in, some sticking up
            boolean black = Math.floorMod(dx, 7) == 1 || Math.floorMod(dx, 7) == 3 || Math.floorMod(dx, 7) == 5;
            BlockState key = black ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
            int lift = Math.abs(dx * 37 % 5) == 0 ? 2 : 1;
            for (int dz = -16; dz <= -14; dz++) {
                set(level, c, dx, FLOOR + 1, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                if (lift == 2 || dz > -16) set(level, c, dx, FLOOR + lift, dz, key);
            }
        }
    }

    /** Where Roland and the guests are put on each floor when the fight moves. */
    static Vec3 factoryRoland(int slot) {
        BlockPos f = factory(slot);
        return new Vec3(f.getX() + 0.5, FLOOR + 1, f.getZ() - 6.5);
    }

    static Vec3 factoryGuest(int slot, int i) {
        BlockPos f = factory(slot);
        return new Vec3(f.getX() + 0.5 + (i % 3 - 1) * 2, FLOOR + 1, f.getZ() + 10.5);
    }

    static Vec3 libraryGuest(int slot, int i) {
        BlockPos c = center(slot);
        return new Vec3(c.getX() + 0.5 + (i % 3 - 1) * 2, FLOOR + 1, c.getZ() + 9.5);
    }

    @Nullable
    static ServerLevel library(MinecraftServer server) {
        return server.getLevel(LIBRARY);
    }
}
