package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The one saved run. It's written every morning, before the day starts, so
 * Continue puts you back in bed that morning. Nothing can be saved over it: a
 * new run can't start until this one's finished, one way or the other.
 * It lives beside the game, not inside a world, because every launch builds the
 * town fresh.
 */
public final class SaveSlot {
    private static final String FOLDER = "kokuhaku-save", RUN = "run.dat", SUMMARY = "summary.txt";

    /** What the menu shows about it. */
    public record Summary(int day, String difficulty, String players, boolean devoted) {
    }

    private SaveSlot() {
    }

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve(FOLDER);
    }

    public static boolean exists() {
        return Files.isRegularFile(dir().resolve(RUN));
    }

    public static Summary summary() {
        try {
            int day = 1;
            String difficulty = "", players = "";
            boolean devoted = false;
            for (String line : Files.readAllLines(dir().resolve(SUMMARY), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq), value = line.substring(eq + 1);
                switch (key) {
                    case "day" -> day = Integer.parseInt(value.trim());
                    case "difficulty" -> difficulty = value.trim();
                    case "players" -> players = value.trim();
                    case "devoted" -> devoted = Boolean.parseBoolean(value.trim());
                    default -> {
                    }
                }
            }
            return new Summary(day, difficulty, players, devoted);
        } catch (Exception e) {
            return new Summary(1, "", "", false);
        }
    }

    /**
     * The run as it stands. morning: the night's over and the day hasn't begun, so
     * loading it starts that next day; otherwise it's the very first morning.
     */
    static void write(RunState s, boolean morning, Collection<UUID> gone, UUID accomplice, List<ServerPlayerEntity> players) {
        if (System.getenv("KOKUHAKU_NOSAVE") != null) return;
        NbtCompound save = new NbtCompound();
        save.put("State", s.writeNbt(new NbtCompound()));
        save.putBoolean("Morning", morning);
        NbtList out = new NbtList();
        for (UUID id : gone) out.add(NbtString.of(id.toString()));
        save.put("Gone", out);
        if (accomplice != null) save.putString("Accomplice", accomplice.toString());
        NbtCompound bags = new NbtCompound();
        StringBuilder names = new StringBuilder();
        for (ServerPlayerEntity p : players) {
            if (!s.party.contains(p.getUuid())) continue;
            bags.put(p.getUuid().toString(), p.getInventory().writeNbt(new NbtList()));
            if (names.length() > 0) names.append(", ");
            names.append(p.getGameProfile().getName());
        }
        save.put("Bags", bags);
        // The day you'll wake up on.
        int day = morning ? s.day + 1 : s.day;
        try {
            Files.createDirectories(dir());
            Path temp = dir().resolve(RUN + ".new");
            NbtIo.writeCompressed(save, temp.toFile());
            Files.move(temp, dir().resolve(RUN), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(dir().resolve(SUMMARY), "day=" + day + "\ndifficulty=" + s.difficulty.name() + "\nplayers=" + names + "\ndevoted=" + s.devoted + "\n", StandardCharsets.UTF_8);
            Kokuhaku.LOG.info("Saved: day {}, {}", day, names);
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't save the run", e);
        }
    }

    static NbtCompound read() {
        try {
            return NbtIo.readCompressed(dir().resolve(RUN).toFile());
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't read the saved run", e);
            return null;
        }
    }

    /** It's over: the slot's free. */
    static void delete() {
        try {
            Files.deleteIfExists(dir().resolve(RUN));
            Files.deleteIfExists(dir().resolve(SUMMARY));
            Kokuhaku.LOG.info("Run finished: the save slot is free");
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't clear the saved run", e);
        }
    }
}
