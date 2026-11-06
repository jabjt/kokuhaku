package com.kokuhaku.client;

import com.kokuhaku.block.LockerBlock;
import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Festival;
import com.kokuhaku.game.Lessons;
import com.kokuhaku.game.RunManager;
import com.kokuhaku.game.RunState;
import com.kokuhaku.game.Roster;
import com.kokuhaku.game.Schedule;
import com.kokuhaku.game.Townsfolk;
import com.kokuhaku.screen.BagScreen;
import com.kokuhaku.screen.DialogueScreen;
import com.kokuhaku.screen.EndScreen;
import com.kokuhaku.screen.GrabScreen;
import com.kokuhaku.screen.JumpscareScreen;
import com.kokuhaku.screen.LessonScreen;
import com.kokuhaku.screen.MenuButton;
import com.kokuhaku.screen.PhoneScreen;
import com.kokuhaku.screen.ReelScreen;
import com.kokuhaku.screen.WalkScreen;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Developer testing only (KOKUHAKU_BOT=path to a journal): plays a whole run the
 * way a person would, with the movement keys, the mouse and the real screens. It
 * only decides from what's on screen and in the bag, and writes down what happens.
 * KOKUHAKU_TIMEWARP=n runs the whole game n times faster.
 */
public final class DevBot {
    private static final String PATH = System.getenv("KOKUHAKU_BOT");
    public static final boolean ON = PATH != null;
    private static final int WARP = System.getenv("KOKUHAKU_TIMEWARP") != null ? Integer.parseInt(System.getenv("KOKUHAKU_TIMEWARP")) : 1;
    private static final int ACCUSE_FROM = System.getenv("KOKUHAKU_ACCUSEFROM") != null ? Integer.parseInt(System.getenv("KOKUHAKU_ACCUSEFROM")) : 28;
    private static final double SPEED = 4.3;
    private static final String[] TOUR = {"station", "shops", "park", "beach", "hospital", "field", "gym", "center:Library"};
    private static final Random RANDOM = new Random();

    private static PrintWriter journal;
    private static boolean started = false, finished = false;
    private static int lastDay = 0, ticks = 0;
    private static long nextAct = 0, dayStartedAt = 0, endSeen = 0;
    private static Screen lastScreen, seenScreen;
    private static long screenSince = 0;
    private static int said = 0;

    // Walking.
    private static String goalName = "";
    private static List<Vec3d> route = List.of();
    private static int next = 0;
    private static Vec3d stuckFrom = Vec3d.ZERO;
    private static int stuckTicks = 0, doorWait = 0;
    public static int stuckCount = 0, rescued = 0, detours = 0;

    // Today.
    private static final Map<Integer, Integer> talkedOn = new HashMap<>();
    private static StudentEntity chasing;
    private static int chaseTicks = 0, tourAt = 0, lockerCheckedOn = 0, bagOn = 0, phoneAt = -999, stage = 0;
    private static int conversations = 0, soleSuspectOn = 0, stallsDone = 0, accuseStep = 0, target = -1;
    private static BlockPos lockerPos;
    private static int waitedToCross = 0, circles = 0, headedFor = 0;
    private static final int EVEN_THE_STRIPES = -1000, INDOORS = 40;

    private DevBot() {
    }

    private static void log(String format, Object... args) {
        try {
            if (journal == null) journal = new PrintWriter(new FileWriter(PATH, true), true);
            RunState s = state();
            String when = s == null ? "--" : "D" + s.day + " " + Schedule.clock(s.minute);
            journal.println("[" + when + "] " + String.format(format, args));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RunState state() {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        return server == null ? null : RunManager.state(server);
    }

    private static Object get(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void release(MinecraftClient c) {
        c.options.forwardKey.setPressed(false);
        c.options.leftKey.setPressed(false);
        c.options.rightKey.setPressed(false);
    }

    private static void warpTime() {
        if (WARP <= 1) return;
        long base = System.nanoTime(), from = Util.getMeasuringTimeNano();
        Util.nanoTimeSupplier = () -> from + (System.nanoTime() - base) * WARP;
    }

    private static void begin(MinecraftClient c) {
        started = true;
        c.options.pauseOnLostFocus = false;
        warpTime();
        log("=== Started. Difficulty %s, warp x%d, seed %d", state().difficulty, WARP, state().seed);
        for (KeyBinding k : c.options.allKeys) {
            if (k != KeyBindings.PHONE && k.getBoundKeyTranslationKey().equals(KeyBindings.PHONE.getBoundKeyTranslationKey())) log("KEY CLASH: %s shares the phone key", k.getTranslationKey());
        }
    }

    public static void tick(MinecraftClient c) {
        if (!ON || finished) return;
        if (c.player == null || c.getServer() == null || !ClientRun.active) {
            if (started && endSeen > 0) finish(c, "back at the menu");
            return;
        }
        if (!started) begin(c);
        try {
            step(c);
        } catch (RuntimeException e) {
            log("BOT ERROR: %s at %s", e, e.getStackTrace().length > 0 ? e.getStackTrace()[0] : "?");
            release(c);
            if (c.currentScreen != null && !(c.currentScreen instanceof EndScreen)) c.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }
    }

    private static void step(MinecraftClient c) {
        ticks++;
        if (ticks % 600 == 0) {
            log("Now at %s y%.2f, riding %s, goal %s %d/%d, stuck %d, screen %s, cutscene %s, feet in %s", c.player.getBlockPos().toShortString(), c.player.getY(), c.player.hasVehicle(), goalName, next, route.size(), stuckTicks,
                    c.currentScreen == null ? "none" : c.currentScreen.getClass().getSimpleName(), Cutscene.active(), c.world.getBlockState(c.player.getBlockPos()).getBlock());
            if (c.currentScreen == null && stuckTicks > 40) DevShots.take();
        }
        c.options.sneakKey.setPressed(c.player.hasVehicle() && !Cutscene.active());
        RunState s = state();
        ServerPlayerEntity sp = c.getServer().getPlayerManager().getPlayerList().get(0);
        if (s.day != lastDay) newDay(c, s, sp);
        Screen screen = c.currentScreen;
        if (screen != seenScreen) {
            seenScreen = screen;
            screenSince = System.currentTimeMillis();
            said = 0;
            if (screen != null && !(screen instanceof DialogueScreen || screen instanceof LessonScreen)) log("Screen: %s", screen.getClass().getSimpleName());
            if (screen instanceof JumpscareScreen || screen instanceof EndScreen || screen instanceof ReelScreen) DevShots.take();
        }
        if (screen != null) {
            release(c);
            screen(c, screen, s);
            return;
        }
        if (Cutscene.active()) {
            release(c);
            return;
        }
        play(c, c.player, sp, s);
    }

    private static void finish(MinecraftClient c, String why) {
        finished = true;
        log("=== BOT FINISHED: %s. Stuck %d times, moved on by hand %d times, %d detours", why, stuckCount, rescued, detours);
        c.scheduleStop();
    }

    private static void newDay(MinecraftClient c, RunState s, ServerPlayerEntity sp) {
        long now = System.currentTimeMillis();
        if (lastDay > 0) log("--- Day %d took %d real seconds", lastDay, (now - dayStartedAt) / 1000);
        lastDay = s.day;
        dayStartedAt = now;
        talkedOn.clear();
        tourAt = 0;
        stallsDone = 0;
        long fits = java.util.stream.IntStream.range(0, Roster.girlsFor(s.party.size())).filter(g -> !s.dead.contains(g) && !s.caught.contains(g) && s.mystery().stillFits(g, s.found)).count();
        int fond = 0;
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) fond = Math.max(fond, s.fondness[g]);
        log("=== DAY %d %s: yen %d, marks %d, clues %d, notes %d, profiles %d, still fit %d, guesses %d, dead %d, dating %d, gifts %d, pages %d, her fondness %d, best %d",
                s.day, Schedule.WEEKDAYS[Schedule.weekday(s.day)], s.yen, s.strikes, s.found.size(), s.notes.size(), s.known.size(), fits, s.guessesLeft, s.dead.size(), s.dating, s.gifts, s.pages.size(), s.fondness[s.freeYandere()], fond);
        if (fits == 1 && soleSuspectOn == 0) soleSuspectOn = s.day;
        DevShots.take();
    }

    // ---------------------------------------------------------------- screens

    private static void click(Screen sc, MenuButton b) {
        sc.mouseClicked(b.getX() + 2, b.getY() + 2, 0);
    }

    private static List<MenuButton> buttons(Screen sc) {
        List<MenuButton> out = new ArrayList<>();
        for (var e : sc.children()) if (e instanceof MenuButton b) out.add(b);
        return out;
    }

    private static void screen(MinecraftClient c, Screen sc, RunState s) {
        long now = System.currentTimeMillis();
        if (now < nextAct) return;
        nextAct = now + 70;
        if (sc instanceof com.kokuhaku.screen.PracticalScreen) {
            nextAct = 0;
            boolean holding = (boolean) get(sc, "holding"), poured = (boolean) get(sc, "poured");
            float level = (float) get(sc, "level"), low = (float) get(sc, "low");
            if (!holding && !poured && (long) get(sc, "resultAt") == 0) sc.mouseClicked(5, 5, 0);
            else if (holding && level >= low - 0.035f) sc.mouseReleased(5, 5, 0);
        } else if (sc instanceof com.kokuhaku.screen.HoopScreen) hoop(c, sc);
        else if (sc instanceof GrabScreen) grab(sc);
        else if (sc instanceof WalkScreen) walkScreen(sc);
        else if (sc instanceof DialogueScreen) dialogue(c, sc, s);
        else if (sc instanceof LessonScreen) lesson(sc);
        else if (sc instanceof BagScreen) bag(c, sc, s);
        else if (sc instanceof PhoneScreen) phone(sc);
        else if (sc instanceof EndScreen) {
            if (said++ == 0) log("END: %s", String.join(" / ", (String[]) get(sc, "lines")));
            endSeen = now;
        } else if (sc instanceof ReelScreen) {
            endSeen = now;
            if (now - screenSince > 45_000) finish(c, "saw the reel");
        } else if (sc instanceof JumpscareScreen) {
            endSeen = now;
            if (now - screenSince > 300_000) finish(c, "still in the last room after five minutes");
        } else if (sc.getClass().getSimpleName().equals("CutsceneScreen") || sc.getClass().getSimpleName().equals("DotsScreen")) {
            if (now - screenSince > 120_000 && said++ == 0) log("WARNING: cutscene up for two real minutes");
        } else if (now - screenSince > 3000) {
            log("Closing %s", sc.getClass().getSimpleName());
            sc.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static void walkScreen(Screen sc) {
        List<String> options = (List<String>) get(sc, "options");
        String line = (String) get(sc, "line");
        if (!line.isEmpty() && !line.equals(lastLine)) {
            lastLine = line;
            log("Walking: %s: %s", get(sc, "name"), line);
        }
        if (options.isEmpty() || System.currentTimeMillis() - (long) get(sc, "shown") < 1500) return;
        int pick = RANDOM.nextInt(options.size());
        log("  > %s", options.get(pick));
        click(sc, buttons(sc).get(pick));
    }

    private static String lastLine = "";

    @SuppressWarnings("unchecked")
    private static void dialogue(MinecraftClient c, Screen sc, RunState s) {
        String name = (String) get(sc, "name"), line = (String) get(sc, "line");
        List<String> options = (List<String>) get(sc, "options");
        if (said++ == 0) {
            log("%s: %s", name, line);
            return;
        }
        if ((boolean) get(sc, "skipped") == false) {
            sc.mouseClicked(0, 0, 0); // finish the line
            if (System.currentTimeMillis() - (long) get(sc, "opened") < 200) return;
        }
        if (options.isEmpty()) {
            sc.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            return;
        }
        int pick = choose(name, line, options, s);
        log("  > %s", options.get(pick));
        List<MenuButton> b = buttons(sc);
        if (pick < b.size()) click(sc, b.get(pick));
        else sc.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
    }

    private static int find(List<String> options, String text) {
        for (int i = 0; i < options.size(); i++) if (options.get(i).contains(text)) return i;
        return -1;
    }

    /** What someone sensible would say: ask what they know, be polite, don't go anywhere alone with anyone. */
    private static int choose(String name, String line, List<String> options, RunState s) {
        int last = options.size() - 1;
        if (options.equals(com.kokuhaku.game.Questions.ANSWERS)) return RANDOM.nextInt(10) < 7 ? 0 : 1;
        if (find(options, "Goodbye.") >= 0) {
            int give = find(options, "Give");
            if (give >= 0 && s.gifts > 0) return give;
            int strange = find(options, "Noticed anything strange");
            int about = find(options, "Tell me about yourself");
            if (stage == 0) {
                stage = 1;
                if (strange >= 0) return strange;
            }
            if (stage == 1) {
                stage = 2;
                if (about >= 0) return about;
            }
            stage = 0;
            return last;
        }
        for (String first : new String[]{"Buy a box", "Take one", "Go in.", "Pay ", "How am I", "Watch with", "Keep it.", "Noted."}) {
            int i = find(options, first);
            if (i >= 0) {
                if (first.equals("How am I") && ++conversations % 3 == 0 && last > 1) return RANDOM.nextInt(last);
                return i;
            }
        }
        return RANDOM.nextInt(10) < 6 ? 0 : RANDOM.nextInt(options.size());
    }

    private static void lesson(Screen sc) {
        NbtList questions = (NbtList) get(sc, "questions");
        int index = (int) get(sc, "index");
        if (index >= questions.size()) return;
        if (System.currentTimeMillis() - (long) get(sc, "questionStart") < 500) return;
        NbtCompound q = questions.getCompound(index);
        boolean knows = RANDOM.nextInt(100) < 92;
        List<MenuButton> b = buttons(sc);
        String hand = get(sc, "hand").toString();
        if (hand.equals("TOLD")) return;
        if (hand.equals("WAITING")) {
            if (RANDOM.nextInt(10) < 8) {
                log("Hand up");
                sc.keyPressed(GLFW.GLFW_KEY_H, 0, 0);
            }
            return;
        }
        if (b.isEmpty()) return;
        switch (q.getInt("Kind")) {
            case Lessons.NOTE -> click(sc, b.get(RANDOM.nextInt(10) < 7 ? 0 : 1));
            default -> {
                int count = q.getList("Answers", NbtElement.STRING_TYPE).size(), correct = q.getInt("Correct");
                int wrong = (correct + 1 + RANDOM.nextInt(count - 1)) % count;
                click(sc, b.get(knows ? correct : wrong));
            }
        }
    }

    /** Near enough to the red, most of the time. */
    private static void hoop(MinecraftClient c, Screen sc) {
        nextAct = 0;
        try {
            var marker = sc.getClass().getDeclaredMethod("marker");
            marker.setAccessible(true);
            float at = (float) marker.invoke(sc), zone = (float) get(sc, "zone");
            if (Math.abs(at) <= zone * (RANDOM.nextInt(10) < 7 ? 0.9f : 2.5f)) {
                InputUtil.Key key = InputUtil.fromTranslationKey(c.options.jumpKey.getBoundKeyTranslationKey());
                sc.keyPressed(key.getCode(), 0, 0);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void grab(Screen sc) {
        nextAct = System.currentTimeMillis() + 420;
        int letter = (int) get(sc, "letter");
        int[] keys = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_F};
        sc.keyPressed(keys[RANDOM.nextInt(100) < 88 ? letter : RANDOM.nextInt(keys.length)], 0, 0);
    }

    private static void phone(Screen sc) {
        if (said++ == 0) {
            NbtList messages = (NbtList) get(sc, "messages");
            log("Phone: %d messages, last: %s", messages.size(), messages.isEmpty() ? "" : messages.getCompound(messages.size() - 1).getString("From") + ": " + messages.getCompound(messages.size() - 1).getString("Body"));
            return;
        }
        PhoneScreen phone = (PhoneScreen) sc;
        if (phone.replyCount() > 0 && said == 2) {
            phone.choose(RANDOM.nextInt(phone.replyCount()));
            return;
        }
        if (said > 3) sc.keyPressed(KeyBindings.PHONE.getDefaultKey().getCode(), 0, 0);
    }

    private static void bag(MinecraftClient c, Screen sc, RunState s) {
        NbtList girls = (NbtList) get(sc, "girls");
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < girls.size(); i++) {
            NbtCompound g = girls.getCompound(i);
            if (g.getBoolean("Fits") && !g.getBoolean("Dead") && !g.getBoolean("Accused") && !g.getBoolean("Caught")) open.add(i);
        }
        boolean accusing = s.day >= ACCUSE_FROM && !open.isEmpty() && (open.size() == 1 || s.day >= Schedule.DAYS - 1);
        if (!accusing) {
            if (said++ == 0) log("Bag: %d clues listed, %d girls still fit: %s", ((NbtList) get(sc, "clues")).size(), open.size(), open.size() <= 6 ? open.stream().map(g -> Roster.get(g).firstName()).toList() : "");
            else sc.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            return;
        }
        // Tab, card, Accuse, then again to be sure.
        int x = Math.max(16, sc.width / 2 - 220);
        switch (accuseStep) {
            case 0 -> sc.mouseClicked(x + 170, 18, 0);
            case 1 -> {
                target = open.get(RANDOM.nextInt(open.size()));
                int wide = Math.min(440, sc.width - 32) - 170, card = (wide - 12) / 4;
                sc.mouseClicked(x + (target % 4) * (card + 4) + 3, 44 + (target / 4) * 22 + 3, 0);
                log("Accusing %s out of %d left", Roster.get(target).name(), open.size());
            }
            default -> {
                MenuButton accuse = buttons(sc).stream().filter(b -> b.getMessage().getString().contains("ccuse")).findFirst().orElse(null);
                if (accuse != null) click(sc, accuse);
                if (accuseStep >= 3) {
                    accuseStep = -1;
                    sc.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
            }
        }
        accuseStep++;
    }

    // ---------------------------------------------------------------- the day

    private static void press(KeyBinding key) {
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()));
    }

    private static void play(MinecraftClient c, ClientPlayerEntity me, ServerPlayerEntity sp, RunState s) {
        if (ticks < 40 || c.interactionManager == null) return;
        Town town = RunManager.town();
        int m = s.minute, d = s.day;
        // Once a day, go through the bag; read the phone when it buzzes.
        if (bagOn != d && m > Schedule.WAKE + 15) {
            bagOn = d;
            press(c.options.inventoryKey);
            return;
        }
        if (ClientRun.unread > 0 && ticks - phoneAt > 1200) {
            phoneAt = ticks;
            press(KeyBindings.PHONE);
            return;
        }
        if (d > Schedule.DAYS) {
            walkTo(c, me, "out of the house", town.graph.pos(town.foyer));
            return;
        }
        if (com.kokuhaku.game.Pe.racing(sp)) {
            me.setYaw(-90f);
            me.setPitch(0f);
            c.options.forwardKey.setPressed(true);
            return;
        }
        if (ClientRun.objective.toLowerCase().contains("front door") && !ClientRun.objective.contains("lock")) {
            BlockPos door = RunManager.devFrontDoor(sp, s);
            if (door != null) {
                goStraightTo(c, me, "the front door", door);
                return;
            }
        }
        boolean school = Schedule.isSchoolDay(d);
        int bedtime = Festival.on(s) ? 19 * 60 + 30 : school ? Schedule.AFTER_SCHOOL + 75 : Schedule.AFTER_SCHOOL + 15;
        if (RunManager.devWard(sp) || m >= bedtime) {
            goHome(c, me, sp, s);
            return;
        }
        // In order: curfew and bed (above), the next lesson, the locker, talking to people, and only then looking round.
        boolean lockerDue = school && lockerCheckedOn != d && (d % 3 == 0 || ClientRun.objective.toLowerCase().contains("locker") || ClientRun.objective.contains("envelope"));
        if (school && m < Schedule.AFTER_SCHOOL) {
            List<String> subjects = Schedule.subjects(s.seed, Schedule.weekday(d));
            for (int i = 0; i < Schedule.PERIOD_START.length; i++) {
                int start = Schedule.PERIOD_START[i];
                if (m > start + Schedule.LATE_GRACE) continue;
                int room = Schedule.roomNumber(subjects.get(i));
                BlockPos to = town.graph.pos(town.place(room == 0 ? Schedule.swimming(d) ? "pool" : "gym" : "center:" + Schedule.roomKey(room)));
                double minutes = me.getPos().distanceTo(Vec3d.ofCenter(to)) * 1.6 / SPEED * 20 / Schedule.TICKS_PER_MINUTE + 3;
                // Once it's time to leave for a lesson, nothing else gets a look in until you're sitting down.
                int lesson = d * 10 + i;
                if (headedFor != lesson && start - m <= minutes + 8) {
                    headedFor = lesson;
                    chasing = null;
                    log("Leaving for %s now, no stopping", subjects.get(i));
                }
                boolean there = room == 0 ? com.kokuhaku.game.Pe.there(me.getPos(), d) : town.rooms.get(Schedule.roomKey(room)).contains(me.getPos());
                if (there) {
                    release(c);
                    return;
                }
                if (headedFor != lesson) {
                    if (lockerDue && locker(c, me, s, town)) return;
                    if (talk(c, me, s, 14)) return;
                }
                walkTo(c, me, subjects.get(i), to);
                return;
            }
        }
        if (lockerDue && locker(c, me, s, town)) return;
        if (Festival.on(s)) {
            festival(c, me, s, town);
            return;
        }
        if (talk(c, me, s, 20)) return;
        BlockPos stop = town.graph.pos(town.place(TOUR[tourAt % TOUR.length]));
        if (me.getPos().squaredDistanceTo(Vec3d.ofCenter(stop)) < 25) {
            log("Visited %s", TOUR[tourAt % TOUR.length]);
            tourAt++;
            return;
        }
        walkTo(c, me, TOUR[tourAt % TOUR.length], stop);
    }

    /** Waypoints as far as they go, then across the room. */
    private static void goStraightTo(MinecraftClient c, ClientPlayerEntity me, String name, BlockPos to) {
        // Close by, in a house with stairs in it: the blocks that are really there, not the waypoints.
        if (!name.equals(goalName)) {
            List<Vec3d> path = search(c, me, to, INDOORS);
            if (!path.isEmpty()) {
                goalName = name;
                route = new ArrayList<>(path);
                next = 0;
                stuckTicks = 0;
                log("Found the way to %s, %d steps", name, path.size());
            }
        }
        walkTo(c, me, name, to);
        if (next >= route.size()) {
            face(me, Vec3d.ofCenter(to));
            c.options.forwardKey.setPressed(true);
        }
    }

    private static void festival(MinecraftClient c, ClientPlayerEntity me, RunState s, Town town) {
        if (ClientRun.objective.contains("Station") || ClientRun.objective.contains("train")) {
            walkTo(c, me, "the station", town.graph.pos(town.place("station")));
            return;
        }
        if (s.minute >= Festival.OPENS && stallsDone < 4) {
            BlockPos stall = RunManager.devStall(stallsDone);
            if (stall == null) {
                stallsDone++;
                return;
            }
            if (me.getPos().squaredDistanceTo(Vec3d.ofCenter(stall)) < 16) {
                release(c);
                stallsDone++;
                use(c, me, stall);
                return;
            }
            walkTo(c, me, "stall " + stallsDone, stall.south(2));
            return;
        }
        if (talk(c, me, s, 20)) return;
        BlockPos field = town.graph.pos(town.place("field"));
        if (me.getPos().squaredDistanceTo(Vec3d.ofCenter(field)) > 100) walkTo(c, me, "the field", field);
        else release(c);
    }

    private static void use(MinecraftClient c, ClientPlayerEntity me, BlockPos pos) {
        c.interactionManager.interactBlock(me, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
    }

    private static boolean locker(MinecraftClient c, ClientPlayerEntity me, RunState s, Town town) {
        if (lockerPos == null) {
            BlockPos middle = town.graph.pos(town.foyer);
            for (BlockPos p : BlockPos.iterateOutwards(middle, 32, 5, 32)) {
                BlockState b = c.world.getBlockState(p);
                if (b.isOf(ModBlocks.LOCKER) && b.get(LockerBlock.NUMBER) == s.lockers[0]) {
                    lockerPos = p.toImmutable();
                    break;
                }
            }
            if (lockerPos == null) {
                if (town.graph.pos(town.foyer).getSquaredDistance(me.getPos()) > 900) {
                    walkTo(c, me, "the shoe lockers", town.graph.pos(town.foyer));
                    return true;
                }
                log("WARNING: couldn't find locker %d", s.lockers[0]);
                lockerCheckedOn = s.day;
                return false;
            }
        }
        if (me.getPos().squaredDistanceTo(Vec3d.ofCenter(lockerPos)) < 34) {
            release(c);
            lockerCheckedOn = s.day;
            log("Checked locker %d", s.lockers[0]);
            use(c, me, lockerPos);
            return true;
        }
        goStraightTo(c, me, "locker " + s.lockers[0], lockerPos);
        return true;
    }

    private static void goHome(MinecraftClient c, ClientPlayerEntity me, ServerPlayerEntity sp, RunState s) {
        Town.Home home = RunManager.devHome(sp, s);
        BlockPos bed = home.bedHead();
        BlockPos front = RunManager.devFrontDoor(sp, s);
        if (front != null && !s.doorLocked[0] && s.minute >= Schedule.AFTER_SCHOOL && me.getPos().squaredDistanceTo(Vec3d.ofCenter(front)) < 16) {
            release(c);
            me.getInventory().selectedSlot = 1;
            use(c, me, front);
            me.getInventory().selectedSlot = 0;
            log("Locked the front door");
            return;
        }
        if (me.getPos().squaredDistanceTo(Vec3d.ofCenter(bed)) > 20) {
            goStraightTo(c, me, "home", RunManager.town().graph.pos(home.inside()));
            if (home.contains(me.getPos())) {
                c.options.forwardKey.setPressed(true);
                face(me, Vec3d.ofCenter(bed));
            }
            return;
        }
        release(c);
        if (s.minute >= Schedule.AFTER_SCHOOL) {
            log("Going to bed");
            use(c, me, bed);
            nextAct = System.currentTimeMillis() + 2000;
        }
    }

    /** Whoever's nearest that you haven't spoken to today. */
    private static boolean talk(MinecraftClient c, ClientPlayerEntity me, RunState s, double reach) {
        if (chasing != null && (chasing.isRemoved() || ++chaseTicks > 160)) {
            talkedOn.put(chasing.getId(), s.day);
            chasing = null;
        }
        if (chasing == null) {
            chaseTicks = 0;
            double best = reach * reach;
            for (Entity e : c.world.getEntities()) {
                if (!(e instanceof StudentEntity st) || st.isAiDisabled() || talkedOn.getOrDefault(st.getId(), 0) == s.day) continue;
                if (Townsfolk.isOne(st) && !com.kokuhaku.game.Teachers.isOne(st)) continue;
                double dist = st.squaredDistanceTo(me);
                if (dist < best && c.world.isSpaceEmpty(me) && me.canSee(st)) {
                    best = dist;
                    chasing = st;
                }
            }
            if (chasing == null) return false;
        }
        face(me, chasing.getEyePos());
        if (chasing.squaredDistanceTo(me) < 7) {
            release(c);
            talkedOn.put(chasing.getId(), s.day);
            stage = 0;
            log("Talking to %s", chasing.getName().getString());
            c.interactionManager.interactEntity(me, chasing, Hand.MAIN_HAND);
            chasing = null;
        } else {
            c.options.forwardKey.setPressed(true);
        }
        return true;
    }

    private static final int BOT_LOOKS_AHEAD = 3;
    private static final float BOT_TURN_RATE = 0.22f, BOT_MOST_TURN = 14f;

    /** A step or two up the path, and round to it a bit at a time. */
    private static void turnTowards(ClientPlayerEntity me, Vec3d at) {
        float wanted = (float) (Math.toDegrees(Math.atan2(at.z - me.getZ(), at.x - me.getX())) - 90);
        float off = net.minecraft.util.math.MathHelper.wrapDegrees(wanted - me.getYaw());
        me.setYaw(me.getYaw() + net.minecraft.util.math.MathHelper.clamp(off * BOT_TURN_RATE, -BOT_MOST_TURN, BOT_MOST_TURN));
        me.setPitch(0f);
    }

    private static void face(ClientPlayerEntity me, Vec3d at) {
        me.setYaw((float) (Math.toDegrees(Math.atan2(at.z - me.getZ(), at.x - me.getX())) - 90));
        me.setPitch(0f);
    }

    private static boolean standable(MinecraftClient c, BlockPos p) {
        BlockState under = c.world.getBlockState(p.down());
        if (under.getBlock() instanceof net.minecraft.block.PaneBlock || under.getBlock() instanceof DoorBlock) return false;
        return open(c, p) && open(c, p.up()) && !under.getCollisionShape(c.world, p.down()).isEmpty();
    }

    private static boolean open(MinecraftClient c, BlockPos p) {
        BlockState b = c.world.getBlockState(p);
        return b.getBlock() instanceof DoorBlock || b.getCollisionShape(c.world, p).isEmpty();
    }

    /** Round whatever's in the way: a short search over the blocks actually there. */
    private static List<Vec3d> detour(MinecraftClient c, ClientPlayerEntity me, BlockPos goal) {
        return search(c, me, goal, 22);
    }

    private static List<Vec3d> search(MinecraftClient c, ClientPlayerEntity me, BlockPos goal, int reach) {
        BlockPos start = me.getBlockPos();
        Map<BlockPos, BlockPos> from = new HashMap<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        from.put(start, start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (p.getX() == goal.getX() && p.getZ() == goal.getZ() && Math.abs(p.getY() - goal.getY()) <= 1) {
                List<Vec3d> path = new ArrayList<>();
                for (BlockPos q = p; !q.equals(start); q = from.get(q)) path.add(0, Vec3d.ofBottomCenter(q));
                return path;
            }
            for (Direction d : Direction.Type.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = p.offset(d).up(dy);
                    if (from.containsKey(n) || n.getManhattanDistance(start) > reach || !standable(c, n)) continue;
                    if (dy > 0 && !open(c, p.up(2))) continue;
                    if (dy < 0 && !open(c, n.up(2))) continue;
                    from.put(n, p);
                    queue.add(n);
                }
            }
        }
        return List.of();
    }

    private static void walkTo(MinecraftClient c, ClientPlayerEntity me, String name, BlockPos to) {
        Town town = RunManager.town();
        if (!name.equals(goalName) || route.isEmpty()) {
            goalName = name;
            var server = c.getServer();
            ServerPlayerEntity sp = server.getPlayerManager().getPlayerList().get(0);
            List<Vec3d> points = new ArrayList<>(server.submit(() -> RunManager.devRoute(sp, to)).join());
            route = points;
            next = 0;
            stuckTicks = 0;
            log("Walking to %s, %d waypoints, from %s via %s, first waypoint block %s", name, points.size(), me.getBlockPos().toShortString(), points.isEmpty() ? "?" : points.get(0), points.isEmpty() ? "?" : c.world.getBlockState(BlockPos.ofFloored(points.get(0))));
        }
        if (next >= route.size()) {
            release(c);
            return;
        }
        // Something's moved you since you set off.
        if (route.get(next).squaredDistanceTo(me.getPos()) > 900) {
            route = List.of();
            return;
        }
        Vec3d at = route.get(next);
        double dx = at.x - me.getX(), dz = at.z - me.getZ();
        if (dx * dx + dz * dz < 1.2) {
            // Right above or below you is no use to steer by: that's how you end up spinning on the spot.
            if (Math.abs(at.y - me.getY()) >= 1.8) log("Waypoint on another floor at %s, skipping it", at);
            next++;
            return;
        }
        // Look both ways.
        BlockPos ahead = BlockPos.ofFloored(me.getPos().add(at.subtract(me.getPos()).normalize().multiply(3)));
        if (Town.isRoad(c.world, ahead.getX(), ahead.getZ(), EVEN_THE_STRIPES) && !Town.isRoad(c.world, me.getBlockX(), me.getBlockZ(), EVEN_THE_STRIPES) && waitedToCross < 300) {
            for (Entity e : c.world.getEntities()) {
                if (e instanceof net.minecraft.entity.mob.MobEntity && !(e instanceof StudentEntity) && e.squaredDistanceTo(me) < 500) {
                    waitedToCross++;
                    release(c);
                    return;
                }
            }
        }
        waitedToCross = 0;
        turnTowards(me, route.get(Math.min(route.size() - 1, next + BOT_LOOKS_AHEAD)));
        c.options.forwardKey.setPressed(true);
        if (me.getPos().squaredDistanceTo(stuckFrom) > 0.5) {
            stuckFrom = me.getPos();
            stuckTicks = 0;
            c.options.leftKey.setPressed(false);
            c.options.rightKey.setPressed(false);
            return;
        }
        stuckTicks++;
        if (stuckTicks == 30) {
            List<Vec3d> around = detour(c, me, BlockPos.ofFloored(route.get(Math.min(next + 1, route.size() - 1))));
            if (around.isEmpty()) around = detour(c, me, BlockPos.ofFloored(at));
            if (!around.isEmpty() && route.size() < 300) {
                detours++;
                route.addAll(next, around);
                stuckTicks = 0;
                return;
            }
            if (route.size() >= 300) {
                StringBuilder near = new StringBuilder();
                for (BlockPos p : BlockPos.iterateOutwards(me.getBlockPos(), 2, 1, 2)) {
                    BlockState b = c.world.getBlockState(p);
                    if (!b.isAir()) near.append(p.toShortString()).append('=').append(b.getBlock().getTranslationKey().replace("block.minecraft.", "")).append(' ');
                }
                log("Going in circles at %s y%.2f towards %s: %s", me.getBlockPos().toShortString(), me.getY(), at, near);
                DevShots.take();
                if (++circles >= 2) {
                    circles = 0;
                    rescued++;
                    Vec3d hop = route.get(Math.min(route.size() - 1, next + 200));
                    log("Moved on by hand to %s", hop);
                    c.getServer().execute(() -> c.getServer().getPlayerManager().getPlayerList().get(0).requestTeleport(hop.x, hop.y, hop.z));
                }
                route = List.of();
                stuckTicks = 400;
                return;
            }
        }
        if (stuckTicks == 25) {
            for (BlockPos p : BlockPos.iterateOutwards(me.getBlockPos(), 2, 1, 2)) {
                BlockState b = c.world.getBlockState(p);
                if (b.getBlock() instanceof DoorBlock && !b.isOf(ModBlocks.LOCKER) && !b.get(DoorBlock.OPEN)) {
                    use(c, me, p.toImmutable());
                    break;
                }
            }
        }
        if (stuckTicks == 60) (RANDOM.nextBoolean() ? c.options.leftKey : c.options.rightKey).setPressed(true);
        if (stuckTicks == 140) {
            c.options.leftKey.setPressed(false);
            c.options.rightKey.setPressed(false);
            route = List.of();
        }
        if (stuckTicks == 400) {
            stuckCount++;
            log("STUCK on the way to %s at %s, next waypoint %s", name, me.getBlockPos().toShortString(), at);
            DevShots.take();
        }
        if (stuckTicks >= 1000) {
            rescued++;
            log("Gave up walking: moved on by hand from %s", me.getBlockPos().toShortString());
            stuckTicks = 0;
            Vec3d hop = at;
            MinecraftClient.getInstance().getServer().execute(() -> MinecraftClient.getInstance().getServer().getPlayerManager().getPlayerList().get(0).requestTeleport(hop.x, hop.y, hop.z));
        }
    }
}
