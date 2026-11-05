package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The walk to school. You don't go alone: one or two people fall in beside
 * you, the camera walks you in, and you talk on the way. The clock keeps
 * running and the rest of the school is on the streets around you.
 */
public final class WalkScene {
    /** A strolling pace: slower than walking on your own, because you're talking. */
    private static final double BLOCKS_PER_TICK = 3.4 / 20, LONGEST_WALK = 170, BESIDE = 1.35, AHEAD = 0.8;
    /** How far ahead on the path someone walks when there's no room beside you, the second behind the first. */
    private static final double IN_FRONT = 2.7, NEXT_IN_LINE = 1.3;
    /** They look this far up the path, so they've dropped in front before the gap, not in it. */
    private static final double[] LOOK_AHEAD = {0, 0.75, 1.5};
    private static final double CLIMB_PER_TICK = 0.2, DOOR_REACH = 1.2, DOOR_CLOSES = 4, FLOATING = 0.4;
    /** How quickly someone drops in front of you, or comes back alongside, and how far from your height the ground under them can be. */
    private static final double DROPS_IN_PER_TICK = 0.05, LEVEL_WITH_YOU = 0.9;
    private static final float TURN_PER_TICK = 9f, MOST_NECK = 100f;
    private static final int STAY_IN_FRONT = 25, CLOCK_EVERY = 10;
    /** How much rather anyone walking to school stays on the pavement. */
    private static final double KEEP_TO_THE_PAVEMENT = 12;
    /** You stop this far short of the line, and how long a train can hold you up. */
    private static final double[] LOOK_FOR_TRAINS = {2, 4, 6, 8, 10};
    private static final int LONGEST_WAIT = 700;
    private static final int LEAVE_AT = 7 * 60 + 48, PLAYER_FOLLOWS = 10, GAP = 24, ANSWER_TICKS = 220;

    /** One thing said on the way: by companion 0 or 1, with answers if it's a question. */
    public record Step(int speaker, String line, List<Answer> answers) {
    }

    public record Answer(String label, int speaker, String reply, int fondness, java.util.function.Consumer<ServerPlayerEntity> then) {
    }

    private static final class Scene {
        ServerPlayerEntity player;
        PathWalker path;
        int ticks, age, wait, speaker, waited;
        /** For each companion: how long to stay in front, how far they're alongside you (0 in front, 1 beside), where their feet are and which way they face. */
        final int[] holdFront = new int[2];
        final double[] beside = new double[2];
        double furthest;
        final Vec3d[] feet = new Vec3d[2];
        final float[] bodyYaw = new float[2];
        final List<BlockPos> doors = new ArrayList<>();
        int inWall, floating, crabbing, held, onLine, besideTrain;
        boolean holding, warned;
        final List<StudentEntity> people = new ArrayList<>();
        final Deque<Step> steps = new ArrayDeque<>();
    }

    private static final Map<UUID, Scene> scenes = new HashMap<>();

    private WalkScene() {
    }

    public static void clear() {
        scenes.clear();
    }

    public static boolean active(ServerPlayerEntity p) {
        return scenes.containsKey(p.getUuid());
    }

    private static List<Vec3d> route(ServerPlayerEntity p, RunState s, Town town) {
        int to = Festival.on(s) ? town.place("field") : town.foyer;
        List<Integer> path = town.graph.path(town.graph.nearest(p.getPos()), to, KEEP_TO_THE_PAVEMENT);
        List<Vec3d> points = new ArrayList<>();
        for (int n : path) {
            BlockPos b = town.graph.pos(n);
            points.add(new Vec3d(b.getX() + 0.5, b.getY(), b.getZ() + 0.5));
        }
        // Only the last stretch is shown, so it starts outdoors and never drags.
        double walked = 0;
        int from = points.size() - 1;
        while (from > 0 && walked < LONGEST_WALK) {
            walked += points.get(from).distanceTo(points.get(from - 1));
            from--;
        }
        // Your front door is where the walk begins, not your bedroom.
        var world = p.getServerWorld();
        while (from < points.size() - 3 && !world.isSkyVisible(BlockPos.ofFloored(points.get(from)).up())) from++;
        return new ArrayList<>(points.subList(from, points.size()));
    }

    /** Who you walk in with: seeded, so a replay of a morning isn't a different morning. */
    private static List<Integer> company(RunState s, MinecraftServer server) {
        if (Living.active(s)) return s.bonk ? List.of() : List.of(s.livingWith);
        if (Festival.on(s) && TrainRide.walkingWith >= 0) return List.of(TrainRide.walkingWith);
        Random r = new Random(s.seed ^ s.day * 31337L);
        List<Integer> free = new ArrayList<>();
        for (StudentEntity e : RunManager.studentsOf(server)) if (!People.stayingHome(s, e.getSkin())) free.add(e.getSkin());
        // Haruto shows the first of you round; he can only be in one place.
        if (s.day == 1 && free.contains(Roster.GIRLS)) return List.of(Roster.GIRLS);
        free.sort(Integer::compare);
        java.util.Collections.shuffle(free, r);
        int want = 1 + r.nextInt(2);
        List<Integer> chosen = new ArrayList<>(free.subList(0, Math.min(want, free.size())));
        // She waits for you at the end of the street.
        if (free.contains(s.dating)) {
            chosen.remove((Integer) s.dating);
            chosen.add(0, s.dating);
            if (chosen.size() > want) chosen.remove(chosen.size() - 1);
        }
        return chosen;
    }

    /** Starts the walk, if there's anywhere to walk from. */
    static boolean start(ServerPlayerEntity p, RunState s, Town town) {
        return begin(p, s, WalkRoute.fit(p.getServerWorld(), route(p, s, town), p));
    }

    /** The same walk, along a way that isn't on the town's map. */
    static boolean startAlong(ServerPlayerEntity p, RunState s, List<Vec3d> way) {
        return begin(p, s, WalkRoute.fit(p.getServerWorld(), way, p));
    }

    private static boolean begin(ServerPlayerEntity p, RunState s, List<Vec3d> points) {
        if (points.size() < 2) return false;
        Scene scene = new Scene();
        scene.player = p;
        scene.path = new PathWalker(points);
        scene.ticks = (int) (scene.path.length() / BLOCKS_PER_TICK);
        // Whoever's already walking someone else in can't be beside you too: they'd be dragged along both ways at once.
        java.util.Set<StudentEntity> busy = new java.util.HashSet<>();
        for (Scene other : scenes.values()) busy.addAll(other.people);
        List<Integer> with = new ArrayList<>(company(s, p.server));
        with.removeIf(id -> busy.stream().anyMatch(e -> e.getSkin() == id));
        if (with.isEmpty() && !Living.active(s)) {
            Random r = new Random(s.seed ^ s.day * 7919L ^ p.getUuid().getLeastSignificantBits());
            List<StudentEntity> free = new ArrayList<>(RunManager.studentsOf(p.server));
            free.removeIf(e -> busy.contains(e) || People.stayingHome(s, e.getSkin()) || e.hasVehicle());
            free.sort(java.util.Comparator.comparingInt(StudentEntity::getSkin));
            if (!free.isEmpty()) with.add(free.get(r.nextInt(free.size())).getSkin());
        }
        for (int id : with) {
            for (StudentEntity e : RunManager.studentsOf(p.server)) {
                if (e.getSkin() != id || busy.contains(e)) continue;
                e.getNavigation().stop();
                e.setAiDisabled(true);
                e.setNoGravity(true);
                e.noClip = true;
                scene.people.add(e);
            }
        }
        if (scene.people.isEmpty()) return false;
        scene.steps.addAll(WalkTalk.script(s, scene.people.stream().map(StudentEntity::getSkin).toList(), scene.ticks));
        scene.wait = 50;
        scenes.put(p.getUuid(), scene);
        Traffic.holdFor(p.getUuid(), points);
        if (!Living.active(s) && scene.people.stream().anyMatch(e -> s.mystery().isYandere(e.getSkin()))) Shadow.saw(s, "She walked right beside you, asking about your homework.");
        if (s.minute < LEAVE_AT) s.minute = LEAVE_AT;
        s.markDirty();
        p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        RunManager.setBusy(p, true);
        Kokuhaku.LOG.info("Walking to school with {} over {} blocks, {} ticks", scene.people.stream().map(e -> Roster.get(e.getSkin()).firstName()).toList(), (int) scene.path.length(), scene.ticks);
        RunManager.sendWalk(p, points, scene.ticks);
        move(scene);
        return true;
    }

    private static Vec3d besideYou(Scene scene, int i, double progress) {
        Vec3d at = scene.path.at(progress), heading = scene.path.heading(progress);
        Vec3d right = new Vec3d(-heading.z, 0, heading.x);
        return at.add(right.multiply(i == 0 ? BESIDE : -BESIDE)).add(heading.multiply(AHEAD));
    }

    /** Whether there's clear ground beside you, here and for the next couple of blocks. */
    private static boolean roomBeside(Scene scene, int i, double progress) {
        double length = scene.path.length();
        for (double look : LOOK_AHEAD) {
            double ahead = Math.min(1, progress + look / length);
            if (!WalkRoute.reachable(scene.player.getServerWorld(), scene.path.at(ahead), besideYou(scene, i, ahead), scene.player)) return false;
        }
        return true;
    }

    /** Doors open as anyone reaches them, and shut again behind you. */
    private static void doors(Scene scene, Vec3d at, Vec3d heading) {
        ServerWorld world = scene.player.getServerWorld();
        List<Vec3d> near = new ArrayList<>(List.of(at.add(heading.multiply(DOOR_REACH))));
        for (StudentEntity e : scene.people) near.add(e.getPos().add(heading.multiply(DOOR_REACH)));
        for (Vec3d v : near) {
            BlockPos pos = BlockPos.ofFloored(v.x, v.y + 0.5, v.z);
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door && !door.isOpen(state)) {
                door.setOpen(null, world, state, pos, true);
                scene.doors.add(pos.toImmutable());
            }
        }
        scene.doors.removeIf(pos -> {
            if (pos.getSquaredDistance(at) < DOOR_CLOSES * DOOR_CLOSES) return false;
            shut(world, pos);
            return true;
        });
    }

    private static void shut(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) door.setOpen(null, world, state, pos, false);
    }

    /**
     * They go where you go: a place on your own route, beside you where there's
     * room and just in front where there isn't. Your route's already clear all
     * the way, so nothing can hold them up, and they can't end up left behind.
     */
    private static void move(Scene scene) {
        double progress = scene.age / (double) scene.ticks, length = scene.path.length();
        Vec3d at = scene.path.at(progress), heading = scene.path.heading(progress);
        ServerWorld world = scene.player.getServerWorld();
        float yaw = (float) (Math.toDegrees(Math.atan2(heading.z, heading.x)) - 90);
        for (int i = 0; i < scene.people.size(); i++) {
            StudentEntity e = scene.people.get(i);
            if (!roomBeside(scene, i, progress)) scene.holdFront[i] = STAY_IN_FRONT;
            else if (scene.holdFront[i] > 0) scene.holdFront[i]--;
            boolean first = scene.feet[i] == null;
            double wantBeside = scene.holdFront[i] > 0 ? 0 : 1;
            if (first) scene.beside[i] = wantBeside;
            scene.beside[i] += Math.max(-DROPS_IN_PER_TICK, Math.min(DROPS_IN_PER_TICK, wantBeside - scene.beside[i]));
            double ahead = AHEAD + (IN_FRONT + i * NEXT_IN_LINE - AHEAD) * (1 - scene.beside[i]);
            double along = Math.min(1, progress + ahead / length);
            Vec3d on = scene.path.at(along), way = scene.path.heading(along);
            Vec3d flat = on.add(new Vec3d(-way.z, 0, way.x).multiply((i == 0 ? BESIDE : -BESIDE) * scene.beside[i]));
            double from = first ? on.y : scene.feet[i].y;
            Double under = WalkRoute.ground(world, flat.x, flat.z, from, scene.player);
            double height = under != null && Math.abs(under - on.y) <= LEVEL_WITH_YOU ? under : on.y;
            Vec3d was = first ? new Vec3d(flat.x, height, flat.z) : scene.feet[i];
            Vec3d next = new Vec3d(flat.x, first ? height : from + Math.max(-CLIMB_PER_TICK, Math.min(CLIMB_PER_TICK, height - from)), flat.z);
            if (first) scene.bodyYaw[i] = yaw;
            scene.feet[i] = next;
            scene.furthest = Math.max(scene.furthest, next.distanceTo(at));
            Vec3d moved = next.subtract(was);
            float facing = moved.horizontalLengthSquared() > 0.0004 ? (float) (Math.toDegrees(Math.atan2(moved.z, moved.x)) - 90) : yaw;
            scene.bodyYaw[i] += Math.max(-TURN_PER_TICK, Math.min(TURN_PER_TICK, net.minecraft.util.math.MathHelper.wrapDegrees(facing - scene.bodyYaw[i])));
            if (moved.horizontalLengthSquared() > 0.0004 && Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(facing - scene.bodyYaw[i])) > 40) scene.crabbing++;
            if (!WalkRoute.fits(world, next.x, next.y, next.z)) scene.inWall++;
            Double ground = WalkRoute.ground(world, next.x, next.z, next.y, scene.player);
            if (ground != null && next.y - ground > FLOATING) scene.floating++;
            e.refreshPositionAndAngles(next.x, next.y, next.z, scene.bodyYaw[i], 0f);
            e.setBodyYaw(scene.bodyYaw[i]);
            // Whoever's talking glances at you, as far as a neck goes.
            boolean talking = scene.wait > 0 && scene.speaker == i;
            float toYou = (float) (Math.toDegrees(Math.atan2(at.z - next.z, at.x - next.x)) - 90);
            float neck = Math.max(-MOST_NECK, Math.min(MOST_NECK, net.minecraft.util.math.MathHelper.wrapDegrees(toYou - scene.bodyYaw[i])));
            e.setHeadYaw(talking ? scene.bodyYaw[i] + neck : scene.bodyYaw[i]);
            e.setWalkTalking(talking);
        }
        doors(scene, at, heading);
        if (scene.age % PLAYER_FOLLOWS == 0) {
            ServerPlayerEntity p = scene.player;
            p.teleport(p.getServerWorld(), at.x, at.y, at.z, yaw, 0f);
        }
    }

    public static void tick() {
        for (Scene scene : new ArrayList<>(scenes.values())) {
            if (scene.player.isRemoved()) {
                finish(scene);
                continue;
            }
            boolean wait = trainInTheWay(scene);
            if (wait != scene.holding) {
                scene.holding = wait;
                RunManager.sendWalkHold(scene.player, wait);
                if (wait) Kokuhaku.LOG.info("The walk stops for a train at age {}", scene.age);
            }
            if (wait) {
                scene.held++;
                if (!scene.warned && !Talk.waiting(scene.player)) {
                    scene.warned = true;
                    StudentEntity by = scene.people.get(0);
                    Talk.say(scene.player, Roster.get(by.getSkin()).firstName(), "Hang on. Train.", by.getId());
                    scene.wait = 40;
                }
            } else {
                scene.age++;
            }
            // Your camera keeps real time and this doesn't: a moment's lag would leave them all behind you.
            if (scene.age % CLOCK_EVERY == 0) RunManager.sendWalkClock(scene.player, scene.age);
            move(scene);
            if (Railway.nearLine(scene.path.at(scene.age / (double) scene.ticks).x)) {
                scene.onLine++;
                if (Railway.blocksCrossing(scene.path.at(scene.age / (double) scene.ticks))) scene.besideTrain++;
            }
            if (scene.age >= scene.ticks) {
                finish(scene);
                continue;
            }
            if (scene.wait > 0) {
                scene.wait--;
                continue;
            }
            if (Talk.waiting(scene.player)) {
                // Nobody waits forever for an answer. Saying nothing is an answer.
                if (++scene.waited > ANSWER_TICKS) {
                    scene.waited = 0;
                    Talk.choose(scene.player, 0);
                }
                continue;
            }
            next(scene);
        }
    }

    /** You stop short of the line until it's clear, like anyone. Once you're on it, you keep going. */
    private static boolean trainInTheWay(Scene scene) {
        double progress = scene.age / (double) scene.ticks, length = scene.path.length();
        if (Railway.nearLine(scene.path.at(progress).x) || scene.held > LONGEST_WAIT) return false;
        for (double ahead : LOOK_FOR_TRAINS) {
            if (Railway.blocksCrossing(scene.path.at(Math.min(1, progress + ahead / length)))) return true;
        }
        return false;
    }

    private static void next(Scene scene) {
        Step step = scene.steps.poll();
        if (step == null) return;
        ServerPlayerEntity p = scene.player;
        StudentEntity by = scene.people.get(Math.min(step.speaker(), scene.people.size() - 1));
        scene.speaker = step.speaker();
        scene.waited = 0;
        String name = Roster.get(by.getSkin()).firstName();
        if (step.answers().isEmpty()) {
            Talk.say(p, name, step.line(), by.getId());
            scene.wait = hold(step.line());
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
                String reply = a.reply();
                if (reply.isEmpty()) {
                    scene.wait = GAP;
                    return;
                }
                StudentEntity speaker = scene.people.get(Math.min(a.speaker(), scene.people.size() - 1));
                Talk.say(q, Roster.get(speaker.getSkin()).firstName(), reply, speaker.getId());
                scene.speaker = a.speaker();
                scene.wait = hold(reply);
            });
        }
        Talk.say(p, name, step.line(), by.getId(), options);
        scene.wait = 20;
    }

    private static int hold(String line) {
        return 45 + line.length();
    }

    private static void finish(Scene scene) {
        scenes.remove(scene.player.getUuid());
        Traffic.release(scene.player.getUuid());
        Talk.choose(scene.player, -1);
        scene.doors.forEach(pos -> shut(scene.player.getServerWorld(), pos));
        Kokuhaku.LOG.info("Walk done: {} ticks in a wall, {} floating, {} walking sideways, of {}; {} on the line, {} of them near the train, {} waiting; never more than {} blocks from you", scene.inWall, scene.floating, scene.crabbing, scene.age * scene.people.size(), scene.onLine, scene.besideTrain, scene.held, String.format("%.1f", scene.furthest));
        for (StudentEntity e : scene.people) {
            e.setAiDisabled(false);
            e.setNoGravity(false);
            e.noClip = false;
            e.setWalkTalking(false);
            e.settle();
        }
        ServerPlayerEntity p = scene.player;
        RunManager.setBusy(p, false);
        Vec3d end = scene.path.at(1);
        p.teleport(p.getServerWorld(), end.x, end.y, end.z, p.getYaw(), 0f);
    }

    /** You'd rather just get there. */
    public static void skip(ServerPlayerEntity p) {
        if (TrainRide.on(p)) {
            TrainRide.skip(p, RunManager.state(p.server), RunManager.town());
            return;
        }
        Scene scene = scenes.get(p.getUuid());
        if (scene == null) return;
        Whispers.skippedWalk();
        finish(scene);
    }
}
