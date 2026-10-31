package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.RolandKin;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A Smouldering Byproduct, after the reception's art: a tall flat snout stood straight up like a slab, a row of teeth
 * down its open edge; under it a thin ribbed body, hunched, two dark holes in its chest and two thin arms hanging; and
 * where its legs should be, eight long ribbons of flesh spreading over the floor and curling up at their tips.
 * Shares the beast's texture.
 */
public class ByproductModel extends HierarchicalModel<RolandKin> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectMinecraft.id("roland_byproduct"), "main");
    static final int TENTACLES = 8, SEGS = 5;

    private final ModelPart root, body, snout, jaw, armL, armR;
    private final ModelPart[][] tentacles = new ModelPart[TENTACLES][SEGS];

    public ByproductModel(ModelPart root) {
        this.root = root;
        body = root.getChild("base").getChild("body");
        snout = body.getChild("snout");
        jaw = snout.getChild("jaw");
        armL = body.getChild("arm_l");
        armR = body.getChild("arm_r");
        for (int i = 0; i < TENTACLES; i++) {
            ModelPart p = root.getChild("base").getChild("tentacle" + i);
            for (int s = 0; s < SEGS; s++) {
                tentacles[i][s] = p;
                if (s + 1 < SEGS) p = p.getChild("t");
            }
        }
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // a knot of flesh on the floor that everything grows out of
        PartDefinition base = root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -4, -3.5f, 7, 4, 7), PartPose.offset(0, 23, 0));
        // the ribbons it stands on: flat, spreading, curling up at the tips
        for (int i = 0; i < TENTACLES; i++) {
            float yaw = i * Mth.TWO_PI / TENTACLES + 0.2f;
            PartDefinition p = base.addOrReplaceChild("tentacle" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -0.6f, 0, 3, 1.2f, 7),
                    PartPose.offsetAndRotation(Mth.sin(yaw) * 2.5f, -1, Mth.cos(yaw) * 2.5f, 0.15f, yaw, 0));
            for (int s = 1; s < SEGS; s++) {
                float w = 3 - s * 0.45f;
                p = p.addOrReplaceChild("t", CubeListBuilder.create().texOffs(0, 0).addBox(-w / 2, -0.5f, 0, w, 1, 6), PartPose.offsetAndRotation(0, 0, 6.7f, 0, 0, 0));
            }
        }
        // the body: thin, hunched forward, ribs showing, two dark holes in its chest
        CubeListBuilder torso = CubeListBuilder.create().texOffs(0, 0).addBox(-3, -14, -2.5f, 6, 14, 5)
                .texOffs(0, 64).addBox(-2.2f, -10, -2.9f, 1.6f, 2.2f, 0.6f).addBox(0.6f, -10, -2.9f, 1.6f, 2.2f, 0.6f)
                .texOffs(128, 64);
        for (int i = 0; i < 5; i++) torso.addBox(-3.4f, -12.5f + i * 2.3f, -2.8f, 6.8f, 0.8f, 0.6f).addBox(-3.4f, -12.5f + i * 2.3f, -2.8f, 0.6f, 0.8f, 5.6f).addBox(2.8f, -12.5f + i * 2.3f, -2.8f, 0.6f, 0.8f, 5.6f);
        PartDefinition body = base.addOrReplaceChild("body", torso, PartPose.offsetAndRotation(0, -3, 0, 0.35f, 0, 0));
        // the snout: a tall slab stood on end over the body, its mouth an open dark seam lined with teeth
        CubeListBuilder slab = CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -24, -1, 5, 24, 4)
                .texOffs(0, 96).addBox(-2, -23, -1.6f, 4, 22, 0.8f);
        slab.texOffs(128, 64);
        for (int i = 0; i < 9; i++) slab.addBox(-2.6f, -22.5f + i * 2.4f, -2.2f, 1, 1.4f, 0.8f).addBox(1.6f, -21.5f + i * 2.4f, -2.2f, 1, 1.4f, 0.8f);
        PartDefinition snout = body.addOrReplaceChild("snout", slab, PartPose.offsetAndRotation(0, -13, -1, -0.55f, 0, 0.08f));
        // its lower jaw, hanging open off the front of the slab
        snout.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -20, -1.2f, 4, 20, 1.6f), PartPose.offsetAndRotation(0, -1, -1.4f, -0.12f, 0, 0));
        // two thin arms, hanging, with little hooked hands
        CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 0).addBox(-0.8f, 0, -0.8f, 1.6f, 11, 1.6f)
                .texOffs(128, 64).addBox(-1.1f, 10.5f, -1.1f, 0.6f, 3, 0.6f).addBox(0.5f, 10.5f, -1.1f, 0.6f, 3, 0.6f);
        body.addOrReplaceChild("arm_l", arm, PartPose.offsetAndRotation(-3.6f, -12, -1, -0.5f, 0, 0.2f));
        body.addOrReplaceChild("arm_r", arm, PartPose.offsetAndRotation(3.6f, -12, -1, -0.5f, 0, -0.2f));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(RolandKin kin, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float walk = Math.min(1, limbAmount * 1.5f);
        // the ribbons ripple along the floor and curl tighter toward their tips
        for (int i = 0; i < TENTACLES; i++) for (int s = 0; s < SEGS; s++) {
            float k = age * 0.12f + limbSwing * 0.6f * walk + i * 0.9f - s * 0.7f;
            tentacles[i][s].xRot += (s == 0 ? 0 : -0.28f * s * 0.6f) + Mth.sin(k) * 0.12f;
            tentacles[i][s].yRot += Mth.cos(k * 0.8f) * 0.15f + (s >= 2 ? 0.25f : 0);
        }
        body.xRot += Mth.sin(age * 0.08f) * 0.05f + kin.attackAnim * 0.35f;
        body.yRot += headYaw * Mth.DEG_TO_RAD * 0.4f;
        // the snout lunges down when it bites, the jaw gapes
        snout.xRot += Mth.sin(age * 0.06f) * 0.05f - kin.attackAnim * 0.6f;
        jaw.xRot += 0.08f + Mth.sin(age * 0.15f) * 0.05f + kin.attackAnim * 0.5f;
        armL.xRot += Mth.sin(age * 0.1f) * 0.1f - kin.attackAnim * 0.8f;
        armR.xRot += Mth.cos(age * 0.1f) * 0.1f - kin.attackAnim * 0.8f;
    }
}
