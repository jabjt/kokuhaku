package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** The 16x16 pixel icons in icons.png (painted by tools/ArtGen.java). */
public final class Icons {
    public static final Identifier SHEET = new Identifier(KokuhakuClient.MOD_ID, "textures/gui/icons.png");
    public static final int COUNT = 10;
    public static final int NOTE = 0, CALENDAR = 1, BAG = 2, CLUE = 3, TALK = 4, HOUSE = 5, CLOCK = 6, ACCUSE = 7,
            BAT = 8, EYE = 9;

    private Icons() {
    }

    public static void draw(DrawContext context, int icon, int x, int y, int scale) {
        context.drawTexture(SHEET, x, y, 16 * scale, 16 * scale, icon * 16, 0, 16, 16, 16 * COUNT, 16);
    }
}
