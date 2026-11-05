package com.kokuhaku.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create won't run a timetable without someone sat at the controls. Here it's always staffed, both ways. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.entity.Train", remap = false)
public class TrainDriverMixin {
    @Inject(method = {"hasForwardConductor", "hasBackwardConductor"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void kokuhaku$alwaysStaffed(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
