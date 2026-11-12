package com.slop.projectminecraft.client;

import com.slop.projectminecraft.item.Hitlist;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The hitlist as a page: the Head's note at the top, the names below (only those already out in the world can be read;
 * the rest are blacked out), and under the open name, where to look next.
 */
public class HitlistScreen extends Screen {
    private static final int W = 230, H = 250, PAPER = 0xFFE6DCC4, EDGE = 0xFF8A7A5C, INK = 0xFF2A2218, RED = 0xFF7A1010, BAR = 0xFF14100C;
    private static final String[] WINDS = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
    private final InteractionHand hand;

    HitlistScreen(InteractionHand hand) {
        super(Component.translatable("item.projectminecraft.hitlist"));
        this.hand = hand;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new HitlistScreen(hand));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!Hitlist.isHitlist(stack)) {
            onClose();
            return;
        }
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, EDGE);
        g.fill(x0, y0, x0 + W, y0 + H, PAPER);
        Font f = font;
        int y = y0 + 12;
        y = para(g, Component.translatable("item.projectminecraft.hitlist.header"), x0 + 12, y, W - 24, INK) + 8;
        g.drawString(f, Component.translatable("item.projectminecraft.hitlist.assets").withStyle(net.minecraft.ChatFormatting.UNDERLINE), x0 + 12, y, INK, false);
        y += 16;
        int stage = Hitlist.stage(stack);
        for (Hitlist.Target t : Hitlist.TARGETS) {
            if (!t.released()) {
                // blacked out: a bar the length of a name, and a shorter one under it
                g.fill(x0 + 14, y + 1, x0 + 14 + 120 + (t.id().hashCode() & 31), y + 9, BAR);
                g.fill(x0 + 20, y + 13, x0 + 20 + 70 + (t.id().hashCode() >> 5 & 31), y + 20, BAR);
                y += 30;
                continue;
            }
            Component name = Component.translatable("item.projectminecraft.hitlist.target." + t.id());
            g.drawString(f, Component.literal("— ").append(name).withStyle(stage >= Hitlist.DONE ? net.minecraft.ChatFormatting.STRIKETHROUGH : net.minecraft.ChatFormatting.BOLD), x0 + 12, y, RED, false);
            y += 11;
            y = para(g, Component.translatable("item.projectminecraft.hitlist.target." + t.id() + ".note"), x0 + 20, y, W - 32, INK) + 3;
            y = para(g, clue(stack, stage), x0 + 20, y, W - 32, RED) + 10;
        }
    }

    /** Where to look now, from the trail on this copy. */
    static Component clue(ItemStack stack, int stage) {
        String k = "item.projectminecraft.hitlist.clue.";
        return switch (stage) {
            case Hitlist.SCORCH -> {
                BlockPos s = Hitlist.stop(stack, 0);
                // close, not exact: a guess off by up to twenty blocks (well inside the distance the burns appear at), the same every read
                java.util.UUID id = Hitlist.id(stack);
                long h = id == null ? 0 : id.getLeastSignificantBits();
                int ox = (int) (h % 21), oz = (int) ((h >> 8) % 21);
                yield Component.translatable(k + "scorch", Math.round((s.getX() + ox) / 10f) * 10, Math.round((s.getZ() + oz) / 10f) * 10);
            }
            case Hitlist.BLOOD -> way(k + "blood", Hitlist.stop(stack, 0), Hitlist.stop(stack, 1));
            case Hitlist.SCENE -> way(k + "scene", Hitlist.stop(stack, 1), Hitlist.stop(stack, 2));
            case Hitlist.MET -> Component.translatable(k + "met");
            case Hitlist.DONE -> Component.translatable(k + "done");
            default -> Component.translatable(k + "unopened");
        };
    }

    static Component way(String key, BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int wind = Math.floorMod(Math.round((float) (Math.atan2(dz, dx) / (Math.PI / 4))), 8);
        int dist = Math.round((float) Math.sqrt(dx * dx + dz * dz) / 10f) * 10;
        return Component.translatable(key, Component.translatable("item.projectminecraft.hitlist.wind." + WINDS[wind]), dist);
    }

    private int para(GuiGraphics g, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, y, color, false);
            y += 10;
        }
        return y;
    }
}
