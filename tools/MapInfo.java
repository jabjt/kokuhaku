import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Dumps sign text and bed positions from a pre-1.18 world. Usage: java MapInfo <world dir> */
public class MapInfo {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        List<String> signs = new ArrayList<>(), beds = new ArrayList<>(), other = new ArrayList<>();
        for (File rf : Paths.get(a[0], "region").toFile().listFiles((d, n) -> n.endsWith(".mca"))) {
            byte[] data = Files.readAllBytes(rf.toPath());
            if (data.length < 8192) continue;
            for (int i = 0; i < 1024; i++) {
                int off = ((data[i * 4] & 255) << 16 | (data[i * 4 + 1] & 255) << 8 | (data[i * 4 + 2] & 255)) * 4096;
                if (off == 0) continue;
                int len = (data[off] & 255) << 24 | (data[off + 1] & 255) << 16 | (data[off + 2] & 255) << 8 | (data[off + 3] & 255);
                InputStream raw = new ByteArrayInputStream(data, off + 5, len - 1);
                Map<String, Object> root = Nbt.readRoot(data[off + 4] == 2 ? new InflaterInputStream(raw) : new GZIPInputStream(raw));
                Map<String, Object> lv = (Map<String, Object>) root.get("Level");
                List<Map<String, Object>> tes = (List<Map<String, Object>>) lv.get("TileEntities");
                if (tes == null) continue;
                for (Map<String, Object> te : tes) {
                    String id = String.valueOf(te.get("id"));
                    String pos = te.get("x") + " " + te.get("y") + " " + te.get("z");
                    if (id.contains("sign")) {
                        StringBuilder sb = new StringBuilder();
                        for (int t = 1; t <= 4; t++) {
                            String s = String.valueOf(te.get("Text" + t));
                            s = s.replaceAll(".*\"text\":\"", "").replaceAll("\".*", "");
                            if (!s.isBlank() && !s.equals("null")) sb.append(s).append(" / ");
                        }
                        signs.add(pos + " : " + sb);
                    } else if (id.contains("bed")) {
                        beds.add(pos);
                    } else if (!id.contains("chest")) {
                        other.add(id);
                    }
                }
            }
        }
        Collections.sort(signs);
        System.out.println("SIGNS " + signs.size());
        signs.forEach(System.out::println);
        System.out.println("BEDS " + beds.size());
        Map<String, Integer> clusters = new TreeMap<>();
        for (String b : beds) {
            String[] p = b.split(" ");
            int x = Integer.parseInt(p[0]) >> 5 << 5, z = Integer.parseInt(p[2]) >> 5 << 5;
            clusters.merge("x" + x + " z" + z + " y" + p[1], 1, Integer::sum);
        }
        System.out.println(clusters);
        Map<String, Integer> c = new TreeMap<>();
        other.forEach(o -> c.merge(o, 1, Integer::sum));
        System.out.println(c);
    }
}
