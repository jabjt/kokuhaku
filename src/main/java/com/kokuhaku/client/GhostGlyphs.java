package com.kokuhaku.client;

/**
 * What words look like to the dead. Once you've been called back from the room you
 * can still see what people write, but not all of it: about a third of the letters
 * come out as her marks. The same text always comes out the same way, so a line
 * doesn't crawl about while you read it.
 */
public final class GhostGlyphs {
    /** How many letters in a hundred are lost. */
    private static final int LOST = 30;
    /** † ‡ § ⛤ ⛧ ᚦ ᛉ ⸸ ꙮ ¤ */
    private static final String MARKS = "†‡§⛤⛧ᚦᛉ⸸ꙮ¤";

    private GhostGlyphs() {
    }

    /** The text as a ghost sees it, or as it is for anyone alive. */
    public static String filter(String text, long seed) {
        return ClientRun.ghost ? scramble(text, seed) : text;
    }

    public static String scramble(String text, long seed) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            long h = mix(seed * 31 + i);
            if (Character.isLetter(c) && Math.floorMod(h, 100) < LOST) out.append(MARKS.charAt((int) Math.floorMod(h >>> 8, MARKS.length())));
            else out.append(c);
        }
        return out.toString();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
