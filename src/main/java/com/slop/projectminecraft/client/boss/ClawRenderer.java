package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.Claw;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.slop.projectminecraft.client.CinematicCamera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Baral, the Claw: the user's skin (a coat over a suit, the mask of bark with holes in it, the rusted prosthetic left arm),
 * plus what a skin can't hold: the three vial antennae (green on his head, blue on his shoulder, orange at the elbow,
 * each lit while its serum is live) and the plated claw with its three blades. Once he breaks, the claw and vials are
 * gone. Also used to draw him beside a player he drags through Serum W (ClawWarpClient).
 */
public class ClawRenderer extends HumanoidMobRenderer<Claw, PlayerModel<Claw>> {
    static final ResourceLocation SKIN = ProjectMinecraft.id("textures/entity/claw/claw.png");
    static final ResourceLocation EXTRAS = ProjectMinecraft.id("textures/entity/claw/claw_extras.png");
    /** Beaten: torn and bloodied, the arm wrecked; then the mask split open too. */
    static final ResourceLocation BROKEN = ProjectMinecraft.id("textures/entity/claw/claw_broken.png"),
            CRACKED = ProjectMinecraft.id("textures/entity/claw/claw_cracked.png");
    /** The rematch: the arm and the claw polished new, the vials stronger, eyes burning behind the mask. */
    static final ResourceLocation REBUILT = ProjectMinecraft.id("textures/entity/claw/claw_rebuilt.png"),
            EXTRAS_REBUILT = ProjectMinecraft.id("textures/entity/claw/claw_extras_rebuilt.png"),
            EYES = ProjectMinecraft.id("textures/entity/claw/claw_eyes.png");
    /** How much bigger the rebuilt Claw's vials are. */
    static final float REBUILT_VIALS = 1.25f;
    /** Down on his knees: how far the body drops with both legs folded back under him. */
    static final float KNEEL_DROP = 0.58f, KNEEL_LEGS = 1.35f;
    public static final ModelLayerLocation EXTRAS_LAYER = new ModelLayerLocation(ProjectMinecraft.id("claw"), "extras");

    public ClawRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ClawModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new Extras(this, ctx.bakeLayer(EXTRAS_LAYER)));
    }

    @Override
    public ResourceLocation getTextureLocation(Claw c) {
        if (!c.broken()) return c.rebuilt() ? REBUILT : SKIN;
        return c.exit(0) >= Claw.R_MASK ? CRACKED : BROKEN;
    }

    @Override
    public void render(Claw c, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = c.entrance(partial), x = c.exit(partial);
        if (t < Claw.ARRIVE_TICKS || x >= 0) ClawEntrance.ensure(c);
        if (x >= 0) {
            renderExit(c, x, yaw, partial, pose, buffers, light);
            return;
        }
        // still entirely inside the rift: nothing to see, not even a shadow (drawn after this returns)
        shadowRadius = t < Claw.E_BODY ? 0 : 0.5f;
        if (t < Claw.E_CLAW) return;
        pose.pushPose();
        // coming out: he's further back, behind the rift's plane (ClawEntrance draws the void in front of what's left)
        float back = back(t);
        if (back > 0) {
            Vec3 f = Vec3.directionFromRotation(0, c.getYRot());
            pose.translate(-f.x * back, 0, -f.z * back);
        }
        byte p = c.bodyPose();
        // down on one knee
        if (p == Claw.POSE_KNEEL || p == Claw.POSE_GROGGY) pose.translate(0, -KNEEL_DROP, 0);
        super.render(c, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    /** Beaten: down on a knee and back up, then limping backwards into the rift behind him until it has all of him. */
    private void renderExit(Claw c, float x, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float back = exitBack(x);
        shadowRadius = 0.5f * Mth.clamp(1 - (back - 0.5f) / 0.6f, 0, 1);
        // all the way through the rift's plane: the void can't hide him from every side, so he's simply gone
        if (x >= Claw.R_SNAP - 2 || back > 1.15f) return;
        pose.pushPose();
        Vec3 f = Vec3.directionFromRotation(0, c.getYRot());
        pose.translate(-f.x * back, -KNEEL_DROP * kneel(x), -f.z * back);
        super.render(c, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    /** 0..1: how far down on his knee he is. */
    static float kneel(float x) {
        return CinematicCamera.ease(x, Claw.R_KNEEL, Claw.R_KNEEL + 5) - CinematicCamera.ease(x, Claw.R_RISE, Claw.R_RISE + 10);
    }

    /** How far behind where he was beaten he is: knocked back a step, then backing into the rift. */
    static float exitBack(float x) {
        return 0.15f * CinematicCamera.ease(x, 0, 6) + 1.95f * CinematicCamera.ease(x, Claw.R_BACK, Claw.R_SNAP - 4);
    }

    /** How far behind his final spot he still is at entrance time t: the claw, the leg, then all of him come through. */
    static float back(float t) {
        if (t >= Claw.E_OUT) return 0;
        if (t < Claw.E_GRIP) return Mth.lerp(CinematicCamera.ease(t, Claw.E_CLAW, Claw.E_GRIP), 1.75f, 0.95f);
        if (t < Claw.E_LEG) return 0.95f;
        if (t < Claw.E_BODY) return Mth.lerp(CinematicCamera.ease(t, Claw.E_LEG, Claw.E_STEP), 0.95f, 0.72f);
        return Mth.lerp(CinematicCamera.ease(t, Claw.E_BODY, Claw.E_OUT), 0.72f, 0);
    }

    /** His poses on top of the walk: the swipe, the thrust, the injection, kneeling, groggy, the dash. */
    static final class ClawModel extends PlayerModel<Claw> {
        ClawModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(Claw c, float limb, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(c, limb, limbAmount, age, headYaw, headPitch);
            // at rest the claw hangs a little forward, the other hand behind his back (the art)
            leftArm.xRot = -0.08f + Mth.cos(age * 0.08f) * 0.03f;
            leftArm.zRot = -0.06f;
            rightArm.xRot = 0.45f;
            rightArm.zRot = -0.12f;
            switch (c.bodyPose()) {
                case Claw.POSE_SWIPE -> {
                    float k = (age % 10) / 10f;
                    leftArm.xRot = -2.2f + k * 2.6f;
                    leftArm.yRot = 0.6f - k * 1.2f;
                    body.yRot = 0.4f - k * 0.8f;
                }
                case Claw.POSE_THRUST -> {
                    leftArm.xRot = -1.6f;
                    leftArm.yRot = 0.1f;
                    body.yRot = -0.35f;
                }
                case Claw.POSE_INJECT -> {
                    // the right hand to the vials on the left arm, head down
                    head.xRot = 0.55f;
                    leftArm.xRot = -1.0f;
                    leftArm.yRot = 0.6f;
                    rightArm.xRot = -1.2f;
                    rightArm.yRot = -0.7f;
                    rightArm.zRot = 0;
                }
                case Claw.POSE_KNEEL, Claw.POSE_GROGGY -> {
                    // on both knees, legs folded back under him
                    rightLeg.xRot = KNEEL_LEGS;
                    leftLeg.xRot = KNEEL_LEGS;
                    body.xRot = 0.3f;
                    head.xRot = c.bodyPose() == Claw.POSE_GROGGY ? 0.8f + Mth.sin(age * 0.3f) * 0.05f : 0.45f;
                    leftArm.xRot = c.bodyPose() == Claw.POSE_GROGGY ? 0.2f : -0.6f;
                    rightArm.xRot = -0.4f;
                }
                case Claw.POSE_DASH -> {
                    body.xRot = 0.45f;
                    leftArm.xRot = 1.1f;
                    leftArm.zRot = -0.5f;
                    rightArm.xRot = 1.0f;
                    rightArm.zRot = 0.4f;
                }
                default -> {}
            }
            // in Serum W's scene: his claw locked round your throat, holding you up (0), the rush (1), diving in for you (2)
            if (c == ClawWarpClient.ghost) {
                int gp = ClawWarpClient.ghostPose;
                leftArm.xRot = (gp == 2 ? -1.55f : -2.05f) + Mth.sin(age * 0.6f) * 0.03f;
                leftArm.yRot = gp == 2 ? 0.1f : -0.25f;
                leftArm.zRot = 0;
                body.yRot = -0.15f;
                head.xRot = 0.1f;
                head.yRot = 0;
                rightArm.xRot = gp == 0 ? 0.1f : 0.9f;
                rightArm.zRot = 0.15f;
                if (gp > 0) {
                    // leaning into it, mid-stride
                    ClawWarpClient.lean(this, 0.4f);
                    float stride = Mth.sin(age * 1.6f);
                    rightLeg.xRot = -0.8f * stride;
                    leftLeg.xRot = 0.8f * stride;
                    head.xRot = -0.25f;
                }
            }
            float t = c.entrance(age - c.tickCount), ex = c.exit(age - c.tickCount);
            boolean entering = t < Claw.ARRIVE_TICKS;
            if (entering) enter(t, age);
            if (ex >= 0) leave(ex, age);
            // during the entrance, at first only the claw arm (the rest of him is too far back, and the rift's void can't
            // hide him from every angle); from the step on he's close enough behind it that the void does the hiding
            boolean all = !entering || t >= Claw.E_LEG;
            head.visible = body.visible = rightArm.visible = rightLeg.visible = leftLeg.visible = all;
            leftArm.visible = true;
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
            jacket.copyFrom(body);
            hat.copyFrom(head);
            leftSleeve.visible = leftArm.visible;
            rightSleeve.visible = rightArm.visible;
            leftPants.visible = leftLeg.visible;
            rightPants.visible = rightLeg.visible;
            jacket.visible = body.visible;
            hat.visible = head.visible;
        }

        /** The exit: thrown back by the last blow, down on his knees, the mask split, the injection, up again, limping backwards. */
        private void leave(float x, float age) {
            float tremble = Mth.sin(age * 1.7f) * 0.035f;
            head.yRot = 0;
            leftArm.yRot = rightArm.yRot = 0;
            if (x < Claw.R_KNEEL) {
                // the blow lands: head snaps back, arms flung out
                float k = CinematicCamera.ease(x, 0, 3) * (1 - CinematicCamera.ease(x, 3, Claw.R_KNEEL));
                body.xRot = -0.3f * k;
                head.xRot = -0.55f * k;
                leftLeg.xRot = -0.25f * k;
                rightLeg.xRot = 0.2f * k;
                leftArm.xRot = 0.5f * k;
                leftArm.zRot = -0.75f * k;
                rightArm.xRot = 0.2f;
                rightArm.zRot = 0.65f * k;
                return;
            }
            if (x < Claw.R_RISE) {
                // down on his knees, the stump hanging, the other hand braced on his thigh; the head hangs, then lifts to you
                float down = kneel(x);
                rightLeg.xRot = KNEEL_LEGS * down;
                leftLeg.xRot = KNEEL_LEGS * down;
                body.xRot = 0.35f * down;
                head.xRot = Mth.lerp(CinematicCamera.ease(x, Claw.R_LOOK, Claw.R_LOOK + 8), 0.85f, 0.05f);
                leftArm.xRot = 0.25f + tremble;
                leftArm.zRot = -0.15f + tremble;
                rightArm.xRot = -0.6f;
                rightArm.zRot = 0.1f;
                float inject = CinematicCamera.ease(x, Claw.R_INJECT - 4, Claw.R_INJECT) * (1 - CinematicCamera.ease(x, Claw.R_INJECT + 12, Claw.R_INJECT + 18));
                if (inject > 0) {
                    // the good hand to the stump of the shoulder vial, head turned to it
                    rightArm.xRot = Mth.lerp(inject, -0.6f, -1.3f);
                    rightArm.yRot = -0.75f * inject;
                    rightArm.zRot = 0.1f * (1 - inject);
                    head.xRot = Mth.lerp(inject, head.xRot, 0.5f);
                    head.yRot = 0.4f * inject;
                }
                return;
            }
            if (x < Claw.R_BACK) {
                // getting up
                float up = CinematicCamera.ease(x, Claw.R_RISE, Claw.R_RISE + 10);
                rightLeg.xRot = KNEEL_LEGS * (1 - up);
                leftLeg.xRot = KNEEL_LEGS * (1 - up) + 0.2f * up;
                body.xRot = Mth.lerp(up, 0.35f, 0.2f);
                head.xRot = 0.05f;
                leftArm.xRot = 0.2f + tremble;
                leftArm.zRot = -0.12f + tremble;
                rightArm.xRot = Mth.lerp(up, -0.6f, 0.25f);
                rightArm.zRot = 0.1f;
                return;
            }
            // backing into the rift: a dragging, uneven step, hunched, eyes on you to the last
            float phase = (x - Claw.R_BACK) * 0.35f;
            rightLeg.xRot = 0.4f * Mth.sin(phase);
            leftLeg.xRot = 0.18f - 0.18f * Mth.sin(phase);
            body.xRot = 0.22f;
            head.xRot = -0.05f;
            leftArm.xRot = 0.2f + 0.12f * Mth.sin(phase + 1) + tremble;
            leftArm.zRot = -0.12f + tremble;
            rightArm.xRot = 0.25f - 0.15f * Mth.sin(phase);
            rightArm.zRot = 0.1f;
        }

        /** The entrance: the claw reaching out and dragging the edge open, the step through, the lean, the head coming up. */
        private void enter(float t, float age) {
            float twitch = Mth.sin(age * 0.9f) * 0.04f;
            if (t < Claw.E_LEG) {
                // reaching straight out of the dark, then hooking the edge and pulling it to his left
                float grip = CinematicCamera.ease(t, Claw.E_GRIP - 2, Claw.E_GRIP + 6);
                leftArm.xRot = -1.5f + twitch;
                leftArm.yRot = Mth.lerp(grip, 0.08f, 0.75f);
                leftArm.zRot = Mth.lerp(grip, -0.05f, -0.3f);
            } else if (t < Claw.E_BODY) {
                // still hooked on the torn edge, he pulls himself through as the leg steps out, bent forward, head down
                float k = CinematicCamera.ease(t, Claw.E_LEG, Claw.E_BODY);
                body.xRot = 0.45f;
                head.xRot = 0.75f;
                head.yRot = 0;
                rightArm.xRot = 0.35f;
                rightLeg.xRot = 0.4f * CinematicCamera.ease(t, Claw.E_STEP - 6, Claw.E_STEP);
                leftArm.xRot = Mth.lerp(k, -1.5f, -1.2f) + twitch;
                leftArm.yRot = Mth.lerp(k, 0.75f, 0.95f);
                leftArm.zRot = Mth.lerp(k, -0.3f, -0.45f);
                float lift = CinematicCamera.ease(t, Claw.E_LEG, Claw.E_LEG + 12), plant = CinematicCamera.ease(t, Claw.E_STEP - 6, Claw.E_STEP);
                leftLeg.xRot = -0.85f * lift + 0.4f * plant;
            } else {
                // leaning out head down, straightening, then the head comes up
                float k = CinematicCamera.ease(t, Claw.E_BODY, Claw.E_OUT);
                body.xRot = 0.45f * (1 - k);
                head.xRot = Mth.lerp(CinematicCamera.ease(t, Claw.E_OUT, Claw.E_HEAD + 6), 0.75f, 0);
                head.yRot = 0;
                // and lets go of the edge as he straightens
                leftArm.xRot = Mth.lerp(k, -1.2f, leftArm.xRot);
                leftArm.yRot = Mth.lerp(k, 0.95f, 0);
                leftArm.zRot = Mth.lerp(k, -0.45f, leftArm.zRot);
                rightArm.xRot = Mth.lerp(k, 0.35f, rightArm.xRot);
                leftLeg.xRot = Mth.lerp(k, -0.45f, 0);
                rightLeg.xRot = Mth.lerp(k, 0.4f, 0);
            }
        }
    }

    // ------------------------------------------------------------------ the extras

    /**
     * Parts, each in the space of the bone it rides on (head or left arm, wide arms). Sheet regions: tube (0,0), cap
     * (16,0), green (32,0), blue (48,0), orange (0,16), rust plates (16,16), blades (48,16).
     */
    /** A tube standing up from its base (`at`), `len` long: the vial (frame) with a cap, and its glass band (glass) to light. */
    static void vial(PartDefinition root, String name, int glassU, PartPose at, float len) {
        root.addOrReplaceChild(name + "_frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.75f, -len, -0.75f, 1.5f, len, 1.5f)
                .texOffs(16, 0).addBox(-0.5f, -len - 0.75f, -0.5f, 1, 0.75f, 1), at);
        root.addOrReplaceChild(name + "_glass", CubeListBuilder.create()
                .texOffs(glassU, glassU == 0 ? 16 : 0).addBox(-1, -len * 0.78f, -1, 2, len * 0.3f, 2), at);
    }

    /** Where each vial stands: on the head, up from the shoulder pad, out of the elbow (the stumps stay there when broken). */
    static final PartPose HEAD_VIAL = PartPose.offsetAndRotation(2, -8, 0.5f, -0.15f, 0, 0.3f),
            SHOULDER_VIAL = PartPose.offsetAndRotation(2, -3.4f, 0.6f, -0.1f, 0, 0.38f),
            ELBOW_VIAL = PartPose.offsetAndRotation(3.1f, 4.2f, -0.6f, 0.08f, 0, 0.62f);

    /** A burst vial: a short stump of the tube, jagged glass round its top, a torn wire hanging off the side. */
    static void stub(PartDefinition root, String name, PartPose at) {
        PartDefinition s = root.addOrReplaceChild(name, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.75f, -1.8f, -0.75f, 1.5f, 1.8f, 1.5f)
                .texOffs(8, 48).addBox(-0.7f, -2.7f, -0.7f, 0.5f, 0.9f, 0.5f)
                .texOffs(8, 48).addBox(0.2f, -2.4f, 0.15f, 0.5f, 0.6f, 0.5f)
                .texOffs(8, 48).addBox(-0.15f, -3.0f, 0.2f, 0.4f, 1.2f, 0.4f), at);
        wire(s, "wire", 0, 32, PartPose.offsetAndRotation(0.7f, -1.6f, 0, 0, 0, -0.8f), 1.6f, 0, 0);
    }

    /** A torn wire `len` long from `at`, and a loose end drooping off it (`droop` long, bent `bend`), if any. */
    static void wire(PartDefinition parent, String name, int u, int v, PartPose at, float len, float droop, float bend) {
        PartDefinition w = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-0.2f, 0, -0.2f, 0.4f, len, 0.4f), at);
        if (droop > 0) w.addOrReplaceChild("end", CubeListBuilder.create().texOffs(u, v).addBox(-0.2f, 0, -0.2f, 0.4f, droop, 0.4f), PartPose.offsetAndRotation(0, len, 0, bend, 0, 0));
    }

    public static LayerDefinition createExtras() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the serum tubes, each a short glass vial with its colour band, leaning out at its own angle: green out of the top
        // of the head (top at y -8), blue up from the shoulder pad, orange from the outside of the elbow (left arm, wide:
        // x -1..3, y -2..10, outside at +x)
        vial(root, "head", 32, HEAD_VIAL, 5.5f);
        vial(root, "shoulder", 48, SHOULDER_VIAL, 5);
        vial(root, "elbow", 0, ELBOW_VIAL, 4.5f);
        // broken: each vial a stump of tube and shards of glass, a torn wire hanging off it
        stub(root, "stub_head", HEAD_VIAL);
        stub(root, "stub_shoulder", SHOULDER_VIAL);
        stub(root, "stub_elbow", ELBOW_VIAL);
        // and the claw gone: a ragged end of meat round the steel they put in him, plate shards still stuck on, wires torn out
        PartDefinition wreck = root.addOrReplaceChild("wreck", CubeListBuilder.create()
                .texOffs(0, 48).addBox(-1.2f, 9.6f, -2.2f, 4.4f, 1, 4.4f)
                .texOffs(0, 48).addBox(-0.6f, 10.5f, -1.5f, 2.8f, 0.7f, 2.6f)
                .texOffs(8, 40).addBox(0.4f, 10.2f, -0.5f, 0.8f, 3.0f, 0.8f)
                .texOffs(8, 40).addBox(1.7f, 10.2f, 0.6f, 0.6f, 2.0f, 0.6f)
                .texOffs(16, 26).addBox(-1.5f, 6.2f, -2.5f, 2.6f, 2.4f, 0.9f)
                .texOffs(16, 26).addBox(2.6f, 7.0f, -1.2f, 0.9f, 2.6f, 2.4f), PartPose.ZERO);
        wire(wreck, "w1", 0, 32, PartPose.offsetAndRotation(0.0f, 10.6f, 0.9f, 0.55f, 0, 0.35f), 2.6f, 1.2f, 0.7f);
        wire(wreck, "w2", 8, 32, PartPose.offsetAndRotation(2.0f, 10.5f, -0.9f, -0.45f, 0, -0.5f), 2.2f, 1.4f, 0.9f);
        wire(wreck, "w3", 0, 40, PartPose.offsetAndRotation(2.5f, 10.3f, 0.9f, 0.3f, 0, -0.95f), 1.8f, 0, 0);
        wire(wreck, "w4", 0, 32, PartPose.offsetAndRotation(3.0f, 4.6f, 0, 0, 0, -1.15f), 2.0f, 1.0f, 1.0f);
        wire(wreck, "w5", 8, 32, PartPose.offsetAndRotation(3.0f, 5.4f, -0.8f, 0.5f, 0, -0.9f), 1.5f, 0, 0);
        // the plated arm: a big shoulder pad riding up over the shoulder, and the gauntlet
        root.addOrReplaceChild("shoulder_plate", CubeListBuilder.create().texOffs(16, 16).addBox(-1.5f, -3.5f, -2.75f, 5.25f, 5, 5.5f), PartPose.ZERO);
        root.addOrReplaceChild("plates", CubeListBuilder.create().texOffs(16, 26).addBox(-1.4f, 5.5f, -2.4f, 4.8f, 5, 4.8f), PartPose.ZERO);
        // three jointed fingers out of the gauntlet, spread across the hand, curling back at the tips
        PartDefinition blades = root.addOrReplaceChild("blades", CubeListBuilder.create(), PartPose.offset(1, 10.3f, 0));
        for (int i = 0; i < 3; i++) {
            PartDefinition finger = blades.addOrReplaceChild("blade" + i, CubeListBuilder.create().texOffs(48, 16).addBox(-0.5f, 0, -0.5f, 1, 3, 1),
                    PartPose.offsetAndRotation(-1.4f + i * 1.4f, 0, -0.9f + i * 0.9f, 0.25f, 0, (1 - i) * 0.12f));
            finger.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(48, 23).addBox(-0.5f, 0, -0.5f, 1, 3, 1),
                    PartPose.offsetAndRotation(0, 2.7f, 0, 0.55f, 0, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    static final class Extras extends RenderLayer<Claw, PlayerModel<Claw>> {
        final ModelPart headFrame, headGlass, shoulderFrame, shoulderGlass, elbowFrame, elbowGlass, shoulderPlate, plates, blades;
        final ModelPart stubHead, stubShoulder, stubElbow, wreck;

        Extras(RenderLayerParent<Claw, PlayerModel<Claw>> parent, ModelPart root) {
            super(parent);
            headFrame = root.getChild("head_frame");
            headGlass = root.getChild("head_glass");
            shoulderFrame = root.getChild("shoulder_frame");
            shoulderGlass = root.getChild("shoulder_glass");
            elbowFrame = root.getChild("elbow_frame");
            elbowGlass = root.getChild("elbow_glass");
            shoulderPlate = root.getChild("shoulder_plate");
            plates = root.getChild("plates");
            blades = root.getChild("blades");
            stubHead = root.getChild("stub_head");
            stubShoulder = root.getChild("stub_shoulder");
            stubElbow = root.getChild("stub_elbow");
            wreck = root.getChild("wreck");
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Claw c, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (c.isInvisible()) return;
            if (c.broken()) drawBroken(getParentModel(), this, pose, buffers, light);
            else drawExtras(getParentModel(), this, pose, buffers, light, c.serums(), age, c.rebuilt());
            // rebuilt: his eyes burning behind the mask's holes
            if (c.rebuilt() && !c.broken() && getParentModel().head.visible)
                getParentModel().head.render(pose, buffers.getBuffer(RenderType.eyes(EYES)), light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** What's left of them once he's beaten: the vial stumps, the shoulder pad, the wrecked end of the arm. */
    static void drawBroken(PlayerModel<?> m, Extras x, PoseStack pose, MultiBufferSource buffers, int light) {
        var solid = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        m.head.translateAndRotate(pose);
        x.stubHead.render(pose, solid, light, ov);
        pose.popPose();
        pose.pushPose();
        m.leftArm.translateAndRotate(pose);
        x.shoulderPlate.render(pose, solid, light, ov);
        x.stubShoulder.render(pose, solid, light, ov);
        x.stubElbow.render(pose, solid, light, ov);
        x.wreck.render(pose, solid, light, ov);
        pose.popPose();
    }

    /** The vials and the claw on a posed model; `lit` = which vials glow (Claw.SERUM_*). */
    static void drawExtras(PlayerModel<?> m, Extras x, PoseStack pose, MultiBufferSource buffers, int light, int lit, float age, boolean rebuilt) {
        ResourceLocation tex = rebuilt ? EXTRAS_REBUILT : EXTRAS;
        float vs = rebuilt ? REBUILT_VIALS : 1;
        for (ModelPart p : new ModelPart[]{x.headFrame, x.headGlass, x.shoulderFrame, x.shoulderGlass, x.elbowFrame, x.elbowGlass}) p.xScale = p.yScale = p.zScale = vs;
        var solid = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        int ov = OverlayTexture.NO_OVERLAY;
        // the head's vial only once the head is out of the rift
        if (m.head.visible) {
            pose.pushPose();
            m.head.translateAndRotate(pose);
            x.headFrame.render(pose, solid, light, ov);
            if ((lit & Claw.SERUM_K) == 0) x.headGlass.render(pose, solid, light, ov);
            if ((lit & Claw.SERUM_K) != 0) x.headGlass.render(pose, buffers.getBuffer(RenderType.eyes(tex)), light, ov);
            pose.popPose();
        }
        pose.pushPose();
        m.leftArm.translateAndRotate(pose);
        // a batched buffer ends when another one is asked for: fetch it again after the head's glow
        solid = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        // the upper arm's vials come out with his body (in the entrance the claw comes first)
        boolean upper = m.body.visible;
        if (upper) {
            x.shoulderFrame.render(pose, solid, light, ov);
            if ((lit & Claw.SERUM_W) == 0) x.shoulderGlass.render(pose, solid, light, ov);
            x.elbowFrame.render(pose, solid, light, ov);
            if ((lit & Claw.SERUM_R) == 0) x.elbowGlass.render(pose, solid, light, ov);
        }
        if (upper) x.shoulderPlate.render(pose, solid, light, ov);
        x.plates.render(pose, solid, light, ov);
        x.blades.render(pose, solid, light, ov);
        var glow = buffers.getBuffer(RenderType.eyes(tex));
        if (upper && (lit & Claw.SERUM_W) != 0) x.shoulderGlass.render(pose, glow, light, ov);
        if (upper && (lit & Claw.SERUM_R) != 0) x.elbowGlass.render(pose, glow, light, ov);
        pose.popPose();
    }
}
