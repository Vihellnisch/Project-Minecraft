package com.slop.projectminecraft.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/** Vergilius's white-hot gladius: it sets what it cuts on fire; its skills are on the B wheel (GladiusServer). */
public class WhiteHotGladius extends SwordItem {
    public WhiteHotGladius() {
        super(Tiers.NETHERITE, new Properties().rarity(Rarity.EPIC).fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.2f)));
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player p, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResultHolder.pass(stack);
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.slop.projectminecraft.arsenal.GladiusServer.use(sp);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.igniteForSeconds(4);
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.projectminecraft.white_hot_gladius.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.projectminecraft.white_hot_gladius.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
