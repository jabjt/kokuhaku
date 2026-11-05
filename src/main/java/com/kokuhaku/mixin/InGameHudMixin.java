package com.kokuhaku.mixin;

import com.kokuhaku.client.ClientRun;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** You're a student, not an adventurer: no hearts, hunger, armour or XP bar during a run. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noStatusBars(DrawContext context, CallbackInfo ci) {
        if (ClientRun.active) ci.cancel();
    }

    /** The hotbar is replaced by two faint slots at the side of the screen (see RunHud). */
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        if (ClientRun.active) ci.cancel();
    }

    /** No crosshair, and with it no attack indicator: nothing on screen says this is a game. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noCrosshair(DrawContext context, CallbackInfo ci) {
        ci.cancel();
    }

    /** You're sitting on an invisible seat: no hearts for it, and no vanilla hint. */
    @Inject(method = "renderMountHealth", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noMountHealth(DrawContext context, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noMountHint(Text message, boolean tinted, CallbackInfo ci) {
        if (message.getContent() instanceof TranslatableTextContent content && content.getKey().equals("mount.onboard")) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$noXpBar(DrawContext context, int x, CallbackInfo ci) {
        if (ClientRun.active) ci.cancel();
    }
}
