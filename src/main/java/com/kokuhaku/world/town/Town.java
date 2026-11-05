package com.kokuhaku.world.town;

import com.kokuhaku.block.LockerBlock;
import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.block.ThinPanelBlock;
import com.kokuhaku.game.Job;
import com.kokuhaku.game.Pages;
import com.kokuhaku.game.Routines;
import com.kokuhaku.game.Traits;
import com.kokuhaku.game.Schedule;
import com.kokuhaku.game.Traits.District;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The town is the Chinetsu Town map. Everywhere you can walk, and every
 * reachable bedroom, comes from data/kokuhaku/town.txt (tools/MapNav.java).
 * This class adds what the map lacks: shoe lockers, vending machines and job boards.
 *
 * Constructed with a world it also places those blocks; with null it only
 * works out the layout, which is identical either way.
 */
public final class Town {
    public static final int MIN_X = -800, MAX_X = -470, MIN_Z = -240, MAX_Z = 110;
    /** Ground floor of the school. */
    private static final int SCHOOL_Y = 25, UPPER_Y = 32;
    public static final int UPPER_FLOOR = UPPER_Y;
    private static final String[] ROOM_NAMES = {"Japanese", "Mathematics", "English", "History", "Science", "Art",
            "Music", "Home Economics", "Homeroom 2-A", "Homeroom 2-B", "", ""};
    public static final int LOCKERS = 42;
    /** Crossings are three blocks wide, so one each side of the line is the stripes; two takes in a wide turn. */
    private static final int CROSSING_COUNT = 20, ON_THE_STRIPES = 1;
    public static final int WIDE_TURN = 2;

    /** bedHead/bedFoot are the two blocks of the home's bed; a and b are opposite corners of the bedroom. */
    public record Home(int index, District district, int inside, BlockPos bedHead, BlockPos bedFoot, BlockPos a, BlockPos b) {
        public boolean isBed(BlockPos p) {
            return p.equals(bedHead) || p.equals(bedFoot);
        }

        /** Camera spot for the bedroom cutscenes: the corner furthest from the bed, up by the ceiling. */
        public Vec3d cameraSpot() {
            int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
            int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
            double x = Math.abs(bedHead.getX() - minX) > Math.abs(bedHead.getX() - maxX) ? minX + 0.3 : maxX + 0.7;
            double z = Math.abs(bedHead.getZ() - minZ) > Math.abs(bedHead.getZ() - maxZ) ? minZ + 0.3 : maxZ + 0.7;
            return new Vec3d(x, a.getY() + 2.1, z);
        }

        public Vec3d bedCenter() {
            return new Vec3d((bedHead.getX() + bedFoot.getX()) / 2.0 + 0.5, bedHead.getY() + 0.6, (bedHead.getZ() + bedFoot.getZ()) / 2.0 + 0.5);
        }

        /** Strictly within the room's walkable floor. */
        public boolean containsBlock(BlockPos p) {
            return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                    && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
        }

        /** Whether a standing position is inside this room. */
        public boolean contains(Vec3d p) {
            return p.x >= Math.min(a.getX(), b.getX()) - 1 && p.x < Math.max(a.getX(), b.getX()) + 2
                    && p.z >= Math.min(a.getZ(), b.getZ()) - 1 && p.z < Math.max(a.getZ(), b.getZ()) + 2
                    && p.y >= a.getY() - 1 && p.y < a.getY() + 3;
        }
    }

    /** A place to sit: walk to {@code node}, then settle onto {@code chair} (null: stand at the back). */
    public record Seat(int node, Vec3d chair) {
    }

    /** A room you can be in for a lesson. */
    public record Room(int x1, int z1, int x2, int z2, int y) {
        public boolean contains(Vec3d p) {
            return p.x >= x1 && p.x < x2 + 1 && p.z >= z1 && p.z < z2 + 1 && Math.abs(p.y - y) < 3;
        }
    }

    public final Graph graph = new Graph();
    public final List<Home> homes = new ArrayList<>();
    /** Named waypoints: rooms, club spots, hangouts. */
    public final Map<String, Integer> places = new HashMap<>();
    /** A chair at lunch: the floor behind it, the chair, the way you face in it, which table, and where the tray goes. */
    public record LunchSeat(int node, BlockPos chair, float yaw, int table, Vec3d tray) {
    }

    public final List<LunchSeat> lunchSeats = new ArrayList<>();
    /** Seats per classroom, by room number, e.g. "01". */
    public final Map<String, List<Seat>> seats = new HashMap<>();
    public final Map<String, Room> rooms = new HashMap<>();

    public final Map<BlockPos, Job> jobBoards = new HashMap<>();
    /** Haruka's diary pages, where they lie. */
    public final Map<BlockPos, Integer> pages = new HashMap<>();
    /** The one place that says what the name means. */
    public BlockPos trueName;
    /** Every block of the rock it's on. */
    public final java.util.Set<BlockPos> trueRock = new java.util.HashSet<>();

    /** Club registers and residents' boards, and what each one lists. */
    public final Map<BlockPos, String> registers = new HashMap<>();
    /** A hospital bed for each player. */
    public final List<Home> wards = new ArrayList<>();
    /** Every zebra crossing: x1, z1, x2, z2. */
    private static final List<int[]> CROSSINGS = new ArrayList<>();
    /** The school's front foyer, where notes and lockers are. */
    public int foyer;

    private final ServerWorld world;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    public Town(ServerWorld world) {
        this.world = world;
        importMap();
        assignDistricts();
        school();
        grounds();
        lockers();
        furniture();
        hospital();
        diary();
        registers();
        levelCrossings();
    }

    /**
     * The map ramps its kerbs with half stairs, which leaves a half-block dip
     * right where people step off the pavement. At a crossing the kerb is flush.
     */
    private void levelCrossings() {
        if (world == null) return;
        for (int[] c : CROSSINGS) {
            for (int x = Math.min(c[0], c[2]) - 2; x <= Math.max(c[0], c[2]) + 2; x++) {
                for (int z = Math.min(c[1], c[3]) - 2; z <= Math.max(c[1], c[3]) + 2; z++) {
                    BlockState kerb = world.getBlockState(cursor.set(x, 21, z));
                    if (kerb.isOf(Blocks.QUARTZ_STAIRS)) set(x, 21, z, Blocks.QUARTZ_BLOCK.getDefaultState());
                    else if (kerb.isOf(Blocks.COBBLESTONE_STAIRS)) set(x, 21, z, Blocks.COBBLESTONE.getDefaultState());
                }
            }
        }
    }

    /** Every club keeps a sign-in sheet in its room, and every neighbourhood a residents' board on the street. */
    private void registers() {
        for (Traits.Club club : Traits.Club.values()) notice(place(Routines.clubPlace(club)), -2, "club:" + club.name());
        notice(place("shops"), -2, "shop");
        notice(place("courtyard_w"), 2, "shop");
        for (District d : District.values()) {
            List<Home> here = homes.stream().filter(h -> h.district() == d).toList();
            double x = here.stream().mapToInt(h -> h.bedHead().getX()).average().orElse(0), z = here.stream().mapToInt(h -> h.bedHead().getZ()).average().orElse(0);
            notice(graph.nearest(new Vec3d(x, 22, z)), 1, "district:" + d.name());
        }
    }

    /** A board on a post beside a spot, the same as a job board. */
    private void notice(int node, int dx, String what) {
        BlockPos p = graph.pos(node);
        BlockPos at = new BlockPos(p.getX() + dx, p.getY() + 1, p.getZ());
        registers.put(at, what);
        set(at.getX(), at.getY() - 1, at.getZ(), Blocks.DARK_OAK_FENCE.getDefaultState());
        set(at.getX(), at.getY(), at.getZ(), ModBlocks.JOB_BOARD.getDefaultState().with(ThinPanelBlock.FACING, Direction.SOUTH));
    }

    /** Every crossing, as x1 z1 x2 z2. */
    public static List<int[]> crossings() {
        return java.util.Collections.unmodifiableList(CROSSINGS);
    }

    /** The road surface, which nobody on foot goes near unless they're at a crossing. */
    public static boolean isRoad(BlockView view, int x, int z) {
        return isRoad(view, x, z, ON_THE_STRIPES);
    }

    /** The road, with this much room either side of the line of a crossing still counting as the crossing. */
    public static boolean isRoad(BlockView view, int x, int z, int margin) {
        BlockState ground = view.getBlockState(new BlockPos(x, 21, z));
        if (!ground.isOf(Blocks.COBBLESTONE) && !ground.isOf(Blocks.COBBLESTONE_STAIRS)) return false;
        if (x >= -750 && x <= -728 && z >= -169 && z <= -150) return false; // the edging round the park
        for (int[] c : CROSSINGS) {
            if (x >= Math.min(c[0], c[2]) - margin && x <= Math.max(c[0], c[2]) + margin && z >= Math.min(c[1], c[3]) - margin && z <= Math.max(c[1], c[3]) + margin) return false;
        }
        return true;
    }

    /** Three private rooms on the ground floor, and a way in from the crossing on the main road. */
    private void hospital() {
        if (world != null) new HospitalBuilder(world).build();
        int z = HospitalBuilder.MIDDLE;
        int gate = near(-641, 22, z);
        int door = walk(gate, -630, z, -617, z, -604, z);
        for (int slot = 0; slot < 3; slot++) {
            int x = -596 + slot * 8;
            int corridor = walk(door, x, z);
            int inside = walk(corridor, x, z + 3, x, z + 10);
            wards.add(new Home(-1, District.STATION, inside, new BlockPos(x - 2, 22, HospitalBuilder.SOUTH - 1), new BlockPos(x - 2, 22, HospitalBuilder.SOUTH - 2),
                    new BlockPos(x - 3, 22, z + 4), new BlockPos(x + 3, 22, HospitalBuilder.SOUTH - 1)));
            door = corridor;
        }
        places.put("hospital", door);
    }

    /** A chain of street-level waypoints from an existing one; returns the last. */
    private int walk(int from, int... xz) {
        int at = from;
        for (int i = 0; i + 1 < xz.length; i += 2) {
            int next = graph.node(xz[i], 22, xz[i + 1]);
            graph.link(at, next);
            at = next;
        }
        return at;
    }

    /** Where she left them. The same spots every run, a few steps away from where anyone would stop. */
    private void diary() {
        trueName = graph.pos(graph.wander(place("beach"), 9, new java.util.Random(1313L)));
        trueRock.add(trueName);
        set(trueName.getX(), trueName.getY(), trueName.getZ(), ModBlocks.ROCK.getDefaultState());
        for (int i = 0; i < Pages.PLACES.length; i++) {
            BlockPos at = graph.pos(graph.wander(place(Pages.PLACES[i]), 3, new java.util.Random(31L * i + 7)));
            pages.put(at, i);
        }
        pages.put(HospitalBuilder.recordsShelf(), Pages.PLACES.length);
        for (BlockPos at : pages.keySet()) set(at.getX(), at.getY(), at.getZ(), ModBlocks.EVIDENCE.getDefaultState());
    }


    public int place(String name) {
        Integer id = places.get(name);
        if (id == null) throw new IllegalArgumentException("No place called " + name);
        return id;
    }

    private void set(int x, int y, int z, BlockState s) {
        if (world != null) world.setBlockState(cursor.set(x, y, z), s, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private int near(int x, int y, int z) {
        return graph.nearest(new Vec3d(x + 0.5, y, z + 0.5));
    }

    private void importMap() {
        List<int[]> rawHomes = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Town.class.getResourceAsStream("/data/kokuhaku/town.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "NODE" -> {
                        int n = graph.node(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                        if (p[4].equals("1")) graph.setSoft(n);
                    }
                    case "LINK" -> graph.link(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    case "CROSS" -> {
                        if (CROSSINGS.size() < CROSSING_COUNT) CROSSINGS.add(new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])});
                    }
                    case "HOME" -> {
                        int[] v = new int[10];
                        for (int i = 0; i < 10; i++) v[i] = Integer.parseInt(p[i + 1]);
                        rawHomes.add(v);
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read town data", e);
        }
        for (int[] h : rawHomes) {
            BlockPos head = new BlockPos(h[0], h[1], h[2]), foot = new BlockPos(h[3], h[1], h[4]);
            homes.add(new Home(homes.size(), District.NORTH_HILL, h[9], head, foot, new BlockPos(h[5], h[1], h[6]), new BlockPos(h[7], h[1], h[8])));
        }
    }

    /** Four equal neighbourhoods: split the homes at the median x, then the median z. */
    private void assignDistricts() {
        List<Home> sorted = new ArrayList<>(homes);
        sorted.sort(Comparator.comparingInt(h -> h.bedHead().getX()));
        int half = sorted.size() / 2;
        List<Home> west = new ArrayList<>(sorted.subList(0, half)), east = new ArrayList<>(sorted.subList(half, sorted.size()));
        Map<Integer, District> by = new HashMap<>();
        split(west, District.SHRINE, District.RIVERSIDE, by);
        split(east, District.NORTH_HILL, District.STATION, by);
        for (int i = 0; i < homes.size(); i++) {
            Home h = homes.get(i);
            homes.set(i, new Home(h.index(), by.get(h.index()), h.inside(), h.bedHead(), h.bedFoot(), h.a(), h.b()));
        }
    }

    private static void split(List<Home> side, District north, District south, Map<Integer, District> by) {
        side.sort(Comparator.comparingInt(h -> h.bedHead().getZ()));
        for (int i = 0; i < side.size(); i++) by.put(side.get(i).index(), i < side.size() / 2 ? north : south);
    }

    /**
     * The twelve numbered classrooms, six on each floor: three rows of stair
     * chairs facing the board to the north, and a sign by every door.
     */
    private void school() {
        int[][] rows = {{-64, 5}, {-61, 6}, {-58, 6}};
        int number = 1;
        for (int y : new int[]{SCHOOL_Y, UPPER_Y}) {
            for (int r = 0; r < 3; r++) {
                int dz = r * 16;
                for (int side = 0; side < 2; side++) {
                    String name = Schedule.roomKey(number);
                    int x0 = side == 0 ? -606 : -585; // first chair of a full row
                    List<Seat> list = new ArrayList<>();
                    for (int[] row : rows) {
                        int chairs = row[1];
                        int firstX = chairs == 6 ? x0 : (side == 0 ? x0 : x0 + 2);
                        for (int c = 0; c < chairs; c++) {
                            int x = firstX + 2 * c, z = row[0] + dz;
                            list.add(new Seat(aisle(x, y, z + 1), new Vec3d(x + 0.5, y, z + 0.5)));
                        }
                    }
                    // Three more stand along the back wall: the rooms are a little too small.
                    for (int c = 0; c < 4; c++) list.add(new Seat(aisle(x0 + 3 * c, y, -57 + dz), null));
                    seats.put(name, list);
                    int bx1 = side == 0 ? -607 : -586, bx2 = side == 0 ? -595 : -574;
                    rooms.put(name, new Room(bx1, -70 + dz, bx2, -57 + dz, y));
                    places.put("center:" + name, near(bx1 + 6, y, -60 + dz));

                    places.put("hall:" + name, near(-590, y, -64 + dz));
                    doorSign(side == 0 ? -593 : -588, y + 1, -62 + dz, side == 0 ? Direction.EAST : Direction.WEST, number);
                    number++;
                }
            }
        }
    }


    private void doorSign(int x, int y, int z, Direction facing, int number) {
        if (world == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isAir(pos)) return;
        world.setBlockState(pos, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String what = ROOM_NAMES[number - 1];
            sign.setText(new SignText().withMessage(1, Text.literal("Room " + Schedule.roomKey(number))).withMessage(2, Text.literal(what)), true);
            sign.setWaxed(true);
            sign.markDirty();
        }
    }

    /** The tables in the lunch hall: their chairs' x range, and the z of the chairs along each long side. */
    private static final int[][] LUNCH_TABLES = {{-599, -597, -118, -115}, {-599, -597, -112, -109}, {-599, -597, -106, -103}, {-594, -593, -115, -112}, {-594, -593, -109, -106}, {-589, -589, -114, -112}, {-589, -589, -109, -107}};
    private static final int LUNCH_Y = 25;

    private void lunchHall() {
        for (int t = 0; t < LUNCH_TABLES.length; t++) {
            int[] table = LUNCH_TABLES[t];
            int north = table[2], south = table[3];
            double reach = south - north == 3 ? 0.85 : 0.72;
            for (int x = table[0]; x <= table[1]; x++) {
                lunchSeats.add(new LunchSeat(floorAisle(x, north - 1), new BlockPos(x, LUNCH_Y, north), 0f, t, new Vec3d(x + 0.5, LUNCH_Y + 1.02, north + 0.5 + reach)));
                lunchSeats.add(new LunchSeat(floorAisle(x, south + 1), new BlockPos(x, LUNCH_Y, south), 180f, t, new Vec3d(x + 0.5, LUNCH_Y + 1.02, south + 0.5 - reach)));
            }
        }
        places.put("lunch_counter", floorAisle(-586, -115));
    }

    /** A waypoint on the hall's floor, joined to the nearest one that's on the floor too: the nearest of all can be on a table. */
    private int floorAisle(int x, int z) {
        Vec3d at = new Vec3d(x + 0.5, LUNCH_Y, z + 0.5);
        int best = -1;
        double closest = Double.MAX_VALUE;
        for (int n = 0; n < graph.size(); n++) {
            BlockPos pos = graph.pos(n);
            if (pos.getY() != LUNCH_Y) continue;
            double gap = at.squaredDistanceTo(Vec3d.ofBottomCenter(pos));
            if (gap < closest) {
                closest = gap;
                best = n;
            }
        }
        int node = graph.node(x, LUNCH_Y, z);
        if (best >= 0 && node != best) graph.link(node, best);
        return node;
    }

    /** Anywhere that was on top of something moves to the nearest bit of floor that's joined up. */
    public int offTheFurniture() {
        int moved = 0;
        for (var entry : places.entrySet()) {
            if (!graph.neighbours(entry.getValue()).isEmpty()) continue;
            BlockPos from = graph.pos(entry.getValue());
            int best = -1;
            double closest = Double.MAX_VALUE;
            for (int n = 0; n < graph.size(); n++) {
                if (graph.neighbours(n).isEmpty()) continue;
                double gap = from.getSquaredDistance(graph.pos(n));
                if (gap < closest) {
                    closest = gap;
                    best = n;
                }
            }
            if (best >= 0) {
                entry.setValue(best);
                moved++;
            }
        }
        return moved;
    }

    /** A waypoint in the aisle by a seat, joined to the nearest waypoint in the room. */
    private int aisle(int x, int y, int z) {
        int existing = near(x, y, z);
        int n = graph.node(x, y, z);
        if (n != existing) graph.link(n, existing);
        return n;
    }

    private void grounds() {
        foyer = near(-604, SCHOOL_Y, -82);
        places.put("foyer", foyer);
        places.put("center:Library", near(-590, 32, -64));
        places.put("center:Darkroom", near(-577, SCHOOL_Y, -86));
        places.put("center:Cafeteria", near(-590, SCHOOL_Y, -112));
        places.put("courtyard_w", near(-590, SCHOOL_Y, -84));
        places.put("courtyard_e", near(-590, SCHOOL_Y, -80));
        places.put("gym", near(-546, 22, -36));
        places.put("gym_stage", near(-590, SCHOOL_Y, -105));
        places.put("gym_kendo", near(-546, 22, -30));
        places.put("pool", near(-540, 22, 42));
        places.put("lab", near(-600, UPPER_Y, -80));
        places.put("kitchen", near(-577, SCHOOL_Y, -110));
        lunchHall();
        for (int z : new int[]{-20, -54}) hoop(-548, z, z < -30 ? 1 : -1);
        places.put("tennis", near(-546, 22, -80));
        places.put("field", near(-503, 22, -58));
        places.put("station", near(-693, 22, -72));
        places.put("shops", near(-700, 22, -120));
        places.put("park", near(-526, 22, -124));
        places.put("beach", near(-724, 22, -214));
    }

    /** A hoop at the end of the court: a post, a board, and the rim out in front of it. */
    private void hoop(int x, int z, int front) {
        for (int y = 22; y <= 26; y++) set(x, y, z, Blocks.IRON_BARS.getDefaultState());
        for (int dx = -1; dx <= 1; dx++) for (int y = 26; y <= 27; y++) set(x + dx, y, z, Blocks.WHITE_CONCRETE.getDefaultState());
        set(x, 26, z + front, Blocks.ORANGE_CARPET.getDefaultState());
    }

    /** Two banks of numbered shoe lockers in the entrance hall. */
    private void lockers() {
        if (world == null) return;
        int n = 1;
        for (int z : new int[]{-85, -79}) {
            Direction facing = z == -85 ? Direction.SOUTH : Direction.NORTH;
            for (int y = 25; y <= 27; y++)
                for (int x = -608; x <= -602; x++)
                    set(x, y, z, ModBlocks.LOCKER.getDefaultState().with(LockerBlock.FACING, facing).with(LockerBlock.NUMBER, n++));
        }
    }

    private void furniture() {
        vending("foyer", 3, -3);
        vending("station", 2, 2);
        vending("shops", 2, 2);
        vending("park", 2, 2);
        vending("beach", 2, 2);
        shopCounter();
        board(Job.KIOSK, "station", -2);
        board(Job.LIBRARY, "center:Library", 2);
    }

    /** The till in the shop by the station: right-click the counter to work a shift. */
    private void shopCounter() {
        for (int x = -696; x <= -694; x++) {
            for (int z = -72; z <= -70; z++) {
                for (int y = 22; y <= 23; y++) jobBoards.put(new BlockPos(x, y, z), Job.KONBINI);
            }
        }
    }

    private void vending(String place, int dx, int dz) {
        BlockPos p = graph.pos(place(place));
        set(p.getX() + dx, p.getY(), p.getZ() + dz, ModBlocks.VENDING_MACHINE.getDefaultState());
    }

    /** A board at eye height beside a spot. */
    private void board(Job job, String place, int dx) {
        BlockPos p = graph.pos(place(place));
        BlockPos at = new BlockPos(p.getX() + dx, p.getY() + 1, p.getZ());
        jobBoards.put(at, job);
        set(at.getX(), at.getY() - 1, at.getZ(), net.minecraft.block.Blocks.DARK_OAK_FENCE.getDefaultState());
        set(at.getX(), at.getY(), at.getZ(), ModBlocks.JOB_BOARD.getDefaultState().with(ThinPanelBlock.FACING, Direction.SOUTH));
    }
}
