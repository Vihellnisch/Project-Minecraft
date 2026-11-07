package com.slop.projectminecraft.item;

import com.slop.projectminecraft.arsenal.ClawServer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** What the Claw leaves behind, what you make of it, and his mask. */
public final class ClawItems {
    private ClawItems() {}

    /** A broken piece or a mended part: a description line and nothing else. */
    public static class Part extends Item {
        final String key;

        public Part(String key, Rarity rarity) {
            super(new Item.Properties().stacksTo(1).rarity(rarity).fireResistant());
            this.key = key;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.projectminecraft." + key + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /** The Claw's mask: a trophy, worn on the head. */
    public static class Mask extends Part implements Equipable {
        public Mask() {
            super("claw_mask", Rarity.EPIC);
        }

        @Override
        public EquipmentSlot getEquipmentSlot() {
            return EquipmentSlot.HEAD;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            return swapWithEquipmentSlot(this, level, p, hand);
        }
    }

    /** The Handmade Claw: a netherite-grade claw with the three serums in it (ClawServer does the rest). */
    public static class HandmadeClaw extends SwordItem {
        public HandmadeClaw() {
            super(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4, -2.2f)));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack stack = p.getItemInHand(hand);
            if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
            if (p instanceof ServerPlayer sp) ClawServer.use(sp);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            for (String l : new String[]{"desc", "k", "r", "w", "tri", "keys"}) {
                lines.add(Component.translatable("item.projectminecraft.handmade_claw." + l).withStyle(l.equals("desc") ? ChatFormatting.GRAY : l.equals("keys") ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD));
            }
        }
    }
}
