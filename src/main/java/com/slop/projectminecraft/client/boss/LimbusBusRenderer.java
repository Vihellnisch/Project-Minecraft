package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.boss.LimbusBus;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Mephistopheles, the Limbus bus, after the game's story art: a long black coach with engraved art-deco panels and red
 * trim, rows of tall narrow windows, a lit yellow panel on its flank, and up front a red locomotive cab with a round
 * porthole windshield (the LIMBUS plate across its lower half), a chimney, brass lanterns and a red slatted cowcatcher.
 * The driver is a dark shape behind the glass. Model units are pixels at twice the usual size (8 to a block); the front is +z.
 */
public class LimbusBusRenderer extends EntityRenderer<LimbusBus> {
    static final ResourceLocation TEX = ProjectMinecraft.id("textures/entity/vergilius/limbus_bus.png");
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectMinecraft.id("limbus_bus"), "main");
    static final float SCALE = 2;
    /** Sheet regions (in 256-unit sheet coordinates; the image is 4x that). */
    static final int[] BLACK = {0, 0}, RED = {0, 96}, STEEL = {0, 176}, GLASS = {64, 176}, LIT = {128, 176}, LAMP = {192, 176},
            TAIL = {224, 176}, WHEEL = {0, 208}, WINDOW = {64, 208}, DOOR = {128, 208}, DARK = {192, 208}, PLATE = {0, 232};

    final ModelPart body, red, steel, glass, lit, windows, doorFront, doorBack, driver;
    final ModelPart[] wheels = new ModelPart[4];

    public LimbusBusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(LAYER);
        body = root.getChild("body");
        red = root.getChild("red");
        steel = root.getChild("steel");
        glass = root.getChild("glass");
        lit = root.getChild("lit");
        windows = root.getChild("windows");
        doorFront = root.getChild("door_front");
        doorBack = root.getChild("door_back");
        driver = root.getChild("driver");
        for (int i = 0; i < 4; i++) wheels[i] = root.getChild("wheel" + i);
        shadowRadius = 4;
    }

    static CubeListBuilder at(CubeListBuilder b, int[] r) {
        return b.texOffs(r[0], r[1]);
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the coach: chassis, body, roof (y is down; the ground is y 0)
        CubeListBuilder b = CubeListBuilder.create();
        at(b, BLACK).addBox(-10.5f, -9, -40, 21, 4, 72);
        at(b, BLACK).addBox(-11, -27, -40, 22, 18, 70);
        at(b, BLACK).addBox(-10, -30, -39, 20, 3, 68);
        at(b, BLACK).addBox(-7, -31.5f, -34, 14, 1.5f, 58);
        // ribs between the window groups, standing proud of the body
        for (float z : new float[]{-38.5f, -22, -6, 10, 26}) at(b, BLACK).addBox(-11.6f, -27.5f, z, 23.2f, 19, 1.6f);
        root.addOrReplaceChild("body", b, PartPose.ZERO);

        // red: the trim bands top and bottom, the cab, the cowcatcher
        CubeListBuilder r = CubeListBuilder.create();
        at(r, RED).addBox(-11.3f, -25.6f, -40.2f, 22.6f, 1.8f, 70.4f);
        at(r, RED).addBox(-11.3f, -11.4f, -40.2f, 22.6f, 2.2f, 70.4f);
        // the cab: a block wider and taller than the coach, its front face where the porthole sits
        at(r, RED).addBox(-12, -29, 30, 24, 22, 11);
        at(r, RED).addBox(-11, -31, 31, 22, 2, 9);
        // the cab's front frame, stepping in round the porthole
        at(r, RED).addBox(-12, -29, 41, 3, 22, 1.5f);
        at(r, RED).addBox(9, -29, 41, 3, 22, 1.5f);
        at(r, RED).addBox(-9, -29, 41, 18, 2, 1.5f);
        at(r, RED).addBox(-9, -9, 41, 18, 2, 1.5f);
        // the cowcatcher: a deck, a top rail, slats raked forward and down
        at(r, RED).addBox(-12.5f, -7, 41, 25, 2, 4);
        at(r, RED).addBox(-12.5f, -8, 44.5f, 25, 1.4f, 1.2f);
        root.addOrReplaceChild("red", r, PartPose.ZERO);
        PartDefinition redRoot = root.getChild("red");
        for (int i = 0; i < 11; i++) {
            float x = -11.5f + i * 2.3f;
            redRoot.addOrReplaceChild("slat" + i, at(CubeListBuilder.create(), RED).addBox(-0.7f, 0, -0.5f, 1.4f, 7.6f, 1.0f),
                    PartPose.offsetAndRotation(x, -7.4f, 45, -0.62f, 0, 0));
        }
        redRoot.addOrReplaceChild("chimney_band", at(CubeListBuilder.create(), RED).addBox(-3.1f, -1, -3.1f, 6.2f, 2, 6.2f), PartPose.offset(0, -32, 35));

        // steel: the porthole's ring (sixteen short segments round the window), the chimney, the lanterns' cages
        CubeListBuilder s = CubeListBuilder.create();
        at(s, STEEL).addBox(-2.6f, -40, 32.4f, 5.2f, 9, 5.2f);
        at(s, STEEL).addBox(-3.4f, -41.4f, 31.6f, 6.8f, 1.6f, 6.8f);
        root.addOrReplaceChild("steel", s, PartPose.ZERO);
        PartDefinition steelRoot = root.getChild("steel");
        for (int i = 0; i < 28; i++) {
            float a = i * Mth.TWO_PI / 28;
            steelRoot.addOrReplaceChild("ring" + i, at(CubeListBuilder.create(), STEEL).addBox(-1.0f, -1.1f, -0.8f, 2.0f, 2.2f, 1.6f),
                    PartPose.offsetAndRotation(Mth.sin(a) * 8.1f, -18 - Mth.cos(a) * 8.1f, 42.1f, 0, 0, -a));
        }
        // the rivets' hub at the window's middle bar
        steelRoot.addOrReplaceChild("bar", at(CubeListBuilder.create(), STEEL).addBox(-0.5f, -7.6f, -0.3f, 1, 8.6f, 0.6f)
                .texOffs(STEEL[0], STEEL[1]).addBox(-7.6f, 0.6f, -0.35f, 15.2f, 0.8f, 0.7f), PartPose.offset(0, -18, 42.3f));

        // the windshield's upper half: tinted glass (translucent), with the driver behind it
        root.addOrReplaceChild("glass", at(CubeListBuilder.create(), GLASS).addBox(-7.4f, -25.6f, 41.6f, 14.8f, 7.4f, 0.4f)
                .texOffs(GLASS[0], GLASS[1]).addBox(-5.6f, -26.4f, 41.6f, 11.2f, 1, 0.4f), PartPose.ZERO);
        root.addOrReplaceChild("driver", at(CubeListBuilder.create(), DARK).addBox(-3, -21, 35, 6, 5, 3)
                .texOffs(DARK[0], DARK[1]).addBox(-1.8f, -24.6f, 35.4f, 3.6f, 3.6f, 2.6f), PartPose.ZERO);

        // lit: the LIMBUS plate (the windshield's lower half), the yellow panel on each flank, brass lanterns, tail lights
        CubeListBuilder l = CubeListBuilder.create();
        at(l, PLATE).addBox(-7.4f, -17.4f, 41.6f, 14.8f, 7, 0.5f);
        at(l, LIT).addBox(-11.25f, -15.6f, -20, 0.4f, 4.0f, 13);
        at(l, LIT).addBox(10.85f, -15.6f, -20, 0.4f, 4.0f, 13);
        for (float x : new float[]{-10.6f, 8.4f}) at(l, LAMP).addBox(x, -9.8f, 41.4f, 2.2f, 3, 2.2f);
        for (float x : new float[]{-6.6f, 4.4f}) at(l, LAMP).addBox(x, -32.4f, 38.6f, 2.2f, 3, 2.2f);
        for (float x : new float[]{-9.5f, 6.5f}) at(l, TAIL).addBox(x, -14, -40.6f, 3, 2.4f, 0.6f);
        root.addOrReplaceChild("lit", l, PartPose.ZERO);

        // the windows: tall narrow panes in groups of three between the ribs, both flanks
        CubeListBuilder w = CubeListBuilder.create();
        float[] starts = {-36.4f, -20, -4, 12};
        for (float z0 : starts) {
            for (int k = 0; k < 4; k++) {
                float z = z0 + 0.6f + k * 3.6f;
                if (z0 == -4 && k >= 2) continue; // the door's place
                // the panes stop above the lit panel's bay
                float bottom = z0 == -20 ? -16.2f : -14.6f;
                at(w, WINDOW).addBox(-11.2f, -23.6f, z, 0.4f, bottom + 23.6f, 2.6f);
                at(w, WINDOW).addBox(10.8f, -23.6f, z, 0.4f, bottom + 23.6f, 2.6f);
            }
        }
        // the cab's side windows
        at(w, WINDOW).addBox(-12.2f, -25, 33.5f, 0.4f, 7, 2.2f);
        at(w, WINDOW).addBox(-12.2f, -25, 36.6f, 0.4f, 7, 2.2f);
        at(w, WINDOW).addBox(11.8f, -25, 33.5f, 0.4f, 7, 2.2f);
        at(w, WINDOW).addBox(11.8f, -25, 36.6f, 0.4f, 7, 2.2f);
        root.addOrReplaceChild("windows", w, PartPose.ZERO);

        // the doorway behind the door, dark (shows as the leaves slide apart)
        root.getChild("driver").addOrReplaceChild("doorway", at(CubeListBuilder.create(), DARK).addBox(11.03f, -24.5f, 0, 0.1f, 15.5f, 6.8f), PartPose.ZERO);
        // the door on the left flank (+x): two leaves that slide apart along the coach
        root.addOrReplaceChild("door_front", at(CubeListBuilder.create(), DOOR).addBox(11.0f, -24.5f, 0, 0.6f, 15.5f, 3.4f), PartPose.offset(0, 0, 3.4f));
        root.addOrReplaceChild("door_back", at(CubeListBuilder.create(), DOOR).addBox(11.0f, -24.5f, -3.4f, 0.6f, 15.5f, 3.4f), PartPose.offset(0, 0, 3.4f));

        // wheels: two boxes crossed into an octagon each, turning about x
        float[][] wh = {{-10.8f, 26}, {10.8f, 26}, {-10.8f, -30}, {10.8f, -30}};
        for (int i = 0; i < 4; i++) {
            PartDefinition wd = root.addOrReplaceChild("wheel" + i, at(CubeListBuilder.create(), WHEEL).addBox(-1.5f, -4.6f, -2, 3, 9.2f, 4)
                    .texOffs(WHEEL[0], WHEEL[1]).addBox(-1.5f, -2, -4.6f, 3, 4, 9.2f), PartPose.offset(wh[i][0], -4.6f, wh[i][1]));
            wd.addOrReplaceChild("x", at(CubeListBuilder.create(), WHEEL).addBox(-1.5f, -4.6f, -2, 3, 9.2f, 4)
                    .texOffs(WHEEL[0], WHEEL[1]).addBox(-1.5f, -2, -4.6f, 3, 4, 9.2f), PartPose.rotation(Mth.PI / 4, 0, 0));
            wd.addOrReplaceChild("hub", at(CubeListBuilder.create(), STEEL).addBox(-1.8f, -1.4f, -1.4f, 3.6f, 2.8f, 2.8f), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void render(LimbusBus bus, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float fade = bus.fade();
        if (fade >= 1) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, bus.yRotO, bus.getYRot())));
        pose.scale(-SCALE, -SCALE, SCALE);
        int alpha = (int) (255 * (1 - fade)), color = FastColor.ARGB32.color(alpha, 255, 255, 255);
        int ov = OverlayTexture.NO_OVERLAY;
        boolean faded = fade > 0;
        var solid = buffers.getBuffer(faded ? RenderType.entityTranslucent(TEX) : RenderType.entityCutoutNoCull(TEX));
        float spin = Mth.lerp(partial, bus.wheelSpinO, bus.wheelSpin);
        for (ModelPart w : wheels) w.xRot = spin;
        float door = bus.door();
        doorFront.z = 3.4f + door * 3.2f;
        doorBack.z = 3.4f - door * 3.2f;
        body.render(pose, solid, light, ov, color);
        red.render(pose, solid, light, ov, color);
        steel.render(pose, solid, light, ov, color);
        windows.render(pose, solid, light, ov, color);
        doorFront.render(pose, solid, light, ov, color);
        doorBack.render(pose, solid, light, ov, color);
        driver.render(pose, solid, light, ov, color);
        for (ModelPart w : wheels) w.render(pose, solid, light, ov, color);
        // the lights burn at full brightness, the glass last (it's see-through)
        var bright = buffers.getBuffer(faded ? RenderType.entityTranslucent(TEX) : RenderType.entityCutoutNoCull(TEX));
        lit.render(pose, bright, LightTexture.FULL_BRIGHT, ov, color);
        lit.render(pose, buffers.getBuffer(RenderType.eyes(TEX)), LightTexture.FULL_BRIGHT, ov, FastColor.ARGB32.color(255, alpha / 3, alpha / 3, alpha / 3));
        glass.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEX)), light, ov, color);
        pose.popPose();
        super.render(bus, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LimbusBus bus) {
        return TEX;
    }

    @Override
    public boolean shouldRender(LimbusBus bus, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(bus.getBoundingBox().inflate(7));
    }
}
