package com.slop.projectminecraft.tour;

import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A mob stood on a grey sheet from four sides (front, three quarters, side, back), for comparing with the reference art. */
public class MobSheet extends Screen {
    private static final float[] TURNS = {0, 35, 90, 180};
    private final Function<Level, LivingEntity> make;
    private LivingEntity mob;

    MobSheet(Function<Level, LivingEntity> make) {
        super(Component.literal("sheet"));
        this.make = make;
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
        if (mob == null && mc.level != null) mob = make.apply(mc.level);
        if (mob == null) return;
        mob.tickCount = (int) (mc.level.getGameTime() % 100000);
        float cw = width / (float) TURNS.length, scale = height * 0.34f;
        for (int i = 0; i < TURNS.length; i++) {
            mob.setYRot(0);
            mob.yRotO = mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = 0;
            mob.setXRot(0);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(180 + TURNS[i]));
            InventoryScreen.renderEntityInInventory(g, cw * (i + 0.5f), height * 0.86f, scale, new Vector3f(0, 0, 0), pose, null, mob);
        }
    }
}
