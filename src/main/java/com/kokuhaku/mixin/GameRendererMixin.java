package com.kokuhaku.mixin;

import com.kokuhaku.client.Cutscene;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The camera has no roll of its own, so a cutscene's sideways tilt goes in here. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void kokuhaku$cutsceneRoll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        float roll = Cutscene.roll();
        if (roll != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }
}