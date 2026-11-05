package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sometimes, as you walk out of the school after classes: a figure holding the
 * corner of a wall, leaning out to look at you. She's only a shape, so she
 * can't be told apart from anyone. Your head turns to her by itself, she holds
 * your gaze, then she turns and runs behind the wall, along a stretch you
 * can't see. By the time you get round it there's nobody there.
 */
public final class Peeker {
    private static final int NEAR = 5, IDEAL = 9, FAR = 16;
    private static final double LEAN_OUT = 0.42;
    private static final int LEAN_TICKS = 12;
    /** She bolts this far in, you set off after her, and you stand looking for this long at the end. */
    public static final int RUN_AT = 72, WALK_AT = 100, LOOK_TICKS = 55;
    private static final int TO_CORNER_TICKS = 6, RUN_TICKS = 11, RUN_BLOCKS = 3;
    private static final int ROUTE_REACH = 26;
    private static final double SPRINT_SPEED = 5.6; // blocks a second, the same as a player sprinting

    private static final Random RANDOM = new Random();
    private static StudentEntity girl;
    private static ServerPlayerEntity watcher;
    private static Vec3d hidden, out, away, beside;
    private static List<Vec3d> route = List.of();
    private static int age, retreating;

    /** Her lean, as seen by whoever's watching: 1 or -1 for the side she leans to. */
    private record Spot(Vec3d hidden, Vec3d out, Vec3d away, Vec3d beside, int side, List<Vec3d> route) {
    }

    private Peeker() {
    }

    /** Round the corner, where she was hiding. */
    public static Vec3d hiding() {
        return hidden;
    }

    /** The open side of the corner, where you run to first. */
    public static Vec3d corner() {
        return beside;
    }

    /** The way you walk there, along the ground. */
    public static List<Vec3d> route() {
        return route;
    }

    public static int sceneTicks() {
        double length = 0;
        for (int i = 1; i < route.size(); i++) length += route.get(i).distanceTo(route.get(i - 1));
        return WALK_AT + (int) (length / SPRINT_SPEED * 20) + LOOK_TICKS;
    }

    /** The way she went. */
    public static Vec3d escape() {
        return away;
    }

    /** Where you'll see her face, once she's leaned out. */
    public static Vec3d face() {
        return out.add(0, 1.5, 0);
    }

    public static boolean active() {
        return girl != null;
    }

    /** Looks for a corner ahead of the player to peer round. False if there isn't one. */
    public static boolean start(ServerPlayerEntity player, int skin) {
        if (active()) return false;
        Spot spot = findCorner(player);
        if (spot == null) return false;
        ServerWorld world = player.getServerWorld();
        StudentEntity e = Kokuhaku.STUDENT.create(world);
        if (e == null) return false;
        hidden = spot.hidden();
        out = spot.out();
        away = spot.away();
        beside = spot.beside();
        route = spot.route();
        Vec3d facing = player.getPos().subtract(hidden);
        float yaw = (float) (Math.toDegrees(Math.atan2(facing.z, facing.x)) - 90);
        e.refreshPositionAndAngles(hidden.x, hidden.y, hidden.z, yaw, 0f);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        e.setSkin(skin);
        e.setAiDisabled(true);
        e.setNoGravity(true);
        e.setPeek(spot.side());
        world.spawnEntity(e);
        girl = e;
        watcher = player;
        age = 0;
        retreating = 0;
        Kokuhaku.LOG.info("A figure at a corner: {} ", hidden);
        return true;
    }

    public static void tick() {
        if (girl == null) return;
        if (girl.isRemoved() || watcher.isRemoved()) {
            finish();
            return;
        }
        age++;
        if (retreating > 0) {
            retreating++;
            if (retreating <= TO_CORNER_TICKS) {
                place(out.lerp(hidden, retreating / (float) TO_CORNER_TICKS));
            } else {
                place(hidden.lerp(away, Math.min(1f, (retreating - TO_CORNER_TICKS) / (float) RUN_TICKS)));
            }
            if (retreating % 5 == 0) {
                // Quick footsteps, getting further off.
                girl.getWorld().playSound(null, girl.getX(), girl.getY(), girl.getZ(), SoundEvents.BLOCK_STONE_STEP, SoundCategory.AMBIENT, 0.5f, 1.1f);
            }
            if (retreating >= TO_CORNER_TICKS + RUN_TICKS) finish();
            return;
        }
        float t = Math.min(1f, age / (float) LEAN_TICKS);
        place(hidden.lerp(out, t * t * (3f - 2f * t)));
        if (age >= RUN_AT) run();
    }

    /** She turns her back on you and goes. */
    private static void run() {
        retreating = 1;
        Vec3d route = away.subtract(hidden);
        float yaw = (float) (Math.toDegrees(Math.atan2(route.z, route.x)) - 90);
        girl.setYaw(yaw);
        girl.setBodyYaw(yaw);
        girl.setHeadYaw(yaw);
        girl.setPeek(StudentEntity.RUNNING);
    }

    private static void place(Vec3d at) {
        girl.setPosition(at.x, at.y, at.z);
    }

    private static void finish() {
        Kokuhaku.LOG.info("The figure has gone");
        if (girl != null) girl.discard();
        girl = null;
        watcher = null;
    }

    private static boolean clear(ServerPlayerEntity player, Vec3d from, Vec3d to) {
        // Outlines, not collisions: this map builds some walls out of blocks you could walk through.
        var ray = new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player);
        return player.getServerWorld().raycast(ray).getType() == HitResult.Type.MISS;
    }

    /** A straight run from the corner, away from you, that you can't see any of. */
    private static Vec3d escape(ServerPlayerEntity player, Vec3d eye, BlockPos cell, Direction side, Direction towards) {
        ServerWorld world = player.getServerWorld();
        for (Direction way : new Direction[]{towards.getOpposite(), side.getOpposite()}) {
            boolean ok = true;
            for (int step = 1; step <= RUN_BLOCKS && ok; step++) {
                BlockPos next = cell.offset(way, step);
                ok = empty(world, next) && !clear(player, eye, middle(next).add(0, 1.5, 0));
            }
            if (ok) return middle(cell.offset(way, RUN_BLOCKS));
        }
        return null;
    }

    /** Nothing at all here, not even something you could walk through. */
    private static boolean empty(ServerWorld world, BlockPos pos) {
        return RunManager.standable(world, pos) && world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir();
    }

    /** The shortest way on foot, a step at a time, up and down stairs included. */
    private static List<Vec3d> walkingRoute(ServerWorld world, BlockPos from, BlockPos to) {
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        cameFrom.put(from, from);
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (at.equals(to)) break;
            for (Direction way : Direction.Type.HORIZONTAL) {
                for (int rise : new int[]{0, 1, -1}) {
                    BlockPos next = at.offset(way).up(rise);
                    if (cameFrom.containsKey(next) || next.getManhattanDistance(from) > ROUTE_REACH * 2) continue;
                    if (!RunManager.standable(world, next)) continue;
                    cameFrom.put(next, at);
                    queue.add(next);
                    break;
                }
            }
        }
        if (!cameFrom.containsKey(to)) return null;
        List<Vec3d> steps = new ArrayList<>();
        for (BlockPos at = to; ; at = cameFrom.get(at)) {
            BlockPos below = at.down();
            var shape = world.getBlockState(below).getCollisionShape(world, below);
            double floor = below.getY() + (shape.isEmpty() ? 1 : shape.getMax(Direction.Axis.Y));
            steps.add(0, new Vec3d(at.getX() + 0.5, floor, at.getZ() + 0.5));
            if (at.equals(from)) break;
        }
        return smooth(smooth(steps));
    }

    /** Cuts the corners off, so you walk a curve and not a staircase of right angles. */
    private static List<Vec3d> smooth(List<Vec3d> points) {
        if (points.size() < 3) return points;
        List<Vec3d> out = new ArrayList<>();
        out.add(points.get(0));
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d a = points.get(i), b = points.get(i + 1);
            out.add(a.lerp(b, 0.25));
            out.add(a.lerp(b, 0.75));
        }
        out.add(points.get(points.size() - 1));
        return out;
    }

    private static boolean solid(ServerWorld world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && !world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty();
    }

    private static Vec3d middle(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /**
     * A wall's end between you and a free cell: the cell's hidden from you, the
     * one beside it isn't, so someone standing at the join can lean out into view.
     * Best is a corner with a hidden run behind it, close by and in good light;
     * failing that, anything solid she can duck behind.
     */
    private static Spot findCorner(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d eye = player.getEyePos();
        BlockPos centre = player.getBlockPos();
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -FAR; dx <= FAR; dx++) {
            for (int dz = -FAR; dz <= FAR; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < NEAR || distance > FAR) continue;
                // Any direction, and the ground outside isn't flat: your head turns to find her.
                for (int dy = -3; dy <= 3; dy++) cells.add(centre.add(dx, dy, dz));
            }
        }
        cells.removeIf(c -> !RunManager.standable(world, c) || clear(player, eye, middle(c).add(0, 1.5, 0)));
        Collections.shuffle(cells, RANDOM);
        cells.sort(Comparator.comparingDouble(c -> (world.getLightLevel(LightType.SKY, c) >= 10 ? 0 : 100)
                + Math.max(0, Math.abs(Math.sqrt(c.getSquaredDistance(centre)) - IDEAL) - 3)));
        for (boolean needRun : new boolean[]{true, false}) {
            for (BlockPos cell : cells) {
                Spot spot = spotAt(player, eye, cell, needRun);
                if (spot != null) return spot;
            }
        }
        return null;
    }

    private static Spot spotAt(ServerPlayerEntity player, Vec3d eye, BlockPos cell, boolean needRun) {
        ServerWorld world = player.getServerWorld();
        Vec3d toPlayer = player.getPos().subtract(middle(cell));
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos beside = cell.offset(side);
            if (!empty(world, cell) || !empty(world, beside) || !clear(player, eye, middle(beside).add(0, 1.5, 0))) continue;
            for (Direction towards : new Direction[]{side.rotateYClockwise(), side.rotateYCounterclockwise()}) {
                boolean facesYou = towards.getOffsetX() * toPlayer.x + towards.getOffsetZ() * toPlayer.z > 0;
                if (!facesYou || !solid(world, cell.offset(towards)) || solid(world, beside.offset(towards))) continue;
                Vec3d away = escape(player, eye, cell, side, towards);
                if (away == null) {
                    if (needRun) continue;
                    // Nowhere to run: she just steps back behind it, and that's the last of her.
                    away = middle(cell).add(new Vec3d(-towards.getOffsetX(), 0, -towards.getOffsetZ()).multiply(0.4));
                }
                Vec3d lean = new Vec3d(side.getOffsetX(), 0, side.getOffsetZ());
                // Mostly behind the wall, but her head has to be in view.
                Vec3d shown = middle(cell).add(lean.multiply(LEAN_OUT));
                if (!clear(player, eye, shown.add(0, 1.5, 0))) continue;
                List<Vec3d> walk = walkingRoute(world, player.getBlockPos(), cell);
                if (walk == null) continue;
                Vec3d look = toPlayer.normalize();
                int leanSide = look.x * lean.z - look.z * lean.x > 0 ? 1 : -1;
                return new Spot(middle(cell), shown, away, middle(beside), leanSide, walk);
            }
        }
        return null;
    }
}
