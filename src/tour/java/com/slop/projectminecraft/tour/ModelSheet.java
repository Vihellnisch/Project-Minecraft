package com.slop.projectminecraft.tour;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.slop.projectminecraft.client.arsenal.GloveLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every Furioso model stood upright on a grey sheet, like the reference art, for side-by-side checks. */
public class ModelSheet extends Screen {
    /** Which models, in the order of the reference sheet's columns. */
    private static final int[] ORDER = {8, 1, 10, 7, 2, 5, 9, 3, 12, 4, 0, 6, 13, 11};
    private static final ItemStack CARRIER = new ItemStack(Items.STICK);
    private final float turn;

    ModelSheet(float turn) {
        super(Component.literal("models"));
        this.turn = turn;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xFF6B6B6B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cols = 7;
        float cw = width / (float) cols, ch = height / 2f, s = ch * 0.36f;
        for (int i = 0; i < ORDER.length; i++) {
            int m = ORDER[i];
            BakedModel model = GloveLayer.model(m);
            if (model == null) continue;
            boolean gun = m == 0 || m == 10;
            g.pose().pushPose();
            g.pose().translate(cw * (i % cols + 0.5f), ch * (i / cols + 0.5f), 150);
            g.pose().scale(s, -s, s);
            if (gun) {
                g.pose().mulPose(Axis.YP.rotationDegrees(90 + turn));
            } else {
                g.pose().mulPose(Axis.YP.rotationDegrees(turn));
                // undo the models' 45 degree lean so they stand up
                g.pose().mulPose(Axis.ZP.rotationDegrees(45));
                g.pose().translate(0, -0.55f, 0);
            }
            Lighting.setupForFlatItems();
            Minecraft.getInstance().getItemRenderer().render(CARRIER, ItemDisplayContext.NONE, false, g.pose(), g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, model);
            g.flush();
            Lighting.setupFor3DItems();
            g.pose().popPose();
        }
    }
}
