package com.slop.projectminecraft.client.arsenal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.arsenal.Gloves;
import com.slop.projectminecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Black Silence's gloves on both hands while worn (or a Furioso runs), and the workshop weapon they hold: the one
 * picked on the wheel when the main hand is empty, or during Furioso the one just produced. One hand or both.
 */
public class GloveLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectMinecraft.id("black_silence_gloves"), "main");
    static final ResourceLocation TEX = ProjectMinecraft.id("textures/entity/black_silence_gloves.png");
    public static final String[] WEAPON_MODELS = {"pistol", "lance", "hammer", "katana", "gauntlet", "knife", "axe", "mace", "greatsword", "shortsword", "shotgun", "durandal"};
    /** The sheaths Roland draws the katana and Durandal from: model ids after the weapons. */
    private static final String[] SHEATH_MODELS = {"katana_sheath", "durandal_sheath", "gauntlet_left"};
    /** The sheaths, then the left gauntlet (a mirror image of the right, so its thumb stays in front). */
    public static final int KATANA_SHEATH = 12, DURANDAL_SHEATH = 13, GAUNTLET_LEFT = 14, MODELS = 15;

    private final ModelPart right, left;
    private static ItemStack carrier = ItemStack.EMPTY;

    public GloveLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ModelPart root) {
        super(parent);
        right = root.getChild("right");
        left = root.getChild("left");
    }

    /** Gloves for wide (4px) arms; slim arms get the same gloves pulled in by half a pixel. */
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3, 4, -2, 4, 6, 4, new CubeDeformation(0.32f))
                .texOffs(0, 16).addBox(-3, 4, -2, 4, 2, 4, new CubeDeformation(0.55f)), PartPose.ZERO);
        root.addOrReplaceChild("left", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-1, 4, -2, 4, 6, 4, new CubeDeformation(0.32f))
                .texOffs(32, 16).addBox(-1, 4, -2, 4, 2, 4, new CubeDeformation(0.55f)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    public static boolean wearing(LivingEntity e) {
        return Gloves.worn(e) || FuriosoClient.active(e.getId());
    }

    static ItemStack carrier() {
        if (carrier.isEmpty()) carrier = new ItemStack(ModItems.BLACK_SILENCE.get());
        return carrier;
    }

    static boolean slim(AbstractClientPlayer p) {
        return p.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
        if (!wearing(p) || p.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        float slimShift = slim(p) ? 0.5f / 16f : 0;
        // the weapon in hand: Furioso's current one, or the one picked on the wheel
        FuriosoClient.Run r = FuriosoClient.of(p.getId());
        int w = r != null ? FuriosoClient.heldWeapon(r, partial) : Gloves.armed(p) ? GloveClient.selected(p.getId()) : -1;
        // during Furioso the off hand holds the sheath the katana and Durandal are drawn from, until the draw is done
        int sheath = -1;
        if (r != null) {
            double t = r.st(partial);
            if (w == FuriosoScript.KATANA && t < FuriosoScript.IAI + 3) sheath = KATANA_SHEATH;
            else if (w == FuriosoScript.DURANDAL && t < FuriosoScript.DRAW_END + 2) sheath = DURANDAL_SHEATH;
        }
        renderingCaster = r != null ? p.getId() : -1;
        for (boolean rightSide : new boolean[]{true, false}) {
            pose.pushPose();
            (rightSide ? m.rightArm : m.leftArm).translateAndRotate(pose);
            pose.translate(rightSide ? slimShift : -slimShift, 0, 0);
            // fetched per arm: drawing the weapon in between switches render types
            (rightSide ? right : left).render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY);
            if (w >= 0 && (rightSide || FuriosoScript.pair(w))) held(pose, buffers, light, w, rightSide);
            else if (!rightSide && sheath >= 0) held(pose, buffers, light, sheath, false);
            pose.popPose();
        }
        renderingCaster = -1;
    }

    // =================================================================== weapons in the fist

    /** How each weapon is held: like a vanilla sword or axe, the vanilla mace, the 1.21.11 spear, a gun, or claws over the fist. */
    static final int SWORD = 0, MACE = 1, SPEAR = 2, GUN = 3, CLAW = 4;

    /**
     * Per weapon: style, where it's gripped in its model (pixels; melee y is before the generator's shrink and 45 degree
     * tilt), and a size factor on top of the vanilla transform.
     */
    private static final float[][] GRIP = {
            // style, grip x, y, z, size
            {GUN, 8, 4, 5.4f, 0.55f},        // pistol
            {SPEAR, 8, -6, 8, 0.7f},         // lance: by the handle, under the guard
            {MACE, 8, -7, 8, 0.85f},         // hammer: near the end of the haft, like the vanilla mace
            {SWORD, 8, -3, 8, 1},            // katana
            {CLAW, 8, 9, 8, 1},              // gauntlet: built in the arm's own pixels, worn over the glove
            {SWORD, 8, -1.3f, 8, 1.15f},     // knife
            {SWORD, 8, -5.5f, 8, 1},         // axe
            {MACE, 8, -7.5f, 8, 0.9f},       // mace
            {SWORD, 8, -4, 8, 1.1f},         // greatsword
            {SWORD, 8, -0.7f, 8, 1},         // short sword
            {GUN, 8, 6.2f, 7.2f, 0.6f},      // shotgun: at the wrist of the stock
            {SWORD, 8, -2.8f, 8, 1},         // Durandal
            {SWORD, 8, 0, 8, 1},             // the katana's sheath, by the mouth
            {SWORD, 8, -0.4f, 8, 1},         // Durandal's sheath
            {CLAW, 8, 9, 8, 1}};             // the left gauntlet

    static int style(int w) {
        return (int) GRIP[w][0];
    }

    /**
     * Vanilla's right-hand display transforms {rotation, translation (pixels), scale}: item/handheld, item/handheld_mace
     * and 1.21.11's item/spear_in_hand, third person then first person. HAND is where the hand sits on the vanilla sprite.
     */
    private static final float[][][] TP = {
            {{0, -90, 55}, {0, 4, 0.5f}, {0.85f, 0.85f, 0.85f}},
            {{0, -90, 55}, {0, 4, 1}, {1, 1, 1}},
            {{5, 270, -40}, {0, 2, 2}, {1.7f, 1.7f, 0.85f}}};
    private static final float[][][] FP = {
            {{0, -90, 25}, {1.13f, 3.2f, 1.13f}, {0.68f, 0.68f, 0.68f}},
            {{0, -90, 25}, {0, 3, 0.8f}, {0.9f, 0.9f, 0.9f}},
            {{-20, 90, -35}, {3.13f, 2, 0.13f}, {1.36f, 1.36f, 0.68f}}};
    private static final float[][] HAND = {{3, 3, 8}, {2.5f, 2.5f, 8}, {8.85f, 7.15f, 8}};

    private static final Quaternionf[] TURN = new Quaternionf[GRIP.length];

    /** The model's own axes: melee blades run up the 45 degree diagonal (like vanilla sword sprites), guns point down -z. */
    private static final Vector3f BLADE = new Vector3f(0.70710677f, 0.70710677f, 0), WIDTH = new Vector3f(0.70710677f, -0.70710677f, 0);

    /** Guns and claws, in the arm's frame: +y runs down the arm to the fist, -z is the front of the body. */
    /** The gauntlets are authored in the arm's own frame: no turn at all. */
    private static final Quaternionf ON_ARM = new Quaternionf();

    private static Quaternionf turn(int w) {
        if (TURN[w] != null) return TURN[w];
        return TURN[w] = style(w) == GUN
                ? orient(w, new Vector3f(0, 1, 0), new Vector3f(0, 0, -1))   // barrel in line with the arm, as when aiming
                : orient(w, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0));   // claws carry on from the forearm
    }

    private static final Quaternionf[][] FP_TURN = new Quaternionf[2][GRIP.length];

    /**
     * First-person guns: picked in screen space (x right, y up, -z ahead), then carried back into the arm's frame through
     * vanilla's first-person arm rotation (ItemInHandRenderer.renderPlayerArm), so they move with the arm.
     */
    private static Quaternionf fpTurn(int w, boolean rightSide) {
        int i = rightSide ? 0 : 1;
        if (FP_TURN[i][w] != null) return FP_TURN[i][w];
        float f = rightSide ? 1 : -1;
        // ahead, angled a little in toward the crosshair (the line the user drew)
        Quaternionf screen = orient(w, new Vector3f(-0.08f * f, 0.06f, -1), new Vector3f(0, 1, 0));
        Quaternionf arm = new Quaternionf().rotateY((float) Math.toRadians(f * 45)).rotateZ((float) Math.toRadians(f * 120))
                .rotateX((float) Math.toRadians(200)).rotateY((float) Math.toRadians(f * -135));
        return FP_TURN[i][w] = arm.conjugate().mul(screen);
    }

    /** The rotation taking the weapon's own long axis and width onto `to` and `toAcross` (made square to `to`). */
    private static Quaternionf orient(int w, Vector3f to, Vector3f toAcross) {
        boolean gun = style(w) == GUN;
        Vector3f along = gun ? new Vector3f(0, 0, -1) : BLADE, across = gun ? new Vector3f(0, 1, 0) : WIDTH;
        to = new Vector3f(to).normalize();
        toAcross = new Vector3f(toAcross).sub(new Vector3f(to).mul(toAcross.dot(to))).normalize();
        Matrix3f from = new Matrix3f(along, across, new Vector3f(across).cross(along));
        Matrix3f onto = new Matrix3f(to, toAcross, new Vector3f(toAcross).cross(to));
        return new Quaternionf().setFromNormalized(onto.mul(from.transpose()));
    }

    /** The grip in the finished model's pixels. */
    static Vector3f grip(int w) {
        float[] g = GRIP[w];
        if (style(w) >= GUN) return new Vector3f(g[1], g[2], g[3]);
        // the generator shrinks melee models about y = 0, then tilts them 45 degrees about the model's centre
        float d = (g[2] * 0.72f - 8) * 0.70710677f;
        return new Vector3f(8 + d, 8 + d, g[3]);
    }

    /**
     * Third person, from the arm's own frame (after its translateAndRotate): melee weapons go through vanilla's held-item
     * path (ItemInHandLayer) with the matching vanilla display transform; guns and claws sit straight on the fist.
     */
    public static void held(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean rightSide) {
        if (style(w) >= GUN) {
            if (style(w) == GUN) onFist(pose, buffers, light, w, rightSide, turn(w), 1);
            else onFist(pose, buffers, light, rightSide ? w : GAUNTLET_LEFT, rightSide, ON_ARM, 1);
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((rightSide ? 1 : -1) / 16.0F, 0.125F, -0.625F);
        vanilla(pose, buffers, light, w, !rightSide, TP[style(w)], 1);
        pose.popPose();
    }

    /** First-person guns and claws, from the first-person arm's frame. Claws follow the forearm as in third person. */
    static void heldOnFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean rightSide) {
        if (style(w) == GUN) onFist(pose, buffers, light, w, rightSide, fpTurn(w, rightSide), 1.3f);
        else onFist(pose, buffers, light, rightSide ? w : GAUNTLET_LEFT, rightSide, ON_ARM, 1);
    }

    /** First-person melee: the pose is already at vanilla's held-item spot (ItemInHandRenderer.renderArmWithItem). */
    static void heldFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean rightSide) {
        // our hammer and mace heads are far bigger than the vanilla mace's, and right at the camera
        vanilla(pose, buffers, light, w, !rightSide, FP[style(w)], style(w) == MACE ? 0.75f : 1);
    }

    /**
     * Vanilla's display transform (ItemTransform.apply: a left hand mirrors the offset and keeps the right hand's turn,
     * since the lefthand entries are pre-negated and apply negates them back), then the weapon with its grip on the hand.
     */
    private static void vanilla(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean leftHand, float[][] d, float size) {
        BakedModel model = model(w);
        if (model == null) return;
        int s = style(w);
        Vector3f g = grip(w);
        float[] hand = HAND[s];
        float k = GRIP[w][4] * size, r = (float) (Math.PI / 180);
        pose.pushPose();
        pose.translate((leftHand ? -1 : 1) * d[1][0] / 16f, d[1][1] / 16f, d[1][2] / 16f);
        pose.mulPose(new Quaternionf().rotationXYZ(d[0][0] * r, d[0][1] * r, d[0][2] * r));
        pose.scale(d[2][0], d[2][1], d[2][2]);
        pose.translate(hand[0] / 16f - 0.5f, hand[1] / 16f - 0.5f, hand[2] / 16f - 0.5f);
        pose.scale(k, k, k);
        // the spear's blade runs up the other diagonal
        if (s == SPEAR) pose.mulPose(Axis.ZP.rotationDegrees(90));
        // the item renderer centres the model (-0.5): undo that and put the grip on the hand
        pose.translate(0.5f - g.x / 16f, 0.5f - g.y / 16f, 0.5f - g.z / 16f);
        // Durandal in a Furioso: tell it where the blade really is, for the edge glow
        if (w == FuriosoScript.DURANDAL && renderingCaster >= 0) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            FuriosoClient.blade(renderingCaster, world(pose, bladePoint(3), cam), world(pose, bladePoint(29), cam));
        }
        draw(pose, buffers, light, model);
        pose.popPose();
    }

    /** A first-person gun drawn straight down the crosshair from `pose` (camera space), grip at the origin. */
    static void gunFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, int w, float scale) {
        BakedModel model = model(w);
        if (model == null) return;
        Vector3f g = grip(w);
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate((8 - g.x) / 16f, (8 - g.y) / 16f, (8 - g.z) / 16f);
        draw(pose, buffers, light, model);
        pose.popPose();
    }

    /** Whose Furioso the layer is drawing right now, or -1. */
    private static int renderingCaster = -1;

    /** A point on a melee model's centre line, from the authored height (before the generator's shrink and tilt). */
    private static Vector3f bladePoint(float y) {
        float d = (y * 0.72f - 8) * 0.70710677f;
        return new Vector3f(8 + d, 8 + d, 8);
    }

    /** Model pixels to world space, through the pose the item renderer is about to use (it shifts by -0.5 itself). */
    private static Vec3 world(PoseStack pose, Vector3f px, Vec3 cam) {
        org.joml.Vector4f v = new org.joml.Vector4f(px.x / 16f - 0.5f, px.y / 16f - 0.5f, px.z / 16f - 0.5f, 1).mul(pose.last().pose());
        return new Vec3(v.x + cam.x, v.y + cam.y, v.z + cam.z);
    }

    private static void onFist(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean rightSide, Quaternionf q, float size) {
        onFist(pose, buffers, light, w, rightSide, q, size, 0);
    }

    /** `lift`: pixels along the weapon's own up, after it's turned. */
    private static void onFist(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean rightSide, Quaternionf q, float size, float lift) {
        BakedModel model = model(w);
        if (model == null) return;
        Vector3f g = grip(w);
        float k = GRIP[w][4] * size;
        pose.pushPose();
        pose.translate((rightSide ? -1 : 1) / 16f, 9 / 16f, 0);
        pose.mulPose(q);
        pose.translate(0, lift / 16f, 0);
        pose.scale(k, k, k);
        // the item renderer centres the model (-0.5), so put the grip at the origin instead
        pose.translate((8 - g.x) / 16f, (8 - g.y) / 16f, (8 - g.z) / 16f);
        draw(pose, buffers, light, model);
        pose.popPose();
    }

    /**
     * A colour to draw the weapons in instead of their own (r, g, b, alpha), or null: Roland's smoke-wrapped weapons
     * and Angelica's white ones. Set it around a call to held() and clear it after.
     */
    @javax.annotation.Nullable
    public static float[] tint;
    private static final net.minecraft.util.RandomSource QUAD_RANDOM = net.minecraft.util.RandomSource.create();

    /** Draws a weapon model where the item renderer would, in its own colours or tinted. */
    public static void drawModel(PoseStack pose, MultiBufferSource buffers, int light, BakedModel model) {
        draw(pose, buffers, light, model);
    }

    static void draw(PoseStack pose, MultiBufferSource buffers, int light, BakedModel model) {
        if (tint == null) {
            Minecraft.getInstance().getItemRenderer().render(carrier(), ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
            if (distorted) mist(pose, buffers, model);
            return;
        }
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)); // not emissive: shader packs bloom that
        pose.pushPose();
        pose.translate(-0.5f, -0.5f, -0.5f);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            QUAD_RANDOM.setSeed(42);
            for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(null, d, QUAD_RANDOM))
                vc.putBulkData(pose.last(), q, tint[0], tint[1], tint[2], tint[3], light, OverlayTexture.NO_OVERLAY);
        }
        QUAD_RANDOM.setSeed(42);
        for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(null, null, QUAD_RANDOM))
            vc.putBulkData(pose.last(), q, tint[0], tint[1], tint[2], tint[3], light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** The black mist round a distorted weapon: the weapon again, swollen and see-through black, three times, wavering. */
    private static void mist(PoseStack pose, MultiBufferSource buffers, BakedModel model) {
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        for (int i = 1; i <= 3; i++) {
            float s = 1 + i * (0.09f + 0.02f * net.minecraft.util.Mth.sin(mistTime * 0.4f + i * 1.7f));
            float a = 0.42f / i;
            pose.pushPose();
            // swollen about the model's middle, drifting a little as it wavers
            pose.translate(0.02f * net.minecraft.util.Mth.sin(mistTime * 0.3f + i), 0.02f * net.minecraft.util.Mth.cos(mistTime * 0.25f + i * 2), 0);
            pose.scale(s, s, s);
            pose.translate(-0.5f, -0.5f, -0.5f);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                QUAD_RANDOM.setSeed(42);
                for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(null, d, QUAD_RANDOM))
                    vc.putBulkData(pose.last(), q, 0.02f, 0.02f, 0.03f, a, MIST_LIGHT, OverlayTexture.NO_OVERLAY);
            }
            QUAD_RANDOM.setSeed(42);
            for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(null, null, QUAD_RANDOM))
                vc.putBulkData(pose.last(), q, 0.02f, 0.02f, 0.03f, a, MIST_LIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static final int MIST_LIGHT = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

    public static ModelResourceLocation location(int w) {
        return location(w, false);
    }

    /** The weapon as the Black Silence's distortion holds it: gone black, with models/furioso_distorted. */
    public static ModelResourceLocation location(int w, boolean distorted) {
        return ModelResourceLocation.standalone(ProjectMinecraft.id((distorted ? "furioso_distorted/" : "furioso/") + (w < WEAPON_MODELS.length ? WEAPON_MODELS[w] : SHEATH_MODELS[w - WEAPON_MODELS.length])));
    }

    /**
     * Set around a call to held(): draw the distorted (black) weapons with black mist pouring off them. `mistTime` is
     * the age the mist flickers with.
     */
    public static boolean distorted;
    public static float mistTime;

    public static BakedModel model(int w) {
        if (w < 0 || w >= MODELS) return null;
        return Minecraft.getInstance().getModelManager().getModel(location(w, distorted));
    }
}
