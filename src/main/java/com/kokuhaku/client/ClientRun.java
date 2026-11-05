package com.kokuhaku.client;

import com.kokuhaku.Difficulty;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** What the client knows about the current run, kept up to date by the server. */
public final class ClientRun {
    public record Announcement(String title, String subtitle, int style) {
    }

    public static boolean active = false;
    public static int day = 1;
    public static int minute = 0;
    public static int guessesLeft = 0;
    /** Where you're meant to be, on the difficulties that tell you. */
    public static net.minecraft.util.math.BlockPos target = null;
    /** How close it all is, 0..100. */
    public static int dread = 0;
    /** How near she is to losing her patience, 0..100, or -1 if she isn't keeping count. */
    public static int temper = -1;
    /** What she did, ready for after the ending. It outlives the run, as the ending does. */
    public static String reel = null;
    /** Messages you haven't read. */
    public static int unread = 0;
    /** Seconds left to get home with something behind you, or -1. */
    public static int chase = -1;
    public static boolean slowing = false;
    /** Called back from the room: nothing can touch you now, and nothing listens to you either. */
    public static boolean ghost = false;
    public static int yen = 0;
    /** What you're carrying on a tray, if anything. */
    public static net.minecraft.item.ItemStack tray = null;
    public static int locker = 0;
    /** Picks your timetable for this run. */
    public static long week = 0;
    /** What you should be doing, as clearly as the difficulty will tell you. */
    public static String objective = "";
    /** How many started the run, and how many are still alive. */
    public static int party = 1, alive = 1;
    public static Difficulty difficulty = Difficulty.EASY;
    /** Girls you've crossed off on the suspect board. Only a note to yourself; the server doesn't care. */
    public static final Set<Integer> crossed = new HashSet<>();

    private static final Deque<Announcement> queue = new ArrayDeque<>();
    private static Announcement current;
    private static long currentStart;

    private ClientRun() {
    }

    public static void reset() {
        active = false;
        party = 1;
        alive = 1;
        crossed.clear();
        queue.clear();
        current = null;
    }

    public static void announce(Announcement a) {
        queue.add(a);
    }

    public static long duration(Announcement a) {
        return a.style() == 1 ? 5500 : 4000;
    }

    /** The announcement on screen now and how far through it we are (0..1), or null. */
    public static Announcement current(long now) {
        if (current != null && now - currentStart > duration(current)) {
            current = null;
        }
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            currentStart = now;
        }
        return current;
    }

    public static float progress(long now) {
        return current == null ? 0f : (now - currentStart) / (float) duration(current);
    }
}
