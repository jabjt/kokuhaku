import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Paints the mod's pixel-art GUI textures. Run from the project root:
 *   java tools/ArtGen.java
 * Writes into src/main/resources/assets/kokuhaku/textures/gui/.
 */
public class ArtGen {
    static final int W = 480, H = 270;
    // One-point perspective: eye sits at the vanishing point, back wall is 200 px wide.
    static final double VX = 240, VY = 83.57, K = 1.4;

    static BufferedImage img, emissive;
    static Graphics2D g, ge;
    static Polygon beam;

    public static void main(String[] args) throws Exception {
        File out = new File("src/main/resources/assets/kokuhaku/textures/gui");
        out.mkdirs();
        // room() is kept for reference; the menu now uses a real in-game room (MenuRoom).
        ImageIO.write(icons(), "png", new File(out, "icons.png"));
        System.out.println("Wrote textures to " + out.getAbsolutePath());
    }

    // ---------------------------------------------------------------- room

    /** Room space: X -1..1 (left..right), Y 0..1 (floor..ceiling), z 0..1 (front..back wall). */
    static int[] p(double x, double y, double z) {
        double fx = 240 + x * 240, fy = 270 - y * 270, s = 1 / (1 + K * z);
        return new int[]{(int) Math.round(VX + (fx - VX) * s), (int) Math.round(VY + (fy - VY) * s)};
    }

    static Polygon poly(int[]... pts) {
        Polygon poly = new Polygon();
        for (int[] q : pts) poly.addPoint(q[0], q[1]);
        return poly;
    }

    static void fill(Polygon poly, int rgb) {
        g.setColor(new Color(rgb));
        g.fillPolygon(poly);
        g.drawPolygon(poly); // closes 1 px seams between faces
    }

    static void glow(Polygon poly) {
        ge.fillPolygon(poly);
        ge.drawPolygon(poly);
    }

    /** Back-wall rectangle (z = 1). */
    static Polygon wallRect(double x0, double x1, double y0, double y1) {
        return poly(p(x0, y0, 1), p(x1, y0, 1), p(x1, y1, 1), p(x0, y1, 1));
    }

    /** Axis-aligned box; draws only the faces the camera (at X = 0, eye height ~0.69) can see. */
    static void box(double x0, double x1, double y0, double y1, double z0, double z1, int top, int front, int side) {
        if (x0 > 0) fill(poly(p(x0, y0, z0), p(x0, y1, z0), p(x0, y1, z1), p(x0, y0, z1)), side);
        if (x1 < 0) fill(poly(p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), p(x1, y0, z1)), side);
        fill(poly(p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0)), front);
        if (y1 < 0.69) fill(poly(p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1)), top);
    }

    static BufferedImage room() {
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        emissive = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        g = img.createGraphics();
        ge = emissive.createGraphics();
        ge.setColor(Color.WHITE);
        Random rnd = new Random(1310);

        // Shell of the room.
        fill(poly(p(-1, 1, 0), p(1, 1, 0), p(1, 1, 1), p(-1, 1, 1)), 0x1C1820);
        fill(poly(p(-1, 0, 1), p(1, 0, 1), p(1, 1, 1), p(-1, 1, 1)), 0x3A3240);
        fill(poly(p(-1, 0, 0), p(-1, 1, 0), p(-1, 1, 1), p(-1, 0, 1)), 0x332C38);
        fill(poly(p(1, 0, 0), p(1, 1, 0), p(1, 1, 1), p(1, 0, 1)), 0x2E2834);
        fill(poly(p(-1, 0, 0), p(1, 0, 0), p(1, 0, 1), p(-1, 0, 1)), 0x4A3222);

        // Faint wallpaper stripes on the back wall.
        for (double x = -0.95; x < 1; x += 0.1) {
            fill(wallRect(x, x + 0.012, 0.04, 1), 0x352E3B);
        }
        // Floor planks converging on the vanishing point, with staggered seams.
        g.setColor(new Color(0x3A2618));
        for (double x = -1; x <= 1.001; x += 0.125) {
            int[] a = p(x, 0, 0), b = p(x, 0, 1);
            g.drawLine(a[0], a[1], b[0], b[1]);
            for (double z = rnd.nextDouble() * 0.3; z < 1; z += 0.3 + rnd.nextDouble() * 0.2) {
                int[] c = p(x, 0, z), d = p(x + 0.125, 0, z);
                g.drawLine(c[0], c[1], d[0], d[1]);
            }
        }
        // Skirting boards.
        fill(poly(p(-1, 0, 1), p(1, 0, 1), p(1, 0.04, 1), p(-1, 0.04, 1)), 0x221C24);
        fill(poly(p(-1, 0, 0), p(-1, 0.04, 0), p(-1, 0.04, 1), p(-1, 0, 1)), 0x221C24);
        fill(poly(p(1, 0, 0), p(1, 0.04, 0), p(1, 0.04, 1), p(1, 0, 1)), 0x221C24);

        // Window with shoji-style grid, moonlit sky and a small moon.
        Polygon win = wallRect(-0.55, -0.05, 0.38, 0.8);
        fill(win, 0x24324A);
        glow(win);
        int[] moon = p(-0.2, 0.7, 1);
        g.setColor(new Color(0xC8D4E0));
        g.fillOval(moon[0] - 5, moon[1] - 5, 10, 10);
        g.setColor(new Color(0x24324A));
        g.fillOval(moon[0] - 2, moon[1] - 6, 10, 10); // crescent
        for (double x = -0.55; x <= -0.049; x += 0.125) fill(wallRect(x - 0.008, x + 0.008, 0.38, 0.8), 0x1A1418);
        for (double y = 0.38; y <= 0.801; y += 0.105) fill(wallRect(-0.55, -0.05, y - 0.006, y + 0.006), 0x1A1418);
        // Heavy curtains.
        fill(wallRect(-0.68, -0.53, 0.3, 0.86), 0x4A1A22);
        fill(wallRect(-0.07, 0.07, 0.3, 0.86), 0x4A1A22);
        fill(wallRect(-0.7, 0.09, 0.85, 0.88), 0x221C24);
        // Moonlight falling across the floor towards you.
        beam = poly(p(-0.5, 0, 0.78), p(-0.08, 0, 0.78), p(0.2, 0, 0.25), p(-0.3, 0, 0.25));

        // Calendar on the wall: 30 days, most of them crossed out.
        fill(wallRect(0.3, 0.52, 0.45, 0.72), 0xB8B0A8);
        fill(wallRect(0.3, 0.52, 0.66, 0.72), 0x8A1A26);
        for (int i = 0; i < 30; i++) {
            double cx = 0.315 + (i % 6) * 0.034, cy = 0.63 - (i / 6) * 0.034;
            fill(wallRect(cx, cx + 0.02, cy - 0.02, cy), i < 23 ? 0x8A1A26 : 0x6A645E);
        }

        // Door on the left wall, slightly open: a black gap down one side.
        fill(poly(p(-1, 0, 0.08), p(-1, 0.78, 0.08), p(-1, 0.78, 0.3), p(-1, 0, 0.3)), 0x2A1E16);
        fill(poly(p(-1, 0, 0.28), p(-1, 0.78, 0.28), p(-1, 0.78, 0.3), p(-1, 0, 0.3)), 0x050405);
        int[] knob = p(-1, 0.38, 0.25);
        g.setColor(new Color(0x8A7A50));
        g.fillRect(knob[0], knob[1], 3, 3);

        // Rug.
        fill(poly(p(-0.38, 0, 0.12), p(0.38, 0, 0.12), p(0.38, 0, 0.58), p(-0.38, 0, 0.58)), 0x2A1620);
        fill(poly(p(-0.33, 0, 0.16), p(0.33, 0, 0.16), p(0.33, 0, 0.54), p(-0.33, 0, 0.54)), 0x3A2030);

        // Bed against the right wall: headboard, frame, blanket, pillow.
        box(0.4, 0.98, 0, 0.36, 0.88, 0.93, 0x3A2418, 0x3A2418, 0x2E1C12);
        box(0.4, 0.98, 0, 0.12, 0.35, 0.88, 0x3A2418, 0x3A2418, 0x2E1C12);
        box(0.42, 0.98, 0.12, 0.2, 0.36, 0.88, 0x5A2430, 0x4A1C26, 0x40181F);
        box(0.48, 0.92, 0.2, 0.26, 0.76, 0.86, 0x8A8490, 0x6E6874, 0x5E5864);

        // Desk with a lamp: the only warm light in the room.
        box(-0.98, -0.62, 0, 0.33, 0.76, 0.79, 0x2A1C14, 0x2A1C14, 0x24180F); // back panel
        box(-0.66, -0.62, 0, 0.33, 0.46, 0.5, 0x2A1C14, 0x30200F, 0x24180F);  // front leg
        box(-0.98, -0.62, 0.33, 0.38, 0.45, 0.8, 0x4A3020, 0x3A2416, 0x30200F);
        box(-0.81, -0.77, 0.38, 0.4, 0.6, 0.64, 0x2A2A2A, 0x222222, 0x1A1A1A);
        box(-0.8, -0.78, 0.4, 0.47, 0.61, 0.63, 0x2A2A2A, 0x222222, 0x1A1A1A);
        box(-0.86, -0.72, 0.47, 0.54, 0.58, 0.67, 0xE8B070, 0xD89850, 0xC08040);
        Polygon shade = poly(p(-0.86, 0.47, 0.58), p(-0.72, 0.47, 0.58), p(-0.72, 0.54, 0.58), p(-0.86, 0.54, 0.58));
        glow(shade);
        glow(poly(p(-0.86, 0.47, 0.58), p(-0.72, 0.47, 0.58), p(-0.72, 0.47, 0.67), p(-0.86, 0.47, 0.67)));
        // A few books.
        box(-0.95, -0.92, 0.38, 0.47, 0.66, 0.74, 0x5A1A22, 0x5A1A22, 0x401218);
        box(-0.92, -0.9, 0.38, 0.45, 0.66, 0.74, 0x2A3A5A, 0x2A3A5A, 0x1C2840);

        light(p(-0.79, 0.5, 0.62), rnd);
        return img;
    }

    /** Darkens everything, then adds back the lamp's warm pool, the moonbeam and a vignette. */
    static void light(int[] lamp, Random rnd) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = img.getRGB(x, y);
                double r = (c >> 16 & 255) / 255.0, gr = (c >> 8 & 255) / 255.0, b = (c & 255) / 255.0;
                double lr, lg, lb;
                if ((emissive.getRaster().getSample(x, y, 0)) > 0) {
                    lr = lg = lb = 1.05;
                } else {
                    double dx = (x - lamp[0]) / 480.0, dy = (y - lamp[1]) / 480.0 * 1.3;
                    double lampI = Math.exp(-(dx * dx + dy * dy) / (0.16 * 0.16)) * 1.15;
                    lr = 0.24 + lampI * 1.0;
                    lg = 0.22 + lampI * 0.72;
                    lb = 0.28 + lampI * 0.45;
                    if (beam.contains(x, y)) {
                        lr += 0.2;
                        lg += 0.27;
                        lb += 0.42;
                    }
                }
                double vx = (x - W / 2.0) / (W / 2.0), vy = (y - H / 2.0) / (H / 2.0);
                double vig = Math.max(0, 1 - 0.55 * (vx * vx + vy * vy));
                int noise = rnd.nextInt(7) - 3;
                int nr = clamp(r * lr * vig * 255 + noise), ng = clamp(gr * lg * vig * 255 + noise), nb = clamp(b * lb * vig * 255 + noise);
                img.setRGB(x, y, nr << 16 | ng << 8 | nb);
            }
        }
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    // --------------------------------------------------------------- icons

    // Order matters: HowToPlayScreen indexes into this sheet.
    // 0 note, 1 calendar, 2 bag, 3 clue, 4 talk, 5 house, 6 clock, 7 accuse, 8 bat, 9 eye
    static final String[][] ICONS = {
            {"", "", "", ".kkkkkkkkkkkkkk.", ".kwwwwwwwwwwwwk.", ".kkwwwwwwwwwwkk.", ".kwkwwwwwwwwkwk.", ".kwwkwwwwwwkwwk.",
                    ".kwwwkrrwrrkwwk.", ".kwwwwrrrrrwwwk.", ".kwwwwwrrrwwwwk.", ".kwwwwwwrwwwwwk.", ".kwwwwwwwwwwwwk.", ".kkkkkkkkkkkkkk."},
            {"", "...k......k.....", ".kkkkkkkkkkkkkk.", ".krrrrrrrrrrrrk.", ".krrrrrrrrrrrrk.", ".kkkkkkkkkkkkkk.", ".kwwwwwwwwwwwwk.",
                    ".kwgwgwgwgwgwwk.", ".kwwwwwwwwwwwwk.", ".kwgwgwgwgwgwwk.", ".kwwwwwwwwwwwwk.", ".kwgwgwgwgwrwwk.", ".kwwwwwwwwwwwwk.",
                    ".kwgwgwgwwwwwwk.", ".kkkkkkkkkkkkkk."},
            {"", "", ".....kkkkkk.....", "....k......k....", "....k......k....", ".kkkkkkkkkkkkkk.", ".kbbbbbbbbbbbbk.",
                    ".kddddddddddddk.", ".kbbbbbyybbbbbk.", ".kbbbbbyybbbbbk.", ".kbbbbbbbbbbbbk.", ".kbbbbbbbbbbbbk.", ".kbbbbbbbbbbbbk.",
                    ".kdbbbbbbbbbbdk.", ".kkkkkkkkkkkkkk."},
            {"", "...kkkkkk.......", "..kcwwccck......", ".kcwcccccck.....", ".kcccccccck.....", ".kcccccccck.....",
                    ".kcccccccck.....", "..kcccccck......", "...kkkkkkd......", "..........dd....", "...........dd...",
                    "............dd..", ".............dd.", "..............d."},
            {"", "", "..kkkkkkkkkkkk..", ".kwwwwwwwwwwwwk.", ".kwwwwwwwwwwwwk.", ".kwggwgggwggwwk.", ".kwwwwwwwwwwwwk.",
                    ".kwgggwggwgggwk.", ".kwwwwwwwwwwwwk.", "..kkkwwkkkkkkk..", "....kwk.........", "...kwk..........",
                    "...kk..........."},
            {"", ".......kk.......", "......krrk......", ".....krrrrk.....", "....krrrrrrk....", "...krrrrrrrrk...",
                    "..krrrrrrrrrrk..", ".kkkkkkkkkkkkkk.", "..kwwwwwwwwwwk..", "..kwyywwwwkkwk..", "..kwyywwwwkbwk..",
                    "..kwwwwwwwkbwk..", "..kwwwwwwwkbwk..", "..kkkkkkkkkkkk.."},
            {"", ".....kkkkkk.....", "...kkwwwwwwkk...", "..kwwwwkwwwwwk..", ".kwwwwwkwwwwwwk.", ".kwwwwwkwwwwwwk.",
                    ".kwwwwwkwwwwwwk.", ".kwwwwwkkkkwwwk.", ".kwwwwwwwwwwwwk.", ".kwwwwwwwwwwwwk.", "..kwwwwwwwwwwk..",
                    "...kkwwwwwwkk...", ".....kkkkkk....."},
            {"", ".......rr.......", ".....rrrrrr.....", "....rr.rr.rr....", "...rr..rr..rr...", "..rr...rr...rr..",
                    "..r....rr....r..", ".rrrrrrkkrrrrrr.", ".rrrrrrkkrrrrrr.", "..r....rr....r..", "..rr...rr...rr..",
                    "...rr..rr..rr...", "....rr.rr.rr....", ".....rrrrrr.....", ".......rr......."},
            {"", "............kk..", "...........kbbk.", "..........kbbbk.", ".........kbbbk..", "........kbbbk...",
                    ".......kbbbk....", "......kbbk......", ".....kbbk.......", "....kbk...r.....", "...kbk..........",
                    "..kdk......r....", ".kdk............", ".kk............."},
            {"", "", "", "", "", "....kkkkkkkk....", "..kkwwwwwwwwkk..", ".kwwwwkkkkwwwwk.", ".kwwwkrkkrkwwwk.",
                    ".kwwwkrkkrkwwwk.", ".kwwwwkkkkwwwwk.", "..kkwwwwwwwwkk..", "....kkkkkkkk...."},
    };

    static int palette(char ch) {
        return switch (ch) {
            case 'k' -> 0xFF1A1418;
            case 'w' -> 0xFFE8E2DC;
            case 'g' -> 0xFF8A848A;
            case 'r' -> 0xFFC0182E;
            case 'b' -> 0xFF7A4A2A;
            case 'd' -> 0xFF4A2A18;
            case 'y' -> 0xFFE8C050;
            case 'c' -> 0xFF8AB4D8;
            default -> 0x00000000;
        };
    }

    static BufferedImage icons() {
        BufferedImage sheet = new BufferedImage(16 * ICONS.length, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < ICONS.length; i++) {
            String[] rows = ICONS[i];
            for (int y = 0; y < Math.min(16, rows.length); y++) {
                String row = rows[y];
                for (int x = 0; x < Math.min(16, row.length()); x++) {
                    sheet.setRGB(i * 16 + x, y, palette(row.charAt(x)));
                }
            }
        }
        return sheet;
    }
}
