package com.slop.projectminecraft.mixin;

import com.slop.projectminecraft.client.PostFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Screen effects after the level + hand, but before glowing outlines are composited on top. */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V"))
    private void projectminecraft$postEffects(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PostFx.process(deltaTracker);
    }
}
