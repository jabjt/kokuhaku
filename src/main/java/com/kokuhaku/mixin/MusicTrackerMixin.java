package com.kokuhaku.mixin;

import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Shadow
    public abstract void stop();

    /** No music, ever: the menu and the town are both meant to be silent. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$silence(CallbackInfo ci) {
        stop();
        ci.cancel();
    }
}
