package com.kokuhaku.game;

import com.kokuhaku.game.Mystery.Clue;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything she really did this run, kept as it happened. When it's over the
 * mask comes off and you're shown what you walked past. Nothing here is made
 * up afterwards, so the mystery was always fair.
 */
final class Shadow {
    private static final int SHOWN = 11;
    private static final String FIRST = "Day 1: She was at your locker before the caretaker. She folded the note four times.";
    private static final List<String> events = new ArrayList<>();

    private Shadow() {
    }

    static void clear() {
        events.clear();
    }

    static void saw(RunState s, String what) {
        String line = "Day " + s.day + ": " + what;
        if (!events.contains(line)) events.add(line);
    }

    /** Evenly through the month, always ending on the last thing she did. */
    private static List<String> chosen() {
        List<String> picked = new ArrayList<>(List.of(FIRST));
        int room = SHOWN - 1, count = events.size();
        for (int i = 0; i < Math.min(room, count); i++) {
            picked.add(events.get(count <= room ? i : (int) Math.round(i * (count - 1) / (double) (room - 1))));
        }
        return picked;
    }

    /** masked: there are still friends out there, so you never learn her name. */
    static String reel(RunState s, boolean masked) {
        Mystery m = s.mystery();
        List<String> lines = new ArrayList<>();
        lines.add(masked ? "You never saw her face." : "It was " + String.join(" and ", m.yanderes.stream().map(y -> Roster.get(y).name()).toList()) + ".");
        lines.addAll(chosen());
        if (!masked) {
            List<Clue> told = m.clues.stream().filter(c -> c.witness() >= 0).toList();
            List<Clue> missed = told.stream().filter(c -> !s.found.contains(c.id())).toList();
            lines.add("");
            lines.add(Habits.kind(s));
            lines.add(Story.lastLine());
            lines.add("You heard " + (told.size() - missed.size()) + " of the " + told.size() + " things people knew.");
            Clue unheard = missed.stream().filter(c -> c.kind() == Mystery.Kind.TRAIT).findFirst().orElse(null);
            if (unheard != null) lines.add(Roster.get(unheard.witness()).firstName() + " could have told you: " + unheard.text());
            else if (!missed.isEmpty()) lines.add(Roster.get(missed.get(0).witness()).firstName() + " knew something. Nobody ever asked.");
            if (!s.dead.isEmpty()) lines.add(s.dead.size() + (s.dead.size() == 1 ? " person" : " people") + " didn't see the end of it.");
            String helper = Accomplice.reveal();
            if (helper != null) lines.add(helper);
        }
        return String.join("\n", lines);
    }

    static void send(ServerPlayerEntity p, RunState s, boolean masked) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(reel(s, masked));
        ServerPlayNetworking.send(p, Net.REEL, buf);
    }
}
