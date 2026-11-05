package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.game.Job;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A shift: customers come up one at a time and you scan each item by finding
 * its code among the buttons, against the clock, which gets shorter. A wrong
 * code or a timeout is a mistake. The shift then skips time and pays out.
 */
public class TillScreen extends Screen {
    private static final int ROUNDS = 12;
    private static final String[] GROCERY = {"Melon Bread", "Onigiri", "Green Tea", "Instant Ramen", "Milk", "Eggs", "Rice 5kg", "Curry Roux", "Pocky", "Tofu", "Natto", "Soy Sauce"};
    private static final String[] KIOSK = {"Ticket to Chinetsu", "Ticket to Minami", "Return to Kita", "Gum", "Newspaper", "Rice Ball", "Canned Coffee", "Umbrella"};
    private static final String[] LIBRARY = {"Kokoro", "Snow Country", "Rashomon", "Botchan", "No Longer Human", "The Gate", "Kusamakura", "The Pillow Book"};
    private static final String[] PEOPLE = {"An old man", "A tired office worker", "A mother and child", "A student you half know", "A woman in a hurry", "Someone who won't look at you"};

    private final Job job;
    private final Random random = new Random();
    private final List<String> codes = new ArrayList<>();
    private int round = 0;
    private int mistakes = 0;
    private String item = "";
    private String code = "";
    private String person = "";
    private long roundStart;
    private boolean sent = false;

    public TillScreen(Job job) {
        super(KokuhakuClient.menuText(job.title));
        this.job = job;
    }

    private long limit() {
        return Math.max(1800, 4500 - round * 220L);
    }

    private String newCode() {
        return String.format("%04d", 1000 + random.nextInt(9000));
    }

    private void nextRound() {
        String[] items = switch (job) {
            case KONBINI -> GROCERY;
            case KIOSK -> KIOSK;
            case LIBRARY -> LIBRARY;
        };
        item = items[random.nextInt(items.length)];
        person = PEOPLE[random.nextInt(PEOPLE.length)];
        code = newCode();
        codes.clear();
        codes.add(code);
        // Near-identical codes, so you have to actually read them.
        while (codes.size() < 6) {
            char[] c = code.toCharArray();
            c[random.nextInt(4)] = (char) ('0' + random.nextInt(10));
            String decoy = new String(c);
            if (!codes.contains(decoy)) codes.add(decoy);
        }
        Collections.shuffle(codes, random);
        roundStart = System.currentTimeMillis();
        clearAndInit();
    }

    @Override
    protected void init() {
        if (item.isEmpty()) {
            nextRound();
            return;
        }
        int x = width / 2 - 96, y = height / 2 + 4;
        for (int i = 0; i < codes.size(); i++) {
            String c = codes.get(i);
            addDrawableChild(new MenuButton(x + (i % 3) * 66, y + (i / 3) * 22, 60, 18, Text.literal(c), b -> scan(c.equals(code))));
        }
    }

    private void scan(boolean right) {
        if (!right) mistakes++;
        round++;
        if (round >= ROUNDS) finish();
        else nextRound();
    }

    private void finish() {
        if (sent) return;
        sent = true;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(job.ordinal());
        buf.writeVarInt(mistakes);
        ClientPlayNetworking.send(Net.SHIFT_DONE, buf);
        close();
    }

    @Override
    public void tick() {
        if (!sent && System.currentTimeMillis() - roundStart > limit()) scan(false);
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
        context.fill(0, 0, width, height, 0xE0101014);
        int cx = width / 2, top = height / 2 - 78;
        context.drawCenteredTextWithShadow(textRenderer, title, cx, top, 0xFFE8E2E4);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Customer " + (round + 1) + " of " + ROUNDS + "   ·   mistakes: " + mistakes), cx, top + 13, mistakes > 0 ? 0xFFD01C30 : 0xFF9A9AA2);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(person + " hands you:"), cx, top + 32, 0xFF9A9AA2);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(item), cx, top + 45, 0xFFF2D6DA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Barcode  " + code), cx, top + 58, 0xFFE8E2E4);
        float frac = 1f - Math.min(1f, (System.currentTimeMillis() - roundStart) / (float) limit());
        context.fill(cx - 96, top + 72, cx - 96 + (int) (192 * frac), top + 74, frac < 0.3f ? 0xFFC0182E : 0xFF8A8A92);
        super.render(context, mouseX, mouseY, delta);
    }
}
