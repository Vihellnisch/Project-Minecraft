package com.slop.projectminecraft.client.arsenal;

import static com.slop.projectminecraft.arsenal.FuriosoScript.*;

import com.mojang.blaze3d.platform.InputConstants;
import com.slop.projectminecraft.arsenal.FuriosoScript;
import com.slop.projectminecraft.arsenal.GloveServer;
import com.slop.projectminecraft.arsenal.Gloves;
import com.slop.projectminecraft.client.CameraShake;
import com.slop.projectminecraft.client.juice.ImpactFrame;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.RingFx;
import com.slop.projectminecraft.network.ArsenalActionPayload;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModSounds;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client side of the worn Black Silence: the wheel key, the attack click, swings, hit effects and Furioso progress. */
public final class GloveClient {
    private GloveClient() {}

    public static final KeyMapping WHEEL_KEY = new KeyMapping("key.projectminecraft.black_silence", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.projectminecraft");

    private static final RandomSource R = RandomSource.create();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Each player's weapon on the wheel (for drawing it in their hands). */
    private static final Int2IntOpenHashMap SELECTED = new Int2IntOpenHashMap();
    /** Our own progress toward Furioso. */
    static int mask, target = -1;
    static boolean random;
    /** Ticks since our weapon last changed (the workshop caption), or -1. */
    static int swapAge = -1;
    private static final long[] READY = new long[WEAPONS];

    record Swing(int weapon, boolean right, long start, int length) {}

    private static final Int2ObjectOpenHashMap<Swing> SWINGS = new Int2ObjectOpenHashMap<>();
    /** Each player's last gunshot (which hand, when). */
    private static final Int2ObjectOpenHashMap<Swing> SHOTS = new Int2ObjectOpenHashMap<>();
    static final int AIM_TICKS = 30, KICK_TICKS = 5;

    /** Ticks since that player last fired, or a large number. */
    static float sinceShot(int player, float partial) {
        Swing s = SHOTS.get(player);
        Minecraft mc = Minecraft.getInstance();
        return s == null || mc.level == null ? 999 : mc.level.getGameTime() - s.start + partial;
    }

    /** Recoil on one hand: 1 right after its shot, easing back to 0 (both hands for the shotgun). */
    static float recoil(int player, boolean right, float partial) {
        Swing s = SHOTS.get(player);
        if (s == null || (s.right != right && s.weapon != SHOTGUN)) return 0;
        float t = sinceShot(player, partial) / KICK_TICKS;
        return t >= 1 ? 0 : (1 - t) * (1 - t);
    }

    static {
        SELECTED.defaultReturnValue(PISTOLS);
    }

    public static int selected(int player) {
        return SELECTED.get(player);
    }

    static int mine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? PISTOLS : selected(mc.player.getId());
    }

    /** How much of weapon w's cooldown is left, 1 just after an attack down to 0 when it's ready. */
    static float cooldown(int w, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        return (float) Math.max(0, Math.min(1, (READY[w] - mc.level.getGameTime() - partial) / GloveServer.COOLDOWN[w]));
    }

    public static boolean ready() {
        return mask == GloveServer.ALL && target >= 0;
    }

    static void select(int w) {
        PacketDistributor.sendToServer(new ArsenalActionPayload(ArsenalActionPayload.GLOVE_SELECT, 0, 0, 0, new int[]{w}, new float[0]));
    }

    // =================================================================== input

    static void tick(Minecraft mc) {
        if (swapAge >= 0 && ++swapAge > 40) swapAge = -1;
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        SWINGS.int2ObjectEntrySet().removeIf(e -> now > e.getValue().start + e.getValue().length + 2);
        while (WHEEL_KEY.consumeClick()) {} // the key is read held/released below
        // tap: the next weapon in Roland's order; hold: the wheel (opening a screen releases every key, so this resets)
        boolean usable = mc.screen == null && mc.player != null && Gloves.worn(mc.player) && !FuriosoClient.active(mc.player.getId())
                && !holdingSlopStaff(mc.player);
        if (usable && WHEEL_KEY.isDown()) {
            if (++wheelHeld == HOLD_TICKS) mc.setScreen(new GloveWheel());
        } else {
            if (usable && wheelHeld > 0 && wheelHeld < HOLD_TICKS) select((mine() + 1) % WEAPONS);
            wheelHeld = 0;
        }
    }

    /** Random Slop's Frieren staff shares the wheel key: its wheel wins while the staff is in hand. */
    private static final net.minecraft.resources.ResourceLocation SLOP_STAFF = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("effectslop", "frieren_staff");

    static boolean holdingSlopStaff(net.minecraft.world.entity.player.Player p) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).equals(SLOP_STAFF)
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getOffhandItem().getItem()).equals(SLOP_STAFF);
    }

    /** Ticks the wheel key has been held; holding it this long opens the wheel instead of cycling. */
    private static int wheelHeld;
    static final int HOLD_TICKS = 5;

    /** The attack click with an empty hand and the gloves on: true when we took it. Blocks still get mined normally. */
    static boolean onAttack(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !Gloves.armed(p)) return false;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return false;
        if (FuriosoClient.active(p.getId())) return true;
        int w = mine();
        long now = p.level().getGameTime();
        if (now < READY[w]) return true;
        READY[w] = now + GloveServer.COOLDOWN[w];
        PacketDistributor.sendToServer(ArsenalActionPayload.of(ArsenalActionPayload.GLOVE_ATTACK));
        return true;
    }

    // =================================================================== from the server

    static void state(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        int player = p.i(0), w = p.i(1);
        int before = SELECTED.get(player);
        SELECTED.put(player, w);
        Entity e = mc.level == null ? null : mc.level.getEntity(player);
        if (w != before && e != null) FuriosoClient.mist(mc.level, e.position().add(0, 1.0, 0), 10);
        if (mc.player == null || player != mc.player.getId()) return;
        boolean wasReady = ready();
        mask = p.i(2);
        target = p.i(3);
        random = p.i(4) == 1;
        if (w != before || p.i(5) == 1) swapAge = 0;
        if (!wasReady && ready()) mc.player.playSound(ModSounds.FURIOSO_STING.get(), 0.8f, 1.4f);
    }

    static void attack(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        int player = p.i(0), w = p.i(1);
        boolean right = p.i(2) == 1;
        SELECTED.put(player, w);
        boolean mine = mc.player != null && mc.player.getId() == player;
        if (GloveLayer.style(w) == GloveLayer.GUN) {
            // guns don't swing: they fire and kick
            SHOTS.put(player, new Swing(w, right, level.getGameTime(), 0));
        } else {
            SWINGS.put(player, new Swing(w, right, level.getGameTime(), Math.max(5, Math.min(10, GloveServer.COOLDOWN[w]))));
            if (mine) mc.player.swing(right ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, false);
        }

        Vec3 from = p.a(), to = p.b();
        Vec3 dir = to.subtract(from).lengthSqr() < 1e-4 ? UP : to.subtract(from).normalize();
        Vec3 flat = new Vec3(dir.x, 0, dir.z).lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 side = flat.cross(UP).normalize();
        int victims = Math.max(0, p.ints().length - 3);
        Entity first = victims > 0 ? level.getEntity(p.i(3)) : null;
        Vec3 at = first != null ? first.getBoundingBox().getCenter() : to;
        double dist = mc.player == null ? 99 : mc.player.position().distanceTo(from);
        if (mine && victims > 0) {
            ArsenalClient.hitmarker = 6;
            ArsenalClient.hitmarkerKind = 0;
        }
        switch (w) {
            case PISTOLS -> {
                ArcFx.addBeam(from, to, 4, 0.08f, 1.0f, 0.85f, 0.5f, false);
                RingFx.flash(from, 0.4f, 3, 1.0f, 0.8f, 0.4f, 1.0f);
                if (first != null) ArsenalClient.impact(level, to, true, 1.0f, 0.85f, 0.5f);
                sound(level, from, ModSounds.REVOLVER.get(), 1.2f, 1.1f);
                CameraShake.addAt(dist, 0.1f, 20);
            }
            case SHOTGUN -> {
                for (int i = 0; i < 8; i++) ArcFx.addBeam(from, to.add(side.scale(R.nextGaussian() * 0.9)).add(0, R.nextGaussian() * 0.9, 0), 5, 0.07f, 1.0f, 0.8f, 0.4f, false);
                RingFx.flash(from, 1.2f, 5, 1.0f, 0.7f, 0.3f, 1.0f);
                RingFx.add(from, dir, 0.2f, 2.0f, 7, 1.0f, 0.7f, 0.3f, 1.0f);
                sound(level, from, ModSounds.SHOTGUN.get(), 2.0f, 1.0f);
                CameraShake.addAt(dist, 0.5f, 40);
                if (mine && victims > 0) ImpactFrame.trigger(ImpactFrame.RED, 90);
            }
            case LANCE -> {
                ArcFx.addBeam(from, to, 8, 0.2f, 0.85f, 0.9f, 1.0f, false);
                RingFx.add(at, dir, 0.2f, 1.6f, 8, 0.85f, 0.9f, 1.0f, 1.0f);
                sound(level, from, ModSounds.SPEAR.get(), 1.6f, 1.1f);
                CameraShake.addAt(dist, 0.25f, 30);
            }
            case KATANA -> {
                ArcFx.addColoredSlash(from.add(to).scale(0.5), side.scale(-1).add(flat.scale(0.3)).normalize(), 5, 1.0f, 1.0f, 1.0f);
                sound(level, to, ModSounds.IAI_SLASHES.get(), 1.6f, 1.2f);
                if (mine && victims > 0) {
                    ArsenalClient.inkFrame = 0;
                    ArsenalClient.inkSeed = R.nextFloat() * 100;
                    ImpactFrame.trigger(ImpactFrame.WHITE, 100);
                }
                CameraShake.addAt(dist, 0.35f, 40);
            }
            case DURANDAL -> {
                ArcFx.addColoredSlash(from.add(dir.scale(2)), dir.add(0, -0.6, 0).normalize(), R.nextBoolean() ? 60 : -60, 0.85f, 0.92f, 1.0f);
                sound(level, from, ModSounds.SLICE.get(), 1.6f, 0.8f);
                CameraShake.addAt(dist, 0.3f, 30);
            }
            case HAMMER, GREATSWORD -> {
                Vec3 ground = new Vec3(to.x, Math.floor(from.y - 1.2) + 0.1, to.z);
                boolean great = w == GREATSWORD;
                RingFx.add(ground, UP, 0.4f, great ? 5 : 3.5f, 10, 0.9f, 0.75f, 0.55f, 0.9f);
                Shockwaves.add(ground, great ? 7 : 5, 500, great ? 1.4f : 1.0f);
                for (int i = 0; i < (great ? 12 : 6); i++) level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x + R.nextGaussian(), ground.y, ground.z + R.nextGaussian(), R.nextGaussian() * 0.08, 0.04, R.nextGaussian() * 0.08);
                sound(level, ground, ModSounds.BLUNT.get(), great ? 2.2f : 1.6f, great ? 0.8f : 1.0f);
                CameraShake.addAt(dist, great ? 0.7f : 0.45f, 50);
                if (mine && victims > 0 && great) ImpactFrame.trigger(ImpactFrame.WHITE, 110);
            }
            case AXE -> {
                ArcFx.addColoredSlash(from.add(flat.scale(1.8)), flat.add(0, -0.3, 0).normalize(), 80, 1.0f, 0.4f, 0.2f);
                sound(level, from, ModSounds.AXE_HIT.get(), 1.4f, 1.0f);
                CameraShake.addAt(dist, 0.3f, 30);
            }
            case MACE -> {
                RingFx.add(at, UP, 0.3f, 2.2f, 10, 1.0f, 0.85f, 0.55f, 0.9f);
                sound(level, at, ModSounds.BLUNT.get(), 1.4f, 1.1f);
                CameraShake.addAt(dist, 0.35f, 30);
            }
            case GAUNTLETS, KNIFE, SHORTSWORDS -> {
                boolean crystal = w == SHORTSWORDS;
                float rr = crystal ? 0.5f : 1.0f, gg = crystal ? 0.95f : 0.25f, bb = crystal ? 1.0f : 0.2f;
                int n = crystal ? 2 : 1;
                Vec3 c = first != null ? at : from.add(dir.scale(1.6));
                for (int i = 0; i < n; i++) ArcFx.addColoredSlash(c.add(side.scale(i * 0.25)), dir, (R.nextFloat() - 0.5f) * 140, rr, gg, bb);
                sound(level, c, w == GAUNTLETS ? ModSounds.CLAW.get() : ModSounds.SLICE.get(), 1.1f, 0.9f + R.nextFloat() * 0.3f);
                CameraShake.addAt(dist, 0.12f, 20);
            }
            default -> {}
        }
        for (int i = 0; i < victims; i++) {
            Entity v = level.getEntity(p.i(3 + i));
            if (v != null) ArsenalClient.blood(level, v.getBoundingBox().getCenter(), dir.scale(5), v);
        }
    }

    static void sound(ClientLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch, false);
    }

    // =================================================================== third person

    /**
     * Guns: the shotgun is always held in two hands like a crossbow; pistols are raised to aim for a moment after a shot,
     * and the hand that fired kicks up. Everything else: a short slice of that weapon's Furioso keyframes, around its
     * hit, played over the swing.
     */
    static void pose(HumanoidModel<?> m, LivingEntity e) {
        if (FuriosoClient.active(e.getId()) || !Gloves.armed(e)) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        int w = selected(e.getId());
        if (GloveLayer.style(w) == GloveLayer.GUN) {
            float since = sinceShot(e.getId(), partial);
            if (w == SHOTGUN) AnimationUtils.animateCrossbowHold(m.rightArm, m.leftArm, m.head, true);
            else if (since < AIM_TICKS) {
                aim(m.rightArm, m.head, 0);
                aim(m.leftArm, m.head, 0);
            } else return;
            for (boolean right : new boolean[]{true, false}) (right ? m.rightArm : m.leftArm).xRot -= 0.45f * recoil(e.getId(), right, partial);
            return;
        }
        Swing s = SWINGS.get(e.getId());
        if (s == null) return;
        double k = (e.level().getGameTime() - s.start + partial) / s.length;
        if (k < 0 || k > 1) return;
        double hit = hitTime(s.weapon, s.right) - INTROS[s.weapon];
        FuriosoClient.keys(m, s.weapon, hit - 3 + k * 5, false);
    }

    /** An arm pointed where the head looks. */
    static void aim(ModelPart arm, ModelPart head, float yaw) {
        arm.xRot = head.xRot - (float) Math.PI / 2;
        arm.yRot = head.yRot + yaw;
        arm.zRot = 0;
    }

    static double hitTime(int w, boolean right) {
        double any = -1;
        for (FuriosoScript.Hit h : HITS) {
            if (h.weapon() != w) continue;
            if (h.right() == right) return h.time();
            if (any < 0) any = h.time();
        }
        return any < 0 ? INTROS[w] + 4 : any;
    }

    static void clear() {
        SELECTED.clear();
        SWINGS.clear();
        SHOTS.clear();
        mask = 0;
        target = -1;
        random = false;
        swapAge = -1;
    }
}
