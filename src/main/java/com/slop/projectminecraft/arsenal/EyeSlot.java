package com.slop.projectminecraft.arsenal;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.Network;
import com.slop.projectminecraft.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Vergilius's eyes, worn: a slot of their own on the player (not an armour slot). Put them in by using them, or by
 * clicking the eye slot in the inventory with them; take them out by clicking it with nothing in hand. They drop on death
 * like anything else (kept with keepInventory). Other players see them glow, so who wears them is synced.
 */
public final class EyeSlot {
    private EyeSlot() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectMinecraft.MODID);
    public static final Supplier<AttachmentType<ItemStack>> EYES = ATTACHMENTS.register("vergilius_eyes",
            () -> AttachmentType.builder(() -> ItemStack.EMPTY).serialize(ItemStack.OPTIONAL_CODEC).build());
    /**
     * How the worn eyes look on this player's face: which of the face's 8x8 pixels glow (bit row * 8 + column, columns
     * from the texture's left), and whether they glow at all. Skins differ, so players set it themselves (EyeEditor).
     */
    public static final Supplier<AttachmentType<Long>> MASK = ATTACHMENTS.register("eye_mask",
            () -> AttachmentType.builder(() -> EyeSlot.DEFAULT_MASK).serialize(com.mojang.serialization.Codec.LONG).copyOnDeath().build());
    public static final Supplier<AttachmentType<Boolean>> GLOW = ATTACHMENTS.register("eye_glow",
            () -> AttachmentType.builder(() -> true).serialize(com.mojang.serialization.Codec.BOOL).copyOnDeath().build());
    /** The usual eyes: row 4, columns 2 and 5. */
    public static final long DEFAULT_MASK = (1L << (4 * 8 + 2)) | (1L << (4 * 8 + 5));
    /** ArsenalFxPayload.VERGILIUS sub-kind: [FX_EYES, player, 1 worn / 0 not, 1 glow / 0 off, mask low 32 bits, mask high 32 bits]. */
    public static final int FX_EYES = 45;

    public static ItemStack get(Player p) {
        return p.getData(EYES);
    }

    public static boolean worn(Player p) {
        return !get(p).isEmpty();
    }

    /** Using the eyes: into the slot, if it's empty. */
    public static boolean insert(ServerPlayer p, ItemStack stack) {
        if (worn(p) || !stack.is(ModItems.VERGILIUS_EYES.get())) return false;
        p.setData(EYES, stack.split(1));
        p.level().playSound(null, p.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.2f, 0.6f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
        p.displayClientMessage(Component.translatable("message.projectminecraft.eyes.in").withStyle(ChatFormatting.DARK_RED), true);
        sync(p);
        return true;
    }

    /** The inventory's eye slot was clicked: swap what's held on the cursor with what's in the slot. */
    public static void click(ServerPlayer p) {
        var menu = p.containerMenu;
        ItemStack carried = menu.getCarried(), in = get(p);
        if (carried.isEmpty() && !in.isEmpty()) {
            menu.setCarried(in);
            p.setData(EYES, ItemStack.EMPTY);
            p.level().playSound(null, p.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0f, 0.8f);
        } else if (in.isEmpty() && carried.is(ModItems.VERGILIUS_EYES.get())) {
            insert(p, carried);
            menu.setCarried(carried);
        } else return;
        menu.broadcastChanges();
        sync(p);
    }

    static void sync(ServerPlayer p) {
        Network.sendTracking(p, state(p));
    }

    static ArsenalFxPayload state(ServerPlayer p) {
        long m = p.getData(MASK);
        return ArsenalFxPayload.of(ArsenalFxPayload.VERGILIUS, FX_EYES, p.getId(), worn(p) ? 1 : 0, p.getData(GLOW) ? 1 : 0, (int) m, (int) (m >>> 32));
    }

    /** From the inventory's toggle and the eye editor: [glow, mask low, mask high]. */
    public static void look(ServerPlayer p, int[] ints) {
        if (ints.length < 3) return;
        p.setData(GLOW, ints[0] == 1);
        p.setData(MASK, (ints[1] & 0xFFFFFFFFL) | ((long) ints[2] << 32));
        sync(p);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !worn(p)) return;
        if (p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        event.getDrops().add(new ItemEntity(p.level(), p.getX(), p.getY() + 1, p.getZ(), get(p).copy()));
        p.setData(EYES, ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player was = event.getOriginal();
        if (!event.isWasDeath() || was.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) event.getEntity().setData(EYES, was.getData(EYES).copy());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer other && event.getEntity() instanceof ServerPlayer viewer)
            Network.sendTo(viewer, state(other));
    }
}
