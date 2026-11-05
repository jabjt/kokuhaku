package com.kokuhaku.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create's helper for Sodium is written for Sodium 0.6 and crashes on the first
 * frame under 0.5, which is the version everything else here needs. All it does
 * is keep a few of Create's animated textures ticking, so it goes.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.compat.sodium.SodiumCompat", remap = false)
public class CreateSodiumMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kokuhaku$notThisSodium(CallbackInfo ci) {
        ci.cancel();
    }
}
