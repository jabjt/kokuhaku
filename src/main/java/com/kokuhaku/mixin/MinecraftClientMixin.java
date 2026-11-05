package com.kokuhaku.mixin;

import com.kokuhaku.client.ClientRun;
import com.kokuhaku.client.MenuScene;
import com.kokuhaku.client.Party;
import com.kokuhaku.client.WindowTitle;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import com.kokuhaku.screen.DotsScreen;
import com.kokuhaku.screen.KokuhakuTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    /** Screens that don't exist in this game: pause menu, chat, advancements, social. */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$blockScreens(Screen screen, CallbackInfo ci) {
        if (screen instanceof GameMenuScreen || screen instanceof ChatScreen
                || screen instanceof AdvancementsScreen || screen instanceof SocialInteractionsScreen) {
            ci.cancel();
            return;
        }
        // The inventory key opens your bag instead (the server sends its contents).
        if (screen instanceof InventoryScreen && ClientRun.active) {
            ClientPlayNetworking.send(Net.OPEN_BAG, PacketByteBufs.empty());
            ci.cancel();
        }
    }

    /**
     * Whenever the game would show the vanilla title screen: with no world
     * loaded, show "..." and load the bedroom; inside the bedroom, show ours.
     */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen kokuhaku$replaceTitleScreen(Screen screen) {
        if (screen instanceof AccessibilityOnboardingScreen) {
            // The first-launch "press Ctrl+B for the narrator" screen: skip it, for good.
            MinecraftClient client = (MinecraftClient) (Object) this;
            client.options.onboardAccessibility = false;
            client.options.write();
            return new DotsScreen();
        }
        if (screen instanceof DisconnectedScreen disconnected) {
            // Back to your own menu, with the reason shown there.
            Party.error = ((DisconnectedScreenAccessor) disconnected).kokuhaku$reason().getString();
            return new DotsScreen();
        }
        if (screen instanceof TitleScreen) {
            MinecraftClient client = (MinecraftClient) (Object) this;
            if (client.world == null || !MenuScene.isActive()) {
                return new DotsScreen();
            }
            return new KokuhakuTitleScreen();
        }
        return screen;
    }

    /** Scrambled from the very first frame. */
    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$title(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(WindowTitle.scrambled());
    }

    /** No pausing, ever, including when the window loses focus. */
    @Inject(method = "openPauseMenu", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noPauseMenu(boolean pauseOnly, CallbackInfo ci) {
        ci.cancel();
    }
}
