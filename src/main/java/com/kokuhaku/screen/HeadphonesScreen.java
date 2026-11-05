package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.DevShots;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The first thing you see: a quiet word about headphones, fading up out of the black and back into it. */
public class HeadphonesScreen extends Screen {
    private static final Identifier ICON = new Identifier(KokuhakuClient.MOD_ID, "textures/gui/headphones.png");
    private static final long FADE_MS = 1300, HOLD_MS = 2200, ALL_MS = FADE_MS * 2 + HOLD_MS;
    private static final int ICON_SIZE = 256, ICON_DRAWN = 64;
    private static final Text WORDS = Text.literal("Headphones are recommended for this game");
    private static boolean seen = false;

    private final long opened = System.currentTimeMillis();
    private boolean photographed = false;

    public HeadphonesScreen() {
        super(Text.empty());
    }

    /** Once each time the game's opened: it isn't something anyone needs telling twice. */
    public static Screen first() {
        if (seen) return new KokuhakuTitleScreen();
        seen = true;
        return new HeadphonesScreen();
    }

    private void done() {
        client.setScreen(new KokuhakuTitleScreen());
    }

    @Override
    public void tick() {
        long since = System.currentTimeMillis() - opened;
        if (DevShots.ON && !photographed && since > FADE_MS + HOLD_MS / 2) {
            photographed = true;
            DevShots.take();
        }
        if (since > ALL_MS) done();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        done();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        done();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF000000);
        long since = System.currentTimeMillis() - opened;
        float alpha = Math.min(1f, Math.min(since, ALL_MS - since) / (float) FADE_MS);
        alpha = Math.max(0f, alpha * alpha * (3f - 2f * alpha));
        if (alpha <= 0.02f) return;
        int mid = width / 2, y = height / 2 - 34;
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(WORDS.getString()), mid, y, ((int) (alpha * 255) << 24) | 0xE8E2E4);
        int size = ICON_DRAWN;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        context.drawTexture(ICON, mid - size / 2, y + 18, size, size, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
