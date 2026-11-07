package com.slop.projectminecraft.tour;

import com.slop.projectminecraft.boss.Claw;
import com.slop.projectminecraft.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The Claw stood on a grey sheet from four sides (front, three quarters, his claw side, back), like the user's reference. */
public class ClawSheet extends Screen {
    private static final float[] TURNS = {0, 35, 90, 180};
    private net.minecraft.world.entity.LivingEntity claw;
    /** The player instead of the Claw (what they wear and hold). */
    private final boolean player;

    ClawSheet() {
        this(false);
    }

    ClawSheet(boolean player) {
        super(Component.literal("claw"));
        this.player = player;
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
        Minecraft mc = Minecraft.getInstance();
        if (claw == null && mc.level != null) claw = player ? mc.player : ModEntities.CLAW.get().create(mc.level);
        if (claw == null) return;
        float cw = width / (float) TURNS.length, scale = height * 0.36f;
        float yRot = claw.getYRot(), body = claw.yBodyRot, head = claw.yHeadRot, xRot = claw.getXRot();
        for (int i = 0; i < TURNS.length; i++) {
            claw.setYRot(0);
            claw.yRotO = claw.yBodyRot = claw.yBodyRotO = claw.yHeadRot = claw.yHeadRotO = 0;
            claw.setXRot(0);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(180 + TURNS[i]));
            InventoryScreen.renderEntityInInventory(g, cw * (i + 0.5f), height * 0.88f, scale, new Vector3f(0, 0, 0), pose, null, claw);
        }
        claw.setYRot(yRot);
        claw.yBodyRot = body;
        claw.yHeadRot = head;
        claw.setXRot(xRot);
    }
}
