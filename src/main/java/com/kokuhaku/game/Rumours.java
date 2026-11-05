package com.kokuhaku.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** What people are saying by now. The school notices what's happening, and it notices what you did. */
final class Rumours {
    private static final float CHANCE = 0.35f;
    private static final int FIRST_DAY = 3;
    private static final Random RANDOM = new Random();

    private static final List<List<String>> BY_WEEK = List.of(
            List.of(),
            List.of("Some parents have started keeping their kids at home.", "The teachers keep having meetings. Nobody says what about."),
            List.of("My mum picks me up now. She won't tell me why.", "Half the class sits with their backs to the wall these days."),
            List.of("Nobody walks home alone any more. Nobody talks about it either.", "The caretaker's put new locks on everything. They don't seem to help."),
            List.of("I think we all know it isn't over.", "I've stopped counting the empty desks."));

    private Rumours() {
    }

    /** Something to add after a hello, or nothing. */
    static String line(RunState s) {
        if (!s.devoted && !Living.active(s) && LunchScene.missed() >= LunchScene.MISSED_FROM && RANDOM.nextBoolean()) return " We saved you a seat at lunch. You never came.";
        if (s.devoted || Living.active(s) || s.day < FIRST_DAY || RANDOM.nextFloat() > CHANCE) return "";
        List<String> open = new ArrayList<>(BY_WEEK.get(Math.min(BY_WEEK.size() - 1, (s.day - 1) / 3)));
        for (int g : s.accused) {
            if (s.caught.contains(g) || s.dead.contains(g)) continue;
            String name = Roster.get(g).firstName();
            open.add("Everyone knows you named " + name + ". She eats lunch by herself now.");
        }
        if (!s.dead.isEmpty()) {
            String last = null;
            for (int d : s.dead) last = Roster.get(d).firstName();
            open.add("They've moved " + last + "'s desk to the back. Nobody will sit near it.");
            open.add(last + "'s shoes are still in the locker. Nobody wants to be the one to move them.");
            if (s.dead.size() > 1) open.add("That's " + s.dead.size() + " now. The teachers have stopped saying the names.");
        }
        return open.isEmpty() ? "" : " " + open.get(RANDOM.nextInt(open.size()));
    }
}
