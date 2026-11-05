import com.kokuhaku.game.Roster;

import com.kokuhaku.game.Traits.Hair;
import com.kokuhaku.game.Traits.Length;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Builds the 40 student skins (and yours) from the two base skins in tools/skins.
 * Girls come from girl.png, boys from boy.png; each gets their roster hair colour,
 * hair length and an eye colour; glasses are a model of their own (GlassesModel). Needs the compiled mod classes:
 *   javac -cp build/classes/java/main -d build/tools tools/SkinGen.java
 *   java -cp "build/classes/java/main;build/tools" SkinGen
 */
public class SkinGen {
    static final String OUT = "src/main/resources/assets/kokuhaku/textures/entity/student/";

    /** Darkest and lightest shade for each hair colour. */
    static int[][] hairShades(Hair h) {
        return switch (h) {
            case BLACK -> new int[][]{{0x16, 0x14, 0x1C}, {0x3C, 0x38, 0x46}};
            case BROWN -> new int[][]{{0x46, 0x2A, 0x1C}, {0x8C, 0x5C, 0x3A}};
            case BLONDE -> new int[][]{{0xB0, 0x88, 0x40}, {0xF2, 0xDA, 0x8E}};
            case RED -> new int[][]{{0x72, 0x1A, 0x1A}, {0xC8, 0x44, 0x30}};
            case PINK -> new int[][]{{0xBC, 0x64, 0x86}, {0xF6, 0xAE, 0xC8}};
            case SILVER -> new int[][]{{0x94, 0x96, 0xA4}, {0xE2, 0xE4, 0xEC}};
        };
    }

    static final Hair[] BOY_HAIR = {Hair.BLACK, Hair.BLACK, Hair.BROWN, Hair.BLACK, Hair.BROWN, Hair.BLONDE, Hair.BLACK,
            Hair.RED, Hair.BROWN, Hair.BLACK, Hair.SILVER, Hair.BLACK, Hair.BROWN, Hair.BLACK, Hair.BLONDE, Hair.BLACK,
            Hair.BROWN, Hair.BLACK, Hair.BLACK, Hair.BROWN, Hair.BLACK, Hair.BROWN, Hair.BLACK, Hair.BLACK, Hair.BROWN, Hair.BLACK};
    static final int[] EYES = {0x3A2A22, 0x2A4A7A, 0x3A6A3A, 0x7A3A2A, 0x5A3A6A, 0x2A2A2A, 0x6A5A2A, 0x2A5A5A};
    static final int UNIFORM = 0x101121;


    public static void main(String[] args) throws Exception {

        BufferedImage girl = withFace(ImageIO.read(new File("tools/skins/girl.png")), ImageIO.read(new File("tools/skins/face_girl.png")));
        BufferedImage boy = withFace(ImageIO.read(new File("tools/skins/boy.png")), ImageIO.read(new File("tools/skins/face_boy.png")));
        new File(OUT).mkdirs();
        for (Roster.Student s : Roster.STUDENTS) {
            boolean female = s.female();
            Hair hair = female ? s.hair() : BOY_HAIR[s.id() - Roster.GIRLS];
            boolean shortHair = female && s.length() == Length.SHORT;
            BufferedImage skin = make(female ? girl : boy, hair, shortHair, EYES[s.id() % EYES.length]);
            ImageIO.write(skin, "png", new File(OUT + String.format("student_%02d.png", s.id())));
        }
        // Townsfolk: same bases, out of uniform.
        int[][] clothes = {{120, 130, 150}, {150, 110, 90}, {90, 120, 95}, {170, 165, 150}, {110, 95, 130}, {80, 95, 120}};
        Hair[] hairs = {Hair.BLACK, Hair.BROWN, Hair.SILVER, Hair.BLACK, Hair.BROWN, Hair.BLONDE};
        for (int i = 0; i < 12; i++) {
            boolean female = i % 2 == 0;
            BufferedImage skin = make(female ? girl : boy, hairs[i % hairs.length], i % 3 != 0, EYES[i % EYES.length]);
            int[] c = clothes[i % clothes.length];
            for (int y = 16; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int p = skin.getRGB(x, y);
                    if ((p >>> 24) == 0) continue;
                    int light = Math.max((p >> 16) & 255, Math.max((p >> 8) & 255, p & 255));
                    int skinTone = ((p >> 16) & 255) - (p & 255);
                    if (skinTone > 40 && light > 150) continue; // hands and faces stay as they are
                    double v = 0.45 + light / 255.0 * 0.75;
                    skin.setRGB(x, y, 0xFF000000 | Math.min(255, (int) (c[0] * v)) << 16 | Math.min(255, (int) (c[1] * v)) << 8 | Math.min(255, (int) (c[2] * v)));
                }
            }
            ImageIO.write(skin, "png", new File(OUT + String.format("student_%02d.png", Roster.TOTAL + i)));
        }
        ImageIO.write(copy(boy), "png", new File(OUT + "you.png"));
        System.out.println("Wrote " + Roster.TOTAL + " student skins + you.png to " + OUT);
    }

    static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) out.setRGB(x, y, src.getRGB(x, y));
        return out;
    }

    /** The front of the head on the base layer. The hat layer over it is never touched. */
    static boolean inFace(int x, int y) {
        return x >= 8 && x < 16 && y >= 8 && y < 16;
    }

    static BufferedImage withFace(BufferedImage base, BufferedImage face) {
        BufferedImage out = copy(base);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) out.setRGB(x, y, face.getRGB(x, y));
        return out;
    }

    /** The eye itself, in both faces: everything else in the eye block is lash or white. */
    static boolean isIris(int x, int y) {
        return (y == 13 || y == 14) && (x == 10 || x == 13);
    }

    static boolean inEyeBlock(int x, int y) {
        return y >= 12 && y <= 14 && (x == 9 || x == 10 || x == 13 || x == 14);
    }

    /** In the face, hair is anything dark that isn't part of an eye. */
    static boolean isFaceHair(int x, int y, int argb) {
        return !inEyeBlock(x, y) && (argb >> 16 & 255) < 0x60;
    }

    static int red(int argb) {
        return argb >> 16 & 255;
    }

    static int scaled(int colour, double by) {
        int r = Math.min(255, (int) (red(colour) * by)), g = Math.min(255, (int) ((colour >> 8 & 255) * by)), b = Math.min(255, (int) ((colour & 255) * by));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The base skins' hair is a dark, slightly warm grey; the uniform is navy (more blue than red). */
    static boolean isHair(int argb) {
        if ((argb >>> 24) == 0) return false;
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        return r >= 0x24 && r <= 0x48 && r >= g && r - b >= 3 && r - b <= 0x16;
    }

    static boolean inBody(int x, int y) {
        return x >= 16 && x < 40 && y >= 16 && y < 32;
    }

    static boolean inBodyOverlay(int x, int y) {
        return x >= 16 && x < 40 && y >= 32 && y < 48;
    }

    static BufferedImage make(BufferedImage base, Hair hair, boolean shortHair, int eye) {
        BufferedImage out = copy(base);
        int[][] shade = hairShades(hair);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = out.getRGB(x, y);
                if (inFace(x, y) || !isHair(argb)) continue;
                if (shortHair && inBody(x, y)) {
                    out.setRGB(x, y, 0xFF000000 | UNIFORM);
                    continue;
                }
                if (shortHair && inBodyOverlay(x, y)) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                float t = Math.max(0f, Math.min(1f, ((argb >> 16 & 255) - 0x24) / (float) (0x46 - 0x24)));
                int r = lerp(shade[0][0], shade[1][0], t), g = lerp(shade[0][1], shade[1][1], t), b = lerp(shade[0][2], shade[1][2], t);
                out.setRGB(x, y, (argb & 0xFF000000) | r << 16 | g << 8 | b);
            }
        }
        // The face's hair runs darker or lighter than the rest of the skin, so it's scaled to its own range.
        int darkest = 255, lightest = 0;
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                int argb = base.getRGB(x, y);
                if (isFaceHair(x, y, argb) && (argb & 0xFFFFFF) != 0) {
                    darkest = Math.min(darkest, red(argb));
                    lightest = Math.max(lightest, red(argb));
                }
            }
        }
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                int argb = base.getRGB(x, y);
                if (isFaceHair(x, y, argb)) {
                    float t = Math.max(0f, Math.min(1f, (red(argb) - darkest) / (float) Math.max(1, lightest - darkest)));
                    int r = lerp(shade[0][0], shade[1][0], t), g = lerp(shade[0][1], shade[1][1], t), b = lerp(shade[0][2], shade[1][2], t);
                    out.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
                }
                // Each eye keeps its own light and dark, in the student's colour.
                if (isIris(x, y)) out.setRGB(x, y, scaled(eye, y == 13 ? 1.0 : Math.min(1.6, red(argb) / (double) Math.max(1, red(base.getRGB(x, 13))))));
            }
        }

        return out;
    }

    static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
