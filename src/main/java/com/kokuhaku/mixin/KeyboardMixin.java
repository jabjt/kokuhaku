package com.kokuhaku.mixin;

import com.kokuhaku.screen.QuitScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // No debug screen, no hiding the HUD, no changing camera.
        if (key == GLFW.GLFW_KEY_F3 || key == GLFW.GLFW_KEY_F1 || key == GLFW.GLFW_KEY_F5) {
            ci.cancel();
            return;
        }
        // Esc while playing doesn't pause. It asks if you want to leave.
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS
                && client.currentScreen == null && client.world != null) {
            client.setScreen(new QuitScreen(null));
            ci.cancel();
        }
    }
}
