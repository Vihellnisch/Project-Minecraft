package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.network.ArsenalActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Meeting Vergilius: draw your weapon, or show him the hitlist. */
public class VergiliusChoiceScreen extends Screen {
    private final int verg;

    VergiliusChoiceScreen(int verg) {
        super(Component.translatable("screen.projectminecraft.vergilius.choice"));
        this.verg = verg;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 10;
        addRenderableWidget(Button.builder(Component.translatable("screen.projectminecraft.vergilius.draw"), b -> pick(1)).bounds(cx - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.projectminecraft.vergilius.warn"), b -> pick(0)).bounds(cx + 5, y, 150, 20).build());
    }

    private void pick(int choice) {
        PacketDistributor.sendToServer(new ArsenalActionPayload(ArsenalActionPayload.VERG_CHOICE, 0, 0, 0, new int[]{verg, choice}, new float[0]));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 24, 0xD83030);
        g.drawCenteredString(font, Component.translatable("screen.projectminecraft.vergilius.choice.sub"), width / 2, height / 2 - 10, 0xB0B0B0);
    }
}
