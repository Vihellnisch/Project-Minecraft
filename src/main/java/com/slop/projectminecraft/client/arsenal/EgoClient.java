package com.slop.projectminecraft.client.arsenal;

import static com.slop.projectminecraft.arsenal.ArsenalTiming.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.arsenal.ArsenalEvents;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.client.ScreenFlash;
import com.slop.projectminecraft.client.juice.HitStop;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * E.G.O, Limbus Company style: the cut-in (your face, the sin colour, the skill's name), coins tossed above your head
 * that land heads (more power) or tails, then the attack itself:
 *   Solemn Lament  - The Living and The Departed, two pistols firing in turn, each shot a swarm of funeral butterflies.
 *   Magic Bullet   - the Freischutz rifle; the bullet passes through a corridor of magic circles and leaves a portal.
 *   4th Match Flame - you appear before the target and write the character for "fierce" in fire, then it ignites.
 */
public final class EgoClient {
    private EgoClient() {}

    private static final RandomSource R = RandomSource.create();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Sin colours (Gloom, Pride, Wrath). */
    public static final int[] COLORS = {0x2FA8E0, 0x2A4FC8, 0xE0281E};
    public static final String[] SINS = {"GLOOM", "PRIDE", "WRATH"};
    public static final String[] NAMES = {"Solemn Lament", "Magic Bullet", "4th Match Flame"};
    public static final int[] BASE = {6, 22, 21}, COIN_POWER = {3, 14, 16};

    static final class Ego {
        final int caster, kind, coins, heads, bullet;
        final int[] targets, order;
        final Vec3 start, dir, side;
        int age;
        final List<Butterfly> flies = new ArrayList<>();

        Ego(ArsenalFxPayload p) {
            caster = p.i(0);
            kind = p.i(1);
            coins = p.i(2);
            heads = p.i(3);
            bullet = p.i(4);
            int n = Math.max(0, p.i(5));
            targets = new int[n];
            for (int i = 0; i < n; i++) targets[i] = p.i(6 + i);
            order = new int[coins];
            for (int i = 0; i < coins; i++) order[i] = p.i(6 + n + i);
            start = p.a();
            dir = p.b().lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : p.b().normalize();
            side = dir.cross(UP).normalize();
        }

        boolean head(int coin) {
            return (heads >> coin & 1) == 1;
        }

        int headsUpTo(int coin) {
            int n = 0;
            for (int i = 0; i <= coin && i < coins; i++) if (head(i)) n++;
            return n;
        }

        int total() {
            return ArsenalEvents.total(kind);
        }

        /** When coin i lands (spinning until then). */
        int flipTick(int coin) {
            return switch (kind) {
                case ArsenalEvents.SOLEMN -> solemnShot(coin) - 3;
                case ArsenalEvents.MAGIC_BULLET -> EGO_CUTIN + BULLET_FIRE - 6;
                default -> EGO_CUTIN + MATCH_STROKE0 - 3;
            };
        }

        /** Coins landed so far (for the power number). */
        int landed(float t) {
            int n = 0;
            for (int i = 0; i < coins; i++) if (t >= flipTick(i)) n++;
            return n;
        }

        int power(float t) {
            int l = landed(t);
            return BASE[kind] + COIN_POWER[kind] * (l == 0 ? 0 : headsUpTo(l - 1));
        }

        @Nullable
        Entity target(int index) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || index < 0 || index >= targets.length) return null;
            return mc.level.getEntity(targets[index]);
        }

        Vec3 targetPos(int index, float partial) {
            Entity e = target(index);
            if (e != null) return e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0);
            return start.add(dir.scale(8)).add(0, 1.4, 0);
        }

        Vec3 casterPos(float partial) {
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(caster);
            return e == null ? start : e.getPosition(partial);
        }
    }

    static final List<Ego> EGOS = new ArrayList<>();

    @Nullable
    public static Ego mine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        for (Ego e : EGOS) if (e.caster == mc.player.getId()) return e;
        return null;
    }

    static void start(ArsenalFxPayload p) {
        Ego e = new Ego(p);
        EGOS.add(e);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == e.caster) {
            CinematicCamera.play(new Cam(e));
            ImpactFrame.trigger(ImpactFrame.WHITE, 80);
        }
        Shockwaves.add(e.start.add(0, 1, 0), 4, 500, 1.0f);
    }

    /** 0..1 strength of the sin-coloured grade, and its colour, for the local caster's screen. */
    public static float tint(float partial) {
        Ego e = mine();
        if (e == null) return 0;
        float t = e.age + partial;
        float in = Mth.clamp(t / 4f, 0, 1), out = 1 - Mth.clamp((t - EGO_CUTIN + 4) / 6f, 0, 1);
        float during = 0.25f * (1 - Mth.clamp((t - e.total() + 10) / 10f, 0, 1));
        return Math.max(in * out, during);
    }

    public static int tintColor() {
        Ego e = mine();
        return e == null ? 0 : COLORS[e.kind];
    }

    // =================================================================== ticking: the timeline

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        for (Ego e : EGOS) {
            e.age++;
            boolean mine = mc.player != null && mc.player.getId() == e.caster;
            for (int i = 0; i < e.coins; i++) {
                if (e.age == e.flipTick(i) && mine) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(e.head(i) ? ModSounds.COIN_HEADS.get() : ModSounds.COIN_TAILS.get(), 1.0f + i * 0.05f, 0.9f));
                }
            }
            if (level == null) continue;
            switch (e.kind) {
                case ArsenalEvents.SOLEMN -> tickSolemn(e, level, mine);
                case ArsenalEvents.MAGIC_BULLET -> tickBullet(e, level, mine);
                default -> tickMatch(e, level, mine);
            }
            e.flies.removeIf(b -> ++b.age > b.life);
            for (Butterfly b : e.flies) b.step(e);
        }
        EGOS.removeIf(e -> e.age > e.total() + 30);
    }

    // =================================================================== SOLEMN LAMENT

    static final class Butterfly {
        Vec3 pos, prev;
        final boolean living;
        final int target;
        int age;
        final int life;
        final float phase = R.nextFloat() * 6.28f, orbit = 0.4f + R.nextFloat() * 0.6f, speed = 0.25f + R.nextFloat() * 0.25f;

        Butterfly(Vec3 pos, boolean living, int target, int life) {
            this.pos = this.prev = pos;
            this.living = living;
            this.target = target;
            this.life = life;
        }

        void step(Ego e) {
            prev = pos;
            float t = e.age;
            boolean funeral = t >= EGO_CUTIN + SOLEMN_FUNERAL;
            Vec3 goal = e.targetPos(target, 1);
            if (funeral) {
                // they rise and scatter, leaving
                Vec3 out = pos.subtract(goal).normalize();
                pos = pos.add(out.scale(0.25)).add(0, 0.12 + 0.05 * Math.sin(age * 0.7 + phase), 0);
                return;
            }
            double ang = t * 0.25 * speed * 4 + phase;
            Vec3 around = goal.add(Math.cos(ang) * orbit, Math.sin(ang * 1.7) * 0.5, Math.sin(ang) * orbit);
            Vec3 d = around.subtract(pos);
            double len = d.length();
            pos = pos.add(len > 0.01 ? d.scale(Math.min(1, speed * 1.8 / Math.max(len, 0.5))) : Vec3.ZERO)
                    .add(R.nextGaussian() * 0.04, Math.sin(age * 0.9 + phase) * 0.05, R.nextGaussian() * 0.04);
        }
    }

    /** Where each pistol floats: The Living at your right, The Departed at your left. */
    static Vec3 pistol(Ego e, boolean living, float partial) {
        return e.casterPos(partial).add(0, 1.35, 0).add(e.side.scale(living ? 0.95 : -0.95)).add(e.dir.scale(0.35));
    }

    static void tickSolemn(Ego e, ClientLevel level, boolean mine) {
        for (int i = 0; i < e.coins; i++) {
            if (e.age != solemnShot(i)) continue;
            boolean living = i % 2 == 0;
            Vec3 from = pistol(e, living, 1).add(e.dir.scale(0.5));
            Vec3 to = e.targetPos(e.order[i], 1);
            ArcFx.addBeam(from, to, 7, 0.14f, living ? 0.95f : 0.25f, living ? 0.97f : 0.15f, living ? 1.0f : 0.4f, !living);
            RingFx.flash(from, 0.6f, 4, living ? 1.0f : 0.5f, living ? 1.0f : 0.3f, 1.0f, 1.0f);
            RingFx.add(to, from.subtract(to).normalize(), 0.1f, 1.4f, 7, 0.4f, 0.7f, 1.0f, 0.9f);
            for (int b = 0; b < 9; b++) e.flies.add(new Butterfly(from.add(R.nextGaussian() * 0.2, R.nextGaussian() * 0.2, R.nextGaussian() * 0.2), living, e.order[i], 70 + R.nextInt(30)));
            if (mine) {
                CameraShake.add(0.25f);
                HitStop.trigger(1);
            }
        }
        if (e.age == EGO_CUTIN + SOLEMN_FUNERAL) {
            for (int i = 0; i < e.targets.length; i++) {
                Vec3 at = e.targetPos(i, 1);
                RingFx.add(at, UP, 0.3f, 4, 14, 0.7f, 0.85f, 1.0f, 0.9f);
                for (int b = 0; b < 14; b++) e.flies.add(new Butterfly(at.add(R.nextGaussian() * 0.5, R.nextGaussian() * 0.5, R.nextGaussian() * 0.5), b % 2 == 0, i, 40 + R.nextInt(20)));
                for (int k = 0; k < 10; k++) level.addParticle(ParticleTypes.SOUL, at.x + R.nextGaussian() * 0.4, at.y + R.nextGaussian() * 0.4, at.z + R.nextGaussian() * 0.4, 0, 0.05, 0);
            }
            if (mine) {
                ImpactFrame.trigger(ImpactFrame.CYAN, 120);
                HitStop.trigger(3);
            }
        }
    }

    static void renderSolemn(Ego e, VertexConsumer solid, VertexConsumer shade, VertexConsumer glow, Matrix4f m, Vec3 cam, float partial) {
        float t = e.age + partial;
        if (t < EGO_CUTIN - 6 || t > e.total() - 6) return;
        float appear = Mth.clamp((t - EGO_CUTIN + 6) / 6f, 0, 1) * (1 - Mth.clamp((t - e.total() + 14) / 8f, 0, 1));
        for (int g = 0; g < 2; g++) {
            boolean living = g == 0;
            Vec3 at = pistol(e, living, partial).subtract(cam).add(0, Math.sin(t * 0.15 + g) * 0.06, 0);
            // aim at the target of the most recent shot by this gun
            Vec3 aim = e.dir;
            float kick = 0;
            for (int i = living ? 0 : 1; i < e.coins; i += 2) {
                if (t >= solemnShot(i) - 4) {
                    aim = e.targetPos(e.order[i], partial).subtract(cam).subtract(at).normalize();
                    kick = Math.max(0, 1 - (t - solemnShot(i)) / 4f) * (t >= solemnShot(i) ? 1 : 0);
                }
            }
            gun(solid, glow, m, at.subtract(aim.scale(0.25 * kick)).add(0, 0.12 * kick, 0), aim, living, appear);
        }
        // butterflies
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cu = new Vec3(camera.getUpVector());
        for (Butterfly b : e.flies) {
            Vec3 p = b.prev.lerp(b.pos, partial).subtract(cam);
            float fade = Mth.clamp((b.life - b.age - partial) / 10f, 0, 1);
            float flap = Mth.sin((b.age + partial) * 1.6f + b.phase);
            Vec3 heading = b.pos.subtract(b.prev);
            Vec3 fwd = heading.lengthSqr() < 1e-5 ? e.dir : heading.normalize();
            Vec3 right = fwd.cross(UP).lengthSqr() < 1e-4 ? e.side : fwd.cross(UP).normalize();
            float s = 0.24f;
            float r = b.living ? 0.97f : 0.08f, g = b.living ? 0.97f : 0.06f, bl = b.living ? 1.0f : 0.12f;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 tipA = p.add(right.scale(side * s * (0.4 + 0.6 * Math.abs(flap)))).add(UP.scale(s * flap * 0.8)).add(fwd.scale(s * 0.55));
                Vec3 tipB = p.add(right.scale(side * s * 0.8 * (0.4 + 0.6 * Math.abs(flap)))).add(UP.scale(s * flap * 0.6)).add(fwd.scale(-s * 0.6));
                if (shade != null) quad(shade, m, p.add(fwd.scale(s * 0.2)), tipA, tipB, p.add(fwd.scale(-s * 0.25)), r, g, bl, 0.95f * fade);
            }
            if (glow != null) FxRenderTypes.sphere(glow, m, p, 0.12f, 4, 6, b.living ? 0.9f : 0.5f, b.living ? 0.95f : 0.3f, 1.0f, 0.25f * fade);
        }
    }

    /** Double-sided quad. */
    static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float al) {
        FxRenderTypes.vertex(vc, m, a, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, b, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, c, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, d, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, d, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, c, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, b, r, g, bl, al);
        FxRenderTypes.vertex(vc, m, a, r, g, bl, al);
    }

    /** A long-barrelled pistol out of boxes (white with black, or black with white). */
    static void gun(VertexConsumer vc, VertexConsumer glow, Matrix4f m, Vec3 at, Vec3 aim, boolean living, float scale) {
        if (scale <= 0.01f) return;
        if (vc == null) {
            if (glow != null) {
                Vec3 f0 = aim.normalize();
                FxRenderTypes.sphere(glow, m, at.add(f0.scale(0.5 * scale)), 0.08f * scale, 4, 6, living ? 1 : 0.5f, living ? 1 : 0.3f, 1, 0.5f);
            }
            return;
        }
        Vec3 f = aim.normalize();
        Vec3 r = f.cross(UP).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : f.cross(UP).normalize();
        Vec3 u = r.cross(f).normalize();
        Vec3 light = new Vec3(0.3, 1, 0.4).normalize();
        float base = living ? 0.93f : 0.1f, trim = living ? 0.12f : 0.9f;
        float s = scale;
        FxRenderTypes.box(vc, m, at.add(f.scale(0.18 * s)), f.scale(0.34 * s), u.scale(0.06 * s), r.scale(0.05 * s), light, base, base, base, 1);
        FxRenderTypes.box(vc, m, at.add(f.scale(-0.12 * s)).add(u.scale(-0.02 * s)), f.scale(0.14 * s), u.scale(0.09 * s), r.scale(0.07 * s), light, base, base, base, 1);
        FxRenderTypes.box(vc, m, at.add(f.scale(-0.2 * s)).add(u.scale(-0.22 * s)), f.scale(0.07 * s), u.scale(0.16 * s), r.scale(0.055 * s), light, trim, trim, trim, 1);
        FxRenderTypes.box(vc, m, at.add(f.scale(0.05 * s)).add(u.scale(0.075 * s)), f.scale(0.3 * s), u.scale(0.015 * s), r.scale(0.03 * s), light, trim, trim, trim, 1);
    }

    // =================================================================== MAGIC BULLET

    static int circles(Ego e) {
        return BULLET_CIRCLES + e.bullet / 2;
    }

    static float circleRadius(Ego e) {
        return e.bullet == 7 ? 2.4f : 1.1f;
    }

    static Vec3 muzzle(Ego e, float partial) {
        return e.casterPos(partial).add(0, 1.45, 0).add(e.side.scale(0.25)).add(aim(e, partial).scale(1.4));
    }

    static Vec3 aim(Ego e, float partial) {
        Vec3 eye = e.casterPos(partial).add(0, 1.45, 0);
        return e.targets.length == 0 ? e.dir : e.targetPos(0, partial).subtract(eye).normalize();
    }

    static void tickBullet(Ego e, ClientLevel level, boolean mine) {
        int n = circles(e);
        for (int i = 0; i < n; i++) {
            if (e.age == EGO_CUTIN + 2 + i * 4) {
                Vec3 c = muzzle(e, 1).add(aim(e, 1).scale(1.4 + i * 1.6));
                RingFx.flash(c, circleRadius(e), 5, 0.4f, 0.6f, 1.0f, 0.9f);
                if (mine) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.MAGIC_CIRCLE.get(), 0.8f + i * 0.12f, 0.6f));
            }
        }
        if (e.age == EGO_CUTIN + BULLET_FIRE) {
            Vec3 from = muzzle(e, 1);
            Vec3 a = aim(e, 1);
            Vec3 end = from.add(a.scale(72));
            boolean seventh = e.bullet == 7;
            ArcFx.addBeam(from, end, seventh ? 30 : 16, seventh ? 2.4f : 0.5f, 0.15f, 0.25f, 0.85f, false);
            ArcFx.addBeam(from, end, seventh ? 20 : 10, seventh ? 0.9f : 0.18f, 0.85f, 0.9f, 1.0f, false);
            ArcFx.addBeam(from, end, seventh ? 34 : 18, seventh ? 3.5f : 0.8f, 0.05f, 0.03f, 0.15f, true);
            Shockwaves.add(from, seventh ? 14 : 6, 600, seventh ? 2.2f : 1.2f);
            if (e.targets.length > 0) {
                Vec3 at = e.targetPos(0, 1);
                RingFx.flash(at, seventh ? 4 : 1.8f, 8, 0.3f, 0.5f, 1.0f, 1.0f);
                for (int k = 0; k < 30; k++) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, R.nextGaussian() * 0.25, R.nextDouble() * 0.3, R.nextGaussian() * 0.25);
            }
            if (mine) {
                ImpactFrame.trigger(seventh ? ImpactFrame.VIOLET : ImpactFrame.CYAN, seventh ? 200 : 110);
                HitStop.trigger(seventh ? 6 : 3);
                CameraShake.add(seventh ? 1.2f : 0.6f);
                if (seventh) ScreenFlash.flash(0.6f);
            }
        }
        if (e.age > EGO_CUTIN + BULLET_FIRE && e.age < EGO_CUTIN + BULLET_FIRE + 40) {
            for (int i = 0; i < e.targets.length; i++) {
                Entity t = e.target(i);
                if (t == null || i > 0 && e.bullet != 7) continue;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, t.getX() + R.nextGaussian() * 0.3, t.getY() + R.nextDouble() * t.getBbHeight(), t.getZ() + R.nextGaussian() * 0.3, 0, 0.05, 0);
            }
        }
    }

    static void renderBullet(Ego e, VertexConsumer solid, VertexConsumer glow, Matrix4f m, Vec3 cam, float partial) {
        float t = e.age + partial;
        if (t < EGO_CUTIN - 4 || t > EGO_CUTIN + BULLET_FIRE + 20) return;
        float fade = 1 - Mth.clamp((t - EGO_CUTIN - BULLET_FIRE - 4) / 14f, 0, 1);
        Vec3 a = aim(e, partial);
        Vec3 mz = muzzle(e, partial).subtract(cam);
        // the Freischutz: a very long rifle at the shoulder
        if (solid != null) rifle(solid, m, mz.subtract(a.scale(1.4)), a, Mth.clamp((t - EGO_CUTIN + 4) / 5f, 0, 1) * fade);
        if (glow == null) return;
        int n = circles(e);
        float rad = circleRadius(e);
        for (int i = 0; i < n; i++) {
            float born = EGO_CUTIN + 2 + i * 4;
            float k = Mth.clamp((t - born) / 4f, 0, 1);
            if (k <= 0) continue;
            float passed = t >= EGO_CUTIN + BULLET_FIRE ? Mth.clamp((t - EGO_CUTIN - BULLET_FIRE) / 3f, 0, 1) : 0;
            Vec3 c = mz.add(a.scale(1.4 + i * 1.6));
            float rr = rad * k * (1 + 0.4f * passed) * (0.85f + 0.15f * i / Math.max(1, n - 1));
            float al = fade * (1 - 0.5f * passed);
            magicCircle(glow, m, c, a, rr, t * 0.08f * (i % 2 == 0 ? 1 : -1), al);
        }
        // the portal it comes out of, beside the target
        if (e.targets.length > 0 && t > EGO_CUTIN + BULLET_FIRE - 6) {
            Vec3 at = e.targetPos(0, partial).subtract(cam);
            float k = Mth.clamp((t - EGO_CUTIN - BULLET_FIRE + 6) / 5f, 0, 1) * fade;
            magicCircle(glow, m, at.subtract(a.scale(1.4)), a, rad * 1.2f * k, -t * 0.1f, k);
        }
    }

    static void magicCircle(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 normal, float r, float spin, float alpha) {
        if (r <= 0.02f || alpha <= 0.01f) return;
        Vec3 n = normal.normalize();
        Vec3 t1 = Math.abs(n.y) < 0.99 ? n.cross(UP).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 t2 = n.cross(t1).normalize();
        FxRenderTypes.annulus(vc, m, c, n, r * 0.92f, r, 40, 0.35f, 0.55f, 1.0f, alpha, alpha);
        FxRenderTypes.annulus(vc, m, c, n, r * 0.7f, r * 0.74f, 40, 0.5f, 0.7f, 1.0f, alpha * 0.9f, alpha * 0.9f);
        FxRenderTypes.annulus(vc, m, c, n, 0, r * 0.7f, 40, 0.1f, 0.15f, 0.5f, alpha * 0.25f, alpha * 0.05f);
        // a hexagram
        for (int tri = 0; tri < 2; tri++) {
            for (int i = 0; i < 3; i++) {
                double a0 = spin + tri * Math.PI / 3 + i * Math.PI * 2 / 3, a1 = a0 + Math.PI * 2 / 3;
                Vec3 p0 = c.add(t1.scale(Math.cos(a0) * r * 0.7)).add(t2.scale(Math.sin(a0) * r * 0.7));
                Vec3 p1 = c.add(t1.scale(Math.cos(a1) * r * 0.7)).add(t2.scale(Math.sin(a1) * r * 0.7));
                FxRenderTypes.ribbon(vc, m, p0, p1, 0.035f * r + 0.02f, 0.5f, 0.75f, 1.0f, alpha, alpha);
            }
        }
        // runes ticking around the rim
        for (int i = 0; i < 18; i++) {
            double a0 = -spin * 1.5 + i * Math.PI * 2 / 18;
            Vec3 p0 = c.add(t1.scale(Math.cos(a0) * r * 0.8)).add(t2.scale(Math.sin(a0) * r * 0.8));
            Vec3 p1 = c.add(t1.scale(Math.cos(a0 + 0.12) * r * 0.86)).add(t2.scale(Math.sin(a0 + 0.12) * r * 0.86));
            FxRenderTypes.ribbon(vc, m, p0, p1, 0.03f * r + 0.015f, 0.7f, 0.85f, 1.0f, alpha, alpha);
        }
    }

    static void rifle(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 aim, float s) {
        if (s <= 0.01f) return;
        Vec3 f = aim.normalize();
        Vec3 r = f.cross(UP).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : f.cross(UP).normalize();
        Vec3 u = r.cross(f).normalize();
        Vec3 light = new Vec3(0.3, 1, 0.4).normalize();
        FxRenderTypes.box(vc, m, at.add(f.scale(0.75 * s)), f.scale(0.8 * s), u.scale(0.035 * s), r.scale(0.035 * s), light, 0.12f, 0.12f, 0.16f, 1);
        FxRenderTypes.box(vc, m, at.add(f.scale(0.1 * s)).add(u.scale(-0.03 * s)), f.scale(0.35 * s), u.scale(0.06 * s), r.scale(0.05 * s), light, 0.3f, 0.18f, 0.1f, 1);
        FxRenderTypes.box(vc, m, at.add(f.scale(-0.35 * s)).add(u.scale(-0.1 * s)), f.scale(0.25 * s), u.scale(0.1 * s), r.scale(0.055 * s), light, 0.32f, 0.2f, 0.1f, 1);
        for (int i = 0; i < 3; i++) FxRenderTypes.box(vc, m, at.add(f.scale((0.3 + i * 0.45) * s)), f.scale(0.03 * s), u.scale(0.045 * s), r.scale(0.045 * s), light, 0.85f, 0.65f, 0.2f, 1);
    }

    // =================================================================== 4TH MATCH FLAME

    /** The ten strokes of the character, in a unit square (x right, y down). */
    static final float[][] STROKES = {
            {0.08f, 0.10f, 0.56f, 0.10f},
            {0.33f, 0.12f, 0.22f, 0.30f, 0.11f, 0.42f},
            {0.22f, 0.24f, 0.48f, 0.24f, 0.36f, 0.42f, 0.18f, 0.58f},
            {0.27f, 0.36f, 0.37f, 0.44f},
            {0.66f, 0.16f, 0.66f, 0.46f},
            {0.87f, 0.05f, 0.87f, 0.58f, 0.80f, 0.53f},
            {0.15f, 0.72f, 0.08f, 0.88f},
            {0.36f, 0.72f, 0.40f, 0.88f},
            {0.58f, 0.72f, 0.62f, 0.88f},
            {0.79f, 0.70f, 0.91f, 0.88f}};

    static Vec3 kanjiPoint(Ego e, Vec3 center, float x, float y, float size) {
        // facing the caster: x runs to the caster's right, y down
        return center.add(e.side.scale((x - 0.5) * size)).add(0, (0.5 - y) * size, 0);
    }

    static Vec3 kanjiCenter(Ego e, float partial) {
        Vec3 t = e.targetPos(0, partial);
        return t.subtract(e.dir.scale(0.4)).add(0, 1.6, 0);
    }

    static void tickMatch(Ego e, ClientLevel level, boolean mine) {
        int s0 = EGO_CUTIN + MATCH_STROKE0;
        for (int i = 0; i < STROKES.length; i++) {
            if (e.age != s0 + i * MATCH_STROKE_GAP) continue;
            float[] st = STROKES[i];
            Vec3 c = kanjiCenter(e, 1);
            Vec3 a = kanjiPoint(e, c, st[0], st[1], 4.2f), b = kanjiPoint(e, c, st[st.length - 2], st[st.length - 1], 4.2f);
            for (int k = 0; k < 6; k++) {
                Vec3 q = a.lerp(b, R.nextDouble());
                level.addParticle(ParticleTypes.FLAME, q.x, q.y, q.z, R.nextGaussian() * 0.05, 0.05, R.nextGaussian() * 0.05);
            }
            level.addParticle(ParticleTypes.LAVA, b.x, b.y, b.z, 0, 0, 0);
            if (mine) CameraShake.add(0.12f);
        }
        if (e.age == EGO_CUTIN + MATCH_IGNITE) {
            Vec3 c = kanjiCenter(e, 1);
            RingFx.flash(c, 4.5f, 8, 1.0f, 0.5f, 0.15f, 1.0f);
            RingFx.add(c, e.dir, 0.5f, 8, 12, 1.0f, 0.35f, 0.1f, 1.0f);
            Shockwaves.add(c, 12, 700, 2.0f);
            for (int k = 0; k < 50; k++) level.addParticle(k % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, c.x, c.y, c.z, R.nextGaussian() * 0.5, R.nextGaussian() * 0.5, R.nextGaussian() * 0.5);
            if (mine) {
                ImpactFrame.trigger(ImpactFrame.RED, 160);
                HitStop.trigger(4);
                CameraShake.add(0.9f);
                ScreenFlash.flash(0.4f);
            }
        }
        if (e.age > EGO_CUTIN + MATCH_DASH && e.age < EGO_CUTIN + MATCH_IGNITE) {
            Vec3 p = e.casterPos(1).add(0, 1.2, 0).add(e.side.scale(0.5));
            level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.04, 0);
        }
    }

    static void renderMatch(Ego e, VertexConsumer glow, Matrix4f m, Vec3 cam, float partial) {
        if (glow == null) return;
        float t = e.age + partial;
        int s0 = EGO_CUTIN + MATCH_STROKE0;
        if (t < s0 || t > EGO_CUTIN + MATCH_IGNITE + 26) return;
        Vec3 c = kanjiCenter(e, partial).subtract(cam);
        float ignite = Mth.clamp((t - EGO_CUTIN - MATCH_IGNITE) / 3f, 0, 1);
        float fade = 1 - Mth.clamp((t - EGO_CUTIN - MATCH_IGNITE - 4) / 20f, 0, 1);
        float size = 4.2f * (1 + 0.25f * ignite);
        for (int i = 0; i < STROKES.length; i++) {
            float born = s0 + i * MATCH_STROKE_GAP;
            float k = Mth.clamp((t - born) / MATCH_STROKE_GAP, 0, 1);
            if (k <= 0) continue;
            float[] st = STROKES[i];
            int pts = st.length / 2;
            // draw progressively along the stroke's polyline
            double total = 0;
            for (int j = 1; j < pts; j++) total += Math.hypot(st[j * 2] - st[j * 2 - 2], st[j * 2 + 1] - st[j * 2 - 1]);
            double drawn = total * k, acc = 0;
            for (int j = 1; j < pts && acc < drawn; j++) {
                float x0 = st[j * 2 - 2], y0 = st[j * 2 - 1], x1 = st[j * 2], y1 = st[j * 2 + 1];
                double len = Math.hypot(x1 - x0, y1 - y0);
                double part = Math.min(1, (drawn - acc) / len);
                acc += len;
                Vec3 a = kanjiPoint(e, c, x0, y0, size), b = kanjiPoint(e, c, (float) (x0 + (x1 - x0) * part), (float) (y0 + (y1 - y0) * part), size);
                float flick = 0.85f + 0.15f * Mth.sin(t * 2.3f + i * 1.7f + j);
                float w = (0.32f + 0.25f * ignite) * flick;
                FxRenderTypes.ribbon(glow, m, a, b, w * 1.9f, 1.0f, 0.25f, 0.05f, 0.45f * fade, 0.45f * fade);
                FxRenderTypes.ribbon(glow, m, a, b, w, 1.0f, 0.6f + 0.3f * ignite, 0.2f + 0.5f * ignite, 0.9f * fade, 0.9f * fade);
                FxRenderTypes.ribbon(glow, m, a, b, w * 0.35f, 1.0f, 0.95f, 0.8f, fade, fade);
            }
        }
        if (ignite > 0) FxRenderTypes.sphere(glow, m, c, 2.6f * ignite * fade + 0.2f, 10, 14, 1.0f, 0.45f, 0.1f, 0.35f * fade);
    }

    // =================================================================== camera

    static final class Cam implements CinematicCamera.Director {
        final Ego e;

        Cam(Ego e) {
            this.e = e;
        }

        @Override
        public int duration() {
            return e.total();
        }

        @Override
        public void frame(float t, CinematicCamera.Shot s) {
            Vec3 me = e.casterPos(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
            Vec3 head = me.add(0, 1.55, 0);
            s.letterbox = 1;
            if (t < EGO_CUTIN) {
                // the cut-in: a tilted close-up from below
                float k = CinematicCamera.ease(t, 0, EGO_CUTIN);
                Vec3 from = head.add(e.dir.scale(2.4 - 0.6 * k)).add(e.side.scale(0.9)).add(0, -0.5, 0);
                s.lookAt(from, head);
                s.roll = -8 + 4 * k;
                s.fov = 50;
                return;
            }
            Vec3 tgt = e.targetPos(0, 1);
            switch (e.kind) {
                case ArsenalEvents.SOLEMN -> {
                    float k = CinematicCamera.ease(t, EGO_CUTIN, EGO_CUTIN + SOLEMN_FUNERAL);
                    Vec3 mid = head.lerp(tgt, 0.45);
                    Vec3 from = me.subtract(e.dir.scale(3.4)).add(e.side.scale(2.6 - 4.8 * k)).add(0, 2.2, 0);
                    if (t > EGO_CUTIN + SOLEMN_FUNERAL - 2) from = tgt.subtract(e.dir.scale(5)).add(e.side.scale(4)).add(0, 1.5, 0);
                    s.lookAt(from, mid);
                    s.fov = 68;
                }
                case ArsenalEvents.MAGIC_BULLET -> {
                    // side on to the corridor of circles
                    Vec3 mid = head.add(aim(e, 1).scale(5));
                    float k = CinematicCamera.ease(t, EGO_CUTIN, EGO_CUTIN + BULLET_FIRE);
                    Vec3 from = mid.add(e.side.scale(-6.5 + 1.5 * k)).add(e.dir.scale(-2)).add(0, 0.8, 0);
                    s.lookAt(from, mid);
                    s.fov = 66 + (t > EGO_CUTIN + BULLET_FIRE ? 10 : 0);
                }
                default -> {
                    // behind you, facing the character as it is written
                    Vec3 kc = kanjiCenter(e, 1);
                    Vec3 from = kc.subtract(e.dir.scale(8.5)).add(e.side.scale(3.6)).add(0, 0.4, 0);
                    if (t < EGO_CUTIN + MATCH_STROKE0) from = head.subtract(e.dir.scale(4)).add(e.side.scale(2)).add(0, 0.6, 0);
                    s.lookAt(from, t < EGO_CUTIN + MATCH_STROKE0 ? tgt : kc);
                    s.fov = 64 + (t > EGO_CUTIN + MATCH_IGNITE ? 8 * CinematicCamera.ease(t, EGO_CUTIN + MATCH_IGNITE, EGO_CUTIN + MATCH_IGNITE + 4) : 0);
                }
            }
            CinematicCamera.returnToPlayer(s, t, e.total() - 14, e.total());
        }
    }

    // =================================================================== world coins above the caster

    static void renderCoins(VertexConsumer solid, VertexConsumer glow, Matrix4f m, Vec3 cam, float partial) {
        for (Ego e : EGOS) {
            float t = e.age + partial;
            if (t < 6 || t > e.total() - 4) continue;
            Vec3 above = e.casterPos(partial).add(0, 2.55, 0).subtract(cam);
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 right = new Vec3(camera.getLeftVector()).scale(-1);
            for (int i = 0; i < e.coins; i++) {
                Vec3 c = above.add(right.scale((i - (e.coins - 1) / 2.0) * 0.42));
                int flip = e.flipTick(i);
                float toss = Mth.clamp((t - (flip - 10)) / 10f, 0, 1);
                c = c.add(0, Math.sin(toss * Math.PI) * 0.6, 0);
                boolean landed = t >= flip;
                Vec3 axis = landed ? new Vec3(camera.getLookVector()).scale(-1) : new Vec3(Math.cos(t * 1.4), 0.2, Math.sin(t * 1.4)).normalize();
                boolean head = e.head(i);
                float r = landed ? (head ? 1.0f : 0.45f) : 0.95f, g = landed ? (head ? 0.82f : 0.45f) : 0.78f, b = landed ? (head ? 0.3f : 0.48f) : 0.3f;
                if (solid != null) {
                    FxRenderTypes.annulus(solid, m, c, axis, 0, 0.16f, 14, r, g, b, 1, 1);
                    FxRenderTypes.annulus(solid, m, c, axis.scale(-1), 0, 0.16f, 14, r * 0.8f, g * 0.8f, b * 0.8f, 1, 1);
                }
                if (glow != null && landed && head) FxRenderTypes.sphere(glow, m, c, 0.2f, 6, 8, 1.0f, 0.8f, 0.3f, 0.16f * (0.7f + 0.3f * Mth.sin(t * 0.5f + i)));
            }
        }
    }

    static void render(VertexConsumer solid, VertexConsumer shade, VertexConsumer glow, Matrix4f m, Vec3 cam, float partial) {
        for (Ego e : EGOS) {
            switch (e.kind) {
                case ArsenalEvents.SOLEMN -> renderSolemn(e, solid, shade, glow, m, cam, partial);
                case ArsenalEvents.MAGIC_BULLET -> renderBullet(e, solid, glow, m, cam, partial);
                default -> renderMatch(e, glow, m, cam, partial);
            }
        }
        renderCoins(solid, glow, m, cam, partial);
    }

    static void clear() {
        EGOS.clear();
    }
}
