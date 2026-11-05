package com.kokuhaku.game;

import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** A conversation the game drives: a line, some answers, and what each answer leads to. */
public final class Talk {
    /** Sent in place of a student's id, so the screen knows the server decides what happens next. */
    public static final int ID = -1;

    public record Option(String label, Consumer<ServerPlayerEntity> then) {
    }

    private static final Map<UUID, List<Option>> open = new HashMap<>();

    private Talk() {
    }

    public static Option option(String label, Consumer<ServerPlayerEntity> then) {
        return new Option(label, then);
    }

    /** An answer that just ends it. */
    public static Option leave(String label) {
        return new Option(label, p -> {
        });
    }

    public static void clear() {
        open.clear();
    }

    /** How many answers are on offer. */
    public static int answers(ServerPlayerEntity p) {
        return open.getOrDefault(p.getUuid(), List.of()).size();
    }

    public static boolean waiting(ServerPlayerEntity p) {
        return open.containsKey(p.getUuid());
    }

    public static void say(ServerPlayerEntity p, String name, String line, Option... options) {
        say(p, name, line, -1, options);
    }

    /** speaker: the entity id of whoever's talking, so a scene can turn to look at them. */
    public static void say(ServerPlayerEntity p, String name, String line, int speaker, Option... options) {
        if (options.length > 0) open.put(p.getUuid(), List.of(options));
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Student", ID);
        nbt.putInt("Speaker", speaker);
        nbt.putString("Name", name);
        nbt.putString("Line", line);
        NbtList list = new NbtList();
        for (Option o : options) list.add(NbtString.of(o.label()));
        nbt.put("Options", list);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(p, Net.DIALOGUE, buf);
    }

    public static void choose(ServerPlayerEntity p, int option) {
        List<Option> options = open.remove(p.getUuid());
        if (options == null || option < 0 || option >= options.size()) return;
        Consumer<ServerPlayerEntity> then = options.get(option).then();
        if (then != null) then.accept(p);
    }
}
