import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Works out the playable town from the imported map (grid.bin from MapAnalyze):
 * house outlines, a street-facing door for each, and pavement waypoints.
 * Writes data/kokuhaku/town.txt, which the mod reads at runtime.
 * Usage: java MapTown <grid.bin> <town.txt>
 */
public class MapTown {
    static int X1, Z1, X2, Z2, Y1, Y2, W, H;
    static short[][][] grid;
    static String[] names;
    static boolean[][] building, walk, pavement, road;

    record House(int x1, int z1, int x2, int z2) {}
    record Door(House h, int x, int z, int dx, int dz, int len, int endX, int endZ) {}

    static String at(int x, int y, int z) {
        return names[grid[y - Y1][z - Z1][x - X1]];
    }

    static boolean inside(int x, int z) {
        return x >= X1 && x <= X2 && z >= Z1 && z <= Z2;
    }

    static boolean solidWall(String n) {
        return n.equals("minecraft:diorite") || n.equals("minecraft:white_terracotta") || n.equals("minecraft:white_concrete")
                || n.equals("minecraft:granite") || n.equals("minecraft:stone_bricks");
    }

    static boolean passable(String n) {
        return n.equals("minecraft:air") || n.contains("_button") || n.contains("pressure_plate") || n.contains("tripwire")
                || n.contains("coral_fan") || n.contains("coral_wall_fan") || n.contains("sign");
    }

    public static void main(String[] a) throws Exception {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new FileInputStream(a[0])))) {
            X1 = d.readInt(); Z1 = d.readInt(); X2 = d.readInt(); Z2 = d.readInt(); Y1 = d.readInt(); Y2 = d.readInt();
            names = new String[d.readInt()];
            for (int i = 0; i < names.length; i++) names[i] = d.readUTF();
            W = X2 - X1 + 1; H = Z2 - Z1 + 1;
            grid = new short[Y2 - Y1 + 1][H][W];
            for (short[][] layer : grid) for (short[] row : layer) for (int i = 0; i < W; i++) row[i] = d.readShort();
        }
        building = new boolean[H][W]; walk = new boolean[H][W]; pavement = new boolean[H][W]; road = new boolean[H][W];
        for (int z = Z1; z <= Z2; z++)
            for (int x = X1; x <= X2; x++) {
                int zi = z - Z1, xi = x - X1;
                building[zi][xi] = solidWall(at(x, 6, z)) && solidWall(at(x, 5, z));
                String ground = at(x, 3, z);
                boolean clear = passable(at(x, 4, z)) && passable(at(x, 5, z));
                walk[zi][xi] = !ground.equals("minecraft:air") && clear;
                pavement[zi][xi] = walk[zi][xi] && ground.contains("brown_glazed_terracotta");
                road[zi][xi] = walk[zi][xi] && (ground.contains("cyan_terracotta") || ground.contains("white_glazed_terracotta"));
            }

        List<House> houses = findHouses();
        System.out.println("houses found: " + houses.size());
        List<Door> doors = new ArrayList<>();
        for (House h : houses) {
            Door best = null;
            for (Door d : doorOptions(h)) if (best == null || d.len < best.len) best = d;
            if (best != null) doors.add(best);
        }
        System.out.println("with a usable door: " + doors.size());

        // Four neighbourhoods of equal size: split at the median x, then the median z.
        doors.sort(Comparator.comparingInt(d -> d.x));
        List<Door> west = new ArrayList<>(doors.subList(0, doors.size() / 2)), east = new ArrayList<>(doors.subList(doors.size() / 2, doors.size()));
        List<List<Door>> groups = new ArrayList<>();
        for (List<Door> side : List.of(west, east)) {
            side.sort(Comparator.comparingInt(d -> d.z));
            groups.add(new ArrayList<>(side.subList(0, side.size() / 2)));
            groups.add(new ArrayList<>(side.subList(side.size() / 2, side.size())));
        }
        String[] district = {"SHRINE", "RIVERSIDE", "NORTH_HILL", "STATION"}; // NW, SW, NE, SE

        // Waypoints: pavement first, spaced out; road cells only join what pavement can't.
        // These are narrow alleys: the kerb strip is the side of the street where it's free,
        // and the road takes over where garden walls stand on the kerb.
        List<int[]> nodes = new ArrayList<>();
        addNodes(nodes, pavement, 4);
        addNodes(nodes, road, 4);
        List<Set<Integer>> links = linkAll(nodes);
        System.out.println("nodes: " + nodes.size() + ", components " + components(nodes, links).size());
        report(nodes, links);
        // Bridge separate pieces across the shortest gap that doesn't cross a building,
        // clearing small obstacles (walls, fences, gates) along the way.
        while (true) {
            List<List<Integer>> pieces = components(nodes, links);
            pieces.sort((p, q) -> q.size() - p.size());
            if (pieces.size() == 1) break;
            Set<Integer> core = new HashSet<>(pieces.get(0));
            int bestA = -1, bestB = -1;
            double bestD = 24;
            for (int i = 0; i < nodes.size(); i++) {
                if (core.contains(i)) continue;
                for (int j : core) {
                    double d = Math.hypot(nodes.get(i)[0] - nodes.get(j)[0], nodes.get(i)[1] - nodes.get(j)[1]);
                    if (d < bestD && freeOfBuildings(nodes.get(i), nodes.get(j))) {
                        bestD = d;
                        bestA = i;
                        bestB = j;
                    }
                }
            }
            if (bestA < 0) break;
            links.get(bestA).add(bestB);
            links.get(bestB).add(bestA);
            clearCells(nodes.get(bestA), nodes.get(bestB));
        }
        report(nodes, links);
        List<List<Integer>> cs = components(nodes, links);
        cs.sort((p, q) -> q.size() - p.size());
        Set<Integer> main = new HashSet<>(cs.get(0));
        System.out.println("main component: " + main.size() + " of " + nodes.size());

        try (PrintWriter pw = new PrintWriter(a[1])) {
            pw.println("# Generated by tools/MapTown.java from the Japan map. All at standing level y=4.");
            int[] remap = new int[nodes.size()];
            Arrays.fill(remap, -1);
            int n = 0;
            for (int i = 0; i < nodes.size(); i++) {
                if (!main.contains(i)) continue;
                remap[i] = n++;
                pw.println("NODE " + nodes.get(i)[0] + " " + nodes.get(i)[1]);
            }
            for (int i = 0; i < nodes.size(); i++)
                for (int j : links.get(i))
                    if (i < j && remap[i] >= 0 && remap[j] >= 0) pw.println("LINK " + remap[i] + " " + remap[j]);
            for (int g = 0; g < 4; g++) {
                List<Door> list = groups.get(g);
                list.sort(Comparator.comparingInt(d -> d.len));
                System.out.println(district[g] + ": " + list.size() + " houses");
                int taken = 0;
                for (Door d : list) {
                    if (taken == 12) break;
                    int end = nearestLinked(nodes, main, remap, d.endX, d.endZ);
                    if (end < 0) continue;
                    House h = d.h;
                    pw.println("HOUSE " + district[g] + " " + h.x1 + " " + h.z1 + " " + h.x2 + " " + h.z2 + " "
                            + d.x + " " + d.z + " " + d.dx + " " + d.dz + " " + d.len + " " + end);
                    taken++;
                }
                if (taken < 12) System.out.println("  ONLY " + taken + " usable!");
            }
            // Hangouts: nearest node to the station plaza, and the northernmost point.
            pw.println("PLACE station " + nearestLinked(nodes, main, remap, -190, -60));
            int north = -1;
            for (int i : main) if (north < 0 || nodes.get(i)[1] < nodes.get(north)[1]) north = i;
            pw.println("PLACE lookout " + remap[north]);
            pw.println("PLACE townlink " + nearestLinked(nodes, main, remap, -150, -60));
            for (long k : clear) pw.println("CLEAR " + ((k >> 20) - 100000) + " " + ((k & 0xFFFFF) - 100000));
            System.out.println("cells to clear: " + clear.size());
        }
        // Is the school site clear? (only what the box covers)
        int blocked = 0;
        for (int x = Math.max(X1, -110); x <= X2; x++)
            for (int z = -65; z <= -21; z++) if (!walk[z - Z1][x - X1] && inside(x, z)) blocked++;
        System.out.println("non-walkable cells at the school/link site within the box: " + blocked);
    }

    /** The biggest pieces of the network, with where they are. */
    static void report(List<int[]> nodes, List<Set<Integer>> links) {
        List<List<Integer>> cs = components(nodes, links);
        cs.sort((p, q) -> q.size() - p.size());
        for (int i = 0; i < Math.min(12, cs.size()); i++) {
            int a = 9999, b = -9999, c = 9999, d = -9999;
            for (int n : cs.get(i)) {
                a = Math.min(a, nodes.get(n)[0]); b = Math.max(b, nodes.get(n)[0]);
                c = Math.min(c, nodes.get(n)[1]); d = Math.max(d, nodes.get(n)[1]);
            }
            System.out.println("  piece " + cs.get(i).size() + " nodes, x " + a + ".." + b + " z " + c + ".." + d);
        }
    }

    static List<House> findHouses() {
        boolean[][] seen = new boolean[H][W];
        List<House> out = new ArrayList<>();
        int[] hist = new int[4];
        for (int zi = 0; zi < H; zi++)
            for (int xi = 0; xi < W; xi++) {
                if (!building[zi][xi] || seen[zi][xi]) continue;
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{xi, zi});
                seen[zi][xi] = true;
                boolean[][] piece = new boolean[H][W];
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    piece[c[1]][c[0]] = core(c[0] + X1, c[1] + Z1);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + d[0], nz = c[1] + d[1];
                        if (nx >= 0 && nz >= 0 && nx < W && nz < H && building[nz][nx] && !seen[nz][nx]) {
                            seen[nz][nx] = true;
                            q.add(new int[]{nx, nz});
                        }
                    }
                }
                // Houses can be L-shaped: use the biggest solid rectangle, so a carved
                // room always keeps a full one-block shell around it.
                int[] r = biggestRectangle(piece);
                if (r == null) { hist[0]++; continue; }
                int w = r[2] - r[0] + 1, h = r[3] - r[1] + 1;
                if (Math.min(w, h) < 6) { hist[0]++; continue; }
                if (Math.max(w, h) > 20) { hist[1]++; continue; }
                hist[3]++;
                out.add(new House(r[0] + X1, r[1] + Z1, r[2] + X1, r[3] + Z1));
            }
        System.out.println("rejected small " + hist[0] + ", big " + hist[1]);
        return out;
    }

    /** Solid enough to be a house wall at every height of a room. */
    static boolean core(int x, int z) {
        return !at(x, 4, z).equals("minecraft:air") && !at(x, 7, z).equals("minecraft:air") && !passable(at(x, 4, z))
                && !passable(at(x, 7, z)) && !at(x, 4, z).contains("fence") && !at(x, 4, z).contains("_wall");
    }

    /** Largest all-true rectangle, as {x1, z1, x2, z2}, or null. */
    static int[] biggestRectangle(boolean[][] cells) {
        int[] heights = new int[W];
        int[] best = null;
        int bestArea = 0;
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) heights[x] = cells[z][x] ? heights[x] + 1 : 0;
            for (int x = 0; x < W; x++) {
                int minH = Integer.MAX_VALUE;
                for (int e = x; e < W && heights[e] > 0; e++) {
                    minH = Math.min(minH, heights[e]);
                    int side = Math.min(minH, e - x + 1);
                    int area = minH * (e - x + 1);
                    // Favour rooms that aren't corridors.
                    if (side >= 6 && area > bestArea) {
                        bestArea = area;
                        best = new int[]{x, z - minH + 1, e, z};
                    }
                }
            }
        }
        return best;
    }

    static List<Door> doorOptions(House h) {
        List<Door> out = new ArrayList<>();
        // Any spot along a wall, at least two blocks in from the corners.
        List<int[]> sides = new ArrayList<>();
        for (int x = h.x1 + 2; x <= h.x2 - 2; x++) {
            sides.add(new int[]{x, h.z1, 0, -1});
            sides.add(new int[]{x, h.z2, 0, 1});
        }
        for (int z = h.z1 + 2; z <= h.z2 - 2; z++) {
            sides.add(new int[]{h.x1, z, -1, 0});
            sides.add(new int[]{h.x2, z, 1, 0});
        }
        for (int[] s : sides) {
            int x = s[0], z = s[1], dx = s[2], dz = s[3];
            for (int k = 1; k <= 22; k++) {
                int px = x + dx * k, pz = z + dz * k;
                if (!inside(px, pz) || building[pz - Z1][px - X1] || at(px, 3, pz).equals("minecraft:air")) break;
                if (pavement[pz - Z1][px - X1] || road[pz - Z1][px - X1]) {
                    out.add(new Door(h, x, z, dx, dz, k, px, pz));
                    break;
                }
            }
        }
        return out;
    }

    static void addNodes(List<int[]> nodes, boolean[][] cells, int spacing) {
        for (int zi = 0; zi < H; zi++)
            for (int xi = 0; xi < W; xi++) {
                if (!cells[zi][xi]) continue;
                int x = xi + X1, z = zi + Z1;
                boolean near = false;
                for (int[] n : nodes) {
                    if (Math.abs(n[0] - x) < spacing && Math.abs(n[1] - z) < spacing) { near = true; break; }
                }
                if (!near) nodes.add(new int[]{x, z});
            }
    }

    static boolean clearLine(int[] a, int[] b) {
        int steps = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
        for (int i = 0; i <= steps; i++) {
            int x = a[0] + Math.round((b[0] - a[0]) * i / (float) Math.max(1, steps));
            int z = a[1] + Math.round((b[1] - a[1]) * i / (float) Math.max(1, steps));
            if (!walk[z - Z1][x - X1]) return false;
        }
        return true;
    }

    static List<Set<Integer>> linkAll(List<int[]> nodes) {
        List<Set<Integer>> links = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) links.add(new HashSet<>());
        for (int i = 0; i < nodes.size(); i++)
            for (int j = i + 1; j < nodes.size(); j++) {
                int[] a = nodes.get(i), b = nodes.get(j);
                if (Math.abs(a[0] - b[0]) <= 12 && Math.abs(a[1] - b[1]) <= 12 && clearLine(a, b)) {
                    links.get(i).add(j);
                    links.get(j).add(i);
                }
            }
        return links;
    }

    static List<List<Integer>> components(List<int[]> nodes, List<Set<Integer>> links) {
        boolean[] seen = new boolean[nodes.size()];
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (seen[i]) continue;
            List<Integer> c = new ArrayList<>();
            ArrayDeque<Integer> q = new ArrayDeque<>(List.of(i));
            seen[i] = true;
            while (!q.isEmpty()) {
                int u = q.poll();
                c.add(u);
                for (int v : links.get(u)) if (!seen[v]) { seen[v] = true; q.add(v); }
            }
            out.add(c);
        }
        return out;
    }

    static Set<Long> clear = new TreeSet<>();

    static long key(int x, int z) {
        return ((long) (x + 100000) << 20) | (z + 100000);
    }

    static boolean freeOfBuildings(int[] a, int[] b) {
        int steps = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
        for (int i = 0; i <= steps; i++) {
            int x = a[0] + Math.round((b[0] - a[0]) * i / (float) Math.max(1, steps));
            int z = a[1] + Math.round((b[1] - a[1]) * i / (float) Math.max(1, steps));
            if (!inside(x, z) || building[z - Z1][x - X1] || at(x, 3, z).equals("minecraft:air")) return false;
        }
        return true;
    }

    /** Marks a two-wide strip along the line to be cleared of small obstacles. */
    static void clearCells(int[] a, int[] b) {
        int steps = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
        boolean alongX = Math.abs(a[0] - b[0]) >= Math.abs(a[1] - b[1]);
        for (int i = 0; i <= steps; i++) {
            int x = a[0] + Math.round((b[0] - a[0]) * i / (float) Math.max(1, steps));
            int z = a[1] + Math.round((b[1] - a[1]) * i / (float) Math.max(1, steps));
            for (int w = 0; w < 2; w++) {
                int cx = alongX ? x : x + w, cz = alongX ? z + w : z;
                if (inside(cx, cz) && !building[cz - Z1][cx - X1]) clear.add(key(cx, cz));
            }
        }
    }

    /** Closest main-network node reachable from (x, z) without crossing a building; -1 if none. */
    static int nearestLinked(List<int[]> nodes, Set<Integer> main, int[] remap, int x, int z) {
        int best = -1;
        double bestD = 1e18;
        int[] from = {x, z};
        for (int i : main) {
            int[] n = nodes.get(i);
            double d = Math.hypot(n[0] - x, n[1] - z);
            // A clear line is worth a lot more than a shorter one that needs clearing.
            double score = clearLine(from, n) ? d : d + 6;
            if (score < bestD && d <= 26 && freeOfBuildings(from, n)) { bestD = score; best = i; }
        }
        if (best < 0) return -1;
        if (!clearLine(from, nodes.get(best))) clearCells(from, nodes.get(best));
        return remap[best];
    }
}
