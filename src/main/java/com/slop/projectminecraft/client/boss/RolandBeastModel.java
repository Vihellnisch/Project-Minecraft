package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.Roland;
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
 * Roland distorted, after the reception's art (a side view: its left is his front, its right his rear). In front, a
 * great mass of red-black flesh with what's left of Roland rising out of the middle of it, a small black skull on a
 * long stalk with black beads hanging off it like a rosary, and a toothed maw split down beside him; behind it a second,
 * rounder mass crowded with black sockets, a thick limb reaching back from it to a clawed foot planted on the floor.
 * Striated trunks heave up out of both and fray into flat black ribbons; stumpy elephant legs carry him, and a long
 * skeletal arm reaches out in front to a hand of hooked claws.
 * 256x128 texture: flesh (0,0), black (0,64), bone (128,64), the inside of the maw (0,96).
 */
public class RolandBeastModel extends HierarchicalModel<Roland> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectMinecraft.id("roland_beast"), "main");
    static final int TRUNKS = 6, FLESH_SEGS = 3, RIBBON_SEGS = 5, BEADS = 5;
    /** Where each trunk leaves him {x, z, lean sideways, lean back, 1 if off the rear mass}. */
    static final float[][] TRUNK_AT = {{-9, -4, -0.75f, -0.1f, 0}, {0, -9, -0.1f, -0.35f, 0}, {9, -2, 0.7f, 0, 0}, {-6, 8, -0.5f, 0.45f, 1}, {4, 5, 0.25f, 0.3f, 1},
            {8, 13, 0.6f, 0.85f, 1}};
    /** Sockets on the front mass: {x, y, z, face (0 front, 1 left, 2 right, 3 top)}. */
    static final float[][] EYES = {
            {-12, -9, 0, 0}, {13, -6, 0, 0}, {-11, 2, 0, 0},
            {0, -8, -10, 1}, {0, 0, -4, 1}, {0, -5, 5, 1}, {0, 4, 10, 1},
            {0, -9, -8, 2}, {0, -1, 0, 2}, {0, 5, -11, 2}, {0, -6, 9, 2},
            {-8, 0, -8, 3}, {10, 0, -4, 3}, {-10, 0, 9, 3}};
    /** Sockets crowding the rear mass: {x, y, z, face (1 left, 2 right, 3 top, 4 back)}. */
    static final float[][] REAR_EYES = {
            {0, -8, 4, 1}, {0, -2, 9, 1}, {0, -7, 14, 1}, {0, 3, 3, 1}, {0, 2, 15, 1}, {0, -10, 9, 1},
            {0, -8, 5, 2}, {0, -2, 11, 2}, {0, -9, 15, 2}, {0, 4, 6, 2}, {0, 3, 16, 2},
            {-8, 0, 5, 3}, {-1, 0, 10, 3}, {7, 0, 4, 3}, {-6, 0, 16, 3}, {5, 0, 15, 3},
            {-7, -6, 0, 4}, {2, -2, 0, 4}, {8, -8, 0, 4}, {-3, 4, 0, 4}};

    private final ModelPart root, body, rear, neck, neckTop;
    private final ModelPart[][] trunks = new ModelPart[TRUNKS][FLESH_SEGS + RIBBON_SEGS];
    private final ModelPart[] beads = new ModelPart[BEADS];
    private final ModelPart armL, foreL, hindLimb, hindFoot, legFL, legFR, legBL, legBR;

    public RolandBeastModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        rear = body.getChild("rear");
        neck = body.getChild("neck");
        neckTop = neck.getChild("top");
        ModelPart b = neckTop.getChild("head");
        for (int i = 0; i < BEADS; i++) {
            beads[i] = b.getChild("bead");
            b = beads[i];
        }
        for (int i = 0; i < TRUNKS; i++) {
            ModelPart p = (TRUNK_AT[i][4] == 1 ? rear : body).getChild("trunk" + i);
            for (int s = 0; s < FLESH_SEGS + RIBBON_SEGS; s++) {
                trunks[i][s] = p;
                if (s + 1 < FLESH_SEGS + RIBBON_SEGS) p = p.getChild("t");
            }
        }
        armL = body.getChild("arm_l");
        foreL = armL.getChild("fore");
        hindLimb = rear.getChild("hind");
        hindFoot = hindLimb.getChild("fore");
        legFL = body.getChild("leg_fl");
        legFR = body.getChild("leg_fr");
        legBL = rear.getChild("leg_bl");
        legBR = rear.getChild("leg_br");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the front mass, wide and heavy, rounded off with bulges
        CubeListBuilder mass = CubeListBuilder.create().texOffs(0, 0).addBox(-18, -14, -16, 36, 24, 30)
                .addBox(-14, -19, -13, 28, 5, 24).addBox(-21, -9, -12, 3, 16, 22).addBox(18, -9, -12, 3, 16, 22)
                .addBox(-13, -10, -18.5f, 26, 18, 3).addBox(-14, 9.5f, -13, 28, 3, 24);
        mass.texOffs(0, 64);
        for (float[] e : EYES) {
            switch ((int) e[3]) {
                case 0 -> mass.addBox(e[0] - 2.5f, e[1] - 2.5f, -19.6f, 5, 5, 1.5f);
                case 1 -> mass.addBox(-22.2f, e[1] - 2.5f, e[2] - 2.5f, 1.5f, 5, 5);
                case 2 -> mass.addBox(20.7f, e[1] - 2.5f, e[2] - 2.5f, 1.5f, 5, 5);
                default -> mass.addBox(e[0] - 2.5f, -19.8f, e[2] - 2.5f, 5, 1.5f, 5);
            }
        }
        // the maw split down his front beside Roland: dark inside, red lips, a row of teeth down each side
        mass.texOffs(0, 96).addBox(4, -8, -19.2f, 6, 18, 1);
        mass.texOffs(0, 0).addBox(2.2f, -9, -19.6f, 1.8f, 20, 1.2f).addBox(10, -9, -19.6f, 1.8f, 20, 1.2f);
        mass.texOffs(128, 64);
        for (int i = 0; i < 8; i++) {
            float y = -7.5f + i * 2.2f;
            mass.addBox(3.8f, y, -19.9f, 1.8f, 1.3f, 0.8f).addBox(8.4f, y + 1.1f, -19.9f, 1.8f, 1.3f, 0.8f);
        }
        PartDefinition body = root.addOrReplaceChild("body", mass, PartPose.offset(0, 2, -6));

        // the rear mass: rounder, lower, crowded with sockets
        CubeListBuilder bulb = CubeListBuilder.create().texOffs(0, 0).addBox(-14, -12, 0, 28, 22, 20)
                .addBox(-11, -15, 2, 22, 3, 16).addBox(-11, -9, 19.5f, 22, 16, 2.5f).addBox(-16, -7, 3, 2, 13, 14).addBox(14, -7, 3, 2, 13, 14);
        bulb.texOffs(0, 64);
        for (float[] e : REAR_EYES) {
            switch ((int) e[3]) {
                case 1 -> bulb.addBox(-17.2f, e[1] - 2.5f, e[2] - 2.5f, 1.5f, 5, 5);
                case 2 -> bulb.addBox(15.7f, e[1] - 2.5f, e[2] - 2.5f, 1.5f, 5, 5);
                case 3 -> bulb.addBox(e[0] - 2.5f, -15.8f, e[2] - 2.5f, 5, 1.5f, 5);
                default -> bulb.addBox(e[0] - 2.5f, e[1] - 2.5f, 21.6f, 5, 5, 1.5f);
            }
        }
        PartDefinition rear = body.addOrReplaceChild("rear", bulb, PartPose.offsetAndRotation(0, 2, 12, 0.08f, 0, 0));

        // the trunks out of both: thick striated flesh, fraying into flat black ribbons
        for (int i = 0; i < TRUNKS; i++) {
            float[] a = TRUNK_AT[i];
            boolean back = a[4] == 1;
            PartDefinition p = (back ? rear : body).addOrReplaceChild("trunk" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -8, -3.5f, 7, 8, 7),
                    PartPose.offsetAndRotation(a[0], back ? -13 : -17, a[1], a[3], 0, a[2]));
            for (int s = 1; s < FLESH_SEGS; s++) {
                float w = 6 - s * 1.2f;
                p = p.addOrReplaceChild("t", CubeListBuilder.create().texOffs(0, 0).addBox(-w / 2, -7.5f, -w / 2, w, 7.5f, w), PartPose.offset(0, -7.6f, 0));
            }
            for (int s = 0; s < RIBBON_SEGS; s++) {
                float w = 3.8f - s * 0.4f;
                p = p.addOrReplaceChild("t", CubeListBuilder.create().texOffs(0, 64).addBox(-w / 2, -7, -0.5f, w, 7, 1), PartPose.offset(0, s == 0 ? -7.3f : -6.8f, 0));
            }
        }

        // Roland, what's left of him: out of the middle of the front, a long stalk, a small black skull, a string of beads
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -15, -2.5f, 5, 15, 5),
                PartPose.offsetAndRotation(-4, -10, -16, 0.5f, 0, -0.08f));
        PartDefinition top = neck.addOrReplaceChild("top", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -13, -2, 4, 13, 4), PartPose.offsetAndRotation(0, -14.5f, 0, 0.15f, 0, -0.1f));
        PartDefinition head = top.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 64).addBox(-3, -5.5f, -3, 6, 6, 6)
                .texOffs(128, 64).addBox(-2, -2, -3.4f, 1.2f, 1.2f, 0.6f).addBox(0.8f, -2, -3.4f, 1.2f, 1.2f, 0.6f), PartPose.offsetAndRotation(0, -13, 0, 0.5f, 0, 0));
        PartDefinition bead = head;
        for (int i = 0; i < BEADS; i++) {
            float s = 4.2f - i * 0.3f;
            bead = bead.addOrReplaceChild("bead", CubeListBuilder.create().texOffs(0, 64).addBox(-s / 2, 0, -s / 2, s, s, s), PartPose.offset(0, i == 0 ? 0.5f : 4.6f - i * 0.3f, i == 0 ? -2 : 0));
        }

        // stumpy legs underneath, elephant-thick, with flat nails
        CubeListBuilder foot = CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, 0, -4.5f, 9, 12, 9)
                .texOffs(128, 64).addBox(-4, 10.6f, -4.9f, 2.2f, 1.4f, 0.6f).addBox(-1.1f, 10.6f, -4.9f, 2.2f, 1.4f, 0.6f).addBox(1.8f, 10.6f, -4.9f, 2.2f, 1.4f, 0.6f);
        body.addOrReplaceChild("leg_fl", foot, PartPose.offset(-9, 10, -9));
        body.addOrReplaceChild("leg_fr", foot, PartPose.offset(9, 10, -9));
        CubeListBuilder hindFoot = CubeListBuilder.create().texOffs(0, 0).addBox(-4, 0, -4, 8, 10, 8)
                .texOffs(128, 64).addBox(-3.5f, 8.6f, -4.4f, 2, 1.4f, 0.6f).addBox(-1, 8.6f, -4.4f, 2, 1.4f, 0.6f).addBox(1.5f, 8.6f, -4.4f, 2, 1.4f, 0.6f);
        rear.addOrReplaceChild("leg_bl", hindFoot, PartPose.offset(-8, 9.5f, 9));
        rear.addOrReplaceChild("leg_br", hindFoot, PartPose.offset(8, 9.5f, 9));

        // a long skeletal arm reaching out in front, down to a hand of long hooked fingers
        PartDefinition arm = body.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, 0, -1.5f, 3, 22, 3),
                PartPose.offsetAndRotation(-18, 4, -14, -0.7f, 0.3f, 0.9f));
        CubeListBuilder hand = CubeListBuilder.create().texOffs(0, 0).addBox(-1.2f, 0, -1.2f, 2.4f, 20, 2.4f).addBox(-2, 19, -2, 4, 2, 4);
        hand.texOffs(128, 64).addBox(-2.6f, 20.5f, -2.6f, 0.9f, 7, 0.9f).addBox(-0.8f, 20.5f, -3.2f, 0.9f, 8, 0.9f).addBox(1.2f, 20.5f, -2.6f, 0.9f, 7, 0.9f)
                .addBox(1.4f, 20.5f, 1.2f, 0.9f, 6, 0.9f).addBox(-2.4f, 20.5f, 1.2f, 0.9f, 6, 0.9f);
        arm.addOrReplaceChild("fore", hand, PartPose.offsetAndRotation(0, 21.5f, 0, 0.9f, 0, -1.2f));

        // the thick limb reaching back off the rear mass, bent at the knee, down to a clawed foot on the floor
        PartDefinition hind = rear.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 0).addBox(-3, 0, -3, 6, 22, 6),
                PartPose.offsetAndRotation(9, 2, 16, 1.25f, 0, -0.35f));
        CubeListBuilder claw = CubeListBuilder.create().texOffs(0, 0).addBox(-2.4f, 0, -2.4f, 4.8f, 20, 4.8f).addBox(-3.4f, 19, -4.5f, 6.8f, 2.5f, 8);
        claw.texOffs(128, 64).addBox(-3.2f, 20.5f, -7.5f, 1.1f, 1.1f, 3.5f).addBox(-0.55f, 20.5f, -8, 1.1f, 1.1f, 4).addBox(2.1f, 20.5f, -7.5f, 1.1f, 1.1f, 3.5f)
                .addBox(-0.55f, 20.5f, 3.4f, 1.1f, 1.1f, 2.5f);
        hind.addOrReplaceChild("fore", claw, PartPose.offsetAndRotation(0, 21.5f, 0, -1.55f, 0, 0.3f));

        // little clawed hands reaching out from under the front of him
        CubeListBuilder small = CubeListBuilder.create().texOffs(0, 0).addBox(-0.9f, 0, -0.9f, 1.8f, 12, 1.8f)
                .texOffs(128, 64).addBox(-1.4f, 11.5f, -1.4f, 0.7f, 3.5f, 0.7f).addBox(0.7f, 11.5f, -1.4f, 0.7f, 3.5f, 0.7f).addBox(-0.35f, 11.5f, -2, 0.7f, 4, 0.7f);
        body.addOrReplaceChild("claw_a", small, PartPose.offsetAndRotation(-3, 9, -16, -0.9f, 0, 0.1f));
        body.addOrReplaceChild("claw_b", small, PartPose.offsetAndRotation(5, 10, -15, -1.1f, 0, -0.2f));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Roland r, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float shift = r.shiftTime(0);
        boolean crouch = r.bodyPose() == Roland.POSE_CROUCH;
        // heaving, breathing; the rear mass a beat behind the front
        body.y += Mth.sin(age * 0.1f) * 0.8f + (crouch ? 5 : 0);
        body.xRot += Mth.sin(age * 0.06f) * 0.025f - r.attackAnim * 0.18f;
        rear.xRot += Mth.sin(age * 0.1f - 1.2f) * 0.04f;
        rear.yRot += Mth.sin(age * 0.05f) * 0.04f;
        float walk = Math.min(1, limbAmount * 1.5f);
        legFL.xRot += Mth.sin(limbSwing * 0.5f) * 0.45f * walk;
        legBR.xRot += Mth.sin(limbSwing * 0.5f) * 0.45f * walk;
        legFR.xRot += Mth.sin(limbSwing * 0.5f + Mth.PI) * 0.45f * walk;
        legBL.xRot += Mth.sin(limbSwing * 0.5f + Mth.PI) * 0.45f * walk;
        // the front arm claws at the floor as he drags himself along, and sweeps when he strikes
        armL.yRot += Mth.sin(limbSwing * 0.35f) * 0.25f * walk - r.attackAnim * 0.6f;
        armL.xRot -= r.attackAnim * 0.5f;
        foreL.zRot += Mth.sin(age * 0.05f) * 0.06f;
        // the hind limb pushes him along
        hindLimb.xRot += Mth.sin(limbSwing * 0.35f + Mth.PI) * 0.2f * walk + Mth.sin(age * 0.04f) * 0.04f;
        hindFoot.xRot -= Mth.sin(limbSwing * 0.35f + Mth.PI) * 0.2f * walk;
        // the trunks writhe; the ribbons at their ends snap back and forth in zig-zags
        for (int i = 0; i < TRUNKS; i++) {
            for (int s = 0; s < FLESH_SEGS + RIBBON_SEGS; s++) {
                float k = age * 0.07f + i * 1.9f + s * 0.8f;
                boolean ribbon = s >= FLESH_SEGS;
                trunks[i][s].xRot += Mth.sin(k) * (ribbon ? 0.35f : 0.12f);
                trunks[i][s].zRot += (ribbon ? ((s + i) % 2 == 0 ? 0.9f : -0.9f) : 0) + Mth.cos(k * 0.8f) * (ribbon ? 0.25f : 0.1f);
                if (ribbon) trunks[i][s].yRot += Mth.sin(k * 0.5f) * 0.4f;
            }
        }
        // Roland's stalk sways; the beads hang down whatever it does
        neck.zRot += Mth.sin(age * 0.045f) * 0.08f;
        neck.xRot += Mth.sin(age * 0.06f) * 0.06f - r.attackAnim * 0.3f;
        neckTop.xRot += Mth.sin(age * 0.07f) * 0.08f;
        ModelPart head = neckTop.getChild("head");
        beads[0].zRot -= neck.zRot + neckTop.zRot + head.zRot;
        beads[0].xRot -= neck.xRot + neckTop.xRot + head.xRot;
        for (int i = 0; i < BEADS; i++) beads[i].zRot += Mth.sin(age * 0.09f + i * 0.7f) * 0.06f;
        if (shift >= 0 && r.shiftFrom() == Roland.DISTORTED) {
            // falling apart into smoke
            float k = Math.min(1, shift / 50f);
            body.y += k * 8;
            for (int i = 0; i < TRUNKS; i++) trunks[i][0].xRot += k * 0.8f;
        }
    }
}
