package com.slop.projectminecraft.client.juice;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Screen-space shockwave distortion rings anchored in the world (explosions, slams, ultimates). Each frame the
 * active ones are projected to the screen and handed to the "shock" post shader (up to 4 at once).
 */
public final class Shockwaves {
    private Shockwaves() {}

    private record Wave(Vec3 pos, long start, float durationMs, float maxRadius, float strength) {}

    private static final List<Wave> WAVES = new ArrayList<>();
    public static final int MAX = 4;
    public static final float[] X = new float[MAX], Y = new float[MAX], R = new float[MAX], A = new float[MAX];
    public static int count;

    public static void add(Vec3 pos, float maxRadius, float durationMs, float strength) {
        WAVES.add(new Wave(pos, System.nanoTime(), durationMs, maxRadius, strength));
        if (WAVES.size() > 8) WAVES.remove(0);
    }

    public static void clear() {
        WAVES.clear();
        count = 0;
    }

    /** Project live waves to screen space; call once per frame before the post chain runs. */
    public static void prepare(Matrix4f proj, Matrix4f view, Vec3 cam, float fovDegrees) {
        count = 0;
        long now = System.nanoTime();
        WAVES.removeIf(w -> (now - w.start) / 1e6f > w.durationMs);
        if (WAVES.isEmpty()) return;
        Matrix4f vp = new Matrix4f(proj).mul(view);
        float tanHalf = (float) Math.tan(Math.toRadians(fovDegrees) * 0.5);
        for (Wave w : WAVES) {
            if (count >= MAX) break;
            float t = ((now - w.start) / 1e6f) / w.durationMs;
            Vec3 rel = w.pos.subtract(cam);
            double dist = Math.max(0.5, rel.length());
            Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1).mul(vp);
            float worldR = w.maxRadius * (1 - (1 - t) * (1 - t));
            if (clip.w <= 0.05f) {
                // behind you: still ripple the edges of the screen from roughly the right side
                X[count] = clip.x < 0 ? -0.5f : 1.5f;
                Y[count] = 0.5f;
            } else {
                X[count] = (clip.x / clip.w) * 0.5f + 0.5f;
                Y[count] = (clip.y / clip.w) * 0.5f + 0.5f;
            }
            R[count] = (float) (worldR / (2 * dist * tanHalf));
            A[count] = w.strength * (1 - t) * (float) Math.min(1.0, 12.0 / dist);
            count++;
        }
    }
}
