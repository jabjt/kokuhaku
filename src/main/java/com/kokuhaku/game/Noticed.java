package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * What you see for yourself. Nobody has to tell you anything if you're paying
 * attention: who's watching you when you look up, and where someone goes when
 * you follow them home. Some of what you catch means nothing. People do look at
 * each other. Some of it doesn't.
 */
final class Noticed {
    private static final double SEEN_AT = 14, FOLLOW_AT = 11;
    private static final double LOOKING_AT = 0.9;
    private static final float CHANCE_INNOCENT_GLANCE = 0.002f;
    private static final Random RANDOM = new Random();
    private static final Set<String> seen = new HashSet<>();

    private Noticed() {
    }

    static void clear() {
        seen.clear();
    }

    private static boolean youSee(ServerPlayerEntity p, StudentEntity e, double reach) {
        if (e.squaredDistanceTo(p) > reach * reach || !p.canSee(e)) return false;
        Vec3d to = e.getEyePos().subtract(p.getEyePos()).normalize();
        return p.getRotationVec(1f).dotProduct(to) > LOOKING_AT;
    }

    private static void write(ServerPlayerEntity p, RunState s, String line) {
        s.notes.add(line);
        s.markDirty();
        RunManager.tell(p, "", "You make a note of that.");
    }

    /** Called every couple of seconds. */
    static void tick(RunState s, List<? extends StudentEntity> all, ServerPlayerEntity p) {
        if (s.devoted || Living.active(s) || RunManager.inScene(p)) return;
        for (StudentEntity e : all) {
            int id = e.getSkin();
            if (id >= Roster.GIRLS || s.dead.contains(id)) continue;
            String name = Roster.get(id).firstName();
            boolean staring = e.isStaring() || RANDOM.nextFloat() < CHANCE_INNOCENT_GLANCE;
            if (staring && !e.isTalking() && youSee(p, e, SEEN_AT) && seen.add("look" + s.day + id)) {
                write(p, s, "Day " + s.day + ": you looked up and " + name + " was already looking at you.");
            }
            followed(p, s, e, name);
        }
    }

    /** Stay near someone after school, and you'll see where they end up. */
    private static void followed(ServerPlayerEntity p, RunState s, StudentEntity e, String name) {
        int id = e.getSkin();
        if (s.known.contains(id) || s.minute < Schedule.AFTER_SCHOOL || e.hasRoute() || !e.isHeadingToHomeOrClub() || !youSee(p, e, FOLLOW_AT)) return;
        RunManager.learned(p, Roster.get(id));
        RunManager.tell(p, "", "You followed " + name + " here. You know where she goes now.");
    }
}
