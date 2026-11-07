package com.slop.projectminecraft.client.boss;

import static com.slop.projectminecraft.warp.ClawWarp.*;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.boss.Claw;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.client.ScreenFlash;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.ClawStagePayload;
import com.slop.projectminecraft.registry.ModEntities;
import com.slop.projectminecraft.registry.ModSounds;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Serum W, "through every floor", from inside the scene, Library of Ruina style: a letterboxed side view where he has you
 * by the throat and rushes you across a place, a slash at the end of it, then a hard cut to the next place and again,
 * three times, then the void, the barrage of cuts, and home for the shatter and the X.
 * <p>
 * The places are backdrops the server copied out of each dimension (ClawStage). While it plays, the real world is drawn
 * over completely: depth cleared after it, then a sky box, the backdrop, and the two of them as actors (the Claw, or the
 * Handmade Claw's user, and the victim). Nobody actually moves. Players watching from outside see the victim held in a
 * shimmer of blue instead.
 */
public final class ClawWarpClient {
    private ClawWarpClient() {}

    static final RandomSource R = RandomSource.create();
    /** Ticks since the grab for the scene we're in (-1: none). */
    static int t = -1;
    static int victim = -1, rider = -1, attacker = -1;
    /** The Claw drags (else the Handmade Claw's user does). */
    static boolean byClaw;
    static long seed;
    /** Where the scene is staged: the victim's spot when it began (the real world is hidden, so any spot will do). */
    static Vec3 origin = Vec3.ZERO;
    static final ClawStage[] STAGES = new ClawStage[HOPS + 1];
    /** Backdrops that came in for a run not started yet (they're sent just before the grab). */
    private static final Map<Long, ClawStagePayload> PENDING = new HashMap<>();
    /** The Claw drawn in the scene (the real one waits, unseen, where it began). */
    @Nullable
    static Claw ghost;
    /** The dragger's pose: 0 holding them up, 1 rushing, 2 diving in for the grab. */
    static int ghostPose;
    /** The player being drawn as the dragger right now (the Handmade Claw's user), for pose(); -1 when none. */
    private static int posing = -1;
    /** Runs seen from outside: victim (and rider) entity id -> clock when it began. */
    private static final Int2IntOpenHashMap OUTSIDE = new Int2IntOpenHashMap();
    private static int clock;

    public static boolean active() {
        return t >= 0;
    }

    /** Tours: ticks into the scene (-1: none), and the places its backdrops came from. */
    public static int time() {
        return t;
    }

    public static String stages() {
        StringBuilder sb = new StringBuilder();
        for (ClawStage s : STAGES) sb.append(s == null ? "missing" : s.dimension).append(' ');
        return sb.toString().trim();
    }

    static boolean involvesMe() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.player.getId() == victim || mc.player.getId() == rider);
    }

    /** A backdrop, ahead of its run's grab. */
    public static void stage(ClawStagePayload p) {
        PENDING.put(((long) p.victim() << 8) | p.index(), p);
    }

    static void handle(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        int kind = p.i(0), who = p.i(1), step = p.i(2), by = p.i(3);
        boolean me = mc.player != null && (who == mc.player.getId() || by == mc.player.getId());
        Vec3 at = p.a().add(0, 1, 0);
        switch (kind) {
            case FX_WARP_GRAB -> {
                for (int i = 0; i < 6; i++) ArcFx.addBeam(at.add(R.nextGaussian() * 0.8, R.nextGaussian() * 1.0, R.nextGaussian() * 0.8), at, 8, 0.08f, 0.5f, 0.75f, 1.0f, false);
                for (int i = 0; i < 30; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, R.nextGaussian() * 0.4, R.nextGaussian() * 0.4, R.nextGaussian() * 0.4);
                if (me && t < 0) begin(p);
                else if (!me) {
                    OUTSIDE.put(who, clock);
                    if (by >= 0) OUTSIDE.put(by, clock);
                }
            }
            case FX_WARP_HOP -> {
                if (me) t = Math.max(t, GRAB + step * HOP);
            }
            case FX_WARP_VOID -> {
                if (me) t = Math.max(t, VOID_AT);
            }
            case FX_WARP_SHATTER -> {
                for (int i = 0; i < 30; i++) level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, R.nextGaussian() * 0.5, R.nextGaussian() * 0.5, R.nextGaussian() * 0.5);
                if (me) {
                    t = Math.max(t, SHATTER_AT);
                    sound(ModSounds.CLAW_SHATTER.get(), 1.0f);
                    CameraShake.add(1.0f);
                }
            }
            case FX_WARP_END -> {
                OUTSIDE.remove(who);
                if (by >= 0) OUTSIDE.remove(by);
                if (me) clear();
            }
            default -> {}
        }
    }

    /** The scene starts: its backdrops, the camera. */
    private static void begin(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        clear();
        t = 0;
        victim = p.i(1);
        rider = p.i(3);
        byClaw = p.i(4) == 1;
        attacker = p.i(5);
        seed = R.nextLong();
        origin = p.a();
        for (int i = 0; i <= HOPS; i++) {
            ClawStagePayload s = PENDING.remove(((long) victim << 8) | i);
            STAGES[i] = s == null ? null : new ClawStage(s);
        }
        PENDING.clear();
        sound(ModSounds.CLAW_INJECT.get(), 1.0f);
        CameraShake.add(0.6f);
        scene = new Scene();
        CinematicCamera.play(scene);
    }

    @Nullable
    private static Scene scene;

    private static void stopScene() {
        if (scene != null && CinematicCamera.active()) CinematicCamera.stop();
        scene = null;
    }

    static void clear() {
        stopScene();
        t = -1;
        victim = rider = attacker = -1;
        byClaw = false;
        ghost = null;
        for (int i = 0; i < STAGES.length; i++) {
            if (STAGES[i] != null) STAGES[i].close();
            STAGES[i] = null;
        }
    }

    private static void sound(net.minecraft.sounds.SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 1.0f));
    }

    // ------------------------------------------------------------------ the timeline

    /** Which backdrop shows at scene time tt: 0 where it began (the grab), then one per stage. */
    static int stageAt(float tt) {
        return tt < GRAB ? 0 : Math.min(HOPS, 1 + (int) ((tt - GRAB) / HOP));
    }

    /** Ticks into the current stage. */
    static float local(float tt) {
        return tt < GRAB ? tt : (tt - GRAB) % HOP;
    }

    /**
     * Which way they rush across a stage: always the way that keeps the claw arm on the camera's side (the side view looks
     * at them from +z), so it's never hidden behind the victim. The Claw's claw is his left arm; a player's is their main arm.
     */
    static float dir(int stage) {
        if (byClaw) return -1;
        Minecraft mc = Minecraft.getInstance();
        Entity a = mc.level == null ? null : mc.level.getEntity(rider);
        return a instanceof net.minecraft.world.entity.player.Player p && p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1 : 1;
    }

    /**
     * Leaning into the rush: the torso tips forward about the neck, so the hips swing back; the legs move
     * with it instead of staying where they were, which left the torso floating off the legs.
     */
    public static void lean(net.minecraft.client.model.HumanoidModel<?> m, float angle) {
        float s = Mth.sin(angle), c = Mth.cos(angle);
        m.body.xRot = angle;
        m.rightLeg.y = m.leftLeg.y = 12 * c;
        m.rightLeg.z = m.leftLeg.z = 12 * s;
        if (m instanceof net.minecraft.client.model.PlayerModel<?> pm) {
            pm.jacket.copyFrom(m.body);
            pm.rightPants.copyFrom(m.rightLeg);
            pm.leftPants.copyFrom(m.leftLeg);
            pm.rightSleeve.copyFrom(m.rightArm);
            pm.leftSleeve.copyFrom(m.leftArm);
        }
    }

    /** How far either side of the middle the rush runs. */
    static final float RUSH_HALF = 8;

    /** 0..1 across a place, at stage time u. */
    static float rush(float u) {
        return Mth.clamp((u - RUSH_FROM) / (HOP - RUSH_FROM), 0, 1);
    }

    /** Where the dragger stands along the stage (x). */
    static float dragX(float tt) {
        int s = stageAt(tt);
        float u = local(tt);
        if (s == 0) {
            // the grab: he dives in from the far side to where you stand
            float k = CinematicCamera.ease(u, 4, 11);
            return (Mth.lerp(k, -7.5f, 0.6f) - 0.9f) * dir(0);
        }
        // never stopping: one steady streak from one edge of the place to the other
        float d = dir(s);
        return Mth.lerp(rush(u), -RUSH_HALF * d, RUSH_HALF * d);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        clock++;
        if (t < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        t++;
        if (t > WATCHDOG + 40) {
            clear();
            return;
        }
        // into the void: the camera is yours again (under the black)
        if (t >= VOID_AT && scene != null) stopScene();
        if (mc.player != null && involvesMe()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.fallDistance = 0;
        }
        // the beats: each cut, the rush, the slash
        int s = stageAt(t);
        int u = (int) local(t);
        if (s == 0 && u == 10) {
            sound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.6f);
            CameraShake.add(0.5f);
        }
        if (s > 0 && t < VOID_AT) {
            if (u == 0) {
                sound(ModSounds.CLAW_BLINK.get(), 0.8f + s * 0.12f);
                CameraShake.add(0.4f);
            }
            if (u == RUSH_FROM) sound(ModSounds.CLAW_CHARGE.get(), 1.4f);
            if (u == RUSH_TO) {
                sound(ModSounds.CLAW_SLASH.get(), 0.9f + s * 0.1f);
                ScreenFlash.flash(0.3f);
                ImpactFrame.trigger(ImpactFrame.CYAN, 110);
                CameraShake.add(0.9f);
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (t < 0 || !involvesMe()) return;
        var in = event.getInput();
        in.forwardImpulse = in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    // ------------------------------------------------------------------ the camera

    /** A side view from in front of the stage, keeping up with them as they rush, until the void (it's stopped there). */
    static final class Scene implements CinematicCamera.Director {
        @Override
        public int duration() {
            return WATCHDOG;
        }

        @Override
        public void frame(float ignored, CinematicCamera.Shot s) {
            // on the scene's own clock (it keeps in step with the server's), not the camera's
            float tt = Math.max(0, t) + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            float x = dragX(tt);
            int st = stageAt(tt);
            float u = local(tt);
            float follow = st == 0 ? 0.25f : 0.8f;
            float rush = st > 0 ? rush(u) : 0;
            Vec3 from = origin.add(x * follow, 1.7, 8.6 - 0.8 * Mth.sin(rush * Mth.PI));
            s.lookAt(from, origin.add(x * 0.85f + (st > 0 ? dir(st) * 0.4f : 0), 1.25, 0));
            s.fov = 56;
            s.roll = st > 0 ? dir(st) * 4 * Mth.sin(rush * Mth.PI) : 0;
        }
    }

    // ------------------------------------------------------------------ drawing the scene over the world

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            outside(event);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || t < 0 || !involvesMe()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float tt = t + partial;
        if (tt >= VOID_AT) return;
        ClawStage stage = STAGES[stageAt(tt)];
        // build the backdrops ahead of time, one a frame, so no cut ever waits on one
        for (ClawStage s : STAGES) {
            if (s != null && !s.ready()) {
                s.prepare();
                break;
            }
        }
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // nothing of the real world shows through from here on: what it still has queued goes out first (it'd land on
        // top otherwise), then its depth is wiped (depth writes back on: a clear doesn't touch depth while they're off)
        buffers.endBatch();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Matrix4f m = new Matrix4f();
        int sky = stage == null ? 0x0A1020 : stage.sky, fog = stage == null ? 0x101828 : stage.fog;
        boolean roof = stage != null && stage.ceiling;
        VertexConsumer box = buffers.getBuffer(FxRenderTypes.SOLID_COLOR);
        skyBox(box, m, origin.subtract(cam), roof ? dim(fog, 0.45f) : sky, roof ? dim(fog, 0.8f) : fog);
        buffers.endBatch(FxRenderTypes.SOLID_COLOR);
        if (stage != null) {
            // the slab: its column under the victim's feet at the origin
            Vec3 o = origin.subtract(cam).add(-stage.sx / 2.0, -BELOW, -(stage.sz - 1 - FRONT));
            float[] tint = tint(stage);
            stage.draw(event.getModelViewMatrix(), event.getProjectionMatrix(), o.x, o.y, o.z, tint[0], tint[1], tint[2]);
        }
        actors(event, mc, buffers, tt, cam, partial);
        // light: streaks of the rush, the slash
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        if (sky == 0 || stage != null && stage.dimension.endsWith("the_end")) stars(glow, m, origin.subtract(cam));
        streaks(glow, m, tt, cam);
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    private static int dim(int rgb, float k) {
        return ((int) (((rgb >> 16) & 255) * k) << 16) | ((int) (((rgb >> 8) & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }

    /** A little of the place's haze over its blocks: red in the Nether, violet in the End. */
    private static float[] tint(ClawStage s) {
        int f = s.fog;
        float r = ((f >> 16) & 255) / 255f, g = ((f >> 8) & 255) / 255f, b = (f & 255) / 255f;
        float k = s.ceiling ? 0.35f : 0.15f;
        return new float[]{Mth.lerp(k, 1, Math.min(1, r * 1.6f)), Mth.lerp(k, 1, Math.min(1, g * 1.6f)), Mth.lerp(k, 1, Math.min(1, b * 1.6f))};
    }

    /** The world beyond the slab: a box all round, sky at the top fading to the haze at the horizon. */
    private static void skyBox(VertexConsumer vc, Matrix4f m, Vec3 o, int top, int horizon) {
        float tr = ((top >> 16) & 255) / 255f, tg = ((top >> 8) & 255) / 255f, tb = (top & 255) / 255f;
        float hr = ((horizon >> 16) & 255) / 255f, hg = ((horizon >> 8) & 255) / 255f, hb = (horizon & 255) / 255f;
        double x0 = o.x - 60, x1 = o.x + 60, y0 = o.y - 30, y1 = o.y + 40, z0 = o.z - 60, z1 = o.z + 30;
        double ym = o.y + 2;
        Vec3[][] walls = {
                {new Vec3(x0, 0, z0), new Vec3(x1, 0, z0)}, {new Vec3(x1, 0, z0), new Vec3(x1, 0, z1)},
                {new Vec3(x1, 0, z1), new Vec3(x0, 0, z1)}, {new Vec3(x0, 0, z1), new Vec3(x0, 0, z0)}};
        for (Vec3[] w : walls) {
            // lower band: haze; upper band: haze to sky
            quad(vc, m, w[0].x, y0, w[0].z, w[1].x, y0, w[1].z, w[1].x, ym, w[1].z, w[0].x, ym, w[0].z, hr, hg, hb, hr, hg, hb);
            quad(vc, m, w[0].x, ym, w[0].z, w[1].x, ym, w[1].z, w[1].x, y1, w[1].z, w[0].x, y1, w[0].z, hr, hg, hb, tr, tg, tb);
        }
        quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, tr, tg, tb, tr, tg, tb);
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, hr * 0.5f, hg * 0.5f, hb * 0.5f, hr * 0.5f, hg * 0.5f, hb * 0.5f);
    }

    /** A quad, the first two corners in colour a, the last two in colour b. */
    private static void quad(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                             double dx, double dy, double dz, float ar, float ag, float ab, float br, float bg, float bb) {
        FxRenderTypes.vertex(vc, m, new Vec3(ax, ay, az), ar, ag, ab, 1);
        FxRenderTypes.vertex(vc, m, new Vec3(bx, by, bz), ar, ag, ab, 1);
        FxRenderTypes.vertex(vc, m, new Vec3(cx, cy, cz), br, bg, bb, 1);
        FxRenderTypes.vertex(vc, m, new Vec3(dx, dy, dz), br, bg, bb, 1);
    }

    /** Stars on the far wall (the End, or any place with a black sky). */
    private static void stars(VertexConsumer vc, Matrix4f m, Vec3 o) {
        RandomSource r = RandomSource.create(seed ^ 99);
        for (int i = 0; i < 160; i++) {
            Vec3 p = o.add((r.nextDouble() - 0.5) * 110, -5 + r.nextDouble() * 42, -59.5);
            float s = 0.08f + r.nextFloat() * 0.18f, a = 0.4f + r.nextFloat() * 0.6f;
            FxRenderTypes.vertex(vc, m, p.add(-s, -s, 0), 0.8f, 0.85f, 1, a);
            FxRenderTypes.vertex(vc, m, p.add(s, -s, 0), 0.8f, 0.85f, 1, a);
            FxRenderTypes.vertex(vc, m, p.add(s, s, 0), 0.8f, 0.85f, 1, a);
            FxRenderTypes.vertex(vc, m, p.add(-s, s, 0), 0.8f, 0.85f, 1, a);
        }
    }

    /** The two of them: the dragger, and the victim hanging from their hand by the throat. */
    private static void actors(RenderLevelStageEvent event, Minecraft mc, MultiBufferSource.BufferSource buffers, float tt, Vec3 cam, float partial) {
        int st = stageAt(tt);
        float u = local(tt);
        float x = dragX(tt);
        // which way they face along the stage
        float d = dir(st);
        boolean grabbed = st > 0 || u >= 11;
        boolean rushing = st > 0 && u >= RUSH_FROM;
        float yawFacing = d > 0 ? -90 : 90;
        Vec3 dragger = origin.add(x, 0, 0);
        // lifted just off their feet, their throat at the height of the dragger's raised hand
        Vec3 held = grabbed ? dragger.add(0.8 * d, 0.12 + 0.04 * Mth.sin(tt * 1.3f), 0) : origin.add(0.6 * d, 0, 0);
        // the victim kicks a little while held, then hangs limp after a slash
        float sway = grabbed ? Mth.sin(tt * 0.9f) * (u < RUSH_TO ? 6 : 2) : 0;
        Entity v = mc.level.getEntity(victim);
        Entity a = byClaw ? ghost() : mc.level.getEntity(rider);
        PoseStack pose = event.getPoseStack();
        // their real shadows would land on the real world, not the backdrop
        var dispatcher = mc.getEntityRenderDispatcher();
        boolean shadows = mc.options.entityShadows().get();
        dispatcher.setRenderShadow(false);
        if (a != null) {
            ghostPose = !grabbed ? 2 : rushing ? 1 : 0;
            posing = a.getId();
            try {
                draw(mc, a, dragger.subtract(cam), yawFacing, 0, partial, pose, buffers);
            } finally {
                posing = -1;
            }
        }
        if (v != null) draw(mc, v, held.subtract(cam), yawFacing + 180, grabbed ? -12 + sway : 0, partial, pose, buffers);
        buffers.endBatch();
        dispatcher.setRenderShadow(shadows);
    }

    /** One actor at `at` (camera-relative), facing `yaw`, tipped back by `tilt` degrees; its own rotations put back after. */
    private static void draw(Minecraft mc, Entity e, Vec3 at, float yaw, float tilt, float partial, PoseStack pose, MultiBufferSource buffers) {
        float yr = e.getYRot(), yro = e.yRotO, xr = e.getXRot(), xro = e.xRotO;
        float body = 0, bodyO = 0, head = 0, headO = 0;
        LivingEntity l = e instanceof LivingEntity le ? le : null;
        if (l != null) {
            body = l.yBodyRot;
            bodyO = l.yBodyRotO;
            head = l.yHeadRot;
            headO = l.yHeadRotO;
            l.yBodyRot = l.yBodyRotO = l.yHeadRot = l.yHeadRotO = yaw;
        }
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.setXRot(0);
        e.xRotO = 0;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        if (tilt != 0) {
            // tipped about their own feet, the head going back
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tilt));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        }
        mc.getEntityRenderDispatcher().render(e, 0, 0, 0, yaw, partial, pose, buffers, 0xF000F0);
        pose.popPose();
        e.setYRot(yr);
        e.yRotO = yro;
        e.setXRot(xr);
        e.xRotO = xro;
        if (l != null) {
            l.yBodyRot = body;
            l.yBodyRotO = bodyO;
            l.yHeadRot = head;
            l.yHeadRotO = headO;
        }
    }

    /**
     * The Handmade Claw's user as the dragger (from HumanoidModelMixin, after vanilla's pose): the lunge with the claw arm
     * thrown out, then that arm locked straight out holding them by the throat, leaning into the rush mid-stride.
     */
    public static void pose(net.minecraft.client.model.HumanoidModel<?> m, LivingEntity e) {
        if (e.getId() != posing) return;
        boolean right = !(e instanceof net.minecraft.world.entity.player.Player p) || p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        var arm = right ? m.rightArm : m.leftArm;
        var other = right ? m.leftArm : m.rightArm;
        float side = right ? 1 : -1, age = e.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        m.head.yRot = 0;
        m.body.yRot = 0;
        if (ghostPose == 2) {
            // the lunge: claw thrown out ahead, low and fast
            arm.xRot = -1.55f;
            arm.yRot = 0.1f * side;
            arm.zRot = 0;
            other.xRot = 0.8f;
            other.zRot = 0.1f * side;
        } else {
            // the claw arm straight out and up a little, round their throat; the other arm back
            arm.xRot = -2.05f + Mth.sin(age * 0.6f) * 0.03f;
            arm.yRot = 0.25f * side;
            arm.zRot = 0;
            other.xRot = ghostPose == 1 ? 0.9f : 0.2f;
            other.zRot = 0.15f * side;
        }
        if (ghostPose > 0) {
            // leaning into it, mid-stride
            lean(m, 0.4f);
            float stride = Mth.sin(age * 1.6f);
            m.rightLeg.xRot = -0.8f * stride;
            m.leftLeg.xRot = 0.8f * stride;
            m.head.xRot = -0.25f;
        } else {
            m.body.xRot = 0;
            m.rightLeg.xRot = m.leftLeg.xRot = 0;
            m.head.xRot = 0.1f;
        }
        m.hat.copyFrom(m.head);
    }

    @Nullable
    private static Claw ghost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (ghost == null || ghost.level() != mc.level) ghost = ModEntities.CLAW.get().create(mc.level);
        if (ghost != null) ghost.tickCount = Math.max(0, t);
        return ghost;
    }

    /** The rush leaves streaks of blue behind them; each slash lands as an X across the victim. */
    private static void streaks(VertexConsumer vc, Matrix4f m, float tt, Vec3 cam) {
        int st = stageAt(tt);
        if (st == 0) return;
        float u = local(tt), d = dir(st);
        float from = -RUSH_HALF * d, x = dragX(tt);
        if (u >= RUSH_FROM) {
            float fade = 1 - Mth.clamp((u - (HOP - 3)) / 3f, 0, 1);
            for (int i = 0; i < 4; i++) {
                double y = 0.5 + i * 0.4;
                Vec3 a = origin.add(from, y, 0.1 * i).subtract(cam), b = origin.add(x, y, 0.1 * i).subtract(cam);
                FxRenderTypes.ribbon(vc, m, a, b, 0.22f, 0.35f, 0.6f, 1.0f, 0, 0.5f * fade);
                FxRenderTypes.ribbon(vc, m, a, b, 0.05f, 0.9f, 0.95f, 1.0f, 0, 0.9f * fade);
            }
        }
        if (u >= RUSH_TO && u < HOP) {
            float k = (u - RUSH_TO) / (HOP - RUSH_TO), a = 1 - k, len = 1.1f + k * 0.6f;
            Vec3 c = origin.add(x + 0.8 * d, 1.4, 0.3).subtract(cam);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3 p = c.add(-len, -len * sgn, 0), q = c.add(len, len * sgn, 0);
                FxRenderTypes.ribbon(vc, m, p, q, 0.3f, 0.4f, 0.65f, 1.0f, a * 0.6f, a * 0.6f);
                FxRenderTypes.ribbon(vc, m, p, q, 0.07f, 1, 1, 1, a, a);
            }
        }
    }

    // ------------------------------------------------------------------ from outside

    /** Someone else in it: held still inside a shimmer of blue until it ends. */
    private static void outside(RenderLevelStageEvent event) {
        if (OUTSIDE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = new Matrix4f();
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.GLOW);
        for (var entry : OUTSIDE.int2IntEntrySet()) {
            Entity e = mc.level.getEntity(entry.getIntKey());
            if (e == null || clock - entry.getIntValue() > WATCHDOG + 40) continue;
            float age = clock - entry.getIntValue() + partial;
            Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() / 2, 0).subtract(cam);
            float pulse = 0.85f + 0.15f * Mth.sin(age * 0.5f);
            FxRenderTypes.sphere(vc, m, c, e.getBbHeight() * 0.8f * pulse, 8, 14, 0.3f, 0.5f, 1.0f, 0.18f);
            FxRenderTypes.annulus(vc, m, e.getPosition(partial).subtract(cam).add(0, 0.05, 0), new Vec3(0, 1, 0), 0.6f, 0.75f + 0.1f * Mth.sin(age * 0.4f), 28,
                    0.4f, 0.65f, 1.0f, 0.8f, 0);
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    // ------------------------------------------------------------------ over the screen

    public static void render(GuiGraphics g, DeltaTracker tracker) {
        if (t < 0 || !involvesMe()) return;
        float partial = tracker.getGameTimeDeltaPartialTick(true);
        overlay(g, partial);
        caption(g, partial);
    }

    /** Each place's name, small, low on the screen, as it cuts in (Library of Ruina names its floors). */
    private static void caption(GuiGraphics g, float partial) {
        float tt = t + partial;
        if (tt >= VOID_AT) return;
        int st = stageAt(tt);
        ClawStage s = STAGES[st];
        if (st == 0 || s == null) return;
        float u = local(tt), a = Mth.clamp(Math.min(u / 4f, (HOP - u) / 6f), 0, 1);
        if (a < 0.05f) return;
        Minecraft mc = Minecraft.getInstance();
        String id = s.dimension.substring(s.dimension.indexOf(':') + 1).replace('_', ' ');
        StringBuilder name = new StringBuilder();
        for (String w : id.split(" ")) if (!w.isEmpty()) name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        String text = name.toString().trim();
        int h = g.guiHeight(), bar = (int) (h * 0.12f);
        g.drawString(mc.font, text, 14, h - bar - 14, ((int) (a * 255) << 24) | 0xD8E4FF, true);
    }

    /** The cuts between places flash white; then the finale: the void, the slash barrage, the shatter. */
    static void overlay(GuiGraphics g, float partial) {
        int w = g.guiWidth(), h = g.guiHeight();
        float tt = Math.max(0, t + partial);
        if (tt < VOID_AT) {
            float u = local(tt);
            if (stageAt(tt) > 0 && u < 3) g.fill(0, 0, w, h, ((int) (180 * (1 - u / 3f)) << 24) | 0xE8F0FF);
            return;
        }
        if (tt < BARRAGE_AT) {
            // the void: black, blue specks drifting, one thin line of light
            g.fill(0, 0, w, h, 0xFF000000);
            RandomSource specks = RandomSource.create(seed);
            for (int i = 0; i < 70; i++) {
                int x = (int) ((specks.nextInt(w) + tt * (0.5 + specks.nextDouble())) % w), y = specks.nextInt(h);
                g.fill(x, y, x + 2, y + 2, ((80 + specks.nextInt(140)) << 24) | 0x5A90FF);
            }
            float k = Mth.clamp((tt - VOID_AT) / 10f, 0, 1);
            int lw = (int) (w * 0.6f * k);
            g.fill(w / 2 - lw / 2, h / 2, w / 2 + lw / 2, h / 2 + 1, 0xE0D8E8FF);
            return;
        }
        if (tt < BARRAGE_AT + 10) {
            // the cuts: thin blue lines across the whole screen, every way
            g.fill(0, 0, w, h, 0xFF02040A);
            int n = (int) ((tt - BARRAGE_AT) * 3) + 3;
            RandomSource cuts = RandomSource.create(seed ^ 77);
            for (int i = 0; i < n; i++) line(g, w * cuts.nextFloat(), h * cuts.nextFloat(), (float) (cuts.nextFloat() * Math.PI), w * 1.6f, 1.5f, 0xF0A8D0FF);
            return;
        }
        if (tt < SHATTER_AT) {
            g.fill(0, 0, w, h, 0xFF000000);
            return;
        }
        // home again: shards of blue fly off the screen's edges, and the X
        float k = Mth.clamp((tt - SHATTER_AT) / 14f, 0, 1);
        RandomSource shards = RandomSource.create(seed ^ 13);
        int black = (int) (255 * Mth.clamp(1 - k * 2.5f, 0, 1));
        if (black > 0) g.fill(0, 0, w, h, black << 24);
        for (int i = 0; i < 26; i++) {
            float sx = shards.nextBoolean() ? 0 : w, sy = shards.nextFloat() * h, ang = (float) (shards.nextFloat() * Math.PI * 2);
            float dist = k * (80 + shards.nextFloat() * 160);
            float x = sx + (sx == 0 ? dist : -dist) + Mth.cos(ang) * dist * 0.3f, y = sy + Mth.sin(ang) * dist * 0.5f;
            int a = (int) (220 * (1 - k));
            line(g, x, y, ang, 10 + shards.nextFloat() * 22, 3, (a << 24) | 0x7AA8FF);
        }
        int xa = (int) (255 * Mth.clamp(1 - Math.abs(k - 0.3f) * 2.2f, 0, 1));
        if (xa > 4) {
            line(g, w / 2f, h / 2f, (float) Math.toRadians(35), w * 1.2f, 6, (xa << 24) | 0xFFFFFF);
            line(g, w / 2f, h / 2f, (float) Math.toRadians(-35), w * 1.2f, 6, (xa << 24) | 0xFFFFFF);
            line(g, w / 2f, h / 2f, (float) Math.toRadians(35), w * 1.25f, 14, ((xa / 3) << 24) | 0x7AB0FF);
            line(g, w / 2f, h / 2f, (float) Math.toRadians(-35), w * 1.25f, 14, ((xa / 3) << 24) | 0x7AB0FF);
        }
    }

    /** A line through (x, y) at `angle`, `len` long and `thick` px wide. */
    static void line(GuiGraphics g, float x, float y, float angle, float len, float thick, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        g.fill((int) (-len / 2), (int) (-thick / 2), (int) (len / 2), (int) Math.max(1, thick / 2), argb);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        OUTSIDE.clear();
        PENDING.clear();
    }
}
