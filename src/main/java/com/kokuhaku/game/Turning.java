package com.kokuhaku.game;

import com.kokuhaku.game.Mystery.Clue;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The month has a shape. At the start of each week something changes that
 * doesn't change back, so day 20 can't feel like day 3 with higher stakes.
 */
final class Turning {
    private static final int ASSEMBLY = 8, FIGURE = 15, KEY = 22, WITNESS = 29;

    private static int doneOn = 0;
    private static String title = "", line = "";

    private Turning() {
    }

    static void reset() {
        doneOn = 0;
        title = line = "";
    }

    /** Someone alive who still knows something you never asked them about. */
    private static int witness(RunState s) {
        for (Clue c : s.mystery().clues) {
            if (c.kind() == Mystery.Kind.TRAIT && c.witness() >= 0 && !s.found.contains(c.id()) && !s.dead.contains(c.witness())) return c.witness();
        }
        return -1;
    }

    private static void happen(RunState s) {
        doneOn = s.day;
        title = line = "";
        switch (s.day) {
            case ASSEMBLY -> {
                title = "The Assembly";
                int w = witness(s);
                if (w < 0) {
                    line = "The head says there's nothing to worry about. Nobody looks up.";
                    break;
                }
                String name = Roster.get(w).firstName();
                line = "The head says there's nothing to worry about. " + name + " won't stop looking at you.";
                s.notes.add("At the assembly, " + name + " kept looking at you, as if there was something to say.");
            }
            case FIGURE -> {
                title = "After Dark";
                line = "A notice at the gate: go straight home. Something's been seen on the streets, and it doesn't just stand there now.";
            }
            case KEY -> {
                title = "The Spare Key";
                line = "Your spare key's gone. Your door won't keep anyone out tonight, unless you've got a chain on it.";
                s.lockBrokenOn = s.day;
            }
            case WITNESS -> {
                title = "Nobody Answers";
                int w = witness(s);
                if (w < 0) {
                    line = "The police have stopped coming. Nobody at school says why.";
                    break;
                }
                String name = Roster.get(w).firstName();
                line = "A message from " + name + ": \"I have to tell you something. Today. It can't wait until tomorrow.\"";
                s.livingVictim = w;
                Shadow.saw(s, Roster.get(w).firstName() + " knew. She read that message before you did.");
            }
            default -> {
            }
        }
        s.markDirty();
    }

    static void morning(ServerPlayerEntity p, RunState s) {
        if (s.devoted || Living.active(s) || Kept.on(s) || s.day > Schedule.DAYS) return;
        if (doneOn != s.day) happen(s);
        if (!title.isEmpty()) RunManager.tell(p, title, line);
    }
}
