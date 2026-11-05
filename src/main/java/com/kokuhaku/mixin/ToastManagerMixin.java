package com.kokuhaku.mixin;

import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nothing pops up in the corner to tell you how to play Minecraft. */
@Mixin(ToastManager.class)
public class ToastManagerMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$none(Toast toast, CallbackInfo ci) {
        ci.cancel();
    }
}
