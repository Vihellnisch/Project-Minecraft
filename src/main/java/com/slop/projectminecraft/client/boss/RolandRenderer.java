package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.boss.Roland;
import com.slop.projectminecraft.client.arsenal.GloveLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The Black Silence. Phase 1 in his suit with his face lost in shadow (the user's skin), phases 3 to 5 the settled
 * distortion: the dark ring where his head should be, smoke pouring off him, his weapons smoke-black. Phase 2 is the
 * beast, drawn by its own model. When he falls he kneels, Durandal down, and his face shows.
 */
public class RolandRenderer extends HumanoidMobRenderer<Roland, PlayerModel<Roland>> {
    static final ResourceLocation SKIN = ProjectMinecraft.id("textures/entity/roland/roland.png");
    static final ResourceLocation MISTY = ProjectMinecraft.id("textures/entity/roland/roland_misty.png");
    static final ResourceLocation FACE = ProjectMinecraft.id("textures/entity/roland/roland_face.png");
    static final ResourceLocation BEAST = ProjectMinecraft.id("textures/entity/roland/roland_beast.png");

    private final Beast beast;

    public RolandRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RolandModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
        addLayer(new Haze(this));
        addLayer(new Weapons(this));
        beast = new Beast(ctx);
    }

    static boolean dying(Roland r) {
        return r.deathTime > 0 || r.isDeadOrDying();
    }

    @Override
    public ResourceLocation getTextureLocation(Roland r) {
        if (dying(r)) return FACE;
        return r.phase() >= Roland.BOND ? MISTY : SKIN;
    }

    @Override
    public void render(Roland r, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (r.phase() == Roland.DISTORTED && !dying(r)) {
            beast.render(r, yaw, partial, pose, buffers, light);
            return;
        }
        int w = r.weapon();
        boolean gun = w == FuriosoScript.PISTOLS || w == FuriosoScript.SHOTGUN;
        model.rightArmPose = w < 0 ? HumanoidModel.ArmPose.EMPTY : gun ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = w >= 0 && FuriosoScript.pair(w) || r.offhand() >= 0 ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
        model.crouching = r.bodyPose() == Roland.POSE_CROUCH && !dying(r);
        pose.pushPose();
        // sinking to his knees as he falls
        if (dying(r)) pose.translate(0, -0.58 * Math.min(1, (r.deathTime + partial) / 12f), 0);
        // no red death flash over his long kneel: drawn as if unhurt (he's still dying: his health says so)
        int deathTime = r.deathTime, hurtTime = r.hurtTime;
        if (dying(r)) r.deathTime = r.hurtTime = 0;
        try {
            super.render(r, yaw, partial, pose, buffers, light);
        } finally {
            r.deathTime = deathTime;
            r.hurtTime = hurtTime;
        }
        pose.popPose();
    }

    /** No death flip: he goes down to one knee instead. */
    @Override
    protected float getFlipDegrees(Roland r) {
        return 0;
    }

    @Override
    protected RenderType getRenderType(Roland r, boolean visible, boolean translucent, boolean glowing) {
        // fallen to mist in the bond: only the haze layer shows him
        if (r.bodyPose() == Roland.POSE_MIST && !dying(r)) return null;
        return super.getRenderType(r, visible, translucent, glowing);
    }

    /** Poses on top of the usual walk and swing: hunched in his smoke, guarding, paralysed, kneeling. */
    static final class RolandModel extends PlayerModel<Roland> {
        RolandModel(ModelPart root) {
            super(root, true);
        }

        @Override
        public void setupAnim(Roland r, float limb, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(r, limb, limbAmount, age, headYaw, headPitch);
            if (dying(r)) {
                // down on his knees, shins flat on the floor behind him, head bowed, Durandal's point on the floor
                rightLeg.xRot = 1.5f;
                leftLeg.xRot = 1.5f;
                rightLeg.yRot = 0.08f;
                leftLeg.yRot = -0.08f;
                body.xRot = 0.35f;
                head.xRot = 0.7f;
                head.yRot = 0;
                rightArm.xRot = -0.5f;
                rightArm.zRot = 0.15f;
                leftArm.xRot = 0.2f;
            } else switch (r.bodyPose()) {
                case Roland.POSE_CROUCH -> {
                    // hunched over, hands up at his face
                    body.xRot = 0.65f;
                    head.xRot = 0.9f;
                    rightArm.xRot = -2.1f;
                    leftArm.xRot = -2.1f;
                    rightArm.yRot = -0.45f;
                    leftArm.yRot = 0.45f;
                }
                case Roland.POSE_GUARD -> {
                    rightArm.xRot = -1.25f;
                    rightArm.yRot = -0.7f;
                    leftArm.xRot = -1.25f;
                    leftArm.yRot = 0.7f;
                }
                case Roland.POSE_PARALYSED -> {
                    head.xRot = 0.8f + Mth.sin(age * 2.1f) * 0.05f;
                    rightArm.xRot = 0.1f;
                    leftArm.xRot = 0.1f;
                    rightArm.zRot = 0.1f + Mth.sin(age * 1.7f) * 0.05f;
                    leftArm.zRot = -0.1f;
                }
                default -> {}
            }
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
            jacket.copyFrom(body);
            hat.copyFrom(head);
        }
    }

    /**
     * The settled distortion's smoke: the body again, a little larger, in translucent black, breathing. When he's
     * fallen to mist it's all there is of him.
     */
    static final class Haze extends RenderLayer<Roland, PlayerModel<Roland>> {
        Haze(RenderLayerParent<Roland, PlayerModel<Roland>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Roland r, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (r.phase() < Roland.BOND && r.bodyPose() != Roland.POSE_MIST) return;
            boolean mist = r.bodyPose() == Roland.POSE_MIST;
            float a = mist ? 0.35f + 0.1f * Mth.sin(age * 0.2f) : 0.28f + 0.06f * Mth.sin(age * 0.15f);
            pose.pushPose();
            float s = mist ? 1.0f : 1.07f + 0.02f * Mth.sin(age * 0.3f);
            pose.scale(s, s, s);
            pose.translate(0, (1 - s) * 0.75, 0);
            int c = FastColor.ARGB32.color((int) (a * 255), 18, 18, 22);
            // culled: only the shell's outside, not its inside seen through the front of it
            if (mist) com.slop.projectminecraft.client.render.FxRenderTypes.depthPrepass(buffers, MISTY,
                    vc -> getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, c));
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentCull(MISTY)), light, OverlayTexture.NO_OVERLAY, c);
            pose.popPose();
        }
    }

    /** The workshop weapons in his hands; once he's distorted they're wrapped in smoke. */
    static final class Weapons extends RenderLayer<Roland, PlayerModel<Roland>> {
        Weapons(RenderLayerParent<Roland, PlayerModel<Roland>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Roland r, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (r.bodyPose() == Roland.POSE_MIST && !dying(r)) return;
            int w = r.weapon(), off = r.offhand();
            if (w < 0 && off < 0) return;
            PlayerModel<Roland> m = getParentModel();
            GloveLayer.distorted = r.phase() >= Roland.BOND && !dying(r);
            GloveLayer.mistTime = age;
            try {
                if (w >= 0) {
                    pose.pushPose();
                    m.rightArm.translateAndRotate(pose);
                    GloveLayer.held(pose, buffers, light, w, true);
                    pose.popPose();
                }
                int left = w >= 0 && FuriosoScript.pair(w) ? w : off;
                if (left >= 0) {
                    pose.pushPose();
                    m.leftArm.translateAndRotate(pose);
                    GloveLayer.held(pose, buffers, light, left, false);
                    pose.popPose();
                }
            } finally {
                GloveLayer.distorted = false;
            }
        }
    }

    /** Phase 2: the beast. */
    static final class Beast extends MobRenderer<Roland, RolandBeastModel> {
        Beast(EntityRendererProvider.Context ctx) {
            super(ctx, new RolandBeastModel(ctx.bakeLayer(RolandBeastModel.LAYER)), 3.2f);
        }

        @Override
        public ResourceLocation getTextureLocation(Roland r) {
            return BEAST;
        }

        @Override
        protected void scale(Roland r, PoseStack pose, float partial) {
            pose.scale(1.6f, 1.6f, 1.6f);
        }
    }
}
