package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.boss.RolandKin;
import com.slop.projectminecraft.client.arsenal.GloveLayer;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Roland's company, each drawn its own way: the byproducts as flesh-hounds, Angelica as a woman of white mist with
 * hair to her knees and white weapons, the Images of the Bygones as pale ghosts, and his weapons hovering in smoke or
 * nailed point-down into the floor.
 */
public class RolandKinRenderer extends EntityRenderer<RolandKin> {
    static final ResourceLocation ANGELICA = ProjectMinecraft.id("textures/entity/roland/angelica.png");
    static final ResourceLocation MIST = ProjectMinecraft.id("textures/entity/roland/angelica_mist.png");
    static final ResourceLocation HAIR = ProjectMinecraft.id("textures/entity/roland/angelica_hair.png");
    static final ResourceLocation BYGONE = ProjectMinecraft.id("textures/entity/roland/bygone.png");
    public static final ModelLayerLocation HAIR_LAYER = new ModelLayerLocation(ProjectMinecraft.id("angelica_hair"), "main");
    static final float[] WHITE = {1, 1, 1, 0.85f};

    private final Hound hound;
    private final Spirit spirit;

    public RolandKinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        hound = new Hound(ctx);
        spirit = new Spirit(ctx);
        shadowRadius = 0.5f;
    }

    @Override
    public void render(RolandKin k, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        switch (k.kind()) {
            case RolandKin.BYPRODUCT -> hound.render(k, yaw, partial, pose, buffers, light);
            case RolandKin.ANGELICA, RolandKin.IMAGE -> spirit.render(k, yaw, partial, pose, buffers, light);
            case RolandKin.WEAPON -> weapon(k, partial, pose, buffers, light);
            default -> {}
        }
        super.render(k, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RolandKin k) {
        return k.kind() == RolandKin.ANGELICA ? ANGELICA : BYGONE;
    }

    /** A weapon in the air, upright and turning; or nailed, point down, half its blade in the floor. */
    private void weapon(RolandKin k, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        int w = k.variant();
        BakedModel model = GloveLayer.model(w);
        if (model == null) return;
        boolean gun = w == FuriosoScript.PISTOLS || w == FuriosoScript.SHOTGUN || w == FuriosoScript.GAUNTLETS;
        float t = k.tickCount + partial;
        pose.pushPose();
        if (k.nailed()) {
            pose.translate(0, gun ? 0.4 : 0.55, 0);
            pose.mulPose(Axis.YP.rotationDegrees(k.getId() * 47 % 360));
            // melee blades run up the model's diagonal: turn it to point straight down
            if (!gun) pose.mulPose(Axis.ZP.rotationDegrees(225));
            pose.scale(1.6f, 1.6f, 1.6f);
        } else {
            pose.translate(0, 0.9 + Mth.sin(t * 0.1f) * 0.08, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, k.yRotO, k.getYRot())));
            if (!gun) pose.mulPose(Axis.ZP.rotationDegrees(45));
            pose.scale(1.8f, 1.8f, 1.8f);
        }
        // his weapons as his distortion has them: black, wreathed in black mist
        GloveLayer.distorted = true;
        GloveLayer.mistTime = t;
        try {
            BakedModel dark = GloveLayer.model(w);
            GloveLayer.drawModel(pose, buffers, light, dark != null ? dark : model);
        } finally {
            GloveLayer.distorted = false;
        }
        pose.popPose();
    }

    /** Long hair, down her back to her knees, and a few strands loose over the shoulders. */
    public static LayerDefinition createHair() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -8.5f, 3.6f, 9, 22, 1.5f), PartPose.ZERO);
        root.addOrReplaceChild("sides", CubeListBuilder.create().texOffs(24, 0).addBox(-4.8f, -8.5f, -2, 1, 12, 6).addBox(3.8f, -8.5f, -2, 1, 12, 6), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The byproducts. */
    static final class Hound extends MobRenderer<RolandKin, ByproductModel> {
        Hound(EntityRendererProvider.Context ctx) {
            super(ctx, new ByproductModel(ctx.bakeLayer(ByproductModel.LAYER)), 0.8f);
        }

        @Override
        public ResourceLocation getTextureLocation(RolandKin k) {
            return RolandRenderer.BEAST;
        }

        @Override
        protected float getFlipDegrees(RolandKin k) {
            return 0;
        }

        @Override
        protected void scale(RolandKin k, PoseStack pose, float partial) {
            pose.scale(1.3f, 1.3f, 1.3f);
        }
    }

    /** Angelica and the Images: translucent, full-bright, Angelica white with her hair, the Images a pale grey-blue. */
    static final class Spirit extends HumanoidMobRenderer<RolandKin, PlayerModel<RolandKin>> {
        Spirit(EntityRendererProvider.Context ctx) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0f);
            addLayer(new Body(this));
            addLayer(new Hair(this, ctx.bakeLayer(HAIR_LAYER)));
            addLayer(new Weapons(this));
        }

        @Override
        public void render(RolandKin k, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            boolean angelica = k.kind() == RolandKin.ANGELICA;
            int w = angelica ? k.weapon() : -1;
            model.rightArmPose = w < 0 ? HumanoidModel.ArmPose.EMPTY : w == FuriosoScript.PISTOLS ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
            model.leftArmPose = w == FuriosoScript.AXE || (w >= 0 && FuriosoScript.pair(w)) ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
            pose.pushPose();
            // she drifts a hand's breadth off the floor
            if (angelica) pose.translate(0, 0.08 + Mth.sin((k.tickCount + partial) * 0.07f) * 0.04, 0);
            super.render(k, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }

        @Nullable
        @Override
        protected RenderType getRenderType(RolandKin k, boolean visible, boolean translucent, boolean glowing) {
            return null;
        }

        @Override
        public ResourceLocation getTextureLocation(RolandKin k) {
            return k.kind() == RolandKin.ANGELICA ? ANGELICA : BYGONE;
        }

        @Override
        protected float getFlipDegrees(RolandKin k) {
            return 0;
        }

        static float alpha(RolandKin k, float age) {
            if (k.kind() == RolandKin.ANGELICA) return k.fallen() ? 0.22f + 0.06f * Mth.sin(age * 0.2f) : 0.9f + 0.05f * Mth.sin(age * 0.1f);
            return 0.5f + 0.08f * Mth.sin(age * 0.15f + k.getId());
        }
    }

    static final class Body extends RenderLayer<RolandKin, PlayerModel<RolandKin>> {
        Body(RenderLayerParent<RolandKin, PlayerModel<RolandKin>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RolandKin k, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            float a = Spirit.alpha(k, age);
            boolean angelica = k.kind() == RolandKin.ANGELICA;
            int c = angelica ? FastColor.ARGB32.color((int) (a * 255), 250, 250, 255) : FastColor.ARGB32.color((int) (a * 255), 200, 210, 230);
            ResourceLocation tex = angelica ? MIST : BYGONE;
            // her shape into the depth buffer first, then the see-through pass culled: only her front surface shows (no back of
            // her head or her hair seen through her face). Translucent at full brightness, not the emissive type: shader packs
            // treat emissive as a light source and bloom her white.
            if (angelica && a >= 0.85f) {
                // fully there: solid, as any mob is (shader packs draw see-through entities far thinner than vanilla, and her pale
                // face washed out into the sky); the halo below keeps the mist about her
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutout(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFAFAFF);
            } else {
                com.slop.projectminecraft.client.render.FxRenderTypes.depthPrepass(buffers, tex,
                        vc -> getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c));
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentCull(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
            }
            if (!angelica) return;
            // a soft white haze around her, breathing
            pose.pushPose();
            float s = 1.08f + 0.03f * Mth.sin(age * 0.12f);
            pose.scale(s, s, s);
            pose.translate(0, (1 - s) * 0.75, 0);
            int halo = FastColor.ARGB32.color((int) (a * 70), 255, 255, 255);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentCull(MIST)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, halo);
            pose.popPose();
        }
    }

    static final class Hair extends RenderLayer<RolandKin, PlayerModel<RolandKin>> {
        private final ModelPart back, sides;

        Hair(RenderLayerParent<RolandKin, PlayerModel<RolandKin>> parent, ModelPart root) {
            super(parent);
            back = root.getChild("back");
            sides = root.getChild("sides");
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RolandKin k, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (k.kind() != RolandKin.ANGELICA) return;
            float a = Spirit.alpha(k, age);
            int c = FastColor.ARGB32.color((int) (a * 255), 255, 255, 255);
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            // the long fall of it sways behind her, not with her head
            pose.mulPose(Axis.XP.rotation(-getParentModel().head.xRot + 0.08f + Mth.sin(age * 0.06f) * 0.05f));
            var vc = buffers.getBuffer(RenderType.entityTranslucentCull(HAIR));
            back.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
            sides.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
            pose.popPose();
        }
    }

    /** Angelica's weapons, white as she is. */
    static final class Weapons extends RenderLayer<RolandKin, PlayerModel<RolandKin>> {
        Weapons(RenderLayerParent<RolandKin, PlayerModel<RolandKin>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RolandKin k, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (k.kind() != RolandKin.ANGELICA || k.fallen()) return;
            int w = k.weapon();
            if (w < 0) return;
            PlayerModel<RolandKin> m = getParentModel();
            GloveLayer.tint = WHITE;
            try {
                pose.pushPose();
                m.rightArm.translateAndRotate(pose);
                GloveLayer.held(pose, buffers, LightTexture.FULL_BRIGHT, w, true);
                pose.popPose();
                int left = FuriosoScript.pair(w) ? w : w == FuriosoScript.AXE ? FuriosoScript.MACE : -1;
                if (left >= 0) {
                    pose.pushPose();
                    m.leftArm.translateAndRotate(pose);
                    GloveLayer.held(pose, buffers, LightTexture.FULL_BRIGHT, left, false);
                    pose.popPose();
                }
            } finally {
                GloveLayer.tint = null;
            }
        }
    }
}
