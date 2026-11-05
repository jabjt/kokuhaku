package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * "I'll stay with you." Single player only: you live in her house and she's
 * beside you wherever you go. Talk to another girl and she'll look at you, and
 * you'll look down, and your arm won't stop shaking. Three times and she goes
 * home ahead of you to wait. Last the two weeks and it's one of the secret
 * endings.
 */
public final class Living {
    public static final int STRIKES = 3, MIDNIGHT = 24 * 60;
    private static final int JEALOUS_TICKS = 170, MOOD_MINUTES = 150;
    private static final double BESIDE = 1.3, BEHIND = 0.5, TOO_FAR = 14, GETS_UP_AT = 3.2, SETTLES_AT = 2.2;
    private static final Random RANDOM = new Random();
    private static boolean inScene = false;
    /** Somebody's being asked to leave her chair. */
    private static boolean clearing = false;
    private static final int GETTING_UP = 25;
    private static final double AISLE = 1.5;

    private Living() {
    }

    public static boolean active(RunState s) {
        return s.livingWith >= 0;
    }

    private static StudentEntity her(MinecraftServer server, RunState s) {
        return server.getOverworld().getEntitiesByType(Kokuhaku.STUDENT, e -> e.getSkin() == s.livingWith && e.getY() < 90).stream().findFirst().orElse(null);
    }

    static void clear() {
        inScene = false;
        clearing = false;
    }

    /** You said it. There's no room, no key and no door: you're just hers now. */
    static void begin(ServerPlayerEntity p, RunState s) {
        s.livingWith = s.freeYandere();
        s.jealous = 0;
        s.bonk = false;
        s.markDirty();
        Student who = Roster.get(s.livingWith);
        Kokuhaku.LOG.info("{} stayed with {}", p.getGameProfile().getName(), who.name());
        p.clearStatusEffects();
        if (s.day > Schedule.DAYS) {
            RunManager.endGame(p.server, s, Endings.stayed(s));
            return;
        }
        RunManager.tell(p, "\"I knew you would.\"", who.firstName() + " has already told everyone you've moved in.");
    }

    static void tick(MinecraftServer server, RunState s, List<ServerPlayerEntity> players, int ticks) {
        if (!active(s) || players.isEmpty() || ticks % 10 != 0) return;
        ServerPlayerEntity p = players.get(0);
        if (s.bonk) {
            if (RunManager.isHomeNow(p, s) && !RunManager.scenePlaying()) finish(p, s);
            return;
        }
        if (inScene || WalkScene.active(p) || RunManager.scenePlaying()) return;
        if (s.devoted && !Kept.free(s)) Devotion.tick(p, s, ticks);
        StudentEntity e = her(server, s);
        if (e == null) return;
        if (Kept.on(s)) {
            sitWithYou(p, e);
            Kept.tick(p, s, e);
            return;
        }
        e.setKeptClose(true);
        Vec3d facing = Vec3d.fromPolar(0f, p.getYaw());
        Vec3d spot = p.getPos().add(new Vec3d(-facing.z, 0, facing.x).multiply(BESIDE)).subtract(facing.multiply(BEHIND));
        if (sitWithYou(p, e)) return;
        // She only moves when you've moved away: looking round isn't going anywhere.
        double away = e.distanceTo(p);
        if (away > TOO_FAR || Math.abs(e.getY() - p.getY()) > 3) {
            e.placeAt("beside", p.getPos().subtract(facing.multiply(2)));
        } else if (away > GETS_UP_AT) {
            e.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.35);
        } else {
            if (away < SETTLES_AT) e.getNavigation().stop();
            e.getLookControl().lookAt(p, 30f, 30f);
        }
        if (s.minute % MOOD_MINUTES == 0 && ticks % Schedule.TICKS_PER_MINUTE == 0) {
            RunManager.tell(p, "", moods(Roster.get(s.livingWith).personality())[RANDOM.nextInt(4)]);
        }
    }

    /** You sit, so she sits: the nearest free chair in your row, or she stands there and waits. Returns whether you're sitting. */
    private static boolean sitWithYou(ServerPlayerEntity p, StudentEntity e) {
        if (!p.hasVehicle()) {
            if (e.isSittingDown()) e.standUp();
            Seats.claim(null);
            return false;
        }
        if (!e.isSittingDown() && !clearing) takeChair(p, e);
        return true;
    }

    /** The chair next to you is hers. If someone's in it, they won't be for long. */
    static void takeChair(ServerPlayerEntity p, StudentEntity e) {
        ServerWorld world = p.getServerWorld();
        BlockPos chair = Seats.besideYou(p);
        if (chair == null) {
            e.getNavigation().stop();
            e.getLookControl().lookAt(p, 30f, 30f);
            return;
        }
        float yaw = Seats.facing(world.getBlockState(chair), p.getYaw());
        Seats.claim(chair);
        StudentEntity sitter = Seats.occupant(world, chair);
        if (sitter == null || sitter == e) {
            e.sitOn(chair, yaw);
            return;
        }
        clearing = true;
        Student who = Roster.get(e.getSkin());
        String name = sitter.getSkin() < Roster.TOTAL ? Roster.get(sitter.getSkin()).firstName() : "They";
        Kokuhaku.LOG.info("{} told {} to move", who.firstName(), name);
        RunManager.tell(p, "", who.firstName() + ": " + moveAlong(who.personality(), name) + " " + name + " gets up without a word.");
        sitter.getLookControl().lookAt(e, 30f, 30f);
        RunManager.later(GETTING_UP, () -> sitter.sentAway(new Vec3d(chair.getX() + 0.5, chair.getY(), chair.getZ() + AISLE)));
        RunManager.later(GETTING_UP * 2, () -> {
            clearing = false;
            if (p.hasVehicle()) e.sitOn(chair, yaw);
        });
    }

    private static String moveAlong(Personality p, String name) {
        return switch (p) {
            case CHEERFUL -> "\"Hiii, " + name + "! That's my seat now! Bye!\"";
            case SHY -> "\"...could you not sit there. Please. Please move.\"";
            case COLD -> "\"Move.\"";
            case GOSSIP -> "\"You'd better sit somewhere else. You know what they say about people who sit there.\"";
            case POLITE -> "\"Excuse me. I'm afraid you're in my seat. It's my seat now.\"";
            case SPORTY -> "\"Up. Now. I won't ask twice.\"";
        };
    }

    /** Who's beside you in class: the girl you live with, or the one you're going out with. */
    static StudentEntity partner(ServerPlayerEntity p, RunState s) {
        // Only someone who's keeping you. A girlfriend goes to her own lesson, like anyone else.
        if (!active(s)) return null;
        int id = s.livingWith;
        if (id < 0 || s.bonk || s.dead.contains(id) || s.caught.contains(id)) return null;
        return p.server.getOverworld().getEntitiesByType(Kokuhaku.STUDENT, e -> e.getSkin() == id && e.getY() < 90).stream().findFirst().orElse(null);
    }

    /** What she'd rather hear than the lesson. The first answer is the one she wants. */
    private record Ask(String ask, String good, String bad, String glad, String hurt) {
    }

    private static final List<Ask> ASKS = List.of(
            new Ask("What were you looking at just now?", "You.", "The board.", "I know. I just wanted to hear it.", "The board. I see."),
            new Ask("Are you happy?", "Yes.", "I don't know.", "Good. That's all I wanted.", "You will be. I'll make sure of it."),
            new Ask("Who did you think about this morning?", "You.", "Nobody.", "Me too. You, I mean.", "Nobody. Hm."),
            new Ask("Do you remember what I said to you yesterday?", "Every word.", "Not really.", "Say that again. Quietly.", "I remember everything you say."),
            new Ask("If I wasn't here, would you notice?", "Straight away.", "Probably.", "Good answer.", "Probably."),
            new Ask("What's the first thing you'd do if I left?", "Come and find you.", "I don't know.", "I knew you would.", "You should have an answer to that."),
            new Ask("Why did you sit there, of all the seats?", "To be near you.", "It was free.", "You're sweet when you lie. Or was it true?", "Nothing's free."),
            new Ask("Tell me something nobody else knows about you.", "You know everything already.", "There's nothing to tell.", "I do, don't I.", "There's always something."),
            new Ask("Am I the only one?", "Yes.", "What do you mean?", "Say it like you mean it next time.", "You know what I mean."),
            new Ask("How long can you go without looking away?", "As long as you want.", "Not long.", "Then don't. Keep looking.", "We'll practise."));
    private static final int ASKS_A_LESSON = 3, TOO_MANY_WRONG = 2;

    /** The teacher talks. She doesn't look at the board once. She turns to you, and you can forget the lesson. */
    static void classTime(ServerPlayerEntity p, RunState s, StudentEntity e) {
        Kokuhaku.LOG.info("{} asks the questions this lesson", Roster.get(e.getSkin()).firstName());
        e.getNavigation().stop();
        e.setStaring(true);
        if (p.hasVehicle() && !e.isSittingDown()) takeChair(p, e);
        float yaw = (float) (Math.toDegrees(Math.atan2(e.getZ() - p.getZ(), e.getX() - p.getX())) - 90);
        p.networkHandler.requestTeleport(p.getX(), p.getY(), p.getZ(), yaw, 8f);
        List<Ask> asks = new java.util.ArrayList<>(ASKS);
        java.util.Collections.shuffle(asks, RANDOM);
        RunManager.later(GETTING_UP * 2 + 10, () -> ask(p, s, e, asks.subList(0, ASKS_A_LESSON), 0, 0));
    }

    private static void ask(ServerPlayerEntity p, RunState s, StudentEntity e, List<Ask> asks, int index, int wrong) {
        Student who = Roster.get(e.getSkin());
        if (index >= asks.size()) {
            e.setStaring(false);
            if (!active(s)) Seats.claim(null);
            if (wrong >= TOO_MANY_WRONG) {
                s.fondness[who.id()] = Math.max(-5, s.fondness[who.id()] - 1);
                s.markDirty();
                if (active(s)) {
                    punish(p, s, -1);
                    RunManager.later(JEALOUS_TICKS, () -> RunManager.classOver(p, s));
                    return;
                }
            }
            RunManager.classOver(p, s);
            return;
        }
        Ask q = asks.get(index);
        String name = who.firstName();
        Talk.say(p, name, q.ask(), e.getId(),
                Talk.option(q.good(), r -> Talk.say(r, name, q.glad(), e.getId(), Talk.option("...", t -> ask(t, s, e, asks, index + 1, wrong)))),
                Talk.option(q.bad(), r -> Talk.say(r, name, q.hurt(), e.getId(), Talk.option("...", t -> ask(t, s, e, asks, index + 1, wrong + 1)))));
    }

    /**
     * You spoke to another girl. Returns true if she saw, which is always: you
     * never get to say a word, and that girl's name goes on tonight's list.
     */
    static boolean caught(ServerPlayerEntity p, RunState s, Student other) {
        if (!active(s) || !other.female() || other.id() == s.livingWith) return false;
        punish(p, s, other.id());
        return true;
    }

    /** How many times she'll let it go. */
    static int limit(RunState s) {
        return s.devoted ? Devotion.limit(s) : STRIKES;
    }

    /** She looks at you. Whoever you were with, if anyone, is on tonight's list. */
    static void punish(ServerPlayerEntity p, RunState s, int victim) {
        if (inScene || s.bonk) return;
        StudentEntity e = her(p.server, s);
        if (e == null) return;
        inScene = true;
        if (!Kept.on(s)) s.jealous++;
        if (victim >= 0) s.livingVictim = victim;
        s.markDirty();
        Personality manner = Roster.get(s.livingWith).personality();
        Kokuhaku.LOG.info("Caught: strike {} of {}", s.jealous, limit(s));
        e.getNavigation().stop();
        e.setAiDisabled(true);
        Vec3d facing = Vec3d.fromPolar(0f, p.getYaw());
        Vec3d side = new Vec3d(-facing.z, 0, facing.x);
        Vec3d at = p.getPos().add(side.multiply(BESIDE)).add(facing.multiply(0.6));
        float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - at.z, p.getX() - at.x)) - 90);
        e.placeAt("beside", at);
        e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        RunManager.sendJealous(p, at.add(0, 1.55, 0), JEALOUS_TICKS, warning(manner, Kept.on(s) ? Kept.fractures() : s.jealous - 1), s.jealous, s.livingWith);
        RunManager.later(JEALOUS_TICKS, () -> {
            inScene = false;
            e.setAiDisabled(false);
            if (Kept.on(s)) {
                Kept.broken(p, s);
                return;
            }
            if (s.jealous >= limit(s)) {
                s.bonk = true;
                s.markDirty();
                e.setKeptClose(false);
                e.placeAt("home", Routines.homePos(s.livingWith, s, RunManager.town()));
                RunManager.tell(p, "", "When you look up, she's gone. She'll be waiting at home.");
            }
        });
    }

    /** Midnight: dragged home if there's a chance left, and the end of it if not. */
    static void midnight(ServerPlayerEntity p, RunState s) {
        if (s.bonk) {
            finish(p, s);
            return;
        }
        if (!RunManager.isHomeNow(p, s)) {
            RunManager.tell(p, "", "Her nails are in your arm. She doesn't say anything all the way home.");
            s.jealous++;
            s.markDirty();
            if (s.jealous >= limit(s)) {
                s.bonk = true;
                finish(p, s);
                return;
            }
        }
        RunManager.sleepNow(p.server, s);
    }

    private static void finish(ServerPlayerEntity p, RunState s) {
        Kokuhaku.LOG.info("Three strikes: the bat");
        RunManager.finalBlow(p, s);
    }

    /** Talking to her is always allowed. It's nearly always what she wants. */
    static void chat(ServerPlayerEntity p, RunState s, StudentEntity e) {
        Student who = Roster.get(s.livingWith);
        String[] lines = moods(who.personality());
        String name = who.firstName();
        List<Talk.Option> options = new java.util.ArrayList<>();
        options.add(Talk.option("I love you.", q -> {
            s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + 1);
            Devotion.saidIt(s);
            Talk.say(q, name, "I know. Say it again tonight.", Talk.leave("..."));
        }));
        options.add(Talk.option("Can I go out by myself?", q -> Talk.say(q, name, "Why would you want to do that?", Talk.leave("No reason."))));
        if (Kept.canPourTea(s, p)) options.add(Kept.pourTea(s));
        options.add(Talk.leave("Say nothing."));
        Talk.say(p, name, lines[RANDOM.nextInt(lines.length)], options.toArray(new Talk.Option[0]));
    }

    public static String[] moods(Personality p) {
        return switch (p) {
            case CHEERFUL -> new String[]{"She hums and swings your hand, all the way down the street.", "\"Isn't this the best? Every day! Forever!\"", "She tells a passing stranger you're engaged. You aren't.", "\"Smile! You're so much cuter when you smile. Smile.\""};
            case SHY -> new String[]{"She's holding your sleeve so tightly her knuckles are white.", "\"...You won't leave, will you? You said.\"", "You catch her writing down what time you blinked.", "\"I-I counted how many people looked at you today. I'll take care of it.\""};
            case COLD -> new String[]{"She hasn't said a word in an hour. She hasn't looked away, either.", "\"Stand up straight.\"", "She straightens your collar, and leaves her hand on your throat a moment.", "\"You're mine. It isn't a compliment. It's a fact.\""};
            case GOSSIP -> new String[]{"\"Everyone's so jealous of us. I made sure of it.\"", "She reads out what you said in your sleep, in front of everyone.", "\"I told them all about you. Everything. There's nobody left to tell.\"", "\"Don't worry about what they're saying. I wrote it.\""};
            case POLITE -> new String[]{"She's packed your bag, your lunch, and tomorrow's lunch.", "\"Please don't look out of the window. It isn't good for you.\"", "\"I've thrown out your old clothes. You won't be needing them.\"", "\"I've told your parents you're happy. Aren't you?\""};
            case SPORTY -> new String[]{"She's keeping pace half a step behind you. She's not even breathing hard.", "\"Don't run. I'd like you to, but don't.\"", "\"Your resting heart rate's up. Are you scared? That's good for you.\"", "She's timing how long it takes you to get to the door."};
        };
    }

    /** What she says, once she's looking at you. It gets quieter each time, whoever she is. */
    private static String warning(Personality p, int strike) {
        String[][] lines = switch (p) {
            case CHEERFUL -> new String[][]{{"Who's that? Is she a friend? You don't need friends!"}, {"Hahaha. Haha. Don't do that again. Haha."}, {"I'm still smiling. Look. I'm still smiling."}};
            case SHY -> new String[][]{{"...I-I saw that."}, {"You said it was just me. You said."}, {"...okay."}};
            case COLD -> new String[][]{{"That's one."}, {"That's two."}, {"Go home."}};
            case GOSSIP -> new String[][]{{"Ooh. Everyone's going to hear about her by morning."}, {"Do you know what happens to people I talk about?"}, {"I've run out of things to say about you."}};
            case POLITE -> new String[][]{{"Pardon me. I don't believe she's anyone you need to know."}, {"Please don't embarrass me again. I do so hate asking twice."}, {"I'm afraid that's quite enough."}};
            case SPORTY -> new String[][]{{"Strike one, champ."}, {"Strike two. You know how this game goes."}, {"You're out."}};
        };
        return "\"" + lines[Math.max(0, Math.min(strike, 2))][0] + "\"";
    }
}
