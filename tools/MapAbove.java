import java.io.*;
import java.util.*;

/** What sits on top of road/pavement cells? Usage: java MapAbove <grid.bin> */
public class MapAbove {
    public static void main(String[] a) throws Exception {
        int X1, Z1, X2, Z2, Y1, Y2, W, H;
        short[][][] grid;
        String[] names;
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new FileInputStream(a[0])))) {
            X1 = d.readInt(); Z1 = d.readInt(); X2 = d.readInt(); Z2 = d.readInt(); Y1 = d.readInt(); Y2 = d.readInt();
            names = new String[d.readInt()];
            for (int i = 0; i < names.length; i++) names[i] = d.readUTF();
            W = X2 - X1 + 1; H = Z2 - Z1 + 1;
            grid = new short[Y2 - Y1 + 1][H][W];
            for (short[][] layer : grid) for (short[] row : layer) for (int i = 0; i < W; i++) row[i] = d.readShort();
        }
        for (String key : List.of("brown_glazed", "cyan_terracotta", "white_glazed", "stone_bricks")) {
            for (int y : new int[]{4, 5, 6}) {
                Map<String, Integer> c = new HashMap<>();
                for (int z = 0; z < H; z++)
                    for (int x = 0; x < W; x++)
                        if (names[grid[3 - Y1][z][x]].contains(key))
                            c.merge(names[grid[y - Y1][z][x]].replace("minecraft:", "").replaceAll("\\[.*", ""), 1, Integer::sum);
                List<Map.Entry<String, Integer>> l = new ArrayList<>(c.entrySet());
                l.sort((p, q) -> q.getValue() - p.getValue());
                System.out.println(key + " y=" + y + ": " + l.subList(0, Math.min(10, l.size())));
            }
        }
    }
}
