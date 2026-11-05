package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The secret endings you've reached, kept outside any one run. */
public final class EndingsBook {
    private static final Pattern SECRET = Pattern.compile("Secret ending (\\d+) of (\\d+)");
    private static final int TOTAL = 7;

    private EndingsBook() {
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("kokuhaku_endings.txt");
    }

    private static Set<String> found() {
        Set<String> found = new TreeSet<>();
        try {
            if (Files.exists(file())) found.addAll(Files.readAllLines(file()));
        } catch (IOException e) {
            return found;
        }
        found.removeIf(String::isBlank);
        return found;
    }

    /** Notes it down if this is a secret ending. */
    public static void saw(String ending) {
        Matcher m = SECRET.matcher(ending);
        if (!m.find()) return;
        Set<String> all = found();
        if (!all.add(m.group(1))) return;
        try {
            Files.write(file(), all);
        } catch (IOException ignored) {
            // A book we can't write to just doesn't get updated.
        }
    }

    public static boolean has(int ending) {
        return found().contains(Integer.toString(ending));
    }

    public static boolean foundAll() {
        return found().size() >= TOTAL;
    }

    public static String summary() {
        return "Secret endings found: " + found().size() + " of " + TOTAL;
    }
}
