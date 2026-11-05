package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The way anyone actually walks somewhere. A student's route starts out as a
 * string of map waypoints; the next stretch of it gets laid out on the ground
 * the same way your own walk to school is, clear of walls, turning only where a
 * person would. A few people a tick, so a whole school can set off at once.
 */
public final class Paths {
    /** How far ahead gets worked out at a time, and how near someone has to be for it to be worth it: this has to be loaded. */
    private static final double STRETCH = 48, WATCHED = 72;
    private static final long NANOS_A_TICK = 3_000_000L;
    private static final Set<StudentEntity> waiting = new LinkedHashSet<>();
    private static long spent = 0;
    private static int done = 0;

    private Paths() {
    }

    public static void clear() {
        waiting.clear();
        spent = 0;
        done = 0;
    }

    /** They'll be told when it's ready. They set off on the rough way until then. */
    public static void ask(StudentEntity e) {
        waiting.add(e);
    }

    static void tick() {
        long until = System.nanoTime() + NANOS_A_TICK;
        Iterator<StudentEntity> next = waiting.iterator();
        while (next.hasNext() && System.nanoTime() < until) {
            StudentEntity e = next.next();
            next.remove();
            if (!e.isRemoved() && e.hasRoute() && !e.isSittingDown() && !e.noClip && !e.isAiDisabled() && e.getWorld().getClosestPlayer(e, WATCHED) != null) lay(e);
        }
    }

    private static void lay(StudentEntity e) {
        long started = System.nanoTime();
        List<Vec3d> left = e.routeLeft();
        List<Vec3d> stretch = new ArrayList<>(List.of(e.getPos()));
        double length = 0;
        int used = 0;
        Vec3d at = e.getPos();
        for (Vec3d point : left) {
            if (length > STRETCH) break;
            length += at.distanceTo(point);
            at = point;
            stretch.add(point);
            used++;
        }
        List<Vec3d> ground = WalkRoute.fit((ServerWorld) e.getWorld(), stretch, e);
        if (ground.size() < 2) return;
        Vec3d end = stretch.get(stretch.size() - 1);
        if (ground.get(ground.size() - 1).squaredDistanceTo(end) > 0.04) ground.add(end);
        List<Vec3d> rest = new ArrayList<>(ground);
        rest.addAll(left.subList(used, left.size()));
        e.laidOut(rest, ground.size());
        spent += System.nanoTime() - started;
        if (++done % 500 == 0) Kokuhaku.LOG.info("Paths: {} laid out, {} ms each", done, spent / done / 1_000_000.0);
    }
}
