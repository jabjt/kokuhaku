package com.kokuhaku.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nobody jumps. Swimming up is a different thing, and Space still does that. */
@Mixin(PlayerEntity.class)
public class NoJumpMixin {
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noJump(CallbackInfo ci) {
        ci.cancel();
    }
}
