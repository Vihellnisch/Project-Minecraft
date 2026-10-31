package com.slop.projectminecraft.client.arsenal;

import static com.slop.projectminecraft.arsenal.FuriosoScript.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.client.ScreenFlash;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModItems;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Furioso, drawn: the camera, the moves (keyframed in Blockbench, see FuriosoAnims), every workshop weapon the gloves
 * produce in a puff of black mist and toss away when done, the hits, and the slow motion. Everything runs on script
 * time (FuriosoScript), which crawls through the big moments so they can be seen.
 */
public final class FuriosoClient {
    private FuriosoClient() {}

    private static final RandomSource R = RandomSource.create();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Freeze-frame caption per weapon (index = weapon id). */
    public static final String[][] CAPTIONS = {
            {"Atelier Logic", "Pistols"}, {"Allas Workshop", "Lance"}, {"Old Boys Workshop", "Hammer"}, {"Mook Workshop", "Katana"},
            {"Ranga Workshop", "Gauntlets"}, {"Ranga Workshop", "Combat Knife"}, {"Zelkova Workshop", "Axe"}, {"Zelkova Workshop", "Mace"},
            {"Wheels Industry", "Greatsword"}, {"Crystal Atelier", "Short Swords"}, {"Atelier Logic", "Shotgun"}, {"", "Durandal"}};

    /** A spent weapon flung away, dissolving into the gloves' black mist. */
    static final class Toss {
        final int weapon;
        final boolean left;
        final Vec3 from, vel;
        int age;

        Toss(int weapon, boolean left, Vec3 from, Vec3 vel) {
            this.weapon = weapon;
            this.left = left;
            this.from = from;
            this.vel = vel;
        }

        Vec3 at(float partial) {
            double a = age + partial;
            return from.add(vel.scale(a)).add(0, -0.03 * a * a, 0);
        }
    }

    public static final class Run {
        final int caster, victim;
        final FuriosoScript s;
        int age;
        double last = -1;
        final List<Toss> tossed = new ArrayList<>();
        /** Hit counter flash (1 on a hit, fading). */
        float pulse;
        /** Where Durandal's blade was last drawn (guard and tip, world space), recorded by the glove layer. */
        Vec3 bladeBase, bladeTip;

        Run(int caster, int victim, FuriosoScript s) {
            this.caster = caster;
            this.victim = victim;
            this.s = s;
        }

        public double st(float partial) {
            return scriptTime(age + partial);
        }

        float victimHeight() {
            Minecraft mc = Minecraft.getInstance();
            Entity v = mc.level == null ? null : mc.level.getEntity(victim);
            return v == null ? 1.8f : v.getBbHeight();
        }

        Vec3 victimCenter(double t) {
            return s.victim(Math.min(t, KILL)).add(0, victimHeight() * 0.55, 0);
        }
    }

    static final List<Run> RUNS = new ArrayList<>();
    static float grade, gradePrev;
    private static ItemStack carrier = ItemStack.EMPTY;

    /** 0..1: how far into slow motion the local caster is (smoothed, for the screen grade). */
    public static float grade(float partial) {
        return Mth.lerp(partial, gradePrev, grade);
    }

    @Nullable
    public static Run of(int caster) {
        for (Run r : RUNS) if (r.caster == caster) return r;
        return null;
    }

    @Nullable
    public static Run mine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : of(mc.player.getId());
    }

    /** The glove layer reports where it drew Durandal (world space), so the edge glow follows the real blade. */
    static void blade(int caster, Vec3 base, Vec3 tip) {
        Run r = of(caster);
        if (r == null) return;
        r.bladeBase = base;
        r.bladeTip = tip;
    }

    public static boolean active(int caster) {
        return of(caster) != null;
    }

    /** The weapon the gloves hold right now, or -1. */
    public static int heldWeapon(Run r, float partial) {
        return FuriosoScript.weapon(r.st(partial));
    }

    static void start(ArsenalFxPayload p) {
        FuriosoScript s = new FuriosoScript(p.a(), p.b(), p.i(2) / 16.0);
        Run r = new Run(p.i(0), p.i(1), s);
        RUNS.removeIf(o -> o.caster == r.caster);
        RUNS.add(r);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == r.caster) {
            CinematicCamera.play(new Cam(r));
            ImpactFrame.trigger(ImpactFrame.WHITE, 90);
        }
        play(r, s.p0, ModSounds.FURIOSO_STING.get(), 2.0f, 1.0f, 0);
    }

    /** Slow motion factor near a position (gore and blood crawl inside a running Furioso). */
    public static float timeScale(Vec3 pos) {
        for (Run r : RUNS) {
            if (r.s.g.distanceToSqr(pos) > 30 * 30) continue;
            return (float) Math.max(0.2, speed(r.st(0)));
        }
        return 1;
    }

    // =================================================================== movement

    static void drive(LocalPlayer p) {
        Run r = of(p.getId());
        if (r == null) return;
        double t = scriptTime(r.age + 1);
        Vec3 to = r.s.player(t);
        p.setPos(to.x, to.y, to.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        float yaw = yaw(r.s.facing(t));
        p.setYRot(yaw);
        p.yBodyRot = yaw;
        p.yHeadRot = yaw;
    }

    // =================================================================== the timeline

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        for (Run r : RUNS) {
            r.age++;
            double now = r.st(0);
            if (level != null) {
                Entity v = level.getEntity(r.victim);
                if (v != null && now <= KILL) {
                    Vec3 m = r.s.victim(now);
                    v.setPos(m.x, m.y, m.z);
                    v.setDeltaMovement(Vec3.ZERO);
                    float yaw = yaw(r.s.f) + 180;
                    v.setYRot(yaw);
                    if (v instanceof LivingEntity le) {
                        le.yBodyRot = yaw;
                        le.yHeadRot = yaw;
                    }
                }
                if (!slow(r.last) && slow(now) && r.last >= 0) play(r, r.victimCenter(now), ModSounds.SLOWMO.get(), 1.4f, 1.0f, now);
                for (int w = 0; w < WEAPONS; w++) {
                    // a new weapon seeps out of the gloves...
                    if (r.last < INTROS[w] && now >= INTROS[w]) {
                        play(r, r.s.player(now), ModSounds.WEAPON_SWAP.get(), 1.2f, 1.0f, now);
                        mist(level, hand(r, now, true), 10);
                        if (pair(w)) mist(level, hand(r, now, false), 10);
                    }
                    // ...and the spent one is flung away
                    int toss = tossAt(w);
                    if (r.last < toss && now >= toss && w != DURANDAL) {
                        Vec3 back = r.s.facing(now).scale(-0.12).add(0, 0.18, 0);
                        r.tossed.add(new Toss(w, false, hand(r, now, true), back.add(r.s.right(now).scale(0.08))));
                        if (pair(w)) r.tossed.add(new Toss(w, true, hand(r, now, false), back.add(r.s.right(now).scale(-0.08))));
                    }
                }
                for (FuriosoScript.Hit h : HITS) if (h.time() > r.last && h.time() <= now) hit(r, h, level, mc);
                for (Toss tz : r.tossed) {
                    Vec3 at = tz.at(0);
                    level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 0, 0.02, 0);
                    if (tz.age > 6) mist(level, at, 3);
                }
            }
            r.tossed.removeIf(tz -> ++tz.age > 10);
            r.pulse = Math.max(0, r.pulse - 0.15f);
            r.last = now;
        }
        RUNS.removeIf(r -> r.age > REAL_TOTAL + 4);
        gradePrev = grade;
        Run me = mine();
        float target = me != null && slow(me.st(0)) ? 1 : 0;
        grade += (target - grade) * 0.25f;
    }

    /** The Black Silence's mist: weapons come out of it and go back into it. */
    static void mist(ClientLevel level, Vec3 at, int n) {
        for (int i = 0; i < n; i++) {
            level.addParticle(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE, at.x + R.nextGaussian() * 0.12, at.y + R.nextGaussian() * 0.12,
                    at.z + R.nextGaussian() * 0.12, R.nextGaussian() * 0.03, 0.02 + R.nextDouble() * 0.03, R.nextGaussian() * 0.03);
        }
    }

    static void play(Run r, Vec3 at, SoundEvent s, float volume, float pitch, double now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        // inside slow motion every sound drops in pitch
        float k = slow(now) ? 0.62f : 1f;
        level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch * k, false);
    }

    static void hit(Run r, FuriosoScript.Hit h, ClientLevel level, Minecraft mc) {
        boolean mine = mc.player != null && mc.player.getId() == r.caster;
        double t = h.time();
        Vec3 vc = r.victimCenter(t);
        Vec3 f = r.s.f, side = r.s.r;
        Entity v = level.getEntity(r.victim);
        double dist = mc.player == null ? 99 : mc.player.position().distanceTo(vc);
        r.pulse = 1;
        switch (h.weapon()) {
            case PISTOLS -> {
                Vec3 from = hand(r, t, h.right()).add(f.scale(0.5));
                ArcFx.addBeam(from, vc.add(side.scale(R.nextGaussian() * 0.15)).add(0, R.nextGaussian() * 0.15, 0), 4, 0.08f, 1.0f, 0.85f, 0.5f, false);
                RingFx.flash(from, 0.4f, 3, 1.0f, 0.8f, 0.4f, 1.0f);
                ArsenalClient.impact(level, vc, true, 1.0f, 0.85f, 0.5f);
                play(r, from, ModSounds.REVOLVER.get(), 1.4f, 1.1f, t);
                CameraShake.addAt(dist, 0.15f, 30);
            }
            case LANCE -> {
                Vec3 from = r.s.player(t).add(0, 1.3, 0);
                ArcFx.addBeam(from, vc.add(f.scale(h.hard() ? 3 : 1.5)), 8, 0.25f, 0.85f, 0.9f, 1.0f, false);
                RingFx.add(vc, f, 0.2f, h.hard() ? 2.6f : 1.6f, 8, 0.85f, 0.9f, 1.0f, 1.0f);
                ArsenalClient.blood(level, vc.add(f.scale(0.5)), f.scale(8), v);
                play(r, vc, ModSounds.SPEAR.get(), 1.8f, h.hard() ? 0.9f : 1.1f, t);
                CameraShake.addAt(dist, h.hard() ? 0.5f : 0.25f, 40);
                if (mine && h.hard()) ImpactFrame.trigger(ImpactFrame.WHITE, 110);
            }
            case HAMMER, AXE, MACE, GREATSWORD -> {
                boolean big = h.weapon() == HAMMER || h.weapon() == GREATSWORD;
                Vec3 feet = r.s.victim(t);
                RingFx.flash(vc, big ? 2.4f : 1.4f, 6, 1.0f, 0.85f, 0.55f, 1.0f);
                if (h.weapon() != MACE) RingFx.add(feet.add(0, 0.1, 0), UP, 0.4f, big ? 7 : 3.5f, 10, 0.9f, 0.75f, 0.55f, 0.9f);
                else RingFx.add(vc, UP, 0.3f, 3f, 10, 1.0f, 0.85f, 0.55f, 0.9f);
                Shockwaves.add(vc, big ? 10 : 5, 600, big ? 2.0f : 1.2f);
                for (int i = 0; i < (big ? 14 : 6); i++) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, vc.x + R.nextGaussian(), feet.y + 0.2, vc.z + R.nextGaussian(), R.nextGaussian() * 0.1, 0.05, R.nextGaussian() * 0.1);
                if (h.weapon() == AXE) ArcFx.addColoredSlash(vc, f.add(0, -0.8, 0).normalize(), 80, 1.0f, 0.4f, 0.2f);
                ArsenalClient.blood(level, vc, f.scale(6).add(0, h.weapon() == MACE ? 8 : 3, 0), v);
                play(r, vc, h.weapon() == AXE ? ModSounds.AXE_HIT.get() : ModSounds.BLUNT.get(), big ? 2.5f : 1.8f, big ? 0.8f : 1.0f, t);
                CameraShake.addAt(dist, big ? 0.9f : 0.5f, 60);
                if (mine && h.hard()) {
                    ImpactFrame.trigger(h.weapon() == HAMMER ? ImpactFrame.RED : ImpactFrame.WHITE, big ? 160 : 110);
                    if (big) ScreenFlash.flash(0.25f);
                }
            }
            case KATANA -> {
                // the draw cut: everything goes to ink for a moment
                ArsenalClient.inkFrame = 0;
                ArsenalClient.inkSeed = R.nextFloat() * 100;
                ArcFx.addColoredSlash(vc, side.scale(-1).add(f.scale(0.3)).normalize(), 5, 1.0f, 1.0f, 1.0f);
                ArsenalClient.blood(level, vc, side.scale(-6), v);
                play(r, vc, ModSounds.IAI_SLASHES.get(), 2.0f, 1.1f, t);
                CameraShake.addAt(dist, 0.5f, 50);
                if (mine) ImpactFrame.trigger(ImpactFrame.WHITE, 140);
            }
            case GAUNTLETS, KNIFE, SHORTSWORDS -> {
                boolean crystal = h.weapon() == SHORTSWORDS;
                int n = h.weapon() == GAUNTLETS ? 3 : 1;
                float rr = crystal ? 0.5f : 1.0f, gg = crystal ? 0.95f : 0.25f, bb = crystal ? 1.0f : 0.2f;
                float roll = (float) ((t * 47) % 160 - 80);
                Vec3 from = r.s.player(t).add(0, 1.2, 0);
                Vec3 dir = vc.subtract(from).normalize();
                for (int i = 0; i < n; i++) ArcFx.addColoredSlash(vc.add(side.scale((i - 1) * 0.22)), dir, roll + i * 6, rr, gg, bb);
                ArsenalClient.blood(level, vc, dir.scale(5), v);
                play(r, vc, h.weapon() == GAUNTLETS ? ModSounds.CLAW.get() : ModSounds.SLICE.get(), 1.4f, 0.9f + R.nextFloat() * 0.3f, t);
                CameraShake.addAt(dist, h.hard() ? 0.45f : 0.2f, 30);
                if (mine && h.hard()) ImpactFrame.trigger(ImpactFrame.CYAN, 120);
            }
            case SHOTGUN -> {
                Vec3 from = hand(r, t, true).add(f.scale(0.8));
                for (int i = 0; i < 10; i++) ArcFx.addBeam(from, vc.add(f.scale(1.5)).add(side.scale(R.nextGaussian() * 0.7)).add(0, R.nextGaussian() * 0.6, 0), 5, 0.07f, 1.0f, 0.8f, 0.4f, false);
                RingFx.flash(from, 1.5f, 5, 1.0f, 0.7f, 0.3f, 1.0f);
                RingFx.add(from, f, 0.2f, 3.0f, 7, 1.0f, 0.7f, 0.3f, 1.0f);
                Shockwaves.add(vc, 7, 500, 1.6f);
                for (int i = 0; i < 3; i++) ArsenalClient.blood(level, vc, f.scale(10).add(side.scale(R.nextGaussian() * 3)), v);
                play(r, from, ModSounds.SHOTGUN.get(), 2.5f, 1.0f, t);
                CameraShake.addAt(dist, 0.8f, 50);
                if (mine) ImpactFrame.trigger(ImpactFrame.RED, 150);
            }
            case DURANDAL -> {
                if (t < KILL) {
                    ArcFx.addColoredSlash(vc, f.add(0, t < CUT2 ? -0.6 : 0.6, 0).normalize(), t < CUT2 ? 60 : -60, 0.85f, 0.92f, 1.0f);
                    ArsenalClient.blood(level, vc, f.scale(6), v);
                    play(r, vc, ModSounds.SLICE.get(), 1.8f, 0.8f, t);
                    CameraShake.addAt(dist, 0.35f, 40);
                } else {
                    // Furioso: one cut, and everything comes apart
                    ArsenalClient.inkFrame = 0;
                    ArsenalClient.inkSeed = R.nextFloat() * 100;
                    Shockwaves.add(vc, 14, 800, 2.4f);
                    RingFx.flash(vc, 4f, 8, 1, 1, 1, 1);
                    for (int i = 0; i < 5; i++) ArsenalClient.blood(level, vc, new Vec3(R.nextGaussian(), R.nextDouble(), R.nextGaussian()).scale(10), v);
                    play(r, vc, ModSounds.DURANDAL.get(), 3.0f, 1.0f, t);
                    play(r, vc, ModSounds.IAI_SLASHES.get(), 3.0f, 0.8f, t);
                    CameraShake.addAt(dist, 1.0f, 60);
                    if (mine) {
                        ImpactFrame.trigger(ImpactFrame.WHITE, 220);
                        ScreenFlash.flash(0.5f);
                    }
                }
            }
            default -> {}
        }
    }

    // =================================================================== the body (keyframed in Blockbench)

    /** Body yaw (degrees) from the animation at script time t. */
    static float bodyYaw(double t) {
        int w = FuriosoScript.weapon(t);
        float[] b = w < 0 ? null : FuriosoAnims.sample(w, FuriosoAnims.BODY, t - INTROS[w]);
        return b == null ? 0 : b[1];
    }

    /** Direction an arm points (world space) at script time t, from its keyframed rotation. */
    static Vec3 armDir(Run r, double t, boolean right) {
        int w = FuriosoScript.weapon(t);
        float[] a = w < 0 ? null : FuriosoAnims.sample(w, right ? FuriosoAnims.RIGHT_ARM : FuriosoAnims.LEFT_ARM, t - INTROS[w]);
        Vec3 f = r.s.facing(t), side = r.s.right(t);
        if (a == null) return f.scale(0.4).add(0, -1, 0).normalize();
        double pitch = Math.toRadians(-a[0]), yaw = Math.toRadians(a[1] + bodyYaw(t));
        Vec3 horiz = f.scale(Math.cos(yaw)).add(side.scale(Math.sin(yaw)));
        return UP.scale(-Math.cos(pitch)).add(horiz.scale(Math.sin(pitch))).normalize();
    }

    static Vec3 shoulder(Run r, double t, boolean right) {
        double by = Math.toRadians(bodyYaw(t));
        Vec3 f = r.s.facing(t), side = r.s.right(t);
        Vec3 out = side.scale(Math.cos(by)).subtract(f.scale(Math.sin(by)));
        return r.s.player(t).add(0, 1.38, 0).add(out.scale(right ? 0.37 : -0.37));
    }

    static Vec3 hand(Run r, double t, boolean right) {
        return shoulder(r, t, right).add(armDir(r, t, right).scale(0.72));
    }

    /** Third person: the keyframed pose (called at the end of setupAnim, before the sleeves copy the arms). */
    static void pose(HumanoidModel<?> m, LivingEntity e) {
        Run r = of(e.getId());
        if (r == null) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        double t = r.st(partial);
        int w = FuriosoScript.weapon(t);
        if (w < 0) return;
        keys(m, w, t - INTROS[w], true);
    }

    /** Applies weapon w's keyframes at `local` script ticks into its beat (the glove attacks reuse these too). */
    static void keys(HumanoidModel<?> m, int w, double local, boolean whole) {
        float[] body = FuriosoAnims.sample(w, FuriosoAnims.BODY, local);
        float by = body == null ? 0 : (float) Math.toRadians(body[1]);
        if (body != null) {
            m.body.xRot = (float) Math.toRadians(body[0]);
            m.body.yRot = by;
            // arms ride on the twisting torso, like vanilla's attack swing
            m.rightArm.z = Mth.sin(by) * 5.0F;
            m.rightArm.x = -Mth.cos(by) * 5.0F;
            m.leftArm.z = -Mth.sin(by) * 5.0F;
            m.leftArm.x = Mth.cos(by) * 5.0F;
        }
        apply(m.rightArm, FuriosoAnims.sample(w, FuriosoAnims.RIGHT_ARM, local), by);
        apply(m.leftArm, FuriosoAnims.sample(w, FuriosoAnims.LEFT_ARM, local), by);
        if (!whole) return;
        apply(m.rightLeg, FuriosoAnims.sample(w, FuriosoAnims.RIGHT_LEG, local), 0);
        apply(m.leftLeg, FuriosoAnims.sample(w, FuriosoAnims.LEFT_LEG, local), 0);
        float[] head = FuriosoAnims.sample(w, FuriosoAnims.HEAD, local);
        if (head != null) apply(m.head, head, 0);
    }

    static void apply(ModelPart part, @Nullable float[] rot, float extraYaw) {
        if (rot == null) return;
        part.xRot = (float) Math.toRadians(rot[0]);
        part.yRot = (float) Math.toRadians(rot[1]) + extraYaw;
        part.zRot = (float) Math.toRadians(rot[2]);
    }

    // =================================================================== rendering

    /** Glow passes: Durandal's edge and its final cut hanging in the air. */
    static void render(@Nullable VertexConsumer solid, @Nullable VertexConsumer glow, Matrix4f m, Vec3 cam, float partial) {
        if (glow == null) return;
        for (Run r : RUNS) {
            double t = r.st(partial);
            // the edge brightens while Durandal is drawn
            // along the blade exactly where the glove layer drew it this frame
            if (FuriosoScript.weapon(t) == DURANDAL && t < KILL && r.bladeBase != null) {
                float k = (float) Mth.clamp((t - DURANDAL_T) / (DRAW_END - DURANDAL_T), 0, 1);
                Vec3 tip = r.bladeTip.add(r.bladeTip.subtract(r.bladeBase).scale(0.08));
                FxRenderTypes.ribbon(glow, m, r.bladeBase.subtract(cam), tip.subtract(cam), 0.04f + 0.12f * k, 0.8f, 0.9f, 1.0f, 0.2f + 0.4f * k, 0.1f);
            }
            if (t >= KILL && t < KILL + 4) {
                float a = (float) (1 - (t - KILL) / 4);
                Vec3 c = r.victimCenter(KILL).subtract(cam);
                Vec3 d = r.s.f.add(0, 0.35, 0).normalize().scale(4);
                FxRenderTypes.ribbon(glow, m, c.subtract(d), c.add(d), 0.06f + 0.8f * (1 - a), 1, 1, 1, a, a);
                FxRenderTypes.ribbon(glow, m, c.subtract(d), c.add(d), 0.3f, 0.7f, 0.85f, 1.0f, 0.4f * a, 0.4f * a);
            }
        }
    }

    /** The spent weapons tumbling away. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || RUNS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (carrier.isEmpty()) carrier = new ItemStack(ModItems.BLACK_SILENCE.get());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = new PoseStack();
        boolean any = false;
        for (Run r : RUNS) {
            for (Toss tz : r.tossed) {
                BakedModel model = GloveLayer.model(tz.weapon);
                if (model == null) continue;
                float shrink = Mth.clamp(1 - (tz.age + partial - 5) / 5f, 0, 1);
                if (shrink <= 0) continue;
                Vec3 at = tz.at(partial);
                Vec3 p = at.subtract(cam);
                ps.pushPose();
                ps.translate(p.x, p.y, p.z);
                ps.mulPose(Axis.YP.rotationDegrees(-yaw(tz.vel.multiply(-1, 0, -1))));
                ps.mulPose(Axis.XP.rotationDegrees((tz.age + partial) * (tz.left ? -40 : 40)));
                ps.scale(shrink, shrink, shrink);
                int light = mc.level == null ? 0xF000F0 : LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
                mc.getItemRenderer().render(carrier, ItemDisplayContext.NONE, false, ps, buffers, light, OverlayTexture.NO_OVERLAY, model);
                ps.popPose();
                any = true;
            }
        }
        if (any) buffers.endBatch();
    }

    // =================================================================== camera

    /**
     * Fast cuts while things are fast, one steady shot whenever time slows down, and a close-up on every new weapon.
     */
    static final class Cam implements CinematicCamera.Director {
        final Run r;

        Cam(Run r) {
            this.r = r;
        }

        @Override
        public int duration() {
            return REAL_TOTAL;
        }

        @Override
        public void frame(float realT, CinematicCamera.Shot shot) {
            double t = scriptTime(realT);
            FuriosoScript s = r.s;
            Vec3 f = s.f, side = s.r;
            Vec3 P = s.player(t).add(0, 1.4, 0);
            Vec3 V = r.victimCenter(t);
            Vec3 mid = P.lerp(V, 0.5);
            shot.letterbox = 1;
            shot.fov = 70;
            if (t < DASH) {
                // the title card: low and close on you
                float k = CinematicCamera.ease((float) t, 0, DASH);
                shot.lookAt(P.add(f.scale(2.2 - 0.5 * k)).add(side.scale(0.7)).add(0, -0.35, 0), P.add(0, 0.1, 0));
                shot.roll = -6;
                shot.fov = 55;
            } else if (t < PISTOLS_T) {
                shot.lookAt(P.subtract(f.scale(3.2)).add(side.scale(1.4)).add(0, 0.6, 0), V);
                shot.fov = 80;
            } else if (introAt(t) >= 0) {
                // a new weapon: close-up on your hands as it comes out of the mist
                Vec3 hd = hand(r, t, true);
                double orbit = (t - introAt(t)) * 0.6;
                shot.lookAt(hd.add(side.scale(1.3 + 0.3 * Math.cos(orbit))).subtract(f.scale(0.9 + 0.3 * Math.sin(orbit))).add(0, 0.35, 0), hd.add(f.scale(0.3)));
                shot.fov = 50;
            } else if (t >= LAUNCH && t < SLAM + 3) {
                // up in the air: a low shot from the side looking up, kept above the ground so it never clips in
                Vec3 low = new Vec3(V.x, s.g.y + 1.2, V.z).add(side.scale(6)).subtract(f.scale(2));
                shot.lookAt(low, mid);
                shot.fov = 78;
            } else if (t >= DURANDAL_T && t < DRAW_END) {
                // behind you, low, as the blade comes out
                shot.lookAt(P.subtract(f.scale(3)).add(side.scale(1)).add(0, -0.3, 0), V);
                shot.fov = 55;
            } else if (t >= DRAW_END && t < KILL - 3) {
                shot.lookAt(mid.add(side.scale(6)).add(0, 0.6, 0), mid);
            } else if (t >= KILL - 3 && t < KILL + 1) {
                shot.lookAt(mid.add(side.scale(7)).add(0, 0.4, 0), mid);
                shot.fov = 60;
            } else if (t >= KILL + 1) {
                // your back to it; it hasn't realised yet
                shot.lookAt(P.add(f.scale(3.6)).add(side.scale(0.6)).add(0, 0.1, 0), P.lerp(V, 0.35));
                shot.fov = 60;
            } else if (slow(t)) {
                // a big hit: one steady, low shot
                shot.lookAt(mid.add(side.scale(3.5)).add(f.scale(0.8)).add(0, -0.3, 0), V);
                shot.fov = 66;
            } else {
                // between the big hits: alternate sides per weapon
                int beat = Math.max(0, FuriosoScript.weapon(t));
                double sgn = beat % 2 == 0 ? 1 : -1;
                shot.lookAt(mid.add(side.scale(4.5 * sgn)).subtract(f.scale(1)).add(0, 1, 0), mid);
            }
            CinematicCamera.returnToPlayer(shot, realT, REAL_TOTAL - 16, REAL_TOTAL);
        }
    }

    /** The intro beat t falls in (within its freeze-frame), or -1. */
    static int introAt(double t) {
        for (int beat : INTROS) if (t >= beat && t < beat + 1) return beat;
        return -1;
    }

    /** Which caption to show (index into CAPTIONS) and how far into it we are, or null. */
    @Nullable
    public static float[] caption(Run r, float partial) {
        double t = r.st(partial);
        for (int i = 0; i < INTROS.length; i++) {
            if (t >= INTROS[i] && t < INTROS[i] + 4) return new float[]{i, (float) ((t - INTROS[i]) / 4)};
        }
        return null;
    }

    static void clear() {
        RUNS.clear();
    }
}
