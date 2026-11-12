package com.slop.projectminecraft.item;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.quest.HitlistTrail;
import com.slop.projectminecraft.registry.ModItems;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * The Head's hitlist, dropped by the Claw as he retreats: a page of names, most of them blacked out. Each copy keeps its
 * own trail (HitlistTrail): opened the first time, it sets where the target was last seen, and each clue found on the way
 * updates the page with where to look next.
 */
public class Hitlist extends Item {
    /** The names on the list, in order. Unreleased names are drawn redacted: a future character is one more entry here. */
    public record Target(String id, boolean released) {}

    public static final List<Target> TARGETS = List.of(new Target("vergilius", true), new Target("redacted_1", false), new Target("redacted_2", false));

    /** Where the trail stands: not opened yet, looking for the burns, the blood, him; met him; finished. */
    public static final int UNOPENED = 0, SCORCH = 1, BLOOD = 2, SCENE = 3, MET = 4, DONE = 5;
    static final String TAG = "Hitlist";

    public Hitlist() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    // ------------------------------------------------------------------ state (CUSTOM_DATA "Hitlist")

    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG);
    }

    public static void edit(ItemStack stack, java.util.function.Consumer<CompoundTag> f) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag t = root.getCompound(TAG);
            f.accept(t);
            root.put(TAG, t);
        });
    }

    public static int stage(ItemStack stack) {
        return data(stack).getInt("Stage");
    }

    public static void setStage(ItemStack stack, int stage) {
        edit(stack, t -> t.putInt("Stage", stage));
    }

    @Nullable
    public static UUID id(ItemStack stack) {
        CompoundTag t = data(stack);
        return t.hasUUID("Id") ? t.getUUID("Id") : null;
    }

    /** Stop `i` (0 = the burns, 1 = the blood, 2 = the scene), x and z; y is found when it's built. */
    @Nullable
    public static BlockPos stop(ItemStack stack, int i) {
        long[] s = data(stack).getLongArray("Stops");
        return i < s.length ? BlockPos.of(s[i]) : null;
    }

    public static boolean isHitlist(ItemStack stack) {
        return stack.is(ModItems.HITLIST.get());
    }

    /** Still on the trail (or not opened yet). */
    public static boolean open(ItemStack stack) {
        return isHitlist(stack) && stage(stack) < DONE;
    }

    /** The player already carries a hitlist they haven't finished. */
    public static boolean hasOpen(Player p) {
        for (ItemStack s : p.getInventory().items) if (open(s)) return true;
        for (ItemStack s : p.getInventory().offhand) if (open(s)) return true;
        return open(p.containerMenu.getCarried());
    }

    /** The hitlist (in `p`'s inventory) whose id is `id`. */
    public static ItemStack find(Player p, @Nullable UUID id) {
        if (id == null) return ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (isHitlist(s) && id.equals(id(s))) return s;
        for (ItemStack s : p.getInventory().offhand) if (isHitlist(s) && id.equals(id(s))) return s;
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (level instanceof ServerLevel server && p instanceof ServerPlayer sp && stage(stack) == UNOPENED) {
            if (!HitlistTrail.begin(sp, stack, Config.TRAIL_DISTANCE.get())) {
                p.displayClientMessage(Component.translatable("message.projectminecraft.hitlist.no_trail").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            server.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.8f);
        }
        if (level.isClientSide) com.slop.projectminecraft.client.HitlistScreen.open(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        int s = stage(stack);
        return s > UNOPENED && s < DONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.projectminecraft.hitlist.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.projectminecraft.hitlist.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
