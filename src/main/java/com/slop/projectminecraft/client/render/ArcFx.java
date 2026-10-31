package com.slop.projectminecraft.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Lightning bolts and void trails. Each bolt is a jagged polyline rebuilt from a seed every two ticks (so it
 * flickers), drawn as three camera-facing ribbon layers: wide faint glow, mid glow and a white-hot core.
 */
public final class ArcFx {
    private ArcFx() {}

    private static final class Arc {
        List<Vec3> points;
        int age, life;
        long seed;
        int sourceId = -1;
        boolean fromHand;
        boolean dark;          // void style: smoky dark body instead of an outer glow
        boolean forks = true;
        boolean streak;        // soft afterimage ribbon instead of a bolt
        float jitter = 1.0f;   // displacement scale
        float width = 1.0f;
        float r = 0.45f, g = 0.75f, b = 1.0f;
    }

    private static final List<Arc> ARCS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /** Smoky purple streak left by a void blink. */
    public static void addTrail(Vec3 from, Vec3 to) {
        Arc a = new Arc();
        a.points = List.of(from, to);
        a.life = 14;
        a.dark = true;
        a.forks = false;
        a.jitter = 0.12f;
        a.width = 2.6f;
        a.r = 0.7f; a.g = 0.3f; a.b = 1.0f;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    /** Crescent slash across a target hit by a void rend. */
    public static void addSlash(Vec3 center, Vec3 dir, float roll) {
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 t1 = Math.abs(d.y) < 0.95 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 t2 = d.cross(t1).normalize();
        Vec3 u = t1.scale(Math.cos(roll)).add(t2.scale(Math.sin(roll)));
        Vec3 v = d.cross(u).normalize();
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            double ang = Math.toRadians(-75 + 150 * i / 10.0);
            pts.add(center.add(u.scale(Math.cos(ang) * 1.1)).add(v.scale(Math.sin(ang) * 1.1)).subtract(d.scale(0.25)));
        }
        Arc a = new Arc();
        a.points = pts;
        a.life = 9;
        a.dark = true;
        a.forks = false;
        a.jitter = 0.04f;
        a.width = 2.0f;
        a.r = 0.8f; a.g = 0.35f; a.b = 1.0f;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    /** Dash afterimage: a soft ghost ribbon through the body from last tick to now (head, chest and legs). */
    public static void addDashStreak(Entity e) {
        Vec3 now = e.position(), before = new Vec3(e.xo, e.yo, e.zo);
        if (now.distanceToSqr(before) < 0.04) return;
        double h = e.getBbHeight();
        for (double f : new double[]{0.25, 0.55, 0.85}) {
            Arc a = new Arc();
            a.points = List.of(before.add(0, h * f, 0), now.add(0, h * f, 0));
            a.life = 9;
            a.streak = true;
            a.forks = false;
            a.jitter = 0;
            a.width = 1.0f;
            a.r = 0.55f; a.g = 0.9f; a.b = 1.0f;
            a.seed = RANDOM.nextLong();
            ARCS.add(a);
        }
    }

    /** Speed Force: golden lightning crackling along the runner's path (think a certain scarlet speedster). */
    public static void addSpeedTrail(Entity e, float momentum) {
        Vec3 now = e.position(), before = new Vec3(e.xo, e.yo, e.zo);
        if (now.distanceToSqr(before) < 0.02) return;
        int n = momentum > 0.9f ? 3 : momentum > 0.6f ? 2 : 1;
        for (int i = 0; i < n; i++) {
            double h = e.getBbHeight() * (0.15 + RANDOM.nextDouble() * 0.75);
            Vec3 back = before.subtract(now).normalize().scale(0.6 + RANDOM.nextDouble() * 1.5 * momentum);
            Arc a = new Arc();
            a.points = List.of(now.add(0, h, 0), before.add(back).add(RANDOM.nextGaussian() * 0.2, h + RANDOM.nextGaussian() * 0.3, RANDOM.nextGaussian() * 0.2));
            a.life = 6 + RANDOM.nextInt(4);
            a.width = 0.55f + 0.3f * momentum;
            a.jitter = 1.4f;
            boolean red = RANDOM.nextInt(4) == 0;
            a.r = 1.0f; a.g = red ? 0.35f : 0.82f; a.b = red ? 0.15f : 0.25f;
            a.seed = RANDOM.nextLong();
            ARCS.add(a);
        }
    }

    /** Charged arrow in flight: a thin white sonic streak along its path. */
    public static void addArrowStreak(Entity e) {
        Arc a = new Arc();
        a.points = List.of(new Vec3(e.xo, e.yo, e.zo), e.position());
        a.life = 6;
        a.streak = true;
        a.forks = false;
        a.jitter = 0;
        a.width = 0.35f;
        a.r = 0.85f; a.g = 0.95f; a.b = 1.0f;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    /** Overcharged crit: a fat bolt straight down from the sky. */
    public static void addSkyBolt(Vec3 target) {
        Vec3 top = target.add(RANDOM.nextGaussian() * 3, 45, RANDOM.nextGaussian() * 3);
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            Vec3 p = top.lerp(target, i / 5.0);
            if (i > 0 && i < 5) p = p.add(RANDOM.nextGaussian() * 1.5, 0, RANDOM.nextGaussian() * 1.5);
            pts.add(p);
        }
        Arc a = new Arc();
        a.points = pts;
        a.life = 16;
        a.width = 3.2f;
        a.jitter = 0.9f;
        a.r = 0.65f; a.g = 0.8f; a.b = 1.0f;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    /** A straight soft afterimage beam (flash strikes, shadow strikes, rifts). */
    public static void addBeam(Vec3 from, Vec3 to, int life, float width, float r, float g, float b, boolean dark) {
        Arc a = new Arc();
        a.points = List.of(from, to);
        a.life = life;
        a.streak = !dark;
        a.dark = dark;
        a.forks = false;
        a.jitter = dark ? 0.08f : 0;
        a.width = width;
        a.r = r; a.g = g; a.b = b;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    /** A crackling bolt between two points with a custom colour. */
    public static void addBolt(List<Vec3> points, int life, float width, float r, float g, float b) {
        Arc a = new Arc();
        a.points = points;
        a.life = life;
        a.width = width;
        a.r = r; a.g = g; a.b = b;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    /** Crescent slash with a custom colour (dash hits). */
    public static void addColoredSlash(Vec3 center, Vec3 dir, float roll, float r, float g, float b) {
        addSlash(center, dir, roll);
        Arc a = ARCS.get(ARCS.size() - 1);
        a.dark = false;
        a.r = r; a.g = g; a.b = b;
        a.width = 1.6f;
    }

    /** Tiny crackle across the body of an Overcharged entity. */
    public static void addIdleSpark(Entity e) {
        AABB box = e.getBoundingBox();
        Vec3 p1 = new Vec3(box.minX + RANDOM.nextDouble() * box.getXsize(), box.minY + RANDOM.nextDouble() * box.getYsize(), box.minZ + RANDOM.nextDouble() * box.getZsize());
        Vec3 p2 = p1.add(RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian() * 0.5);
        Arc a = new Arc();
        a.points = List.of(p1, p2);
        a.life = 4;
        a.width = 0.4f;
        a.forks = false;
        a.r = 0.6f; a.g = 0.9f;
        a.seed = RANDOM.nextLong();
        ARCS.add(a);
    }

    public static void tick() {
        ARCS.removeIf(a -> ++a.age >= a.life);
    }

    public static void clear() {
        ARCS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (ARCS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = new Matrix4f();

        // pass 1: dark smoky bodies, pass 2: glow
        VertexConsumer shade = null;
        for (Arc a : ARCS) {
            if (!a.dark) continue;
            if (shade == null) shade = buffers.getBuffer(FxRenderTypes.SHADE);
            float fade = fade(a, partial);
            for (List<Vec3> line : build(a, cam, camera)) {
                strip(shade, m, line, 0.34f * a.width, 0.04f, 0.0f, 0.08f, 0.55f * fade);
            }
        }
        if (shade != null) buffers.endBatch(FxRenderTypes.SHADE);

        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        for (Arc a : ARCS) {
            float fade = fade(a, partial);
            if (a.streak) {
                for (List<Vec3> line : build(a, cam, camera)) {
                    strip(glow, m, line, 1.1f * a.width, a.r, a.g, a.b, 0.16f * fade);
                    strip(glow, m, line, 0.35f * a.width, mix(a.r, 1, 0.5f), mix(a.g, 1, 0.5f), mix(a.b, 1, 0.5f), 0.35f * fade);
                    strip(glow, m, line, 0.05f * a.width, 1.0f, 1.0f, 1.0f, 0.8f * fade);
                }
                continue;
            }
            for (List<Vec3> line : build(a, cam, camera)) {
                if (!a.dark) strip(glow, m, line, 0.5f * a.width, a.r, a.g, a.b, 0.22f * fade);
                strip(glow, m, line, 0.18f * a.width, mix(a.r, 1, 0.4f), mix(a.g, 1, 0.4f), mix(a.b, 1, 0.4f), 0.6f * fade);
                strip(glow, m, line, 0.06f * a.width, 1.0f, 1.0f, 1.0f, (a.dark ? 0.5f : 1.0f) * fade);
            }
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    private static float fade(Arc a, float partial) {
        float t = 1.0f - (a.age + partial) / a.life;
        return Math.max(0, t * t);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Camera-relative jagged lines for one arc (main bolt per hop + forks). */
    private static List<List<Vec3>> build(Arc a, Vec3 cam, Camera camera) {
        RandomSource r = RandomSource.create(a.seed + a.age / 2);
        List<Vec3> pts = a.points;
        Minecraft mc = Minecraft.getInstance();
        if (a.fromHand && mc.player != null && a.sourceId == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) {
            // start the bolt at the weapon on screen instead of the server's guess
            Vector3f look = camera.getLookVector(), up = camera.getUpVector(), left = camera.getLeftVector();
            Vec3 hand = cam.add(look.x() * 0.7 - left.x() * 0.35 - up.x() * 0.3, look.y() * 0.7 - left.y() * 0.35 - up.y() * 0.3, look.z() * 0.7 - left.z() * 0.35 - up.z() * 0.3);
            pts = new ArrayList<>(pts);
            pts.set(0, hand);
        }
        List<List<Vec3>> lines = new ArrayList<>();
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 s = pts.get(i).subtract(cam), e = pts.get(i + 1).subtract(cam);
            double len = s.distanceTo(e);
            List<Vec3> line = new ArrayList<>();
            line.add(s);
            jag(line, s, e, len * 0.16 * a.jitter, len > 3 ? 5 : 4, r);
            lines.add(line);
            if (a.forks) {
                int forks = 1 + r.nextInt(2);
                for (int f = 0; f < forks; f++) {
                    Vec3 from = line.get(1 + r.nextInt(line.size() - 2));
                    Vec3 dir = e.subtract(s).normalize().add(randUnit(r).scale(0.9)).normalize();
                    Vec3 to = from.add(dir.scale(len * (0.15 + r.nextDouble() * 0.25)));
                    List<Vec3> fork = new ArrayList<>();
                    fork.add(from);
                    jag(fork, from, to, from.distanceTo(to) * 0.25, 3, r);
                    lines.add(fork);
                }
            }
        }
        return lines;
    }

    private static void jag(List<Vec3> out, Vec3 a, Vec3 b, double offset, int depth, RandomSource r) {
        if (depth == 0) {
            out.add(b);
            return;
        }
        Vec3 mid = a.add(b).scale(0.5).add(randUnit(r).scale(offset));
        jag(out, a, mid, offset * 0.5, depth - 1, r);
        jag(out, mid, b, offset * 0.5, depth - 1, r);
    }

    private static Vec3 randUnit(RandomSource r) {
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
        return v.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : v.normalize();
    }

    private static void strip(VertexConsumer vc, Matrix4f m, List<Vec3> line, float width, float r, float g, float b, float alpha) {
        for (int i = 0; i + 1 < line.size(); i++) {
            FxRenderTypes.ribbon(vc, m, line.get(i), line.get(i + 1), width, r, g, b, alpha, alpha);
        }
    }
}
