package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.arsenal.ClawServer;
import com.slop.projectminecraft.client.arsenal.GloveClient;
import com.slop.projectminecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Handmade Claw's wheel, laid out like the Black Silence's: hold B, point, let go. Serum K at the top, R lower right,
 * W lower left, each slot showing its vial, its cooldown draining away and a glow while it runs; the Tri-serum Cocktail
 * is the centre.
 */
public class ClawWheel extends Screen {
    static final int RING = 64, SLOT = 18, CENTER = 26, DEAD = 34;
    /** Ring slots, clockwise from the top. */
    private static final int[] RING_SERUMS = {ClawServer.K, ClawServer.R, ClawServer.W};
    private int hovered = -1;

    public ClawWheel() {
        super(Component.translatable("gui.projectminecraft.claw.wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    static int color(int serum) {
        float[] c = ClawClient.SERUM_RGB[serum];
        return 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    static Item vial(int serum) {
        return serum == ClawServer.K ? ModItems.SERUM_K.get() : serum == ClawServer.R ? ModItems.SERUM_R.get() : serum == ClawServer.W ? ModItems.SERUM_W.get() : ModItems.HANDMADE_CLAW.get();
    }

    /** Running right now (its aura bit; the cocktail lights everything). */
    static boolean live(int serum) {
        int a = ClawClient.aura;
        return (a & 8) != 0 || serum != ClawServer.TRI && (a & (1 << serum)) != 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2, cy = height / 2;
        hovered = pick(mouseX - cx, mouseY - cy);
        float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;

        for (int i = 0; i < RING_SERUMS.length; i++) {
            int serum = RING_SERUMS[i];
            double a = i * Math.PI * 2 / RING_SERUMS.length;
            int x = cx + (int) Math.round(Math.sin(a) * RING), y = cy - (int) Math.round(Math.cos(a) * RING);
            slot(g, serum, x, y, hovered == serum ? SLOT + 3 : SLOT, time);
        }
        slot(g, ClawServer.TRI, cx, cy, hovered == ClawServer.TRI ? CENTER + 3 : CENTER, time);

        // what's under the cursor, and the selected serum
        int below = cy + RING / 2 + SLOT + 12;
        if (hovered >= 0) {
            center(g, Component.translatable("gui.projectminecraft.claw.serum." + hovered + ".name").getString(), below, color(hovered == ClawServer.TRI ? ClawServer.W : hovered) | 0xFF000000);
            center(g, Component.translatable("gui.projectminecraft.claw.serum." + hovered + ".desc").getString(), below + 11, 0xFFB0B0B0);
        }
        String sel = Component.translatable("gui.projectminecraft.claw.serum." + ClawClient.selected + ".name").getString();
        center(g, Component.translatable("gui.projectminecraft.claw.selected", sel).getString(), cy - RING - SLOT - 16, 0xFFB0B0B0);
    }

    /** One serum: its vial, a frame (white if selected, its colour if ready), the cooldown draining from the top, a pulse while live. */
    private void slot(GuiGraphics g, int serum, int x, int y, int s, float time) {
        int col = color(serum == ClawServer.TRI ? ClawServer.W : serum);
        if (serum == ClawServer.TRI) {
            // the cocktail's frame turns through all three colours
            int k = (int) (time / 6) % 3;
            col = color(k == 0 ? ClawServer.K : k == 1 ? ClawServer.R : ClawServer.W);
        }
        int left = ClawClient.COOLDOWN_LEFT[serum];
        boolean ready = left <= 0, selected = ClawClient.selected == serum;
        if (live(serum)) {
            int pulse = (int) (90 + 80 * Mth.sin(time * 0.35f));
            g.fill(x - s - 4, y - s - 4, x + s + 4, y + s + 4, (pulse << 24) | (col & 0xFFFFFF));
        }
        g.fill(x - s - 1, y - s - 1, x + s + 1, y + s + 1, selected ? 0xFFFFFFFF : ready ? col : 0xFF3A3A3A);
        g.fill(x - s, y - s, x + s, y + s, hovered == serum ? 0xF0303030 : 0xE0141414);
        // the vial itself, big
        float scale = (s * 2 - 8) / 16f;
        g.pose().pushPose();
        g.pose().translate(x - 8 * scale, y - 8 * scale, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(new ItemStack(vial(serum)), 0, 0);
        g.pose().popPose();
        if (!ready) {
            // still recovering: a shade that drains away, and the seconds left
            float frac = Mth.clamp(left / (float) ClawServer.COOLDOWN[serum], 0, 1);
            int h = (int) (s * 2 * frac);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x - s, y - s, x + s, y - s + h, 0xB0000000);
            String t = (left + 19) / 20 + "s";
            g.drawString(font, t, x - font.width(t) / 2, y - 4, 0xFFE0E0E0, true);
            g.pose().popPose();
        }
    }

    private void center(GuiGraphics g, String s, int y, int color) {
        g.drawString(font, s, width / 2 - font.width(s) / 2, y, color, true);
    }

    static int pick(double dx, double dy) {
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d <= CENTER) return ClawServer.TRI;
        if (d < DEAD) return -1;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        double step = Math.PI * 2 / RING_SERUMS.length;
        return RING_SERUMS[(int) Math.floor((a + step / 2) / step) % RING_SERUMS.length];
    }

    private void choose() {
        if (hovered >= 0) ClawClient.select(hovered);
        onClose();
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (GloveClient.WHEEL_KEY.matches(key, scan)) {
            choose();
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (GloveClient.WHEEL_KEY.matchesMouse(button)) {
            choose();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            hovered = pick(x - width / 2.0, y - height / 2.0);
            choose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
}
