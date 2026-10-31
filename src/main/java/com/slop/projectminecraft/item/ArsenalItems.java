package com.slop.projectminecraft.item;

import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.apocalypse.ApocalypseServer;
import com.slop.projectminecraft.arsenal.ArsenalEvents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The Black Silence and the E.G.O. Most of the logic lives in com.slop.projectminecraft.arsenal; these are the triggers. */
public final class ArsenalItems {
    private ArsenalItems() {}

    static Item.Properties props(Rarity r) {
        return new Item.Properties().stacksTo(1).rarity(r).fireResistant();
    }

    static int cd(int seconds) {
        return (int) Math.round(seconds * 20 * Config.COOLDOWN.get());
    }

    static void tooltip(String key, List<Component> tooltip, int cooldown) {
        tooltip.add(Component.translatable(key + ".flavor").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(key + ".ability").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(key + ".ability2").withStyle(ChatFormatting.YELLOW));
        if (cooldown > 0) tooltip.add(Component.translatable("tooltip.projectminecraft.cooldown", Math.round(cooldown * Config.COOLDOWN.get())).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Base class with a tooltip and a cooldown shown in it. */
    abstract static class Tool extends Item {
        final int cooldown;

        Tool(Rarity r, int cooldown) {
            super(props(r));
            this.cooldown = cooldown;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip(getDescriptionId(), tooltip, cooldown);
        }
    }

    // =================================================================== Black Silence (worn: the weapon wheel and Furioso live in GloveServer)

    public static class BlackSilence extends Tool {
        public BlackSilence() {
            super(Rarity.EPIC, 0);
        }
    }

    /** What the player is looking at, with generous aim assist (a cone), within range. */
    public static LivingEntity aimTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        EntityHitResult direct = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(range)),
                new AABB(eye, eye.add(look.scale(range))).inflate(1.5), e -> e instanceof LivingEntity le && ArsenalEvents.validTarget(le)
                        && com.slop.projectminecraft.apocalypse.Destruction.canHit(p, le), range * range);
        if (direct != null && direct.getEntity() instanceof LivingEntity le) return le;
        LivingEntity best = null;
        double bestDot = 0.9;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                e -> ArsenalEvents.validTarget(e) && com.slop.projectminecraft.apocalypse.Destruction.canHit(p, e))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            if (d.length() > range) continue;
            double dot = d.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    // =================================================================== E.G.O

    public static class Ego extends Tool {
        final int ego;

        public Ego(int ego, int cooldown) {
            super(Rarity.EPIC, cooldown);
            this.ego = ego;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                ApocalypseServer.start(new ArsenalEvents.Ego(server, sp, ego));
                sp.getCooldowns().addCooldown(this, cd(cooldown));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }
}
