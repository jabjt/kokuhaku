import java.io.*;
import java.util.*;

/** Lists blocks stacked around each match of a name. Usage: java MapFind <grid.bin> <name part> */
public class MapFind {
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
        Map<String, Integer> stacks = new TreeMap<>();
        int shown = 0;
        for (int y = 0; y < grid.length; y++)
            for (int z = 0; z < H; z++)
                for (int x = 0; x < W; x++) {
                    if (!names[grid[y][z][x]].contains(a[1])) continue;
                    StringBuilder sb = new StringBuilder();
                    for (int dy = -2; dy <= 2; dy++) {
                        int yy = y + dy;
                        sb.append(yy < 0 || yy >= grid.length ? "-" : names[grid[yy][z][x]].replace("minecraft:", "").replaceAll("\\[.*", "")).append(" | ");
                    }
                    stacks.merge(sb.toString(), 1, Integer::sum);
                    if (shown++ < 8) System.out.println((x + X1) + " " + (y + Y1) + " " + (z + Z1) + " " + names[grid[y][z][x]]);
                }
        stacks.forEach((k, v) -> System.out.println(v + "x  " + k));
    }
}
