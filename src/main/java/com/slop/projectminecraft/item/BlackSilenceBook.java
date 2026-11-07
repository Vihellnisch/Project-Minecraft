package com.slop.projectminecraft.item;

import com.slop.projectminecraft.boss.Claw;
import com.slop.projectminecraft.boss.ClawMoves;
import com.slop.projectminecraft.boss.LibraryArena;
import com.slop.projectminecraft.registry.ModEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Black Silence's book, left behind when the reception ends. Open it, and the Head notices who holds it: a Claw
 * comes for it, right where you stand.
 */
public class BlackSilenceBook extends Item {
    public BlackSilenceBook() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (p.isCreative() || p.isSpectator() || LibraryArena.inLibrary(level)) {
            p.displayClientMessage(Component.translatable("message.projectminecraft.claw.not_here").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (ClawMoves.ACTIVE.containsKey(p.getUUID()) || ClawMoves.sought(stack)) {
            p.displayClientMessage(Component.translatable("message.projectminecraft.claw.already").withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResultHolder.fail(stack);
        }
        Claw c = ModEntities.CLAW.get().create(server);
        if (c == null) return InteractionResultHolder.fail(stack);
        // a few steps in front of you, on the ground
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 at = p.position().add(f.scale(3.5));
        c.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0);
        for (int dy = 0; dy < 4 && !server.noCollision(c); dy++) c.moveTo(at.x, p.getY() + dy + 1, at.z, p.getYRot() + 180, 0);
        if (!server.noCollision(c)) c.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, 0);
        // down onto the ground (opened mid-jump, or over a drop): he stands still while he comes through
        for (int dy = 0; dy < 6 && server.noCollision(c, c.getBoundingBox().move(0, -0.5, 0)); dy++) c.moveTo(c.getX(), c.getY() - 0.5, c.getZ(), c.getYRot(), 0);
        // arriving before he's added: adding him pairs him with everyone near at once, and they must see him arriving
        c.arrive(sp);
        server.addFreshEntity(c);
        ClawMoves.setSought(stack, true);
        p.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ClawMoves.sought(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.projectminecraft.black_silence_book.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(ClawMoves.sought(stack) ? "item.projectminecraft.black_silence_book.sought" : "item.projectminecraft.black_silence_book.use")
                .withStyle(ClawMoves.sought(stack) ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY));
    }

    /** Sought, but nobody's coming for it (he left, the server restarted): just the book again. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (!level.isClientSide && e.tickCount % 40 == 0 && ClawMoves.sought(stack) && !ClawMoves.ACTIVE.containsKey(e.getUUID())) ClawMoves.setSought(stack, false);
    }
}
