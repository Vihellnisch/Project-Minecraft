package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * The Handmade Claw, worn rather than held: a plated gauntlet over the forearm, three jointed fingers out of the fist, and
 * the three serum vials side by side along the back of the hand, each lit while its serum runs. Drawn on the main arm in
 * third person (a player layer) and on the first-person arm; the item's own model is hidden in the hand.
 */
public final class ClawHands {
    private ClawHands() {}

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectMinecraft.id("handmade_claw"), "main");
    /** Vials in order K, R, W (the aura bit each glows on: 1, 2, 4; the cocktail lights all three). */
    private static final String[] VIALS = {"k", "r", "w"};
    private static final int[] GLASS_U = {32, 0, 48};
    private static ModelPart parts;

    /** Both arms (wide: right x -3..1, left x -1..3; +y down the arm, -z the front). "right"/"left" each hold the whole claw. */
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (boolean right : new boolean[]{true, false}) {
            // f: which way is "out" for this arm (-x on the right arm, +x on the left)
            float f = right ? -1 : 1, mid = right ? -1 : 1;
            PartDefinition arm = root.addOrReplaceChild(right ? "right" : "left", CubeListBuilder.create()
                    // the cuff and the gauntlet over the forearm
                    .texOffs(16, 16).addBox(mid - 2.6f, 3.4f, -2.6f, 5.2f, 1.6f, 5.2f)
                    .texOffs(16, 26).addBox(mid - 2.4f, 5, -2.4f, 4.8f, 5.5f, 4.8f), PartPose.ZERO);
            // the three vials laid side by side along the outside of the hand, parallel with a gap between them, tops
            // leaning out a little toward the elbow
            for (int i = 0; i < 3; i++) {
                PartPose at = PartPose.offsetAndRotation(mid + f * 2.5f, 9.6f, -1.6f + i * 1.6f, 0, 0, f * 0.22f);
                arm.addOrReplaceChild("vial_" + VIALS[i], CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-0.5f, -4.5f, -0.5f, 1, 4.5f, 1)
                        .texOffs(16, 0).addBox(-0.35f, -5, -0.35f, 0.7f, 0.5f, 0.7f), at);
                arm.addOrReplaceChild("glass_" + VIALS[i], CubeListBuilder.create()
                        .texOffs(GLASS_U[i], GLASS_U[i] == 0 ? 16 : 0).addBox(-0.65f, -3.6f, -0.65f, 1.3f, 1.8f, 1.3f), at);
            }
            // three fingers out of the fist, spread across the hand, curling back at the tips
            for (int i = 0; i < 3; i++) {
                PartDefinition finger = arm.addOrReplaceChild("finger" + i, CubeListBuilder.create().texOffs(48, 16).addBox(-0.5f, 0, -0.5f, 1, 3, 1),
                        PartPose.offsetAndRotation(mid + (i - 1) * 1.4f, 10.3f, -0.9f + i * 0.9f, 0.25f, 0, (i - 1) * f * 0.12f));
                finger.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(48, 23).addBox(-0.5f, 0, -0.5f, 1, 3, 1),
                        PartPose.offsetAndRotation(0, 2.7f, 0, 0.55f, 0, 0));
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    static boolean holding(LivingEntity e) {
        return e.getMainHandItem().is(ModItems.HANDMADE_CLAW.get());
    }

    static ModelPart parts() {
        if (parts == null) parts = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
        return parts;
    }

    /** The claw on an arm already posed into `pose` (the arm part's own frame). */
    static void draw(PoseStack pose, MultiBufferSource buffers, int light, boolean right, int aura, boolean firstPerson) {
        ModelPart arm = parts().getChild(right ? "right" : "left");
        // the first-person arm is turned so its back (+z) faces the side of the screen: there the fingers curl the other
        // way, so the tips point ahead, at what you're clawing
        float curl = firstPerson ? -1 : 1;
        for (int i = 0; i < 3; i++) {
            ModelPart finger = arm.getChild("finger" + i);
            finger.xRot = curl * 0.25f;
            finger.getChild("tip").xRot = curl * 0.55f;
        }
        // a live serum's glass is drawn once, glowing, not over its own solid copy (the two fight on the same faces)
        boolean tri = (aura & 8) != 0;
        boolean[] lit = new boolean[3];
        for (int i = 0; i < 3; i++) {
            lit[i] = tri || (aura & (1 << i)) != 0;
            arm.getChild("glass_" + VIALS[i]).visible = !lit[i];
        }
        arm.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ClawRenderer.EXTRAS)), light, OverlayTexture.NO_OVERLAY);
        // fetched after: asking for another buffer ends the one before
        var glow = buffers.getBuffer(RenderType.eyes(ClawRenderer.EXTRAS));
        pose.pushPose();
        arm.translateAndRotate(pose);
        for (int i = 0; i < 3; i++) {
            ModelPart glass = arm.getChild("glass_" + VIALS[i]);
            glass.visible = true;
            if (lit[i]) glass.render(pose, glow, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }

    /** Third person: on whichever arm is the main one. */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (!holding(p) || p.isInvisible()) return;
            boolean right = p.getMainArm() == HumanoidArm.RIGHT;
            pose.pushPose();
            (right ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(pose);
            if (p.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM) pose.translate((right ? 0.5f : -0.5f) / 16f, 0, 0);
            draw(pose, buffers, light, right, ClawClient.AURAS.getOrDefault(p.getId(), 0), false);
            pose.popPose();
        }
    }

    /** First person: the bare arm the way vanilla draws an empty hand, wearing the claw. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isInvisible() || event.getHand() != InteractionHand.MAIN_HAND || !holding(p)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer renderer)) return;
        event.setCanceled(true);
        boolean right = p.getMainArm() == HumanoidArm.RIGHT;
        float f = right ? 1 : -1, swing = event.getSwingProgress(), equip = event.getEquipProgress(), sq = Mth.sqrt(swing);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // ItemInHandRenderer.renderPlayerArm
        pose.translate(f * (-0.3F * Mth.sin(sq * (float) Math.PI) + 0.64F), 0.4F * Mth.sin(sq * ((float) Math.PI * 2F)) - 0.6F + equip * -0.6F,
                -0.4F * Mth.sin(swing * (float) Math.PI) - 0.72F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float a = Mth.sin(swing * swing * (float) Math.PI), b = Mth.sin(sq * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(f * b * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * a * -20.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        if (right) renderer.renderRightHand(pose, buffers, light, p);
        else renderer.renderLeftHand(pose, buffers, light, p);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        (right ? model.rightArm : model.leftArm).translateAndRotate(pose);
        if (p.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM) pose.translate((right ? 0.5f : -0.5f) / 16f, 0, 0);
        draw(pose, buffers, light, right, ClawClient.AURAS.getOrDefault(p.getId(), 0), true);
        pose.popPose();
    }
}
