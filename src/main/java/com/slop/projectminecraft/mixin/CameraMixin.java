package com.slop.projectminecraft.mixin;

import com.slop.projectminecraft.client.CinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cutscenes: put the camera wherever the running CinematicCamera director says. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void projectminecraft$cinematic(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        CinematicCamera.Shot shot = CinematicCamera.shot(partialTick);
        if (shot == null) return;
        this.detached = !shot.firstPerson; // so the player's own body is drawn (until the camera is back in their eyes)
        setRotation(shot.yaw, shot.pitch, shot.roll);
        setPosition(shot.pos);
    }
}
