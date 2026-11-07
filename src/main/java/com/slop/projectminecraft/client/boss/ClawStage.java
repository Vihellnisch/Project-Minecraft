package com.slop.projectminecraft.client.boss;

import static com.slop.projectminecraft.warp.ClawWarp.BELOW;
import static com.slop.projectminecraft.warp.ClawWarp.FRONT;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.slop.projectminecraft.network.ClawStagePayload;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * One of Serum W's backdrops: the slab of blocks the server copied out of a place, as a little world of its own (so the
 * block models can be drawn exactly as they'd be there, faces between blocks culled, water and lava included), baked
 * once into a mesh on the GPU and drawn behind the scene. Stage coordinates: x along the rush, y up (BELOW of it under
 * their feet), z toward the camera (FRONT of it in front of them).
 */
final class ClawStage implements BlockAndTintGetter {
    final int index, sx, sy, sz, sky, fog;
    final boolean ceiling;
    final String dimension;
    private final BlockState[] states;
    @Nullable
    private VertexBuffer mesh;
    private boolean baked;

    ClawStage(ClawStagePayload p) {
        index = p.index();
        sx = p.sx();
        sy = p.sy();
        sz = p.sz();
        sky = p.sky();
        fog = p.fog();
        ceiling = p.ceiling();
        dimension = p.dimension();
        BlockState[] palette = new BlockState[p.palette().length];
        for (int i = 0; i < palette.length; i++) palette[i] = Block.stateById(p.palette()[i]);
        states = new BlockState[sx * sy * sz];
        byte[] b = p.blocks();
        for (int i = 0; i < states.length; i++) {
            int id = i < b.length ? b[i] & 255 : 0;
            BlockState s = id < palette.length ? palette[id] : null;
            states[i] = s == null ? Blocks.AIR.defaultBlockState() : s;
        }
    }

    BlockState at(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return Blocks.AIR.defaultBlockState();
        return states[(y * sz + z) * sx + x];
    }

    /** On the line they rush along, or in front of it, above the ground: left out, so nothing stands in their way or the camera's. */
    boolean shown(int x, int y, int z) {
        return !(z >= sz - 1 - FRONT && y >= BELOW);
    }

    // ------------------------------------------------------------------ as a world, for the block renderers

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return shown(pos.getX(), pos.getY(), pos.getZ()) ? at(pos.getX(), pos.getY(), pos.getZ()) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return sy;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }

    @Override
    public float getShade(Direction d, boolean shade) {
        if (!shade) return 1;
        return switch (d) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
    }

    /** Never asked for: the brightness methods that would use it are answered directly (it's all fully lit). */
    @Override
    public LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException("a backdrop has no light engine");
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) return 0x79C05A;
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return 0x59AE30;
        return 0x3F76E4;
    }

    // ------------------------------------------------------------------ the mesh

    boolean ready() {
        return baked;
    }

    /** Build the mesh now (render thread), if it isn't yet. */
    void prepare() {
        if (!baked) bake();
    }

    /** Build the mesh (on the render thread, ahead of time or else the first time it's drawn). */
    private void bake() {
        baked = true;
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create(42);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 18)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            PoseStack pose = new PoseStack();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
                if (!shown(x, y, z)) continue;
                BlockState s = at(x, y, z);
                if (s.isAir()) continue;
                pos.set(x, y, z);
                if (s.getRenderShape() == RenderShape.MODEL) {
                    pose.pushPose();
                    pose.translate(x, y, z);
                    var model = blocks.getBlockModel(s);
                    random.setSeed(42);
                    for (RenderType layer : model.getRenderTypes(s, random, ModelData.EMPTY)) {
                        blocks.renderBatched(s, pos, this, pose, builder, true, random, ModelData.EMPTY, layer);
                    }
                    pose.popPose();
                }
                FluidState fluid = s.getFluidState();
                // the liquid renderer writes within a 16-block section: put each section back where it belongs
                if (!fluid.isEmpty()) blocks.renderLiquid(pos.immutable(), this, new Offset(builder, x & ~15, y & ~15, z & ~15), s, fluid);
            }
            try (MeshData data = builder.build()) {
                if (data == null) return;
                mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
                mesh.bind();
                mesh.upload(data);
                VertexBuffer.unbind();
            }
        } catch (Exception ex) {
            com.slop.projectminecraft.ProjectMinecraft.LOGGER.warn("Project Minecraft: couldn't build the Serum W backdrop for {}", dimension, ex);
            close();
        }
    }

    /** Draw the slab with its origin (stage 0,0,0) at `origin` (camera-relative), tinted by `tint` (rgb 0..1). */
    void draw(Matrix4f modelView, Matrix4f projection, double ox, double oy, double oz, float r, float g, float b) {
        if (!baked) bake();
        if (mesh == null) return;
        Matrix4f mv = new Matrix4f(modelView).translate((float) ox, (float) oy, (float) oz);
        RenderType layer = RenderType.cutout();
        layer.setupRenderState();
        float fogStart = RenderSystem.getShaderFogStart();
        try {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderColor(r, g, b, 1);
            mesh.bind();
            mesh.drawWithShader(mv, projection, RenderSystem.getShader());
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(fogStart);
            layer.clearRenderState();
        }
    }

    void close() {
        if (mesh != null) mesh.close();
        mesh = null;
    }

    /** A vertex consumer that shifts every vertex by a fixed offset. */
    private record Offset(VertexConsumer to, float dx, float dy, float dz) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            to.addVertex(x + dx, y + dy, z + dz);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            to.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            to.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            to.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            to.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            to.setNormal(x, y, z);
            return this;
        }
    }
}
