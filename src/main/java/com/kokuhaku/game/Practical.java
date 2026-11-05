package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Every other Science lesson's up in the lab with the brewing stands, and every
 * other Home Economics is in the kitchens. You're given a bench, not a question:
 * go to one, put your hand on the equipment, and do the thing. None of Minecraft's
 * own menus come into it.
 */
public final class Practical {
    private static final int LAB_WEST = -609, LAB_EAST = -593, LAB_NORTH = -92, LAB_SOUTH = -70, UPSTAIRS = 30;
    private static final int KITCHEN_WEST = -584, KITCHEN_EAST = -568, KITCHEN_NORTH = -120, KITCHEN_SOUTH = -100;
    private static final int GIVE_UP_TICKS = 20 * 45, TOP_MARKS_PRIZE = 100;

    private static final Set<UUID> waiting = new HashSet<>();

    private Practical() {
    }

    static void clear() {
        waiting.clear();
    }

    public static boolean there(Vec3d p, String subject) {
        if (subject.equals("Science")) return p.x >= LAB_WEST && p.x <= LAB_EAST && p.z >= LAB_NORTH && p.z <= LAB_SOUTH && p.y >= UPSTAIRS;
        return p.x >= KITCHEN_WEST && p.x <= KITCHEN_EAST && p.z >= KITCHEN_NORTH && p.z <= KITCHEN_SOUTH && p.y < UPSTAIRS;
    }

    static void start(ServerPlayerEntity p, RunState s, String subject) {
        waiting.add(p.getUuid());
        boolean lab = subject.equals("Science");
        RunManager.tell(p, Teachers.nameFor(subject), lab ? "\"Practical today. Find a brewing stand and get on with it.\"" : "\"Practical today. Pick a stove. Nobody's eating this, so relax.\"");
        RunManager.later(GIVE_UP_TICKS, () -> {
            if (waiting.remove(p.getUuid())) RunManager.peDone(p, 0, 3, Teachers.nameFor(subject), "\"You stood there the whole lesson. I've noted that.\"", 0);
        });
    }

    /** The lab and kitchen gear never opens a Minecraft menu, lesson or no lesson. */
    static boolean use(ServerPlayerEntity p, RunState s, BlockState state) {
        if (!state.isOf(Blocks.BREWING_STAND) && !state.isOf(Blocks.FURNACE) && !state.isOf(Blocks.CAULDRON)) return false;
        boolean right = state.isOf(Blocks.FURNACE) ? there(p.getPos(), "Home Economics") : there(p.getPos(), "Science");
        if (!waiting.contains(p.getUuid()) || !right) {
            RunManager.tell(p, "", "You shouldn't touch that without a teacher in the room.");
            return true;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(state.isOf(Blocks.FURNACE));
        buf.writeVarInt(s.difficulty.ordinal());
        ServerPlayNetworking.send(p, Net.PRACTICAL, buf);
        return true;
    }

    /** How it went. True if this was what was being waited for. */
    public static boolean finished(ServerPlayerEntity p, int done, int outOf) {
        if (!waiting.remove(p.getUuid())) return false;
        Kokuhaku.LOG.info("Practical: {} of {}", done, outOf);
        String line = done == outOf ? "\"Perfect. Don't look so surprised. Here.\"" : done > 0 ? "\"It'll do. Clean up after yourself.\"" : "\"I'm going to need you to open a window.\"";
        RunManager.peDone(p, done, outOf, "Teacher", done + " of " + outOf + ". " + line, done == outOf ? TOP_MARKS_PRIZE : 0);
        return true;
    }
}
