package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ClientRun;
import com.kokuhaku.client.KeyBindings;
import com.kokuhaku.game.Schedule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Your diary: the week's lessons, with today's column and the current period highlighted. */
public class TimetableScreen extends Screen {
    public TimetableScreen() {
        super(KokuhakuClient.menuText("Diary"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.TIMETABLE.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD0000000);
        int colW = Math.min(100, (width - 90) / 5);
        int left = (width - (60 + colW * 5)) / 2;
        int top = 46;
        int rowH = 16;

        Icons.draw(context, Icons.CLOCK, left, 12, 1);
        context.drawTextWithShadow(textRenderer, title, left + 22, 17, 0xFFE8E2E4);

        int today = Schedule.weekday(ClientRun.day);
        int period = currentPeriod();

        for (int d = 0; d < 5; d++) {
            int x = left + 60 + d * colW;
            if (d == today) context.fill(x, top - 14, x + colW - 2, top + rowH * 6, 0x30E0283C);
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(Schedule.WEEKDAYS[d].substring(0, 3)),
                    x + 4, top - 11, d == today ? 0xFFE0283C : 0xFF9A9AA2);
        }
        for (int p = 0; p < 6; p++) {
            int y = top + p * rowH;
            context.drawTextWithShadow(textRenderer, Text.literal(Schedule.clock(Schedule.PERIOD_START[p])), left, y + 4, 0xFF6C6C74);
            for (int d = 0; d < 5; d++) {
                List<String> subjects = Schedule.subjects(ClientRun.week, d);
                int x = left + 60 + d * colW;
                boolean now = d == today && p == period;
                if (now) context.fill(x, y, x + colW - 2, y + rowH - 2, 0x80E0283C);
                String s = subjects.get(p);
                int number = Schedule.roomNumber(s);
                int date = (ClientRun.day - 1) / 7 * 7 + d + 1;
                String room = number == 0 ? (Schedule.swimming(date) ? "Pool" : "Court") : Schedule.practical(s, date) ? (s.equals("Science") ? "Lab" : "Kit") : Schedule.roomKey(number);
                int roomW = textRenderer.getWidth(room);
                String shown = textRenderer.trimToWidth(s, colW - 12 - roomW);
                context.drawTextWithShadow(textRenderer, Text.literal(shown), x + 4, y + 4, now ? 0xFFFFFFFF : 0xFFD8D0D4);
                context.drawTextWithShadow(textRenderer, Text.literal(room), x + colW - 5 - roomW, y + 4, now ? 0xFFFFFFFF : 0xFF8A8A92);
            }
        }

        int y = top + rowH * 6 + 14;
        String[][] rest = {
                {Schedule.clock(Schedule.WAKE), "Wake up"},
                {Schedule.clock(Schedule.HOMEROOM), "Homeroom, Room " + Schedule.roomKey(Schedule.HOMEROOM_2A)},
                {Schedule.clock(Schedule.LUNCH), "Lunch"},
                {Schedule.clock(Schedule.AFTER_SCHOOL), "After school: clubs, visiting houses. You can sleep now."},
                {Schedule.clock(ClientRun.difficulty.curfew), "Curfew. Be inside your home. Don't be late."},
        };
        for (String[] r : rest) {
            context.drawTextWithShadow(textRenderer, Text.literal(r[0]), left, y, 0xFF6C6C74);
            context.drawTextWithShadow(textRenderer, Text.literal(r[1]), left + 60, y, 0xFFB0A8AC);
            y += 11;
        }
        context.drawTextWithShadow(textRenderer, Text.literal("Shoe locker number " + ClientRun.locker + ". Lock your front door: key in slot 2."), left, y + 2, 0xFF9A9AA2);
        y += 12;
        if (!Schedule.isSchoolDay(ClientRun.day)) {
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("It's the weekend. No school today."), left, y + 6, 0xFFE0283C);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private int currentPeriod() {
        if (!Schedule.isSchoolDay(ClientRun.day)) return -1;
        for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
            int start = Schedule.PERIOD_START[p];
            if (ClientRun.minute >= start && ClientRun.minute < start + Schedule.PERIOD_LENGTH) return p;
        }
        return -1;
    }
}
