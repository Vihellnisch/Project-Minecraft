package com.slop.projectminecraft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Untextured render types for energy effects, plus small geometry helpers shared by the renderers. */
public final class FxRenderTypes extends RenderType {
    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** Additive glow: no culling, depth tested but not written, so layered glows stack. */
    public static final RenderType GLOW = create("projectminecraft_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Additive glow that ignores depth entirely (X-ray outlines). */
    public static final RenderType GLOW_XRAY = create("projectminecraft_glow_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Normal alpha blending, no depth write (dark smoky shapes). */
    public static final RenderType SHADE = create("projectminecraft_shade", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Opaque, unlit, depth writing (the black core of a singularity). */
    public static final RenderType SOLID_COLOR = create("projectminecraft_solid_color", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final ShaderStateShard POSITION_TEX_COLOR = new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, RenderType> TEXTURED = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, RenderType> TEXTURED_GLOW = new java.util.HashMap<>();

    /** Opaque, unlit, textured (moon, planets): POSITION_TEX_COLOR, colour carries the shading. */
    public static RenderType textured(net.minecraft.resources.ResourceLocation tex) {
        return TEXTURED.computeIfAbsent(tex, t -> create("projectminecraft_textured", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_TEX_COLOR)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false)));
    }

    /** Additive textured glow (energy textures, nebulae). */
    public static RenderType texturedGlow(net.minecraft.resources.ResourceLocation tex) {
        return TEXTURED_GLOW.computeIfAbsent(tex, t -> create("projectminecraft_textured_glow", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder()
                        .setShaderState(POSITION_TEX_COLOR)
                        .setTextureState(new TextureStateShard(t, true, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false)));
    }

    private static final java.util.Map<net.minecraft.resources.ResourceLocation, RenderType> ENTITY_DEPTH = new java.util.HashMap<>();

    /**
     * An entity's shape written to the depth buffer only (no colour), culled, cut out by its texture. Drawn just before a
     * see-through pass of the same model, so that only its front surface shows: no back of the head through the face.
     */
    public static RenderType entityDepth(net.minecraft.resources.ResourceLocation tex) {
        return ENTITY_DEPTH.computeIfAbsent(tex, t -> create("projectminecraft_entity_depth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(DEPTH_WRITE)
                        .createCompositeState(true)));
    }

    /** Draws `draw` into the depth-only type at once (it must land before the see-through pass that follows). */
    public static void depthPrepass(net.minecraft.client.renderer.MultiBufferSource buffers, net.minecraft.resources.ResourceLocation tex,
                                    java.util.function.Consumer<VertexConsumer> draw) {
        RenderType t = entityDepth(tex);
        draw.accept(buffers.getBuffer(t));
        if (buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) bs.endBatch(t);
    }

    // ------------------------------------------------------------ helpers

    /** Open tube from a to b (camera relative) with radius ra at a and rb at b. */
    public static void tube(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float ra, float rb, int segs, float r, float g, float bl, float alphaA, float alphaB) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1e-8) return;
        Vec3 n = axis.normalize();
        Vec3 t1 = Math.abs(n.y) < 0.99 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 t2 = n.cross(t1).normalize();
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 d0 = t1.scale(Math.cos(a0)).add(t2.scale(Math.sin(a0)));
            Vec3 d1 = t1.scale(Math.cos(a1)).add(t2.scale(Math.sin(a1)));
            vertex(vc, m, a.add(d0.scale(ra)), r, g, bl, alphaA);
            vertex(vc, m, b.add(d0.scale(rb)), r, g, bl, alphaB);
            vertex(vc, m, b.add(d1.scale(rb)), r, g, bl, alphaB);
            vertex(vc, m, a.add(d1.scale(ra)), r, g, bl, alphaA);
        }
    }

    /** Sphere shaded by a light direction (for SOLID_COLOR / SHADE types). */
    public static void litSphere(VertexConsumer vc, Matrix4f m, Vec3 c, float radius, int rings, int segs, Vec3 light, float r, float g, float b, float a, float ambient) {
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings, t1 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segs; j++) {
                double p0 = 2 * Math.PI * j / segs, p1 = 2 * Math.PI * (j + 1) / segs;
                Vec3[] q = {sph(t0, p0, 1), sph(t1, p0, 1), sph(t1, p1, 1), sph(t0, p1, 1)};
                for (Vec3 n : q) {
                    float s = (float) Math.max(0, n.dot(light)) * (1 - ambient) + ambient;
                    vertex(vc, m, c.add(n.scale(radius)), r * s, g * s, b * s, a);
                }
            }
        }
    }

    /** Equirectangular-textured sphere (POSITION_TEX_COLOR), lit from light, spinning by rot radians around y. */
    public static void texturedSphere(VertexConsumer vc, Matrix4f m, Vec3 c, float radius, int rings, int segs, Vec3 light, float ambient, double rot, float tint) {
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings, t1 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segs; j++) {
                double p0 = 2 * Math.PI * j / segs + rot, p1 = 2 * Math.PI * (j + 1) / segs + rot;
                float u0 = (float) j / segs, u1 = (float) (j + 1) / segs, v0 = (float) i / rings, v1 = (float) (i + 1) / rings;
                texVert(vc, m, c, radius, t0, p0, u0, v0, light, ambient, tint);
                texVert(vc, m, c, radius, t1, p0, u0, v1, light, ambient, tint);
                texVert(vc, m, c, radius, t1, p1, u1, v1, light, ambient, tint);
                texVert(vc, m, c, radius, t0, p1, u1, v0, light, ambient, tint);
            }
        }
    }

    private static void texVert(VertexConsumer vc, Matrix4f m, Vec3 c, float radius, double theta, double phi, float u, float v, Vec3 light, float ambient, float tint) {
        Vec3 n = sph(theta, phi, 1);
        float s = ((float) Math.max(0, n.dot(light)) * (1 - ambient) + ambient) * tint;
        Vec3 p = c.add(n.scale(radius));
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(s, s, s, 1.0f);
    }

    /** Axis-aligned-then-rotated box helper for flat shaded props (satellite etc.): 6 faces shaded by light. */
    public static void box(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, Vec3 light, float r, float g, float b, float a) {
        Vec3[][] faces = {
                {ax, ay, az}, {ax.scale(-1), ay, az.scale(-1)}, {ay, az, ax}, {ay.scale(-1), az, ax.scale(-1)}, {az, ax, ay}, {az.scale(-1), ax, ay.scale(-1)}};
        for (Vec3[] f : faces) {
            Vec3 n = f[0], u = f[1], v = f[2];
            Vec3 nn = n.normalize();
            float s = (float) Math.max(0, nn.dot(light)) * 0.65f + 0.35f;
            Vec3 fc = c.add(n);
            vertex(vc, m, fc.subtract(u).subtract(v), r * s, g * s, b * s, a);
            vertex(vc, m, fc.add(u).subtract(v), r * s, g * s, b * s, a);
            vertex(vc, m, fc.add(u).add(v), r * s, g * s, b * s, a);
            vertex(vc, m, fc.subtract(u).add(v), r * s, g * s, b * s, a);
        }
    }

    /** Camera-facing ribbon segment from a to b (positions already camera relative). */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alphaA, float alphaB) {
        Vec3 dir = b.subtract(a);
        Vec3 view = a.add(b).scale(0.5);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width * 0.5);
        vc.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, alphaA);
        vc.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, alphaA);
        vc.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(r, g, bl, alphaB);
        vc.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(r, g, bl, alphaB);
    }

    /** Flat annulus around center in the plane perpendicular to normal; alpha goes inner -> outer. */
    public static void annulus(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 normal, float inner, float outer, int segments,
                               float r, float g, float b, float alphaIn, float alphaOut) {
        Vec3 n = normal.normalize();
        Vec3 t1 = Math.abs(n.y) < 0.99 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 t2 = n.cross(t1).normalize();
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 d0 = t1.scale(Math.cos(a0)).add(t2.scale(Math.sin(a0)));
            Vec3 d1 = t1.scale(Math.cos(a1)).add(t2.scale(Math.sin(a1)));
            vertex(vc, m, c.add(d0.scale(inner)), r, g, b, alphaIn);
            vertex(vc, m, c.add(d0.scale(outer)), r, g, b, alphaOut);
            vertex(vc, m, c.add(d1.scale(outer)), r, g, b, alphaOut);
            vertex(vc, m, c.add(d1.scale(inner)), r, g, b, alphaIn);
        }
    }

    /** Latitude/longitude sphere. */
    public static void sphere(VertexConsumer vc, Matrix4f m, Vec3 c, float radius, int rings, int segs, float r, float g, float b, float a) {
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings, t1 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segs; j++) {
                double p0 = 2 * Math.PI * j / segs, p1 = 2 * Math.PI * (j + 1) / segs;
                vertex(vc, m, c.add(sph(t0, p0, radius)), r, g, b, a);
                vertex(vc, m, c.add(sph(t1, p0, radius)), r, g, b, a);
                vertex(vc, m, c.add(sph(t1, p1, radius)), r, g, b, a);
                vertex(vc, m, c.add(sph(t0, p1, radius)), r, g, b, a);
            }
        }
    }

    private static Vec3 sph(double theta, double phi, double r) {
        return new Vec3(Math.sin(theta) * Math.cos(phi) * r, Math.cos(theta) * r, Math.sin(theta) * Math.sin(phi) * r);
    }

    public static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    public static Matrix4f pose(PoseStack ps) {
        return ps.last().pose();
    }
}
