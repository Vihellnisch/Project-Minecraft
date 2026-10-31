package com.slop.projectminecraft.item;

import com.slop.projectminecraft.boss.LibraryArena;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * An invitation to the Library. Hold it up for two seconds and the pages take you to your reception, where the Black
 * Silence is waiting; in the Library, the same takes you home. It's spent only when the reception is won.
 */
public class LibraryInvitation extends Item {
    static final int USE_TICKS = 40;

    public LibraryInvitation() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        if (!level.isClientSide) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.5f, 0.6f);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity e) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack stack, int left) {
        if (!(level instanceof ServerLevel sl)) return;
        // pages swirl in around you
        int k = USE_TICKS - left;
        sl.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY() + 1.2, e.getZ(), 4 + k / 4, 0.8, 0.9, 0.8, 0.5 + k * 0.02);
        if (k % 10 == 0) sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f + k * 0.01f);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        if (e instanceof ServerPlayer p) {
            p.getCooldowns().addCooldown(this, 60);
            LibraryArena.use(p);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.projectminecraft.library_invitation.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.projectminecraft.library_invitation.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
