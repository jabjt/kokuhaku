import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Loads a box of a world into memory and prints, for tuning the town import:
 *  - block counts per y layer
 *  - character maps of chosen y slices (one char per block type, legend below)
 * Usage: java MapAnalyze <world dir> <out dir> x1 z1 x2 z2 y1 y2 [slice y...]
 */
public class MapAnalyze {
    static int X1, Z1, X2, Z2, Y1, Y2;
    static short[][][] grid; // [y][z][x] -> name id
    static List<String> names = new ArrayList<>(List.of("minecraft:air"));
    static Map<String, Short> ids = new HashMap<>(Map.of("minecraft:air", (short) 0));

    static short id(String n) {
        return ids.computeIfAbsent(n, k -> { names.add(k); return (short) (names.size() - 1); });
    }

    public static void main(String[] a) throws Exception {
        Path world = Paths.get(a[0]), out = Paths.get(a[1]);
        X1 = Integer.parseInt(a[2]); Z1 = Integer.parseInt(a[3]); X2 = Integer.parseInt(a[4]); Z2 = Integer.parseInt(a[5]);
        Y1 = Integer.parseInt(a[6]); Y2 = Integer.parseInt(a[7]);
        Files.createDirectories(out);
        grid = new short[Y2 - Y1 + 1][Z2 - Z1 + 1][X2 - X1 + 1];
        load(world);

        try (PrintWriter pw = new PrintWriter(out.resolve("layers.txt").toFile())) {
            for (int y = Y1; y <= Y2; y++) {
                Map<String, Integer> c = new TreeMap<>();
                for (short[] row : grid[y - Y1]) for (short v : row) if (v != 0) c.merge(names.get(v), 1, Integer::sum);
                List<Map.Entry<String, Integer>> l = new ArrayList<>(c.entrySet());
                l.sort((p, q) -> q.getValue() - p.getValue());
                pw.print("y=" + y + ": ");
                for (int i = 0; i < Math.min(14, l.size()); i++) pw.print(l.get(i).getKey().replace("minecraft:", "") + " " + l.get(i).getValue() + ", ");
                pw.println();
            }
        }
        for (int i = 8; i < a.length; i++) slice(out, Integer.parseInt(a[i]));
        // Also save the raw grid for later tools.
        try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out.resolve("grid.bin").toFile())))) {
            d.writeInt(X1); d.writeInt(Z1); d.writeInt(X2); d.writeInt(Z2); d.writeInt(Y1); d.writeInt(Y2);
            d.writeInt(names.size());
            for (String n : names) d.writeUTF(n);
            for (short[][] layer : grid) for (short[] row : layer) for (short v : row) d.writeShort(v);
        }
        System.out.println("done, " + names.size() + " block types");
    }

    static void slice(Path out, int y) throws IOException {
        short[][] layer = grid[y - Y1];
        Map<Short, Integer> freq = new HashMap<>();
        for (short[] row : layer) for (short v : row) freq.merge(v, 1, Integer::sum);
        List<Short> order = new ArrayList<>(freq.keySet());
        order.sort((p, q) -> freq.get(q) - freq.get(p));
        String chars = ".#=-+o*%@&$xXabcdefghijklmnpqrstuvwyzABCDEFGHIJKLMNOPQRSTUVWYZ0123456789";
        Map<Short, Character> ch = new HashMap<>();
        ch.put((short) 0, ' ');
        int k = 0;
        for (Short s : order) if (s != 0) ch.put(s, k < chars.length() ? chars.charAt(k++) : '?');
        try (PrintWriter pw = new PrintWriter(out.resolve("slice_y" + y + ".txt").toFile())) {
            pw.println("y=" + y + " x " + X1 + ".." + X2 + " z " + Z1 + ".." + Z2 + " (one char per block, rows are z)");
            for (Short s : order) if (s != 0) pw.println(ch.get(s) + " = " + names.get(s).replace("minecraft:", "") + " (" + freq.get(s) + ")");
            pw.println();
            for (int z = 0; z < layer.length; z++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < layer[z].length; x++) sb.append(ch.get(layer[z][x]));
                pw.println(sb);
            }
        }
    }

    // ------------------------------------------------------------ reading
    @SuppressWarnings("unchecked")
    static void load(Path world) throws IOException {
        for (File rf : world.resolve("region").toFile().listFiles((d, n) -> n.endsWith(".mca"))) {
            byte[] data = Files.readAllBytes(rf.toPath());
            if (data.length < 8192) continue;
            for (int i = 0; i < 1024; i++) {
                int off = ((data[i * 4] & 255) << 16 | (data[i * 4 + 1] & 255) << 8 | (data[i * 4 + 2] & 255)) * 4096;
                if (off == 0) continue;
                int len = (data[off] & 255) << 24 | (data[off + 1] & 255) << 16 | (data[off + 2] & 255) << 8 | (data[off + 3] & 255);
                int comp = data[off + 4];
                InputStream raw = new ByteArrayInputStream(data, off + 5, len - 1);
                InputStream in = comp == 2 ? new InflaterInputStream(raw) : comp == 1 ? new GZIPInputStream(raw) : raw;
                Map<String, Object> root = Nbt.readRoot(in);
                List<Map<String, Object>> sections;
                int cx, cz;
                String pal = "palette", dat = "data";
                if (root.containsKey("Level")) {
                    Map<String, Object> lv = (Map<String, Object>) root.get("Level");
                    cx = (Integer) lv.get("xPos"); cz = (Integer) lv.get("zPos");
                    sections = (List<Map<String, Object>>) lv.get("Sections");
                    pal = "Palette"; dat = "BlockStates";
                } else {
                    cx = (Integer) root.get("xPos"); cz = (Integer) root.get("zPos");
                    sections = (List<Map<String, Object>>) root.get("sections");
                }
                if (sections == null || cx * 16 + 15 < X1 || cx * 16 > X2 || cz * 16 + 15 < Z1 || cz * 16 > Z2) continue;
                for (Map<String, Object> sec : sections) {
                    Map<String, Object> bs = pal.equals("palette") ? (Map<String, Object>) sec.get("block_states") : sec;
                    if (bs == null || bs.get(pal) == null) continue;
                    List<Map<String, Object>> palette = (List<Map<String, Object>>) bs.get(pal);
                    long[] arr = (long[]) bs.get(dat);
                    short[] pid = new short[palette.size()];
                    for (int p = 0; p < pid.length; p++) pid[p] = id(Nbt.stateString(palette.get(p)));
                    int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(pid.length - 1));
                    int per = 64 / bits;
                    int sy = ((Number) sec.get("Y")).intValue();
                    for (int idx = 0; idx < 4096; idx++) {
                        int p = 0;
                        if (arr != null && pid.length > 1) p = Nbt.entry(arr, idx, bits);
                        int x = cx * 16 + (idx & 15), z = cz * 16 + ((idx >> 4) & 15), y = sy * 16 + (idx >> 8);
                        if (x < X1 || x > X2 || z < Z1 || z > Z2 || y < Y1 || y > Y2) continue;
                        grid[y - Y1][z - Z1][x - X1] = pid[Math.min(p, pid.length - 1)];
                    }
                }
            }
        }
    }
}
