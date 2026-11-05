package com.kokuhaku.game;

import com.kokuhaku.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** What the girls ask you. Each has a new question every thirty minutes of the clock. */
public final class Questions {
    public enum Reward { NONE, PROFILE, SLEEPOVER }

    /** onYes and onNo are changes in fondness; from and to limit it to a time of day. */
    public record Question(String ask, String yes, String no, int onYes, int onNo, Reward reward, int from, int to) {
    }

    public static final List<String> ANSWERS = List.of("Yes.", "No.", "Goodbye.");
    private static final int WINDOW = 30, DAY_START = 0, DAY_END = 24 * 60;
    /** You need time to get there before curfew. */
    private static final int LAST_INVITE = 60;

    private static final List<Question> ALL = List.of(
            any("Do you ever walk home alone?", "You should be careful. People notice that.", "Good. Stay with someone.", 0, 1),
            any("Have you got someone you like?", "Oh! ...Oh. Right. Well. Good for you.", "Really? Huh.", -1, 1),
            any("Do you think I talk too much?", "That's... honest of you.", "Thanks. People say I do.", -1, 1),
            any("Is it true you're new around here?", "Thought so. You still look at the signs.", "Weird. I don't remember you.", 0, 0),
            any("Do you believe in ghosts?", "Me too. I don't like the east stairs.", "Must be nice.", 1, 0),
            any("Would you tell someone if you were scared?", "...Yeah. You seem like you would.", "Yeah. Me neither.", 1, 1),
            any("Do you think you can really know a person?", "I hope you're right.", "No. Probably not.", 0, 0),
            profile("Want to hear something about me? Nobody ever asks.", "It's not much, but okay. Sit down a second.", "Right. Of course.", 1, -1),
            at("Did you sleep okay last night?", "Lucky.", "You look it.", 0, 1, DAY_START, Schedule.HOMEROOM + 60),
            at("Did you do the reading for today?", "Can I copy it? Please?", "Thank god, me neither.", 1, 0, DAY_START, Schedule.LUNCH),
            at("Have you eaten? I made too much.", "Here. Don't make that face, it's good.", "Oh. Okay. More for me.", 1, -1, Schedule.LUNCH - 30, Schedule.LUNCH + 60),
            at("Can I sit with you?", "Thanks. Everyone's being strange today.", "Oh. Sure. No, it's fine.", 2, -2, Schedule.LUNCH - 30, Schedule.LUNCH + 60),
            at("Are you staying for clubs?", "You should come and watch ours.", "Lucky. See you tomorrow.", 1, 0, Schedule.AFTER_SCHOOL, Schedule.AFTER_SCHOOL + 120),
            at("Could you walk me to the station?", "Thank you. I really mean that.", "I'll be fine. Probably.", 2, -1, Schedule.AFTER_SCHOOL, DAY_END),
            at("Do you want to go somewhere this weekend?", "Really? ...I'll think of somewhere.", "It's fine. I was only asking.", 2, -2, Schedule.AFTER_SCHOOL, DAY_END),
            new Question("My parents are away tonight. Do you want to sleep over at mine?", "", "Oh. Okay. Never mind, forget I said it.", 0, -1, Reward.SLEEPOVER, Schedule.AFTER_SCHOOL, DAY_END)
    );

    private Questions() {
    }

    private static Question any(String ask, String yes, String no, int onYes, int onNo) {
        return at(ask, yes, no, onYes, onNo, DAY_START, DAY_END);
    }

    private static Question at(String ask, String yes, String no, int onYes, int onNo, int from, int to) {
        return new Question(ask, yes, no, onYes, onNo, Reward.NONE, from, to);
    }

    private static Question profile(String ask, String yes, String no, int onYes, int onNo) {
        return new Question(ask, yes, no, onYes, onNo, Reward.PROFILE, DAY_START, DAY_END);
    }

    public static int window(int day, int minute) {
        return day * (DAY_END / WINDOW) + minute / WINDOW;
    }

    /** The same girl asks the same thing until the half hour turns. */
    public static Question pick(long seed, int day, int minute, int girl, int curfew, boolean invited) {
        List<Question> open = new ArrayList<>();
        for (Question q : ALL) {
            if (minute < q.from() || minute >= q.to()) continue;
            if (q.reward() == Reward.SLEEPOVER && (invited || minute >= curfew - LAST_INVITE)) continue;
            open.add(q);
        }
        if (System.getenv("KOKUHAKU_SLEEPOVER") != null) open.removeIf(q -> q.reward() != Reward.SLEEPOVER);
        if (open.isEmpty()) return null;
        Random r = new Random(seed ^ window(day, minute) * 0x9E3779B97F4A7C15L ^ girl * 0xC2B2AE3D27D4EB4FL);
        r.nextLong();
        return open.get(r.nextInt(open.size()));
    }

    /** She doesn't take either answer the way anyone else would, when she's easy to read. */
    public static String reply(Question q, boolean yes, boolean isHer, Difficulty d) {
        if (isHer && d == Difficulty.EASY) return yes ? "I knew you'd say that. I know you so well." : "...Is that so. I'll remember you said that.";
        if (isHer && d == Difficulty.MEDIUM && !yes) return "No? ...That's okay. You'll change your mind.";
        return yes ? q.yes() : q.no();
    }
}
