package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Out past curfew with a green tea in you: instead of it ending there, you get a
 * head start. Two and a half minutes to reach your own front door, with something
 * walking after you. Slow down and the time runs out three times as fast.
 */
final class Chase {
    private static final double SECONDS = 150, MOVING_PER_SECOND = 4, SLOWED_DOWN = 3;
    private static final double GAP_AT_START = 22, GAP_AT_END = 3, FIGURE_SPEED = 0.4;
    /** How long before curfew a tea still counts. */
    private static final int TEA_LASTS = 60, HEARTBEAT_EVERY = 16;

    private static final class Run {
        ServerPlayerEntity player;
        StudentEntity figure;
        double left = SECONDS;
        Vec3d lastSecond;
        int age;
        boolean slow;
    }

    private static final Map<UUID, Run> runs = new HashMap<>();
    private static int teaDay = -1, teaMinute = -1;

    private Chase() {
    }

    static void clear() {
        runs.clear();
        teaDay = -1;
        teaMinute = -1;
    }

    static void drank(RunState s) {
        teaDay = s.day;
        teaMinute = s.minute;
    }

    static boolean active(ServerPlayerEntity p) {
        return runs.containsKey(p.getUuid());
    }

    static boolean any() {
        return !runs.isEmpty();
    }

    /** Whole seconds left, or -1. Negative-and-a-half means you're slowing down: the screen goes red. */
    static int secondsLeft(ServerPlayerEntity p) {
        Run run = runs.get(p.getUuid());
        return run == null ? -1 : (int) Math.ceil(run.left);
    }

    static boolean slowing(ServerPlayerEntity p) {
        Run run = runs.get(p.getUuid());
        return run != null && run.slow;
    }

    /** It's curfew and you're out. If you've had the tea, you get your chance. */
    static boolean begin(ServerPlayerEntity p, RunState s) {
        if (active(p)) return true;
        boolean tea = p.hasStatusEffect(StatusEffects.SPEED) || teaDay == s.day && s.minute - teaMinute <= TEA_LASTS;
        if (!tea) return false;
        Run run = new Run();
        run.player = p;
        run.lastSecond = p.getPos();
        ServerWorld world = p.getServerWorld();
        StudentEntity figure = Kokuhaku.STUDENT.create(world);
        if (figure != null) {
            Vec3d back = p.getPos().subtract(p.getRotationVector().multiply(GAP_AT_START, 0, GAP_AT_START));
            figure.refreshPositionAndAngles(back.x, p.getY(), back.z, 0f, 0f);
            figure.setSkin(s.freeYandere());
            figure.setPeek(StudentEntity.SILHOUETTE);
            figure.setAiDisabled(true);
            figure.setNoGravity(true);
            figure.noClip = true;
            world.spawnEntity(figure);
            run.figure = figure;
        }
        runs.put(p.getUuid(), run);
        Kokuhaku.LOG.info("{} made a run for it after curfew", p.getGameProfile().getName());
        Shadow.saw(s, "You ran for it after curfew. She didn't hurry. She didn't have to.");
        RunManager.tell(p, "Run.", "Get home. Don't slow down.");
        return true;
    }

    static void tick(RunState s) {
        for (Run run : new ArrayList<>(runs.values())) {
            ServerPlayerEntity p = run.player;
            run.age++;
            if (p.isRemoved() || RunManager.isHomeNow(p, s)) {
                end(run);
                if (!p.isRemoved()) RunManager.tell(p, "", "You get inside and put your back against the door. The footsteps stop.");
                continue;
            }
            if (run.age % 20 == 0) {
                run.slow = p.getPos().squaredDistanceTo(run.lastSecond) < MOVING_PER_SECOND * MOVING_PER_SECOND;
                run.lastSecond = p.getPos();
                // Your legs hold out as long as the time does.
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, false, false));
            }
            run.left -= (run.slow ? SLOWED_DOWN : 1) / 20;
            follow(run);
            if (run.age % (run.slow ? HEARTBEAT_EVERY / 2 : HEARTBEAT_EVERY) == 0) {
                p.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1f, run.slow ? 1.2f : 0.9f);
            }
            if (run.left <= 0) {
                end(run);
                RunManager.caughtOutside(p, s);
            }
        }
    }

    /** Always there when you look back, and nearer the less time you've got. */
    private static void follow(Run run) {
        if (run.figure == null) return;
        ServerPlayerEntity p = run.player;
        double gap = GAP_AT_END + (GAP_AT_START - GAP_AT_END) * run.left / SECONDS;
        Vec3d from = run.figure.getPos(), to = p.getPos().subtract(from);
        double distance = to.horizontalLength();
        if (distance > gap) {
            Vec3d step = new Vec3d(to.x, 0, to.z).normalize().multiply(Math.min(distance - gap, FIGURE_SPEED));
            from = from.add(step.x, 0, step.z);
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        run.figure.refreshPositionAndAngles(from.x, p.getY(), from.z, yaw, 0f);
        run.figure.setBodyYaw(yaw);
        run.figure.setHeadYaw(yaw);
    }

    private static void end(Run run) {
        runs.remove(run.player.getUuid());
        if (run.figure != null) run.figure.discard();
    }

    static List<UUID> running() {
        return new ArrayList<>(runs.keySet());
    }
}
