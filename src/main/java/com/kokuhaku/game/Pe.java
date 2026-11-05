package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.net.Net;
import com.kokuhaku.world.town.Town;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityPose;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * PE isn't in a classroom. It's a length of the pool against three of your class,
 * or out on the court with Coach Iwata for three-pointers, turn and turn about,
 * and you have to be there when the whistle goes.
 */
public final class Pe {
    private static final String COACH = "Coach Iwata";
    /** Where the class meets. */
    private static final int POOL_WEST = -562, POOL_EAST = -515, POOL_NORTH = 34, POOL_SOUTH = 74;
    private static final int COURT_WEST = -564, COURT_EAST = -530, COURT_NORTH = -60, COURT_SOUTH = -14;
    /** Along the pool from west to east, in the lanes across the middle of it. */
    private static final double START_X = -551.5, FINISH_X = -527.5, SWIM_Y = 21.3;
    private static final double[] LANES = {49.5, 52.5, 55.5, 58.5};
    private static final int YOUR_LANE = 1, COUNTDOWN = 70, LONGEST_RACE = 20 * 40, RACERS = 3, HOLD_EVERY = 8;
    private static final double SLOWEST = 1.4, FASTEST = 2.15;
    private static final Vec3d FREE_THROW = new Vec3d(-547.5, 22, -29.5);
    private static final float AT_THE_HOOP = 0f, UP_AT_THE_RIM = -24f;
    private static final int TOP_MARKS_PRIZE = 100;
    private static final Random RANDOM = new Random();

    private static final class Race {
        ServerPlayerEntity player;
        final List<StudentEntity> racers = new ArrayList<>();
        final double[] x = new double[RACERS], speed = new double[RACERS];
        int age, ahead;
        final boolean[] home = new boolean[RACERS];
    }

    private static final Map<UUID, Race> races = new HashMap<>();

    private Pe() {
    }

    static void clear() {
        races.clear();
    }

    public static boolean racing(ServerPlayerEntity p) {
        return races.containsKey(p.getUuid());
    }

    /** Whether you're where the class is today. */
    public static boolean there(Vec3d p, int day) {
        return Schedule.swimming(day) ? p.x >= POOL_WEST && p.x <= POOL_EAST && p.z >= POOL_NORTH && p.z <= POOL_SOUTH
                : p.x >= COURT_WEST && p.x <= COURT_EAST && p.z >= COURT_NORTH && p.z <= COURT_SOUTH;
    }

    static void start(ServerPlayerEntity p, RunState s) {
        RunManager.setBusy(p, true);
        if (Schedule.swimming(s.day)) swim(p, s);
        else shoot(p, s);
    }

    private static void shoot(ServerPlayerEntity p, RunState s) {
        RunManager.tell(p, COACH, "\"Three-pointers, from the line. Five shots. Everyone's watching.\"");
        p.teleport(p.getServerWorld(), FREE_THROW.x, FREE_THROW.y, FREE_THROW.z, AT_THE_HOOP, UP_AT_THE_RIM);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(s.difficulty.ordinal());
        ServerPlayNetworking.send(p, Net.HOOPS, buf);
    }

    private static void swim(ServerPlayerEntity p, RunState s) {
        Race race = new Race();
        race.player = p;
        ServerWorld world = p.getServerWorld();
        List<Integer> classmates = new ArrayList<>();
        for (int g : Roster.active(s.party.size())) if (!s.dead.contains(g) && Routines.group(Roster.get(g)) == 0) classmates.add(g);
        java.util.Collections.shuffle(classmates, RANDOM);
        for (int i = 0; i < RACERS; i++) {
            race.x[i] = START_X;
            race.speed[i] = SLOWEST + RANDOM.nextDouble() * (FASTEST - SLOWEST);
            StudentEntity e = Kokuhaku.STUDENT.create(world);
            if (e == null) continue;
            e.setSkin(classmates.isEmpty() ? i : classmates.get(i % classmates.size()));
            e.setAiDisabled(true);
            e.setNoGravity(true);
            e.noClip = true;
            e.setSwimming(true);
            e.setPose(EntityPose.SWIMMING);
            world.spawnEntity(e);
            race.racers.add(e);
        }
        races.put(p.getUuid(), race);
        place(race);
        hold(race);
        RunManager.tell(p, COACH, "\"One length. Hold W to swim. Go on the whistle.\"");
    }

    private static double lane(int racer) {
        return LANES[racer >= YOUR_LANE ? racer + 1 : racer];
    }

    private static void hold(Race race) {
        ServerPlayerEntity p = race.player;
        p.teleport(p.getServerWorld(), START_X, SWIM_Y, LANES[YOUR_LANE], -90f, 0f);
    }

    private static void place(Race race) {
        for (int i = 0; i < race.racers.size(); i++) {
            race.racers.get(i).refreshPositionAndAngles(race.x[i], SWIM_Y, lane(i), -90f, 0f);
            race.racers.get(i).setBodyYaw(-90f);
            race.racers.get(i).setHeadYaw(-90f);
        }
    }

    static void tick(RunState s) {
        for (Race race : new ArrayList<>(races.values())) {
            race.age++;
            if (race.player.isRemoved()) {
                finish(race, s);
                continue;
            }
            if (race.age < COUNTDOWN) {
                if (race.age % HOLD_EVERY == 0) hold(race);
                continue;
            }
            if (race.age == COUNTDOWN) {
                ServerPlayerEntity p = race.player;
                p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.MASTER, 2f, 2f);
                RunManager.tell(p, "", "The whistle goes.");
            }
            for (int i = 0; i < RACERS; i++) {
                race.x[i] = Math.min(FINISH_X, race.x[i] + race.speed[i] / 20);
                if (!race.home[i] && race.x[i] >= FINISH_X) {
                    race.home[i] = true;
                    race.ahead++;
                }
            }
            place(race);
            if (race.player.getX() >= FINISH_X || race.age > COUNTDOWN + LONGEST_RACE) {
                boolean touched = race.player.getX() >= FINISH_X;
                Kokuhaku.LOG.info("Swimming: touched {}, {} ahead, {} ticks", touched, race.ahead, race.age - COUNTDOWN);
                finish(race, s);
                int position = race.ahead + 1;
                if (!touched) RunManager.peDone(race.player, 0, RACERS, COACH, "\"Get out. Don't waste my time.\"", 0);
                else if (position == 1) RunManager.peDone(race.player, RACERS, RACERS, COACH, "\"First. Who taught you to swim?\" He hands you the swimming club's prize money.", TOP_MARKS_PRIZE);
                else RunManager.peDone(race.player, RACERS + 1 - position, RACERS, COACH, "\"" + (position == 2 ? "Second" : position == 3 ? "Third" : "Last") + ". Dry off.\"", 0);
            }
        }
    }

    private static void finish(Race race, RunState s) {
        races.remove(race.player.getUuid());
        race.racers.forEach(StudentEntity::discard);
        Town town = RunManager.town();
        Vec3d deck = Vec3d.ofBottomCenter(town.graph.pos(town.place("pool")));
        if (!race.player.isRemoved()) race.player.teleport(race.player.getServerWorld(), deck.x, deck.y, deck.z, race.player.getYaw(), 0f);
    }

    /** How the shooting went. */
    public static void shotsIn(ServerPlayerEntity p, int made, int shots) {
        String line = made >= shots - 1 ? "\"That's a real shot. Here. Don't tell the others.\"" : made * 2 >= shots ? "\"Not bad. Again next week.\"" : "\"Was that on purpose?\"";
        RunManager.peDone(p, made, shots, COACH, made + " of " + shots + ". " + line, made >= shots - 1 ? TOP_MARKS_PRIZE : 0);
    }
}
