package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ClientRun;
import com.kokuhaku.client.EndingsBook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

/**
 * Black screen with the result, then back to the bedroom menu.
 * TODO(cutscene phase): the day-31 bat ending and the jumpscare replace the losing version of this.
 */
public class EndScreen extends Screen {
    private static final long SHOW_MS = 8000, LINE_MS = 2500;

    private final boolean won;
    private final String[] lines;
    private final long opened = System.currentTimeMillis();
    private boolean leaving = false;

    public EndScreen(boolean won, String text) {
        super(Text.literal(won ? "Found her" : "Too late"));
        this.won = won;
        this.lines = text.split("\n");
        EndingsBook.saw(text);
        com.kokuhaku.client.Profile.finished(won);
        if (text.startsWith(com.kokuhaku.game.Endings.KEPT_HER_PROMISE)) com.kokuhaku.client.Profile.wonForHer();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (!leaving && System.currentTimeMillis() - opened > Math.max(SHOW_MS, lines.length * LINE_MS)) {
            leaving = true;
            if (ClientRun.reel == null) {
                leave();
                return;
            }
            String reel = ClientRun.reel;
            ClientRun.reel = null;
            client.setScreen(new ReelScreen(reel, this::leave));
        }
    }

    private void leave() {
        ClientRun.reset();
        if (client.world != null) client.world.disconnect();
        client.disconnect(new MessageScreen(Text.literal("...")));
        client.setScreen(new TitleScreen()); // becomes "..." then the bedroom
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float fade = Math.min(1f, (System.currentTimeMillis() - opened) / 1500f);
        context.fill(0, 0, width, height, ((int) (fade * 255) << 24));
        int y = height / 2 - lines.length * 7;
        for (int i = 0; i < lines.length; i++) {
            // The lines come up one at a time, so a secret ending can land.
            if ((System.currentTimeMillis() - opened) < i * LINE_MS / 2) break;
            int color = i == 0 ? (won ? 0xFFE8E2E4 : 0xFFD01C30) : 0xFF9A9AA2;
            context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(lines[i]), width / 2, y, color);
            y += 14;
        }
    }
}
