package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.Traits.Club;
import com.kokuhaku.game.Traits.District;
import com.kokuhaku.world.town.Town;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Detective work you do with your feet. Registers tell you who's in a club or
 * lives on a street, so a clue that says "art club" sends you to the art room.
 * And every few days she drops something, somewhere she's been.
 */
final class Sleuthing {
    /** Something on the floor, where it is, and what the morning says about it. */
    record Drop(BlockPos at, String text, String hint) {
    }

    private static final int FIRST_DROP = 2, DAYS_BETWEEN = 2;
    /** How many things she lets slip, by difficulty. */
    private static final int[] DROPS = {6, 5, 3, 2};

    private Sleuthing() {
    }

    /** What a board says today: the dead are crossed out. */
    static String read(RunState s, String what) {
        String[] parts = what.split(":");
        Mystery m = s.mystery();
        boolean club = parts[0].equals("club");
        List<String> names = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) {
            boolean in = club ? m.clubs[g] == Club.valueOf(parts[1]) : m.districts[g] == District.valueOf(parts[1]);
            if (!in) continue;
            names.add(Roster.get(g).firstName() + (s.dead.contains(g) ? " (crossed out)" : ""));
            s.known.add(g);
        }
        String title = club ? Club.valueOf(parts[1]).label() + " club" : District.valueOf(parts[1]).label();
        String line = title + ": " + (names.isEmpty() ? "no girls listed." : String.join(", ", names) + ".");
        if (!s.notes.contains(line)) s.notes.add(line);
        s.markDirty();
        return line;
    }

    /** Whether today's the day something turns up, and if so, what. All of it's true. */
    static Drop todaysDrop(RunState s, Town town) {
        int index = (s.day - FIRST_DROP) / DAYS_BETWEEN;
        if (s.day < FIRST_DROP || (s.day - FIRST_DROP) % DAYS_BETWEEN != 0 || index >= DROPS[s.difficulty.ordinal()] || Living.active(s)) return null;
        Mystery m = s.mystery();
        Student her = Roster.get(s.freeYandere());
        int level = Math.max(1, s.difficulty.ordinal());
        Random r = new Random(s.seed ^ s.day * 4099L);
        String clubRoom = Routines.clubPlace(m.clubs[her.id()]);
        // Each is a place worth going back to, and something that could only have come from her.
        Object[][] drops = {
                {clubRoom, "her club's room", "A hair clip on the floor. " + her.hair().phrase(level)},
                {"foyer", "the entrance hall", "A pencil case left by the shoe lockers. " + her.homeroom().phrase(level)},
                {"center:Library", "the library", "A borrowed book with your name in the margins. " + her.length().phrase(level)},
                {"station", "the station", "A crumpled receipt. " + m.districts[her.id()].phrase(level)},
                {"park", "the park", "A lens cloth, folded very neatly. " + her.glasses().phrase(level)},
                {clubRoom, "her club's room", "A page of practice signatures. All of them are your surname with her first initial: " + her.firstName().charAt(0) + "."},
        };
        Object[] drop = drops[index];
        BlockPos at = town.graph.pos(town.graph.wander(town.place((String) drop[0]), 2 + r.nextInt(3), r));
        String hint = switch (s.difficulty) {
            case EASY -> "Something's been dropped in " + drop[1] + ".";
            case MEDIUM -> "Someone was seen hurrying through " + drop[1] + " last night.";
            default -> "";
        };
        return new Drop(at, (String) drop[2], hint);
    }
}
