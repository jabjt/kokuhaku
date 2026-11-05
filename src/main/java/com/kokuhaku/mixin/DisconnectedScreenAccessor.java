package com.kokuhaku.mixin;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {
    @Accessor("reason")
    Text kokuhaku$reason();
}
