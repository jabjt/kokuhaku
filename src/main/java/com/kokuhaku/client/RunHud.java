package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.game.Schedule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Top-right clock and the titles that appear across the top of the screen. */
public final class RunHud {
    private static final int ARROW_ABOVE = 64, ARROW_SIZE = 8, ARRIVED = 5;
    private static final int PULSE_FROM = 40, BEAT_FROM = 50, TEMPER_WARNING = 60, CLOCK_WIDTH = 130;

    private RunHud() {
    }

    private static long nextBeat = 0;

    /** The picture closes in as the month goes on, and pulses when she's near. Your heart does the rest. */
    private static void renderDread(DrawContext context, int w, int h) {
        int dread = ClientRun.dread;
        if (dread <= 0) return;
        long now = System.currentTimeMillis();
        float pulse = dread > PULSE_FROM ? (float) (0.5 + 0.5 * Math.sin(now / (900.0 - dread * 6.0))) * 0.25f : 0f;
        int edge = (int) (Math.min(1f, dread / 100f + pulse) * 190);
        int band = h / 4;
        context.fillGradient(0, 0, w, band, edge << 24, 0);
        context.fillGradient(0, h - band, w, h, 0, edge << 24);
        if (dread > BEAT_FROM && now > nextBeat) {
            nextBeat = now + 1700 - dread * 11L;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1f, 0.2f + (dread - BEAT_FROM) / 90f));
        }
    }

    /** A small arrow at the top of the screen, turning as you turn, and how far it is. */
    private static void renderArrow(DrawContext context, TextRenderer tr, MinecraftClient client, int w, int h) {
        var target = ClientRun.target;
        if (target == null) return;
        double dx = target.getX() + 0.5 - client.player.getX(), dz = target.getZ() + 0.5 - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double climb = target.getY() - client.player.getY();
        if (distance < ARRIVED && Math.abs(climb) < 3) return;
        float angle = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90 - client.player.getYaw());
        var m = context.getMatrices();
        m.push();
        int y = h - ARROW_ABOVE;
        m.translate(w / 2f, y, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        for (int row = 0; row < ARROW_SIZE; row++) context.fill(-row, row - ARROW_SIZE / 2, row + 1, row - ARROW_SIZE / 2 + 1, 0xE0E8E2E4);
        context.fill(-1, ARROW_SIZE / 2, 2, ARROW_SIZE, 0xE0E8E2E4);
        m.pop();
        String label = (int) distance + "m" + (climb > 3 ? " (upstairs)" : climb < -3 ? " (downstairs)" : "");
        context.drawCenteredTextWithShadow(tr, KokuhakuClient.menuText(label), w / 2, y + ARROW_SIZE + 3, 0xFFB0A8AC);
    }

    /** A thin line at the bottom of the picture, and it's her patience. */
    private static void renderTemper(DrawContext context, TextRenderer tr, int w, int h) {
        int temper = ClientRun.temper;
        if (temper <= 0) return;
        int width = 140, x = w / 2 - width / 2, y = h - 24;
        boolean close = temper >= TEMPER_WARNING;
        context.fill(x - 1, y - 1, x + width + 1, y + 3, 0xA0000000);
        context.fill(x, y, x + width * Math.min(100, temper) / 100, y + 2, close ? 0xFFE0283C : 0xFFB8A0A4);
        if (close) context.drawCenteredTextWithShadow(tr, KokuhakuClient.menuText("Get away from them"), w / 2, y - 12, 0xFFE0283C);
    }

    /** Top and centre, flashing, and solid red if you've slowed down. */
    private static void renderChase(DrawContext context, TextRenderer tr, int w) {
        if (ClientRun.chase < 0) return;
        boolean flash = System.currentTimeMillis() / 250 % 2 == 0;
        int colour = ClientRun.slowing || flash ? 0xFFE0283C : 0xFFFFFFFF;
        context.drawCenteredTextWithShadow(tr, KokuhakuClient.menuText("TIME LEFT"), w / 2, 10, colour);
        var m = context.getMatrices();
        m.push();
        m.translate(w / 2f, 22, 0);
        m.scale(2.5f, 2.5f, 1f);
        context.drawCenteredTextWithShadow(tr, KokuhakuClient.menuText(ClientRun.chase / 60 + ":" + String.format("%02d", ClientRun.chase % 60)), 0, 0, colour);
        m.pop();
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientRun.active || client.options.hudHidden || MenuScene.isActive()) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();

        // In her room there's no clock and nothing to carry.
        boolean free = client.player.getY() < 90;
        renderDread(context, w, h);
        renderTemper(context, tr, w, h);
        renderArrow(context, tr, client, w, h);
        renderChase(context, tr, w);
        // Clock, top right.
        Text day = KokuhakuClient.menuText("Day " + ClientRun.day + "  ·  " + Schedule.WEEKDAYS[Schedule.weekday(ClientRun.day)]);
        Text time = KokuhakuClient.menuText(Schedule.clock(ClientRun.minute));
        if (free) {
            context.drawTextWithShadow(tr, day, w - tr.getWidth(day) - 8, 8, 0xFFD8D0D4);
            context.drawTextWithShadow(tr, time, w - tr.getWidth(time) - 8, 20, 0xFF9A9AA2);
        }
        Text money = KokuhakuClient.menuText(ClientRun.yen < 0 ? "-¥" + (-ClientRun.yen) : "¥" + ClientRun.yen);
        if (free) context.drawTextWithShadow(tr, money, w - tr.getWidth(money) - 8, 32, ClientRun.yen < 0 ? 0xFFD01C30 : 0xFFD8B860);
        if (free && ClientRun.unread > 0) {
            Text phone = KokuhakuClient.menuText("Phone (" + KeyBindings.PHONE.getBoundKeyLocalizedText().getString() + "): " + ClientRun.unread + " new");
            context.drawTextWithShadow(tr, phone, w - tr.getWidth(phone) - 8, 44, 0xFFE0283C);
        }

        if (free && !ClientRun.objective.isEmpty()) {
            boolean plain = ClientRun.difficulty.ordinal() < 2;
            // It wraps short of the clock, however long the day's orders are.
            int line = 8;
            for (var part : tr.wrapLines(KokuhakuClient.menuText(ClientRun.objective), w - CLOCK_WIDTH)) {
                context.drawTextWithShadow(tr, part, 8, line, plain ? 0xFFE8E2E4 : 0xFF8C7C80);
                line += 11;
            }
        }

        // Two slots at the side, with no boxes: just what you're carrying, the held one brighter.
        var inv = client.player.getInventory();
        for (int i = 0; i < 2 && free; i++) {
            int y = h / 2 - 18 + i * 20;
            if (i == inv.selectedSlot) context.fill(4, y - 1, 6, y + 17, 0x80E8E2E4);
            context.drawItem(inv.getStack(i), 10, y);
        }

        if (Voice.muted) context.drawTextWithShadow(tr, KokuhakuClient.menuText("Muted"), 8, h - 16, 0xFF9A9AA2);

        long now = System.currentTimeMillis();
        ClientRun.Announcement a = ClientRun.current(now);
        if (a == null) {
            return;
        }
        float p = ClientRun.progress(now);

        if (a.style() == 1) {
            // Sleep: black out, then fade back in on the new day.
            float black = p < 0.45f ? 1f : 1f - MathHelper.clamp((p - 0.45f) / 0.35f, 0f, 1f);
            context.fill(0, 0, w, h, ((int) (black * 255) << 24));
        }

        // Fade in over the first 15%, out over the last 25%.
        float alpha = p < 0.15f ? p / 0.15f : p > 0.75f ? (1f - p) / 0.25f : 1f;
        int a8 = MathHelper.clamp((int) (alpha * 255), 8, 255) << 24;
        if (!a.title().isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(w / 2f, 28, 0);
            context.getMatrices().scale(2.2f, 2.2f, 1f);
            context.drawCenteredTextWithShadow(tr, KokuhakuClient.menuText(a.title()), 0, 0, a8 | 0xE8E2E4);
            context.getMatrices().pop();
        }
        if (!a.subtitle().isEmpty()) {
            int y = a.title().isEmpty() ? 32 : 54;
            context.drawCenteredTextWithShadow(tr, KokuhakuClient.menuText(a.subtitle()), w / 2, y, a8 | 0xB0A8AC);
        }
    }
}
