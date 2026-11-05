package com.kokuhaku.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trep's Cars say every side is in charge of moving them, which suits a player
 * at the wheel. Ours are driven by the server, and a client that thinks it's in
 * charge throws away every position it's sent, so the cars never leave the spot
 * they appeared on. Named by string: the mod is only there at runtime.
 */
@Pseudo
@Mixin(targets = {"trep.cars.entity.custom.CarEntity", "trep.cars.entity.custom.CarV2Entity", "trep.cars.entity.custom.CarV3Entity"}, remap = false)
public class CarMovementMixin {
    @Inject(method = {"method_5787", "isLogicalSideForUpdatingMovement"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void kokuhaku$theServerDrives(CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).getWorld().isClient) cir.setReturnValue(false);
    }
}
