package com.slop.projectminecraft.item;

import com.slop.projectminecraft.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Perception-Blocking Mask the Black Silence wore: a smooth black oval over the face. Worn, hostile creatures don't
 * notice you at all, not even an enderman meeting your eyes. Strike one and that one sees you, and comes for you.
 */
public class BlackSilenceMask extends Item implements Equipable {
    /** On a mob: the players it has been struck by while they wore the mask (it can see them now). */
    static final String PROVOKED = "projectminecraft_mask_provoked";

    public BlackSilenceMask() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, p, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.projectminecraft.black_silence_mask.desc").withStyle(ChatFormatting.GRAY));
    }

    public static boolean masked(Entity e) {
        return e instanceof Player p && p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BLACK_SILENCE_MASK.get());
    }

    static boolean provoked(Entity mob, Player p) {
        return mob.getPersistentData().getCompound(PROVOKED).contains(p.getStringUUID());
    }

    /** Hostile things can't pick a masked player as their target, unless that player struck them. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity t = event.getNewAboutToBeSetTarget();
        if (t instanceof Player p && masked(p) && !provoked(event.getEntity(), p)) event.setCanceled(true);
    }

    /** Meeting an enderman's eyes through the mask does nothing. */
    @SubscribeEvent
    public static void onEndermanStare(EnderManAngerEvent event) {
        if (masked(event.getPlayer()) && !provoked(event.getEntity(), event.getPlayer())) event.setCanceled(true);
    }

    /** Strike something while masked and it sees you from then on. */
    @SubscribeEvent
    public static void onHit(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player p) || !masked(p) || event.getEntity() == p) return;
        CompoundTag root = event.getEntity().getPersistentData();
        CompoundTag tag = root.getCompound(PROVOKED);
        tag.putBoolean(p.getStringUUID(), true);
        root.put(PROVOKED, tag);
    }

    /** Putting it on mid-fight: whatever was already after you (and hasn't been struck) loses you. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || p.tickCount % 10 != 0 || !masked(p)) return;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(48), m -> m.getTarget() == p)) {
            if (!provoked(m, p)) m.setTarget(null);
        }
    }
}
