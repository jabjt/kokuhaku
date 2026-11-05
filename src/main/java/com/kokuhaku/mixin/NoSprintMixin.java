package com.kokuhaku.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nobody runs in this game, however they try: the key, double-tapping forward, anything. */
@Mixin(LivingEntity.class)
public class NoSprintMixin {
    @ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
    private boolean kokuhaku$noSprint(boolean sprinting) {
        return sprinting && !((Object) this instanceof PlayerEntity);
    }
}
