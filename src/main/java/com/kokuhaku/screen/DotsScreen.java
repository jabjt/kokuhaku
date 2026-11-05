package com.kokuhaku.screen;

import com.kokuhaku.client.MenuScene;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Black screen with "..." on it. Shown instead of every vanilla loading
 * screen; this one also kicks off loading the menu world.
 */
public class DotsScreen extends Screen {
    private boolean requested = false;

    public DotsScreen() {
        super(Text.literal("..."));
    }

    @Override
    public void tick() {
        // Wait for the game's own resource loading to finish before starting a world.
        if (!requested && client.getOverlay() == null) {
            requested = true;
            client.send(() -> MenuScene.enter(client));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        paint(context, width, height);
    }

    /** Shared by every loading screen. */
    public static void paint(DrawContext context, int width, int height) {
        context.fill(0, 0, width, height, 1000, 0xFF000000);
        // Dots fade in one by one, then all fade out together.
        long t = System.currentTimeMillis() % 2400;
        int shown = (int) Math.min(3, t / 500);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            dots.append(i < shown ? '.' : ' ');
        }
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f - 6, 1000);
        context.getMatrices().scale(2f, 2f, 1f);
        var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        // Plain font: this also draws during startup, before the custom font has loaded.
        context.drawCenteredTextWithShadow(tr, Text.literal(dots.toString()), 0, 0, 0xFF8A8A92);
        context.getMatrices().pop();
    }
}
