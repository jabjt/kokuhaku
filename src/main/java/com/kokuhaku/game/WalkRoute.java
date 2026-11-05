package com.kokuhaku.game;

import com.kokuhaku.world.town.Town;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a walk out on the map itself. The waypoints can be ten blocks apart, so
 * the line between two of them may cut a corner or hang in the air over a
 * flight of steps. This walks every leg a step at a time, standing on what's
 * really there, and goes round anything that's in the way.
 */
final class WalkRoute {
    private static final int CANDIDATES = 30;
    private static final double STEP = 0.5, CHECK = 0.25, RADIUS = 0.28, TOP = 1.75;
    /** Routes keep this far off anything, so the rounded corners don't graze it. */
    private static final double CLEARANCE = 0.42;
    /** Anything lower than this is something you step up onto, not a wall. */
    private static final double KNEE = 0.62;
    private static final double RAY_UP = 1.2, RAY_DOWN = 1.6, BIGGEST_STEP = 1.1;
    private static final int SEARCH_MARGIN = 10, SEARCH_LIMIT = 8000, SMOOTHING = 2;
    /** How far a straight line is tried in one go, how finely it's shortened, and how much corners are eased. */
    private static final int LONGEST_LINE = 100, SHORTEN_BY = 6, ROUNDING = 3;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private record Cell(int x, int y, int z) {
    }

    private WalkRoute() {
    }

    private static boolean swings(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    /** The height of whatever you'd be standing on here, near a height you're already at. Null over a drop. */
    static Double ground(ServerWorld world, double x, double z, double near, Entity who) {
        for (double up : new double[]{0.3, 1.3}) {
            if (swings(world.getBlockState(BlockPos.ofFloored(x, near + up, z)))) return near;
        }
        Vec3d from = new Vec3d(x, near + RAY_UP, z), to = new Vec3d(x, near - RAY_DOWN, z);
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, who));
        // Nobody walks along the top of a hedge, a railing or a window.
        if (hit.getType() == HitResult.Type.MISS) return null;
        var top = world.getBlockState(hit.getBlockPos()).getBlock();
        if (top instanceof PaneBlock || top instanceof net.minecraft.block.LeavesBlock || top instanceof net.minecraft.block.FenceBlock) return null;
        if (com.kokuhaku.world.town.Furniture.at(world, hit.getBlockPos())) return null;
        return hit.getPos().y;
    }

    /** Whether a body fits here: the steps at your feet don't count, and doors open. */
    static boolean fits(ServerWorld world, double x, double y, double z) {
        return fits(world, x, y, z, RADIUS);
    }

    private static boolean fits(ServerWorld world, double x, double y, double z, double radius) {
        Box body = new Box(x - radius, y + KNEE, z - radius, x + radius, y + TOP, z + radius);
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(body.minX, body.minY, body.minZ), BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ))) {
            BlockState state = world.getBlockState(pos);
            if (swings(state)) continue;
            // Thin to look at, but nobody goes through them or squeezes past: it's a wall.
            if (state.getBlock() instanceof PaneBlock) {
                if (new Box(pos).intersects(body)) return false;
                continue;
            }
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) continue;
            for (Box part : shape.getBoundingBoxes()) if (part.offset(pos).intersects(body)) return false;
        }
        return true;
    }

    /** The straight line between two points, on the ground, if a body can really walk it. Null if it can't. */
    private static List<Vec3d> straight(ServerWorld world, Vec3d a, Vec3d b, Entity who) {
        return straight(world, a, b, who, CLEARANCE, false);
    }

    /** Whether someone could stand there and get there from here: a tighter fit than a route needs. */
    static boolean reachable(ServerWorld world, Vec3d from, Vec3d to, Entity who) {
        return straight(world, from, to, who, RADIUS, true) != null;
    }

    /** Whether a body can really get from one to the other, on the ground, with what's actually built there. */
    static boolean walkable(ServerWorld world, Vec3d a, Vec3d b, Entity who) {
        return straight(world, a, b, who, RADIUS, false) != null || around(world, a, b, who) != null;
    }

    /** The nearest waypoint you can walk to from here: the closest one can be the far side of a window. */
    static int startNode(ServerWorld world, Town town, Vec3d at, Entity who) {
        List<Integer> near = town.graph.nearestFew(at, CANDIDATES);
        for (int n : near) {
            if (straight(world, at, Vec3d.ofBottomCenter(town.graph.pos(n)), who, RADIUS, false) != null) return n;
        }
        return near.get(0);
    }

    /** A player's way, for the same. */
    static List<Vec3d> routeTo(net.minecraft.server.network.ServerPlayerEntity p, Town town, int to) {
        List<Vec3d> points = new ArrayList<>(List.of(p.getPos()));
        for (int n : town.graph.path(startNode(p.getServerWorld(), town, p.getPos(), p), to)) points.add(Vec3d.ofBottomCenter(town.graph.pos(n)));
        return points;
    }

    /** A student's way from where they're standing to a waypoint. */
    static List<Vec3d> routeFor(com.kokuhaku.entity.StudentEntity e, Town town, int to) {
        List<Vec3d> points = new ArrayList<>();
        for (int n : town.graph.path(startNode((ServerWorld) e.getWorld(), town, e.getPos(), e), to)) points.add(Vec3d.ofBottomCenter(town.graph.pos(n)));
        return points;
    }

    /** Grass, earth and gravel: walkable, but not where anyone walks to school. */
    static boolean offThePavement(ServerWorld world, double x, double y, double z) {
        BlockState under = world.getBlockState(BlockPos.ofFloored(x, y - 0.3, z));
        return under.isOf(Blocks.GRASS_BLOCK) || under.isOf(Blocks.DIRT) || under.isOf(Blocks.COARSE_DIRT) || under.isOf(Blocks.GRAVEL) || under.isOf(Blocks.SAND);
    }

    private static List<Vec3d> straight(ServerWorld world, Vec3d a, Vec3d b, Entity who, double radius, boolean paved) {
        double length = Math.sqrt(a.squaredDistanceTo(b.x, a.y, b.z));
        int steps = Math.max(1, (int) Math.ceil(length / CHECK));
        int every = Math.max(1, (int) Math.round(STEP / CHECK));
        List<Vec3d> out = new ArrayList<>();
        double y = a.y;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, x = a.x + (b.x - a.x) * t, z = a.z + (b.z - a.z) * t;
            Double g = ground(world, x, z, y, who);
            if (g == null || Math.abs(g - y) > BIGGEST_STEP || !fits(world, x, g, z, radius) || isRoad(world, x, z)) return null;
            if (paved && (offThePavement(world, x, g, z) || Railway.nearLine(x))) return null;
            y = g;
            if (i % every == 0 || i == steps) out.add(new Vec3d(x, y, z));
        }
        return out;
    }

    /** Every block you could stand on near this leg, searched outwards until it reaches the far end. */
    private static List<Vec3d> around(ServerWorld world, Vec3d a, Vec3d b, Entity who) {
        Double startY = ground(world, a.x, a.z, a.y, who);
        if (startY == null) return null;
        int goalX = (int) Math.floor(b.x), goalZ = (int) Math.floor(b.z);
        int minX = (int) Math.floor(Math.min(a.x, b.x)) - SEARCH_MARGIN, maxX = (int) Math.floor(Math.max(a.x, b.x)) + SEARCH_MARGIN;
        int minZ = (int) Math.floor(Math.min(a.z, b.z)) - SEARCH_MARGIN, maxZ = (int) Math.floor(Math.max(a.z, b.z)) + SEARCH_MARGIN;
        Cell start = new Cell((int) Math.floor(a.x), (int) Math.floor(startY + 0.01), (int) Math.floor(a.z));
        Map<Cell, Cell> came = new HashMap<>();
        Map<Cell, Double> heights = new HashMap<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>(List.of(start));
        came.put(start, start);
        heights.put(start, startY);
        while (!queue.isEmpty() && came.size() < SEARCH_LIMIT) {
            Cell at = queue.poll();
            if (at.x() == goalX && at.z() == goalZ && Math.abs(heights.get(at) - b.y) < 1.6) {
                List<Vec3d> out = new ArrayList<>();
                for (Cell c = at; c != start; c = came.get(c)) out.add(new Vec3d(c.x() + 0.5, heights.get(c), c.z() + 0.5));
                out.add(a);
                Collections.reverse(out);
                return out;
            }
            double y = heights.get(at);
            for (int[] d : DIRECTIONS) {
                int x = at.x() + d[0], z = at.z() + d[1];
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                Double g = ground(world, x + 0.5, z + 0.5, y, who);
                if (g == null || Math.abs(g - y) > BIGGEST_STEP || !fits(world, x + 0.5, g, z + 0.5, CLEARANCE) || isRoad(world, x + 0.5, z + 0.5)) continue;
                // No cutting across the corner of a wall.
                if (d[0] != 0 && d[1] != 0 && (!fits(world, at.x() + 0.5, y, z + 0.5) || !fits(world, x + 0.5, y, at.z() + 0.5))) continue;
                Cell next = new Cell(x, (int) Math.floor(g + 0.01), z);
                if (came.containsKey(next)) continue;
                came.put(next, at);
                heights.put(next, g);
                queue.add(next);
            }
        }
        return null;
    }

    private static boolean isRoad(ServerWorld world, double x, double z) {
        return Town.isRoad(world, (int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * Waypoints wander from side to side of a pavement, and nobody walks like
     * that. From each point this takes the longest straight line the walls
     * allow, so the route only turns where a person would have to.
     */
    private static List<Vec3d> straighten(ServerWorld world, List<Vec3d> route, Entity who) {
        List<Vec3d> out = new ArrayList<>(List.of(route.get(0)));
        int at = 0;
        while (at < route.size() - 1) {
            int to = Math.min(route.size() - 1, at + LONGEST_LINE);
            List<Vec3d> line = null;
            for (; to > at + 1; to -= SHORTEN_BY) {
                // Only the waypoints go over the rails; a straight line never gets to choose where.
                line = straight(world, route.get(at), route.get(to), who, CLEARANCE, true);
                if (line != null) break;
            }
            if (line == null) {
                to = at + 1;
                line = List.of(route.get(to));
            }
            out.addAll(line);
            at = to;
        }
        return out;
    }

    /** Takes the corners the way feet do, unless that would put you in something. */
    private static List<Vec3d> eased(ServerWorld world, List<Vec3d> route) {
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            int from = Math.max(0, i - ROUNDING), to = Math.min(route.size() - 1, i + ROUNDING);
            double x = 0, z = 0;
            for (int n = from; n <= to; n++) {
                x += route.get(n).x;
                z += route.get(n).z;
            }
            Vec3d p = route.get(i);
            double ax = x / (to - from + 1), az = z / (to - from + 1);
            out.add(fits(world, ax, p.y, az) ? new Vec3d(ax, p.y, az) : p);
        }
        return out;
    }

    /** The whole walk, a short step at a time, on the ground. */
    static List<Vec3d> fit(ServerWorld world, List<Vec3d> waypoints, Entity who) {
        List<Vec3d> route = new ArrayList<>();
        Vec3d at = waypoints.get(0);
        Double first = ground(world, at.x, at.z, at.y, who);
        if (first != null) at = new Vec3d(at.x, first, at.z);
        route.add(at);
        for (int i = 1; i < waypoints.size(); i++) {
            Vec3d next = waypoints.get(i);
            List<Vec3d> leg = straight(world, at, next, who);
            if (leg == null) {
                List<Vec3d> cells = around(world, at, next, who);
                if (cells == null) {
                    // Nothing on the ground reaches it: leave this leg out and aim for the one after.
                    if (i < waypoints.size() - 1) continue;
                    cells = List.of(at, next);
                }
                leg = new ArrayList<>();
                for (int c = 1; c < cells.size(); c++) {
                    List<Vec3d> piece = straight(world, cells.get(c - 1), cells.get(c), who);
                    leg.addAll(piece != null ? piece : List.of(cells.get(c)));
                }
            }
            route.addAll(leg);
            at = route.get(route.size() - 1);
        }
        route = eased(world, straighten(world, route, who));
        // Steps are sudden, but a walk up them isn't.
        List<Vec3d> smooth = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            double y = 0;
            int from = Math.max(0, i - SMOOTHING), to = Math.min(route.size() - 1, i + SMOOTHING);
            for (int n = from; n <= to; n++) y += route.get(n).y;
            Vec3d p = route.get(i);
            smooth.add(new Vec3d(p.x, y / (to - from + 1), p.z));
        }
        return smooth;
    }
}
