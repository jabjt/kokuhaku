package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The town's traffic. Townsfolk drive Trep's Cars around fixed lanes on the
 * left of the road, the way they do here. Nobody else can get in, and they
 * stop for each other and for pedestrians, but not for you.
 */
public final class Traffic {
    /** Road surface, plus the height of a car's wheels. */
    private static final double ROAD_Y = 22.0;
    private static final double POINT_SPACING = 0.5, CORNER_RADIUS = 5.0;
    private static final double TOWN_SPEED = 0.45, MAIN_ROAD_SPEED = 0.6, TURN_SPEED = 0.22;
    private static final double ACCELERATION = 0.012, BRAKING = 0.07, STOPPING_FOR_A_WALK = 0.14, WALK_CLEARANCE = 4.5;
    private static final int ROUTE_SAMPLE = 3;
    private static final double BRAKE_RANGE = 9.0, STOP_GAP = 6.0, LANE_WIDTH = 2.2, PEDESTRIAN_WIDTH = 1.6;
    /** Half the car, and half a person, either way. */
    private static final double HIT_LENGTH = 2.8, HIT_WIDTH = 1.55, HIT_SPEED = 0.15;
    private static final double HORN_RANGE = 15.0, IMPACT_LEAD = 9, CROSSING_LOOKAHEAD = 7;
    private static final int HORN_GAP = 60, ENGINE_GAP = 50, PATIENCE = 50, STAYS_OUT = 6, OUT_OF_SIGHT = 60;
    private static final double UNDER_THE_ROAD = -18, SCENE_REACH = 45;
    /** How long a car sits behind another lane's traffic before it goes anyway, and how long it keeps going. */
    private static final int STUCK_TICKS = 50, PUSH_TICKS = 70;
    private static final String[] COLOURS = {"wr", "wb", "wbl", "ww", "wg", "wlg", "wy", "wo"};

    /** Corner by corner, as x, z. The first four are loops; the last two run the length of the main road. */
    private static final double[][][] LANES = {
            {{-767.75, -206.75}, {-645, -206.75}, {-645, -145.25}, {-684, -145.25}, {-692, -142.25}, {-767.75, -142.25}},
            {{-765.25, -204.25}, {-765.25, -144.75}, {-692, -144.75}, {-684, -147.75}, {-650, -147.75}, {-650, -204.25}},
            {{-688.75, -147.75}, {-645, -147.75}, {-645, -80.5}, {-688.75, -80.5}},
            // Northbound on the main road swings out round the bus parked in its lane.
            {{-686.25, -145.25}, {-686.25, -84.5}, {-650, -84.5}, {-650, -111}, {-647.5, -115}, {-647.5, -145.25}},
            {{-645, -285}, {-645, 100}},
            {{-650, 100}, {-650, -111}, {-647.5, -115}, {-647.5, -144}, {-650, -148}, {-650, -285}},
    };
    private static final int LOOPS = 4;
    private static final int[] CARS_PER_LANE = {3, 3, 3, 3, 5, 5};

    private static final class Lane {
        final List<Vec3d> points = new ArrayList<>();
        final boolean loop;
        final double cruise;

        Lane(double[][] corners, boolean loop, double cruise) {
            this.loop = loop;
            this.cruise = cruise;
            int n = corners.length;
            List<Vec3d> rounded = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Vec3d here = flat(corners[i]);
                boolean end = !loop && (i == 0 || i == n - 1);
                if (end) {
                    rounded.add(here);
                    continue;
                }
                Vec3d before = flat(corners[(i + n - 1) % n]), after = flat(corners[(i + 1) % n]);
                double in = Math.min(CORNER_RADIUS, here.distanceTo(before) * 0.45), out = Math.min(CORNER_RADIUS, here.distanceTo(after) * 0.45);
                Vec3d from = here.add(before.subtract(here).normalize().multiply(in)), to = here.add(after.subtract(here).normalize().multiply(out));
                for (int s = 0; s <= 10; s++) {
                    double t = s / 10.0;
                    rounded.add(from.multiply((1 - t) * (1 - t)).add(here.multiply(2 * t * (1 - t))).add(to.multiply(t * t)));
                }
            }
            if (loop) rounded.add(rounded.get(0));
            double next = 0, travelled = 0;
            for (int i = 1; i < rounded.size(); i++) {
                Vec3d a = rounded.get(i - 1), b = rounded.get(i);
                double length = a.distanceTo(b);
                while (next <= travelled + length && length > 0) {
                    points.add(a.lerp(b, (next - travelled) / length));
                    next += POINT_SPACING;
                }
                travelled += length;
            }
        }

        private static Vec3d flat(double[] c) {
            return new Vec3d(c[0], ROAD_Y, c[1]);
        }

        double length() {
            return points.size() * POINT_SPACING;
        }

        Vec3d at(double distance) {
            double wrapped = distance % length();
            if (wrapped < 0) wrapped += length();
            double exact = wrapped / POINT_SPACING;
            int i = (int) exact;
            return points.get(i).lerp(points.get((i + 1) % points.size()), exact - i);
        }

        Vec3d heading(double distance) {
            Vec3d way = at(distance + 1.5).subtract(at(distance - 1.5));
            return way.lengthSquared() > 100 || way.lengthSquared() < 1e-6 ? at(distance + 0.5).subtract(at(distance)).normalize() : way.normalize();
        }
    }

    private static final class Car {
        final MobEntity body;
        final StudentEntity driver;
        final Lane lane;
        double distance, speed;
        int waited, lastHorn, stuck, pushing;
        /** Off the road for the night, out of sight. */
        boolean parkedUp;

        Car(MobEntity body, StudentEntity driver, Lane lane, double distance) {
            this.body = body;
            this.driver = driver;
            this.lane = lane;
            this.distance = distance;
            this.speed = lane.cruise;
        }

        Vec3d pos() {
            return lane.at(distance);
        }

        Vec3d heading() {
            return lane.heading(distance);
        }
    }

    private static final Random RANDOM = new Random();
    private static final List<Car> cars = new ArrayList<>();
    /** Everyone being walked somewhere right now: the road's theirs until they've gone. */
    private static final java.util.Map<java.util.UUID, List<Vec3d>> walks = new java.util.HashMap<>();
    private static int ticks = 0;
    private static boolean parked = false;
    private static boolean boarded = false;

    private Traffic() {
    }

    public static void clear() {
        cars.clear();
        walks.clear();
    }

    /** Every car stops where it is, and anything already on the way carries on until it's off it. */
    static void holdFor(java.util.UUID who, List<Vec3d> route) {
        List<Vec3d> every = new ArrayList<>();
        for (int i = 0; i < route.size(); i += ROUTE_SAMPLE) every.add(route.get(i));
        walks.put(who, every);
    }

    static void release(java.util.UUID who) {
        walks.remove(who);
    }

    private static boolean onAWalk(Vec3d pos) {
        for (List<Vec3d> route : walks.values()) {
            for (Vec3d at : route) {
                double dx = at.x - pos.x, dz = at.z - pos.z;
                if (dx * dx + dz * dz < WALK_CLEARANCE * WALK_CLEARANCE) return true;
            }
        }
        return false;
    }

    public static void spawn(ServerWorld world) {
        cars.clear();
        List<EntityType<?>> types = new ArrayList<>();
        for (int version = 1; version <= 3; version++) {
            for (String colour : COLOURS) {
                Identifier id = new Identifier("trepscars", "car_v" + version + "_" + colour);
                Registries.ENTITY_TYPE.getOrEmpty(id).ifPresent(types::add);
            }
        }
        if (types.isEmpty()) {
            Kokuhaku.LOG.warn("No cars: Trep's Cars isn't installed");
            return;
        }
        int blocked = 0;
        for (int l = 0; l < LANES.length; l++) {
            boolean loop = l < LOOPS;
            Lane lane = new Lane(LANES[l], loop, loop ? TOWN_SPEED : MAIN_ROAD_SPEED);
            for (Vec3d p : lane.points) {
                BlockPos at = BlockPos.ofFloored(p.add(0, 0.1, 0));
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty() || !world.getBlockState(at.up()).getCollisionShape(world, at.up()).isEmpty()) {
                    if (blocked++ % 6 == 0) Kokuhaku.LOG.info("Lane {} runs into {} at {}", l, world.getBlockState(at).getBlock(), at.toShortString());
                }
            }
            for (int c = 0; c < CARS_PER_LANE[l]; c++) {
                add(world, lane, types.get(RANDOM.nextInt(types.size())), lane.length() * (c + RANDOM.nextDouble() * 0.5) / CARS_PER_LANE[l], cars.size());
            }
        }
        Kokuhaku.LOG.info("Traffic: {} cars, {} lane points run into something", cars.size(), blocked);
    }

    private static void add(ServerWorld world, Lane lane, EntityType<?> type, double distance, int index) {
        Entity made = type.create(world);
        StudentEntity driver = Kokuhaku.STUDENT.create(world);
        if (!(made instanceof MobEntity body) || driver == null) return;
        Car car = new Car(body, driver, lane, distance);
        Vec3d p = car.pos();
        body.refreshPositionAndAngles(p.x, p.y, p.z, 0f, 0f);
        body.setAiDisabled(true);
        body.setNoGravity(true);
        body.setInvulnerable(true);
        body.setPersistent();
        world.spawnEntity(body);
        driver.refreshPositionAndAngles(p.x, p.y, p.z, 0f, 0f);
        driver.setSkin(Townsfolk.skin(index));
        driver.setAiDisabled(true);
        driver.setNoGravity(true);
        driver.setInvulnerable(true);
        world.spawnEntity(driver);
        driver.startRiding(body, true);
        cars.add(car);
    }

    public static void tick(MinecraftServer server, List<ServerPlayerEntity> players) {
        ticks++;
        Railway.meet(players);
        ServerWorld world = server.getOverworld();
        if (ticks % 100 == 0 && "rail".equals(System.getenv("KOKUHAKU_CAR"))) {
            Kokuhaku.LOG.info("SERVER train: {} | stopped at {} | player {}", Railway.status(), Railway.stoppedAt(), players.isEmpty() ? "" : players.get(0).getPos());
            if (!boarded && !players.isEmpty() && ticks > 400 && Railway.STATION_NAME.equals(Railway.stoppedAt())) {
                boarded = true;
                ServerPlayerEntity p = players.get(0);
                p.teleport(p.getServerWorld(), -714.5, 25, -54.5, 0f, 0f);
                Kokuhaku.LOG.info("SERVER put the player on the train");
            }
        }
        if (ticks % 200 == 0 && "watch".equals(System.getenv("KOKUHAKU_CAR"))) {
            StringBuilder line = new StringBuilder();
            for (Car c : cars) line.append(String.format("%.0f,%.0f@%.2f/%d ", c.body.getX(), c.body.getZ(), c.speed, c.waited));
            Kokuhaku.LOG.info("SERVER cars: {}", line);
            int folk = 0, walking = 0;
            for (StudentEntity e : world.getEntitiesByType(Kokuhaku.STUDENT, Townsfolk::isOne)) {
                if (e.hasVehicle()) continue;
                folk++;
                if (e.hasRoute()) walking++;
            }
            Kokuhaku.LOG.info("SERVER townsfolk {}, walking {}", folk, walking);
            Kokuhaku.LOG.info("SERVER train: {}", Railway.status());
        }

        boolean night = Townsfolk.night(RunManager.state(server).minute);
        for (int i = 0; i < cars.size(); i++) {
            Car car = cars.get(i);
            if (car.body.isRemoved()) continue;
            Vec3d pos = car.pos(), heading = car.heading();
            // One in six stays out at night. The rest go when nobody's near enough to see them go, and come back the same way.
            boolean homeTime = night && i % STAYS_OUT != 0;
            if (homeTime != car.parkedUp && world.getClosestPlayer(pos.x, pos.y, pos.z, OUT_OF_SIGHT, false) == null) {
                car.parkedUp = homeTime;
                move(car);
            }
            if (car.parkedUp) continue;
            double wanted = car.lane.cruise;
            if (heading.dotProduct(car.lane.heading(car.distance + 9)) < 0.85) wanted = Math.min(wanted, TURN_SPEED);
            double gap = gapToOtherCars(i, car, pos, heading);
            wanted = Math.min(wanted, gap);
            if (car.pushing > 0) {
                car.pushing--;
            } else if (gap > 0) {
                car.stuck = 0;
            } else if (++car.stuck > STUCK_TICKS) {
                // Nothing here can give way to everything else forever: someone has to go first.
                car.stuck = 0;
                car.pushing = PUSH_TICKS;
            }
            if (pedestrianAhead(world, pos, heading)) {
                if (car.waited++ < PATIENCE) wanted = 0;
            } else {
                car.waited = 0;
            }
            boolean walking = !walks.isEmpty();
            if (walking && onAWalk(pos)) wanted = car.lane.cruise;
            else if (walking || parked || RunManager.scenePlaying() || sceneNear(players, pos) || Railway.blocksCrossing(pos.add(heading.multiply(CROSSING_LOOKAHEAD)))) wanted = 0;
            double change = wanted - car.speed;
            car.speed += MathHelper.clamp(change, walking ? -STOPPING_FOR_A_WALK : -BRAKING, ACCELERATION);
            car.distance = (car.distance + car.speed) % car.lane.length();
            move(car);
            if (car.speed > HIT_SPEED) {
                for (ServerPlayerEntity p : players) if (!WalkScene.active(p)) meet(world, car, p);
            }
            if (ticks % ENGINE_GAP == i % ENGINE_GAP && car.speed > 0.05) {
                world.playSound(null, pos.x, pos.y, pos.z, com.kokuhaku.ModSounds.CAR_ENGINE, SoundCategory.NEUTRAL, 1.1f, 0.75f + (float) car.speed * 0.8f);
            }
        }
    }

    /** Nothing drives through the middle of a scene. */
    private static boolean sceneNear(List<ServerPlayerEntity> players, Vec3d pos) {
        for (ServerPlayerEntity p : players) if (RunManager.inScene(p) && p.getPos().squaredDistanceTo(pos) < SCENE_REACH * SCENE_REACH) return true;
        return false;
    }

    /** How fast this car can go given the ones in front of it. Whoever's earlier in the list goes first at a junction. */
    private static double gapToOtherCars(int index, Car car, Vec3d pos, Vec3d heading) {
        double limit = Double.MAX_VALUE;
        for (int j = 0; j < cars.size(); j++) {
            if (j == index) continue;
            Car other = cars.get(j);
            if (other.parkedUp) continue;
            if (car.pushing > 0 && other.lane != car.lane) continue;
            Vec3d to = other.pos().subtract(pos);
            double ahead = to.dotProduct(heading), across = Math.abs(to.x * heading.z - to.z * heading.x);
            if (ahead <= 0 || ahead > BRAKE_RANGE || across > LANE_WIDTH) continue;
            double together = heading.dotProduct(other.heading());
            if (together < -0.3) continue; // coming the other way
            if (Math.abs(together) < 0.5 && index < j) continue;
            limit = Math.min(limit, ahead < STOP_GAP ? 0 : other.speed);
        }
        return limit;
    }

    private static boolean pedestrianAhead(ServerWorld world, Vec3d pos, Vec3d heading) {
        Vec3d front = pos.add(heading.multiply(4));
        Box box = new Box(front, front).expand(3, 2, 3).offset(0, 1, 0);
        for (StudentEntity e : world.getEntitiesByClass(StudentEntity.class, box, e -> !e.hasVehicle())) {
            Vec3d to = e.getPos().subtract(pos);
            if (to.dotProduct(heading) > 0 && Math.abs(to.x * heading.z - to.z * heading.x) < PEDESTRIAN_WIDTH) return true;
        }
        return false;
    }

    private static void move(Car car) {
        Vec3d p = car.pos(), heading = car.heading();
        float yaw = (float) (Math.toDegrees(Math.atan2(heading.z, heading.x)) - 90);
        car.body.setPosition(p.x, car.parkedUp ? UNDER_THE_ROAD : p.y, p.z);
        car.body.setVelocity(Vec3d.ZERO);
        for (MobEntity e : List.of(car.body, car.driver)) {
            e.setYaw(yaw);
            e.setBodyYaw(yaw);
            e.setHeadYaw(yaw);
        }
    }

    /** It leans on the horn if it sees you, and it doesn't stop. */
    private static void meet(ServerWorld world, Car car, ServerPlayerEntity p) {
        Vec3d pos = car.pos(), heading = car.heading(), to = p.getPos().subtract(pos);
        if (Math.abs(to.y) > 2) return;
        double ahead = to.dotProduct(heading), across = Math.abs(to.x * heading.z - to.z * heading.x);
        // It counts from a moment before the bumper arrives, so the scene can show it coming.
        if (ahead > -HIT_LENGTH && ahead < HIT_LENGTH + car.speed * IMPACT_LEAD && across < HIT_WIDTH) {
            RunManager.hitByCar(p, pos);
        } else if (ahead > 0 && ahead < HORN_RANGE && across < HIT_WIDTH && ticks - car.lastHorn > HORN_GAP) {
            car.lastHorn = ticks;
            world.playSound(null, pos.x, pos.y, pos.z, com.kokuhaku.ModSounds.CAR_HORN, SoundCategory.NEUTRAL, 2.5f, 1f);
        }
    }

    /** Developer testing only: stands you in the road in front of the first car on the main road. */
    static void devRoad(ServerPlayerEntity p) {
        if ("rail".equals(System.getenv("KOKUHAKU_CAR"))) {
            p.teleport(p.getServerWorld(), -720.5, 25, -54.5, -90f, 8f); // on the platform, facing the train
            return;
        }
        if ("track".equals(System.getenv("KOKUHAKU_CAR"))) {
            p.teleport(p.getServerWorld(), -714.5, 23, -85.5, 0f, 0f); // in the way, on the level crossing
            return;
        }
        if ("watch".equals(System.getenv("KOKUHAKU_CAR"))) {
            p.teleport(p.getServerWorld(), -641.5, 22, -30.5, 90f, 0f);
            if (System.getenv("KOKUHAKU_GLASSES") != null) {
                int girl = Roster.active(1).stream().filter(i -> Roster.get(i).female() && Roster.get(i).glasses() == Traits.Glasses.YES).findFirst().orElseThrow();
                int[] skins = {girl, 27};
                double[][] spots = {{-643.1, -30.5, -90}, {-643.0, -29.0, -130}};
                for (int i = 0; i < skins.length; i++) {
                    StudentEntity e = Kokuhaku.STUDENT.create(p.getServerWorld());
                    if (e == null) continue;
                    e.refreshPositionAndAngles(spots[i][0], 22, spots[i][1], (float) spots[i][2], 0f);
                    e.setBodyYaw((float) spots[i][2]);
                    e.setHeadYaw((float) spots[i][2]);
                    e.setSkin(skins[i]);
                    e.setAiDisabled(true);
                    p.getServerWorld().spawnEntity(e);
                }
            }
            return;
        }
        if ("park".equals(System.getenv("KOKUHAKU_CAR"))) {
            parked = true;
            for (Car car : cars) {
                if (car.lane.loop) continue;
                car.speed = 0;
                move(car);
                Vec3d at = car.pos().add(-7, 0, 0);
                p.teleport(p.getServerWorld(), at.x, at.y, at.z, -90f, 8f);
                return;
            }
        }
        for (Car car : cars) {
            if (car.lane.loop) continue;
            Vec3d ahead = car.lane.at(car.distance + 30);
            Vec3d back = car.pos().subtract(ahead);
            float yaw = (float) (Math.toDegrees(Math.atan2(back.z, back.x)) - 90);
            p.teleport(p.getServerWorld(), ahead.x, ahead.y, ahead.z, yaw, 0f);
            return;
        }
    }
}
