package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** After the ending: one line at a time, everything she did that you never saw. */
public class ReelScreen extends Screen {
    private static final long LINE_MS = 1900, STAYS_MS = 25_000;
    private static final int WIDEST = 400, LINE_HEIGHT = 11, GAP = 4;

    private final String[] lines;
    private final Runnable after;
    private final long opened = System.currentTimeMillis();
    private long skipped = 0;
    private int sounded = -1;
    private boolean done = false, photographed = false;

    public ReelScreen(String text, Runnable after) {
        super(KokuhakuClient.menuText("What She Did"));
        this.lines = text.split("\n", -1);
        this.after = after;
    }

    @Override
    protected void init() {
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private long allShownAt() {
        return opened + lines.length * LINE_MS - skipped;
    }

    private void advance() {
        long now = System.currentTimeMillis();
        if (now < allShownAt()) {
            skipped += allShownAt() - now;
        } else if (!done) {
            done = true;
            after.run();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        advance();
        return true;
    }

    @Override
    public void tick() {
        int showing = shown();
        if (showing > sounded && showing < lines.length && !lines[showing].isEmpty()) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1f, 0.35f));
        }
        sounded = Math.max(sounded, showing);
        if (com.kokuhaku.client.DevShots.ON && !photographed && showing >= lines.length) {
            photographed = true;
            com.kokuhaku.client.DevShots.take();
        }
        if (!done && System.currentTimeMillis() > allShownAt() + STAYS_MS) {
            done = true;
            after.run();
        }
    }

    private int shown() {
        return (int) ((System.currentTimeMillis() + skipped - opened) / LINE_MS);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF000000);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 14, 0);
        context.getMatrices().scale(1.8f, 1.8f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFD01C30);
        context.getMatrices().pop();
        int wide = Math.min(WIDEST, width - 32), left = (width - wide) / 2, y = 38;
        int showing = shown();
        List<List<OrderedText>> wrapped = new ArrayList<>();
        for (String line : lines) wrapped.add(textRenderer.wrapLines(Text.literal(line), wide));
        for (int i = 0; i <= showing && i < lines.length; i++) {
            int color = i == 0 ? 0xFFE8E2E4 : lines[i].startsWith("Day ") ? 0xFFB0A8AC : 0xFFE0283C;
            for (OrderedText part : wrapped.get(i)) {
                context.drawTextWithShadow(textRenderer, part, left, y, color);
                y += LINE_HEIGHT;
            }
            y += GAP;
        }
        if (showing >= lines.length) {
            context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("Click to go on"), width / 2, height - 16, 0xFF6C6C74);
        }
    }
}
