package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * People who live here and have nothing to do with any of it, walking the
 * streets so the town doesn't feel empty. They use the skins after the
 * students', and they aren't part of the mystery.
 */
public final class Townsfolk {
    private static final int PEOPLE = 60, SKINS = 12, FIRST_SKIN = Roster.TOTAL;
    /** The main road: the town is west of it, the school and its grounds east. */
    private static final int TOWN_EDGE = -636;
    private static final int WALK_REACH = 90;
    static final String RECEPTIONIST = "Receptionist";
    private static final int THIN_FROM = 6, THIN_BY = 3;
    /** From eight in the evening until six, there's almost nobody about. */
    private static final int NIGHT_FROM = 20 * 60, NIGHT_ENDS = 6 * 60, UP_LATE = 4, OUT_OF_SIGHT = 48, BACK_AT_ONCE = 3;
    private static int wanted = PEOPLE;

    private static final Random RANDOM = new Random();
    private static final List<Integer> streets = new ArrayList<>();

    private Townsfolk() {
    }

    public static boolean isOne(StudentEntity e) {
        return e.getSkin() >= FIRST_SKIN;
    }

    /** One of their faces, for someone who's only passing through. */
    static int skin(int index) {
        return FIRST_SKIN + index % SKINS;
    }

    /** Nobody who isn't up to something is out at this hour. */
    public static boolean night(int minute) {
        return minute >= NIGHT_FROM || minute < NIGHT_ENDS;
    }

    private static List<StudentEntity> walkers(ServerWorld world) {
        return new ArrayList<>(world.getEntitiesByType(Kokuhaku.STUDENT, e -> isOne(e) && !Teachers.isOne(e) && !e.hasVehicle() && !e.isAiDisabled()));
    }

    /** They go in at night and come back out in the morning, wherever you aren't looking. */
    private static void comeAndGo(MinecraftServer server, RunState s, Town town) {
        ServerWorld world = server.getOverworld();
        List<StudentEntity> people = walkers(world);
        if (night(s.minute)) {
            for (int i = UP_LATE; i < people.size(); i++) {
                if (world.getClosestPlayer(people.get(i), OUT_OF_SIGHT) == null) people.get(i).discard();
            }
            return;
        }
        for (int i = 0; i < BACK_AT_ONCE && people.size() + i < wanted && !streets.isEmpty(); i++) {
            BlockPos at = town.graph.pos(streets.get(RANDOM.nextInt(streets.size())));
            if (world.getClosestPlayer(at.getX(), at.getY(), at.getZ(), OUT_OF_SIGHT, false) != null) continue;
            StudentEntity e = Kokuhaku.STUDENT.create(world);
            if (e == null) continue;
            e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RANDOM.nextFloat() * 360f, 0f);
            e.setSkin(FIRST_SKIN + RANDOM.nextInt(SKINS));
            world.spawnEntity(e);
        }
    }

    public static void spawn(ServerWorld world, Town town) {
        wanted = PEOPLE;
        streets.clear();
        for (int n = 0; n < town.graph.size(); n++) {
            BlockPos at = town.graph.pos(n);
            // Pavement in town: they cross roads on the way, but they don't stop in them.
            if (at.getX() < TOWN_EDGE && world.isSkyVisible(at.up()) && world.getBlockState(at.down()).isOf(Blocks.BRICKS)) streets.add(n);
        }
        Kokuhaku.LOG.info("Townsfolk: {} people across {} pavement waypoints", PEOPLE, streets.size());
        for (int i = 0; i < PEOPLE && !streets.isEmpty(); i++) {
            StudentEntity e = Kokuhaku.STUDENT.create(world);
            if (e == null) continue;
            BlockPos at = town.graph.pos(streets.get(RANDOM.nextInt(streets.size())));
            e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RANDOM.nextFloat() * 360f, 0f);
            e.setSkin(FIRST_SKIN + i % SKINS);
            world.spawnEntity(e);
        }
        receptionist(world);
    }

    /** From the second week the streets get quieter. Nobody says where they've gone. */
    public static void thinOut(MinecraftServer server, int day) {
        if (day < THIN_FROM) return;
        wanted = Math.max(0, wanted - THIN_BY);
        List<StudentEntity> people = new ArrayList<>(server.getOverworld().getEntitiesByType(Kokuhaku.STUDENT, e -> isOne(e) && !Teachers.isOne(e) && !e.hasVehicle() && !e.isAiDisabled()));
        java.util.Collections.shuffle(people, RANDOM);
        for (int i = 0; i < THIN_BY && i < people.size(); i++) people.get(i).discard();
    }

    /** Behind the desk at the hospital, all day, every day. */
    private static void receptionist(ServerWorld world) {
        StudentEntity e = Kokuhaku.STUDENT.create(world);
        if (e == null) return;
        e.refreshPositionAndAngles(-604.5, 22, -200.5, 90f, 0f);
        e.setBodyYaw(90f);
        e.setHeadYaw(90f);
        e.setSkin(FIRST_SKIN + 7);
        e.setCustomName(net.minecraft.text.Text.literal(RECEPTIONIST));
        e.setAiDisabled(true);
        world.spawnEntity(e);
    }

    /** Anyone who's got where they were going picks somewhere else down the road. */
    public static void tick(MinecraftServer server, RunState s, Town town) {
        if (streets.isEmpty()) return;
        comeAndGo(server, s, town);
        for (StudentEntity e : server.getOverworld().getEntitiesByType(Kokuhaku.STUDENT, e -> isOne(e) && !Teachers.isOne(e))) {
            if (e.hasVehicle() || e.hasRoute() || e.isTalking() || e.isAiDisabled() || RANDOM.nextInt(4) != 0) continue;
            int from = town.graph.nearest(e.getPos());
            BlockPos here = town.graph.pos(from);
            for (int tries = 0; tries < 8; tries++) {
                int to = streets.get(RANDOM.nextInt(streets.size()));
                if (town.graph.pos(to).getManhattanDistance(here) > WALK_REACH) continue;
                e.setRoute("street@" + to, WalkRoute.routeFor(e, town, to), null, Float.NaN);
                break;
            }
        }
    }
}
