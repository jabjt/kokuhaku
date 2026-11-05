package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Developer testing only (KOKUHAKU_ENDINGS=1): every way out of the room, one after another, without closing the game. */
public class EndingsScreen extends Screen {
    public static final boolean ON = "1".equals(System.getenv("KOKUHAKU_ENDINGS"));
    private static final String[] HER = {"CHEERFUL", "SHY", "COLD", "GOSSIP", "POLITE", "SPORTY"};
    private static final int[] RUNS = {0, 1, 4};
    private static final long SILENCE_MS = 40_000;
    private static int her = 0, runs = 0;

    public EndingsScreen() {
        super(Text.empty());
    }

    private void row(int y, String label, net.minecraft.client.gui.widget.ButtonWidget.PressAction press) {
        Text text = KokuhakuClient.menuText(label);
        addDrawableChild(new MenuButton(width / 2 - textRenderer.getWidth(text) / 2, y, 300, 16, text, press));
    }

    private void open(String text, boolean love, boolean silence) {
        JumpscareScreen.gallery = true;
        JumpscareScreen.forceLove = love;
        JumpscareScreen.stareOverride = silence ? SILENCE_MS : 0;
        JumpscareScreen.runsOverride = RUNS[runs];
        client.setScreen(new JumpscareScreen(text));
    }

    @Override
    protected void init() {
        client.options.hudHidden = true;
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        String name = HER[her];
        row(34, "Her: " + name + " (click to change)", b -> {
            her = (her + 1) % HER.length;
            clearAndInit();
        });
        row(52, "Runs you've played: " + RUNS[runs] + " (click to change)", b -> {
            runs = (runs + 1) % RUNS.length;
            clearAndInit();
        });
        int y = 84, step = 19;
        row(y, "The bat: tell her you don't love her", b -> open(name, false, false));
        row(y + step, "Begging: try to win her over, then keep pleading", b -> open(name, false, false));
        row(y + step * 2, "Silence: say nothing at all (40 seconds here)", b -> open(name, false, true));
        row(y + step * 3, "Staying: tell her you'll stay", b -> open(name, false, false));
        row(y + step * 4, "Let go: the one in a hundred, always on here", b -> open(name, true, false));
        row(y + step * 5, "Three strikes: she's done talking", b -> open("FINAL:" + name, false, false));
        row(y + step * 6, "Confession: you named her", b -> open(JumpscareScreen.UNMASK + name + "\nYou named her.", false, false));
        row(y + step * 7, "Hit by a car", b -> open("ROAD car", false, false));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF000000);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("Every ending"), width / 2, 14, 0xFFE8D0D4);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("Click skips the wake-up. Esc comes back here."), width / 2, height - 16, 0xFF8C7C80);
        super.render(context, mouseX, mouseY, delta);
    }
}
