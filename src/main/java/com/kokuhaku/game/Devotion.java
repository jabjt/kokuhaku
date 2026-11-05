package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * "No One Will Save You." There's no mystery: everyone knows who she is, you
 * included, and you've simply got to last the month. She's with you all day and
 * she has rules. Standing near anyone fills her temper; when it's full she
 * looks at you, and the person you were near doesn't come to school tomorrow.
 * People step out of your way. Nobody meets your eye. Ask for help, and they'll
 * apologise, and then they'll tell her.
 */
final class Devotion {
    /** How many times she'll forgive you, by difficulty. */
    private static final int[] FORGIVES = {4, 3, 2, 1};
    /** How close counts, and how fast her temper rises each half second near someone. */
    private static final double NEAR_GIRL = 4, NEAR_BOY = 2.5;
    private static final float[] RISE = {3f, 4f, 5.5f, 7f};
    private static final float COOL = 0.8f, FULL = 100f, SPOKE = 30f, LOOKED = 6f, WARN_AT = 50f, LAST_WARNING = 80f;
    private static final float LOOK_DOT = 0.97f;
    private static final double LOOK_REACH = 12;
    private static final int CHECK_EVERY = 10, RULE_GIVEN_AT = 120, EARLY_HOME = 18 * 60 + 30, HOME_GRACE = 15, STAY_IN_FROM = 9 * 60;
    private static final Random RANDOM = new Random();

    /** Today's rule. */
    enum Rule {
        HOME_EARLY("Be home with me by " + Schedule.clock(EARLY_HOME) + ". No shifts. No clubs."),
        SAY_IT("Tell me you love me before lunch. I shouldn't have to ask."),
        NO_LOOKING("Keep your eyes on the floor today. Nobody needs you looking at them."),
        NO_SPENDING("You're not to buy anything today. I've got everything you need."),
        STAY_IN("We're staying in today. Just us. You don't need to go out.");

        final String words;

        Rule(String words) {
            this.words = words;
        }
    }

    private static float temper = 0f;
    private static boolean warned, lastWarned, ruleBroken;

    private Devotion() {
    }

    static boolean active(RunState s) {
        return s.devoted;
    }

    static void clear() {
        temper = 0f;
        warned = lastWarned = false;
        ruleBroken = false;
    }

    static int limit(RunState s) {
        return FORGIVES[s.difficulty.ordinal()];
    }

    static int temper() {
        return (int) temper;
    }

    private static Rule rule(RunState s) {
        Random r = new Random(s.seed ^ s.day * 92821L);
        if (!Schedule.isSchoolDay(s.day)) return r.nextBoolean() ? Rule.STAY_IN : Rule.SAY_IT;
        Rule[] weekday = {Rule.HOME_EARLY, Rule.SAY_IT, Rule.NO_LOOKING, Rule.NO_SPENDING};
        return weekday[r.nextInt(weekday.length)];
    }

    static String ruleWords(RunState s) {
        return rule(s).words;
    }

    /** The first thing you see, on the first morning. */
    static void introduce(ServerPlayerEntity p, RunState s, Runnable then) {
        if (Kept.on(s)) {
            Kept.introduce(p, s, then);
            return;
        }
        Student her = Roster.get(s.livingWith);
        Talk.say(p, her.firstName(), "Good morning. You know who I am. Everyone does. I've told them all, and they've all agreed to leave us alone.",
                Talk.option("What do you want?", q -> Talk.say(q, her.firstName(), "Two weeks. You'll stay beside me for two weeks, and you won't go near anyone, and you'll do as I ask. Then we'll see.",
                        Talk.option("And if I tell someone?", r -> Talk.say(r, her.firstName(), "Try it. I'd like you to see for yourself.", Talk.option("...", t -> giveRule(t, s, then)))))));
    }

    /** Every morning, before anything else. */
    static void giveRule(ServerPlayerEntity p, RunState s, Runnable then) {
        ruleBroken = false;
        s.saidIt = false;
        s.markDirty();
        Talk.say(p, Roster.get(s.livingWith).firstName(), rule(s).words, Talk.option("Yes.", q -> then.run()));
    }

    static void saidIt(RunState s) {
        s.saidIt = true;
        s.markDirty();
    }

    /** Buying something, on the day she said not to. */
    static void spent(ServerPlayerEntity p, RunState s) {
        if (active(s) && rule(s) == Rule.NO_SPENDING) broke(p, s);
    }

    private static void broke(ServerPlayerEntity p, RunState s) {
        if (ruleBroken) return;
        ruleBroken = true;
        Kokuhaku.LOG.info("Broke today's rule: {}", rule(s));
        Living.punish(p, s, -1);
    }

    /** Whether you're allowed to be standing this close: anyone sat at a desk is furniture. */
    private static StudentEntity nearest(ServerPlayerEntity p, RunState s) {
        ServerWorld world = p.getServerWorld();
        StudentEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        var box = p.getBoundingBox().expand(NEAR_GIRL);
        for (StudentEntity e : world.getEntitiesByClass(StudentEntity.class, box, e -> e.getSkin() != s.livingWith && e.getPeek() == 0 && !e.isSittingDown() && !e.hasVehicle() && !e.isAiDisabled())) {
            double distance = e.distanceTo(p);
            if (distance > (e.isGirl() ? NEAR_GIRL : NEAR_BOY) || distance >= bestDistance) continue;
            bestDistance = distance;
            best = e;
        }
        return best;
    }

    private static boolean staringAtSomeone(ServerPlayerEntity p, RunState s) {
        Vec3d look = p.getRotationVec(1f), eye = p.getEyePos();
        for (StudentEntity e : p.getServerWorld().getEntitiesByClass(StudentEntity.class, p.getBoundingBox().expand(LOOK_REACH), e -> e.getSkin() != s.livingWith && e.getPeek() == 0)) {
            if (look.dotProduct(e.getEyePos().subtract(eye).normalize()) > LOOK_DOT) return true;
        }
        return false;
    }

    /** Twice a second, while she's got nothing else to do but watch you. */
    static void tick(ServerPlayerEntity p, RunState s, int ticks) {
        if (ticks % CHECK_EVERY != 0 || s.bonk) return;
        Rule today = rule(s);
        if (today == Rule.HOME_EARLY && s.minute >= EARLY_HOME + HOME_GRACE && !RunManager.isHomeNow(p, s)) broke(p, s);
        if (today == Rule.SAY_IT && !s.saidIt && s.minute >= Schedule.LUNCH) broke(p, s);
        if (today == Rule.STAY_IN && s.minute >= STAY_IN_FROM && !RunManager.isHomeNow(p, s)) broke(p, s);
        StudentEntity near = nearest(p, s);
        float change = near != null ? RISE[s.difficulty.ordinal()] : -COOL;
        if (today == Rule.NO_LOOKING && staringAtSomeone(p, s)) change += LOOKED;
        temper = Math.max(0f, temper + change);
        if (temper >= LAST_WARNING && !lastWarned) {
            lastWarned = true;
            RunManager.tell(p, "", "Her hand closes round your arm. She doesn't look at you.");
        } else if (temper >= WARN_AT && !warned) {
            warned = true;
            RunManager.tell(p, "", "She's stopped smiling.");
        }
        if (temper < WARN_AT) warned = lastWarned = false;
        if (temper >= FULL) {
            temper = 0f;
            Kokuhaku.LOG.info("Her temper ran out, standing near {}", near == null ? "nobody" : near.getSkin());
            Living.punish(p, s, near == null || near.getSkin() >= Roster.TOTAL ? -1 : near.getSkin());
        }
    }

    /** You stopped someone. They're terrified. They can't help you, and they'll say so. */
    static void plead(ServerPlayerEntity p, RunState s, StudentEntity who) {
        temper = Math.min(FULL - 1, temper + SPOKE);
        boolean student = who.getSkin() < Roster.TOTAL;
        String name = student ? Roster.get(who.getSkin()).firstName() : "Stranger";
        int victim = student ? who.getSkin() : -1;
        Talk.say(p, name, TERRIFIED.get(RANDOM.nextInt(TERRIFIED.size())), who.getId(),
                Talk.option("Please. Help me.", q -> {
                    Kokuhaku.LOG.info("Begged {} for help", name);
                    Talk.say(q, name, REFUSALS.get(RANDOM.nextInt(REFUSALS.size())), who.getId(), Talk.option("...", r -> Living.punish(r, s, victim)));
                }),
                Talk.leave("Say nothing."));
    }

    /** Nobody at a desk, a counter or a door is going to do anything. */
    static String stranger() {
        return STRANGERS.get(RANDOM.nextInt(STRANGERS.size()));
    }

    private static final List<String> TERRIFIED = List.of(
            "Don't. Please don't talk to me. She's right there.",
            "Why are you doing this to me? I've got a family.",
            "Look at the floor. Look at the floor and walk away.",
            "I can't see you. I can't hear you. I'm sorry.",
            "...Is she looking? Don't turn round. Is she looking?");

    private static final List<String> REFUSALS = List.of(
            "I'm sorry. I'm so sorry. I have to tell her you asked. She said if I didn't...",
            "We all know. We've known for days. Nobody's coming. I'm sorry.",
            "My sister tried. Go home. Go home and do what she says.",
            "The teachers know. The police know. Do you understand? Everybody knows.",
            "She said you'd ask. She told me what to say. I'm sorry.");

    private static final List<String> STRANGERS = List.of(
            "They look straight through you, and keep walking.",
            "\"I can't help you.\" They don't stop.",
            "\"Not my business.\" Their hands are shaking.",
            "They cross the road before you reach them.",
            "\"She said you might try this.\"",
            "Somebody pulls their curtains shut as you go past.");
}
