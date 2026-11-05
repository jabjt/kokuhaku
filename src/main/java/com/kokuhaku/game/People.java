package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everyone remembers you. Whether you've met, what you were asking about, how
 * long it's been, and whether you've been bothering them, and they've got lives
 * of their own: friends they've lost, and days they can't face coming in.
 */
final class People {
    private static final int PESTERING_WITHIN = 25, FORGOTTEN_AFTER = 6, GRIEF_DAYS = 3, AFRAID_FROM = 7, MANY_DEAD = 3;
    private static final int ABSENT_IN_100 = 3, MORE_FOR_EACH_DEATH = 2, MOST_ABSENT = 14;
    private static final float REMEMBERS = 0.55f;
    private static final Random RANDOM = new Random();

    private static final class Memory {
        int talks, lastDay, lastMinute, pestered;
        boolean askedAround;
    }

    private static final Map<Integer, Memory> memories = new HashMap<>();
    /** Who died, and the day they did. */
    private static final Map<Integer, Integer> deaths = new HashMap<>();

    private static final Map<Personality, List<String>> FIRST_MEETING = Map.of(
            Personality.CHEERFUL, List.of("Oh! I don't think we've ever really talked! I'm %s!", "Finally! You're the one who never says hi. I'm %s."),
            Personality.SHY, List.of("Oh. Um. I'm %s. You probably didn't know that.", "...You know my name? No? It's %s. Sorry."),
            Personality.COLD, List.of("We don't talk. Why are we talking? I'm %s, if you need that for something.", "%s. Now you know. What do you want?"),
            Personality.GOSSIP, List.of("Well, well. I know all about you. I'm %s, in case nobody's warned you.", "%s. Remember it. Everyone does."),
            Personality.POLITE, List.of("I don't believe we've been introduced. I'm %s. It's nice to meet you.", "Pleased to meet you. I'm %s."),
            Personality.SPORTY, List.of("Hey, new face! Well, not new. I'm %s.", "I'm %s. You're the one who walks everywhere so slowly."));

    private static final Map<Personality, List<String>> PESTERED = Map.of(
            Personality.CHEERFUL, List.of("You again! Twice! This is a record.", "Hi again! Did you forget something?"),
            Personality.SHY, List.of("...Did I do something wrong?", "S-sorry, was there something else?"),
            Personality.COLD, List.of("We just did this.", "Still here?"),
            Personality.GOSSIP, List.of("Back already? People will talk. I'll make sure of it.", "Twice in one hour. Should I be flattered?"),
            Personality.POLITE, List.of("Was there something else I could help with?", "Hello again. Did you need something more?"),
            Personality.SPORTY, List.of("Hey again. Didn't we just...?", "You're keeping me from practice, you know."));

    private static final Map<Personality, String> FED_UP = Map.of(
            Personality.CHEERFUL, "Okay, even I need a minute. Go on. Shoo.",
            Personality.SHY, "Please. People are looking.",
            Personality.COLD, "Go away.",
            Personality.GOSSIP, "You know what? I've run out of things to say. Mark the date.",
            Personality.POLITE, "I'm sorry, I really must get on.",
            Personality.SPORTY, "Dude. Space.");

    private static final List<String> ACCUSED = List.of("I know what you told them about me.", "You said it was me. In front of everyone.", "I don't know why you're talking to me. After what you said.");
    private static final List<String> GRIEF = List.of("I keep saving %s a seat. I know. I know.", "%s was my friend. I don't really want to talk.", "I had a message from %s the night before. I still haven't read it.", "We were meant to go somewhere on Sunday. Me and %s.");
    private static final List<String> ASKED_YESTERDAY = List.of("Did you ever find out what you were asking about?", "You were asking me things yesterday. It's been bothering me.", "I thought about what you said yesterday.");
    private static final List<String> TALKED_YESTERDAY = List.of("You again! Good. Yesterday was nice.", "Hey. Sleep any better?", "Oh, hi. I was hoping I'd see you.");
    private static final List<String> LONG_TIME = List.of("It's been ages. I thought you were avoiding me.", "Oh. I'd started to think you'd left.", "Where've you been? Nobody tells me anything.");
    private static final List<String> IN_CLASS = List.of("Shh. Not now. After class.", "(whispering) They're looking right at us.", "(whispering) Can it wait? I'm trying to copy this down.", "(without looking up) What?");
    private static final List<String> AT_LUNCH = List.of("(through a mouthful) Hang on.", "Want some? No? More for me.", "This is the only ten minutes I get all day.");

    private People() {
    }

    static void reset() {
        memories.clear();
        deaths.clear();
    }

    static void died(int id, int day) {
        deaths.put(id, day);
    }

    private static String pick(List<String> lines) {
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    /** The friend they've just lost, if they have. */
    private static String lost(RunState s, Student who) {
        String name = null;
        int group = Routines.group(who);
        for (Map.Entry<Integer, Integer> d : deaths.entrySet()) {
            if (d.getKey() != who.id() && Routines.group(Roster.get(d.getKey())) == group && s.day - d.getValue() <= GRIEF_DAYS) name = Roster.get(d.getKey()).firstName();
        }
        return name;
    }

    static boolean grieving(RunState s, int id) {
        return id < Roster.TOTAL && lost(s, Roster.get(id)) != null;
    }

    /** How they're carrying themselves today. */
    static int mood(RunState s, int id) {
        if (grieving(s, id)) return StudentEntity.GRIEVING;
        return s.day >= AFRAID_FROM || s.dead.size() >= MANY_DEAD ? StudentEntity.AFRAID : StudentEntity.AT_EASE;
    }

    /** Off sick, or can't face it. She's never off: she wouldn't miss a day of you. */
    static boolean stayingHome(RunState s, int id) {
        if (id >= Roster.TOTAL || !Schedule.isSchoolDay(s.day) || s.mystery().isYandere(id) || Festival.on(s)) return false;
        Random today = new Random(s.seed ^ s.day * 15485863L ^ id * 32452843L);
        int chance = Math.min(MOST_ABSENT, ABSENT_IN_100 + s.dead.size() * MORE_FOR_EACH_DEATH);
        return today.nextInt(100) < (grieving(s, id) ? chance * 8 : chance);
    }

    /** School days they've missed so far. She's never missed one. */
    static int daysOff(RunState s, int id) {
        int off = 0, today = s.day;
        for (s.day = 1; s.day < today; s.day++) if (stayingHome(s, id)) off++;
        s.day = today;
        return off;
    }

    /** What they say when you walk up to them: what they remember, or else whatever they'd have said anyway. */
    static String greet(RunState s, Student who, boolean isHer, boolean sitting, String usual) {
        Memory m = memories.computeIfAbsent(who.id(), k -> new Memory());
        boolean first = m.talks == 0, today = m.lastDay == s.day, recent = today && s.minute - m.lastMinute < PESTERING_WITHIN;
        int since = s.day - m.lastDay;
        m.pestered = recent ? m.pestered + 1 : 0;
        m.talks++;
        m.lastDay = s.day;
        m.lastMinute = s.minute;
        if (isHer) return usual;
        if (m.pestered >= 3) return FED_UP.get(who.personality());
        if (m.pestered >= 1) return pick(PESTERED.get(who.personality()));
        if (s.accused.contains(who.id())) return pick(ACCUSED);
        String friend = lost(s, who);
        if (friend != null) return String.format(pick(GRIEF), friend);
        if (first) return String.format(pick(FIRST_MEETING.get(who.personality())), who.firstName());
        if (sitting) return pick(IN_CLASS);
        if (s.minute >= Schedule.LUNCH && s.minute < Schedule.PERIOD_START[4] && RANDOM.nextFloat() < REMEMBERS) return pick(AT_LUNCH);
        if (since >= FORGOTTEN_AFTER && RANDOM.nextFloat() < REMEMBERS) return pick(LONG_TIME);
        if (since == 1 && RANDOM.nextFloat() < REMEMBERS) {
            boolean asked = m.askedAround;
            m.askedAround = false;
            return pick(asked ? ASKED_YESTERDAY : TALKED_YESTERDAY);
        }
        return usual;
    }

    /** You went fishing for what they know. They'll remember that tomorrow. */
    static void askedAround(int id) {
        memories.computeIfAbsent(id, k -> new Memory()).askedAround = true;
    }
}
