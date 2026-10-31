package com.slop.projectminecraft.mixin;

import com.slop.projectminecraft.client.CinematicCamera;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No looking around during cutscenes. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void projectminecraft$lock(double movementTime, CallbackInfo ci) {
        if (CinematicCamera.locksInput()) ci.cancel();
    }
}
