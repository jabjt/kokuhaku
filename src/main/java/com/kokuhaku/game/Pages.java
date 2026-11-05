package com.kokuhaku.game;

/**
 * Haruka's diary: six torn pages left around town by a girl who got the same
 * note three years ago. They aren't clues to who it is, until the last one.
 */
public final class Pages {
    /** Where each page lies, by named place; the last is in the hospital's records room. */
    public static final String[] PLACES = {"center:Library", "center:Darkroom", "station", "beach", "park"};
    public static final int COUNT = PLACES.length + 1;
    /** What the game is really called. Nobody can read it, until they find the place that says what it means. */
    public static final String TRUE_NAME = "\u2E38\uA66E\u16C9\u26E7\u16A6\u2E38";
    public static final String NAME_REVEALED = "Scratched deep into the rock, the same marks as on the game itself: " + TRUE_NAME + ". Under them, in Haruka's hand: \"It was never a name. It means trapped.\"";

    private static final String[] TEXT = {
            "A torn diary page, signed Haruka: \"There was a note in my locker today. 'I love you.' I thought it was sweet.\"",
            "A torn diary page: \"Someone's taking pictures of me. There was one in my bag this morning. I'm asleep in it. In my own bed.\"",
            "A torn diary page: \"I tried to leave. I bought a ticket. She was on the platform when I got there, holding my suitcase. She'd packed it for me.\"",
            "A torn diary page: \"Everyone says how lucky I am. She never hurts me. She only ever hurts the people I talk to.\"",
            "A torn diary page: \"I've worked it out. It was never one girl. It gets handed down, like a key to a club room. She told me I'd understand when I was older.\"",
    };

    private Pages() {
    }

    /** Page six is a patient file, and whoever it is that visits has signed for it. */
    public static String text(int page, RunState s) {
        if (page < TEXT.length) return TEXT[page];
        Roster.Student her = Roster.get(s.freeYandere());
        return "Patient file: Haruka M., 17. Injury to the back of the head. One visitor, every day for a year. The visitors' book has a line in fresh ink: \""
                + her.glasses().phrase(0) + " " + her.homeroom().phrase(0) + "\"";
    }
}
