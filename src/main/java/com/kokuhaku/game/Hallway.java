package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.MenuRoom;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * How you get to the room. You're not there for it, but you see it anyway: the
 * long hall, the lanterns going past, and her ahead of you with your ankle in
 * her hand, not hurrying. Then the chair, and you've been tied to it.
 */
final class Hallway {
    private static final int DRAG_TICKS = 340, STEP = 1;
    private static final double AHEAD = 0.58;
    /** Low enough that you're properly sat on the chair, not hovering over it. */
    private static final double SEATED = 0.25;
    private static final List<Entity> bodies = new ArrayList<>();

    private Hallway() {
    }

    static void clear() {
        bodies.clear();
    }

    private static StudentEntity figure(ServerWorld world, int skin, int peek, Vec3d at, float yaw) {
        StudentEntity e = Kokuhaku.STUDENT.create(world);
        if (e == null) return null;
        e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        e.setSkin(skin);
        e.setPeek(peek);
        e.setAiDisabled(true);
        e.setNoGravity(true);
        e.noClip = true;
        world.spawnEntity(e);
        return e;
    }

    /** Down the hall and through to the door. then is the room. */
    static void drag(ServerPlayerEntity p, RunState s, Runnable then) {
        ServerWorld world = p.getServerWorld();
        Vec3d start = MenuRoom.HALL_START, end = MenuRoom.HALL_END;
        float yaw = (float) (Math.toDegrees(Math.atan2(end.z - start.z, end.x - start.x)) - 90);
        MenuRoom.hide(p);
        Vec3d middle = start.lerp(end, 0.5);
        p.teleport(world, middle.x, middle.y, middle.z, yaw, 0f);
        // She goes backwards, so she can watch you the whole way.
        float facingYou = yaw + 180f;
        StudentEntity her = figure(world, s.freeYandere(), StudentEntity.HAULING, start.add(AHEAD, 0, 0), facingYou);
        StudentEntity you = figure(world, 0, StudentEntity.DRAGGED, start, yaw);
        if (her == null || you == null) {
            then.run();
            return;
        }
        Kokuhaku.LOG.info("Dragged down the hall");
        RunManager.sendDrag(p, start, end, DRAG_TICKS);
        for (int t = STEP; t <= DRAG_TICKS; t += STEP) {
            Vec3d at = start.lerp(end, MenuRoom.pulled(t / (double) DRAG_TICKS));
            RunManager.later(t, () -> {
                you.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
                her.refreshPositionAndAngles(at.x + AHEAD, at.y, at.z, facingYou, 0f);
                her.setBodyYaw(facingYou);
                her.setHeadYaw(facingYou);
                you.setBodyYaw(yaw);
            });
        }
        RunManager.later(DRAG_TICKS, () -> {
            you.discard();
            her.discard();
            seat(world);
            then.run();
        });
    }

    /** Whoever's in the room, it's the same chair. */
    private static void seat(ServerWorld world) {
        if (!bodies.isEmpty()) return;
        StudentEntity tied = figure(world, 0, StudentEntity.TIED, new Vec3d(MenuRoom.SEAT_X, MenuRoom.GIRL_Y + SEATED, MenuRoom.SEAT_Z), 180f);
        if (tied == null) return;
        tied.sit();
        bodies.add(tied);
    }

    /** She's let you go, or you've said you'll stay. Either way there's nobody in the chair. */
    static void untie() {
        bodies.forEach(Entity::discard);
        bodies.clear();
    }
}
