package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The photos she sends you, which your own game takes. When the server asks, at the next
 * moment you're walking about with nothing on screen, the view drops behind you for a frame
 * with no HUD, and that frame is kept: small, dark and grainy, like something taken from a
 * long way off. If you notice the flicker, you noticed her.
 */
public final class PhotoAlbum {
    private static final int WIDE = 160, HIGH = 90;
    private static final long GIVE_UP_MS = 30000;
    private static final Map<Integer, Identifier> photos = new HashMap<>();
    private static final Random RANDOM = new Random();

    private static int pending = -1, frames;
    private static long asked;
    private static boolean behind;
    private static Perspective was;
    private static boolean hudWas;

    private PhotoAlbum() {
    }

    /** The server wants picture number id. */
    public static void request(int id) {
        pending = id;
        asked = System.currentTimeMillis();
    }

    /** Picture number id, or null if this game never took it. */
    public static Identifier get(int id) {
        return photos.get(id);
    }

    /** Counts the frames drawn from behind. */
    public static void frameDrawn() {
        if (behind) frames++;
    }

    public static void tick(MinecraftClient client) {
        if (pending < 0) return;
        if (behind) {
            if (frames < 1) return;
            // The last frame is still in the framebuffer: keep it, then put the view back.
            NativeImage full = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            Identifier id = new Identifier("kokuhaku", "photo/" + pending);
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(shrink(full)));
            full.close();
            photos.put(pending, id);
            client.options.setPerspective(was);
            client.options.hudHidden = hudWas;
            behind = false;
            pending = -1;
            return;
        }
        if (System.currentTimeMillis() - asked > GIVE_UP_MS) {
            pending = -1;
            return;
        }
        if (client.player == null || client.currentScreen != null || !ClientRun.active || Cutscene.active() || client.options.getPerspective() != Perspective.FIRST_PERSON) return;
        was = client.options.getPerspective();
        hudWas = client.options.hudHidden;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        client.options.hudHidden = true;
        frames = 0;
        behind = true;
    }

    /** The middle of the frame, shrunk, darkened, a little washed out, grainy and darker at the edges. */
    private static NativeImage shrink(NativeImage full) {
        NativeImage out = new NativeImage(WIDE, HIGH, false);
        int fw = full.getWidth(), fh = full.getHeight();
        // The same shape as the photo, cut from the middle.
        int cw = Math.min(fw, fh * WIDE / HIGH), ch = cw * HIGH / WIDE, ox = (fw - cw) / 2, oy = (fh - ch) / 2;
        for (int y = 0; y < HIGH; y++) {
            for (int x = 0; x < WIDE; x++) {
                int c = full.getColor(ox + x * cw / WIDE, oy + y * ch / HIGH);
                int r = c & 0xFF, g = c >> 8 & 0xFF, b = c >> 16 & 0xFF;
                int grey = (r * 30 + g * 59 + b * 11) / 100;
                double dx = (x - WIDE / 2.0) / (WIDE / 2.0), dy = (y - HIGH / 2.0) / (HIGH / 2.0);
                double keep = 0.78 * (1 - 0.45 * Math.min(1, dx * dx + dy * dy));
                int noise = RANDOM.nextInt(25) - 12;
                r = clamp((int) ((r * 0.7 + grey * 0.3) * keep) + noise);
                g = clamp((int) ((g * 0.7 + grey * 0.3) * keep) + noise);
                b = clamp((int) ((b * 0.7 + grey * 0.3) * keep) + noise);
                out.setColor(x, y, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
