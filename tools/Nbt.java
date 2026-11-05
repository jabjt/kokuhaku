import java.io.*;
import java.util.*;

/** Minimal NBT reader/writer for the map tools (no Minecraft classes needed). */
public class Nbt {
    public static Object readTag(DataInputStream in, int type) throws IOException {
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
                List<Object> l = new TagList(t);
                for (int i = 0; i < n; i++) l.add(readTag(in, t));
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
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

    /** A list that remembers its element type, so empty lists write back correctly. */
    public static class TagList extends ArrayList<Object> {
        public final int type;

        public TagList(int type) {
            this.type = type;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readRoot(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
        int type = in.readByte();
        in.readUTF();
        return (Map<String, Object>) readTag(in, type);
    }

    public static int typeOf(Object o) {
        if (o instanceof Byte) return 1;
        if (o instanceof Short) return 2;
        if (o instanceof Integer) return 3;
        if (o instanceof Long) return 4;
        if (o instanceof Float) return 5;
        if (o instanceof Double) return 6;
        if (o instanceof byte[]) return 7;
        if (o instanceof String) return 8;
        if (o instanceof List) return 9;
        if (o instanceof Map) return 10;
        if (o instanceof int[]) return 11;
        if (o instanceof long[]) return 12;
        throw new IllegalArgumentException("not NBT: " + o);
    }

    @SuppressWarnings("unchecked")
    public static void writeTag(DataOutputStream out, Object o) throws IOException {
        switch (typeOf(o)) {
            case 1 -> out.writeByte((Byte) o);
            case 2 -> out.writeShort((Short) o);
            case 3 -> out.writeInt((Integer) o);
            case 4 -> out.writeLong((Long) o);
            case 5 -> out.writeFloat((Float) o);
            case 6 -> out.writeDouble((Double) o);
            case 7 -> { byte[] b = (byte[]) o; out.writeInt(b.length); out.write(b); }
            case 8 -> out.writeUTF((String) o);
            case 9 -> {
                List<Object> l = (List<Object>) o;
                int t = l instanceof TagList tl && l.isEmpty() ? tl.type : l.isEmpty() ? 0 : typeOf(l.get(0));
                out.writeByte(t);
                out.writeInt(l.size());
                for (Object e : l) writeTag(out, e);
            }
            case 10 -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    writeTag(out, e.getValue());
                }
                out.writeByte(0);
            }
            case 11 -> { int[] a = (int[]) o; out.writeInt(a.length); for (int v : a) out.writeInt(v); }
            case 12 -> { long[] a = (long[]) o; out.writeInt(a.length); for (long v : a) out.writeLong(v); }
        }
    }

    public static void writeRoot(OutputStream raw, Map<String, Object> root) throws IOException {
        DataOutputStream out = new DataOutputStream(raw);
        out.writeByte(10);
        out.writeUTF("");
        writeTag(out, root);
        out.flush();
    }

    /** One palette index from a block-state array, in either the modern or the pre-1.16 spanning layout. */
    public static int entry(long[] data, int index, int bits) {
        long mask = (1L << bits) - 1;
        if (data.length * 64 == 4096 * bits) {
            int bit = index * bits, word = bit / 64, offset = bit % 64;
            long v = data[word] >>> offset;
            if (offset + bits > 64) v |= data[word + 1] << (64 - offset);
            return (int) (v & mask);
        }
        int perLong = 64 / bits;
        return (int) ((data[index / perLong] >>> ((index % perLong) * bits)) & mask);
    }

    /** "minecraft:oak_stairs[facing=east,half=bottom]" from a palette entry. */
    @SuppressWarnings("unchecked")
    public static String stateString(Map<String, Object> entry) {
        String name = (String) entry.get("Name");
        Map<String, Object> props = (Map<String, Object>) entry.get("Properties");
        if (props == null || props.isEmpty()) return name;
        StringBuilder sb = new StringBuilder(name).append('[');
        new TreeMap<>(props).forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.setLength(sb.length() - 1);
        return sb.append(']').toString();
    }
}
