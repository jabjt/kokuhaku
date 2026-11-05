package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.Traits.Club;
import com.kokuhaku.game.Traits.District;
import com.kokuhaku.game.Traits.Phrased;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * One run's mystery, rebuilt identically from (seed, difficulty, party size):
 * who they are, every student's club and neighbourhood, and every clue with
 * who holds it and the day it becomes available.
 *
 * Solving is deduction: each trait clue is true of one yandere, and together
 * a yandere's clues single her out. There's one yandere per player; with more
 * than one, every clue says which of them it's about.
 */
public final class Mystery {
    public enum Kind { NOTE, TRAIT, HERRING, HINT, ALIBI }

    /** witness = student id who tells you, or -1 if it comes to you. about = girl id for herrings/hints. */
    public record Clue(int id, Kind kind, String text, int witness, int day, int about) {
    }

    private record Fact(Phrased value, IntPredicate matches) {
    }

    /** The last clue a difficulty hands out: you can't be sure before about this day. */
    private static final int[] LAST_CLUE_DAY = {7, 11, 13, 14};
    private static final String[] WHICH = {"The first girl: ", "The second girl: ", "The third girl: "};
    /** Someone vouches for an innocent girl. Fewer of them on harder difficulties. */
    private static final int[] ALIBIS = {7, 5, 3, 2};
    private static final String[] ALIBI_LINES = {
            "%s was at my house the night the note appeared. It wasn't her.",
            "%s has been helping at the clinic every evening. She's never around after dark.",
            "%s couldn't have written that note. Her handwriting's nothing like it.",
            "%s was away that whole week, visiting her grandmother.",
            "%s told me she likes someone else. I've seen them together.",
            "%s is scared of the dark. She gets picked up from school every day.",
            "I walk home with %s every night. She goes straight indoors.",
            "%s broke her wrist last month. She can't even carry her own bag.",
            "%s hardly knows who you are. She had to ask me your name.",
            "%s was in the nurse's office the whole time. I was there too.",
    };
    /** What turns up in your locker as the days go on. */
    private static final String[] LOCKER_NOTES = {
            "Another note: \"You looked tired today. Are you sleeping?\"",
            "Another note: \"I saw who you were talking to.\"",
            "Another note: \"You walk the long way home now. Why?\"",
            "Another note, in the same neat hand: \"I know you've been asking about me.\"",
            "Another note: \"Your curtains were open last night.\"",
            "Another note: \"Not long now.\"",
    };
    private static final String[] HERRINGS = {
            "%s has been asking everyone which locker is yours.",
            "%s was crying behind the gym after you talked to another girl.",
            "%s keeps a photo in her pencil case. Nobody's seen who's in it.",
            "%s left school early the day the note appeared.",
            "%s wrote something on her hand and hid it when you walked past.",
            "%s knows your address. Nobody remembers telling her.",
    };

    public final long seed;
    public final Difficulty difficulty;
    public final int party;
    public final List<Integer> yanderes;
    public final Club[] clubs = new Club[Roster.TOTAL];
    public final District[] districts = new District[Roster.TOTAL];
    public final List<Clue> clues;
    /** For each trait clue, which girls it could be true of. */
    private final java.util.Map<Integer, IntPredicate> tests = new java.util.HashMap<>();

    public Mystery(long seed, Difficulty difficulty, int party) {
        this.seed = seed;
        this.difficulty = difficulty;
        this.party = party;
        Random r = new Random(seed);

        int girls = Roster.girlsFor(party);
        assign(r, 0, girls);
        assign(r, Roster.GIRLS, Roster.GIRLS + Roster.boysFor(party));
        List<Integer> shuffled = new ArrayList<>();
        for (int g = 0; g < girls; g++) shuffled.add(g);
        Collections.shuffle(shuffled, r);
        this.yanderes = List.copyOf(shuffled.subList(0, party));
        // Each full profile must be unique, or no set of clues could single her out.
        for (int pick : yanderes) {
            for (int tries = 0; !isUnique(pick) && tries < 200; tries++) {
                clubs[pick] = Club.values()[r.nextInt(Club.values().length)];
                districts[pick] = District.values()[r.nextInt(District.values().length)];
            }
        }
        this.clues = buildClues(r);
    }

    /** Something that's true of somebody, and isn't of her. It sounds exactly like the real thing. */
    String falseRumour(Random r) {
        int yandere = yanderes.get(0), level = difficulty.ordinal();
        List<Integer> others = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(party); g++) if (!isYandere(g)) others.add(g);
        Collections.shuffle(others, r);
        for (int other : others) {
            List<Fact> facts = factsAbout(other);
            Collections.shuffle(facts, r);
            for (Fact f : facts) if (!f.matches.test(yandere)) return f.value.phrase(level);
        }
        return "She doesn't go home after school.";
    }

    public boolean isYandere(int student) {
        return yanderes.contains(student);
    }

    public Clue clue(int id) {
        return clues.get(id);
    }

    /** Could she still be it, given the clues in your bag? With several of them, she only has to fit one's. */
    public boolean stillFits(int girl, java.util.Set<Integer> found) {
        for (int yandere : yanderes) {
            boolean fits = true;
            for (int id : found) {
                if (id >= clues.size() || !tests.containsKey(id) || clues.get(id).about() != yandere) continue;
                if (!tests.get(id).test(girl)) fits = false;
            }
            if (fits) return true;
        }
        return false;
    }

    private void assign(Random r, int from, int to) {
        List<Club> c = new ArrayList<>();
        List<District> d = new ArrayList<>();
        for (int i = from; i < to; i++) {
            c.add(Club.values()[i % Club.values().length]);
            d.add(District.values()[i % District.values().length]);
        }
        Collections.shuffle(c, r);
        Collections.shuffle(d, r);
        for (int i = from; i < to; i++) {
            clubs[i] = c.get(i - from);
            districts[i] = d.get(i - from);
        }
    }

    private boolean isUnique(int girl) {
        for (int i = 0; i < Roster.girlsFor(party); i++) {
            if (i != girl && sameProfile(i, girl)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameProfile(int a, int b) {
        Student x = Roster.get(a), y = Roster.get(b);
        return x.hair() == y.hair() && x.length() == y.length() && x.glasses() == y.glasses()
                && x.homeroom() == y.homeroom() && clubs[a] == clubs[b] && districts[a] == districts[b];
    }

    private List<Fact> factsAbout(int yandere) {
        Student her = Roster.get(yandere);
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact(her.hair(), g -> Roster.get(g).hair() == her.hair()));
        facts.add(new Fact(her.length(), g -> Roster.get(g).length() == her.length()));
        facts.add(new Fact(her.glasses(), g -> Roster.get(g).glasses() == her.glasses()));
        facts.add(new Fact(her.homeroom(), g -> Roster.get(g).homeroom() == her.homeroom()));
        facts.add(new Fact(clubs[yandere], g -> clubs[g] == clubs[yandere]));
        facts.add(new Fact(districts[yandere], g -> districts[g] == districts[yandere]));
        return facts;
    }

    private List<Clue> buildClues(Random r) {
        int level = difficulty.ordinal();
        int girls = Roster.girlsFor(party);
        List<Clue> out = new ArrayList<>();
        out.add(new Clue(0, Kind.NOTE, "A note in your locker: \"I love you.\"", -1, 1, -1));

        List<Integer> witnesses = new ArrayList<>(Roster.active(party));
        witnesses.removeAll(yanderes);
        Collections.shuffle(witnesses, r);
        int w = 0;

        int last = LAST_CLUE_DAY[level];
        for (int y = 0; y < yanderes.size(); y++) {
            int yandere = yanderes.get(y);
            // Pick facts greedily until only she fits; harder difficulties get no spare clues.
            List<Fact> facts = factsAbout(yandere);
            Collections.shuffle(facts, r);
            List<Fact> chosen = new ArrayList<>();
            List<Fact> spare = new ArrayList<>();
            List<Integer> candidates = new ArrayList<>();
            for (int g = 0; g < girls; g++) candidates.add(g);
            for (Fact f : facts) {
                if (candidates.size() > 1 && candidates.stream().anyMatch(g -> !f.matches.test(g))) {
                    chosen.add(f);
                    candidates.removeIf(g -> !f.matches.test(g));
                } else {
                    spare.add(f);
                }
            }
            int extras = switch (difficulty) {
                case EASY, MEDIUM -> spare.size();
                case HARD -> Math.min(1, spare.size());
                case IMPOSSIBLE -> 0;
            };
            chosen.addAll(spare.subList(0, extras));
            Collections.shuffle(chosen, r);

            String which = party > 1 ? WHICH[y] : "";
            for (int i = 0; i < chosen.size(); i++) {
                int first = 2 + level;
                int day = chosen.size() == 1 ? last : first + Math.round(i * (last - first) / (float) (chosen.size() - 1));
                tests.put(out.size(), chosen.get(i).matches);
                out.add(new Clue(out.size(), Kind.TRAIT, which + chosen.get(i).value.phrase(level), witnesses.get(w++), day, yandere));
            }
        }

        // Red herrings: true, suspicious, and about someone innocent.
        List<Integer> innocents = new ArrayList<>();
        for (int g = 0; g < girls; g++) if (!isYandere(g)) innocents.add(g);
        Collections.shuffle(innocents, r);
        List<String> herringText = new ArrayList<>(List.of(HERRINGS));
        Collections.shuffle(herringText, r);
        for (int i = 0; i < level; i++) {
            int about = innocents.get(i);
            String text = String.format(herringText.get(i), Roster.get(about).firstName());
            out.add(new Clue(out.size(), Kind.HERRING, text, witnesses.get(w++), 3 + r.nextInt(last - 2), about));
        }

        // Easy only: every third morning, a gut feeling rules someone out.
        if (difficulty.hints) {
            Collections.shuffle(innocents, r);
            for (int k = 1; k <= 5; k++) {
                int about = innocents.get(k - 1);
                out.add(new Clue(out.size(), Kind.HINT, "Gut feeling: it isn't " + Roster.get(about).firstName() + ".",
                        -1, k * 3, about));
            }
        }
        // Alibis: people swearing for the innocent, to narrow things down.
        List<String> alibis = new ArrayList<>(List.of(ALIBI_LINES));
        Collections.shuffle(alibis, r);
        Collections.shuffle(innocents, r);
        int vouched = Math.min(ALIBIS[level], Math.min(alibis.size(), innocents.size()));
        for (int i = 0; i < vouched && w < witnesses.size(); i++) {
            out.add(new Clue(out.size(), Kind.ALIBI, String.format(alibis.get(i), Roster.get(innocents.get(i)).firstName()),
                    witnesses.get(w++), 2 + r.nextInt(last - 1), innocents.get(i)));
        }

        // And now and then, something new in your locker.
        for (int i = 0; i < LOCKER_NOTES.length; i++) {
            out.add(new Clue(out.size(), Kind.NOTE, LOCKER_NOTES[i], -1, 3 + i * 2, -1));
        }
        return List.copyOf(out);
    }
}
