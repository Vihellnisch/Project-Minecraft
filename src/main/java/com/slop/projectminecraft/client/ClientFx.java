package com.slop.projectminecraft.client;

import com.slop.projectminecraft.ProjectMinecraft;
import com.slop.projectminecraft.client.juice.HitStop;
import com.slop.projectminecraft.client.juice.Shockwaves;
import com.slop.projectminecraft.client.render.ArcFx;
import com.slop.projectminecraft.client.render.RingFx;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/** Ticks and draws the shared screen and world effects (shake, hit stop, arcs, rings, cutscenes, flashes). */
public final class ClientFx {
    private ClientFx() {}

    private static long clientTicks;
    static float lastFov = 70;

    /** Wrapping animation clock for the shaders, in seconds. */
    public static float seconds(float partial) {
        return ((clientTicks % 72000L) + partial) / 20.0f;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clientTicks++;
        CameraShake.tick();
        HitStop.tick(mc);
        ArcFx.tick();
        RingFx.tick();
        CinematicCamera.tick();
        ScreenFlash.tick();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            ArcFx.render(event);
            RingFx.render(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            Shockwaves.prepare(new Matrix4f(event.getProjectionMatrix()), new Matrix4f(event.getModelViewMatrix()), event.getCamera().getPosition(), lastFov);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        float[] o = CameraShake.offsets(clientTicks + partial, partial);
        if (o == null) return;
        event.setYaw(event.getYaw() + o[0]);
        event.setPitch(event.getPitch() + o[1]);
        event.setRoll(event.getRoll() + o[2]);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && CinematicCamera.fov() > 0) event.setFOV(Mth.clamp(CinematicCamera.fov(), 10.0, 150.0));
        lastFov = (float) event.getFOV();
    }

    // ------------------------------------------------------------------ cutscene controls

    @SubscribeEvent
    public static void onMovementInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        if (!CinematicCamera.locksInput()) return;
        var in = event.getInput();
        in.forwardImpulse = in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if (CinematicCamera.active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (CinematicCamera.locksInput() && !event.getName().getNamespace().equals(ProjectMinecraft.MODID)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.slop.projectminecraft.client.boss.BossClient.clear();
        CinematicCamera.stop();
        HitStop.clear();
        ArcFx.clear();
    }
}
