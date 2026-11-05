package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.world.town.Town;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * "No One Can Save You": No One Will Save You, on Impossible. She doesn't walk
 * beside you here. She leads you, everywhere, by the wrist. Stray and she drags
 * you back and something breaks. Stand still and she takes you somewhere and
 * hours go missing. Lasting the two weeks isn't winning: nobody's coming on the
 * last morning. The only way to win is out.
 *
 * Haruka tried it first, and got most of the way, and the plan's in her diary.
 * The pages are wherever she happens to lead you. In order, it takes: a break or
 * two, for a trip to the hospital and the moment she lets go of you there; the
 * tablets from the records room; money kept from her in your shoe; a can of tea;
 * days of doing exactly as you're told, so she'll drink it; the word from the
 * rock past the beach; and the last train, at three in the morning.
 */
final class Kept {
    private static final int FRACTURES_TO_END = 5, BREAKS_FOR_HOSPITAL = 2;
    /** Checks, at two a second. */
    private static final int STILL_CHECKS = 40, STRAY_CHECKS = 6, HOSPITAL_CHECKS = 70;
    private static final double TETHER = 9, WAITS_AT = 5, MOVED = 1.5;
    private static final int LOST_HOURS = 120, DRAG_TICKS = 70, TAKEN_TICKS = 90;
    /** Pocket money every morning, gone every night unless it's hidden; what the tea costs. */
    private static final int LUNCH_MONEY = 60, TEA = Drink.GREEN_TEA.price;
    private static final int TRUST_DAYS = 3, TEA_FROM = 20 * 60, ASLEEP_AT = 24 * 60 + 30, WAKES_AT = 28 * 60;
    private static final int TRAIN_FROM = 26 * 60 + 50, TRAIN_UNTIL = 27 * 60 + 10;
    private static final int NIGHT_OUT = 30 * 60;
    private static final int STATION_WEST = -712, STATION_EAST = -684, STATION_NORTH = -84, STATION_SOUTH = -28;
    private static final String[] ERRANDS = {"center:Library", "center:Darkroom", "station", "beach", "park"};
    private static final String WORD = "Trapped";
    private static final Random RANDOM = new Random();

    private static final List<String> SHE_SAYS = List.of(
            "\"Don't watch. It's better if you don't watch.\"",
            "\"Crying won't change it. We're family now.\"",
            "\"I set every one of these myself. I've got good at it.\"",
            "\"You have to love me. It isn't a question.\"",
            "\"I won't need to do this once you've learned. You will learn.\"",
            "\"Hold still. There. That's what family's for.\"");

    private static final String[] MARGINS = {
            "Haruka's written in the margin: \"She doesn't sleep. She only ever slept once: the tablets from the hospital records room, in her tea. Green tea. Nothing else hides it.\"",
            "In the margin: \"She only lets go of you in one place. At the hospital desk, signing you in. She only takes you if you've been broken twice. I'm sorry.\"",
            "In the margin: \"There's a train after the last train. Three in the morning, Chinetsu Station. It only stops for someone who knows the word at the gate.\"",
            "In the margin: \"The word's on the rock past the beach. It's what she made of me. It's the name of the whole thing.\"",
            "In the margin: \"She goes through your pockets every night. She doesn't go through your shoes. Your shoe locker's the only place in this town that's yours. And she won't drink from your hand until you've been good for three days running. Not one broken rule.\""};

    private static int fractures = 0, still = 0, stray = 0, atDesk = 0, trustSince = 1, stashed = 0;
    private static boolean tablets = false, teaCan = false, asleep = false, rockRead = false, hospitalDue = false, deskUsed = false, leading = false;
    private static Vec3d last = Vec3d.ZERO;
    private static String goal = "";

    private Kept() {
    }

    static void clear() {
        fractures = still = stray = atDesk = stashed = 0;
        trustSince = 1;
        tablets = teaCan = asleep = rockRead = hospitalDue = deskUsed = leading = false;
        last = Vec3d.ZERO;
        goal = "";
    }

    static boolean on(RunState s) {
        return s.devoted && s.difficulty == Difficulty.IMPOSSIBLE;
    }

    /** While she's out cold, the night's your own. */
    static int curfew(RunState s, int otherwise) {
        return on(s) && asleep ? NIGHT_OUT : otherwise;
    }

    static boolean free(RunState s) {
        return on(s) && asleep;
    }

    static int fractures() {
        return fractures;
    }

    static void introduce(ServerPlayerEntity p, RunState s, Runnable then) {
        String name = Roster.get(s.livingWith).firstName();
        Talk.say(p, name, "There you are. Give me your hand. No, the other one. You don't need to know where we're going. I do.",
                Talk.option("Let go of me.", q -> Talk.say(q, name, "No. Here's how this works. You walk where I walk. If you pull away, something breaks. If you stop, I'll take you somewhere you can't. Two weeks, and then you'll stop asking.",
                        Talk.option("And after two weeks?", r -> Talk.say(r, name, "There isn't an after. We're family now.", Talk.option("...", t -> Devotion.giveRule(t, s, then)))))));
    }

    // ---------------------------------------------------------------- leading

    /** Where she's taking you next. It's always somewhere, and you never get a say. */
    private static int destination(ServerPlayerEntity p, RunState s, Town town) {
        int m = s.minute;
        int home = town.homes.get(s.homes[s.livingWith]).inside();
        if (Schedule.isSchoolDay(s.day) && m < Schedule.AFTER_SCHOOL && m >= Schedule.WAKE + 60) {
            List<String> subjects = Schedule.subjects(s.seed, Schedule.weekday(s.day));
            for (int i = 0; i < Schedule.PERIOD_START.length; i++) {
                if (m > Schedule.PERIOD_START[i] + Schedule.PERIOD_LENGTH) continue;
                String subject = subjects.get(i);
                if (Schedule.practical(subject, s.day)) return town.place(Schedule.practicalPlace(subject));
                int room = Schedule.roomNumber(subject);
                return town.place(room == 0 ? Schedule.swimming(s.day) ? "pool" : "gym" : "center:" + Schedule.roomKey(room));
            }
        }
        boolean errands = m >= Schedule.AFTER_SCHOOL || !Schedule.isSchoolDay(s.day) && m >= Schedule.WAKE + 150;
        if (!errands || m >= Schedule.EVENING) return home;
        if (hospitalDue) return town.place("hospital");
        // The same round, in an order only she knows.
        List<String> round = new ArrayList<>(List.of(ERRANDS));
        Collections.shuffle(round, new Random(s.seed ^ 0x4B455054L));
        return town.place(round.get((s.day + (m - Schedule.AFTER_SCHOOL) / 90) % round.size()));
    }

    private static void lead(ServerPlayerEntity p, RunState s, StudentEntity e, Town town) {
        e.setKeptClose(true);
        int to = destination(p, s, town);
        String key = "kept@" + to;
        if (!key.equals(goal) || !e.hasRoute() && e.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(town.graph.pos(to))) > 9) {
            goal = key;
            e.setRoute(key, WalkRoute.routeFor(e, town, to), null, Float.NaN);
        }
        // She stops and waits, with her hand held out. Not for long.
        e.setHeld(e.distanceTo(p) > WAITS_AT);
    }

    private static boolean busy(ServerPlayerEntity p) {
        return p.hasVehicle() || Talk.waiting(p) || RunManager.inScene(p) || RunManager.inLesson(p) || WalkScene.active(p);
    }

    /** Twice a second. */
    static void tick(ServerPlayerEntity p, RunState s, StudentEntity e) {
        Town town = RunManager.town();
        if (asleep) {
            e.setHeld(true);
            night(p, s);
            return;
        }
        // The fractures don't heal. You feel every one of them.
        if (fractures > 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, Math.min(2, fractures - 1), false, false, false));
        lead(p, s, e, town);
        if (busy(p)) {
            still = stray = 0;
            last = p.getPos();
            return;
        }
        if (hospitalDue && !deskUsed && p.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(town.graph.pos(town.place("hospital")))) < 400) {
            atDesk = HOSPITAL_CHECKS;
            deskUsed = true;
            hospitalDue = false;
            RunManager.tell(p, "", "She lets go of your wrist to sign the form. It's the first time in days. She's not looking.");
            Kokuhaku.LOG.info("She's let go, at the hospital desk");
        }
        if (atDesk > 0) {
            if (--atDesk == 0) RunManager.tell(p, "", "\"All done.\" Her hand finds your wrist again.");
            return;
        }
        double away = e.distanceTo(p);
        stray = away > TETHER ? stray + 1 : 0;
        if (stray == STRAY_CHECKS / 2) RunManager.tell(p, "", "She's stopped. She's holding out her hand.");
        if (stray >= STRAY_CHECKS) {
            stray = 0;
            drag(p, s, e);
            return;
        }
        still = !RunManager.isHomeNow(p, s) && p.getPos().squaredDistanceTo(last) < MOVED * MOVED ? still + 1 : 0;
        if (still == 0) last = p.getPos();
        if (still == STILL_CHECKS * 3 / 4) RunManager.tell(p, "", "\"You've stopped. Why have you stopped?\"");
        if (still >= STILL_CHECKS) {
            still = 0;
            takeAway(p, s, e, town);
        }
    }

    /** You pulled away. She brings you back, and you don't get it all back. */
    private static void drag(ServerPlayerEntity p, RunState s, StudentEntity e) {
        Kokuhaku.LOG.info("Dragged back: {} of {}", fractures + 1, FRACTURES_TO_END);
        RunManager.setBusy(p, true);
        RunManager.sendBlack(p, RunManager.homeFor(p, s), DRAG_TICKS, "She has you by the wrist before you've gone ten steps.\n\n" + SHE_SAYS.get(RANDOM.nextInt(SHE_SAYS.size())));
        RunManager.later(10, () -> p.teleport(p.getServerWorld(), e.getX(), e.getY(), e.getZ(), p.getYaw(), 0f));
        RunManager.later(DRAG_TICKS, () -> {
            RunManager.setBusy(p, false);
            fracture(p, s);
        });
    }

    /** One more thing broken. */
    static void fracture(ServerPlayerEntity p, RunState s) {
        fractures++;
        trustSince = s.day + 1;
        s.fondness[s.livingWith] = Math.max(-5, s.fondness[s.livingWith] - 1);
        s.markDirty();
        if (fractures >= FRACTURES_TO_END) {
            RunManager.finalBlow(p, s);
            return;
        }
        if (fractures % BREAKS_FOR_HOSPITAL == 0) {
            hospitalDue = true;
            deskUsed = false;
            RunManager.tell(p, "", "It's swollen badly. \"We'd better get that looked at,\" she says. She sounds pleased.");
        } else {
            RunManager.tell(p, "", "Something's broken. Every step's slower now. That's " + fractures + " of " + FRACTURES_TO_END + ".");
        }
    }

    /** You stopped, so she decides. It goes dark, and when it comes back it's hours later and you're in her room. */
    private static void takeAway(ServerPlayerEntity p, RunState s, StudentEntity e, Town town) {
        Kokuhaku.LOG.info("Stood still too long: taken home for {} minutes", LOST_HOURS);
        trustSince = s.day + 1;
        RunManager.setBusy(p, true);
        RunManager.sendBlack(p, RunManager.homeFor(p, s), TAKEN_TICKS, "An arm goes round you and you're walking, without having decided to.\n\nYou don't remember the next two hours.");
        RunManager.later(TAKEN_TICKS / 2, () -> {
            Vec3d in = Vec3d.ofBottomCenter(town.graph.pos(RunManager.homeFor(p, s).inside()));
            p.teleport(p.getServerWorld(), in.x, in.y, in.z, p.getYaw(), 0f);
            e.placeAt("beside", in.add(1, 0, 0));
            goal = "";
            RunManager.skipMinutes(p.server, s, LOST_HOURS);
        });
        RunManager.later(TAKEN_TICKS, () -> RunManager.setBusy(p, false));
    }

    // -------------------------------------------------------------- the plan

    /** A rule broken: she doesn't forgive, she just starts counting again. */
    static void broken(ServerPlayerEntity p, RunState s) {
        fracture(p, s);
    }

    /** Every morning: yesterday's pockets are gone, and there's a little for lunch. */
    static void morning(RunState s) {
        s.yen = LUNCH_MONEY;
        s.markDirty();
    }

    /** What she finds. */
    static void nightSearch(RunState s) {
        if (s.yen > 0) Shadow.saw(s, "She went through your pockets while you slept, and counted what she found twice.");
        s.yen = 0;
        s.markDirty();
    }

    /** A page in her mode has more on it than a page. */
    static void pageFound(ServerPlayerEntity p, RunState s, int page) {
        if (page >= MARGINS.length) return;
        s.notes.add(MARGINS[page]);
        s.markDirty();
        RunManager.tell(p, "", "There's writing in the margin. It's in your bag.");
    }

    /** The records room: the same shelf as her file. */
    static void fileFound(ServerPlayerEntity p, RunState s) {
        Talk.say(p, "On the shelf", "Behind the file there's a brown bottle with Haruka's name on the label. It's still half full.",
                Talk.option("Put it in your sock.", q -> {
                    tablets = true;
                    s.notes.add("A bottle of Haruka's sleeping tablets, in your sock.");
                    s.markDirty();
                    Kokuhaku.LOG.info("Got the tablets");
                }),
                Talk.leave("Leave it."));
    }

    static void rock() {
        rockRead = true;
    }

    /** Your shoe locker: the one place she doesn't look. Returns whether this was that. */
    static boolean locker(ServerPlayerEntity p, RunState s) {
        if (!on(s)) return false;
        List<Talk.Option> options = new ArrayList<>();
        if (s.yen > 0) options.add(Talk.option("Hide ¥" + s.yen + " in your shoe.", q -> {
            stashed += s.yen;
            s.yen = 0;
            s.markDirty();
            RunManager.tell(q, "", "¥" + stashed + " in your shoe.");
        }));
        if (stashed > 0) options.add(Talk.option("Take out ¥" + stashed + ".", q -> {
            s.yen += stashed;
            stashed = 0;
            s.markDirty();
        }));
        options.add(Talk.leave("Close it."));
        Talk.say(p, "Your shoe locker", "Your shoes, and ¥" + stashed + " folded under the insole. She's looking the other way.", options.toArray(new Talk.Option[0]));
        return true;
    }

    /** The can from the machine is for later. You don't drink it. */
    static void boughtTea(ServerPlayerEntity p, RunState s) {
        if (!on(s)) return;
        teaCan = true;
        p.removeStatusEffect(StatusEffects.SPEED);
        s.notes.add("A can of green tea, up your sleeve.");
        s.markDirty();
        RunManager.tell(p, "", "You slide the can up your sleeve. She didn't see. You think she didn't see.");
    }

    /** At home, late on: the one thing you can do that she didn't tell you to. */
    static boolean canPourTea(RunState s, ServerPlayerEntity p) {
        return on(s) && tablets && teaCan && !asleep && s.minute >= TEA_FROM && RunManager.isHomeNow(p, s);
    }

    static Talk.Option pourTea(RunState s) {
        Student her = Roster.get(s.livingWith);
        return Talk.option("I made you some tea.", q -> {
            teaCan = false;
            if (s.day - trustSince < TRUST_DAYS) {
                tablets = false;
                Kokuhaku.LOG.info("She smelled the tablets");
                Talk.say(q, her.firstName(), "She holds it under her nose and looks at you for a long time. \"Who taught you that? It doesn't matter. You've only been good for " + Math.max(0, s.day - trustSince) + " days.\"",
                        Talk.option("...", r -> {
                            RunManager.tell(r, "", "She pours it down the sink, and takes the bottle out of your sock.");
                            fracture(r, s);
                        }));
                return;
            }
            asleep = true;
            s.minute = Math.max(s.minute, ASLEEP_AT);
            s.markDirty();
            Kokuhaku.LOG.info("She's asleep. The night's yours");
            Talk.say(q, her.firstName(), "She drinks it all, watching you over the rim. \"You're being so good.\" She gets through half a sentence, and then her head goes down. Her hand opens. The key's in it.",
                    Talk.leave("Take it."));
            RunManager.tell(q, "Three o'clock.", "Chinetsu Station. She'll wake at four.");
        });
    }

    /** The night you've been working towards. */
    private static void night(ServerPlayerEntity p, RunState s) {
        if (s.minute >= WAKES_AT) {
            asleep = false;
            Kokuhaku.LOG.info("She woke up");
            RunManager.finalBlow(p, s);
            return;
        }
        boolean atStation = p.getX() >= STATION_WEST && p.getX() <= STATION_EAST && p.getZ() >= STATION_NORTH && p.getZ() <= STATION_SOUTH;
        if (atStation && s.minute >= TRAIN_FROM && s.minute <= TRAIN_UNTIL && !Talk.waiting(p)) gate(p, s);
    }

    /** There's a barrier on the platform that isn't there in the daytime. */
    private static void gate(ServerPlayerEntity p, RunState s) {
        List<String> words = new ArrayList<>(List.of("Family", "Love", "Home", "Forever"));
        if (rockRead) words.set(System.getenv("KOKUHAKU_STAGE") != null ? 0 : RANDOM.nextInt(words.size()), WORD);
        List<Talk.Option> options = new ArrayList<>();
        for (String word : words) {
            options.add(Talk.option(word, q -> {
                if (word.equals(WORD)) {
                    Kokuhaku.LOG.info("Escaped");
                    RunManager.endGame(q.server, s, Endings.escaped(s));
                } else {
                    Kokuhaku.LOG.info("Wrong word at the gate");
                    RunManager.tell(q, "", "The barrier stays down. Somewhere behind you, you hear someone running. She's never run before.");
                    asleep = false;
                    RunManager.later(60, () -> RunManager.finalBlow(q, s));
                }
            }));
        }
        Talk.say(p, "The gate", "A barrier that isn't there in the daytime, and a dark train behind it with its doors open. There's a little screen. It wants one word. You only get to say it once.", options.toArray(new Talk.Option[0]));
    }

    /** Developer testing only: the plan's done, up to the last night. */
    static void devReady(RunState s) {
        tablets = teaCan = rockRead = true;
        trustSince = s.day - TRUST_DAYS;
    }

    static BlockPos devStation() {
        return new BlockPos(-693, 22, -72);
    }
}
