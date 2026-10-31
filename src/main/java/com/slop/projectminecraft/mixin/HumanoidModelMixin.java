package com.slop.projectminecraft.mixin;

import com.slop.projectminecraft.client.arsenal.ArsenalClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arsenal poses (web arm, axe throw, sheathing, the haymaker wind-up, Chimera morphs) on top of vanilla's. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void projectminecraft$arsenalPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        ArsenalClient.pose((HumanoidModel<?>) (Object) this, entity);
    }
}
