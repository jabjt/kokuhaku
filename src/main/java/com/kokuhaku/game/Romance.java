package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

/**
 * Asking someone out. It's all through talking to her: get her to like you and
 * you can ask. A girlfriend walks you in, tells you things, and makes the nights
 * safer. She might be the one you're looking for, and nothing she does will
 * tell you. If she isn't, someone's going to notice.
 */
final class Romance {
    /** How much she has to like you before you can ask, and what a date costs. */
    static final int ASK_AT = 2;
    private static final int DATE_YEN = 500, DATE_MINUTES = 120, LATEST_DATE = 60;
    /** From this many nights in, she may decide your girlfriend's in the way. */
    private static final int JEALOUS_AFTER = 5;
    private static final float JEALOUS_CHANCE = 0.12f, SAFER = 0.5f;
    private static final Random RANDOM = new Random();

    private Romance() {
    }

    static boolean canAsk(RunState s, Student who) {
        return who.female() && !Living.active(s) && s.dating < 0 && s.fondness[who.id()] >= ASK_AT;
    }

    static boolean isDating(RunState s, Student who) {
        return s.dating == who.id();
    }

    static boolean datingHer(RunState s) {
        return s.dating >= 0 && s.mystery().isYandere(s.dating);
    }

    /** What your girlfriend does for your nights: she's the one you'd be taken by, or she's the one who walks you home. */
    static float nightChance(RunState s, float chance) {
        if (s.dating < 0) return chance;
        return datingHer(s) ? 0f : chance * SAFER;
    }

    /** Saying something kind: once a day to each girl, and each has one thing she really wants to hear. */
    static final String NICE = "Say something nice.";
    private static final String KIND = "You're really kind.", FUNNY = "You always make me laugh.", HARD_WORKING = "You work so hard.";
    private static final int[] NICE_ON = new int[Roster.TOTAL];
    private static final int FLUSTERED_TICKS = 90;

    static void clear() {
        java.util.Arrays.fill(NICE_ON, 0);
    }

    private static String favourite(Personality p) {
        return switch (p) {
            case SHY, POLITE -> KIND;
            case CHEERFUL, GOSSIP -> FUNNY;
            case COLD, SPORTY -> HARD_WORKING;
        };
    }

    static void compliment(ServerPlayerEntity p, RunState s, Student who, StudentEntity her) {
        String name = who.firstName();
        if (NICE_ON[who.id()] == s.day) {
            Talk.say(p, name, "You already said that. ...You can stop looking so pleased with yourself.", Talk.leave("Sorry."));
            return;
        }
        NICE_ON[who.id()] = s.day;
        String wanted = favourite(who.personality());
        Talk.Option[] options = new Talk.Option[3];
        String[] all = {KIND, FUNNY, HARD_WORKING};
        for (int i = 0; i < all.length; i++) {
            String said = all[i];
            options[i] = Talk.option(said, q -> {
                boolean right = said.equals(wanted);
                s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + (right ? 2 : 1));
                s.markDirty();
                Kokuhaku.LOG.info("Complimented {}: {} ({})", name, said, right ? "flustered" : "polite");
                if (right && her != null) her.fluster(FLUSTERED_TICKS);
                Talk.say(q, name, right ? flustered(who.personality()) : polite(who.personality()), Talk.leave("..."));
            });
        }
        Talk.say(p, name, "...Yes?", options);
    }

    private static String flustered(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Wh- you can't just say that! I'm the one who says things! ...Do I really?";
            case SHY -> "I... n-nobody's ever... um. I have to go. I'm not going. Thank you.";
            case COLD -> "...Don't. I'm not... that isn't... Look somewhere else for a second.";
            case GOSSIP -> "Oh. I... I don't have anything to say to that. That's never happened before.";
            case POLITE -> "That's... very kind of you. I'm afraid I've forgotten what I was going to say.";
            case SPORTY -> "Ha! Yeah, well. Whatever. It's hot out, that's why my face is like this.";
        };
    }

    private static String polite(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Aw, thanks! You're nice!";
            case SHY -> "O-oh. Thank you.";
            case COLD -> "Is that so.";
            case GOSSIP -> "I'll be telling people you said that.";
            case POLITE -> "Thank you. That's kind of you to say.";
            case SPORTY -> "Thanks, I guess!";
        };
    }

    /** The line in her dialogue, if there is one. */
    static String option(RunState s, Student who) {
        if (canAsk(s, who)) return "Will you go out with me?";
        return isDating(s, who) ? "About us..." : null;
    }

    static void talk(ServerPlayerEntity p, RunState s, Student who) {
        if (canAsk(s, who)) ask(p, s, who);
        else if (isDating(s, who)) together(p, s, who);
    }

    private static void ask(ServerPlayerEntity p, RunState s, Student who) {
        s.dating = who.id();
        s.datingSince = s.day;
        s.known.add(who.id());
        s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + 1);
        s.markDirty();
        Kokuhaku.LOG.info("Now going out with {} (yandere: {})", who.firstName(), datingHer(s));
        Talk.say(p, who.firstName(), yes(who.personality()), Talk.leave("..."));
    }

    private static void together(ServerPlayerEntity p, RunState s, Student who) {
        String name = who.firstName();
        boolean free = s.dateOn != s.day && (!Schedule.isSchoolDay(s.day) || s.minute >= Schedule.AFTER_SCHOOL) && s.minute < RunManager.curfewOf(s) - LATEST_DATE;
        if (!free) {
            Sweethearts.talk(p, s, who, s.dateOn == s.day ? "I had a really nice time. Get home safe, okay?" : s.minute < Schedule.AFTER_SCHOOL ? "Hey, you. Quick, before the bell." : "Come and find me later.",
                    Talk.option("I want to break up.", q -> breakUp(q, s, who)), Talk.leave("See you."));
            return;
        }
        Talk.say(p, name, "Do you want to do something? Just us, for a couple of hours.",
                Talk.option("Ramen by the station (¥" + DATE_YEN + ").", q -> {
                    s.yen -= DATE_YEN;
                    date(q, s, who, "You split the gyoza. She steals yours and thinks you don't notice.", 3);
                }),
                Talk.option("Walk along the sea.", q -> date(q, s, who, "The wind takes most of what you say to each other. It doesn't seem to matter.", 2)),
                Talk.option("Sit on the school roof.", q -> date(q, s, who, "She puts her head on your shoulder and counts the lights coming on across town.", 2)),
                Talk.leave("Not today."));
    }

    /** Two hours go by, and at the end of them she trusts you with something. */
    private static void date(ServerPlayerEntity p, RunState s, Student who, String scene, int fondness) {
        s.dateOn = s.day;
        s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + fondness);
        s.markDirty();
        RunManager.skipMinutes(p.server, s, DATE_MINUTES);
        String told = RunManager.askedAboutStrange(s, who);
        RunManager.tellClueFound(p, s, who);
        Talk.say(p, who.firstName(), scene + "\n\nOn the way back she goes quiet. \"Can I tell you something? " + told + "\"", Talk.leave("Thank you."));
    }

    private static void breakUp(ServerPlayerEntity p, RunState s, Student who) {
        s.dating = -1;
        s.fondness[who.id()] = -3;
        s.markDirty();
        Kokuhaku.LOG.info("Broke up with {}", who.firstName());
        Talk.say(p, who.firstName(), datingHerWas(s, who) ? "...No you don't." : "Oh. Okay. I think I knew.", Talk.leave("I'm sorry."));
    }

    private static boolean datingHerWas(RunState s, Student who) {
        return s.mystery().isYandere(who.id());
    }

    /** Night by night. She's seen you with someone else, and it doesn't take her long. */
    static void night(RunState s) {
        if (s.dating < 0) return;
        if (s.dead.contains(s.dating)) {
            s.dating = -1;
            s.markDirty();
            return;
        }
        if (datingHer(s) || s.day - s.datingSince < JEALOUS_AFTER || RANDOM.nextFloat() > JEALOUS_CHANCE) return;
        Kokuhaku.LOG.info("{} has been noticed", Roster.get(s.dating).firstName());
        s.livingVictim = s.dating;
        s.dating = -1;
        s.markDirty();
    }


    private static String yes(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Yes! Yes yes yes! I've been waiting for you to ask, you've got no idea!";
            case SHY -> "M-me? You mean me? ...Yes. Please. Yes.";
            case COLD -> "...I suppose so. Don't look so pleased with yourself.";
            case GOSSIP -> "Oh, everyone's going to be so annoyed. Yes. Obviously yes.";
            case POLITE -> "I would be very happy to. Thank you for asking me properly.";
            case SPORTY -> "Took you long enough. Yeah. Race you to the gate?";
        };
    }
}
