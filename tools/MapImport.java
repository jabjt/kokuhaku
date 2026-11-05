import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Prepares the map's region files for 1.20.1. Chunks last saved by 1.16 are
 * copied untouched (the game upgrades those itself); chunks saved by 1.21.10
 * are relabelled 1.20.1 and lose their block entities, whose format changed.
 * Usage: java MapImport <world dir> <out dir>
 */
public class MapImport {
    static final int TARGET = 3465; // 1.20.1

    public static void main(String[] a) throws Exception {
        File[] regions = Paths.get(a[0], "region").toFile().listFiles((d, n) -> n.endsWith(".mca"));
        Path out = Paths.get(a[1]);
        Files.createDirectories(out);
        int relabelled = 0, kept = 0, dropped = 0;
        for (File rf : regions) {
            byte[] data = Files.readAllBytes(rf.toPath());
            if (data.length < 8192) continue;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] header = new byte[8192];
            System.arraycopy(data, 4096, header, 4096, 4096); // timestamps
            int sector = 2;
            for (int i = 0; i < 1024; i++) {
                int off = ((data[i * 4] & 255) << 16 | (data[i * 4 + 1] & 255) << 8 | (data[i * 4 + 2] & 255)) * 4096;
                if (off == 0) continue;
                int len = (data[off] & 255) << 24 | (data[off + 1] & 255) << 16 | (data[off + 2] & 255) << 8 | (data[off + 3] & 255);
                int comp = data[off + 4];
                InputStream raw = new ByteArrayInputStream(data, off + 5, len - 1);
                InputStream in = comp == 2 ? new InflaterInputStream(raw) : comp == 1 ? new GZIPInputStream(raw) : raw;
                Map<String, Object> root = Nbt.readRoot(in);
                int version = (Integer) root.getOrDefault("DataVersion", 0);
                byte[] payload;
                byte type;
                if (version <= TARGET) {
                    payload = Arrays.copyOfRange(data, off + 5, off + 4 + len);
                    type = (byte) comp;
                    kept++;
                } else {
                    String status = String.valueOf(root.get("Status"));
                    if (!status.endsWith("full")) {
                        dropped++;
                        continue;
                    }
                    root.put("DataVersion", TARGET);
                    root.put("block_entities", new Nbt.TagList(10));
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (DeflaterOutputStream z = new DeflaterOutputStream(bos)) {
                        Nbt.writeRoot(z, root);
                    }
                    payload = bos.toByteArray();
                    type = 2;
                    relabelled++;
                }
                int total = payload.length + 5;
                int sectors = (total + 4095) / 4096;
                header[i * 4] = (byte) (sector >> 16);
                header[i * 4 + 1] = (byte) (sector >> 8);
                header[i * 4 + 2] = (byte) sector;
                header[i * 4 + 3] = (byte) sectors;
                DataOutputStream d = new DataOutputStream(body);
                d.writeInt(payload.length + 1);
                d.writeByte(type);
                d.write(payload);
                d.write(new byte[sectors * 4096 - total]);
                sector += sectors;
            }
            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out.resolve(rf.getName())))) {
                os.write(header);
                body.writeTo(os);
            }
        }
        System.out.println("kept " + kept + ", relabelled " + relabelled + ", dropped " + dropped);
    }
}
