package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.GameSettings;
import com.kokuhaku.client.RadioPlayer;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** The radio, like the music app on anyone's phone: the station's tracks, what's playing, and the controls along the bottom. */
public class RadioScreen extends Screen {
    private static final int BACK = 0xFF121212, SIDE = 0xFF000000, BAR = 0xFF181818, ROW = 0x22FFFFFF, WHITE = 0xFFFFFFFF, GREY = 0xFFB3B3B3, DIM = 0xFF535353, GREEN = 0xFF1ED760;
    private static final int[][] ART = {{0xFF3A5A78, 0xFF12202C}, {0xFF5A4A78, 0xFF1C1428}, {0xFF2E6B5A, 0xFF0E2420}, {0xFF8A1420, 0xFF1A0406}};
    private static final int SIDE_W = 118, BAR_H = 52, HEADER_H = 92, ROW_H = 25, TABLE_TOP = HEADER_H + 24, VOLUME_W = 50, HALF_LINE = 70, CONTROLS_RIGHT = 24;
    private static final int OFF = -1, LOOK_UNDER = -2;
    private final boolean canLookUnder;
    private boolean draggingVolume = false;

    public RadioScreen(boolean canLookUnder) {
        super(Text.empty());
        this.canLookUnder = canLookUnder;
    }

    private static boolean hers() {
        return RadioPlayer.playlist.length == 1;
    }

    private static void send(int action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(action);
        ClientPlayNetworking.send(Net.RADIO_PICK, buf);
    }

    private void text(DrawContext context, String line, int x, int y, int colour) {
        context.drawText(textRenderer, KokuhakuClient.menuText(line), x, y, colour, false);
    }

    private void art(DrawContext context, int track, int x, int y, int size) {
        int[] c = ART[track];
        context.fillGradient(x, y, x + size, y + size, c[0], c[1]);
        // Rings on a record, a different size for each song.
        int middle = size / 2, ring = size / (3 + track % 2);
        context.fill(x + middle - ring, y + middle - 1, x + middle + ring, y + middle + 1, 0x60FFFFFF);
        context.fill(x + middle - 1, y + middle - ring, x + middle + 1, y + middle + ring, 0x60FFFFFF);
    }

    private static String time(float seconds) {
        int all = (int) seconds;
        return all / 60 + ":" + String.format("%02d", all % 60);
    }

    private int barTop() {
        return height - BAR_H;
    }

    private int middle() {
        return width / 2 + CONTROLS_RIGHT;
    }

    private int volumeLeft() {
        return width - VOLUME_W - 12;
    }

    private void equaliser(DrawContext context, int x, int y) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            int tall = 3 + (int) (Math.abs(Math.sin(now / (170.0 + i * 60.0) + i)) * 6);
            context.fill(x + i * 3, y + 9 - tall, x + i * 3 + 2, y + 9, GREEN);
        }
    }

    private void circle(DrawContext context, int cx, int cy, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int[] list = RadioPlayer.playlist;
        boolean on = RadioPlayer.on();
        context.fill(0, 0, width, height, BACK);
        // The side: where a library would be.
        context.fill(0, 0, SIDE_W, height - BAR_H, SIDE);
        text(context, "The radio", 12, 12, WHITE);
        context.fill(8, 28, SIDE_W - 8, 58, ROW);
        art(context, list[0], 12, 32, 22);
        text(context, hers() ? "Hers" : "Chinetsu FM", 38, 34, WHITE);
        text(context, list.length + (list.length == 1 ? " song" : " songs"), 38, 45, GREY);
        if (canLookUnder) text(context, "Look underneath", 12, height - BAR_H - 16, mouseX < SIDE_W && mouseY > height - BAR_H - 20 && mouseY < height - BAR_H ? WHITE : GREY);
        // The station.
        int[] tint = ART[list[0]];
        context.fillGradient(SIDE_W, 0, width, HEADER_H, tint[0], BACK);
        art(context, list[0], SIDE_W + 12, 12, 68);
        text(context, "STATION", SIDE_W + 90, 22, WHITE);
        context.getMatrices().push();
        context.getMatrices().translate(SIDE_W + 90, 33, 0);
        context.getMatrices().scale(2f, 2f, 1f);
        text(context, hers() ? "Hers" : "Chinetsu FM", 0, 0, WHITE);
        context.getMatrices().pop();
        text(context, hers() ? "It's the only one it plays now." : "Quiet songs for the small hours.", SIDE_W + 90, 56, GREY);
        // The list.
        text(context, "#", SIDE_W + 14, HEADER_H + 8, GREY);
        text(context, "Title", SIDE_W + 34, HEADER_H + 8, GREY);
        text(context, "Time", width - 44, HEADER_H + 8, GREY);
        context.fill(SIDE_W + 10, HEADER_H + 20, width - 10, HEADER_H + 21, 0x30FFFFFF);
        for (int i = 0; i < list.length; i++) {
            int track = list[i], y = TABLE_TOP + i * ROW_H;
            boolean hover = mouseX > SIDE_W && mouseY >= y && mouseY < y + ROW_H, now = on && RadioPlayer.track == track;
            if (hover) context.fill(SIDE_W + 8, y, width - 8, y + ROW_H, ROW);
            if (now) equaliser(context, SIDE_W + 13, y + 7);
            else text(context, Integer.toString(i + 1), SIDE_W + 14, y + 8, GREY);
            text(context, RadioPlayer.TITLES[track], SIDE_W + 34, y + 2, now ? GREEN : WHITE);
            text(context, RadioPlayer.ARTISTS[track], SIDE_W + 34, y + 13, GREY);
            text(context, time(RadioPlayer.SECONDS[track]), width - 44, y + 8, GREY);
        }
        // What's playing, and the controls.
        int top = barTop(), mid = middle(), track = RadioPlayer.track;
        context.fill(0, top, width, height, BAR);
        art(context, track, 8, top + 8, 36);
        text(context, RadioPlayer.TITLES[track], 50, top + 13, WHITE);
        text(context, RadioPlayer.ARTISTS[track], 50, top + 25, GREY);
        int y = top + 16;
        if (!hers()) {
            text(context, "shuffle", mid - 88, y - 4, RadioPlayer.shuffle ? GREEN : GREY);
            text(context, "|<", mid - 34, y - 4, WHITE);
            text(context, ">|", mid + 24, y - 4, WHITE);
            text(context, "repeat", mid + 54, y - 4, RadioPlayer.repeatOne ? GREEN : GREY);
        }
        circle(context, mid, y, 9, WHITE);
        if (on) {
            context.fill(mid - 3, y - 4, mid - 1, y + 5, BACK);
            context.fill(mid + 2, y - 4, mid + 4, y + 5, BACK);
        } else {
            for (int i = 0; i < 8; i++) context.fill(mid - 3 + i, y - 4 + i / 2, mid - 2 + i, y + 5 - i / 2, BACK);
        }
        float shown = RadioPlayer.elapsed(), whole = RadioPlayer.SECONDS[track];
        int left = mid - HALF_LINE, right = mid + HALF_LINE, line = top + 37;
        text(context, time(shown), left - 28, line - 3, GREY);
        text(context, time(whole), right + 6, line - 3, GREY);
        context.fill(left, line, right, line + 2, DIM);
        context.fill(left, line, left + (int) ((right - left) * shown / whole), line + 2, on ? GREEN : WHITE);
        int volume = volumeLeft();
        text(context, "vol", volume - 18, line - 16, GREY);
        context.fill(volume, line - 13, volume + VOLUME_W, line - 11, DIM);
        context.fill(volume, line - 13, volume + (int) (VOLUME_W * RadioPlayer.level), line - 11, WHITE);
        circle(context, volume + (int) (VOLUME_W * RadioPlayer.level), line - 12, 3, WHITE);
        text(context, "Esc to close", width - 78, 8, 0x80FFFFFF);
    }

    private void setVolume(double mouseX) {
        RadioPlayer.level = MathHelper.clamp((float) ((mouseX - volumeLeft()) / (double) VOLUME_W), 0f, 1f);
        GameSettings.radioLevel = RadioPlayer.level;
        GameSettings.save();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int top = barTop(), mid = middle(), mouth = top + 16;
        int[] list = RadioPlayer.playlist;
        if (y >= top) {
            if (Math.abs(x - mid) <= 11 && Math.abs(y - mouth) <= 11) {
                send(RadioPlayer.on() ? OFF : RadioPlayer.track);
            } else if (!hers() && Math.abs(y - mouth) <= 9) {
                if (x >= mid - 90 && x < mid - 50) RadioPlayer.shuffle = !RadioPlayer.shuffle;
                else if (x >= mid - 38 && x < mid - 16) send(RadioPlayer.neighbour(-1));
                else if (x >= mid + 18 && x < mid + 40) send(RadioPlayer.neighbour(1));
                else if (x >= mid + 50 && x < mid + 90) RadioPlayer.repeatOne = !RadioPlayer.repeatOne;
            }
            if (x >= volumeLeft() - 4 && x <= volumeLeft() + VOLUME_W + 4 && y >= top + 16 && y <= top + 36) {
                draggingVolume = true;
                setVolume(x);
            }
            return true;
        }
        if (canLookUnder && x < SIDE_W && y > height - BAR_H - 20) {
            send(LOOK_UNDER);
            close();
            return true;
        }
        if (x > SIDE_W && y >= TABLE_TOP) {
            int row = (int) ((y - TABLE_TOP) / ROW_H);
            if (row < list.length) send(list[row]);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingVolume) setVolume(x);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        draggingVolume = false;
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
