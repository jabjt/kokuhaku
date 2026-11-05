package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mt. Chinetsu, at the end of the line: a snow slope in the empty corner of the
 * island, with a hire hut at the bottom. A run costs a little and takes a minute,
 * and it's the one place in town with nobody around to watch you.
 */
final class SkiSlope {
    private static final int FLOOR = 22, TOP_Y = 52;
    private static final int BOTTOM_X = -462, TOP_X = -556, FLAT_TO_X = -450, NORTH = -258, SOUTH = -206, LAYERS = 8;
    private static final int HUT_WEST = -462, HUT_EAST = -454, HUT_NORTH = -204, HUT_SOUTH = -198, HUT_HEIGHT = 4;
    private static final int TREE_EVERY = 9, TREE_TRUNK = 5;
    private static final Vec3d HIRE = new Vec3d(-452.5, FLOOR, -203.5);
    private static final double HIRE_REACH = 3.5, SWING = 16, BLOCKS_PER_TICK = 0.5, SWINGS = 3 * Math.PI;
    private static final int COST = 500, FADE = 14, POINTS = 70, MIDDLE_Z = (NORTH + SOUTH) / 2;
    /** Where to look for the top, how fine a search, and how the way down is found. */
    private static final double SKI_HEIGHT = 1.5, STEADY_DROP = 0.7;
    private static final int TREELINE = 120, SMOOTH = 5, FOLLOW_EVERY = 10, BARE_STEPS = 12, SNOW_FROM = 30, PEAK_X = -291, PEAK_Z = -621, MOST_STEPS = 600, CARVE_ANGLES = 5;
    private static final double SKI_STEP = 2.0, CARVE_STEP = 0.24, UPHILL = 12, STRAY = 0.35;
    private static final Set<UUID> asked = new HashSet<>();
    private static int askedOn = 0;

    private SkiSlope() {
    }

    static void clear() {
        asked.clear();
        askedOn = 0;
    }

    private static double height(double x) {
        double up = Math.max(0, (BOTTOM_X - x) / (BOTTOM_X - TOP_X));
        return FLOOR + up * (TOP_Y - FLOOR);
    }

    private static void tree(ServerWorld world, int x, int z, int flags) {
        for (int y = 0; y < TREE_TRUNK; y++) world.setBlockState(new BlockPos(x, FLOOR + y, z), Blocks.SPRUCE_LOG.getDefaultState(), flags);
        BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
        for (int y = 2; y <= TREE_TRUNK; y++) {
            int reach = (TREE_TRUNK + 1 - y) / 2 + 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos at = new BlockPos(x + dx, FLOOR + y, z + dz);
                    if (Math.abs(dx) + Math.abs(dz) <= reach + 1 && world.isAir(at)) world.setBlockState(at, leaves, flags);
                }
            }
        }
    }

    private static void hut(ServerWorld world, int flags) {
        BlockState wall = Blocks.SPRUCE_PLANKS.getDefaultState();
        for (int x = HUT_WEST; x <= HUT_EAST; x++) {
            for (int z = HUT_NORTH; z <= HUT_SOUTH; z++) {
                boolean edge = x == HUT_WEST || x == HUT_EAST || z == HUT_NORTH || z == HUT_SOUTH;
                for (int y = 0; y < HUT_HEIGHT; y++) {
                    boolean door = x == HUT_EAST && z == (HUT_NORTH + HUT_SOUTH) / 2 && y < 2;
                    world.setBlockState(new BlockPos(x, FLOOR + y, z), edge && !door ? wall : Blocks.AIR.getDefaultState(), flags);
                }
                world.setBlockState(new BlockPos(x, FLOOR + HUT_HEIGHT, z), Blocks.SNOW_BLOCK.getDefaultState(), flags);
            }
        }
        BlockPos sign = BlockPos.ofFloored(HIRE);
        world.setBlockState(sign, Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION, 4), flags);
        if (world.getBlockEntity(sign) instanceof SignBlockEntity board) {
            board.setText(new SignText().withMessage(1, Text.literal("Ski hire")).withMessage(2, Text.literal("¥" + COST)), true);
            board.setWaxed(true);
            board.markDirty();
        }
    }

    /** Whether the real mountains came with this copy of the game. Without them, there's the old slope. */
    static boolean mountains() {
        return java.nio.file.Files.isDirectory(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kokuhaku-map"));
    }

    private static int ground(ServerWorld world, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        world.getChunk(bx >> 4, bz >> 4);
        return world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /**
     * Off the top of the nearest real peak and down the way the snow goes: each step the lowest
     * ground that's still roughly towards the hut, round the trees and never back uphill if it can help it.
     */
    private static List<Vec3d> mountainRoute(ServerWorld world) {
        // The top of the south ridge, found when the map was put together.
        int bestX = PEAK_X, bestZ = PEAK_Z, best = ground(world, PEAK_X, PEAK_Z);
        List<Vec3d> points = new ArrayList<>();
        Vec3d at = new Vec3d(bestX + 0.5, best, bestZ + 0.5);
        points.add(at);
        Vec3d hut = new Vec3d(HIRE.x - 6, FLOOR, HIRE.z - 8);
        // Down to where the snow runs out. From there it's a walk, and the run ends.
        int bare = 0;
        for (int step = 0; step < MOST_STEPS && at.squaredDistanceTo(hut.x, at.y, hut.z) > 9 && bare < BARE_STEPS; step++) {
            double toHut = Math.atan2(hut.z - at.z, hut.x - at.x), bestScore = Double.MAX_VALUE;
            Vec3d next = null;
            for (int a = -CARVE_ANGLES; a <= CARVE_ANGLES; a++) {
                double angle = toHut + a * CARVE_STEP;
                double x = at.x + Math.cos(angle) * SKI_STEP, z = at.z + Math.sin(angle) * SKI_STEP;
                int y = ground(world, x, z);
                // Downhill is good, uphill is very bad, and straying from the hut costs a little.
                // A piste, not a fall: a steady drop, across the face where it's steep. Uphill is very bad.
                double rise = y - at.y, score = (rise > 0 ? rise * UPHILL : Math.abs(-rise - STEADY_DROP)) + Math.abs(a) * STRAY;
                // Never back over where you've just been: that's how you get stuck in a hollow.
                for (int back = Math.max(0, points.size() - 25); back < points.size() - 1; back++) {
                    Vec3d was = points.get(back);
                    if ((was.x - x) * (was.x - x) + (was.z - z) * (was.z - z) < 4) score += 1000;
                }
                if (score < bestScore) {
                    bestScore = score;
                    next = new Vec3d(x, y, z);
                }
            }
            at = next;
            points.add(at);
            boolean snow = world.getBlockState(BlockPos.ofFloored(at.x, at.y - 1, at.z)).isIn(net.minecraft.registry.tag.BlockTags.SNOW) || world.getBlockState(BlockPos.ofFloored(at.x, at.y, at.z)).isOf(Blocks.SNOW);
            bare = snow || step < SNOW_FROM ? 0 : bare + 1;
            if (bare >= BARE_STEPS) Kokuhaku.LOG.info("Ski run: off the snow at {} {} {}", (int) at.x, (int) at.y, (int) at.z);
            if (at.y < TREELINE) break;
        }
        // The last of it back onto the snow.
        for (int i = 0; i < BARE_STEPS && points.size() > 2; i++) points.remove(points.size() - 1);
        // Bumps smoothed, so the camera rides the snow instead of every block of it.
        List<Vec3d> smooth = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            double y = 0;
            int from = Math.max(0, i - SMOOTH), to = Math.min(points.size() - 1, i + SMOOTH);
            for (int n = from; n <= to; n++) y += points.get(n).y;
            // Averaged, but never down into the mountain: over a ridge the average is under the snow.
            Vec3d here = points.get(i), next = points.get(Math.min(points.size() - 1, i + 1));
            double floor = Math.max(here.y, ground(world, (here.x + next.x) / 2, (here.z + next.z) / 2));
            smooth.add(new Vec3d(here.x, Math.max(y / (to - from + 1), floor) + SKI_HEIGHT, here.z));
        }
        Kokuhaku.LOG.info("Ski run from {} {} {}, {} points", bestX, best, bestZ, smooth.size());
        return smooth;
    }

    static void build(ServerWorld world) {
        int flags = Block.NOTIFY_LISTENERS;
        if (mountains()) {
            // The mountain's real now. Only the hut's needed.
            hut(world, flags);
            Kokuhaku.LOG.info("Mt. Chinetsu: hut built, the mountain's the real one");
            return;
        }
        for (int x = TOP_X; x <= FLAT_TO_X; x++) {
            double top = height(x);
            int whole = (int) top;
            int layers = (int) ((top - whole) * LAYERS);
            for (int z = NORTH; z <= SOUTH; z++) {
                for (int y = FLOOR - 1; y < whole; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.SNOW_BLOCK.getDefaultState(), flags);
                world.setBlockState(new BlockPos(x, FLOOR - 1, z), Blocks.SNOW_BLOCK.getDefaultState(), flags);
                if (layers > 0) world.setBlockState(new BlockPos(x, whole, z), Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers), flags);
            }
        }
        for (int x = TOP_X; x <= FLAT_TO_X; x += TREE_EVERY) {
            tree(world, x, NORTH - 3, flags);
            tree(world, x + 4, SOUTH + 4, flags);
        }
        hut(world, flags);
        Kokuhaku.LOG.info("Mt. Chinetsu: slope built");
    }

    /** Stopped at the hut with money in your pocket, you get asked once a day. */
    static void tick(RunState s, List<ServerPlayerEntity> living, Town town) {
        if (Living.active(s) || s.devoted) return;
        if (askedOn != s.day) {
            askedOn = s.day;
            asked.clear();
        }
        for (ServerPlayerEntity p : living) {
            if (RunManager.inScene(p) || p.getPos().squaredDistanceTo(HIRE) > HIRE_REACH * HIRE_REACH || !asked.add(p.getUuid())) continue;
            List<Talk.Option> options = new ArrayList<>(List.of(Talk.option("Go on then.", q -> run(q, s, town))));
            if (Story.runs() >= Story.BOOK_FROM) options.add(Talk.option("Can I see the visitors' book?", q -> {
                if (!s.notes.contains(Story.book())) s.notes.add(Story.book());
                s.markDirty();
                Talk.say(q, "The visitors' book", Story.book(), Talk.leave("..."));
            }));
            options.add(Talk.leave("Not today."));
            Talk.say(p, "Ski hire", "One run, top to bottom. ¥" + COST + ". Nobody else has been up there all week.", options.toArray(new Talk.Option[0]));
        }
    }

    private static List<Vec3d> route() {
        List<Vec3d> points = new ArrayList<>();
        for (int i = 0; i <= POINTS; i++) {
            double t = i / (double) POINTS, x = TOP_X + 4 + (BOTTOM_X + 2 - TOP_X - 4) * t;
            points.add(new Vec3d(x, height(x) + 0.1, MIDDLE_Z + Math.sin(t * SWINGS) * SWING * (1 - t * 0.5)));
        }
        return points;
    }

    /** Developer testing only. */
    static void devRun(ServerPlayerEntity p, RunState s, Town town) {
        s.yen += COST;
        run(p, s, town);
    }

    private static void run(ServerPlayerEntity p, RunState s, Town town) {
        if (s.yen < COST) {
            RunManager.tell(p, "", "You can't afford it.");
            return;
        }
        s.yen -= COST;
        s.markDirty();
        Shadow.saw(s, "You went up the mountain alone. She was already at the top.");
        List<Vec3d> way = mountains() ? mountainRoute(p.getServerWorld()) : route();
        double length = 0;
        for (int i = 1; i < way.size(); i++) length += way.get(i).distanceTo(way.get(i - 1));
        int ticks = (int) (length / BLOCKS_PER_TICK);
        Vec3d top = way.get(0), bottom = mountains() ? new Vec3d(HIRE.x - 6, FLOOR, HIRE.z - 8) : way.get(way.size() - 1);
        RunManager.setBusy(p, true);
        RunManager.sendBlack(p, town.homes.get(0), FADE * 2, "");
        float facing = (float) (Math.toDegrees(Math.atan2(way.get(1).z - top.z, way.get(1).x - top.x)) - 90);
        RunManager.later(FADE, () -> p.teleport(p.getServerWorld(), top.x, top.y + 1, top.z, facing, 0f));
        RunManager.later(FADE * 2, () -> RunManager.sendWalk(p, way, ticks));
        // You go down with the camera, so the mountain below is there when you reach it.
        PathWalker down = new PathWalker(way);
        for (int t = FOLLOW_EVERY; t < ticks; t += FOLLOW_EVERY) {
            double done = t / (double) ticks;
            RunManager.later(FADE * 2 + t, () -> {
                Vec3d here = down.at(done);
                p.teleport(p.getServerWorld(), here.x, here.y, here.z, p.getYaw(), 0f);
            });
        }
        RunManager.later(FADE * 2 + ticks / 2, () -> RunManager.tell(p, "", "There's someone at the top of the mountain, where you were. They weren't there before."));
        RunManager.later(FADE * 2 + ticks + 2, () -> {
            p.teleport(p.getServerWorld(), bottom.x, bottom.y + 0.5, bottom.z, p.getYaw(), 0f);
            RunManager.setBusy(p, false);
        });
    }
}
