package com.kokuhaku.client;

import com.kokuhaku.Difficulty;
import com.kokuhaku.net.Net;
import com.kokuhaku.screen.Icons;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Your record, kept outside any one run: wins, losses and the badges they've earned. */
public final class Profile {
    /** Each one's a bit in the number sent to your party, so they can see yours. */
    public enum Badge {
        FIRST("First Find", "Name her in any run", Icons.ACCUSE),
        REGULAR("Regular", "Play 10 runs to the end", Icons.NOTE),
        DETECTIVE("Detective", "Name her in 10 runs", Icons.CLUE),
        IMPOSSIBLE("Impossible", "Win on Impossible", Icons.EYE),
        TRAPPED("Trapped", "Learn what the name means", Icons.BAG),
        EVERY_ENDING("Every Ending", "See all 7 secret endings", Icons.TALK),
        CANT_ESCAPE("You Can't Escape", "Beat No One Can Save You", Icons.CALENDAR),
        TRAITOR("Traitor!", "Win on her side", Icons.HOUSE);

        public final String label, how;
        public final int icon;

        Badge(String label, String how, int icon) {
            this.label = label;
            this.how = how;
            this.icon = icon;
        }

        public boolean in(int badges) {
            return (badges >> ordinal() & 1) == 1;
        }
    }

    private static final int ESCAPE_ENDING = 7, REGULAR_RUNS = 10, DETECTIVE_WINS = 10;
    private static int wins = 0, losses = 0, lastDay = 0;
    private static boolean impossible = false, traitor = false, nameRead = false, loaded = false, counted = false;

    private Profile() {
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("kokuhaku_profile.properties");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        if (!Files.exists(file())) return;
        try (Reader in = Files.newBufferedReader(file())) {
            p.load(in);
            wins = Integer.parseInt(p.getProperty("wins", "0"));
            losses = Integer.parseInt(p.getProperty("losses", "0"));
            lastDay = Integer.parseInt(p.getProperty("lastDay", "0"));
            impossible = Boolean.parseBoolean(p.getProperty("impossible", "false"));
            traitor = Boolean.parseBoolean(p.getProperty("traitor", "false"));
            nameRead = Boolean.parseBoolean(p.getProperty("nameRead", "false"));
        } catch (IOException | NumberFormatException e) {
            // A profile we can't read starts again.
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("wins", Integer.toString(wins));
        p.setProperty("losses", Integer.toString(losses));
        p.setProperty("lastDay", Integer.toString(lastDay));
        p.setProperty("impossible", Boolean.toString(impossible));
        p.setProperty("traitor", Boolean.toString(traitor));
        p.setProperty("nameRead", Boolean.toString(nameRead));
        try (Writer out = Files.newBufferedWriter(file())) {
            p.store(out, "Your record");
        } catch (IOException ignored) {
            // A record we can't write just doesn't get updated.
        }
    }

    /** Everyone else is gone, and she kept her word. */
    public static void wonForHer() {
        load();
        traitor = true;
        save();
    }

    /** You've read what's scratched on the rock. */
    public static void readTheName() {
        load();
        if (nameRead) return;
        nameRead = true;
        save();
    }

    /** How many runs you've seen the end of. */
    public static int played() {
        load();
        return wins + losses;
    }

    /** A new run: this one hasn't been counted yet. */
    public static void newRun() {
        counted = false;
    }

    /** However it ended, once. Watching as a spectator isn't playing. */
    public static void finished(boolean won) {
        load();
        if (counted || ClientRun.ghost) return;
        counted = true;
        lastDay = ClientRun.day;
        if (won) {
            wins++;
            if (ClientRun.difficulty == Difficulty.IMPOSSIBLE) impossible = true;
        } else {
            losses++;
        }
        save();
    }

    public static int badges() {
        load();
        int bits = 0;
        if (wins >= 1) bits |= 1 << Badge.FIRST.ordinal();
        if (wins + losses >= REGULAR_RUNS) bits |= 1 << Badge.REGULAR.ordinal();
        if (wins >= DETECTIVE_WINS) bits |= 1 << Badge.DETECTIVE.ordinal();
        if (impossible) bits |= 1 << Badge.IMPOSSIBLE.ordinal();
        if (nameRead) bits |= 1 << Badge.TRAPPED.ordinal();
        if (EndingsBook.has(ESCAPE_ENDING)) bits |= 1 << Badge.CANT_ESCAPE.ordinal();
        if (traitor) bits |= 1 << Badge.TRAITOR.ordinal();
        if (EndingsBook.foundAll()) bits |= 1 << Badge.EVERY_ENDING.ordinal();
        return bits;
    }

    /** So your party can see it. */
    public static void send() {
        load();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(wins);
        buf.writeVarInt(losses);
        buf.writeVarInt(badges());
        buf.writeVarInt(lastDay);
        ClientPlayNetworking.send(Net.PROFILE, buf);
    }
}
