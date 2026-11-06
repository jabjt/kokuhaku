package com.kokuhaku.client;

import com.kokuhaku.screen.PhoneScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * A message arrives while your phone's away: a card drops in from the top of the screen
 * with who it's from and the start of it, the way a phone shows it on the lock screen,
 * and slides away again. So you know she's written without getting it out.
 */
public final class PhoneBanner {
    private static final long SLIDE_MS = 260, SHOWN_MS = 4200;
    private static final int WIDE = 200, HIGH = 30;
    private static final int CARD = 0xFF202C33, TEXT = 0xFFE9EDEF, META = 0xFF8696A0, GREEN = 0xFF00A884, HER = 0xFFF0A0A8;

    private static String from = "", line = "";
    private static long since = -1;

    private PhoneBanner() {
    }

    /** From the server: who, who in the group, what, whether it's from the dead, whether it's a photo. */
    public static void show(String chat, String sender, String body, boolean dead, boolean photo) {
        from = GhostGlyphs.filter(chat, chat.hashCode());
        String who = dead ? "Unknown: " : sender.isEmpty() ? "" : sender + ": ";
        String what = photo ? "Photo" : dead && !ClientRun.ghost ? GhostGlyphs.scramble(body, body.hashCode(), PhoneScreen.DEAD_LOST) : GhostGlyphs.filter(body, body.hashCode());
        line = who + what;
        since = System.currentTimeMillis();
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (since < 0 || !ClientRun.active || client.options.hudHidden || client.currentScreen instanceof PhoneScreen) return;
        long t = System.currentTimeMillis() - since;
        if (t > SHOWN_MS + SLIDE_MS * 2) {
            since = -1;
            return;
        }
        float in = MathHelper.clamp(t / (float) SLIDE_MS, 0, 1), out = MathHelper.clamp((t - SLIDE_MS - SHOWN_MS) / (float) SLIDE_MS, 0, 1);
        float shown = easeOut(in) * (1 - easeIn(out));
        TextRenderer tr = client.textRenderer;
        int x = ctx.getScaledWindowWidth() / 2 - WIDE / 2, y = Math.round(-HIGH + (HIGH + 6) * shown);
        // Over everything else on the screen, as a notification is.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        draw(ctx, tr, x, y);
        ctx.getMatrices().pop();
    }

    private static void draw(DrawContext ctx, TextRenderer tr, int x, int y) {
        ctx.fill(x + 2, y, x + WIDE - 2, y + HIGH, CARD);
        ctx.fill(x, y + 2, x + WIDE, y + HIGH - 2, CARD);
        // The app's mark: a green circle with a speech bubble's tail.
        int cx = x + 13, cy = y + HIGH / 2;
        for (int i = -6; i < 6; i++) {
            int half = (int) Math.round(Math.sqrt(36 - (i + 0.5) * (i + 0.5)));
            ctx.fill(cx - half, cy + i, cx + half, cy + i + 1, GREEN);
        }
        ctx.fill(cx - 6, cy + 4, cx - 3, cy + 7, GREEN);
        String key = KeyBindings.PHONE.getBoundKeyLocalizedText().getString();
        String hint = "Phone (" + key + ")";
        ctx.drawText(tr, tr.trimToWidth(from, WIDE - 40 - tr.getWidth(hint)), x + 25, y + 5, from.startsWith("Unknown") ? HER : TEXT, false);
        ctx.drawText(tr, hint, x + WIDE - 6 - tr.getWidth(hint), y + 5, META, false);
        String shownLine = tr.getWidth(line) > WIDE - 32 ? tr.trimToWidth(line, WIDE - 32 - tr.getWidth("…")) + "…" : line;
        ctx.drawText(tr, shownLine, x + 25, y + 17, META, false);
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static float easeIn(float t) {
        return t * t;
    }
}
