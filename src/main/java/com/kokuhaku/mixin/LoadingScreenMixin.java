package com.kokuhaku.mixin;

import com.kokuhaku.screen.DotsScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla loading/saving screen just shows "..." on black. Their logic still runs. */
@Mixin({LevelLoadingScreen.class, DownloadingTerrainScreen.class, MessageScreen.class, ProgressScreen.class, ConnectScreen.class})
public class LoadingScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$dots(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        DotsScreen.paint(context, self.width, self.height);
        ci.cancel();
    }
}
