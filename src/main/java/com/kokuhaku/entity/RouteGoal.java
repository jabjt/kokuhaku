package com.kokuhaku.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Walks a student along their route. They aim a few waypoints ahead, so
 * Minecraft's pathfinding rounds corners instead of zig-zagging from point to
 * point; everyone has their own pace, and now and then someone just stops for
 * a moment. Nobody ever hops anywhere: if they truly stop moving they give up
 * on the route and work out a new one from where they're standing.
 */
public class RouteGoal extends Goal {
    private static final double ARRIVE = 1.8;
    /**
     * A mob's real speed goes with the square of its speed setting, so this isn't
     * the obvious number: it's what puts them at a player's 4.3 blocks a second.
     */
    private static final double WALKING_PACE = 1.265;
    /** Only the next waypoint but one: any further and they start cutting corners. */
    private static final int LOOK_AHEAD = 1;
    private static final int REPATH_TICKS = 10;
    private static final int STUCK_TICKS = 70, TIMES_BEFORE_STEPPING_ASIDE = 2, TIMES_BEFORE_GIVING_UP = 4;
    /** Nobody appears in front of you: this only happens with nobody around to see it. */
    private static final double SEEN_FROM = 40;

    private static final double KEEP_OUT_REACH = 26, CLOSE_ENOUGH = 5;
    private static final double PASSED = 1.0, LAST_POINT = 0.7, CHAIR_REACH = 2.6, SHUTS_FROM = 3.5;
    private static final double[] DOOR_REACH = {0.8, 1.4}, DOOR_HEIGHTS = {0.3, 1.3};
    /** The route's points are half a block apart. */
    private static final int LOOKS_AHEAD = 3, STOPS_ONE_IN = 260, ASK_EVERY = 30, BLOCKED_TICKS = 25, FEEL_TICKS = 50, BLOCKED_BEFORE_REPLAN = 2;
    private int feelingWay = 0, blockedInARow = 0;
    private final java.util.List<BlockPos> opened = new java.util.ArrayList<>();

    /** How many times anyone had to give up on a route because they were stuck (logged for tuning). */
    public static int stuckHops = 0;

    private final StudentEntity mob;
    private int lastPath = -REPATH_TICKS;
    private int lastProgress;
    private int pause = 0, timesStuck = 0, stuckInARow = 0;
    private Vec3d anchor;

    public RouteGoal(StudentEntity mob) {
        this.mob = mob;
        setControls(EnumSet.of(Control.MOVE));
    }

    private double speed() {
        return WALKING_PACE * mob.pace();
    }

    @Override
    public boolean canStart() {
        return (mob.hasRoute() || mob.isCatchingUp()) && !mob.isTalking();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    /** Their feet follow the line, a little ahead of where they are, the way you look where you're going and not at your shoes. */
    private void walkTheRoute(Vec3d pos) {
        mob.getNavigation().stop();
        double reach = mob.waypointsLeft() <= 1 ? (mob.goingToLunch() || !mob.headedForChair() ? LAST_POINT : CHAIR_REACH) : PASSED;
        while (mob.hasRoute() && pos.squaredDistanceTo(mob.waypoint().x, pos.y, mob.waypoint().z) < reach * reach) {
            mob.advanceRoute();
            reach = mob.waypointsLeft() <= 1 ? (mob.goingToLunch() || !mob.headedForChair() ? LAST_POINT : CHAIR_REACH) : PASSED;
            if (mob.hasRoute() && mob.getRandom().nextInt(STOPS_ONE_IN) == 0) {
                pause = 20 + mob.getRandom().nextInt(60);
                return;
            }
        }
        if (!mob.hasRoute()) return;
        Vec3d aim = mob.waypointAhead(LOOKS_AHEAD);
        openDoors(pos, aim);
        mob.getMoveControl().moveTo(aim.x, aim.y, aim.z, speed());
        // Not getting anywhere: something's in the way that wasn't when this was laid out. Feel a way round it.
        if (anchor == null || pos.squaredDistanceTo(anchor) > 0.6 * 0.6) {
            anchor = pos;
            lastProgress = mob.age;
        } else if (mob.age - lastProgress > BLOCKED_TICKS) {
            feelingWay = mob.age + FEEL_TICKS;
            lastProgress = mob.age;
            anchor = null;
            if (++blockedInARow >= BLOCKED_BEFORE_REPLAN) {
                blockedInARow = 0;
                com.kokuhaku.game.Paths.ask(mob);
            }
        }
    }

    /** A hand out as they get there. Whoever opens it shuts it. */
    private void openDoors(Vec3d pos, Vec3d aim) {
        Vec3d way = aim.subtract(pos).multiply(1, 0, 1);
        if (way.lengthSquared() < 1e-4) return;
        way = way.normalize();
        for (double out : DOOR_REACH) {
            for (double up : DOOR_HEIGHTS) {
                BlockPos at = BlockPos.ofFloored(pos.x + way.x * out, pos.y + up, pos.z + way.z * out);
                BlockState state = mob.getWorld().getBlockState(at);
                if (state.getBlock() instanceof DoorBlock door && state.isIn(BlockTags.WOODEN_DOORS) && !door.isOpen(state)) {
                    door.setOpen(mob, mob.getWorld(), state, at, true);
                    opened.add(at.toImmutable());
                } else if (state.getBlock() instanceof FenceGateBlock && !state.get(FenceGateBlock.OPEN)) {
                    mob.getWorld().setBlockState(at, state.with(FenceGateBlock.OPEN, true));
                    opened.add(at.toImmutable());
                }
            }
        }
    }

    private void closeDoors(Vec3d pos) {
        opened.removeIf(at -> {
            if (at.getSquaredDistance(pos) < SHUTS_FROM * SHUTS_FROM) return false;
            BlockState state = mob.getWorld().getBlockState(at);
            if (state.getBlock() instanceof DoorBlock door) door.setOpen(mob, mob.getWorld(), state, at, false);
            else if (state.getBlock() instanceof FenceGateBlock) mob.getWorld().setBlockState(at, state.with(FenceGateBlock.OPEN, false));
            return true;
        });
    }

    @Override
    public void tick() {
        if (mob.isCatchingUp()) {
            if (mob.age % 20 == 0) mob.tickCatchUp(speed() + 0.25);
            return;
        }
        if (pause > 0) {
            if (--pause == 0) anchor = null;
            lastProgress = mob.age;
            return;
        }
        if (mob.isHeld()) {
            mob.getNavigation().stop();
            lastProgress = mob.age;
            return;
        }
        Vec3d target = mob.waypoint();
        if (target == null) return;
        Vec3d pos = mob.getPos();
        // At the kerb with the cars still on green: they wait for the lights, like everyone does.
        if (com.kokuhaku.game.Crossings.waitAtKerb(mob, target, mob.waypointAhead(2), mob.waypointAhead(4), mob.waypointAhead(6))) {
            mob.getNavigation().stop();
            mob.getMoveControl().moveTo(pos.x, pos.y, pos.z, 0);
            mob.setVelocity(0, mob.getVelocity().y, 0);
            lastProgress = mob.age;
            anchor = null;
            return;
        }
        closeDoors(pos);
        // Something's going on round there. They don't know what. They just find a reason not to go that way.
        Vec3d out = StudentEntity.keepOut();
        if (out != null && target.squaredDistanceTo(out) < KEEP_OUT_REACH * KEEP_OUT_REACH && target.squaredDistanceTo(out) < pos.squaredDistanceTo(out)) {
            mob.getNavigation().stop();
            lastProgress = mob.age;
            return;
        }
        if (mob.onLaidRoute() && mob.age >= feelingWay) {
            walkTheRoute(pos);
            return;
        }
        // Run out of laid route, with someone around to see: lay the next stretch.
        if (!mob.onLaidRoute() && mob.age % ASK_EVERY == 0) com.kokuhaku.game.Paths.ask(mob);
        // They aim ahead, so they round corners without touching every waypoint: tick off any they've passed.
        for (int ahead = LOOK_AHEAD; ahead >= 0; ahead--) {
            Vec3d w = mob.waypointAhead(ahead);
            boolean last = mob.waypointsLeft() - 1 <= ahead;
            double reach = !last ? ARRIVE : mob.goingToLunch() ? 1.1 : mob.headedForChair() ? 2.6 : 1.1;
            if (pos.squaredDistanceTo(w.x, pos.y, w.z) < reach * reach && Math.abs(w.y - pos.y) < 1.5) {
                for (int i = 0; i <= ahead && mob.hasRoute(); i++) mob.advanceRoute();
                // Stop to check a phone or look around, now and then.
                if (mob.hasRoute() && mob.getRandom().nextInt(60) == 0) {
                    pause = 20 + mob.getRandom().nextInt(60);
                    mob.getNavigation().stop();
                }
                return;
            }
        }
        // Stuck means truly not moving, not just walking away from a waypoint to get round a corner.
        if (anchor == null || pos.squaredDistanceTo(anchor) > 1.5 * 1.5) {
            if (anchor != null) timesStuck = stuckInARow = 0;
            anchor = pos;
            lastProgress = mob.age;
        }
        if (mob.age - lastProgress > STUCK_TICKS) {
            stuckHops++;
            if (System.getenv("KOKUHAKU_BOT") != null) com.kokuhaku.Kokuhaku.LOG.info("Stuck: {} {} at {} on the way to {}", mob.getWorld().getClosestPlayer(mob, SEEN_FROM) != null ? "SEEN" : "unseen", mob.getName().getString(), mob.getBlockPos().toShortString(), target);
            if (System.getenv("KOKUHAKU_BOT") != null && mob.age % 5 == 0) {
                StringBuilder near = new StringBuilder();
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        var at = mob.getBlockPos().add(dx, 0, dz);
                        var feet = mob.getWorld().getBlockState(at);
                        near.append(feet.isAir() ? '.' : feet.getCollisionShape(mob.getWorld(), at).isEmpty() ? '~' : '#');
                    }
                    near.append('/');
                }
                com.kokuhaku.Kokuhaku.LOG.info("Around them: {}, under {}", near, mob.getWorld().getBlockState(mob.getBlockPos().down()).getBlock());
            }
            mob.getNavigation().stop();
            // Nothing's worked, and nobody's watching: they just got there somehow, as people do.
            if (++stuckInARow >= TIMES_BEFORE_GIVING_UP && mob.getWorld().getClosestPlayer(mob, SEEN_FROM) == null) {
                stuckInARow = timesStuck = 0;
                anchor = null;
                mob.refreshPositionAndAngles(target.x, target.y, target.z, mob.getYaw(), 0f);
                mob.advanceRoute();
                return;
            }
            // Working out a new way hasn't helped, twice: whatever they're stuck on, they're off it.
            if (++timesStuck >= TIMES_BEFORE_STEPPING_ASIDE) {
                timesStuck = 0;
                anchor = null;
                mob.stepAside(target);
                return;
            }
            // Right beside it and still can't stand on it: something's been put there. Walk on to the next one.
            if (pos.squaredDistanceTo(target.x, pos.y, target.z) < CLOSE_ENOUGH * CLOSE_ENOUGH) mob.advanceRoute();
            else {
                Vec3d last = mob.waypointBefore();
                if (last != null) com.kokuhaku.game.RunManager.noWayThrough(last, target);
                mob.replan();
            }
            anchor = null;
            return;
        }
        Path current = mob.getNavigation().getCurrentPath();
        boolean runningOut = current == null || current.getLength() - current.getCurrentNodeIndex() <= 3;
        if (mob.getNavigation().isIdle() || (runningOut && mob.age - lastPath >= REPATH_TICKS)) {
            Path path = mob.getNavigation().findPathTo(BlockPos.ofFloored(mob.waypointAhead(LOOK_AHEAD)), 0);
            // Half a path just walks them up to the wall in the way, so that's no use if there's a whole one nearer.
            if (path == null || !path.reachesTarget()) {
                Path nearer = mob.getNavigation().findPathTo(BlockPos.ofFloored(target), 0);
                if (nearer != null) path = nearer;
            }
            mob.getNavigation().startMovingAlong(path, speed());
            lastPath = mob.age;
        }
    }
}
