package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.client.arsenal.GloveClient;
import com.slop.projectminecraft.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The gladius's wheel: hold B, point, let go (or click). Its four skills round the ring, and with his eyes worn the Red
 * Gaze and the E.G.O as well; in E.G.O, the four E.G.O skills. Each slot drains while it recovers.
 */
public class GladiusWheel extends Screen {
    static final int RING = 66, SLOT = 18, DEAD = 30;
    private int hovered = -1;

    public GladiusWheel() {
        super(Component.translatable("gui.projectminecraft.gladius.wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2, cy = height / 2, n = GladiusClient.count();
        hovered = pick(mouseX - cx, mouseY - cy, n);
        int accent = GladiusClient.ego() ? 0xFFD02828 : 0xFFFF8A3A;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = cx + (int) Math.round(Math.sin(a) * RING), y = cy - (int) Math.round(Math.cos(a) * RING);
            int s = hovered == i ? SLOT + 3 : SLOT, left = GladiusClient.left(i);
            boolean sel = GladiusClient.selected == i;
            g.fill(x - s - 1, y - s - 1, x + s + 1, y + s + 1, sel ? 0xFFFFFFFF : left <= 0 ? accent : 0xFF3A3A3A);
            g.fill(x - s, y - s, x + s, y + s, hovered == i ? 0xF0303030 : 0xE0141414);
            boolean eyeSkill = !GladiusClient.ego() && i >= 4;
            ItemStack icon = new ItemStack(eyeSkill ? ModItems.VERGILIUS_EYES.get() : ModItems.WHITE_HOT_GLADIUS.get());
            float scale = (s * 2 - 10) / 16f;
            g.pose().pushPose();
            g.pose().translate(x - 8 * scale, y - 8 * scale, 0);
            g.pose().scale(scale, scale, 1);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            String num = String.valueOf(i + 1);
            g.drawString(font, num, x - s + 2, y - s + 2, 0xFFE0E0E0, true);
            if (left > 0) {
                float frac = Mth.clamp(left / (float) GladiusClient.max(i), 0, 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x - s, y - s, x + s, y - s + (int) (s * 2 * frac), 0xB0000000);
                String t = (left + 19) / 20 + "s";
                g.drawString(font, t, x - font.width(t) / 2, y - 4, 0xFFE0E0E0, true);
                g.pose().popPose();
            }
        }
        int below = cy + RING + SLOT + 10;
        if (hovered >= 0) {
            center(g, Component.translatable(GladiusClient.key(hovered)).getString(), below, accent);
            center(g, Component.translatable(GladiusClient.key(hovered) + ".desc").getString(), below + 11, 0xFFB0B0B0);
        }
        center(g, Component.translatable("gui.projectminecraft.gladius.selected", Component.translatable(GladiusClient.key(Math.min(GladiusClient.selected, n - 1)))).getString(),
                cy - RING - SLOT - 16, 0xFFB0B0B0);
    }

    private void center(GuiGraphics g, String s, int y, int color) {
        g.drawString(font, s, width / 2 - font.width(s) / 2, y, color, true);
    }

    static int pick(double dx, double dy, int n) {
        if (Math.sqrt(dx * dx + dy * dy) < DEAD) return -1;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        double step = Math.PI * 2 / n;
        return (int) Math.floor((a + step / 2) / step) % n;
    }

    private void choose() {
        if (hovered >= 0) GladiusClient.select(hovered);
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
            hovered = pick(x - width / 2.0, y - height / 2.0, GladiusClient.count());
            choose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
}
