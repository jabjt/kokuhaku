package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackPropagator;
import com.simibubi.create.content.trains.track.TrackShape;
import net.createmod.catnip.data.Couple;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's railway. The map's train on track 1 is only a shape made of
 * blocks, so this lays real track under it, gives it bogeys and controls, and
 * has Create assemble those same blocks into a train that runs a timetable.
 */
public final class Railway {
    /** A straight line down the middle of the track bed; the north end is further than the map's rails bend. */
    private static final int TRACK_X = -715, TRACK_Y = 22, NORTH_END = -198;
    /** Then through the hill, along the south shore and up the east side: where each stretch ends, and the corners between them. */
    private static final int SOUTH_END = 120, SHORE_Z = 134, SHORE_WEST = -701, SHORE_EAST = -454, EAST_X = -440, EAST_NORTH = -245, HILL_FROM = 9, CLEARANCE = 8, BODY_REACH = 3;
    private static final int FESTIVAL_Z = -70, SNOW_Z = -232, STOP_WEST = -449, STOP_EAST = -444, STOP_FROM_MARKER = 6, STOP_LENGTH = 19;
    /** The way through the bank to the festival field. */
    private static final int GAP_WEST = -497, GAP_EAST = -450, GAP_NORTH = -60, GAP_SOUTH = -52, GAP_TOP = 45;
    /** The parked train: its body sits a block above where bogeys go. */
    private static final int TRAIN_NORTH = -76, TRAIN_SOUTH = -32, TRAIN_WEST = -718, TRAIN_EAST = -712, TRAIN_TOP = 29;
    private static final int BOGEY_NORTH = -70, BOGEY_SOUTH = -38, CONTROLS_NORTH = -73, CONTROLS_SOUTH = -35;
    /** Assembly starts a block past the station marker, and that's where the leading bogey has to be. */
    private static final int STATION_Z = BOGEY_SOUTH + 1, TERMINUS_Z = -129;
    /** Beside the doors when the train is stopped at the terminus, clear of the road crossings either side. */
    private static final int PLATFORM_WEST = -711, PLATFORM_EAST = -706, PLATFORM_NORTH = -123, PLATFORM_SOUTH = -104, PLATFORM_TOP = 24;
    public static final String STATION_NAME = "Chinetsu Station", TERMINUS_NAME = "Chinetsu North", FESTIVAL_NAME = "Festival Grounds", SNOW_NAME = "Mt. Chinetsu";
    private static final String LINE_NAME = "Chinetsu Line";
    /** Barriers: how close to the track counts, and how far either way a train holds the road. */
    private static final double CROSSING_WIDTH = 6, STANDING_REACH = 6, MOVING_REACH = 22;
    /** Its body, from the middle of the track; how far it runs past the bogeys; and what counts as under way. */
    private static final double BODY_WIDTH = 3.8, NOSE = 6.5, FLOOR_Y = 24.5, MOVING = 0.05;
    private static final int WAIT_SECONDS = 25, SECONDS = 1;
    private static final double THROTTLE = 0.35;

    /** Both tracks, and the ground either side of them. */
    private static final double LINE_WIDTH = 9;

    private enum Step { LAY, WAIT_FOR_STATIONS, ENTER_ASSEMBLY, ASSEMBLE, WAIT_FOR_TRAIN, READY }

    private static Step step = Step.LAY;
    private static int wait = 0;
    private static Train train = null;
    private static int stalled = 0;
    private static final int STALLED_FOR = 100, TIMETABLE_STOPS = 4;

    private Railway() {
    }

    private static BlockState block(String name) {
        return Registries.BLOCK.get(new Identifier("create", name)).getDefaultState();
    }

    public static void reset() {
        step = Step.LAY;
        wait = 0;
        train = null;
    }

    private static BlockPos stationBlock(int x, int z) {
        return new BlockPos(x, TRACK_Y - 2, z);
    }

    /** Empties a stretch a train has to fit through. */
    private static void clearWay(ServerWorld world, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = TRACK_Y; y <= TRACK_Y + CLEARANCE; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (!world.isAir(at)) world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static void rail(ServerWorld world, BlockPos at, BlockState track) {
        if (world.getBlockState(at.down()).isAir() || world.getBlockState(at.down()).isOf(Blocks.GRASS_BLOCK)) world.setBlockState(at.down(), Blocks.GRAVEL.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(at, track, Block.NOTIFY_ALL);
    }

    /** A proper curve between two straight stretches, the way the track tool would lay one. */
    private static void curve(ServerWorld world, BlockPos a, Vec3d out, BlockPos b, Vec3d back) {
        BlockState first = world.getBlockState(a).with(TrackBlock.HAS_BE, true), second = world.getBlockState(b).with(TrackBlock.HAS_BE, true);
        world.setBlockState(a, first, Block.NOTIFY_ALL);
        world.setBlockState(b, second, Block.NOTIFY_ALL);
        TrackBlock track = (TrackBlock) first.getBlock();
        BezierConnection connection = new BezierConnection(Couple.create(a, b), Couple.create(track.getCurveStart(world, a, first, out), track.getCurveStart(world, b, second, back)),
                Couple.create(out, back), Couple.create(track.getUpNormal(world, a, first), track.getUpNormal(world, b, second)), true, false, TrackMaterial.ANDESITE);
        ((TrackBlockEntity) world.getBlockEntity(a)).addConnection(connection);
        ((TrackBlockEntity) world.getBlockEntity(b)).addConnection(connection.secondary());
        TrackPropagator.onRailAdded(world, a, first);
    }

    /** Through the hill, and anything else in the way. Before any track goes down. */
    private static void clearLine(ServerWorld world) {
        clearWay(world, TRACK_X - BODY_REACH, HILL_FROM, TRACK_X + BODY_REACH, SHORE_Z + BODY_REACH);
        clearWay(world, TRACK_X - BODY_REACH, SHORE_Z - BODY_REACH, EAST_X + BODY_REACH, SHORE_Z + BODY_REACH);
        clearWay(world, EAST_X - BODY_REACH, EAST_NORTH, EAST_X + BODY_REACH, SHORE_Z + BODY_REACH);
    }

    /** Past the old end of the line: through the hill, round the shore and up to the snow. */
    private static void extend(ServerWorld world) {
        BlockState alongX = block("track").with(TrackBlock.SHAPE, TrackShape.XO), alongZ = block("track").with(TrackBlock.SHAPE, TrackShape.ZO);
        for (int x = SHORE_WEST; x <= SHORE_EAST; x++) rail(world, new BlockPos(x, TRACK_Y, SHORE_Z), alongX);
        for (int z = EAST_NORTH; z <= SOUTH_END; z++) rail(world, new BlockPos(EAST_X, TRACK_Y, z), alongZ);
        curve(world, new BlockPos(TRACK_X, TRACK_Y, SOUTH_END), new Vec3d(0, 0, 1), new BlockPos(SHORE_WEST, TRACK_Y, SHORE_Z), new Vec3d(-1, 0, 0));
        curve(world, new BlockPos(SHORE_EAST, TRACK_Y, SHORE_Z), new Vec3d(1, 0, 0), new BlockPos(EAST_X, TRACK_Y, SOUTH_END), new Vec3d(0, 0, 1));
        // A way through the bank, from the platform to the field.
        for (int x = GAP_WEST; x <= GAP_EAST; x++) {
            for (int z = GAP_NORTH; z <= GAP_SOUTH; z++) {
                world.setBlockState(new BlockPos(x, TRACK_Y - 1, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int y = TRACK_Y; y <= GAP_TOP; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (!world.isAir(at)) world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        SkiSlope.build(world);
    }

    private static void lay(ServerWorld world) {
        int flags = Block.NOTIFY_ALL;
        BlockState track = block("track").with(TrackBlock.SHAPE, TrackShape.ZO);
        clearLine(world);
        for (int z = NORTH_END; z <= SOUTH_END; z++) {
            BlockPos at = new BlockPos(TRACK_X, TRACK_Y, z);
            if (world.isAir(at.down())) world.setBlockState(at.down(), Blocks.GRAVEL.getDefaultState(), flags);
            world.setBlockState(at, track, flags);
            if (z < TRAIN_NORTH || z > TRAIN_SOUTH) world.setBlockState(at.up(), Blocks.AIR.getDefaultState(), flags);
        }
        extend(world);
        // The body comes with us; the posts it stands on at ground level would stay behind.
        for (int z = TRAIN_NORTH; z <= TRAIN_SOUTH; z++) {
            for (int x = TRAIN_WEST; x <= TRAIN_EAST; x++) {
                if (x != TRACK_X) world.setBlockState(new BlockPos(x, TRACK_Y, z), Blocks.AIR.getDefaultState(), flags);
            }
        }
        for (int z : new int[]{BOGEY_NORTH, BOGEY_SOUTH}) {
            BlockPos at = new BlockPos(TRACK_X, TRACK_Y, z);
            BlockState here = world.getBlockState(at);
            world.setBlockState(at.up(), ((ITrackBlock) here.getBlock()).getBogeyAnchor(world, at, here), flags);
        }
        BlockState controls = block("controls");
        world.setBlockState(new BlockPos(TRACK_X, TRACK_Y + 3, CONTROLS_NORTH), controls.with(Properties.HORIZONTAL_FACING, Direction.NORTH), flags);
        world.setBlockState(new BlockPos(TRACK_X, TRACK_Y + 3, CONTROLS_SOUTH), controls.with(Properties.HORIZONTAL_FACING, Direction.SOUTH), flags);
        world.spawnEntity(new SuperGlueEntity(world, new Box(TRAIN_WEST, TRACK_Y + 1, TRAIN_NORTH, TRAIN_EAST + 1, TRAIN_TOP + 1, TRAIN_SOUTH + 1)));
        finishCurves(world);
        station(world, TRACK_X, STATION_Z, false);
        station(world, TRACK_X, TERMINUS_Z, true);
        // A station needs its own chunk running, out here where nobody is, or it drops off the line when the train's nearly there.
        keepAwake(world);
        station(world, EAST_X, FESTIVAL_Z, true);
        station(world, EAST_X, SNOW_Z, true);
        platform(world, PLATFORM_WEST, PLATFORM_EAST, PLATFORM_NORTH, PLATFORM_SOUTH, PLATFORM_WEST, TERMINUS_NAME);
        platform(world, STOP_WEST, STOP_EAST, FESTIVAL_Z + STOP_FROM_MARKER, FESTIVAL_Z + STOP_FROM_MARKER + STOP_LENGTH, STOP_EAST, FESTIVAL_NAME);
        platform(world, STOP_WEST, STOP_EAST, SNOW_Z + STOP_FROM_MARKER, SNOW_Z + STOP_FROM_MARKER + STOP_LENGTH, STOP_EAST, SNOW_NAME);
    }

    private static void keepAwake(ServerWorld world) {
        for (int z : new int[]{FESTIVAL_Z, SNOW_Z}) world.setChunkForced(EAST_X >> 4, z >> 4, true);
    }

    /** The straight the corners join was laid after them, so the graph hears about it again. */
    private static void finishCurves(ServerWorld world) {
        for (BlockPos at : List.of(new BlockPos(TRACK_X, TRACK_Y, SOUTH_END), new BlockPos(EAST_X, TRACK_Y, SOUTH_END))) TrackPropagator.onRailAdded(world, at, world.getBlockState(at));
    }

    /** The map has no stops out here, and nobody can jump back onto a train from the ground. edge is the side the train's on. */
    private static void platform(ServerWorld world, int west, int east, int north, int south, int edge, String name) {
        int flags = Block.NOTIFY_ALL;
        for (int z = north; z <= south; z++) {
            for (int x = west; x <= east; x++) {
                for (int y = TRACK_Y; y < PLATFORM_TOP; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.STONE_BRICKS.getDefaultState(), flags);
                BlockState top = x == edge ? Blocks.YELLOW_TERRACOTTA.getDefaultState() : Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState();
                world.setBlockState(new BlockPos(x, PLATFORM_TOP, z), top, flags);
            }
        }
        BlockState steps = Blocks.STONE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        for (int x = west; x <= east; x++) {
            if (x == edge) continue;
            for (int i = 0; i < PLATFORM_TOP - TRACK_Y + 1; i++) {
                int z = south + 1 + i, top = PLATFORM_TOP - i;
                for (int y = TRACK_Y; y < top; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.STONE_BRICKS.getDefaultState(), flags);
                world.setBlockState(new BlockPos(x, top, z), steps, flags);
            }
        }
        BlockPos sign = new BlockPos(edge == west ? east : west, PLATFORM_TOP + 1, north + 8);
        world.setBlockState(sign, Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION, edge == west ? 12 : 4), flags);
        if (world.getBlockEntity(sign) instanceof SignBlockEntity board) {
            board.setText(new SignText().withMessage(1, Text.literal(name)), true);
            board.setWaxed(true);
            board.markDirty();
        }
    }

    /** Stations are out of sight under the track: the platforms are the map's own. */
    private static void station(ServerWorld world, int x, int z, boolean arrivingNorthbound) {
        BlockPos at = stationBlock(x, z);
        world.setBlockState(at, block("track_station"), Block.NOTIFY_ALL);
        if (!(world.getBlockEntity(at) instanceof StationBlockEntity be)) throw new IllegalStateException("No station at " + at);
        NbtCompound nbt = be.createNbt();
        nbt.put("TargetTrack", NbtHelper.fromBlockPos(new BlockPos(x, TRACK_Y, z).subtract(at)));
        // Create's stations are one-way: a train can only pull in travelling the way the marker faces.
        nbt.putBoolean("TargetDirection", arrivingNorthbound);
        be.readNbt(nbt);
    }

    private static StationBlockEntity stationAt(ServerWorld world, int z) {
        return stationAt(world, TRACK_X, z);
    }

    private static StationBlockEntity stationAt(ServerWorld world, int x, int z) {
        return world.getBlockEntity(stationBlock(x, z)) instanceof StationBlockEntity be ? be : null;
    }

    /** Runs from the first tick with a player on the server, because assembling needs someone to answer to. */
    public static void tick(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (step == Step.READY) unstick();
        if (step == Step.READY || wait-- > 0) return;
        ServerWorld world = server.getOverworld();
        switch (step) {
            case LAY -> {
                if (players.isEmpty()) return;
                lay(world);
                wait = 40;
                step = Step.WAIT_FOR_STATIONS;
                Kokuhaku.LOG.info("Railway: track laid from z {} to {}", NORTH_END, SOUTH_END);
            }
            case WAIT_FOR_STATIONS -> {
                StationBlockEntity first = stationAt(world, STATION_Z), second = stationAt(world, TERMINUS_Z), third = stationAt(world, EAST_X, FESTIVAL_Z), fourth = stationAt(world, EAST_X, SNOW_Z);
                if (first == null || second == null || third == null || fourth == null || first.getStation() == null || second.getStation() == null || third.getStation() == null || fourth.getStation() == null) {
                    wait = 10;
                    return;
                }
                first.updateName(STATION_NAME);
                second.updateName(TERMINUS_NAME);
                third.updateName(FESTIVAL_NAME);
                fourth.updateName(SNOW_NAME);
                Kokuhaku.LOG.info("Railway: stations up, assembling towards {}", first.getAssemblyDirection());
                step = Step.ENTER_ASSEMBLY;
            }
            case ENTER_ASSEMBLY -> {
                if (players.isEmpty()) return;
                stationAt(world, STATION_Z).enterAssemblyMode(players.get(0));
                wait = 30; // the block changes state, and the station looks for bogeys on its own tick
                step = Step.ASSEMBLE;
            }
            case ASSEMBLE -> {
                if (players.isEmpty()) return;
                StationBlockEntity first = stationAt(world, STATION_Z);
                first.refreshAssemblyInfo();
                first.assemble(players.get(0).getUuid());
                wait = 20;
                step = Step.WAIT_FOR_TRAIN;
            }
            case WAIT_FOR_TRAIN -> {
                if (Create.RAILWAYS.trains.isEmpty()) {
                    Kokuhaku.LOG.warn("Railway: nothing assembled: {}", stationAt(world, STATION_Z).createNbt().get("LastException"));
                    step = Step.READY;
                    return;
                }
                train = Create.RAILWAYS.trains.values().iterator().next();
                stationAt(world, STATION_Z).exitAssemblyMode(); // a station won't take trains while it's building one
                train.throttle = THROTTLE;
                train.name = Text.literal(LINE_NAME);
                train.runtime.setSchedule(timetable(), true);
                step = Step.READY;
                Kokuhaku.LOG.info("Railway: {} has {} carriages, double ended {}, running", train.name.getString(), train.carriages.size(), train.doubleEnded);
            }
            default -> {
            }
        }
    }

    /** A train that's run past its stop can't back into it, so it gets on with the rest of the timetable. */
    private static void unstick() {
        if (train == null || train.derailed || train.runtime.state != ScheduleRuntime.State.PRE_TRANSIT || train.navigation.destination != null || Math.abs(train.speed) > MOVING) {
            stalled = 0;
            return;
        }
        if (++stalled < STALLED_FOR) return;
        stalled = 0;
        train.runtime.currentEntry = (train.runtime.currentEntry + 1) % TIMETABLE_STOPS;
        Kokuhaku.LOG.info("Railway: ran past a stop, going on to entry {}", train.runtime.currentEntry);
    }

    /** Whether it's come to rest at or just past the festival stop. */
    public static boolean atFestival() {
        if (train == null || train.graph == null || Math.abs(train.speed) > MOVING) return false;
        Vec3d at = middle();
        return Math.abs(at.x - EAST_X) < LINE_WIDTH && at.z < FESTIVAL_Z + STOP_FROM_MARKER + STOP_LENGTH + 10 && at.z > SNOW_Z;
    }

    /** Where the train thinks it's going, for logs. */
    public static String heading() {
        if (train == null || train.navigation.destination == null) return "nowhere";
        return train.navigation.destination.name + " in " + (int) train.navigation.distanceToDestination;
    }

    private static Schedule timetable() {
        Schedule schedule = new Schedule();
        schedule.cyclic = true;
        for (String stop : new String[]{TERMINUS_NAME, STATION_NAME, FESTIVAL_NAME, SNOW_NAME}) {
            ScheduleEntry entry = new ScheduleEntry();
            DestinationInstruction go = new DestinationInstruction();
            go.getData().putString("Text", stop);
            entry.instruction = go;
            ScheduledDelay delay = new ScheduledDelay();
            delay.getData().putInt("Value", WAIT_SECONDS);
            delay.getData().putInt("TimeUnit", SECONDS);
            List<ScheduleWaitCondition> all = new ArrayList<>();
            all.add(delay);
            entry.conditions = new ArrayList<>();
            entry.conditions.add(all);
            schedule.entries.add(entry);
        }
        return schedule;
    }

    private static Vec3d[] ends() {
        return new Vec3d[]{train.carriages.get(0).getLeadingPoint().getPosition(train.graph), train.carriages.get(0).getTrailingPoint().getPosition(train.graph)};
    }

    /** The closest point on the line through the train, seen from above, reaching a little past either end. */
    private static Vec3d closestOnTrain(Vec3d from, double past) {
        Vec3d[] end = ends();
        Vec3d along = end[1].subtract(end[0]).multiply(1, 0, 1);
        double length = along.length();
        if (length < 0.01) return end[0];
        Vec3d way = along.multiply(1 / length);
        double t = Math.max(-past, Math.min(length + past, from.subtract(end[0]).multiply(1, 0, 1).dotProduct(way)));
        return end[0].add(way.multiply(t));
    }

    private static double[] span() {
        double a = train.carriages.get(0).getLeadingPoint().getPosition(train.graph).z;
        double b = train.carriages.get(0).getTrailingPoint().getPosition(train.graph).z;
        return new double[]{Math.min(a, b), Math.max(a, b)};
    }

    /** Whether a spot is on or beside the line, where nobody takes a shortcut. */
    public static boolean nearLine(double x) {
        return Math.abs(x - TRACK_X - 0.5) <= LINE_WIDTH;
    }

    /** Whether a car shouldn't drive onto this spot: there's a train across it, or nearly. */
    public static boolean blocksCrossing(Vec3d spot) {
        if (train == null || train.graph == null || train.derailed || Math.abs(spot.x - TRACK_X - 0.5) > CROSSING_WIDTH) return false;
        if (Math.abs(ends()[0].x - TRACK_X - 0.5) > CROSSING_WIDTH) return false;
        double[] span = span();
        double reach = Math.abs(train.speed) < 0.01 ? STANDING_REACH : MOVING_REACH;
        return spot.z > span[0] - reach && spot.z < span[1] + reach;
    }

    /** The spot on the train closest to someone, so the scene can look at it. */
    public static Vec3d nearestPoint(Vec3d from) {
        if (train == null || train.graph == null) return from.add(0, 0, 3);
        return closestOnTrain(from, 0).withAxis(net.minecraft.util.math.Direction.Axis.Y, TRACK_Y);
    }

    /** Anyone on the ground in its way. Passengers stand above the floor, so they never count. */
    public static void meet(List<ServerPlayerEntity> players) {
        if (train == null || train.graph == null || Math.abs(train.speed) < MOVING) return;
        for (ServerPlayerEntity p : players) {
            if (p.getY() >= FLOOR_Y || TrainRide.on(p)) continue;
            if (closestOnTrain(p.getPos(), NOSE).multiply(1, 0, 1).distanceTo(p.getPos().multiply(1, 0, 1)) <= BODY_WIDTH) RunManager.hitByTrain(p);
        }
    }

    /** The stop the train is standing at, or null while it's moving. */
    public static String stoppedAt() {
        if (train == null || train.getCurrentStation() == null) return null;
        return train.getCurrentStation().name;
    }


    /** Halfway along the train, or where it starts if there isn't one. */
    public static Vec3d middle() {
        if (train == null || train.graph == null) return new Vec3d(TRACK_X + 0.5, TRACK_Y, (TRAIN_NORTH + TRAIN_SOUTH) / 2.0);
        Vec3d[] end = ends();
        return end[0].add(end[1]).multiply(0.5);
    }

    /** Where you step off for the festival. */
    public static Vec3d platform() {
        return new Vec3d((STOP_WEST + STOP_EAST) / 2.0 + 0.5, PLATFORM_TOP + 1, FESTIVAL_Z + STOP_FROM_MARKER + STOP_LENGTH / 2.0 + 0.5);
    }

    /** From the bottom of the platform steps, through the bank, to the field. */
    public static List<Vec3d> toTheField(Vec3d field) {
        int steps = FESTIVAL_Z + STOP_FROM_MARKER + STOP_LENGTH + PLATFORM_TOP - TRACK_Y + 3;
        double middle = (GAP_NORTH + GAP_SOUTH) / 2.0 + 0.5, stairs = (STOP_WEST + STOP_EAST - 1) / 2.0 + 0.5;
        return List.of(platform(), new Vec3d(stairs, TRACK_Y, steps), new Vec3d(GAP_EAST - 1.5, TRACK_Y, middle), new Vec3d(GAP_WEST + 1.5, TRACK_Y, middle), field);
    }

    /** Where the train's front is right now, for logs and tests. */
    public static String status() {
        if (train == null) return "no train";
        return String.format("speed %.2f, state %s, entry %d, derailed %s, at %s", train.speed, train.runtime.state, train.runtime.currentEntry, train.derailed,
                train.carriages.get(0).getLeadingPoint().getPosition(train.graph));
    }
}
