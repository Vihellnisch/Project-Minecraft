package com.slop.projectminecraft.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Expanding shockwave rings and camera-facing flashes (additive). */
public final class RingFx {
    private RingFx() {}

    private record Ring(Vec3 center, Vec3 normal, float from, float to, int life, float r, float g, float b, float alpha, boolean flash, int[] age) {}

    private static final List<Ring> RINGS = new ArrayList<>();

    public static void add(Vec3 center, Vec3 normal, float from, float to, int life, float r, float g, float b, float alpha) {
        RINGS.add(new Ring(center, normal, from, to, life, r, g, b, alpha, false, new int[1]));
    }

    /** Billboard glow disc that swells and fades. */
    public static void flash(Vec3 center, float size, int life, float r, float g, float b, float alpha) {
        RINGS.add(new Ring(center, Vec3.ZERO, size * 0.4f, size, life, r, g, b, alpha, true, new int[1]));
    }

    public static void tick() {
        RINGS.removeIf(ring -> ++ring.age[0] >= ring.life);
    }

    public static void clear() {
        RINGS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (RINGS.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.GLOW);
        Matrix4f m = new Matrix4f();
        for (Ring ring : RINGS) {
            float t = Math.min(1.0f, (ring.age[0] + partial) / ring.life);
            float ease = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            float radius = ring.from + (ring.to - ring.from) * ease;
            float a = ring.alpha * (1.0f - t);
            Vec3 c = ring.center.subtract(cam);
            if (ring.flash) {
                Vec3 n = c.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : c;
                FxRenderTypes.annulus(vc, m, c, n, 0, radius, 24, ring.r, ring.g, ring.b, a, 0);
            } else {
                float w = Math.max(0.15f, radius * 0.12f);
                FxRenderTypes.annulus(vc, m, c, ring.normal, Math.max(0, radius - w), radius, 48, ring.r, ring.g, ring.b, 0, a);
                FxRenderTypes.annulus(vc, m, c, ring.normal, radius, radius + w * 0.35f, 48, ring.r, ring.g, ring.b, a, 0);
            }
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }
}
