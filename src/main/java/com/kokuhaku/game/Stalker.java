package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Random;

/**
 * The figure. A black shape that stands a long way behind you after school.
 * Stare at it and it isn't there any more. Look away and it's closer. From the
 * third week, if you're out late, it stops standing and starts walking; it's a
 * little slower than you, so you can always get home, unless you're limping or
 * you've left it too late.
 */
final class Stalker {
    private static final int FIRST_DAY = 2, HUNT_DAY = 7, HUNT_BEFORE_CURFEW = 75;
    private static final int LOOK_TICKS = 25, CREEP_TICKS = 90, CHECK_EVERY = 20;
    private static final double FAR = 34, NEAR = 15, CAUGHT = 1.7, HUNT_PACE = 1.08, CREEP_FRACTION = 0.6, GONE_WITHIN = 6;
    /** Each second after school, the chance she's out there: small, and bigger by the day. */
    private static final float CHANCE_A_DAY = 0.0012f, EVENING_FACTOR = 2.5f;
    private static final Random RANDOM = new Random();

    private static StudentEntity figure;
    private static ServerPlayerEntity watched;
    private static boolean hunting;
    private static int lookedAt, unseen;

    private Stalker() {
    }

    static void clear() {
        vanish();
    }

    static void vanish() {
        if (figure != null) figure.discard();
        figure = null;
        watched = null;
        hunting = false;
    }

    /** Developer testing only: where she is. */
    static Vec3d devWhere() {
        return figure == null ? null : figure.getPos();
    }

    /** How close she is, 0..1, for the dread on screen. */
    static float closeness(ServerPlayerEntity p) {
        if (figure == null || watched != p) return 0f;
        return (float) Math.max(0, Math.min(1, 1 - figure.distanceTo(p) / FAR));
    }

    private static boolean sees(ServerPlayerEntity p, Vec3d spot) {
        ServerWorld world = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        var ray = new RaycastContext(eye, spot.add(0, 1.5, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p);
        return world.raycast(ray).getType() == HitResult.Type.MISS;
    }

    private static boolean lookingAt(ServerPlayerEntity p, Vec3d spot) {
        Vec3d to = spot.add(0, 1.2, 0).subtract(p.getEyePos()).normalize();
        return p.getRotationVec(1f).dotProduct(to) > 0.93 && sees(p, spot);
    }

    /** Somewhere on the street behind you, in the open, a set distance away. */
    private static Vec3d spotBehind(ServerPlayerEntity p, Town town, double distance, boolean mustBeBehind) {
        Vec3d facing = Vec3d.fromPolar(0f, p.getYaw());
        Vec3d best = null;
        double bestGap = 12;
        int start = RANDOM.nextInt(town.graph.size());
        for (int i = 0; i < town.graph.size(); i++) {
            BlockPos at = town.graph.pos((start + i) % town.graph.size());
            Vec3d v = new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (Math.abs(v.y - p.getY()) > 4) continue;
            double gap = Math.abs(v.distanceTo(p.getPos()) - distance);
            if (gap >= bestGap) continue;
            Vec3d to = v.subtract(p.getPos()).normalize();
            if (mustBeBehind && to.dotProduct(facing) > -0.2) continue;
            if (!sees(p, v)) continue;
            best = v;
            bestGap = gap;
            if (gap < 2) break;
        }
        return best;
    }

    private static void appear(ServerPlayerEntity p, RunState s, Vec3d at, boolean hunt) {
        ServerWorld world = p.getServerWorld();
        StudentEntity e = Kokuhaku.STUDENT.create(world);
        if (e == null) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - at.z, p.getX() - at.x)) - 90);
        e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        e.setSkin(s.freeYandere());
        e.setPeek(StudentEntity.SILHOUETTE);
        e.setKeptClose(true);
        e.setAiDisabled(!hunt);
        world.spawnEntity(e);
        figure = e;
        Shadow.saw(s, hunt ? "She came after you down the street, and she wasn't hiding." : "The shape standing a long way behind you was her.");
        watched = p;
        hunting = hunt;
        lookedAt = 0;
        unseen = 0;
        Kokuhaku.LOG.info("The figure is {} blocks from {} ({})", (int) e.distanceTo(p), p.getGameProfile().getName(), hunt ? "hunting" : "watching");
    }

    /** No more standing a long way off. */
    static void comeFor(ServerPlayerEntity p, RunState s, Town town) {
        vanish();
        Vec3d at = spotBehind(p, town, NEAR, false);
        if (at != null) appear(p, s, at, true);
    }

    private static float dayChance(RunState s) {
        return s.day * CHANCE_A_DAY * (s.minute >= Schedule.EVENING ? EVENING_FACTOR : 1f) * Habits.stalking(s);
    }

    static void tick(RunState s, List<ServerPlayerEntity> players, Town town, boolean playing, int ticks) {
        boolean forced = System.getenv("KOKUHAKU_STALK") != null;
        if (!playing || Living.active(s) || players.isEmpty()) {
            vanish();
            return;
        }
        if (figure != null) {
            follow(s, town);
            return;
        }
        if (ticks % CHECK_EVERY != 0 || s.day < FIRST_DAY) return;
        if (!forced && s.minute < Schedule.AFTER_SCHOOL && Schedule.isSchoolDay(s.day)) return;
        ServerPlayerEntity p = players.get(RANDOM.nextInt(players.size()));
        if (Accomplice.is(p) || RunManager.isHomeNow(p, s) || RunManager.inScene(p)) return;
        int curfew = RunManager.curfewOf(s);
        boolean hunt = s.day >= HUNT_DAY && s.minute >= curfew - HUNT_BEFORE_CURFEW;
        if (!hunt && !forced && RANDOM.nextFloat() > dayChance(s)) return;
        // She starts far off in the first week and a good deal nearer in the last.
        double distance = FAR - (FAR - NEAR) * Math.min(1f, s.day / (float) Schedule.DAYS);
        Vec3d at = spotBehind(p, town, distance, true);
        if (at != null) appear(p, s, at, hunt);
    }

    private static void follow(RunState s, Town town) {
        ServerPlayerEntity p = watched;
        if (p == null || p.isRemoved() || figure == null || figure.isRemoved() || RunManager.isHomeNow(p, s) || RunManager.inScene(p)) {
            vanish();
            return;
        }
        ServerWorld world = p.getServerWorld();
        double distance = figure.distanceTo(p);
        float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - figure.getZ(), p.getX() - figure.getX())) - 90);
        figure.setHeadYaw(yaw);
        if (hunting) {
            if (figure.age % 10 == 0) figure.getNavigation().startMovingTo(p, HUNT_PACE * Habits.pace(s));
            if (figure.age % 18 == 0) world.playSound(null, figure.getX(), figure.getY(), figure.getZ(), SoundEvents.BLOCK_STONE_STEP, SoundCategory.HOSTILE, 0.6f, 0.7f);
            if (distance < CAUGHT) {
                Kokuhaku.LOG.info("The figure caught {}", p.getGameProfile().getName());
                vanish();
                RunManager.caughtOutside(p, s);
            }
            return;
        }
        figure.setBodyYaw(yaw);
        if (lookingAt(p, figure.getPos())) {
            unseen = 0;
            if (++lookedAt >= LOOK_TICKS) {
                world.playSound(null, figure.getX(), figure.getY(), figure.getZ(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.HOSTILE, 0.5f, 0.6f);
                vanish();
            }
            return;
        }
        // Look away and she's closer. She never moves while you can see her.
        if (++unseen >= CREEP_TICKS) {
            unseen = 0;
            if (distance < GONE_WITHIN) {
                RunManager.tell(p, "", "Something touches the back of your neck. There's nobody there.");
                vanish();
                return;
            }
            Vec3d closer = spotBehind(p, town, distance * CREEP_FRACTION, true);
            if (closer == null) return;
            figure.refreshPositionAndAngles(closer.x, closer.y, closer.z, yaw, 0f);
            world.playSound(null, closer.x, closer.y, closer.z, SoundEvents.BLOCK_STONE_STEP, SoundCategory.HOSTILE, 0.5f, 0.6f);
        }
    }
}
