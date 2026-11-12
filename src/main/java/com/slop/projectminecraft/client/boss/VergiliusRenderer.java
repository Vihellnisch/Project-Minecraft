package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.Vergilius;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Vergilius: the user's skin (grey hair, the scar, red eyes, the black pinstripe suit), his eyes lit on top of it, and what
 * a skin can't hold: the white-hot gladius in his right hand, and in E.G.O (Lavacrum Sanguinis) the golden laurel crown with
 * its thorns, the spiked hair, blood running down from under the crown, the red plated pauldron on his left shoulder and the
 * blood cape over his shoulders, its ragged ends dripping. The gladius turns to a blade of blood.
 */
public class VergiliusRenderer extends HumanoidMobRenderer<Vergilius, PlayerModel<Vergilius>> {
    static final ResourceLocation SKIN = ProjectMinecraft.id("textures/entity/vergilius/vergilius.png"),
            EGO_SKIN = ProjectMinecraft.id("textures/entity/vergilius/vergilius_ego.png"),
            EYES = ProjectMinecraft.id("textures/entity/vergilius/vergilius_eyes.png"),
            EXTRAS = ProjectMinecraft.id("textures/entity/vergilius/vergilius_extras.png");
    public static final ModelLayerLocation EXTRAS_LAYER = new ModelLayerLocation(ProjectMinecraft.id("vergilius"), "extras");
    static final float KNEEL_DROP = 0.58f, KNEEL_LEGS = 1.35f;

    public VergiliusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new Extras(this, ctx.bakeLayer(EXTRAS_LAYER)));
    }

    @Override
    public ResourceLocation getTextureLocation(Vergilius v) {
        return v.ego() ? EGO_SKIN : SKIN;
    }

    @Override
    public void render(Vergilius v, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        if (v.bodyPose() == Vergilius.POSE_KNEEL) pose.translate(0, -KNEEL_DROP, 0);
        super.render(v, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    /** His stance (the free hand in his pocket, the gladius held low) and the poses of his skills. */
    static final class Model extends PlayerModel<Vergilius> {
        /** Where the gladius points, about the hand (radians, x): set with the pose, read by the layer. */
        float bladePitch;

        Model(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(Vergilius v, float limb, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(v, limb, limbAmount, age, headYaw, headPitch);
            // the free hand hangs and swings as anyone's does (vanilla's pose); the right holds the gladius low and forward
            rightArm.xRot = -0.25f + Mth.cos(limb * 0.6662f + Mth.PI) * 0.5f * limbAmount + Mth.sin(age * 0.07f) * 0.03f;
            rightArm.zRot = 0.08f;
            bladePitch = -0.75f;
            switch (v.bodyPose()) {
                case Vergilius.POSE_THRUST -> {
                    // arm out straight, the blade a line with it
                    rightArm.xRot = -1.55f;
                    rightArm.yRot = -0.1f;
                    body.yRot = 0.35f;
                    bladePitch = 0;
                }
                case Vergilius.POSE_SLASH -> {
                    float k = (age % 8) / 8f;
                    rightArm.xRot = -2.1f + k * 2.3f;
                    rightArm.yRot = -0.7f + k * 1.4f;
                    body.yRot = -0.4f + k * 0.8f;
                    bladePitch = -1.2f;
                }
                case Vergilius.POSE_COUNTER -> {
                    // the blade across his body, waiting
                    rightArm.xRot = -1.1f;
                    rightArm.yRot = 0.75f;
                    bladePitch = -1.45f;
                    body.yRot = -0.2f;
                    head.xRot = 0.15f;
                }
                case Vergilius.POSE_DASH -> {
                    body.xRot = 0.5f;
                    rightArm.xRot = 1.0f;
                    rightArm.zRot = 0.35f;
                    bladePitch = -1.9f;
                    leftArm.xRot = 0.6f;
                }
                case Vergilius.POSE_OVERHEAD -> {
                    rightArm.xRot = -2.9f;
                    rightArm.zRot = -0.1f;
                    bladePitch = -0.3f;
                    leftArm.xRot = -0.4f;
                    leftArm.zRot = -0.3f;
                    leftArm.yRot = 0;
                }
                case Vergilius.POSE_KNEEL -> {
                    // on both knees, legs folded back under him (as the Claw and Roland kneel)
                    rightLeg.xRot = KNEEL_LEGS;
                    leftLeg.xRot = KNEEL_LEGS;
                    body.xRot = 0.25f;
                    head.xRot = 0.5f;
                    rightArm.xRot = -0.35f;
                    bladePitch = -1.6f;
                }
                case Vergilius.POSE_STILL -> {
                    // Funeral: standing perfectly still, head bowed, the blade down at his side
                    head.xRot = 0.35f;
                    rightArm.xRot = 0;
                    bladePitch = -0.1f;
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

    // ------------------------------------------------------------------ the extras

    /*
     * Sheet (vergilius_extras.png): gold leaves (0,0), thorns (16,0), hair (24,0), pauldron plates (32,0) 32x8, the gladius blade
     * (32,8), the blood blade (48,8), grip (0,8), guard (8,8), the blade's white-hot core (24,8) and the blood blade's (16,8),
     * blood (0,16) to the bottom of the sheet.
     */

    /**
     * The gladius as the art draws it, built along +y from the hand: a short dark grip and no guard, then the blade, which
     * flares into an angular swell just past the grip, pinches in to a waist, swells a little again and runs in one long
     * taper to a needle point. A raised ridge down its middle makes it faceted. `blood` = the E.G.O blade (the same shape,
     * bigger, with barbs of blood off its edges); `grow` > 0 = the glow round it (blade only).
     * Profile: {width, length} per step, widths across the edges.
     */
    static final float[][] PROFILE = {{0.9f, 0.5f}, {1.9f, 0.7f}, {2.9f, 1.0f}, {2.3f, 0.7f}, {1.5f, 0.8f}, {1.2f, 0.9f}, {1.6f, 0.7f},
            {1.95f, 1.0f}, {1.75f, 1.6f}, {1.45f, 1.6f}, {1.15f, 1.6f}, {0.85f, 1.5f}, {0.55f, 1.3f}, {0.25f, 1.1f}};

    static void blade(PartDefinition root, String name, boolean blood, float grow) {
        CubeDeformation d = new CubeDeformation(grow);
        CubeListBuilder b = CubeListBuilder.create();
        int u = blood ? 48 : 32, v = 8;
        float k = blood ? 1.3f : 1.1f;
        if (grow == 0) {
            // the grip, wrapped dark, a small cap at its end
            b.texOffs(0, 8).addBox(-0.55f, -1.4f, -0.55f, 1.1f, 0.6f, 1.1f)
                    .texOffs(0, 8).addBox(-0.45f, -0.8f, -0.45f, 0.9f, 2.5f, 0.9f);
        }
        float y = 1.7f, t = blood ? 0.6f : 0.45f;
        for (float[] s : PROFILE) {
            float w = s[0] * k, len = s[1] * k;
            b.texOffs(u, v).addBox(-w / 2, y, -t / 2, w, len, t, d);
            // the ridge down the middle, standing proud of both flats
            if (grow == 0 && w > 0.4f) b.texOffs(u, v + 4).addBox(-w * 0.2f, y, -t / 2 - 0.15f, w * 0.4f, len, t + 0.3f);
            y += len;
        }
        if (blood && grow == 0) {
            // ragged barbs of blood off the edges
            b.texOffs(u, v).addBox(1.1f, 4.5f, -0.2f, 0.6f, 1.4f, 0.4f).texOffs(u, v).addBox(-1.7f, 8.0f, -0.2f, 0.6f, 1.6f, 0.4f)
                    .texOffs(u, v).addBox(0.9f, 11.5f, -0.2f, 0.5f, 1.2f, 0.4f);
        }
        root.addOrReplaceChild(name, b, PartPose.ZERO);
    }

    /**
     * A gold laurel leaf on the crown's band, at angle `a` round the head (0 = the front) on side `side`, lying along the band
     * and pointing toward the front as laurel does, its tip raised by `lift`; `y` = height on the head (the top is -8).
     */
    static void leaf(PartDefinition crown, String name, float a, float side, float y, float lift) {
        float r = 4.35f;
        float yaw = (float) Math.atan2(side * Mth.cos(a), Mth.sin(a));
        crown.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(-0.45f, -0.3f, -2.0f, 0.9f, 0.6f, 2.0f),
                PartPose.offsetAndRotation(side * Mth.sin(a) * r, y, -Mth.cos(a) * r, -lift, yaw, 0));
    }

    public static LayerDefinition createExtras() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        blade(root, "gladius", false, 0);
        blade(root, "gladius_glow", false, 0.08f);
        blade(root, "blood_blade", true, 0);
        blade(root, "blood_glow", true, 0.1f);

        // the laurel crown: a thin gold band round the head just above the brow, two rows of leaves along it on each side, all
        // pointing forward, the two branches stopping short of meeting over the middle of the forehead
        PartDefinition crown = root.addOrReplaceChild("crown", CubeListBuilder.create(), PartPose.ZERO);
        for (int k = 0; k < 4; k++) {
            float a = k * Mth.HALF_PI;
            crown.addOrReplaceChild("band" + k, CubeListBuilder.create().texOffs(0, 0).addBox(-4.3f, -0.25f, -0.25f, 8.6f, 0.5f, 0.5f),
                    PartPose.offsetAndRotation(Mth.sin(a) * 4.25f, -6.4f, -Mth.cos(a) * 4.25f, 0, -a, 0));
        }
        int n = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 7; i++) {
                float a = 0.42f + i * 0.4f;
                leaf(crown, "leaf" + n++, a, side, -6.75f, 0.55f);
                leaf(crown, "leaf" + n++, a + 0.2f, side, -6.05f, -0.25f);
            }
        }
        // thorns through the band, short and dark
        PartDefinition thorns = root.addOrReplaceChild("thorns", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 7; i++) {
            float a = 0.8f + i * (Mth.TWO_PI - 1.6f) / 6;
            thorns.addOrReplaceChild("t" + i, CubeListBuilder.create().texOffs(16, 0).addBox(-0.2f, -1.4f, -0.2f, 0.4f, 1.4f, 0.4f),
                    PartPose.offsetAndRotation(Mth.sin(a) * 4.5f, -6.8f, -Mth.cos(a) * 4.5f, -Mth.cos(a) * 0.6f, 0, -Mth.sin(a) * 0.6f));
        }
        // the hair, roughed up in E.G.O: a few short dark spikes from under the crown at the sides and the back
        PartDefinition hair = root.addOrReplaceChild("hair", CubeListBuilder.create(), PartPose.ZERO);
        float[][] spikes = {{-4.1f, -4.6f, 1.2f, 0.2f, -0.8f}, {4.1f, -4.6f, 1.2f, 0.2f, 0.8f}, {-4.1f, -2.8f, 2.4f, 0.4f, -0.9f},
                {4.1f, -2.8f, 2.4f, 0.4f, 0.9f}, {-2.2f, -5.2f, 4.1f, 0.8f, -0.3f}, {1.8f, -4.0f, 4.1f, 0.9f, 0.3f}};
        int h = 0;
        for (float[] sp : spikes) hair.addOrReplaceChild("h" + h++, CubeListBuilder.create().texOffs(24, 0).addBox(-0.5f, -1.8f, -0.5f, 1.0f, 1.8f, 1.0f),
                PartPose.offsetAndRotation(sp[0], sp[1], sp[2], sp[3], 0, sp[4]));

        // the pauldron, on the left shoulder (left arm space, wide: x -1..3, y -2..10): three overlapping red plates stepping
        // down over the shoulder, and a plate over the left of his chest (body space)
        root.addOrReplaceChild("pauldron", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-1.9f, -3.1f, -2.9f, 5.6f, 2.2f, 5.8f)
                .texOffs(32, 0).addBox(-1.4f, -1.3f, -3.0f, 5.5f, 2.0f, 6.0f)
                .texOffs(32, 0).addBox(-0.8f, 0.5f, -3.05f, 5.2f, 1.9f, 6.1f), PartPose.ZERO);
        root.addOrReplaceChild("chest_plate", CubeListBuilder.create()
                .texOffs(32, 0).addBox(0.3f, -0.4f, -2.75f, 4.2f, 4.2f, 0.7f)
                .texOffs(32, 0).addBox(1.2f, -1.1f, -2.6f, 3.0f, 1.2f, 3.0f), PartPose.ZERO);

        // the blood cape (body space: x -4..4, y 0..12, z -2..2): a mantle over both shoulders, then the cloak down his back
        // in two hanging lengths, ragged strips and drips at its hem
        root.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0, 16).addBox(-8.6f, -1.1f, -2.7f, 17.2f, 1.6f, 5.6f), PartPose.ZERO);
        PartDefinition cape = root.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 24).addBox(-8.4f, 0, 0, 16.8f, 9, 0.7f),
                PartPose.offset(0, 0, 2.5f));
        PartDefinition lower = cape.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(0, 36).addBox(-7.8f, 0, 0, 15.6f, 7, 0.6f),
                PartPose.offset(0, 9, 0.05f));
        float[][] strips = {{-7.6f, 7, 1.8f}, {-5.4f, 4.5f, 1.4f}, {-3.6f, 8.5f, 2.0f}, {-1.2f, 5.5f, 1.6f}, {0.8f, 9.5f, 1.8f}, {2.9f, 4f, 1.5f},
                {4.6f, 7.5f, 1.8f}, {6.5f, 5f, 1.3f}};
        int sN = 0;
        for (float[] s : strips) lower.addOrReplaceChild("s" + sN++, CubeListBuilder.create().texOffs(36 + (sN % 3) * 8, 46).addBox(0, 0, 0, s[2], s[1], 0.5f),
                PartPose.offset(s[0], 6.8f, 0.05f));
        // on each arm a fold of the cape round the shoulder, ragged at its edge (arm space, wide)
        root.addOrReplaceChild("drape_right", CubeListBuilder.create().texOffs(0, 46).addBox(-3.6f, -2.6f, -2.6f, 5.2f, 5.4f, 5.2f)
                .texOffs(24, 46).addBox(-3.55f, 2.8f, -1.2f, 1.3f, 2.4f, 2.4f).texOffs(24, 46).addBox(-3.55f, 2.8f, 1.0f, 1.3f, 1.4f, 1.4f), PartPose.ZERO);
        root.addOrReplaceChild("drape_left", CubeListBuilder.create().texOffs(0, 46).addBox(-1.6f, -2.6f, -2.6f, 5.2f, 5.4f, 5.2f)
                .texOffs(24, 46).addBox(2.25f, 2.8f, -1.0f, 1.3f, 2.6f, 2.2f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The baked extra parts (shared by his renderer and the player layer). */
    static final class Parts {
        final ModelPart gladius, gladiusGlow, bloodBlade, bloodGlow, crown, thorns, hair, pauldron, chestPlate, mantle, cape, lower,
                drapeRight, drapeLeft;

        Parts(ModelPart root) {
            gladius = root.getChild("gladius");
            gladiusGlow = root.getChild("gladius_glow");
            bloodBlade = root.getChild("blood_blade");
            bloodGlow = root.getChild("blood_glow");
            crown = root.getChild("crown");
            thorns = root.getChild("thorns");
            hair = root.getChild("hair");
            pauldron = root.getChild("pauldron");
            chestPlate = root.getChild("chest_plate");
            mantle = root.getChild("mantle");
            cape = root.getChild("cape");
            lower = cape.getChild("lower");
            drapeRight = root.getChild("drape_right");
            drapeLeft = root.getChild("drape_left");
        }
    }

    static final class Extras extends RenderLayer<Vergilius, PlayerModel<Vergilius>> {
        final Parts x;

        Extras(RenderLayerParent<Vergilius, PlayerModel<Vergilius>> parent, ModelPart root) {
            super(parent);
            x = new Parts(root);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Vergilius v, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (v.isInvisible()) return;
            PlayerModel<Vergilius> m = getParentModel();
            // the eyes, lit (brighter while the Red Gaze flares)
            int a = (int) (255 * Mth.clamp(0.45f + 0.55f * v.gaze(), 0, 1));
            m.head.render(pose, buffers.getBuffer(RenderType.eyes(EYES)), light, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, a, a, a));
            float pitch = m instanceof Model mm ? mm.bladePitch : -0.75f;
            if (v.ego()) drawEgo(m, x, pose, buffers, light, limbAmount, age);
            // beaten, he's let go of it (it lies on the ground)
            if (v.state() != Vergilius.RETREAT) drawBlade(m, x, pose, buffers, v.ego(), pitch);
        }
    }

    /** The gladius in his right hand (or a player's): the blade lit white-hot (or blood), with a glow round it. */
    static void drawBlade(PlayerModel<?> m, Parts x, PoseStack pose, MultiBufferSource buffers, boolean blood, float pitch) {
        pose.pushPose();
        m.rightArm.translateAndRotate(pose);
        pose.translate(-1 / 16f, 10.2f / 16f, -0.4f / 16f);
        pose.mulPose(com.mojang.math.Axis.XP.rotation(pitch));
        // turned on its own axis so the edges face up and down, the flats to the sides, as a blade is held
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        pose.scale(0.9f, 0.9f, 0.9f);
        ModelPart blade = blood ? x.bloodBlade : x.gladius, glow = blood ? x.bloodGlow : x.gladiusGlow;
        blade.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        glow.render(pose, buffers.getBuffer(RenderType.eyes(EXTRAS)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, 90, 90, 90));
        pose.popPose();
    }

    /** Lavacrum Sanguinis: the crown, thorns and hair on the head, the pauldron and the cape; the cape sways as he moves. */
    static void drawEgo(PlayerModel<?> m, Parts x, PoseStack pose, MultiBufferSource buffers, int light, float limbAmount, float age) {
        var solid = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        m.head.translateAndRotate(pose);
        x.crown.render(pose, solid, light, ov);
        x.thorns.render(pose, solid, light, ov);
        x.hair.render(pose, solid, light, ov);
        pose.popPose();
        pose.pushPose();
        m.body.translateAndRotate(pose);
        x.mantle.render(pose, solid, light, ov);
        x.chestPlate.render(pose, solid, light, ov);
        float sway = Mth.sin(age * 0.09f);
        x.cape.xRot = 0.1f + limbAmount * 0.55f + sway * 0.04f;
        x.lower.xRot = 0.06f + limbAmount * 0.4f + Mth.sin(age * 0.09f + 1.1f) * 0.06f;
        int i = 0;
        for (ModelPart s : x.lower.getAllParts().toList()) {
            if (s == x.lower) continue;
            s.xRot = 0.05f + limbAmount * 0.3f + Mth.sin(age * 0.12f + i++ * 0.9f) * 0.08f;
        }
        x.cape.render(pose, solid, light, ov);
        pose.popPose();
        pose.pushPose();
        m.rightArm.translateAndRotate(pose);
        x.drapeRight.render(pose, solid, light, ov);
        pose.popPose();
        pose.pushPose();
        m.leftArm.translateAndRotate(pose);
        x.drapeLeft.render(pose, solid, light, ov);
        x.pauldron.render(pose, solid, light, ov);
        pose.popPose();
    }
}
