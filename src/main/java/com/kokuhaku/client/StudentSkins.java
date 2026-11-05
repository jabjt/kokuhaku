package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import net.minecraft.util.Identifier;

/**
 * Student skins, generated from the two base skins by tools/SkinGen.java.
 * All of them (and yours) use the slim arm model, like the bases.
 */
public final class StudentSkins {
    public static final int GIRL_COUNT = 26;
    /** While your profile's being drawn: the skin you really have. */
    public static boolean ownSkin = false;
    public static final Identifier YOU = new Identifier(KokuhakuClient.MOD_ID, "textures/entity/student/you.png");

    private static final Identifier[] SKINS = new Identifier[74]; // the students, then the townsfolk, then the teachers, then the cook

    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = new Identifier(KokuhakuClient.MOD_ID, String.format("textures/entity/student/student_%02d.png", i));
        }
    }

    private StudentSkins() {
    }

    public static Identifier texture(int skin) {
        return SKINS[Math.floorMod(skin, SKINS.length)];
    }
}
