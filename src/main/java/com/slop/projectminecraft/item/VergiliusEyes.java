package com.slop.projectminecraft.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/** His eyes, the Red Gaze: worn in their own slot (EyeSlot), not an armour slot. */
public class VergiliusEyes extends Item {
    public VergiliusEyes() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player p, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (com.slop.projectminecraft.arsenal.EyeSlot.worn(p)) return net.minecraft.world.InteractionResultHolder.fail(stack);
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.slop.projectminecraft.arsenal.EyeSlot.insert(sp, stack);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.projectminecraft.vergilius_eyes.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.projectminecraft.vergilius_eyes.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
