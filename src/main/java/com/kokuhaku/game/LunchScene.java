package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.WalkScene.Answer;
import com.kokuhaku.game.WalkScene.Step;
import com.kokuhaku.net.Net;
import com.kokuhaku.world.town.Town;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The first time you walk into the hall at lunch: up to the counter, Mrs. Mori
 * and whatever you can afford, then over to the table your friends kept for you.
 * You sit, they talk, you eat, and it's the best chance all day to ask.
 */
final class LunchScene {
    static final String COOK = "Mrs. Mori";

    private enum Stage { TO_COUNTER, ORDERING, SERVED, TO_TABLE, AT_TABLE }

    private record Meal(String label, int price, String item, String reply) {
    }

    private static final List<Meal> MEALS = List.of(
            new Meal("Curry rice", 350, "minecraft:mushroom_stew", "Curry. Good. Eat it while it's hot."),
            new Meal("Katsu don", 450, "minecraft:cooked_porkchop", "Big appetite. Here, I've given you the good one."),
            new Meal("Udon", 300, "minecraft:rabbit_stew", "Mind, the bowl's hot."),
            new Meal("Melon bread and milk", 150, "minecraft:bread", "Is that all? ...Here. Have it anyway."));
    private static final String WATER_ITEM = "minecraft:glass_bottle";
    private static final List<String> GREETINGS = List.of("What'll it be, love?", "Next. What are you having?", "You again. Good. What can I get you?", "Don't stand there letting it go cold. What do you want?");
    private static final String WORRIED = "You look like you haven't slept. Eat something. What'll it be?";
    private static final int WORRIES_FROM_DAY = 5;

    private static final double BLOCKS_PER_TICK = 0.2;
    private static final int LAST_START = 12, LATEST_END = 3, ANSWER_TICKS = 260, EAT_EVERY = 190, CHECK_EVERY = 10, GAP = 24, AFTERWARDS = 50;
    private static final int CLATTER_FROM = 30, CLATTER_MORE = 70, GLANCE_TICKS = 60, USUAL_AFTER = 3, NOTES_FROM_DAY = 3, NOTE_ONE_IN = 4;
    /** How much is left on your tray after each mouthful. */
    private static final float[] BITES = {0.25f, 0.2f, 0.13f, 0f};
    private static final Random RANDOM = new Random();
    private static final float TURN_PER_TICK = 10f, MOST_NECK = 80f, TO_THE_COUNTER = -90f;
    private static final int WEST = -601, EAST = -585, NORTH = -120, SOUTH = -102, FLOOR = 25, CEILING = 29;
    private static final double LAY_FROM = 48;
    private static final double COUNTER_X = -585.5, COUNTER_Z = -114.5;

    private static final class Scene {
        ServerPlayerEntity player;
        Stage stage;
        int left, wait, waited, age, speaker = -1;
        Meal meal;
        Town.LunchSeat seat;
        Entity plate;
        final List<StudentEntity> girls = new ArrayList<>();
        final Deque<LunchTalk.Beat> steps = new ArrayDeque<>();
        int to = LunchTalk.YOU, bites = 0;
        boolean noted = false;
    }

    /** Developer testing only. */
    private static final boolean SHOTS = "1".equals(System.getenv("KOKUHAKU_SHOTS")) && "lunchscene".equals(System.getenv("KOKUHAKU_STAGE"));
    private static final int SHOT_EVERY = 90;
    private static Scene scene = null;
    private static int lastDay = 0, ticks = 0, nextClatter = 0, missed = 0, thanked = 0;
    private static boolean cameBack = false, wasLunch = false;
    static final int MISSED_FROM = 2;
    private static final int STOPPED_COMING = 3, TIP_EVERY = 3;
    /** What you've had, meal by meal: she notices. */
    private static final int[] ordered = new int[MEALS.size()];

    private LunchScene() {
    }

    /** School days in a row you haven't turned up. */
    static int missed() {
        return missed;
    }

    static boolean cameBack() {
        return cameBack;
    }

    static void clear() {
        scene = null;
        lastDay = 0;
        ticks = 0;
        nextClatter = 0;
        missed = thanked = 0;
        cameBack = wasLunch = false;
        java.util.Arrays.fill(ordered, 0);
        LunchHall.clear();
    }

    static boolean active(ServerPlayerEntity p) {
        return scene != null && scene.player == p;
    }

    private static boolean inHall(Vec3d at) {
        return at.x >= WEST && at.x <= EAST + 1 && at.z >= NORTH && at.z <= SOUTH + 1 && at.y >= FLOOR && at.y <= CEILING;
    }

    private static boolean lunchtime(RunState s) {
        return Schedule.isSchoolDay(s.day) && s.minute >= Schedule.LUNCH && s.minute < Schedule.PERIOD_START[4] && !Festival.on(s);
    }

    static void tick(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        ticks++;
        if (ticks % CHECK_EVERY == 0) {
            boolean now = lunchtime(s);
            if (now) wasLunch = true;
            else if (wasLunch) {
                wasLunch = false;
                if (lastDay == s.day) missed = 0;
                else if (++missed == STOPPED_COMING && !Living.active(s)) Shadow.saw(s, "You stopped coming to lunch. She noticed before your friends did.");
            }
            if (!now && LunchHall.laid()) {
                LunchHall.clearTables();
                LunchHall.stopTalking();
            }
            if (now && !LunchHall.laid() && living.stream().anyMatch(p -> p.getPos().squaredDistanceTo(WEST + 8, FLOOR, NORTH + 9) < LAY_FROM * LAY_FROM)) LunchHall.lay(server.getOverworld(), s, town);
        }
        if (lunchtime(s) && LunchHall.laid()) {
            LunchHall.talkAmongThemselves(server, s, scene == null ? null : scene.player);
            if (ticks >= nextClatter) clatter(server, s, town);
        }
        if (scene != null) {
            run(s, town);
            return;
        }
        if (ticks % CHECK_EVERY != 0 || lastDay == s.day || !lunchtime(s) || s.minute > Schedule.LUNCH + LAST_START || Living.active(s) || Kept.on(s)) return;
        for (ServerPlayerEntity p : living) {
            if (inHall(p.getPos()) && !p.hasVehicle() && !RunManager.inScene(p) && !WalkScene.active(p) && !Talk.waiting(p) && LunchHall.cook() != null) {
                start(p, s, town);
                return;
            }
        }
    }

    /** Forks and trays, from somewhere across the room. */
    private static void clatter(MinecraftServer server, RunState s, Town town) {
        nextClatter = ticks + (CLATTER_FROM + RANDOM.nextInt(CLATTER_MORE)) * (1 + s.dead.size() / 3);
        Town.LunchSeat seat = town.lunchSeats.get(RANDOM.nextInt(town.lunchSeats.size()));
        var sound = RANDOM.nextBoolean() ? SoundEvents.BLOCK_LANTERN_PLACE : SoundEvents.ENTITY_ITEM_FRAME_PLACE;
        server.getOverworld().playSound(null, seat.tray().x, seat.tray().y, seat.tray().z, sound, SoundCategory.PLAYERS, 0.16f, 1.4f + RANDOM.nextFloat() * 0.5f);
    }

    private static void start(ServerPlayerEntity p, RunState s, Town town) {
        lastDay = s.day;
        cameBack = missed >= MISSED_FROM;
        missed = 0;
        Scene c = new Scene();
        c.player = p;
        c.seat = LunchHall.yours(s, town);
        for (int id : LunchHall.yourTable(s, town)) {
            for (StudentEntity e : RunManager.studentsOf(p.server)) if (e.getSkin() == id) c.girls.add(e);
        }
        if (c.girls.size() < 2) return;
        scene = c;
        RunManager.setBusy(p, true);
        Kokuhaku.LOG.info("Lunch with {}", c.girls.stream().map(e -> Roster.get(e.getSkin()).firstName()).toList());
        c.stage = Stage.TO_COUNTER;
        c.left = walk(p, town, town.place("lunch_counter"));
    }

    /** The camera walks you there along the floor, and it's as long as the walk is. */
    private static int walk(ServerPlayerEntity p, Town town, int to) {
        List<Vec3d> points = new ArrayList<>(List.of(p.getPos()));
        for (int n : town.graph.path(town.graph.nearest(p.getPos()), to, 1)) points.add(Vec3d.ofBottomCenter(town.graph.pos(n)).add(0, 0, 0));
        List<Vec3d> way = WalkRoute.fit(p.getServerWorld(), points, p);
        if (way.size() < 2) return 0;
        int length = Math.max(10, (int) (new PathWalker(way).length() / BLOCKS_PER_TICK));
        RunManager.sendWalk(p, way, length);
        return length;
    }

    private static void tray(ServerPlayerEntity p, String item) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(item);
        ServerPlayNetworking.send(p, Net.TRAY, buf);
    }

    private static void run(RunState s, Town town) {
        Scene c = scene;
        ServerPlayerEntity p = c.player;
        if (p.isRemoved() || s.minute >= Schedule.PERIOD_START[4] - LATEST_END) {
            finish(c, town);
            return;
        }
        c.age++;
        if (SHOTS && c.age % SHOT_EVERY == 0) ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
        switch (c.stage) {
            case TO_COUNTER -> {
                if (--c.left > 0) return;
                p.teleport(p.getServerWorld(), COUNTER_X, FLOOR, COUNTER_Z, TO_THE_COUNTER, 0f);
                order(c, s);
            }
            case ORDERING -> {
                speak(LunchHall.cook(), true);
                if (Talk.waiting(p) && ++c.waited > ANSWER_TICKS) Talk.choose(p, 0);
            }
            case SERVED -> {
                if (--c.left > 0) return;
                speak(LunchHall.cook(), false);
                tray(p, c.meal == null ? WATER_ITEM : c.meal.item());
                c.stage = Stage.TO_TABLE;
                c.left = walk(p, town, c.seat.node());
            }
            case TO_TABLE -> {
                if (--c.left > 0) return;
                sitDown(c, s, town);
            }
            case AT_TABLE -> atTable(c, s, town);
        }
    }

    private static void speak(StudentEntity e, boolean talking) {
        if (e != null) e.setWalkTalking(talking);
    }

    private static void order(Scene c, RunState s) {
        c.stage = Stage.ORDERING;
        c.waited = 0;
        ServerPlayerEntity p = c.player;
        List<Talk.Option> options = new ArrayList<>();
        // Have the same thing often enough and she stops asking.
        Meal usual = null;
        for (int i = 0; i < MEALS.size(); i++) if (ordered[i] >= USUAL_AFTER && MEALS.get(i).price() <= s.yen && (usual == null || ordered[i] > ordered[MEALS.indexOf(usual)])) usual = MEALS.get(i);
        if (usual != null) {
            Meal same = usual;
            options.add(Talk.option("The usual, please (\u00A5" + same.price() + ")", q -> served(c, s, same)));
        }
        for (Meal meal : MEALS) {
            if (meal.price() > s.yen || meal == usual) continue;
            options.add(Talk.option(meal.label() + " (\u00A5" + meal.price() + ")", q -> served(c, s, meal)));
        }
        options.add(Talk.option("Just some water, please.", q -> served(c, s, null)));
        String line = usual != null ? "The " + usual.label().toLowerCase() + " again? I've kept one back for you." : s.day >= WORRIES_FROM_DAY ? WORRIED : GREETINGS.get(new Random(s.seed ^ s.day).nextInt(GREETINGS.size()));
        StudentEntity cook = LunchHall.cook();
        Talk.say(p, COOK, line, cook == null ? -1 : cook.getId(), options.toArray(new Talk.Option[0]));
    }

    private static void served(Scene c, RunState s, Meal meal) {
        c.meal = meal;
        ServerPlayerEntity p = c.player;
        if (meal != null) {
            s.yen -= meal.price();
            ordered[MEALS.indexOf(meal)]++;
            s.markDirty();
        }
        String reply = meal == null ? "Water. And they wonder why you're all so pale. Here." : meal.reply();
        StudentEntity cook = LunchHall.cook();
        p.getServerWorld().playSound(null, BlockPos.ofFloored(COUNTER_X, FLOOR, COUNTER_Z), SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.5f, 1.3f);
        c.waited = 0;
        Talk.say(p, COOK, reply, cook == null ? -1 : cook.getId(), Talk.option("Thank you, Mrs. Mori.", q -> thanks(c, s, true)), Talk.option("Say nothing.", q -> thanks(c, s, false)));
    }

    /** Nobody thanks the dinner lady. And she hears everything. */
    private static void thanks(Scene c, RunState s, boolean said) {
        c.stage = Stage.SERVED;
        c.left = GAP;
        if (!said) return;
        ServerPlayerEntity p = c.player;
        StudentEntity cook = LunchHall.cook();
        int id = cook == null ? -1 : cook.getId();
        var clue = ++thanked % TIP_EVERY != 0 ? null : s.mystery().clues.stream().filter(k -> k.witness() >= 0 && k.day() <= s.day && !s.found.contains(k.id())).findFirst().orElse(null);
        String line = clue == null ? "Well. Aren't you polite. Go on, before it gets cold." : "You're the only one who ever says that. ...I hear things back here, you know. Nobody sees a dinner lady.\n\n\"" + clue.text() + "\"";
        Talk.say(p, COOK, line, id);
        if (clue != null) RunManager.tellClueFound(p, s, Roster.get(clue.witness()));
        c.left = hold(line);
    }

    private static void sitDown(Scene c, RunState s, Town town) {
        ServerPlayerEntity p = c.player;
        ServerWorld world = p.getServerWorld();
        tray(p, "");
        Seats.sit(p, c.seat.chair(), world.getBlockState(c.seat.chair()));
        c.plate = LunchHall.serveYou(world, s, town, c.meal == null ? WATER_ITEM : c.meal.item());
        for (StudentEntity e : c.girls) {
            // Nearly everyone's sat down by now. Anyone who isn't, is.
            Town.LunchSeat seat = LunchHall.seatFor(e.getSkin(), s, town);
            if (!e.isSittingDown() && seat != null) e.sitOn(seat.chair(), seat.yaw());
            e.getNavigation().stop();
            e.setAiDisabled(true);
        }
        c.steps.addAll(LunchTalk.script(s, c.girls.stream().map(StudentEntity::getSkin).toList(), new Random(s.seed ^ s.day * 3571L)));
        c.wait = GAP;
        c.stage = Stage.AT_TABLE;
    }

    private static void atTable(Scene c, RunState s, Town town) {
        ServerPlayerEntity p = c.player;
        // Everyone's watching whoever's talking, except anyone who'd rather watch you.
        for (int i = 0; i < c.girls.size(); i++) {
            StudentEntity e = c.girls.get(i);
            look(e, watching(c, i, s.mystery().isYandere(e.getSkin())));
            e.setWalkTalking(c.speaker == i && c.wait > 0);
        }
        if (c.age % EAT_EVERY == 0 && c.meal != null && c.bites < BITES.length) bite(c, s, town);
        if (!p.hasVehicle()) {
            finish(c, town);
            return;
        }
        if (c.wait > 0) {
            c.wait--;
            return;
        }
        if (Talk.waiting(p)) {
            if (++c.waited > ANSWER_TICKS) {
                c.waited = 0;
                Talk.choose(p, 0);
            }
            return;
        }
        LunchTalk.Beat beat = c.steps.poll();
        if (beat == null) {
            if (!c.noted && s.day >= NOTES_FROM_DAY && new Random(s.seed ^ s.day * 277L).nextInt(NOTE_ONE_IN) == 0) {
                c.noted = true;
                noteUnderTheTray(c, s);
                return;
            }
            if (++c.left > AFTERWARDS) finish(c, town);
            return;
        }
        c.to = beat.to();
        say(c, beat.step());
    }

    /** Whoever's talking looks at who they're talking to. Everyone else looks at them, and the odd glance goes to you. */
    private static Entity watching(Scene c, int i, boolean yandere) {
        ServerPlayerEntity p = c.player;
        int last = c.girls.size() - 1, speaker = Math.min(c.speaker, last);
        if (c.speaker < 0) return p;
        if (i == speaker) return c.to == LunchTalk.YOU ? p : c.girls.get(Math.min(c.to, last));
        if (i == c.to) return c.girls.get(speaker);
        // She's not really listening.
        if (yandere) return p;
        boolean glancing = c.to == LunchTalk.YOU && (i + c.age / GLANCE_TICKS) % 2 == 0;
        return glancing ? p : c.girls.get(speaker);
    }

    /** A mouthful: your hand, the sound, a few crumbs, and a little less on the tray. */
    private static void bite(Scene c, RunState s, Town town) {
        ServerPlayerEntity p = c.player;
        ServerWorld world = p.getServerWorld();
        float left = BITES[c.bites++];
        p.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.4f, 0.95f + p.getRandom().nextFloat() * 0.15f);
        Vec3d tray = c.seat.tray();
        var item = net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier(c.meal.item()));
        world.spawnParticles(new net.minecraft.particle.ItemStackParticleEffect(net.minecraft.particle.ParticleTypes.ITEM, new net.minecraft.item.ItemStack(item)), tray.x, tray.y + 0.1, tray.z, 5, 0.06, 0.02, 0.06, 0.02);
        if (c.plate != null) c.plate.discard();
        c.plate = left > 0f ? LunchHall.dish(world, tray, c.meal.item(), left) : null;
    }

    /** It wasn't there when you sat down. */
    private static void noteUnderTheTray(Scene c, RunState s) {
        String what = c.meal == null ? "Only water again" : c.meal.label() + " again";
        String text = what + ". You should eat more. I worry.";
        Shadow.saw(s, "She slid a note under your tray while you were listening to someone else.");
        Talk.say(c.player, "Under your tray", "A folded note you didn't put there. \"" + text + "\"", Talk.option("Keep it.", q -> {
            s.notes.add("Under your lunch tray: \"" + text + "\"");
            s.markDirty();
        }));
    }

    private static void look(StudentEntity e, Entity at) {
        float body = e.bodyYaw;
        float want = (float) (Math.toDegrees(Math.atan2(at.getZ() - e.getZ(), at.getX() - e.getX())) - 90);
        float neck = MathHelper.clamp(MathHelper.wrapDegrees(want - body), -MOST_NECK, MOST_NECK);
        float now = MathHelper.wrapDegrees(e.getHeadYaw() - body);
        e.setHeadYaw(body + now + MathHelper.clamp(neck - now, -TURN_PER_TICK, TURN_PER_TICK));
    }

    private static void say(Scene c, Step step) {
        ServerPlayerEntity p = c.player;
        StudentEntity by = c.girls.get(Math.min(step.speaker(), c.girls.size() - 1));
        c.speaker = Math.min(step.speaker(), c.girls.size() - 1);
        c.waited = 0;
        String name = Roster.get(by.getSkin()).firstName();
        if (step.answers().isEmpty()) {
            Talk.say(p, name, step.line(), by.getId());
            c.wait = hold(step.line());
            return;
        }
        Talk.Option[] options = new Talk.Option[step.answers().size()];
        for (int i = 0; i < options.length; i++) {
            Answer a = step.answers().get(i);
            options[i] = Talk.option(a.label(), q -> {
                RunState s = RunManager.state(q.server);
                if (a.fondness() != 0 && by.getSkin() < Roster.GIRLS) {
                    s.fondness[by.getSkin()] = Math.max(-5, Math.min(5, s.fondness[by.getSkin()] + a.fondness()));
                    s.markDirty();
                }
                if (a.then() != null) a.then().accept(q);
                StudentEntity speaker = c.girls.get(Math.min(a.speaker(), c.girls.size() - 1));
                Talk.say(q, Roster.get(speaker.getSkin()).firstName(), a.reply(), speaker.getId());
                c.speaker = Math.min(a.speaker(), c.girls.size() - 1);
                c.to = LunchTalk.YOU;
                c.wait = hold(a.reply());
            });
        }
        Talk.say(p, name, step.line(), by.getId(), options);
        c.wait = GAP;
    }

    private static int hold(String line) {
        return 45 + line.length();
    }

    private static void finish(Scene c, Town town) {
        scene = null;
        ServerPlayerEntity p = c.player;
        Talk.choose(p, -1);
        tray(p, "");
        speak(LunchHall.cook(), false);
        for (StudentEntity e : c.girls) {
            e.setWalkTalking(false);
            e.setAiDisabled(false);
        }
        p.stopRiding();
        BlockPos aisle = town.graph.pos(c.seat.node());
        if (c.stage == Stage.AT_TABLE) p.teleport(p.getServerWorld(), aisle.getX() + 0.5, aisle.getY(), aisle.getZ() + 0.5, p.getYaw(), 0f);
        RunManager.setBusy(p, false);
        Kokuhaku.LOG.info("Lunch over after {} ticks", c.age);
    }
}
