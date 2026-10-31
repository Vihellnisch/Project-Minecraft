package com.slop.projectminecraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.slop.projectminecraft.Config;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/**
 * Owns the full-screen post chains. They run right after the level (and hand) are drawn, before the glowing
 * entity outlines are composited. Order: shockwaves, the arsenal grade (tint, ink frames), impact frames.
 */
public final class PostFx {
    private PostFx() {}

    private enum Fx {
        SHOCK("shock"), ARSENAL("arsenal"), IMPACT("impact");

        final ResourceLocation id;
        PostChain chain;

        Fx(String name) {
            this.id = ProjectMinecraft.id("shaders/post/" + name + ".json");
        }
    }

    private static boolean broken;
    private static int width, height;

    static void invalidate() {
        for (Fx fx : Fx.values()) {
            if (fx.chain != null) fx.chain.close();
            fx.chain = null;
        }
        broken = false;
    }

    private static boolean enabled() {
        return !broken && Config.SCREEN_SHADERS.get();
    }

    @Nullable
    private static PostChain get(Fx fx) {
        if (fx.chain != null || broken) return fx.chain;
        Minecraft mc = Minecraft.getInstance();
        try {
            fx.chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), fx.id);
            fx.chain.resize(mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
        } catch (Exception e) {
            ProjectMinecraft.LOGGER.error("Project Minecraft: failed to load post shader {}, screen effects disabled until reload", fx.id, e);
            broken = true;
        }
        return fx.chain;
    }

    private static void syncSize() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width == width && main.height == height) return;
        width = main.width;
        height = main.height;
        for (Fx fx : Fx.values()) {
            if (fx.chain != null) fx.chain.resize(width, height);
        }
    }

    /** Called from GameRendererMixin right before entity outlines are drawn. */
    public static void process(DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !enabled()) return;
        float partial = tracker.getGameTimeDeltaPartialTick(false);
        int shocks = Shockwaves.count;
        float impact = ImpactFrame.strength();
        float[] ars = com.slop.projectminecraft.client.arsenal.ArsenalClient.shader(partial);
        if (shocks == 0 && impact <= 0 && ars == null) return;

        float delta = tracker.getGameTimeDeltaTicks();
        syncSize();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        float seconds = ClientFx.seconds(partial);
        float aspect = (float) width / Math.max(1, height);

        PostChain c;
        if (shocks > 0 && (c = get(Fx.SHOCK)) != null) {
            c.setUniform("Count", shocks);
            c.setUniform("Aspect", aspect);
            for (int i = 0; i < Shockwaves.MAX; i++) {
                c.setUniform("S" + i + "X", Shockwaves.X[i]);
                c.setUniform("S" + i + "Y", Shockwaves.Y[i]);
                c.setUniform("S" + i + "R", Shockwaves.R[i]);
                c.setUniform("S" + i + "A", i < shocks ? Shockwaves.A[i] : 0);
            }
            c.process(delta);
        }
        if (ars != null && (c = get(Fx.ARSENAL)) != null) {
            c.setUniform("Deadeye", ars[0]);
            c.setUniform("Beat", ars[1]);
            c.setUniform("Ink", ars[2]);
            c.setUniform("InkSeed", ars[3]);
            c.setUniform("Tint", ars[4]);
            c.setUniform("TintR", ars[5]);
            c.setUniform("TintG", ars[6]);
            c.setUniform("TintB", ars[7]);
            c.setUniform("Speed", ars[8]);
            c.setUniform("Aspect", aspect);
            c.setUniform("GameTime", seconds);
            c.process(delta);
        }
        if (impact > 0 && (c = get(Fx.IMPACT)) != null) {
            c.setUniform("Strength", impact);
            c.setUniform("Style", ImpactFrame.style());
            c.setUniform("OriginX", ImpactFrame.originX());
            c.setUniform("OriginY", ImpactFrame.originY());
            c.setUniform("Aspect", aspect);
            c.setUniform("GameTime", seconds);
            c.process(delta);
        }

        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
    }
}
