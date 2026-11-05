package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Nobody can see you, so she stops waiting. Arms close round you from behind.
 * You can't turn your head, and you never see her. Press the keys as they come
 * up, before you run out of air. Get free and you've torn something off her;
 * don't, and it all goes dark.
 */
public final class Grab {
    public static final int LONGEST_TICKS = 20 * 60;
    private static final int FIRST_DAY = 2, CHECK_EVERY = 60, DAYS_BETWEEN = 1, CAUGHT_AT = 30;
    private static final double HEARD = 12, SEEN = 24, FRIEND_NEAR = 30, OPEN_GROUND = 32, TRAFFIC = 24;
    private static final float CHANCE = 0.05f, NIGHT_CHANCE = 0.2f;
    /** Walls this close on two sides, or a roof, makes it a place she can work. */
    private static final int ALLEY = 4;
    /** How many keys it takes, and how many seconds of air you've got, by difficulty. */
    private static final int[] KEYS = {6, 8, 10, 12};
    private static final float[] AIR = {7f, 6.5f, 6.5f, 6f};
    private static final Random RANDOM = new Random();

    private static final Set<UUID> held = new HashSet<>();
    /** However hard you fight this one, it only goes one way. */
    private static final int HOPELESS_LONGEST = 20 * 40;
    private static int lastDay = 0;

    private Grab() {
    }

    static void clear() {
        held.clear();
        StudentEntity.setKeepOut(null);
        lastDay = 0;
    }

    private static boolean canSee(ServerPlayerEntity p, Entity e) {
        var ray = new RaycastContext(e.getEyePos(), p.getEyePos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e);
        return e.getWorld().raycast(ray).getType() == HitResult.Type.MISS;
    }

    /** Not a soul within earshot, nobody with a clear line to you, and none of your friends about. */
    static boolean alone(ServerPlayerEntity p, List<ServerPlayerEntity> everyone) {
        ServerWorld world = p.getServerWorld();
        for (ServerPlayerEntity other : everyone) {
            if (other != p && other.distanceTo(p) < FRIEND_NEAR) return false;
        }
        var box = p.getBoundingBox().expand(SEEN);
        for (StudentEntity e : world.getEntitiesByClass(StudentEntity.class, box, e -> e.getPeek() == 0)) {
            double distance = e.distanceTo(p);
            if (distance < HEARD || distance < SEEN && canSee(p, e)) return false;
        }
        return true;
    }

    private static boolean wall(ServerWorld world, net.minecraft.util.math.BlockPos from, net.minecraft.util.math.Direction way) {
        for (int i = 1; i <= ALLEY; i++) {
            net.minecraft.util.math.BlockPos at = from.offset(way, i).up();
            if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) return true;
        }
        return false;
    }

    /** Somewhere out of the way: under cover, or in a gap between two walls. */
    private static boolean covered(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        var at = p.getBlockPos();
        if (!world.isSkyVisible(at.up(2))) return true;
        return wall(world, at, net.minecraft.util.math.Direction.NORTH) && wall(world, at, net.minecraft.util.math.Direction.SOUTH)
                || wall(world, at, net.minecraft.util.math.Direction.EAST) && wall(world, at, net.minecraft.util.math.Direction.WEST);
    }

    /** Out in the open, it takes a whole empty street, and no cars either. */
    private static boolean emptyStreet(ServerPlayerEntity p) {
        var box = p.getBoundingBox().expand(OPEN_GROUND);
        ServerWorld world = p.getServerWorld();
        return world.getEntitiesByClass(StudentEntity.class, box, e -> e.getPeek() == 0 && e.distanceTo(p) < OPEN_GROUND).isEmpty()
                && world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, p.getBoundingBox().expand(TRAFFIC), e -> !(e instanceof StudentEntity) && e.getY() > 0).isEmpty();
    }

    static void tick(RunState s, List<ServerPlayerEntity> living, List<ServerPlayerEntity> everyone, boolean playing, int ticks) {
        if (!playing || ticks % CHECK_EVERY != 0 || s.day < FIRST_DAY || s.day - lastDay < DAYS_BETWEEN && lastDay > 0 || Living.active(s) || System.getenv("KOKUHAKU_STAGE") != null) return;
        if (Schedule.isSchoolDay(s.day) && s.minute < Schedule.AFTER_SCHOOL) return;
        for (ServerPlayerEntity p : living) {
            if (Accomplice.is(p) || RunManager.isHomeNow(p, s) || RunManager.inScene(p) || Chase.active(p) || WalkScene.active(p) || p.isTouchingWater() || !alone(p, everyone) || !covered(p) && !emptyStreet(p)
                    || RANDOM.nextFloat() > (Townsfolk.night(s.minute) ? NIGHT_CHANCE : CHANCE)) continue;
            start(p, s);
            return;
        }
    }

    static void start(ServerPlayerEntity p, RunState s) {
        lastDay = s.day;
        Stalker.vanish();
        Shadow.saw(s, "She waited until nobody could see you, then took hold of you from behind.");
        if (s.sprays > 0) {
            s.sprays--;
            keepWhatFell(s);
            Kokuhaku.LOG.info("{} sprayed her and got away", p.getGameProfile().getName());
            RunManager.tell(p, "Arms close round you.", "You spray behind you without looking. A gasp, and they're gone.");
            RunManager.later(90, () -> RunManager.tell(p, "", "Something fell when they ran. It's in your bag now."));
            return;
        }
        held.add(p.getUuid());
        StudentEntity.setKeepOut(p.getPos());
        RunManager.setBusy(p, true);
        RunManager.holdClock(s.party.size() == 1);
        ServerWorld world = p.getServerWorld();
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_WOOL_FALL, SoundCategory.HOSTILE, 1f, 0.6f);
        int level = s.difficulty.ordinal();
        Kokuhaku.LOG.info("{} was grabbed from behind at {}", p.getGameProfile().getName(), p.getBlockPos().toShortString());
        RunManager.sendGrab(p, KEYS[level], AIR[level]);
        // If the answer never comes, neither does help.
        RunManager.later((int) (AIR[level] * 20 * 3) + CAUGHT_AT, () -> done(p, false));
    }

    /** You've run out of guesses, and out of people. There's no getting free of this one. */
    static void hopeless(ServerPlayerEntity p, RunState s) {
        Shadow.saw(s, "You ran out of guesses. She'd been standing behind you the whole time, waiting for the last one.");
        held.add(p.getUuid());
        StudentEntity.setKeepOut(p.getPos());
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_WOOL_FALL, SoundCategory.HOSTILE, 1f, 0.6f);
        int level = s.difficulty.ordinal();
        RunManager.sendGrab(p, -1, AIR[level]);
        RunManager.later(HOPELESS_LONGEST, () -> done(p, false));
    }

    /** You've got a piece of her in your hand, and no idea who it came from. */
    static String torn(RunState s) {
        Student her = Roster.get(s.freeYandere());
        int level = Math.max(1, s.difficulty.ordinal());
        return switch (RANDOM.nextInt(3)) {
            case 0 -> "Torn off in the struggle: a few strands. " + her.hair().phrase(level);
            case 1 -> "Torn off in the struggle: a button and some thread. " + her.homeroom().phrase(level);
            default -> "What you smelled, when she held you: " + s.mystery().clubs[her.id()].phrase(level);
        };
    }

    private static void keepWhatFell(RunState s) {
        s.notes.add(torn(s));
        s.markDirty();
        Whispers.foundClue();
    }

    public static void done(ServerPlayerEntity p, boolean free) {
        if (!held.remove(p.getUuid())) return;
        if (held.isEmpty()) StudentEntity.setKeepOut(null);
        RunState s = RunManager.state(p.server);
        Kokuhaku.LOG.info("{} {}", p.getGameProfile().getName(), free ? "broke free" : "didn't break free");
        if (!free) {
            RunManager.carriedOff(p, s);
            return;
        }
        keepWhatFell(s);
        RunManager.freed(p, s);
        RunManager.later(14, () -> {
            RunManager.tell(p, "You tear yourself free and spin round.", "There's nobody there.");
            RunManager.later(90, () -> RunManager.tell(p, "", "There's something in your hand. It's in your bag now."));
        });
    }
}
