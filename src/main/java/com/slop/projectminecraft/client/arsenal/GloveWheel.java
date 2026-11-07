package com.slop.projectminecraft.client.arsenal;

import static com.slop.projectminecraft.arsenal.FuriosoScript.WEAPONS;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * The Black Silence wheel: hold the key, point at a weapon, let go. Roland's twelve in Furioso order around the ring;
 * the ones already used on the current enemy are lit. The centre toggles Random mode, or unleashes Furioso once all
 * twelve have landed.
 */
public class GloveWheel extends Screen {
    static final int RING = 82, SLOT = 15, CENTER = 26, DEAD = 40;
    static final int GOLD = 0xFFE8C060;

    private int hovered = -1; // weapon, CENTER_ID, or -1
    private static final int CENTER_ID = 99;

    public GloveWheel() {
        super(Component.translatable("gui.projectminecraft.black_silence.wheel"));
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
        int cx = width / 2, cy = height / 2;
        hovered = pick(mouseX - cx, mouseY - cy);
        int sel = GloveClient.mine();
        float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;

        for (int w = 0; w < WEAPONS; w++) {
            double a = w * Math.PI * 2 / WEAPONS;
            int x = cx + (int) Math.round(Math.sin(a) * RING), y = cy - (int) Math.round(Math.cos(a) * RING);
            boolean used = (GloveClient.mask & (1 << w)) != 0, hot = hovered == w;
            int s = hot ? SLOT + 3 : SLOT;
            int border = w == sel ? 0xFFFFFFFF : used ? GOLD : 0xFF3A3A3A;
            g.fill(x - s - 1, y - s - 1, x + s + 1, y + s + 1, border);
            g.fill(x - s, y - s, x + s, y + s, hot ? 0xF0303030 : used ? 0xE0302814 : 0xE0141414);
            icon(g, w, x, y, hot ? 26 : 22);
        }

        // centre: Random, or Furioso when it's earned; locked to Random until they've mastered the gloves
        boolean ready = GloveClient.ready(), hot = hovered == CENTER_ID;
        if (!GloveClient.mastered) {
            g.fill(cx - CENTER, cy - CENTER, cx + CENTER, cy + CENTER, 0xE0281010);
            center(g, "RANDOM", cy - 9, 0xFFC8C8C8);
            center(g, "LOCKED", cy + 2, 0xFFB03030);
            String[] why = Component.translatable("gui.projectminecraft.black_silence.locked").getString().split("\\n");
            for (int i = 0; i < why.length; i++) center(g, why[i], cy + RING + SLOT + 10 + i * 11, i == 0 ? 0xFFE0A0A0 : 0xFF9A9A9A);
            return;
        }
        int c = ready ? (int) (180 + 75 * Mth.sin(time * 0.4f)) : hot ? 70 : 40;
        g.fill(cx - CENTER, cy - CENTER, cx + CENTER, cy + CENTER, ready ? (0xE0 << 24) | (c << 16) | ((int) (c * 0.75f) << 8) | 0x30 : (0xE0 << 24) | (c << 16) | (c << 8) | c);
        if (ready) {
            center(g, "FURIOSO", cy - 4, 0xFF000000);
        } else {
            center(g, "RANDOM", cy - 9, 0xFFC8C8C8);
            center(g, GloveClient.random ? "ON" : "OFF", cy + 2, GloveClient.random ? GOLD : 0xFF808080);
        }

        // what's under the cursor, and the count
        int below = cy + RING + SLOT + 10;
        if (hovered >= 0 && hovered < WEAPONS) {
            String[] cap = FuriosoClient.CAPTIONS[hovered];
            center(g, cap[1], below, 0xFFFFFFFF);
            if (!cap[0].isEmpty()) center(g, cap[0], below + 11, 0xFF9A9A9A);
        } else if (hot) {
            center(g, ready ? "Unleash Furioso" : "Toggle Random mode (click)", below, 0xFFFFFFFF);
        }
        int used = Integer.bitCount(GloveClient.mask);
        center(g, GloveClient.target < 0 ? "No target yet" : used + " / " + WEAPONS + " used on this enemy", cy - RING - SLOT - 16, used == WEAPONS ? GOLD : 0xFFB0B0B0);
    }

    private void center(GuiGraphics g, String s, int y, int color) {
        g.drawString(font, s, width / 2 - font.width(s) / 2, y, color, color != 0xFF000000);
    }

    /** A weapon's 3D model as a GUI icon of `size` pixels centred on x, y. */
    static void icon(GuiGraphics g, int w, float x, float y, float size) {
        BakedModel model = GloveLayer.model(w);
        if (model == null) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 150);
        g.pose().scale(size, -size, size);
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Minecraft.getInstance().getItemRenderer().render(GloveLayer.carrier(), ItemDisplayContext.GUI, false, g.pose(), g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, model);
        g.flush();
        if (flat) Lighting.setupFor3DItems();
        g.pose().popPose();
    }

    static int pick(double dx, double dy) {
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d <= CENTER) return CENTER_ID;
        if (d < DEAD) return -1;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        double step = Math.PI * 2 / WEAPONS;
        return (int) Math.floor((a + step / 2) / step) % WEAPONS;
    }

    /** Letting go picks a weapon; the centre only answers a click, so a quick tap never toggles anything. */
    private void choose(boolean click) {
        // locked: nothing to choose (the server says why)
        if (!GloveClient.mastered) {
            if (hovered >= 0) GloveClient.select(hovered);
            onClose();
            return;
        }
        if (hovered >= 0 && hovered < WEAPONS) GloveClient.select(hovered);
        else if (hovered == CENTER_ID && click) GloveClient.select(GloveClient.ready() ? -2 : -1);
        else if (click) return; // a click on nothing keeps the wheel open
        onClose();
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (GloveClient.WHEEL_KEY.matches(key, scan)) {
            choose(false);
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (GloveClient.WHEEL_KEY.matchesMouse(button)) {
            choose(false);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            hovered = pick(x - width / 2.0, y - height / 2.0);
            choose(true);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
}
