package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Traffic lights at every crossing. Green for the cars until someone's waiting
 * to cross, then amber, then red while they go over. Cars stop at the line on
 * red, and for anyone already on the stripes whatever the lights say. People on
 * foot wait at the kerb for their turn. Out in the road anywhere else, you're on
 * your own.
 */
public final class Crossings {
    private enum Phase { CARS, AMBER, WALK, CLEAR }

    /** Ticks: how long the cars get at least, at most with nobody waiting, and the rest. */
    private static final int CARS_AT_LEAST = 260, AMBER_TICKS = 50, WALK_TICKS = 220, CLEAR_TICKS = 50, TOO_LATE_TO_START = 50;
    /** How far a car looks ahead for a light, where it stops short of the stripes, and the point past which amber means go. */
    private static final double LOOKS_FOR = 18, STOPS_SHORT = 2.6, PAST_AMBER = 6;
    /** How close to the kerb counts as waiting. */
    private static final double AT_THE_KERB = 3.2, STRIPES = 1;
    private static final int POLE = 22, HEAD = 25, GLOW = 11;

    private static final class Crossing {
        final int x1, z1, x2, z2;
        /** True if people cross along z, so the road it's on runs along x. */
        final boolean alongZ;
        final List<BlockPos> heads = new ArrayList<>();
        Phase phase = Phase.CARS;
        int timer, waiting;

        Crossing(int[] c) {
            x1 = Math.min(c[0], c[2]);
            z1 = Math.min(c[1], c[3]);
            x2 = Math.max(c[0], c[2]);
            z2 = Math.max(c[1], c[3]);
            alongZ = x1 == x2;
        }

        /** The stripes, as a box on the ground, a little wider than painted. */
        boolean on(double x, double z, double margin) {
            return x >= x1 - margin && x <= x2 + 1 + margin && z >= z1 - margin && z <= z2 + 1 + margin;
        }

        Vec3d middle() {
            return new Vec3d((x1 + x2 + 1) / 2.0, 22, (z1 + z2 + 1) / 2.0);
        }

        double halfLength() {
            return (alongZ ? z2 - z1 + 1 : x2 - x1 + 1) / 2.0;
        }

        /** How far outside the stripes a point is, flat. */
        double distanceTo(double x, double z) {
            double dx = Math.max(Math.max(x1 - x, 0), x - (x2 + 1)), dz = Math.max(Math.max(z1 - z, 0), z - (z2 + 1));
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private static final List<Crossing> crossings = new ArrayList<>();
    private static ServerWorld world;

    private Crossings() {
    }

    public static void clear() {
        crossings.clear();
        world = null;
    }

    /** Puts a post up at each end of every crossing, beside it on the pavement. */
    static void build(ServerWorld w) {
        world = w;
        crossings.clear();
        int posts = 0;
        for (int[] c : Town.crossings()) {
            Crossing crossing = new Crossing(c);
            crossings.add(crossing);
            // The two ends, just off the stripes, then one step along the kerb so nobody walks into the pole.
            Vec3d m = crossing.middle();
            double reach = crossing.halfLength() + 1;
            for (int end : new int[]{-1, 1}) {
                for (int side : new int[]{2, -2, 3, -3}) {
                    int x = (int) Math.floor(crossing.alongZ ? m.x + side : m.x + end * reach);
                    int z = (int) Math.floor(crossing.alongZ ? m.z + end * reach : m.z + side);
                    if (!room(x, z)) continue;
                    post(crossing, x, z);
                    posts++;
                    break;
                }
            }
            show(crossing);
        }
        Kokuhaku.LOG.info("Traffic lights: {} crossings, {} posts", crossings.size(), posts);
    }

    /** Pavement underneath, nothing in the way above, and not on the road. */
    private static boolean room(int x, int z) {
        BlockPos ground = new BlockPos(x, POLE - 1, z);
        if (Town.isRoad(world, x, z, 0) || world.getBlockState(ground).getCollisionShape(world, ground).isEmpty()) return false;
        for (int y = POLE; y <= HEAD + 3; y++) if (!world.isAir(new BlockPos(x, y, z))) return false;
        return true;
    }

    private static void post(Crossing crossing, int x, int z) {
        for (int y = POLE; y < HEAD; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
        crossing.heads.add(new BlockPos(x, HEAD, z));
    }

    /** Red at the top, amber, green at the bottom; whichever's on glows. */
    private static void show(Crossing c) {
        if (world == null) return;
        boolean red = c.phase == Phase.WALK || c.phase == Phase.CLEAR, amber = c.phase == Phase.AMBER, green = c.phase == Phase.CARS;
        for (BlockPos head : c.heads) {
            set(head, green ? Blocks.LIME_CONCRETE.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState());
            set(head.up(), amber ? Blocks.ORANGE_CONCRETE.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState());
            set(head.up(2), red ? Blocks.RED_CONCRETE.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState());
            set(head.up(3), Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, GLOW));
        }
    }

    private static void set(BlockPos at, BlockState state) {
        if (!world.getBlockState(at).equals(state)) world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
    }

    static void tick(List<ServerPlayerEntity> players) {
        if (world == null) return;
        for (Crossing c : crossings) {
            // You count as waiting too, standing at the kerb.
            for (ServerPlayerEntity p : players) {
                if (!c.on(p.getX(), p.getZ(), 0) && c.distanceTo(p.getX(), p.getZ()) < AT_THE_KERB && Math.abs(p.getY() - 22) < 2) c.waiting = 40;
            }
            c.timer++;
            Phase before = c.phase;
            switch (c.phase) {
                case CARS -> {
                    if (c.waiting > 0 && c.timer >= CARS_AT_LEAST) next(c, Phase.AMBER);
                }
                case AMBER -> {
                    if (c.timer >= AMBER_TICKS) next(c, Phase.WALK);
                }
                case WALK -> {
                    if (c.timer >= WALK_TICKS) next(c, Phase.CLEAR);
                }
                case CLEAR -> {
                    if (c.timer >= CLEAR_TICKS && !anyoneOn(c)) next(c, Phase.CARS);
                }
            }
            if (c.waiting > 0) c.waiting--;
            if (c.phase != before) show(c);
        }
    }

    private static void next(Crossing c, Phase phase) {
        c.phase = phase;
        c.timer = 0;
        if (phase == Phase.WALK) c.waiting = 0;
    }

    private static boolean anyoneOn(Crossing c) {
        Box box = new Box(c.x1 - STRIPES, 21, c.z1 - STRIPES, c.x2 + 1 + STRIPES, 24, c.z2 + 1 + STRIPES);
        return !world.getEntitiesByClass(StudentEntity.class, box, e -> !e.hasVehicle()).isEmpty() || !world.getPlayers(p -> box.contains(p.getPos())).isEmpty();
    }

    /**
     * How far this car can go before it has to stop for a light or for someone on a
     * crossing, or a negative number if nothing ahead stops it.
     */
    static double stopAhead(Vec3d pos, Vec3d heading) {
        double nearest = -1;
        for (Crossing c : crossings) {
            Vec3d to = c.middle().subtract(pos);
            double ahead = to.x * heading.x + to.z * heading.z, across = Math.abs(to.x * heading.z - to.z * heading.x);
            if (ahead <= 0 || ahead > LOOKS_FOR || across > c.halfLength() + 1) continue;
            // Already over the line: on through, don't stop on the stripes.
            if (ahead < STOPS_SHORT) continue;
            boolean stop = c.phase == Phase.WALK || c.phase == Phase.CLEAR || c.phase == Phase.AMBER && ahead > PAST_AMBER || anyoneOn(c);
            if (!stop) continue;
            double room = ahead - STOPS_SHORT;
            if (nearest < 0 || room < nearest) nearest = room;
        }
        return nearest;
    }

    /** At the kerb, about to step onto a crossing while it's the cars' turn: wait. Pressing the button, as it were. */
    public static boolean waitAtKerb(StudentEntity mob, Vec3d... ahead) {
        if (world == null || mob.getWorld() != world) return false;
        double x = mob.getX(), z = mob.getZ();
        for (Crossing c : crossings) {
            if (c.on(x, z, STRIPES) || c.distanceTo(x, z) > AT_THE_KERB) continue;
            boolean crossing = false;
            for (Vec3d v : ahead) if (v != null && c.on(v.x, v.z, 0)) crossing = true;
            if (!crossing) continue;
            if (c.phase == Phase.WALK && c.timer < WALK_TICKS - TOO_LATE_TO_START) return false;
            c.waiting = 40;
            return true;
        }
        return false;
    }

    /** Developer testing only: what every light says. */
    static String status() {
        StringBuilder b = new StringBuilder();
        for (Crossing c : crossings) b.append(c.phase.name().charAt(0));
        return b.toString();
    }
}
