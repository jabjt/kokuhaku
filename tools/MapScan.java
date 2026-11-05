import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Reads a world's region files without Minecraft and reports:
 *  - every block used, how often, and which don't exist in 1.20.1
 *  - a top-down image (topmost block colour, shaded by height)
 * Usage: java MapScan <world dir> <minecraft 1.20.1 client jar> <out dir>
 */
public class MapScan {
    // ------------------------------------------------------------ tiny NBT
    static Object readTag(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case 8: return in.readUTF();
            case 9: {
                int t = in.readByte(), n = in.readInt();
                List<Object> l = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) l.add(readTag(in, t));
                return l;
            }
            case 10: {
                Map<String, Object> m = new HashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) return m;
                    String name = in.readUTF();
                    m.put(name, readTag(in, t));
                }
            }
            case 11: { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case 12: { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("bad tag " + type);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readRoot(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
        int type = in.readByte();
        in.readUTF();
        return (Map<String, Object>) readTag(in, type);
    }

    // ------------------------------------------------------------- colours
    static ZipFile jar;
    static Map<String, Integer> colourCache = new HashMap<>();

    static int textureAverage(String path) {
        try {
            ZipEntry e = jar.getEntry(path);
            if (e == null) return -1;
            BufferedImage img = ImageIO.read(jar.getInputStream(e));
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < Math.min(16, img.getHeight()); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int c = img.getRGB(x, y);
                    if ((c >>> 24) < 128) continue;
                    r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; n++;
                }
            return n == 0 ? -1 : (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        } catch (Exception ex) {
            return -1;
        }
    }

    static int colour(String name) {
        return colourCache.computeIfAbsent(name, n -> {
            String id = n.replace("minecraft:", "");
            if (id.contains("leaves")) return id.contains("cherry") ? 0xE8A0C0 : 0x3C7A2C;
            if (id.equals("grass_block") || id.equals("grass") || id.equals("short_grass") || id.equals("tall_grass")) return 0x5C9A3C;
            if (id.contains("water")) return 0x3A5FCD;
            String[] tries = {id + "_top", id, id.replaceAll("_(stairs|slab|wall|fence|fence_gate|pane|carpet|button|pressure_plate|trapdoor|door)$", ""),
                    id.replaceAll("_(stairs|slab)$", "") + "s", id.replace("_wall", "").replace("_slab", "").replace("_stairs", "") + "_top",
                    id.replace("waxed_", ""), id.replace("_block", "")};
            for (String t : tries) {
                int c = textureAverage("assets/minecraft/textures/block/" + t + ".png");
                if (c >= 0) return c;
            }
            return 0xFF00FF; // unknown (probably newer than 1.20.1)
        });
    }

    static Map<Integer, Integer> versionCounts = new TreeMap<>();
    static int legacyNumeric = 0;

    /**
     * Converts a 1.13-1.17 "Level" chunk into the modern shape this scanner reads:
     * xPos/zPos, and sections with block_states {palette, data}. In 1.16+ entries
     * don't span longs (same as modern); earlier ones do, which we approximate.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> legacy(Map<String, Object> level) {
        Map<String, Object> out = new HashMap<>();
        out.put("xPos", level.get("xPos"));
        out.put("zPos", level.get("zPos"));
        List<Map<String, Object>> secs = new ArrayList<>();
        List<Map<String, Object>> old = (List<Map<String, Object>>) level.get("Sections");
        if (old == null) return out;
        for (Map<String, Object> s : old) {
            if (!s.containsKey("Palette")) {
                if (s.containsKey("Blocks")) legacyNumeric++;
                continue;
            }
            Map<String, Object> bs = new HashMap<>();
            bs.put("palette", s.get("Palette"));
            bs.put("data", s.get("BlockStates"));
            Map<String, Object> ns = new HashMap<>();
            ns.put("Y", ((Number) s.get("Y")).intValue());
            ns.put("block_states", bs);
            secs.add(ns);
        }
        out.put("sections", secs);
        return out;
    }

    // ----------------------------------------------------------------- main
    public static void main(String[] args) throws Exception {
        Path world = Paths.get(args[0]);
        jar = new ZipFile(args[1]);
        Path out = Paths.get(args[2]);
        Files.createDirectories(out);

        Set<String> known = new HashSet<>();
        for (Enumeration<? extends ZipEntry> en = jar.entries(); en.hasMoreElements(); ) {
            String n = en.nextElement().getName();
            if (n.startsWith("assets/minecraft/blockstates/") && n.endsWith(".json"))
                known.add("minecraft:" + n.substring(29, n.length() - 5));
        }

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Map<Long, int[]> columns = new HashMap<>(); // key x,z -> {colour, y}
        Map<String, Long> counts = new TreeMap<>();
        int chunks = 0;
        Set<Integer> dataVersions = new TreeSet<>();
        int minBuilt = Integer.MAX_VALUE, maxBuilt = Integer.MIN_VALUE;

        File[] regions = world.resolve("region").toFile().listFiles((d, n) -> n.endsWith(".mca"));
        for (File rf : regions) {
            byte[] data = Files.readAllBytes(rf.toPath());
            if (data.length < 8192) continue;
            for (int i = 0; i < 1024; i++) {
                int off = ((data[i * 4] & 255) << 16 | (data[i * 4 + 1] & 255) << 8 | (data[i * 4 + 2] & 255)) * 4096;
                if (off == 0) continue;
                int len = (data[off] & 255) << 24 | (data[off + 1] & 255) << 16 | (data[off + 2] & 255) << 8 | (data[off + 3] & 255);
                int comp = data[off + 4];
                InputStream raw = new ByteArrayInputStream(data, off + 5, len - 1);
                InputStream in = comp == 2 ? new InflaterInputStream(raw) : comp == 1 ? new GZIPInputStream(raw) : raw;
                if (comp != 1 && comp != 2 && comp != 3) { System.out.println("compression " + comp + " in " + rf.getName()); continue; }
                Map<String, Object> root = readRoot(in);
                versionCounts.merge((Integer) root.getOrDefault("DataVersion", -1), 1, Integer::sum);
                if (root.containsKey("Level")) {
                    @SuppressWarnings("unchecked") Map<String, Object> level = (Map<String, Object>) root.get("Level");
                    root = legacy(level);
                    if (root == null) continue;
                }
                chunks++;
                dataVersions.add((Integer) root.getOrDefault("DataVersion", -1));
                int cx = (Integer) root.get("xPos"), cz = (Integer) root.get("zPos");
                @SuppressWarnings("unchecked") List<Map<String, Object>> sections = (List<Map<String, Object>>) root.get("sections");
                if (sections == null) continue;
                sections.sort(Comparator.comparingInt(s -> -((Number) s.get("Y")).intValue()));
                int[][] topY = new int[16][16];
                for (int[] row : topY) Arrays.fill(row, Integer.MIN_VALUE);
                int[][] topC = new int[16][16];
                boolean any = false;
                for (Map<String, Object> sec : sections) {
                    int sy = ((Number) sec.get("Y")).intValue();
                    @SuppressWarnings("unchecked") Map<String, Object> bs = (Map<String, Object>) sec.get("block_states");
                    if (bs == null) continue;
                    @SuppressWarnings("unchecked") List<Map<String, Object>> palette = (List<Map<String, Object>>) bs.get("palette");
                    long[] arr = (long[]) bs.get("data");
                    String[] names = new String[palette.size()];
                    for (int p = 0; p < names.length; p++) names[p] = (String) palette.get(p).get("Name");
                    int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(names.length - 1));
                    int perLong = 64 / bits;
                    for (int idx = 0; idx < 4096; idx++) {
                        int pi = 0;
                        if (arr != null && names.length > 1) {
                            pi = Nbt.entry(arr, idx, bits);
                        }
                        String n = names[Math.min(pi, names.length - 1)];
                        if (n.endsWith(":air") || n.endsWith("cave_air") || n.endsWith("void_air")) continue;
                        counts.merge(n, 1L, Long::sum);
                        int x = idx & 15, z = (idx >> 4) & 15, y = sy * 16 + (idx >> 8);
                        if (y > topY[z][x]) {
                            topY[z][x] = y;
                            topC[z][x] = colour(n);
                        }
                        any = true;
                        if (y > -61 && !n.contains("grass") && !n.contains("dirt")) {
                            minBuilt = Math.min(minBuilt, y);
                            maxBuilt = Math.max(maxBuilt, y);
                        }
                    }
                }
                if (!any) continue;
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        if (topY[z][x] == Integer.MIN_VALUE) continue;
                        int wx = cx * 16 + x, wz = cz * 16 + z;
                        columns.put(((long) wx << 32) | (wz & 0xFFFFFFFFL), new int[]{topC[z][x], topY[z][x]});
                        minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
                        minZ = Math.min(minZ, wz); maxZ = Math.max(maxZ, wz);
                    }
            }
        }

        System.out.println("Chunks: " + chunks + "  DataVersion counts: " + versionCounts + "  legacy-without-palette: " + legacyNumeric);
        System.out.println("Bounds x " + minX + ".." + maxX + "  z " + minZ + ".." + maxZ + "  built y " + minBuilt + ".." + maxBuilt);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long unknownTotal = 0;
        StringBuilder unknown = new StringBuilder();
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        try (PrintWriter pw = new PrintWriter(out.resolve("blocks.txt").toFile())) {
            for (Map.Entry<String, Long> e : sorted) {
                boolean ok = known.contains(e.getKey());
                pw.println((ok ? "  " : "! ") + e.getKey() + " " + e.getValue());
                if (!ok) { unknownTotal += e.getValue(); unknown.append(e.getKey()).append(" ").append(e.getValue()).append('\n'); }
            }
        }
        System.out.println("Distinct blocks: " + counts.size() + ", total placed: " + total);
        System.out.println("Not in 1.20.1: " + unknownTotal + " blocks (" + String.format("%.2f", 100.0 * unknownTotal / total) + "%)");
        System.out.print(unknown);

        int w = maxX - minX + 1, h = maxZ - minZ + 1;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (Map.Entry<Long, int[]> e : columns.entrySet()) {
            int x = (int) (e.getKey() >> 32) - minX, z = (int) (long) e.getKey().intValue() - minZ;
            int c = e.getValue()[0], y = e.getValue()[1];
            double shade = Math.max(0.55, Math.min(1.35, 0.8 + (y + 60) / 80.0));
            int r = (int) Math.min(255, (c >> 16 & 255) * shade), g = (int) Math.min(255, (c >> 8 & 255) * shade), b = (int) Math.min(255, (c & 255) * shade);
            img.setRGB(x, z, r << 16 | g << 8 | b);
        }
        ImageIO.write(img, "png", out.resolve("map_full.png").toFile());
        int scale = Math.max(1, Math.max(w, h) / 1400);
        BufferedImage small = new BufferedImage(w / scale, h / scale, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = small.createGraphics();
        g2.drawImage(img, 0, 0, w / scale, h / scale, null);
        ImageIO.write(small, "png", out.resolve("map_small.png").toFile());
        System.out.println("Image " + w + "x" + h + " (small 1/" + scale + "), origin " + minX + "," + minZ);
    }
}
