package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Random;

/**
 * A practical: three steps, each the same idea. Hold to pour or to heat, and let
 * go with the level between the two lines. It keeps creeping for a moment after
 * you let go, so you have to stop early. Too much, and it's ruined.
 */
public class PracticalScreen extends Screen {
    private static final String[][] LAB = {{"Add the acid", "It fizzes up over the rim."}, {"Heat the flask", "The flask cracks."}, {"Drip in the indicator", "It's gone the wrong colour."}};
    private static final String[][] KITCHEN = {{"Pour in the stock", "It's all over the hob."}, {"Turn up the heat", "It's burnt to the pan."}, {"Add the seasoning", "Nobody could eat that."}};
    private static final int[] LAB_COLOURS = {0xFF58C8A0, 0xFFE08030, 0xFFB050D0}, KITCHEN_COLOURS = {0xFFC8A060, 0xFFE05030, 0xFFD8C040};
    private static final float[] RISE = {0.0085f, 0.0105f, 0.0125f, 0.015f}, BAND = {0.16f, 0.13f, 0.1f, 0.08f};
    private static final float CREEP = 0.04f, LOWEST = 0.35f, HIGHEST = 0.7f;
    private static final long RESULT_MS = 900;
    private static final int STEPS = 3, TALL = 110, WIDE = 50;

    private final String[][] steps;
    private final int[] colours;
    private final float rise, band;
    private final Random random = new Random();
    private int step = 0, done = 0;
    private float level = 0f, low, creep = 0f;
    private boolean holding = false, poured = false, sent = false, good = false;
    private long resultAt = 0;

    public PracticalScreen(boolean kitchen, int difficulty) {
        super(KokuhakuClient.menuText(kitchen ? "Home Economics" : "Science"));
        steps = kitchen ? KITCHEN : LAB;
        colours = kitchen ? KITCHEN_COLOURS : LAB_COLOURS;
        rise = RISE[difficulty];
        band = BAND[difficulty];
        nextStep();
    }

    private void nextStep() {
        level = 0f;
        creep = 0f;
        poured = false;
        resultAt = 0;
        low = LOWEST + random.nextFloat() * (HIGHEST - LOWEST);
    }

    private void stop() {
        if (!holding) return;
        holding = false;
        poured = true;
        creep = CREEP;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!poured && resultAt == 0) holding = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        stop();
        return true;
    }

    @Override
    public void tick() {
        if (sent) return;
        if (resultAt != 0) {
            if (System.currentTimeMillis() - resultAt < RESULT_MS) return;
            step++;
            if (step >= STEPS) {
                sent = true;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(done);
                buf.writeVarInt(STEPS);
                ClientPlayNetworking.send(Net.PE_DONE, buf);
                close();
            } else {
                nextStep();
            }
            return;
        }
        if (holding) level += rise;
        if (creep > 0f) {
            float bit = Math.min(creep, rise * 0.5f);
            level += bit;
            creep -= bit;
        }
        boolean finished = poured && creep <= 0f, over = level >= 1f;
        if (finished || over) {
            holding = false;
            good = !over && level >= low && level <= low + band;
            if (good) done++;
            resultAt = System.currentTimeMillis();
            client.getSoundManager().play(PositionedSoundInstance.master(good ? SoundEvents.BLOCK_BREWING_STAND_BREW : SoundEvents.BLOCK_GLASS_BREAK, 1f, 0.5f));
        }
    }

    @Override
    public void removed() {
        client.options.hudHidden = false;
    }

    @Override
    protected void init() {
        client.options.hudHidden = true;
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
        context.fill(0, 0, width, height, 0x90000000);
        int mid = width / 2, top = height / 2 - TALL / 2 + 6, bottom = top + TALL;
        int left = mid - WIDE / 2, right = mid + WIDE / 2;
        int shown = Math.min(step, STEPS - 1);
        context.drawCenteredTextWithShadow(textRenderer, title, mid, top - 34, 0xFFE0283C);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("Step " + (shown + 1) + " of " + STEPS + ":  " + steps[shown][0]), mid, top - 20, 0xFFE8E2E4);
        context.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF9A9AA2);
        context.fill(left, top, right, bottom, 0xFF141016);
        int surface = bottom - (int) (Math.min(1f, level) * TALL);
        context.fill(left, surface, right, bottom, colours[shown]);
        // The two lines you've got to stop between.
        int high = bottom - (int) ((low + band) * TALL), under = bottom - (int) (low * TALL);
        context.fill(left - 8, high, right + 8, high + 1, 0xFFFFFFFF);
        context.fill(left - 8, under, right + 8, under + 1, 0xFFFFFFFF);
        String say = resultAt != 0 ? (good ? "Just right." : steps[shown][1]) : "Hold the mouse button. Let go early: it keeps creeping up.";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(say), mid, bottom + 10, resultAt != 0 && !good ? 0xFFE0283C : 0xFFD8D0D4);
    }
}
