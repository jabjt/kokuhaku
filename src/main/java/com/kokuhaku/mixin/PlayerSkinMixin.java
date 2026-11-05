package com.kokuhaku.mixin;

import com.kokuhaku.client.StudentSkins;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** You always look like You, whatever account is signed in, except on your profile. Your friends keep their own skins. */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$skin(CallbackInfoReturnable<Identifier> cir) {
        if ((Object) this instanceof ClientPlayerEntity && !StudentSkins.ownSkin) cir.setReturnValue(StudentSkins.YOU);
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$slim(CallbackInfoReturnable<String> cir) {
        if ((Object) this instanceof ClientPlayerEntity && !StudentSkins.ownSkin) cir.setReturnValue("slim");
    }
}
