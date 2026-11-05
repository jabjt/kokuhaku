package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The festival morning: everyone's getting the train. Someone waits for you at
 * Chinetsu Station; when it pulls in, you get on, and you ride the real train
 * round the shore to the Festival Grounds with them beside you, then walk in together.
 */
final class TrainRide {
    private enum Stage { NONE, WAITING, ABOARD, DEPARTED, DONE }

    /** Where the station is, for anyone turning up. */
    private static final int WEST = -712, EAST = -684, NORTH = -84, SOUTH = -28;
    /** Standing in the carriage, looking out of the east windows. */
    private static final Vec3d SEAT = new Vec3d(-716.0, 24.5, -57.5);
    private static final double BESIDE = 2.6;
    private static final int FOLLOW_EVERY = 5, LONGEST_RIDE = 2600, FADE = 14, RIDE_TICKS = 3000, OUT_OF_THE_WAY = 16;
    /** Ticks after the train pulls away that each of the later lines comes. */
    private static final int HELLO = 40, FIRST_BEAT = 30, SECOND_BEAT = 260;

    private static Stage stage = Stage.NONE;
    private static final List<ServerPlayerEntity> riders = new ArrayList<>();
    private static StudentEntity companion;
    /** Who walks the rest of the way with you. */
    static int walkingWith = -1;
    private static Vec3d startedAt;
    private static int age, departedAt;
    private static boolean greeted, asked, warned;

    private TrainRide() {
    }

    static void clear() {
        stage = Stage.NONE;
        riders.clear();
        companion = null;
        walkingWith = -1;
    }

    static boolean on(ServerPlayerEntity p) {
        return riders.contains(p);
    }

    /** Whoever's waiting on the platform for you. */
    static void meet(RunState s, Town town, net.minecraft.server.MinecraftServer server) {
        if (stage != Stage.NONE) return;
        int id = Living.active(s) ? s.livingWith : s.dating >= 0 && !s.dead.contains(s.dating) ? s.dating : -1;
        List<StudentEntity> everyone = RunManager.studentsOf(server);
        if (everyone.isEmpty()) return;
        companion = everyone.get(new java.util.Random(s.seed ^ 0x54524149L).nextInt(everyone.size()));
        for (StudentEntity e : everyone) if (e.getSkin() == id) companion = e;
        walkingWith = companion.getSkin();
        if (id != s.livingWith) {
            BlockPos at = town.graph.pos(town.place("station"));
            companion.placeAt("station", new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
            companion.setAiDisabled(true);
        }
        stage = Stage.WAITING;
        Kokuhaku.LOG.info("{} is waiting at the station", Roster.get(walkingWith).firstName());
    }

    /** What to do this morning, while the train's still to come. */
    static String objective() {
        return stage == Stage.WAITING ? "Everyone's getting the train. Go to Chinetsu Station; " + Roster.get(walkingWith).firstName() + " is waiting." : null;
    }

    static boolean waiting() {
        return stage == Stage.WAITING;
    }

    private static boolean atStation(ServerPlayerEntity p) {
        return p.getX() >= WEST && p.getX() <= EAST && p.getZ() >= NORTH && p.getZ() <= SOUTH && p.getY() < 40;
    }

    static void tick(RunState s, List<ServerPlayerEntity> living, Town town) {
        switch (stage) {
            case WAITING -> {
                if (!Festival.on(s) || s.minute >= Festival.LAST_TRAIN) {
                    release();
                    stage = Stage.DONE;
                    return;
                }
                if (!Railway.STATION_NAME.equals(Railway.stoppedAt())) return;
                for (ServerPlayerEntity p : living) if (atStation(p) && !RunManager.inScene(p)) riders.add(p);
                if (!riders.isEmpty()) board(s);
            }
            case ABOARD, DEPARTED -> ride(s, town);
            default -> {
            }
        }
    }

    private static void board(RunState s) {
        stage = Stage.ABOARD;
        age = 0;
        greeted = asked = warned = false;
        startedAt = Railway.middle();
        companion.setAiDisabled(true);
        companion.setNoGravity(true);
        companion.noClip = true;
        Kokuhaku.LOG.info("The train's in. {} aboard with {}", riders.size(), Roster.get(walkingWith).firstName());
        Shadow.saw(s, "She was in the next carriage the whole way, looking at your reflection.");
        for (ServerPlayerEntity p : riders) {
            RunManager.setBusy(p, true);
            RunManager.sendRide(p, SEAT, RIDE_TICKS);
        }
        place();
    }

    private static void place() {
        Vec3d moved = Railway.middle().subtract(startedAt);
        companion.refreshPositionAndAngles(SEAT.x + moved.x, SEAT.y, SEAT.z + BESIDE + moved.z, 180f, 0f);
        companion.setBodyYaw(180f);
        companion.setHeadYaw(180f);
    }

    private static void ride(RunState s, Town town) {
        age++;
        place();
        if (age % 400 == 0 || age % 40 == 0 && stage == Stage.DEPARTED && Railway.middle().x > -460) Kokuhaku.LOG.info("Riding: age {}, {}, to {}", age, Railway.status(), Railway.heading());
        if (age % FOLLOW_EVERY == 0) {
            // Out of the way of the carriage, but near enough to keep the train on screen.
            Vec3d over = Railway.middle();
            for (ServerPlayerEntity p : riders) p.teleport(p.getServerWorld(), over.x, over.y + OUT_OF_THE_WAY, over.z, -90f, 0f);
        }
        // Developer testing only: what it looks like out of the window, before and after anyone speaks.
        if (System.getenv("KOKUHAKU_SHOTS") != null && (age == 50 || age == 240 || age == 480 || age == 800 || age == 1100 || age == 1400 || age == 1700)) {
            for (ServerPlayerEntity p : riders) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, com.kokuhaku.net.Net.SHOT, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
        }
        if (!greeted && age >= HELLO) {
            greeted = true;
            say(s, 0);
        }
        if (stage == Stage.ABOARD && Railway.stoppedAt() == null) {
            stage = Stage.DEPARTED;
            departedAt = age;
            Kokuhaku.LOG.info("Train leaving after {} ticks aboard", age);
        }
        if (stage == Stage.DEPARTED && !asked && age >= departedAt + FIRST_BEAT) {
            asked = true;
            say(s, 1);
        }
        if (stage == Stage.DEPARTED && !warned && age >= departedAt + SECOND_BEAT) {
            warned = true;
            say(s, 2);
        }
        if (stage == Stage.DEPARTED && (Railway.FESTIVAL_NAME.equals(Railway.stoppedAt()) || Railway.atFestival()) || age > LONGEST_RIDE) arrive(s, town);
    }

    private static void say(RunState s, int beat) {
        String name = Roster.get(walkingWith).firstName();
        int speaker = companion.getId();
        for (ServerPlayerEntity p : riders) {
            switch (beat) {
                case 0 -> Talk.say(p, name, "I love this bit. You can see the whole town from up here. It looks so normal.", speaker);
                case 1 -> Talk.say(p, name, "They do fireworks at seven. Everyone pairs off for it. Are you watching with anyone?", speaker,
                        Talk.option("I haven't decided.", q -> Talk.say(q, name, "Decide soon. People notice who you stand next to.", speaker)),
                        Talk.option("Maybe with you.", q -> {
                            if (walkingWith < Roster.GIRLS) {
                                s.fondness[walkingWith] = Math.min(5, s.fondness[walkingWith] + 1);
                                s.markDirty();
                            }
                            Talk.say(q, name, "Ha. Careful. People notice who you stand next to.", speaker);
                        }));
                default -> Talk.say(p, "In the window", "For a moment there's someone standing right behind you in the glass. When you turn round, the carriage is empty.", -1);
            }
        }
    }

    private static void arrive(RunState s, Town town) {
        stage = Stage.DONE;

        Kokuhaku.LOG.info("Arrived after {} ticks aboard, {} moving", age, age - departedAt);
        List<ServerPlayerEntity> off = new ArrayList<>(riders);
        riders.clear();
        release();
        Vec3d platform = Railway.platform();
        for (ServerPlayerEntity p : off) {
            RunManager.sendBlack(p, town.homes.get(0), FADE * 2, "");
            RunManager.later(FADE, () -> {
                p.teleport(p.getServerWorld(), platform.x, platform.y, platform.z, 180f, 0f);
                RunManager.setBusy(p, false);
            });
            RunManager.later(FADE * 2 + 2, () -> {
                BlockPos field = town.graph.pos(town.place("field"));
                if (!WalkScene.startAlong(p, s, Railway.toTheField(new Vec3d(field.getX() + 0.5, field.getY(), field.getZ() + 0.5)))) Kokuhaku.LOG.info("No walk from the platform");
            });
        }
    }

    private static void release() {
        if (companion == null) return;
        companion.setAiDisabled(false);
        companion.setNoGravity(false);
        companion.noClip = false;
        companion.settle();
    }

    /** You'd rather just be there. */
    static void skip(ServerPlayerEntity p, RunState s, Town town) {
        if (riders.contains(p)) arrive(s, town);
    }
}
