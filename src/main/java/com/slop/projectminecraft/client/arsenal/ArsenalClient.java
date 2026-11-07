package com.slop.projectminecraft.client.arsenal;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;

/** Client hub of the Black Silence and the E.G.O: packets in, ticking, input, the render passes and shared hit effects. */
public final class ArsenalClient {
    private ArsenalClient() {}

    private static final RandomSource R = RandomSource.create();

    public static void handle(ArsenalFxPayload p) {
        if (Minecraft.getInstance().level == null) return;
        switch (p.kind()) {
            case ArsenalFxPayload.EGO -> EgoClient.start(p);
            case ArsenalFxPayload.FURIOSO -> FuriosoClient.start(p);
            case ArsenalFxPayload.ROLAND -> com.slop.projectminecraft.client.boss.RolandClient.handle(p);
            case ArsenalFxPayload.CLAW -> com.slop.projectminecraft.client.boss.ClawClient.handle(p);
            case ArsenalFxPayload.GLOVES -> GloveClient.state(p);
            case ArsenalFxPayload.GLOVE_ATTACK -> GloveClient.attack(p);
            default -> {}
        }
    }

    // =================================================================== shared hit effects

    /** Crosshair hit marker: ticks left and kind (0 hit, 1 head, 2 special). */
    static int hitmarker, hitmarkerKind;
    /** Ink impact frames (black, inverted, red) after a big cut: frame index, -1 = none. */
    static int inkFrame = -1;
    static float inkSeed;

    /** Which impact frame is showing (0 = none, 1 = black, 2 = inverted, 3 = red). */
    public static int ink() {
        if (inkFrame < 0) return 0;
        return inkFrame < 2 ? 1 : inkFrame < 4 ? 2 : 3;
    }

    public static float inkSeed() {
        return inkSeed;
    }

    static void impact(ClientLevel level, Vec3 at, boolean flesh, float r, float g, float b) {
        for (int i = 0; i < (flesh ? 6 : 10); i++) {
            level.addParticle(flesh ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, R.nextGaussian() * 0.25, R.nextDouble() * 0.25, R.nextGaussian() * 0.25);
        }
        if (!flesh) level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.02, 0);
        RingFx.flash(at, 0.5f, 4, r, g, b, 0.9f);
    }

    /** A spurt of blood on a hit (vanilla particles). dirTimesDamage: hit direction scaled by damage dealt. */
    static void blood(ClientLevel level, Vec3 at, Vec3 dirTimesDamage, @Nullable Entity victim) {
        double dmg = dirTimesDamage.length();
        Vec3 dir = dmg < 1e-4 ? new Vec3(0, 0.3, 0) : dirTimesDamage.scale(1.0 / dmg);
        BlockParticleOption drop = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        int n = (int) Math.min(24, 4 + dmg * 2);
        for (int i = 0; i < n; i++) {
            Vec3 v = dir.scale(0.2 + R.nextDouble() * 0.25).add(R.nextGaussian() * 0.1, 0.1 + R.nextDouble() * 0.15, R.nextGaussian() * 0.1);
            level.addParticle(drop, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        level.addParticle(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, dir.x * 0.2, 0.1, dir.z * 0.2);
    }

    // =================================================================== screen pass

    /** Uniforms for the arsenal screen pass, or null when nothing needs it. */
    @Nullable
    public static float[] shader(float partial) {
        int ink = ink();
        float tint = EgoClient.tint(partial);
        int tintColor = EgoClient.tintColor();
        // Furioso: black and gold for the title card, then a drained, grainy look whenever time slows
        FuriosoClient.Run fr = FuriosoClient.mine();
        if (fr != null && tint < 0.01f) {
            double ft = fr.st(partial);
            if (ft < com.slop.projectminecraft.arsenal.FuriosoScript.DASH) {
                tint = 0.55f;
                tintColor = 0xC8A040;
            } else if (FuriosoClient.grade(partial) > 0.01f) {
                tint = 0.32f * FuriosoClient.grade(partial);
                tintColor = 0xD8D0C0;
            }
        }
        if (ink == 0 && tint <= 0.001f) return null;
        int c = tintColor;
        return new float[]{0, 0, ink, inkSeed, tint, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 0};
    }

    // =================================================================== ticking

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (hitmarker > 0) hitmarker--;
        if (inkFrame >= 0 && ++inkFrame > 5) inkFrame = -1;
        EgoClient.tick(mc);
        FuriosoClient.tick(mc);
        GloveClient.tick(mc);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer p && p == Minecraft.getInstance().player) FuriosoClient.drive(p);
    }

    // =================================================================== input

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        // the worn Black Silence: an empty-handed attack is the weapon on the wheel
        if (event.isAttack() && GloveClient.onAttack(Minecraft.getInstance())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !FuriosoClient.active(mc.player.getId())) return;
        var in = event.getInput();
        in.forwardImpulse = in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    // =================================================================== rendering

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // one batch per render type: opaque props, then translucent, then additive light
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f m = new Matrix4f();
        VertexConsumer solid = buffers.getBuffer(FxRenderTypes.SOLID_COLOR);
        EgoClient.render(solid, null, null, m, cam, partial);
        FuriosoClient.render(solid, null, m, cam, partial);
        buffers.endBatch(FxRenderTypes.SOLID_COLOR);
        VertexConsumer shade = buffers.getBuffer(FxRenderTypes.SHADE);
        EgoClient.render(null, shade, null, m, cam, partial);
        buffers.endBatch(FxRenderTypes.SHADE);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        EgoClient.render(null, null, glow, m, cam, partial);
        FuriosoClient.render(null, glow, m, cam, partial);
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    /** Called from HumanoidModelMixin at the end of setupAnim. */
    public static void pose(HumanoidModel<?> m, LivingEntity e) {
        GloveClient.pose(m, e);
        FuriosoClient.pose(m, e);
        com.slop.projectminecraft.client.boss.ClawWarpClient.pose(m, e);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hitmarker = 0;
        inkFrame = -1;
        EgoClient.clear();
        FuriosoClient.clear();
        GloveClient.clear();
    }
}
