package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ClientRun;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** What Esc brings up. Saying no goes straight back to where you were. */
public class QuitScreen extends Screen {
    private final Screen parent;

    public QuitScreen(Screen parent) {
        super(KokuhakuClient.menuText("Are you sure you want to quit?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 40, y = height / 2 + 2;
        addDrawableChild(new MenuButton(x, y, 80, 18, KokuhakuClient.menuText("Quit"), b -> client.scheduleStop()));
        addDrawableChild(new MenuButton(x, y + 20, 80, 18, KokuhakuClient.menuText("Stay"), b -> close()));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC8000000);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f - 34, 0);
        context.getMatrices().scale(1.5f, 1.5f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFE8E2E4);
        context.getMatrices().pop();
        boolean host = client.isIntegratedServerRunning() && ClientRun.party > 1;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(host ? "This ends the game for your whole party." : "Nothing here is saved."),
                width / 2, height / 2 - 14, 0xFF8C7C80);
        super.render(context, mouseX, mouseY, delta);
    }
}
