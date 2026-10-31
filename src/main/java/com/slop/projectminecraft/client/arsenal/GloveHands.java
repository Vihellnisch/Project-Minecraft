package com.slop.projectminecraft.client.arsenal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.arsenal.Gloves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person with the Black Silence worn and the main hand empty. Blades, hafts and the lance are held exactly like
 * vanilla's sword, axe, mace and spear (no arm drawn, as vanilla does); guns and claws show both gloved fists.
 */
public final class GloveHands {
    private GloveHands() {}

    private static ModelPart gloves;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isInvisible() || !Gloves.armed(p)) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer renderer)) return;
        if (gloves == null) gloves = mc.getEntityModels().bakeLayer(GloveLayer.LAYER);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float partial = event.getPartialTick();
        HumanoidArm main = p.getMainArm();
        // paired weapons swing with whichever hand struck last
        HumanoidArm swinging = p.swingingArm == InteractionHand.OFF_HAND ? main.getOpposite() : main;
        float swing = p.getAttackAnim(partial), equip = event.getEquipProgress();
        int w = GloveClient.selected(p.getId());
        int style = GloveLayer.style(w);
        if (w == FuriosoScript.SHOTGUN) {
            shotgun(pose, buffers, light, p, renderer, GloveClient.recoil(p.getId(), true, partial), equip);
            return;
        }
        for (HumanoidArm side : new HumanoidArm[]{main, main.getOpposite()}) {
            boolean right = side == HumanoidArm.RIGHT, weapon = side == main || FuriosoScript.pair(w);
            float s = side == swinging ? swing : 0;
            if (style < GloveLayer.GUN) {
                if (weapon) item(pose, buffers, light, w, right, s, equip);
            } else {
                // pistols kick instead of swinging
                float kick = style == GloveLayer.GUN ? GloveClient.recoil(p.getId(), right, partial) : 0;
                arm(pose, buffers, light, p, renderer, side, style == GloveLayer.GUN ? 0 : s, equip, weapon ? w : -1, kick);
            }
        }
    }

    /** First-person pistols, no arms (like the swords): each grip low in its corner, the barrel straight down the crosshair. */
    private static final float PISTOL_SCALE = 0.55f;

    private static void pistol(PoseStack pose, MultiBufferSource buffers, int light, boolean right, float kick, float equip) {
        float f = right ? 1 : -1;
        pose.pushPose();
        pose.translate(f * 0.34F, -0.36F - equip * 0.6F + 0.04F * kick, -0.62F + 0.12F * kick);
        // the kick tips the muzzle up
        pose.mulPose(Axis.XP.rotationDegrees(12 * kick));
        GloveLayer.gunFirstPerson(pose, buffers, light, FuriosoScript.PISTOLS, PISTOL_SCALE);
        pose.popPose();
    }

    /** Where the shotgun's grip sits in camera space (x right, y up, -z ahead), its drawn size, and where each arm reaches from. */
    private static final Vector3f SHOTGUN_AT = new Vector3f(0.32f, -0.4f, -0.95f);
    private static final float SHOTGUN_SCALE = 0.7f;
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(0.6f, -1.5f, -0.55f), LEFT_SHOULDER = new Vector3f(-0.25f, -1.5f, -1.1f);

    /** Two hands on the shotgun: it points down the crosshair, the right fist on the grip and the left under the barrel. */
    private static void shotgun(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerRenderer renderer, float kick, float equip) {
        pose.pushPose();
        pose.translate(0, 0.05F * kick - equip * 0.6F, 0.14F * kick);
        pose.mulPose(Axis.XP.rotationDegrees(10 * kick));
        pose.pushPose();
        pose.translate(SHOTGUN_AT.x, SHOTGUN_AT.y, SHOTGUN_AT.z);
        GloveLayer.gunFirstPerson(pose, buffers, light, FuriosoScript.SHOTGUN, SHOTGUN_SCALE);
        pose.popPose();
        // both fists hold it from below: the right under the grip, the left under the fore-end, about 11 pixels ahead
        Vector3f grip = new Vector3f(SHOTGUN_AT).add(0, -0.1f, 0.02f);
        Vector3f fore = new Vector3f(SHOTGUN_AT).add(0, -0.07f, -11 / 16f * SHOTGUN_SCALE);
        reach(pose, buffers, light, p, renderer, true, grip, RIGHT_SHOULDER);
        reach(pose, buffers, light, p, renderer, false, fore, LEFT_SHOULDER);
        pose.popPose();
    }

    /** One gloved arm, its fist at `hand`, coming from `shoulder` (camera space). */
    private static void reach(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerRenderer renderer, boolean right,
                              Vector3f hand, Vector3f shoulder) {
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm, sleeve = right ? model.rightSleeve : model.leftSleeve;
        Vector3f dir = new Vector3f(hand).sub(shoulder).normalize();
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        // the arm's +y runs from the shoulder to the fist
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z));
        // the arm boxes run y -2..10 about their pivot, off-centre by a pixel sideways: put the fist's end on the hand
        pose.translate((right ? 1 : -1) / 16f, -9 / 16f, 0);
        PartPose armPose = arm.storePose(), sleevePose = sleeve.storePose();
        arm.setPos(0, 0, 0);
        arm.setRotation(0, 0, 0);
        sleeve.setPos(0, 0, 0);
        sleeve.setRotation(0, 0, 0);
        var skin = p.getSkin().texture();
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        arm.loadPose(armPose);
        sleeve.loadPose(sleevePose);
        float slim = GloveLayer.slim(p) ? 0.5f / 16f : 0;
        pose.translate(right ? slim : -slim, 0, 0);
        gloves.getChild(right ? "right" : "left").render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GloveLayer.TEX)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Vanilla's held-item placement (ItemInHandRenderer.renderArmWithItem for a plain item), then the weapon. */
    private static void item(PoseStack pose, MultiBufferSource buffers, int light, int w, boolean right, float swing, float equip) {
        int i = right ? 1 : -1;
        float sq = Mth.sqrt(swing);
        pose.pushPose();
        pose.translate(i * -0.4F * Mth.sin(sq * (float) Math.PI), 0.2F * Mth.sin(sq * ((float) Math.PI * 2F)), -0.2F * Mth.sin(swing * (float) Math.PI));
        pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float a = Mth.sin(swing * swing * (float) Math.PI), b = Mth.sin(sq * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(i * (45.0F + a * -20.0F)));
        pose.mulPose(Axis.ZP.rotationDegrees(i * b * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(b * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(i * -45.0F));
        GloveLayer.heldFirstPerson(pose, buffers, light, w, right);
        pose.popPose();
    }

    /** Vanilla's empty-hand arm transform (ItemInHandRenderer.renderPlayerArm), then the arm, its glove and the weapon in it. */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerRenderer renderer, HumanoidArm side,
                            float swing, float equip, int weapon, float kick) {
        boolean right = side == HumanoidArm.RIGHT;
        float f = right ? 1 : -1;
        float sq = Mth.sqrt(swing);
        pose.pushPose();
        // recoil: the fist jumps back and up, then settles
        pose.translate(0, 0.06F * kick, 0.16F * kick);
        pose.mulPose(Axis.XP.rotationDegrees(14 * kick));
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
        if (right) renderer.renderRightHand(pose, buffers, light, p);
        else renderer.renderLeftHand(pose, buffers, light, p);
        // the glove follows the arm part the renderer just posed
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart armPart = right ? model.rightArm : model.leftArm;
        armPart.translateAndRotate(pose);
        float slim = GloveLayer.slim(p) ? 0.5f / 16f : 0;
        pose.translate(right ? slim : -slim, 0, 0);
        gloves.getChild(right ? "right" : "left").render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GloveLayer.TEX)), light, OverlayTexture.NO_OVERLAY);
        if (weapon >= 0) GloveLayer.heldOnFirstPersonArm(pose, buffers, light, weapon, right);
        pose.popPose();
    }
}
