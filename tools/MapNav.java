import java.io.*;
import java.util.*;

/**
 * Finds everywhere a person can stand in the map (through doors, up stairs),
 * thins it into a waypoint graph, and picks a home for each reachable bedroom.
 * Writes data/kokuhaku/town.txt. Usage: java MapNav <grid.bin> <town.txt> <seedX> <seedZ>
 */
public class MapNav {
    static int X1, Z1, X2, Z2, Y1, Y2, W, H, L;
    static short[][][] grid;
    static String[] names;
    static boolean[] pass, floor, door;

    record Cell(int x, int y, int z) {}

    static boolean in(int x, int y, int z) {
        return x >= X1 && x <= X2 && z >= Z1 && z <= Z2 && y >= Y1 && y < Y2;
    }

    static int id(int x, int y, int z) {
        return grid[y - Y1][z - Z1][x - X1];
    }

    static String name(int x, int y, int z) {
        return names[id(x, y, z)];
    }

    static boolean noCollision(String n) {
        String s = n.replaceAll("\\[.*", "").replace("minecraft:", "");
        return s.equals("air") || s.equals("cave_air") || s.equals("grass") || s.equals("fern") || s.equals("tall_grass") || s.equals("large_fern")
                || s.endsWith("carpet") || s.endsWith("_button") || s.endsWith("pressure_plate") || s.contains("sign") || s.contains("torch")
                || s.equals("lever") || s.endsWith("rail") || s.equals("snow") || s.equals("dandelion") || s.equals("poppy") || s.endsWith("tulip")
                || s.equals("oxeye_daisy") || s.equals("cornflower") || s.equals("azure_bluet") || s.equals("allium") || s.equals("blue_orchid")
                || s.equals("lily_of_the_valley") || s.equals("rose_bush") || s.equals("peony") || s.equals("lilac") || s.equals("sunflower")
                || s.endsWith("_sapling") || s.equals("vine") || s.equals("tripwire") || s.endsWith("_banner") || s.equals("dead_bush");
    }

    static boolean woodDoor(String n) {
        return n.contains("_door[") && !n.contains("iron_door");
    }

    static boolean goodFloor(String n) {
        String s = n.replaceAll("\\[.*", "");
        if (noCollision(n) || s.contains("water") || s.contains("lava") || s.contains("fence") || s.contains("_wall") || s.contains("door")
                || s.contains("iron_bars") || s.contains("glass_pane") || s.contains("barrier") || s.contains("fire") || s.contains("cactus")) return false;
        return !(n.contains("_slab[") && n.contains("type=bottom")) || true;
    }

    /** Space kept free for the shoe lockers placed at runtime. */
    static boolean reserved(int x, int y, int z) {
        return y >= 25 && y <= 27 && x >= -608 && x <= -602 && (z == -85 || z == -79);
    }

    static boolean stand(int x, int y, int z) {
        if (!in(x, y, z) || y - 1 < Y1 || reserved(x, y, z)) return false;
        int a = id(x, y, z), b = id(x, y + 1, z), f = id(x, y - 1, z);
        return (pass[a] || door[a]) && (pass[b] || door[b]) && floor[f];
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 4096)) << 32 | ((long) (y + 64)) << 20 | (z + 4096);
    }

    static Cell cell(long k) {
        return new Cell((int) (k >> 32) - 4096, (int) ((k >> 20) & 0xFFF) - 64, (int) (k & 0xFFFFF) - 4096);
    }

    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Cells you can step to from c: flat, up one, or down one. */
    static List<Cell> steps(Cell c) {
        List<Cell> out = new ArrayList<>(6);
        for (int[] d : DIRS) {
            int x = c.x + d[0], z = c.z + d[1];
            for (int dy : new int[]{0, 1, -1}) {
                if (stand(x, c.y + dy, z)) {
                    out.add(new Cell(x, c.y + dy, z));
                    break;
                }
            }
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new FileInputStream(a[0])))) {
            X1 = d.readInt(); Z1 = d.readInt(); X2 = d.readInt(); Z2 = d.readInt(); Y1 = d.readInt(); Y2 = d.readInt();
            names = new String[d.readInt()];
            for (int i = 0; i < names.length; i++) names[i] = d.readUTF();
            W = X2 - X1 + 1; H = Z2 - Z1 + 1; L = Y2 - Y1 + 1;
            grid = new short[L][H][W];
            for (short[][] layer : grid) for (short[] row : layer) for (int i = 0; i < W; i++) row[i] = d.readShort();
        }
        pass = new boolean[names.length]; floor = new boolean[names.length]; door = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            pass[i] = noCollision(names[i]);
            floor[i] = goodFloor(names[i]);
            door[i] = woodDoor(names[i]);
        }
        int sx = Integer.parseInt(a[2]), sz = Integer.parseInt(a[3]), sy = -1;
        search:
        for (int r = 0; r < 30; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int y = 15; y < 45; y++)
                        if (stand(sx + dx, y, sz + dz)) {
                            sx += dx;
                            sz += dz;
                            sy = y;
                            break search;
                        }
        System.out.println("seed " + sx + " " + sy + " " + sz);

        // Flood from the seed.
        Set<Long> reached = new HashSet<>();
        ArrayDeque<Cell> q = new ArrayDeque<>();
        List<Cell> order = new ArrayList<>();
        q.add(new Cell(sx, sy, sz));
        reached.add(key(sx, sy, sz));
        while (!q.isEmpty()) {
            Cell c = q.poll();
            order.add(c);
            for (Cell n : steps(c)) if (reached.add(key(n.x, n.y, n.z))) q.add(n);
        }
        System.out.println("walkable cells reached: " + order.size());

        // Waypoints: thin the cells out, doors always included.
        int spacing = 5;
        List<Cell> nodes = new ArrayList<>();
        Map<Long, Integer> byBucket = new HashMap<>();
        Map<Long, Integer> nodeAt = new HashMap<>();
        for (Cell c : order) {
            boolean isDoor = door[id(c.x, c.y, c.z)];
            long b = key(Math.floorDiv(c.x, spacing), Math.floorDiv(c.y, 3), Math.floorDiv(c.z, spacing));
            if (isDoor || !byBucket.containsKey(b)) {
                byBucket.putIfAbsent(b, nodes.size());
                nodeAt.put(key(c.x, c.y, c.z), nodes.size());
                nodes.add(c);
            }
        }
        System.out.println("nodes: " + nodes.size());

        // Link nodes that are close on foot.
        List<Set<Integer>> links = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) links.add(new HashSet<>());
        for (int i = 0; i < nodes.size(); i++) {
            Map<Long, Integer> dist = new HashMap<>();
            ArrayDeque<Cell> bq = new ArrayDeque<>();
            Cell s = nodes.get(i);
            dist.put(key(s.x, s.y, s.z), 0);
            bq.add(s);
            while (!bq.isEmpty()) {
                Cell c = bq.poll();
                int dd = dist.get(key(c.x, c.y, c.z));
                if (dd >= 9) continue;
                for (Cell n : steps(c)) {
                    long k = key(n.x, n.y, n.z);
                    if (dist.containsKey(k)) continue;
                    dist.put(k, dd + 1);
                    Integer other = nodeAt.get(k);
                    if (other != null) {
                        if (other != i) { links.get(i).add(other); links.get(other).add(i); }
                        continue; // don't search through another waypoint
                    }
                    bq.add(n);
                }
            }
        }

        // Bedrooms.
        List<String> homes = new ArrayList<>();
        Set<Long> usedRooms = new HashSet<>();
        int beds = 0, unreachable = 0, shared = 0;
        for (int y = Y1; y < Y2; y++)
            for (int z = Z1; z <= Z2; z++)
                for (int x = X1; x <= X2; x++) {
                    String n = name(x, y, z);
                    if (!n.contains("_bed[") || !n.contains("part=head")) continue;
                    beds++;
                    String f = n.replaceAll(".*facing=", "").replaceAll("[,\\]].*", "");
                    int dx = f.equals("east") ? 1 : f.equals("west") ? -1 : 0, dz = f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
                    int footX = x - dx, footZ = z - dz;
                    // Room: walkable cells beside the bed, flooded on this floor, stopping at doors.
                    Set<Long> room = new HashSet<>();
                    ArrayDeque<Cell> rq = new ArrayDeque<>();
                    for (int[] p : new int[][]{{x, z}, {footX, z == footZ ? z : footZ}})
                        for (int[] d : DIRS)
                            if (stand(p[0] + d[0], y, p[1] + d[1]) && reached.contains(key(p[0] + d[0], y, p[1] + d[1]))) {
                                if (room.add(key(p[0] + d[0], y, p[1] + d[1]))) rq.add(new Cell(p[0] + d[0], y, p[1] + d[1]));
                            }
                    if (room.isEmpty()) { unreachable++; continue; }
                    boolean sealed = true;
                    while (!rq.isEmpty() && room.size() < 260) {
                        Cell c = rq.poll();
                        for (Cell m : steps(c)) {
                            if (door[id(m.x, m.y, m.z)]) continue;
                            if (room.add(key(m.x, m.y, m.z))) rq.add(m);
                        }
                    }
                    if (room.size() >= 260) sealed = false;
                    if (!sealed) { unreachable++; continue; }
                    long tag = Collections.min(room);
                    if (!usedRooms.add(tag)) { shared++; continue; }
                    int minX = 99999, maxX = -99999, minZ = 99999, maxZ = -99999;
                    for (long k : room) {
                        Cell c = cell(k);
                        minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
                        minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
                    }
                    // A spot in the room that's on the waypoint network.
                    int inside = -1;
                    for (long k : room) if (nodeAt.containsKey(k)) { inside = nodeAt.get(k); break; }
                    if (inside < 0) {
                        Cell any = cell(room.iterator().next());
                        nodeAt.put(key(any.x, any.y, any.z), nodes.size());
                        inside = nodes.size();
                        nodes.add(any);
                        links.add(new HashSet<>());
                        for (int i = 0; i < nodes.size() - 1; i++) {
                            Cell c = nodes.get(i);
                            if (c.y == any.y && Math.abs(c.x - any.x) + Math.abs(c.z - any.z) <= 8 && room.contains(key(c.x, c.y, c.z))) {
                                links.get(inside).add(i);
                                links.get(i).add(inside);
                            }
                        }
                    }
                    homes.add(x + " " + y + " " + z + " " + footX + " " + footZ + " " + minX + " " + minZ + " " + maxX + " " + maxZ + " " + inside);
                }
        System.out.println("beds " + beds + ", separate bedrooms " + homes.size() + ", same room " + shared + ", cut off/too open " + unreachable);

        // Pieces of the network.
        boolean[] seen = new boolean[nodes.size()];
        List<List<Integer>> pieces = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (seen[i]) continue;
            List<Integer> c = new ArrayList<>();
            ArrayDeque<Integer> iq = new ArrayDeque<>(List.of(i));
            seen[i] = true;
            while (!iq.isEmpty()) {
                int u = iq.poll();
                c.add(u);
                for (int v : links.get(u)) if (!seen[v]) { seen[v] = true; iq.add(v); }
            }
            pieces.add(c);
        }
        pieces.sort((p, r) -> r.size() - p.size());
        System.out.println("network pieces: " + pieces.size() + ", largest " + pieces.get(0).size());
        Set<Integer> main = new HashSet<>(pieces.get(0));

        try (PrintWriter pw = new PrintWriter(a[1])) {
            pw.println("# Generated by tools/MapNav.java from the Chinetsu Town map.");
            int[] remap = new int[nodes.size()];
            Arrays.fill(remap, -1);
            int n = 0;
            for (int i = 0; i < nodes.size(); i++) {
                if (!main.contains(i)) continue;
                remap[i] = n++;
                Cell c = nodes.get(i);
                String ground = name(c.x, c.y - 1, c.z);
                int soft = ground.contains("grass_block") || ground.contains("dirt") ? 1 : 0;
                pw.println("NODE " + c.x + " " + c.y + " " + c.z + " " + soft);
            }
            for (int i = 0; i < nodes.size(); i++)
                for (int j : links.get(i))
                    if (i < j && remap[i] >= 0 && remap[j] >= 0) pw.println("LINK " + remap[i] + " " + remap[j]);
            int written = 0;
            for (String h : homes) {
                String[] p = h.split(" ");
                int in = remap[Integer.parseInt(p[9])];
                if (in < 0) continue;
                p[9] = String.valueOf(in);
                pw.println("HOME " + String.join(" ", p));
                written++;
            }
            System.out.println("homes on the main network: " + written);
        }
    }
}
