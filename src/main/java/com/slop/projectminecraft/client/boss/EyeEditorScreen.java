package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.arsenal.EyeSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Which pixels of your face glow when you wear Vergilius's eyes: your own face (skin and hat layer) as an 8x8 grid,
 * click a pixel to light it or put it out. Done keeps it; Reset goes back to the usual two; Clear puts them all out.
 */
public class EyeEditorScreen extends Screen {
    static final int CELL = 16, GRID = CELL * 8;
    private final Screen back;
    private long mask;
    private boolean glow;

    public EyeEditorScreen(Screen back) {
        super(Component.translatable("gui.projectminecraft.eye_editor"));
        this.back = back;
        this.mask = GladiusClient.mask;
        this.glow = GladiusClient.glow;
    }

    int gx() {
        return width / 2 - GRID / 2;
    }

    int gy() {
        return height / 2 - GRID / 2 - 14;
    }

    @Override
    protected void init() {
        int y = gy() + GRID + 8, cx = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.projectminecraft.eye_editor.reset"), b -> mask = EyeSlot.DEFAULT_MASK).bounds(cx - 96, y, 62, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.projectminecraft.eye_editor.clear"), b -> mask = 0).bounds(cx - 31, y, 62, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 34, y, 62, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        Minecraft mc = Minecraft.getInstance();
        int x0 = gx(), y0 = gy();
        g.drawCenteredString(font, title, width / 2, y0 - 22, 0xFFD83030);
        g.drawCenteredString(font, Component.translatable("gui.projectminecraft.eye_editor.hint"), width / 2, y0 - 11, 0xFFB0B0B0);
        g.fill(x0 - 2, y0 - 2, x0 + GRID + 2, y0 + GRID + 2, 0xFF202020);
        // your face: the skin's face, then its hat layer over it
        if (mc.player != null) {
            ResourceLocation skin = mc.player.getSkin().texture();
            g.blit(skin, x0, y0, GRID, GRID, 8, 8, 8, 8, 64, 64);
            g.blit(skin, x0, y0, GRID, GRID, 40, 8, 8, 8, 64, 64);
        }
        int hover = cell(mouseX, mouseY);
        for (int bit = 0; bit < 64; bit++) {
            int cx = x0 + (bit % 8) * CELL, cy = y0 + (bit / 8) * CELL;
            if ((mask & (1L << bit)) != 0) {
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xC0FF2020);
                g.fill(cx + 5, cy + 5, cx + CELL - 5, cy + CELL - 5, 0xFFFF8080);
            }
            if (bit == hover) g.fill(cx, cy, cx + CELL, cy + CELL, 0x60FFFFFF);
        }
        // the grid lines
        for (int k = 0; k <= 8; k++) {
            g.fill(x0 + k * CELL, y0, x0 + k * CELL + 1, y0 + GRID, 0x40000000);
            g.fill(x0, y0 + k * CELL, x0 + GRID, y0 + k * CELL + 1, 0x40000000);
        }
    }

    int cell(double mx, double my) {
        int c = (int) Math.floor((mx - gx()) / CELL), r = (int) Math.floor((my - gy()) / CELL);
        return c < 0 || r < 0 || c > 7 || r > 7 ? -1 : r * 8 + c;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bit = cell(mx, my);
        if (bit >= 0 && (button == 0 || button == 1)) {
            mask ^= 1L << bit;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        GladiusClient.sendLook(glow, mask);
        Minecraft.getInstance().setScreen(back);
    }
}
