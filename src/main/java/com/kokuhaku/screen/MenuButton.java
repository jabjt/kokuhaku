package com.kokuhaku.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Fully transparent menu button: just the text, which turns red when hovered. */
public class MenuButton extends ButtonWidget {
    public MenuButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        int color;
        if (!active) {
            color = 0xFF4A4448;
        } else if (isSelected()) {
            color = 0xFFE0283C;
        } else {
            color = 0xFFD8D0D4;
        }
        int textY = getY() + (getHeight() - 9) / 2;
        // Hovered text nudges right a little.
        int textX = getX() + (isSelected() && active ? 4 : 0);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), textX, textY, color);
    }
}
