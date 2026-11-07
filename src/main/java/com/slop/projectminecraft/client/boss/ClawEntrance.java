package com.slop.projectminecraft.client.boss;

import static com.slop.projectminecraft.boss.Claw.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.boss.Claw;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.client.ScreenFlash;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.client.render.RingFx;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Claw's entrance, the way villains come through in films: a hairline cut in the air splits into a jagged rift with
 * nothing inside it, his claw reaches out of the dark and drags the edge wider, a leg steps through and plants, then he
 * leans out head down, straightens, lifts his head as the vials light, and the rift snaps shut behind him.
 * <p>
 * Beaten, he leaves the same way backwards (exit): down on his knees, the claw and vials bursting off him, the mask split,
 * then an unstable rift tears open behind him and he limps back into it, the broken pieces left on the ground.
 * <p>
 * The void is drawn opaque after the entities, so whatever part of him is still behind the rift's plane is really hidden
 * by it (ClawRenderer slides him forward through the plane and only shows each part once it's due). Everything runs off
 * the Claw's synced entrance clock; the player who opened the book also gets the camera (CinematicCamera).
 */
public final class ClawEntrance {
    private ClawEntrance() {}

    private static final RandomSource R = RandomSource.create();
    /** The rift's size: half its height, and half its widest width once fully open (then pulled wider at the grip). */
    static final float HALF_H = 1.45f, HALF_W = 0.8f, PULLED = 1.4f;
    /** How far behind where he ends up the rift stands (his facing is `f`). */
    static final double PLANE_BACK = 0.5;
    private static final int SEGS = 18;

    /** One entrance: the Claw, where he ends up (feet), which way he faces, the rift's jagged edge, the last tick handled. */
    static final class Entrance {
        final int claw;
        /** He's leaving through it (beaten) rather than coming out of it. */
        final boolean exit;
        final Vec3 feet, f, left, center;
        final float[] jagL = new float[SEGS + 1], jagR = new float[SEGS + 1];
        int last = -1;
        /** Ticks without the Claw in the level: the packet can arrive a tick before he does. */
        int missing;
        /** He has been seen arriving (before that, his synced state may still be catching up). */
        boolean seen;

        Entrance(int claw, Vec3 feet, Vec3 f, boolean exit) {
            this.claw = claw;
            this.exit = exit;
            this.feet = feet;
            this.f = f;
            // his left: for someone facing +z it's +x
            this.left = new Vec3(f.z, 0, -f.x);
            // leaving, the rift opens a little further behind him (he's knocked back a step first)
            this.center = feet.subtract(f.scale(PLANE_BACK + (exit ? 0.15 : 0))).add(0, 1.2, 0);
            for (int i = 0; i <= SEGS; i++) {
                // ragged, never smooth: alternate in and out, a few deep notches
                jagL[i] = (i % 2 == 0 ? 0.12f : -0.1f) + (R.nextFloat() - 0.5f) * 0.35f;
                jagR[i] = (i % 2 == 1 ? 0.12f : -0.1f) + (R.nextFloat() - 0.5f) * 0.35f;
            }
        }
    }

    static final List<Entrance> ACTIVE = new ArrayList<>();
    /** The cutscene running for this client's own entrance, if any. */
    @Nullable
    private static Camera playing;

    /**
     * From the packets: his rift opening [kind, claw, 1, the player who opened the book], or his exit [kind, claw, the
     * player who beat him]; `at` is his chest, `facing` his heading. That player's camera follows it.
     */
    static void start(int claw, Vec3 at, Vec3 facing, int viewer, boolean exit) {
        Vec3 f = new Vec3(facing.x, 0, facing.z);
        f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        for (Entrance old : new ArrayList<>(ACTIVE)) if (old.claw == claw) end(old);
        Entrance e = new Entrance(claw, at.subtract(0, 1.2, 0), f, exit);
        ACTIVE.add(e);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == viewer) {
            playing = exit ? new ExitCamera(e) : new Camera(e, mc.player.getEyePosition());
            CinematicCamera.play(playing);
        }
    }

    /** Someone who came in range (or joined) mid-entrance missed the packet: the rift from where he stands, no camera. */
    static void ensure(Claw c) {
        boolean exit = c.state() == RETREAT;
        for (Entrance e : ACTIVE) if (e.claw == c.getId() && e.exit == exit) return;
        // an entrance still listed for him when he's already leaving (or the other way round) is over
        for (Entrance old : new ArrayList<>(ACTIVE)) if (old.claw == c.getId()) end(old);
        Entrance e = new Entrance(c.getId(), c.position(), Vec3.directionFromRotation(0, c.getYRot()).multiply(1, 0, 1).normalize(), exit);
        e.seen = true;
        // from the tick before now, so the one just reached still plays
        e.last = Math.max(-1, (int) time(e, c, 0) - 1);
        ACTIVE.add(e);
    }

    /** Where this one is on its own clock (the entrance's or the exit's). */
    static float time(Entrance e, Claw c, float partial) {
        return e.exit ? c.exit(partial) : c.entrance(partial);
    }

    @Nullable
    static Claw claw(Entrance e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(e.claw) instanceof Claw c ? c : null;
    }

    // ------------------------------------------------------------------ the rift's shape over time

    /** Half-width of the opening at time t (0 = a hairline), and its half-height. */
    static float width(Entrance e, float t) {
        if (e.exit) {
            // torn in a hurry, narrower, and it won't hold still
            if (t < R_RIFT) return 0;
            float k = CinematicCamera.ease(t, R_RIFT, R_RIFT + 12);
            float w = HALF_W * 0.95f * (0.02f + k) * (1 + 0.07f * Mth.sin(t * 1.7f) + 0.05f * Mth.sin(t * 4.3f));
            if (t >= R_SNAP) w *= 1 - CinematicCamera.ease(t, R_SNAP, R_SNAP + 5);
            return w;
        }
        if (t < E_CRACK) return 0.012f;
        if (t < E_OPEN) {
            float k = CinematicCamera.ease(t, E_CRACK, E_OPEN);
            // it jerks open in fits: a stutter on the way
            return 0.02f + HALF_W * (k + 0.08f * Mth.sin(t * 2.7f) * (1 - k));
        }
        float w = HALF_W * (1 + (PULLED - 1) * CinematicCamera.ease(t, E_GRIP, E_GRIP + 7));
        w *= 1 + 0.025f * Mth.sin(t * 0.6f);
        if (t >= E_SNAP) w *= 1 - CinematicCamera.ease(t, E_SNAP, E_SNAP + 5);
        return w;
    }

    static float height(Entrance e, float t) {
        if (e.exit) {
            float h = HALF_H * (0.15f + 0.85f * CinematicCamera.ease(t, R_RIFT, R_RIFT + 6));
            if (t >= R_SNAP) h *= 1 - 0.7f * CinematicCamera.ease(t, R_SNAP + 2, R_SNAP + 6);
            return h;
        }
        float h = HALF_H * (0.15f + 0.85f * CinematicCamera.ease(t, 0, E_CRACK));
        if (t >= E_SNAP) h *= 1 - 0.7f * CinematicCamera.ease(t, E_SNAP + 2, E_SNAP + 6);
        return h;
    }

    /** A point on the rift's edge: u 0..1 bottom to top, side -1 his right edge / +1 his left edge. */
    static Vec3 edge(Entrance e, float t, int i, int side) {
        float u = i / (float) SEGS;
        float bulge = (float) Math.pow(Math.sin(Math.PI * u), 0.75);
        float jag = 1 + (side > 0 ? e.jagL[i] : e.jagR[i]) * (e.exit ? 1 : Mth.clamp((t - E_CRACK) / 10f, 0, 1));
        float x = width(e, t) * bulge * jag * side;
        float y = (u * 2 - 1) * height(e, t);
        return e.center.add(e.left.scale(x)).add(0, y, 0);
    }

    // ------------------------------------------------------------------ the beats (one-shot effects, particles)

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || ACTIVE.isEmpty()) return;
        ClientLevel level = mc.level;
        for (Entrance e : new ArrayList<>(ACTIVE)) {
            Claw c = claw(e);
            if (c == null) {
                // seen and now gone (finished, killed, unloaded): over at once; never seen yet: give his packet time
                if (e.seen || ++e.missing > 40) end(e);
                continue;
            }
            e.missing = 0;
            byte want = e.exit ? RETREAT : ARRIVE;
            if (c.state() == want) e.seen = true;
            else if (!e.seen && ++e.missing <= 40) continue;
            int t = (int) time(e, c, 0);
            if (c.state() != want || t >= (e.exit ? RETREAT_TICKS : ARRIVE_TICKS)) {
                end(e);
                continue;
            }
            if (mc.player != null && mc.player.isDeadOrDying()) stopCamera(e);
            // first seen part way in: only the latest beats, not everything that came before at once
            if (e.last < 0) e.last = Math.max(-1, t - 2);
            double dist = mc.player == null ? 99 : mc.player.position().distanceTo(e.center);
            for (int k = e.last + 1; k <= t; k++) {
                if (e.exit) exitBeat(level, e, k, dist);
                else beat(level, e, k, dist);
            }
            e.last = t;
            if (e.exit) exitAmbient(level, e, t);
            else ambient(level, e, t);
        }
    }

    /** Over (or cut short: he's gone, he left, the player died): the rift goes, and so does the cutscene if it's this one. */
    private static void end(Entrance e) {
        ACTIVE.remove(e);
        stopCamera(e);
    }

    private static void stopCamera(Entrance e) {
        if (playing != null && playing.e == e) {
            if (CinematicCamera.active()) CinematicCamera.stop();
            playing = null;
        }
    }

    /** Everything that happens exactly once, at entrance tick k. */
    private static void beat(ClientLevel level, Entrance e, int k, double dist) {
        Vec3 c = e.center;
        if (k > 0 && k < E_OPEN && k % 6 == 0) CameraShake.addAt(dist, 0.12f + k * 0.006f, 24);
        if (k == E_CRACK) {
            flash(0.12f, dist);
            CameraShake.addAt(dist, 0.4f, 24);
            for (int i = 0; i < 20; i++) spark(level, edge(e, k, R.nextInt(SEGS + 1), R.nextBoolean() ? 1 : -1), 0.3);
        }
        if (k == E_OPEN) {
            RingFx.add(c, e.f, 0.3f, 3.0f, 12, 0.35f, 0.6f, 1.0f, 0.8f);
            Shockwaves.add(c, 6, 450, 0.9f);
            CameraShake.addAt(dist, 0.6f, 30);
        }
        if (k == E_GRIP) {
            // the claw hooks the edge and drags it open
            Vec3 lip = c.add(e.left.scale(HALF_W)).add(0, 0.1, 0);
            RingFx.flash(lip, 1.2f, 6, 0.5f, 0.75f, 1.0f, 1.0f);
            for (int i = 0; i < 30; i++) spark(level, edge(e, k, R.nextInt(SEGS + 1), 1), 0.4);
            bolt(e, k, 1);
            bolt(e, k, 1);
            flash(0.15f, dist);
            CameraShake.addAt(dist, 0.7f, 30);
        }
        if (k == E_STEP) {
            // the first foot down: a thud, dust, the ground ringing
            Vec3 foot = e.feet.subtract(e.f.scale(0.2)).add(e.left.scale(0.12));
            BlockState below = level.getBlockState(BlockPos.containing(foot).below());
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, below.isAir() ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : below);
            for (int i = 0; i < 28; i++) {
                double a = R.nextDouble() * Math.PI * 2, s = 0.15 + R.nextDouble() * 0.2;
                level.addParticle(dust, foot.x, foot.y + 0.05, foot.z, Math.cos(a) * s, 0.12 + R.nextDouble() * 0.1, Math.sin(a) * s);
            }
            for (int i = 0; i < 4; i++) level.addParticle(ParticleTypes.POOF, foot.x, foot.y + 0.1, foot.z, R.nextGaussian() * 0.08, 0.02, R.nextGaussian() * 0.08);
            RingFx.add(foot.add(0, 0.05, 0), new Vec3(0, 1, 0), 0.2f, 2.4f, 12, 0.6f, 0.75f, 1.0f, 0.8f);
            Shockwaves.add(foot, 5, 400, 0.8f);
            CameraShake.addAt(dist, 0.9f, 30);
        }
        if (k == E_HEAD || k == E_HEAD + 5 || k == E_HEAD + 10) {
            // the vials light, one by one: green on his head, blue at the shoulder, orange at the elbow
            int s = (k - E_HEAD) / 5;
            Vec3 at = s == 0 ? e.feet.add(0, 2.3, 0) : s == 1 ? e.feet.add(e.left.scale(0.45)).add(0, 1.85, 0) : e.feet.add(e.left.scale(0.55)).add(0, 1.2, 0);
            int serum = s == 0 ? 0 : s == 1 ? 2 : 1;
            float[] col = ClawClient.SERUM_RGB[serum];
            RingFx.flash(at, 0.7f, 6, col[0], col[1], col[2], 1.0f);
            for (int i = 0; i < 10; i++) level.addParticle(new DustParticleOptions(new Vector3f(col), 0.8f), at.x, at.y, at.z, R.nextGaussian() * 0.06, 0.04, R.nextGaussian() * 0.06);
        }
        if (k == E_SNAP) {
            // shut: the edges slam together behind him
            flash(0.35f, dist);
            if (dist < 32) ImpactFrame.trigger(ImpactFrame.CYAN, 150);
            Shockwaves.add(c, 10, 600, 1.6f);
            RingFx.add(c, e.f, 0.3f, 4.0f, 14, 0.5f, 0.75f, 1.0f, 1.0f);
            CameraShake.addAt(dist, 1.0f, 40);
            for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.END_ROD, c.x, c.y + R.nextGaussian() * 0.6, c.z, R.nextGaussian() * 0.12, R.nextGaussian() * 0.12, R.nextGaussian() * 0.12);
            for (int i = 0; i < 30; i++) spark(level, c.add(0, R.nextGaussian() * 0.8, 0), 0.6);
        }
    }

    /** His exit, once per tick k: the blow, the knee, the claw and vials bursting, the mask, the injection, the rift, the snap. */
    private static void exitBeat(ClientLevel level, Entrance e, int k, double dist) {
        Vec3 chest = e.feet.add(0, 1.3, 0), l = e.left;
        if (k == R_HIT) {
            // the last blow lands: a dark spray, a white impact frame
            if (dist < 32) ImpactFrame.trigger(ImpactFrame.WHITE, 140);
            flash(0.3f, dist);
            CameraShake.addAt(dist, 0.9f, 32);
            for (int i = 0; i < 16; i++) level.addParticle(new DustParticleOptions(new Vector3f(0.42f, 0.04f, 0.04f), 1.1f), chest.x, chest.y, chest.z, R.nextGaussian() * 0.12, 0.05 + R.nextDouble() * 0.1, R.nextGaussian() * 0.12);
            for (int i = 0; i < 8; i++) spark(level, chest, 0.3);
        }
        if (k == R_KNEEL + 4) {
            Vec3 knee = e.feet.subtract(e.f.scale(0.15)).add(0, 0.05, 0);
            RingFx.add(knee, new Vec3(0, 1, 0), 0.2f, 1.6f, 10, 0.6f, 0.6f, 0.65f, 0.6f);
            for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.POOF, knee.x, knee.y, knee.z, R.nextGaussian() * 0.05, 0.02, R.nextGaussian() * 0.05);
            CameraShake.addAt(dist, 0.5f, 24);
        }
        if (k == R_BREAK) {
            // the vials burst, each in its colour; sparks off the torn claw arm
            Vec3[] vials = vialSpots(e, true);
            int[] serum = {0, 2, 1};
            BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
            for (int v = 0; v < 3; v++) {
                float[] col = ClawClient.SERUM_RGB[serum[v]];
                for (int i = 0; i < 12; i++) level.addParticle(glass, vials[v].x, vials[v].y, vials[v].z, R.nextGaussian() * 0.15, R.nextDouble() * 0.2, R.nextGaussian() * 0.15);
                for (int i = 0; i < 14; i++) level.addParticle(new DustParticleOptions(new Vector3f(col), 1.2f), vials[v].x, vials[v].y, vials[v].z, R.nextGaussian() * 0.15, R.nextDouble() * 0.15, R.nextGaussian() * 0.15);
                RingFx.flash(vials[v], 0.6f, 5, col[0], col[1], col[2], 1.0f);
            }
            Vec3 stump = stump(e);
            for (int i = 0; i < 20; i++) spark(level, stump, 0.35);
            for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.CRIT, stump.x, stump.y, stump.z, R.nextGaussian() * 0.3, R.nextDouble() * 0.3, R.nextGaussian() * 0.3);
            flash(0.2f, dist);
            CameraShake.addAt(dist, 0.8f, 30);
        }
        if (k == R_MASK) {
            // the mask splits open and a chunk of it breaks away: chips of dark wood, a spatter of blood
            Vec3 face = e.feet.add(e.f.scale(0.2)).add(0, 1.15, 0);
            BlockParticleOption wood = new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS.defaultBlockState());
            for (int i = 0; i < 18; i++) level.addParticle(wood, face.x, face.y, face.z, R.nextGaussian() * 0.12, R.nextDouble() * 0.15, R.nextGaussian() * 0.12);
            for (int i = 0; i < 6; i++) level.addParticle(new DustParticleOptions(new Vector3f(0.42f, 0.04f, 0.04f), 0.9f), face.x, face.y + 0.2, face.z, R.nextGaussian() * 0.06, 0.04, R.nextGaussian() * 0.06);
            CameraShake.addAt(dist, 0.3f, 20);
        }
        if (k == R_INJECT) {
            // the last of W, into the shoulder stump
            Vec3 at = vialSpots(e, true)[1];
            for (int i = 0; i < 16; i++) level.addParticle(new DustParticleOptions(new Vector3f(0.3f, 0.55f, 1.0f), 1.0f), at.x, at.y, at.z, R.nextGaussian() * 0.08, R.nextGaussian() * 0.08, R.nextGaussian() * 0.08);
            RingFx.flash(at, 0.8f, 6, 0.4f, 0.65f, 1.0f, 1.0f);
        }
        if (k == R_RIFT) {
            RingFx.add(e.center, e.f, 0.3f, 2.4f, 10, 0.35f, 0.6f, 1.0f, 0.7f);
            Shockwaves.add(e.center, 5, 400, 0.7f);
            CameraShake.addAt(dist, 0.5f, 28);
            flash(0.12f, dist);
        }
        if (k == R_SNAP) {
            // gone: the rift slams shut on him
            flash(0.3f, dist);
            if (dist < 32) ImpactFrame.trigger(ImpactFrame.CYAN, 130);
            Shockwaves.add(e.center, 8, 550, 1.3f);
            RingFx.add(e.center, e.f, 0.3f, 3.2f, 12, 0.5f, 0.75f, 1.0f, 1.0f);
            CameraShake.addAt(dist, 0.8f, 36);
            for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.END_ROD, e.center.x, e.center.y + R.nextGaussian() * 0.5, e.center.z, R.nextGaussian() * 0.1, R.nextGaussian() * 0.1, R.nextGaussian() * 0.1);
            for (int i = 0; i < 20; i++) spark(level, e.center.add(0, R.nextGaussian() * 0.7, 0), 0.5);
        }
    }

    /** Broken and bleeding: the stumps leak what's left in them, the wrecked arm sparks and drips; the rift crackles. */
    private static void exitAmbient(ClientLevel level, Entrance e, int t) {
        if (t >= R_SNAP) return;
        if (t >= R_BREAK && t < R_BACK + 20) {
            Vec3[] vials = vialSpots(e, t < R_RISE);
            int[] serum = {0, 2, 1};
            if (t % 3 == 0) {
                int v = R.nextInt(3);
                float[] col = ClawClient.SERUM_RGB[serum[v]];
                level.addParticle(new DustParticleOptions(new Vector3f(col), 0.7f), vials[v].x, vials[v].y, vials[v].z, 0, -0.06, 0);
            }
            Vec3 stump = stump(e);
            if (t % 7 == 0) spark(level, stump, 0.15);
            if (t % 11 == 0) level.addParticle(new DustParticleOptions(new Vector3f(0.4f, 0.03f, 0.03f), 0.8f), stump.x, stump.y, stump.z, 0, -0.08, 0);
        }
        if (t >= R_RIFT) {
            if (R.nextInt(3) == 0) spark(level, edge(e, t, R.nextInt(SEGS + 1), R.nextBoolean() ? 1 : -1), 0.15);
            if (t % 7 == 0) bolt(e, t, R.nextBoolean() ? 1 : -1);
            Vec3 o = new Vec3(R.nextGaussian(), R.nextGaussian() * 1.2, R.nextGaussian()).normalize().scale(1.4 + R.nextDouble());
            Vec3 p = e.center.add(o), v = o.scale(-0.06);
            level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.65f, 1.0f), 0.7f), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** About where his vials were: head, shoulder, elbow (lower while he's on his knee). */
    private static Vec3[] vialSpots(Entrance e, boolean kneeling) {
        double drop = kneeling ? 0.45 : 0;
        Vec3 at = e.feet.subtract(e.f.scale(0.15)).subtract(0, drop, 0);
        return new Vec3[]{at.add(0, 2.2, 0), at.add(e.left.scale(0.45)).add(0, 1.7, 0), at.add(e.left.scale(0.55)).add(0, 1.15, 0)};
    }

    /** The torn end of his claw arm. */
    private static Vec3 stump(Entrance e) {
        return e.feet.subtract(e.f.scale(0.1)).add(e.left.scale(0.4)).add(0, 0.55, 0);
    }

    /** What goes on every tick: space pouring into the cut, crackle on the edges, wisps of it peeling off him. */
    private static void ambient(ClientLevel level, Entrance e, int t) {
        if (t >= E_SNAP) return;
        Vec3 c = e.center;
        // the air around it gets pulled in
        int pull = t < E_OPEN ? 3 : 1;
        for (int i = 0; i < pull; i++) {
            // motes of blue light drifting in toward the cut
            Vec3 o = new Vec3(R.nextGaussian(), R.nextGaussian() * 1.2, R.nextGaussian()).normalize().scale(1.6 + R.nextDouble());
            Vec3 p = c.add(o), v = o.scale(-0.06);
            level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.65f, 1.0f), 0.7f), p.x, p.y, p.z, v.x, v.y, v.z);
        }
        if (t >= E_CRACK && R.nextInt(3) == 0) spark(level, edge(e, t, R.nextInt(SEGS + 1), R.nextBoolean() ? 1 : -1), 0.15);
        if (t >= E_CRACK && t % 9 == 0) bolt(e, t, R.nextBoolean() ? 1 : -1);
        // where the claw pierces through
        if (t >= E_CLAW && t < E_LEG && t % 2 == 0) {
            Vec3 hand = c.add(e.left.scale(0.35)).add(0, 0.15, 0);
            spark(level, hand, 0.12);
        }
        // blue wisps trail off him as he comes through
        if (t >= E_BODY && t < E_HEAD && t % 2 == 0) {
            for (int i = 0; i < 1; i++) {
                Vec3 p = e.feet.add(e.left.scale(R.nextGaussian() * 0.35)).add(0, 0.3 + R.nextDouble() * 1.6, 0).subtract(e.f.scale(0.2));
                level.addParticle(new DustParticleOptions(new Vector3f(0.35f, 0.55f, 1.0f), 1.2f), p.x, p.y, p.z, e.f.x * 0.02, 0.01, e.f.z * 0.02);
            }
        }
    }

    /** A white flash, only for those close enough to be blinded by it. */
    private static void flash(float amount, double dist) {
        if (dist < 32) ScreenFlash.flash(amount * (float) (1 - dist / 32));
    }

    private static void spark(ClientLevel level, Vec3 p, double speed) {
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, R.nextGaussian() * speed, R.nextGaussian() * speed, R.nextGaussian() * speed);
    }

    /** A crackle of blue lightning off one edge. */
    private static void bolt(Entrance e, float t, int side) {
        Vec3 from = edge(e, t, 2 + R.nextInt(SEGS - 3), side);
        List<Vec3> pts = new ArrayList<>();
        pts.add(from);
        Vec3 out = e.left.scale(side);
        for (int i = 1; i <= 4; i++) pts.add(from.add(out.scale(i * 0.22)).add(e.f.scale(R.nextGaussian() * 0.15)).add(0, R.nextGaussian() * 0.18, 0));
        ArcFx.addBolt(pts, 4, 0.05f, 0.5f, 0.75f, 1.0f);
    }

    // ------------------------------------------------------------------ drawing the rift

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        boolean entities = event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (!entities && event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = new Matrix4f();
        if (entities) {
            // the void right after the entities, opaque and depth-writing: what of him is still behind it stays hidden,
            // and the particles, bolts and rings drawn later still show in front of it
            VertexConsumer solid = buffers.getBuffer(FxRenderTypes.SOLID_COLOR);
            for (Entrance e : ACTIVE) {
                Claw c = claw(e);
                if (c != null) voidFill(solid, m, e, time(e, c, partial), cam);
            }
            buffers.endBatch(FxRenderTypes.SOLID_COLOR);
            return;
        }
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        for (Entrance e : ACTIVE) {
            Claw c = claw(e);
            if (c != null) glow(glow, m, e, time(e, c, partial), cam);
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    private static void voidFill(VertexConsumer vc, Matrix4f m, Entrance e, float t, Vec3 cam) {
        if (t >= (e.exit ? R_SNAP : E_SNAP) + 6 || e.exit && t < R_RIFT) return;
        for (int i = 0; i < SEGS; i++) {
            Vec3 l0 = edge(e, t, i, 1), r0 = edge(e, t, i, -1), l1 = edge(e, t, i + 1, 1), r1 = edge(e, t, i + 1, -1);
            // deep space: navy, so his dark claw still stands out against it
            FxRenderTypes.vertex(vc, m, l0.subtract(cam), 0.035f, 0.05f, 0.15f, 1);
            FxRenderTypes.vertex(vc, m, r0.subtract(cam), 0.035f, 0.05f, 0.15f, 1);
            FxRenderTypes.vertex(vc, m, r1.subtract(cam), 0.035f, 0.05f, 0.15f, 1);
            FxRenderTypes.vertex(vc, m, l1.subtract(cam), 0.035f, 0.05f, 0.15f, 1);
        }
    }

    private static void glow(VertexConsumer vc, Matrix4f m, Entrance e, float t, Vec3 cam) {
        if (t >= (e.exit ? R_SNAP : E_SNAP) + 6 || e.exit && t < R_RIFT) return;
        float flick = 0.85f + 0.15f * Mth.sin(t * 2.1f);
        // stars drifting in the void, on the camera's side of it
        double toward = Math.signum(cam.subtract(e.center).dot(e.f));
        if (toward == 0) toward = 1;
        if (t >= (e.exit ? R_RIFT + 3 : E_CRACK + 4)) {
            RandomSource stars = RandomSource.create(e.claw * 31L);
            float w = width(e, t), h = height(e, t);
            for (int i = 0; i < 46; i++) {
                float u = stars.nextFloat() * 2 - 1, v = stars.nextFloat() * 2 - 1;
                float drift = ((stars.nextFloat() * 2 - 1) + t * 0.004f * (1 + stars.nextFloat())) % 2;
                v = ((v + drift + 3) % 2) - 1;
                float bulge = (float) Math.pow(Math.max(0, Math.sin(Math.PI * (v + 1) / 2)), 0.75);
                Vec3 p = e.center.add(e.left.scale(u * w * bulge * 0.85)).add(0, v * h * 0.95, 0).add(e.f.scale(0.01 * toward)).subtract(cam);
                float s = 0.018f + stars.nextFloat() * 0.03f, tw = 0.5f + 0.5f * Mth.sin(t * 0.4f + i);
                quad(vc, m, p, e.left.scale(s), new Vec3(0, s, 0), 0.5f, 0.7f, 1.0f, tw);
            }
        }
        // a soft halo just outside the edge, fading out into the air
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < SEGS; i++) {
                Vec3 a = edge(e, t, i, side), b = edge(e, t, i + 1, side);
                Vec3 ao = a.add(e.left.scale(side * 0.35)), bo = b.add(e.left.scale(side * 0.35));
                FxRenderTypes.vertex(vc, m, a.subtract(cam), 0.3f, 0.5f, 1.0f, 0.45f * flick);
                FxRenderTypes.vertex(vc, m, b.subtract(cam), 0.3f, 0.5f, 1.0f, 0.45f * flick);
                FxRenderTypes.vertex(vc, m, bo.subtract(cam), 0.2f, 0.35f, 1.0f, 0);
                FxRenderTypes.vertex(vc, m, ao.subtract(cam), 0.2f, 0.35f, 1.0f, 0);
                // the burning lips of the cut: a white-hot core in a blue glow
                FxRenderTypes.ribbon(vc, m, a.subtract(cam), b.subtract(cam), 0.16f, 0.35f, 0.6f, 1.0f, 0.55f * flick, 0.55f * flick);
                FxRenderTypes.ribbon(vc, m, a.subtract(cam), b.subtract(cam), 0.045f, 0.9f, 0.96f, 1.0f, flick, flick);
            }
        }
        // its light on the ground
        float open = width(e, t) / HALF_W;
        Vec3 ground = new Vec3(e.center.x, e.feet.y + 0.03, e.center.z).subtract(cam);
        FxRenderTypes.annulus(vc, m, ground, new Vec3(0, 1, 0), 0, 1.0f + open * 0.9f, 32, 0.3f, 0.5f, 1.0f, 0.3f * flick * Math.min(1, open), 0);
    }

    /** A flat square around p spanned by the half-axes ax, ay. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 ax, Vec3 ay, float r, float g, float b, float a) {
        FxRenderTypes.vertex(vc, m, p.subtract(ax).subtract(ay), r, g, b, a);
        FxRenderTypes.vertex(vc, m, p.add(ax).subtract(ay), r, g, b, a);
        FxRenderTypes.vertex(vc, m, p.add(ax).add(ay), r, g, b, a);
        FxRenderTypes.vertex(vc, m, p.subtract(ax).add(ay), r, g, b, a);
    }

    // ------------------------------------------------------------------ the camera (the one who opened the book)

    /** His exit, for the one who beat him: the knee, the arm and the vials, his face as he looks up, then wide as he backs away. */
    static final class ExitCamera extends Camera {
        ExitCamera(Entrance e) {
            super(e, Vec3.ZERO);
        }

        @Override
        public int duration() {
            return RETREAT_TICKS;
        }

        @Override
        public void frame(float t, CinematicCamera.Shot s) {
            Vec3 f = e.f, l = e.left, feet = e.feet;
            if (t < R_BREAK - 2) {
                // down he goes: a low medium shot
                float k = CinematicCamera.ease(t, 0, R_BREAK);
                Vec3 from = CinematicCamera.lerp(feet.add(f.scale(2.0)).add(l.scale(2.6)).add(0, 0.7, 0), feet.add(f.scale(1.7)).add(l.scale(2.2)).add(0, 0.6, 0), k);
                s.lookAt(from, feet.add(0, 1.0, 0));
                s.fov = 60;
            } else if (t < R_MASK - 2) {
                // close on the arm as the claw comes apart
                Vec3 arm = feet.add(l.scale(0.45)).add(0, 0.75, 0);
                s.lookAt(feet.add(f.scale(1.7)).add(l.scale(1.5)).add(0, 0.8, 0), arm);
                s.fov = 55;
            } else if (t < R_INJECT + 14) {
                // close on the mask as it splits, then he lifts his head to you through the crack
                float k = CinematicCamera.ease(t, R_MASK, R_LOOK + 8);
                Vec3 face = feet.add(0, 1.15, 0);
                s.lookAt(CinematicCamera.lerp(feet.add(f.scale(1.6)).add(l.scale(0.3)).add(0, 0.9, 0), feet.add(f.scale(1.25)).add(l.scale(0.15)).add(0, 1.05, 0), k), face);
                s.fov = 50;
            } else {
                // wide, from off to his side (you stand in front of him): the rift behind him, and him backing into it
                float k = CinematicCamera.ease(t, R_INJECT + 14, R_SNAP);
                Vec3 from = CinematicCamera.lerp(feet.add(f.scale(3.0)).add(l.scale(2.1)).add(0, 1.1, 0), feet.add(f.scale(3.6)).add(l.scale(2.5)).add(0, 1.3, 0), k);
                s.lookAt(from, e.center.add(f.scale(0.35)).add(0, -0.1, 0));
                s.fov = 60;
                CinematicCamera.returnToPlayer(s, t, R_SNAP + 2, RETREAT_TICKS - 2);
            }
        }
    }

    /** Hard cuts between film shots: over the shoulder, the claw, the foot, the low hero angle, then back to you. */
    static class Camera implements CinematicCamera.Director {
        final Entrance e;
        final Vec3 eye;

        Camera(Entrance e, Vec3 eye) {
            this.e = e;
            this.eye = eye;
        }

        @Override
        public int duration() {
            return ARRIVE_TICKS;
        }

        @Override
        public void frame(float t, CinematicCamera.Shot s) {
            Vec3 f = e.f, l = e.left, c = e.center, feet = e.feet;
            if (t < E_OPEN + 2) {
                // over your shoulder, creeping in on the cut
                float k = CinematicCamera.ease(t, 0, E_OPEN);
                Vec3 from = CinematicCamera.lerp(eye.add(f.scale(2.0)).add(l.scale(1.7)).add(0, 0.5, 0), eye.add(f.scale(0.4)).add(l.scale(1.3)).add(0, 0.3, 0), k);
                s.lookAt(from, c);
                s.fov = 70 - 10 * k;
            } else if (t < E_LEG) {
                // the claw, close, from low and to the side
                float k = CinematicCamera.ease(t, E_OPEN, E_LEG);
                Vec3 hand = c.add(l.scale(0.35)).add(f.scale(0.4)).add(0, 0.1, 0);
                Vec3 from = CinematicCamera.lerp(c.add(f.scale(3.0)).add(l.scale(1.3)).add(0, -0.15, 0), c.add(f.scale(2.5)).add(l.scale(1.05)).add(0, -0.05, 0), k);
                s.lookAt(from, CinematicCamera.lerp(hand, c.add(l.scale(0.6)), CinematicCamera.ease(t, E_GRIP - 4, E_GRIP + 6) * 0.5));
                s.fov = 55;
            } else if (t < E_BODY) {
                // down at the ground: the foot comes through
                Vec3 foot = feet.subtract(f.scale(0.15)).add(l.scale(0.12)).add(0, 0.25, 0);
                Vec3 from = feet.add(f.scale(2.3)).subtract(l.scale(1.1)).add(0, 0.3, 0);
                s.lookAt(from, foot);
                s.fov = 58;
            } else {
                // the low hero shot: up at him as he straightens and looks up
                float k = CinematicCamera.ease(t, E_BODY, E_SNAP);
                Vec3 from = CinematicCamera.lerp(feet.add(f.scale(3.4)).subtract(l.scale(0.7)).add(0, 0.35, 0), feet.add(f.scale(2.6)).subtract(l.scale(0.45)).add(0, 0.5, 0), k);
                s.lookAt(from, feet.add(0, 1.5 + 0.15 * CinematicCamera.ease(t, E_OUT, E_HEAD + 6), 0));
                s.fov = 62 - 10 * CinematicCamera.ease(t, E_HEAD - 4, E_HEAD + 10);
                CinematicCamera.returnToPlayer(s, t, E_SNAP + 4, ARRIVE_TICKS - 2);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        playing = null;
    }
}
