package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The notes that aren't hers. Someone else is writing to you, in a different
 * hand, and he remembers the runs you can't. Each run you finish lets a little
 * more of it through: the handwriting, the card, the book at the hut, the chart
 * on the top floor, and at the end, why it's you.
 *
 * It says why. It never says how, and nothing here should ever start to.
 */
public final class Story {
    private static final int[] NOTE_DAYS = {3, 6, 9, 12};
    public static final int CARD_FROM = 1, BOOK_FROM = 2, CHART_FROM = 3, LETTER_FROM = 4;

    private static final String[][] NOTES = {
            {"Don't answer her.", "Lock it even when you're home. Especially then.", "Turn the radio off after dark. Please.", "You've got further than I thought you would."},
            {"Day %d. That's where it stopped last time. I'm sorry I wasn't quicker.", "You won't remember the last time. I do.", "Look under the radio. I left something there when you were small.", "Whatever you did differently, do it again."},
            {"This is number %d now. It doesn't get easier to watch.", "She asks about you. She always asks about you.", "There's a book at the ski hut. Look at the last page.", "I took you up that mountain once. You won't remember that either."},
            {"Day %d last time. You're getting closer to something, and I don't know what happens then.", "Go to the hospital. The records, on the top floor. Read the chart, not the name.", "I can't get to you. I've tried.", "Don't come looking for me. There's nothing left to find."},
            {"Number %d. You should know why by now. You've earned that much.", "I never meant for it to be you.", "Stay away from the radio. That's not always me.",
                    "She had me, and I'd have said anything. There was a photo in my wallet. I told her your name. That's all it took. That's why it's you. I'm sorry."},
    };

    private static int runs = 0, lastDay = 0;

    private Story() {
    }

    /** What you've done before, from the record the game keeps between runs. In a party, whoever's been at it longest. */
    public static void record(int played, int day) {
        if (played < runs) return;
        lastDay = played > runs ? day : Math.max(lastDay, day);
        runs = played;
    }

    static int runs() {
        return runs;
    }

    private static String note(int index) {
        String[] notes = NOTES[Math.min(runs, NOTES.length - 1)];
        String line = notes[index];
        if (!line.contains("%d")) return line;
        return String.format(line, line.startsWith("Day") ? Math.max(1, lastDay) : runs + 1);
    }

    /** It turns up in your pocket after school, and it's never there when you check before. */
    static void minute(ServerPlayerEntity p, RunState s) {
        if (s.minute != Schedule.AFTER_SCHOOL + 5 || s.devoted || Living.active(s) || Kept.on(s)) return;
        for (int i = 0; i < NOTE_DAYS.length; i++) {
            if (NOTE_DAYS[i] != s.day) continue;
            String text = note(i);
            Kokuhaku.LOG.info("A note in a different hand: {}", text);
            Talk.say(p, "In your coat pocket", "A folded note. It isn't her writing. \"" + text + "\"", Talk.option("Keep it.", q -> {
                s.notes.add("A note in a different hand: \"" + text + "\"");
                s.markDirty();
            }));
        }
    }

    /** Under the radio, from the second run on. */
    static String card() {
        return "An old birthday card, gone soft at the corners. \"Be good. Back soon.\" It's signed Dad. It's the same hand as the notes in your pocket.";
    }

    static String book() {
        return "The visitors' book. The last entry is three years old. The name's been scratched out so hard the paper's torn. Underneath, in the hand from your pocket: \"Not again.\"";
    }

    static String chart() {
        return "Clipped behind the page: a patient's chart with the name cut out. One visitor, every day, for three years. Then one word, in neat handwriting you know very well: \"Discharged.\" There's no date.";
    }

    /** For the end of the run, once it can't hurt anyone. */
    static String lastLine() {
        if (runs >= LETTER_FROM) return "He's still writing. She lets him.";
        if (runs >= CARD_FROM) return "Someone else was watching the whole time. He always is.";
        return "Not all of those notes were hers.";
    }
}
