package com.slop.projectminecraft.arsenal;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WeaponMath {
    private WeaponMath() {}

    /** Distance from p to the segment a-b. */
    public static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1e-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / ab.lengthSqr()));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Horizontal unit vector of a look direction (z+ if looking straight up/down). */
    public static Vec3 flat(Vec3 look) {
        Vec3 f = new Vec3(look.x, 0, look.z);
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    // =================================================================== hitscan

    public record Hit(Vec3 end, @Nullable LivingEntity victim, boolean head) {}

    /** First living thing (or block) along a ray. `inflate` widens hitboxes a little for aim assist. */
    public static Hit trace(ServerLevel level, @Nullable Entity shooter, Vec3 from, Vec3 dir, double range, double inflate) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter == null ? net.minecraft.world.phys.shapes.CollisionContext.empty() : net.minecraft.world.phys.shapes.CollisionContext.of(shooter)));
        if (bh.getType() != HitResult.Type.MISS) to = bh.getLocation();
        LivingEntity best = null;
        Vec3 bestAt = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5),
                e -> e != shooter && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !e.isSpectator())) {
            Optional<Vec3> c = e.getBoundingBox().inflate(inflate).clip(from, to);
            if (c.isEmpty()) continue;
            double d = c.get().distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = e;
                bestAt = c.get();
            }
        }
        if (best == null) return new Hit(to, null, false);
        boolean head = bestAt.y > best.getEyeY() - 0.32 && best.getBbHeight() > 0.6;
        return new Hit(bestAt, best, head);
    }

    static boolean visible(ServerLevel level, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }
}
