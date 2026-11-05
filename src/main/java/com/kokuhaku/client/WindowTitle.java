package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * The name on the window and in the taskbar is never the same twice, and never
 * anything you could read: Windows can't show Minecraft's own scrambled text
 * there, so this is the same effect by hand.
 */
public final class WindowTitle {
    private static final String NOISE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&@$?!/\\|*^~<>=+▓▒░█▌▐";
    private static final Random RANDOM = new Random();
    private static int ticks = 0;

    private WindowTitle() {
    }

    public static String scrambled() {
        StringBuilder title = new StringBuilder();
        int length = 10 + RANDOM.nextInt(16);
        for (int i = 0; i < length; i++) title.append(NOISE.charAt(RANDOM.nextInt(NOISE.length())));
        return title.toString();
    }

    public static void tick(MinecraftClient client) {
        if (++ticks % 3 == 0 && client.getWindow() != null) client.getWindow().setTitle(scrambled());
    }
}
