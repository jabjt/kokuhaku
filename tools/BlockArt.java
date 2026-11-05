import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Paints the custom block textures at 64x64, the texture pack's resolution,
 * and writes their blockstate and model JSON. Run from the project root:
 *   java tools/BlockArt.java
 */
public class BlockArt {
    static final int S = 64;
    static final Random R = new Random(1310);
    static final String ROOT = "src/main/resources/assets/kokuhaku/";

    public static void main(String[] a) throws Exception {
        new File(ROOT + "textures/block").mkdirs();
        new File(ROOT + "models/block").mkdirs();
        new File(ROOT + "blockstates").mkdirs();
        for (String old : List.of("plaster", "tatami", "shoji", "chair_wood", "chair_steel")) new File(ROOT + "textures/block/" + old + ".png").delete();
        for (String old : List.of("plaster_panel", "shoji_window", "school_chair", "tatami")) {
            new File(ROOT + "models/block/" + old + ".json").delete();
            new File(ROOT + "blockstates/" + old + ".json").delete();
        }

        save("corkboard", cork());
        save("locker_side", steel(176, 182, 188));
        panel("job_board", "corkboard");
        facingState("job_board");

        // Lockers: one texture and model per number.
        StringBuilder st = new StringBuilder("{\"variants\":{");
        String[][] rot = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
        for (int n = 1; n <= 42; n++) {
            save("locker_" + n, locker(n));
            write(ROOT + "models/block/locker_" + n + ".json", "{\"parent\":\"minecraft:block/orientable\",\"textures\":{\"top\":\"kokuhaku:block/locker_side\",\"side\":\"kokuhaku:block/locker_side\",\"front\":\"kokuhaku:block/locker_" + n + "\"}}");
            for (String[] r : rot) {
                st.append("\"facing=").append(r[0]).append(",number=").append(n).append("\":{\"model\":\"kokuhaku:block/locker_").append(n).append("\",\"y\":").append(r[1]).append("},");
            }
        }
        st.setLength(st.length() - 1);
        write(ROOT + "blockstates/locker.json", st + "}}");

        // Vending machine: two blocks tall.
        save("vending_front_low", vendingLow());
        save("vending_front_high", vendingHigh());
        save("vending_side", steel(196, 32, 38));
        write(ROOT + "models/block/vending_machine.json", "{\"textures\":{\"particle\":\"kokuhaku:block/vending_side\",\"l\":\"kokuhaku:block/vending_front_low\",\"h\":\"kokuhaku:block/vending_front_high\",\"s\":\"kokuhaku:block/vending_side\"},\"elements\":["
                + "{\"from\":[1,0,1],\"to\":[15,16,15],\"faces\":{\"north\":{\"texture\":\"#s\"},\"south\":{\"texture\":\"#l\"},\"east\":{\"texture\":\"#s\"},\"west\":{\"texture\":\"#s\"},\"down\":{\"texture\":\"#s\"}}},"
                + "{\"from\":[1,16,1],\"to\":[15,31,15],\"faces\":{\"north\":{\"texture\":\"#s\"},\"south\":{\"texture\":\"#h\"},\"east\":{\"texture\":\"#s\"},\"west\":{\"texture\":\"#s\"},\"up\":{\"texture\":\"#s\"}}}]}");
        write(ROOT + "blockstates/vending_machine.json", "{\"variants\":{\"\":{\"model\":\"kokuhaku:block/vending_machine\"}}}");

        // Evidence: a dark stain on the floor.
        save("stain", stain());
        write(ROOT + "models/block/evidence.json", "{\"ambientocclusion\":false,\"textures\":{\"particle\":\"kokuhaku:block/stain\",\"t\":\"kokuhaku:block/stain\"},\"elements\":[{\"from\":[0,0.2,0],\"to\":[16,0.2,16],\"faces\":{\"up\":{\"texture\":\"#t\"}}}]}");
        write(ROOT + "blockstates/evidence.json", "{\"variants\":{\"\":{\"model\":\"kokuhaku:block/evidence\"}}}");
    }

    static void write(String path, String s) throws Exception {
        Files.writeString(Paths.get(path), s);
    }

    static void facingState(String n) throws Exception {
        String m = "kokuhaku:block/" + n;
        write(ROOT + "blockstates/" + n + ".json", "{\"variants\":{"
                + "\"facing=north\":{\"model\":\"" + m + "\"},"
                + "\"facing=east\":{\"model\":\"" + m + "\",\"y\":90},"
                + "\"facing=south\":{\"model\":\"" + m + "\",\"y\":180},"
                + "\"facing=west\":{\"model\":\"" + m + "\",\"y\":270}}}");
    }

    static void panel(String n, String tex) throws Exception {
        String f = "{\"texture\":\"#t\"}";
        write(ROOT + "models/block/" + n + ".json", "{\"textures\":{\"particle\":\"kokuhaku:block/" + tex + "\",\"t\":\"kokuhaku:block/" + tex + "\"},\"elements\":["
                + "{\"from\":[0,0,14],\"to\":[16,16,16],\"faces\":{\"north\":" + f + ",\"south\":" + f + ",\"east\":" + f + ",\"west\":" + f + ",\"up\":" + f + ",\"down\":" + f + "}}]}");
    }

    static void save(String n, BufferedImage i) throws Exception {
        ImageIO.write(i, "png", new File(ROOT + "textures/block/" + n + ".png"));
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
    }

    static BufferedImage steel(int r, int g, int b) {
        BufferedImage i = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int n = R.nextInt(7) - 3 + (x % 16 == 0 ? -6 : 0);
                i.setRGB(x, y, rgb(r + n, g + n, b + n));
            }
        return i;
    }

    /** A pale metal door with a vent, handle and number plate. */
    static BufferedImage locker(int number) {
        BufferedImage i = steel(190, 196, 202);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(120, 126, 132));
        g.drawRect(3, 3, 57, 57);
        for (int y = 10; y <= 20; y += 3) g.fillRect(18, y, 28, 1);
        g.setColor(new Color(244, 242, 232));
        g.fillRect(16, 28, 32, 18);
        g.setColor(new Color(90, 90, 96));
        g.drawRect(16, 28, 32, 18);
        g.setColor(new Color(30, 30, 36));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        String s = String.valueOf(number);
        g.drawString(s, 32 - g.getFontMetrics().stringWidth(s) / 2, 43);
        g.setColor(new Color(70, 74, 80));
        g.fillRect(50, 30, 4, 12);
        g.dispose();
        return i;
    }

    static BufferedImage vendingHigh() {
        BufferedImage i = steel(196, 32, 38);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(236, 244, 250));
        g.fillRect(5, 5, 54, 56);
        Color[] cans = {new Color(40, 150, 80), new Color(120, 76, 44), new Color(190, 30, 40), new Color(40, 90, 180), new Color(240, 170, 40)};
        for (int row = 0; row < 3; row++)
            for (int c = 0; c < 6; c++) {
                g.setColor(cans[R.nextInt(cans.length)]);
                g.fillRect(8 + c * 8, 9 + row * 17, 6, 12);
                g.setColor(new Color(200, 204, 210));
                g.fillRect(8 + c * 8, 9 + row * 17, 6, 2);
            }
        g.dispose();
        return i;
    }

    static BufferedImage vendingLow() {
        BufferedImage i = steel(196, 32, 38);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(236, 244, 250));
        g.fillRect(5, 0, 54, 10);
        g.setColor(new Color(40, 40, 46));
        g.fillRect(44, 16, 12, 18);
        g.setColor(new Color(90, 220, 120));
        g.fillRect(46, 18, 8, 4);
        g.setColor(new Color(24, 24, 28));
        g.fillRect(8, 42, 34, 14);
        g.setColor(new Color(60, 60, 66));
        g.fillRect(8, 42, 34, 2);
        g.dispose();
        return i;
    }

    static BufferedImage cork() {
        BufferedImage i = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                boolean frame = x < 4 || y < 4 || x >= S - 4 || y >= S - 4;
                int n = R.nextInt(25) - 12;
                i.setRGB(x, y, frame ? rgb(70, 48, 30) : rgb(176 + n, 132 + n, 84 + n));
            }
        for (int[] p : new int[][]{{10, 12, 20, 24}, {36, 8, 18, 18}, {16, 40, 26, 14}, {46, 34, 12, 20}})
            for (int y = p[1]; y < p[1] + p[3]; y++)
                for (int x = p[0]; x < p[0] + p[2]; x++)
                    i.setRGB(x, y, (y - p[1]) % 5 == 4 ? rgb(150, 150, 150) : rgb(240, 238, 228));
        return i;
    }

    static BufferedImage stain() {
        BufferedImage i = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int b = 0; b < 26; b++) {
            int cx = 14 + R.nextInt(36), cz = 14 + R.nextInt(36), r = 3 + R.nextInt(9);
            for (int y = -r; y <= r; y++)
                for (int x = -r; x <= r; x++)
                    if (x * x + y * y <= r * r && cx + x >= 0 && cx + x < S && cz + y >= 0 && cz + y < S)
                        i.setRGB(cx + x, cz + y, rgb(74 + R.nextInt(14), 8, 10));
        }
        return i;
    }
}
