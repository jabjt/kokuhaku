package com.kokuhaku.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * From pressing play until you wake up: the menu fades out and it stays black,
 * so nobody sees the world being got ready. The waking scene takes over from it.
 */
public class BlackoutScreen extends Screen {
    private static final long FADE_MS = 700, GIVE_UP_MS = 15_000;

    private final long opened = System.currentTimeMillis();
    private final long fade;
    private Runnable whenBlack;

    /** whenBlack: what to do once the picture's gone. Null if it's black straight away. */
    public BlackoutScreen(Runnable whenBlack) {
        super(Text.empty());
        this.whenBlack = whenBlack;
        this.fade = whenBlack == null ? 0 : FADE_MS;
    }

    @Override
    public void tick() {
        long since = System.currentTimeMillis() - opened;
        if (whenBlack != null && since >= fade) {
            Runnable then = whenBlack;
            whenBlack = null;
            then.run();
        }
        if (since > GIVE_UP_MS) close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float black = fade == 0 ? 1f : Math.min(1f, (System.currentTimeMillis() - opened) / (float) fade);
        context.fill(0, 0, width, height, (int) (black * 255) << 24);
    }
}
