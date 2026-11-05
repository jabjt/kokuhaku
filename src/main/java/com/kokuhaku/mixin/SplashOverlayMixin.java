package com.kokuhaku.mixin;

import com.kokuhaku.screen.DotsScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paints "..." over the Mojang startup screen. Its loading logic still runs underneath. */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void kokuhaku$dots(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        DotsScreen.paint(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }
}
