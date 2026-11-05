package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The forest in the valley behind the mountain. Nobody has a reason to go there,
 * which is the reason she does. Footsteps that keep pace with yours and stop when
 * you stop; someone standing between the trees, who isn't there when you look
 * properly; and once, a clearing with candles still burning.
 */
final class ForestScares {
    /** Where the trees are, once the mountains are in. */
    private static final int WEST = -1400, EAST = 500, NORTH = -2410, SOUTH = -1210;
    private static final int CHECK_EVERY = 5, LEAF_REACH = 10, LEAF_TRIES = 16;
    private static final int STEPS_FROM = 1200, STEPS_MORE = 1800, ECHO_TICKS = 200, ECHO_DELAY = 7, STOPPED_TICKS = 20;
    private static final int FIGURE_FROM = 1600, FIGURE_MORE = 2400, FIGURE_LASTS = 600, CLEARING_AFTER = 1200;
    private static final double BEHIND = 7, STRIDE = 1.6, FIGURE_FAR = 28, FIGURE_NEAR = 14, LOOKED_AT = 0.975, CLEARING_REACH = 4, CLEARING_AHEAD = 30;
    private static final Random RANDOM = new Random();
    /** Developer testing only: everything happens straight away. */
    private static final boolean TESTING = "forest".equals(System.getenv("KOKUHAKU_STAGE"));

    private static final class Visit {
        int inside, nextSteps, echoUntil, stoppedFor, nextFigure, lookedFor;
        Vec3d lastStep, lastPos;
        StudentEntity figure;
        int figureSince;
    }

    private static final Map<UUID, Visit> visits = new HashMap<>();
    private static final List<Entity> clearing = new ArrayList<>();
    private static BlockPos clearingAt = null;
    private static boolean clearingSeen = false;
    private static int ticks = 0;

    private ForestScares() {
    }

    static void clear() {
        visits.values().forEach(v -> {
            if (v.figure != null) v.figure.discard();
        });
        visits.clear();
        clearing.clear();
        clearingAt = null;
        clearingSeen = false;
        ticks = 0;
    }

    /** In the box, and with trees all round, not just near the edge of it. */
    private static boolean inTheTrees(ServerPlayerEntity p) {
        if (p.getX() < WEST || p.getX() > EAST || p.getZ() < NORTH || p.getZ() > SOUTH) return false;
        ServerWorld world = p.getServerWorld();
        int leaves = 0;
        // Needles overhead, here and there around you: the canopy, however tall the trunks.
        for (int i = 0; i < LEAF_TRIES; i++) {
            int x = p.getBlockX() + RANDOM.nextInt(LEAF_REACH * 2 + 1) - LEAF_REACH, z = p.getBlockZ() + RANDOM.nextInt(LEAF_REACH * 2 + 1) - LEAF_REACH;
            int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
            if (top > p.getBlockY() + 2 && world.getBlockState(new BlockPos(x, top - 1, z)).getBlock() instanceof net.minecraft.block.LeavesBlock) leaves++;
        }
        if (leaves >= 3) return true;
        leaves = 0;
        for (int i = 0; i < LEAF_TRIES; i++) {
            BlockPos at = p.getBlockPos().add(RANDOM.nextInt(LEAF_REACH * 2 + 1) - LEAF_REACH, RANDOM.nextInt(16), RANDOM.nextInt(LEAF_REACH * 2 + 1) - LEAF_REACH);
            // Tall spruce: bare trunks for the first dozen blocks, needles above.
            net.minecraft.block.BlockState state = world.getBlockState(at);
            if (state.getBlock() instanceof net.minecraft.block.LeavesBlock || state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) leaves++;
        }
        return leaves >= 2;
    }

    /** Developer testing only: the thickest bit of forest, measured by needles overhead. */
    static BlockPos thickest(ServerWorld world) {
        BlockPos best = null;
        int most = -1;
        for (int x = WEST + 100; x <= EAST - 100; x += 48) {
            for (int z = NORTH + 60; z <= SOUTH - 40; z += 48) {
                world.getChunk(x >> 4, z >> 4);
                int n = 0;
                for (int dx = -6; dx <= 6; dx += 3) {
                    for (int dz = -6; dz <= 6; dz += 3) {
                        int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x + dx, z + dz);
                        if (world.getBlockState(new BlockPos(x + dx, top - 1, z + dz)).getBlock() instanceof net.minecraft.block.LeavesBlock) n++;
                    }
                }
                if (n > most) {
                    most = n;
                    best = new BlockPos(x, world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                }
            }
        }
        com.kokuhaku.Kokuhaku.LOG.info("Thickest forest at {}, {} of 25 under needles", best == null ? "nowhere" : best.toShortString(), most);
        return best;
    }

    static void tick(RunState s, List<ServerPlayerEntity> living) {
        if (++ticks % CHECK_EVERY != 0 || !SkiSlope.mountains() || Living.active(s)) return;
        for (ServerPlayerEntity p : living) {
            Visit v = visits.computeIfAbsent(p.getUuid(), k -> {
                Visit fresh = new Visit();
                fresh.nextSteps = ticks + (TESTING ? 20 : STEPS_FROM / 3);
                fresh.nextFigure = ticks + (TESTING ? 40 : FIGURE_FROM / 2);
                if (TESTING) fresh.inside = CLEARING_AFTER - 200;
                return fresh;
            });
            boolean busy = RunManager.inScene(p) || Talk.waiting(p) || p.hasVehicle();
            if (TESTING && ticks % 100 == 0) com.kokuhaku.Kokuhaku.LOG.info("Forest check: scene {} talk {} riding {} trees {} at {}", RunManager.inScene(p), Talk.waiting(p), p.hasVehicle(), inTheTrees(p), p.getBlockPos().toShortString());
            if (busy || !inTheTrees(p)) {
                // A gap in the trees isn't leaving the forest: time in there wears off slowly.
                v.inside = busy ? 0 : Math.max(0, v.inside - 60);
                v.echoUntil = 0;
                vanish(v, false);
                continue;
            }
            v.inside += CHECK_EVERY;
            footsteps(p, v);
            figure(p, s, v);
            clearing(p, s, v);
        }
    }

    /** Your steps, a moment late and from behind you. When you stop, there's one more. */
    private static void footsteps(ServerPlayerEntity p, Visit v) {
        Vec3d now = p.getPos();
        boolean moving = v.lastPos != null && v.lastPos.squaredDistanceTo(now) > 0.04;
        v.lastPos = now;
        if (v.echoUntil == 0 && ticks >= v.nextSteps && moving) {
            v.echoUntil = ticks + ECHO_TICKS;
            v.lastStep = now;
            v.stoppedFor = 0;
        }
        if (v.echoUntil == 0) return;
        if (moving) {
            v.stoppedFor = 0;
            if (v.lastStep.squaredDistanceTo(now) > STRIDE * STRIDE) {
                v.lastStep = now;
                step(p);
            }
        } else if ((v.stoppedFor += CHECK_EVERY) >= STOPPED_TICKS || ticks > v.echoUntil) {
            // You stopped. So did they. Nearly.
            step(p);
            v.echoUntil = 0;
            v.nextSteps = ticks + STEPS_FROM + RANDOM.nextInt(STEPS_MORE);
        }
    }

    private static void step(ServerPlayerEntity p) {
        Vec3d behind = p.getPos().subtract(p.getRotationVector().multiply(1, 0, 1).normalize().multiply(BEHIND));
        RunManager.later(ECHO_DELAY, () -> p.getServerWorld().playSound(null, behind.x, behind.y, behind.z, SoundEvents.BLOCK_SNOW_STEP, SoundCategory.HOSTILE, 0.55f, 0.85f));
    }

    /** Someone between the trees, well ahead. Look at them properly, or get close, and there's nobody. */
    private static void figure(ServerPlayerEntity p, RunState s, Visit v) {
        ServerWorld world = p.getServerWorld();
        if (v.figure == null) {
            boolean dusk = s.minute >= Schedule.EVENING || s.minute < 5 * 60;
            if (ticks < v.nextFigure || !dusk && RANDOM.nextInt(3) != 0 || s.mystery().yanderes.isEmpty()) return;
            double angle = Math.toRadians(p.getYaw() + 90 + (RANDOM.nextDouble() - 0.5) * 50);
            double x = p.getX() + Math.cos(angle) * FIGURE_FAR, z = p.getZ() + Math.sin(angle) * FIGURE_FAR;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(y - p.getY()) > 8) {
                v.nextFigure = ticks + 200;
                return;
            }
            StudentEntity her = Kokuhaku.STUDENT.create(world);
            if (her == null) return;
            float facing = (float) (Math.toDegrees(Math.atan2(p.getZ() - z, p.getX() - x)) - 90);
            her.refreshPositionAndAngles(x, y, z, facing, 0f);
            her.setBodyYaw(facing);
            her.setHeadYaw(facing);
            her.setSkin(s.mystery().yanderes.get(0));
            her.setPeek(StudentEntity.SILHOUETTE);
            her.setAiDisabled(true);
            world.spawnEntity(her);
            v.figure = her;
            v.figureSince = ticks;
            v.lookedFor = 0;
            Shadow.saw(s, "She followed you into the trees, and stood where you'd see her if you didn't look straight at her.");
            Kokuhaku.LOG.info("Someone between the trees at {} {} {}", (int) x, y, (int) z);
            return;
        }
        Vec3d to = v.figure.getEyePos().subtract(p.getEyePos());
        double distance = to.length();
        boolean looking = p.getRotationVector().dotProduct(to.normalize()) > LOOKED_AT && p.canSee(v.figure);
        v.lookedFor = looking ? v.lookedFor + CHECK_EVERY : 0;
        // She keeps her face to you, the whole time.
        float facing = (float) (Math.toDegrees(Math.atan2(p.getZ() - v.figure.getZ(), p.getX() - v.figure.getX())) - 90);
        v.figure.setHeadYaw(facing);
        v.figure.setBodyYaw(facing);
        if (v.lookedFor >= 10 || distance < FIGURE_NEAR || ticks - v.figureSince > FIGURE_LASTS) vanish(v, true);
    }

    private static void vanish(Visit v, boolean seen) {
        if (v.figure == null) return;
        if (seen) v.figure.getWorld().playSound(null, v.figure.getBlockPos(), SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.HOSTILE, 0.3f, 0.6f);
        v.figure.discard();
        v.figure = null;
        v.nextFigure = ticks + FIGURE_FROM + RANDOM.nextInt(FIGURE_MORE);
    }

    /** Once. Somewhere ahead of you, deep enough in that nobody else would ever find it. */
    private static void clearing(ServerPlayerEntity p, RunState s, Visit v) {
        ServerWorld world = p.getServerWorld();
        if (clearingAt == null && v.inside >= (TESTING ? 200 : CLEARING_AFTER)) {
            // Somewhere ahead, or off to one side, on dry ground about level with you.
            int x = 0, z = 0, y = 0;
            boolean found = false;
            for (int turn : new int[]{0, 40, -40, 80, -80}) {
                double angle = Math.toRadians(p.getYaw() + 90 + turn);
                x = (int) Math.floor(p.getX() + Math.cos(angle) * CLEARING_AHEAD);
                z = (int) Math.floor(p.getZ() + Math.sin(angle) * CLEARING_AHEAD);
                y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - p.getY()) <= 6 && world.getFluidState(new BlockPos(x, y - 1, z)).isEmpty() && world.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    found = true;
                    break;
                }
            }
            if (!found) return;
            clearingAt = new BlockPos(x, y, z);
            world.setBlockState(clearingAt, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
            for (int i = 0; i < 9; i++) {
                double a = i / 9.0 * Math.PI * 2;
                BlockPos at = BlockPos.ofFloored(x + 0.5 + Math.cos(a) * 3.2, y, z + 0.5 + Math.sin(a) * 3.2);
                int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                BlockPos candle = new BlockPos(at.getX(), ground, at.getZ());
                if (world.isAir(candle)) world.setBlockState(candle, Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 1 + i % 3).with(CandleBlock.LIT, true));
            }
            for (int i = 0; i < 6; i++) {
                Vec3d photo = new Vec3d(x + 0.2 + RANDOM.nextDouble() * 0.6, y + 1.01, z + 0.2 + RANDOM.nextDouble() * 0.6);
                clearing.add(LunchHall.loose(world, photo, "minecraft:paper", 0.32f));
            }
            Kokuhaku.LOG.info("A clearing in the forest at {}", clearingAt.toShortString());
        }
        if (clearingAt == null || clearingSeen || p.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(clearingAt)) > CLEARING_REACH * CLEARING_REACH) return;
        clearingSeen = true;
        Shadow.saw(s, "She comes to the clearing most nights. She keeps the candles going.");
        Talk.say(p, "In the clearing", "A ring of candles, still burning. On the stump, photos, a lot of them. Every one of them is you. The newest is from today, from behind, on this path.",
                Talk.option("Take one.", q -> {
                    s.notes.add("A photo from the clearing in the forest: you, walking, taken from somewhere behind you. Today's date on the back.");
                    s.markDirty();
                    RunManager.tell(q, "", "It's in your bag.");
                }), Talk.leave("Back away."));
    }

    /** The clearing goes with the run. */
    static void tidy() {
        clearing.forEach(Entity::discard);
        clearing.clear();
    }

    static boolean near(Vec3d at) {
        return clearingAt != null && at.squaredDistanceTo(Vec3d.ofBottomCenter(clearingAt)) < 64 * 64;
    }

    static float figureTurn(float a, float b) {
        return MathHelper.wrapDegrees(a - b);
    }
}
