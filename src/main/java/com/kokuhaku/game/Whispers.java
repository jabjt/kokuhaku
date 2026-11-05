package com.kokuhaku.game;

import com.kokuhaku.world.town.Town;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * She saw that. Everything you did yesterday is remembered, and the note under
 * your door in the morning is about you, not about anyone: who you spoke to,
 * what you bought, whether your seat was empty. The horror of it is that it's
 * always right, and that it's always tender.
 */
final class Whispers {
    private static final int FIRST_DAY = 2, FLOWER_REACH = 3, CLOSE_DAYS = 3, WARNS_FROM = 3;
    private static final float NOTE_CHANCE = 0.7f;
    private static final Random RANDOM = new Random();

    private static int lastGirl = -1, wrongGuess = -1, flowers = 0, warned = -1;
    /** Which days you spent time with each girl, and who you've been told to stay away from. */
    private static final Map<Integer, Set<Integer>> daysWith = new HashMap<>();
    private static final Set<Integer> warnedAbout = new HashSet<>();
    private static String drink = null;
    private static boolean worked, absent, doorOpen, doorShut, skippedWalk, foundClue, wasHurt;

    private static final List<List<String>> ANY_DAY = List.of(
            List.of("You look so peaceful when you sleep. Please keep doing that.", "I had a dream about you. We were very old.", "You're the first thing I think about. I wanted you to know."),
            List.of("I've started keeping your hair. Only what falls. I'd never hurt you.", "Three weeks ago you said 'excuse me' to me. I've been happy ever since.", "Have you noticed the days go faster when you're with me?"),
            List.of("I don't sleep any more. I don't need to. You do that for both of us.", "There's nobody left who looks at you the way I do. I checked.", "I've counted the steps from your bed to your door. Eleven."),
            List.of("Not long now, my love.", "I've got everything ready.", "You've been so brave. You can stop now."));

    private Whispers() {
    }

    static void reset() {
        lastGirl = wrongGuess = warned = -1;
        flowers = 0;
        daysWith.clear();
        warnedAbout.clear();
        drink = null;
        forget();
    }

    private static void forget() {
        lastGirl = -1;
        wrongGuess = -1;
        drink = null;
        worked = absent = doorOpen = doorShut = skippedWalk = foundClue = wasHurt = false;
    }

    /** She asked you not to. If you do it anyway, she won't ask again. */
    static void talkedTo(RunState s, int girl) {
        lastGirl = girl;
        daysWith.computeIfAbsent(girl, g -> new HashSet<>()).add(s.day);
        if (girl != warned || s.livingVictim >= 0) return;
        warned = -1;
        s.livingVictim = girl;
        s.markDirty();
        Shadow.saw(s, "You went back to " + Roster.get(girl).firstName() + " after she'd asked you not to.");
    }

    /** The girl you keep going back to, if there is one she hasn't mentioned yet. */
    private static int tooClose(RunState s) {
        if (s.devoted || s.day < WARNS_FROM || warned >= 0) return -1;
        for (Map.Entry<Integer, Set<Integer>> e : daysWith.entrySet()) {
            int g = e.getKey();
            if (e.getValue().size() >= CLOSE_DAYS && !warnedAbout.contains(g) && !s.dead.contains(g) && g != s.dating) return g;
        }
        return -1;
    }

    /** Who you talked to last, if anyone, since this morning. */
    static String lastGirl() {
        return lastGirl < 0 ? null : Roster.get(lastGirl).firstName();
    }

    static void bought(String label) {
        drink = label;
    }

    static void worked() {
        worked = true;
    }

    static void absent() {
        absent = true;
    }

    static void skippedWalk() {
        skippedWalk = true;
    }

    static void foundClue() {
        foundClue = true;
    }

    static void hurt() {
        wasHurt = true;
    }

    static void wrongGuess(int girl) {
        wrongGuess = girl;
    }

    static void door(boolean locked) {
        if (locked) doorShut = true;
        else doorOpen = true;
    }

    /** This morning's note, or nothing, and then yesterday's forgotten: she only ever talks about the last thing you did. */
    static String morningNote(RunState s) {
        String note = compose(s);
        forget();
        return note;
    }

    private static String compose(RunState s) {
        if (s.day < FIRST_DAY || s.party.size() > 1 && RANDOM.nextBoolean()) return null;
        if (wrongGuess >= 0) return "You told them it was " + Roster.get(wrongGuess).firstName() + ". I forgive you. I'm the only one who ever will.";
        if (wasHurt) return "I saw the car. I couldn't breathe until I knew. Please look where you're going. You're not only yours.";
        int close = tooClose(s);
        if (close >= 0) {
            warned = close;
            warnedAbout.add(close);
            return "You keep going back to " + Roster.get(close).firstName() + ". Please stop. I won't ask again.";
        }
        List<String> open = new ArrayList<>();
        open.add(Habits.note(s));
        if (doorOpen) open.add("You left your door open for me. Thank you. I only stayed a little while.");
        if (doorShut) open.add("You locked your door. I understand. I stood outside and listened to you breathe instead.");
        if (absent) open.add("Your seat was empty. Please don't do that again. I couldn't feel my hands.");
        if (lastGirl >= 0) {
            String name = Roster.get(lastGirl).firstName();
            open.add("You spoke to " + name + " yesterday. She doesn't know you like I do. I've made a note of her.");
            open.add(name + " made you smile. I've been practising in the mirror. Is this right?");
        }
        if (drink != null) open.add("You had " + drink.toLowerCase() + " yesterday. I got the same one. We have so much in common.");
        if (worked) open.add("You looked so tired at work. I came in twice. You didn't look up. It's alright.");
        if (skippedWalk) open.add("You hurried this morning. You don't have to run. I always know where you're going.");
        if (foundClue) open.add("You're getting closer. I can't tell you how happy that makes me.");
        if (!open.isEmpty() && RANDOM.nextFloat() < NOTE_CHANCE) return open.get(RANDOM.nextInt(open.size()));
        if (RANDOM.nextFloat() > NOTE_CHANCE) return null;
        if (RANDOM.nextBoolean()) return Habits.note(s);
        List<String> week = ANY_DAY.get(Math.min(3, (s.day - 1) / 7));
        return week.get(RANDOM.nextInt(week.size()));
    }

    /** She was in your room, and she left you something. They don't go away. */
    static boolean leaveFlower(ServerWorld world, Town.Home home) {
        BlockPos foot = home.bedFoot();
        for (int ring = 1; ring <= FLOWER_REACH; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    BlockPos at = foot.add(dx, 0, dz);
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !home.containsBlock(at) || !RunManager.standable(world, at)) continue;
                    if ((at.getX() + at.getZ() + flowers) % 2 != 0) continue;
                    world.setBlockState(at, Blocks.POTTED_RED_TULIP.getDefaultState());
                    flowers++;
                    return true;
                }
            }
        }
        return false;
    }

    static int flowers() {
        return flowers;
    }
}
