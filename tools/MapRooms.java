import java.io.*;
import java.util.*;

/** Splits a box of the map into rooms at doors and lists each one's size and furniture.
 *  Usage: java MapRooms <grid.bin> x1 z1 x2 z2 */
public class MapRooms {
    public static void main(String[] a) throws Exception {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new FileInputStream(a[0])))) {
            MapNav.X1 = d.readInt(); MapNav.Z1 = d.readInt(); MapNav.X2 = d.readInt(); MapNav.Z2 = d.readInt(); MapNav.Y1 = d.readInt(); MapNav.Y2 = d.readInt();
            MapNav.names = new String[d.readInt()];
            for (int i = 0; i < MapNav.names.length; i++) MapNav.names[i] = d.readUTF();
            MapNav.W = MapNav.X2 - MapNav.X1 + 1; MapNav.H = MapNav.Z2 - MapNav.Z1 + 1; MapNav.L = MapNav.Y2 - MapNav.Y1 + 1;
            MapNav.grid = new short[MapNav.L][MapNav.H][MapNav.W];
            for (short[][] layer : MapNav.grid) for (short[] row : layer) for (int i = 0; i < MapNav.W; i++) row[i] = d.readShort();
        }
        int n = MapNav.names.length;
        MapNav.pass = new boolean[n]; MapNav.floor = new boolean[n]; MapNav.door = new boolean[n];
        for (int i = 0; i < n; i++) {
            MapNav.pass[i] = MapNav.noCollision(MapNav.names[i]);
            MapNav.floor[i] = MapNav.goodFloor(MapNav.names[i]);
            MapNav.door[i] = MapNav.woodDoor(MapNav.names[i]);
        }
        int x1 = Integer.parseInt(a[1]), z1 = Integer.parseInt(a[2]), x2 = Integer.parseInt(a[3]), z2 = Integer.parseInt(a[4]);
        if (a.length > 5) {
            // Floor plan: . walkable, D door, h hopper, S stairs, # solid, ' ' air/void.
            for (int i = 5; i < a.length; i++) {
                int y = Integer.parseInt(a[i]);
                System.out.println("== y=" + y + "  x " + x1 + ".." + x2);
                for (int z = z1; z <= z2; z++) {
                    StringBuilder sb = new StringBuilder(String.format("%5d ", z));
                    for (int x = x1; x <= x2; x++) {
                        String s = MapNav.name(x, y, z);
                        char c = MapNav.door[MapNav.id(x, y, z)] ? 'D' : MapNav.stand(x, y, z) ? '.' : s.contains("hopper") ? 'h' : s.contains("stairs") ? 'S'
                                : s.contains("slab") ? 's' : s.contains("fence") ? 'f' : s.contains("glass") ? 'g' : s.contains("trapdoor") ? 't' : s.contains("air") ? ' ' : '#';
                        sb.append(c);
                    }
                    System.out.println(sb);
                }
            }
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (int y = 15; y < 60; y++)
            for (int z = z1; z <= z2; z++)
                for (int x = x1; x <= x2; x++) {
                    if (!MapNav.stand(x, y, z) || seen.contains(MapNav.key(x, y, z)) || MapNav.door[MapNav.id(x, y, z)]) continue;
                    ArrayDeque<MapNav.Cell> q = new ArrayDeque<>();
                    q.add(new MapNav.Cell(x, y, z));
                    seen.add(MapNav.key(x, y, z));
                    int area = 0, mnX = x, mxX = x, mnZ = z, mxZ = z, mnY = y, mxY = y, doors = 0;
                    Map<String, Integer> stuff = new TreeMap<>();
                    while (!q.isEmpty()) {
                        MapNav.Cell c = q.poll();
                        area++;
                        mnX = Math.min(mnX, c.x()); mxX = Math.max(mxX, c.x()); mnZ = Math.min(mnZ, c.z()); mxZ = Math.max(mxZ, c.z());
                        mnY = Math.min(mnY, c.y()); mxY = Math.max(mxY, c.y());
                        for (MapNav.Cell m : MapNav.steps(c)) {
                            if (m.x() < x1 || m.x() > x2 || m.z() < z1 || m.z() > z2) continue;
                            if (MapNav.door[MapNav.id(m.x(), m.y(), m.z())]) { doors++; continue; }
                            if (seen.add(MapNav.key(m.x(), m.y(), m.z()))) q.add(m);
                        }
                    }
                    if (area < 12) continue;
                    for (int yy = mnY; yy <= mxY + 3; yy++)
                        for (int zz = mnZ; zz <= mxZ; zz++)
                            for (int xx = mnX; xx <= mxX; xx++) {
                                String s = MapNav.name(xx, yy, zz).replaceAll("\\[.*", "").replace("minecraft:", "");
                                if (s.equals("air") || s.contains("wool") || s.contains("concrete") || s.equals("glass")) continue;
                                stuff.merge(s, 1, Integer::sum);
                            }
                    List<Map.Entry<String, Integer>> l = new ArrayList<>(stuff.entrySet());
                    l.sort((p, r) -> r.getValue() - p.getValue());
                    System.out.println("x " + mnX + ".." + mxX + " z " + mnZ + ".." + mxZ + " y " + mnY + ".." + mxY + " area " + area + " doors " + doors + " : " + l.subList(0, Math.min(7, l.size())));
                }
    }
}
