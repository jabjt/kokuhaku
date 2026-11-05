package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.net.Net;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The radio in your room. It's the only music in the town, and for the first
 * few days that's all it is. Then someone starts sending you requests, and she
 * can tell when you've left it on all night. Later on there's someone else on
 * that frequency, trying to get through.
 */
final class Radio {
    private static final int TRACKS = 3, HER_SONG = 3, FARTHEST = 3, FIRST_REQUEST = 2, REQUESTS_AT = Schedule.EVENING + 90, BREAKS_IN_AT = Schedule.EVENING + 210, DAD_EVERY = 3, LOCK_ONE_IN = 3;
    private static final float REQUEST_CHANCE = 0.6f;
    private static final int DESK_REACH = 7, DESK_HEIGHT = 2;
    private static final Random RANDOM = new Random();

    private static final Map<UUID, BlockPos> radios = new HashMap<>();
    private static final Set<UUID> playing = new HashSet<>();
    private static final Map<UUID, Town.Home> homes = new HashMap<>();

    private Radio() {
    }

    static void clear() {
        radios.clear();
        playing.clear();
        homes.clear();
    }

    /** The thing sitting on your desk. It goes, and the radio's there instead. */
    private static BlockPos deskTop(ServerWorld world, Town.Home home) {
        BlockPos found = null;
        for (BlockPos at : BlockPos.iterateOutwards(home.bedHead().up(), DESK_REACH, DESK_HEIGHT, DESK_REACH)) {
            if (!(world.getBlockState(at).getBlock() instanceof AbstractSkullBlock)) continue;
            if (found == null) found = at.toImmutable();
            else world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
        return found;
    }

    /** On your desk, on the first morning, as if it had always been there. */
    static void place(ServerPlayerEntity p, Town.Home home) {
        if (radios.containsKey(p.getUuid())) return;
        homes.put(p.getUuid(), home);
        ServerWorld world = p.getServerWorld();
        BlockPos desk = deskTop(world, home);
        Kokuhaku.LOG.info("Radio: desk spot {}", desk);
        if (desk != null) {
            world.setBlockState(desk, ModBlocks.RADIO.getDefaultState());
            radios.put(p.getUuid(), desk);
            return;
        }
        BlockPos head = home.bedHead();
        for (int ring = 1; ring <= FARTHEST; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    BlockPos at = head.add(dx, 0, dz);
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !home.containsBlock(at) || !RunManager.standable(world, at)) continue;
                    world.setBlockState(at, ModBlocks.RADIO.getDefaultState());
                    radios.put(p.getUuid(), at);
                    return;
                }
            }
        }
        Kokuhaku.LOG.warn("No room for a radio at home {}", home.index());
    }

    private static void send(ServerPlayerEntity p, boolean on, int track) {
        BlockPos at = radios.get(p.getUuid());
        if (at == null) return;
        if (on) playing.add(p.getUuid());
        else playing.remove(p.getUuid());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        buf.writeBlockPos(at);
        buf.writeVarInt(track);
        ServerPlayNetworking.send(p, Net.RADIO, buf);
    }

    private static boolean on(ServerPlayerEntity p) {
        return playing.contains(p.getUuid());
    }

    /** No One Can Save You has the one song, and nowhere else ever does. */
    private static int[] station(RunState s) {
        if ("radio".equals(System.getenv("KOKUHAKU_STAGE"))) return new int[]{0, 1, 2, 3};
        return Kept.on(s) ? new int[]{HER_SONG} : new int[]{0, 1, 2};
    }

    static void use(ServerPlayerEntity p, RunState s) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(Story.runs() >= Story.CARD_FROM);
        buf.writeIntArray(station(s));
        ServerPlayNetworking.send(p, Net.RADIO_OPEN, buf);
    }

    static void pick(ServerPlayerEntity p, RunState s, int action) {
        if (action == -1) {
            send(p, false, 0);
            return;
        }
        if (action == -2) {
            String card = Story.card();
            if (!s.notes.contains(card)) s.notes.add(card);
            s.markDirty();
            Talk.say(p, "Under the radio", card, Talk.leave("..."));
            return;
        }
        for (int track : station(s)) if (track == action) send(p, true, track);
    }

    static Town.Home homeOf(ServerPlayerEntity p) {
        return homes.get(p.getUuid());
    }

    static BlockPos at(ServerPlayerEntity p) {
        return radios.get(p.getUuid());
    }

    /** Along the desk a little. You'd have to know where you left it. */
    static void shift(ServerPlayerEntity p, BlockPos to) {
        BlockPos from = radios.get(p.getUuid());
        if (from == null || on(p)) return;
        ServerWorld world = p.getServerWorld();
        world.setBlockState(to, world.getBlockState(from));
        world.setBlockState(from, Blocks.AIR.getDefaultState());
        radios.put(p.getUuid(), to.toImmutable());
    }

    private static String request(RunState s) {
        String girl = Whispers.lastGirl();
        List<String> lines = new ArrayList<>(List.of("\"...and this one's going out to someone very special. They know who they are. They're listening right now.\"",
                "\"...a request. No name. They just said: you looked tired today. Sleep well.\"",
                "\"...this one's from someone who knows exactly which room you're in.\""));
        if (girl != null) lines.add("\"...and a request from someone who saw you talking to " + girl + " today. She says she'd like you to think about that.\"");
        if (!s.dead.isEmpty()) lines.add("\"...one for everyone who's missing somebody tonight. Isn't it better, now it's quiet?\"");
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    /** Evenings at home. She doesn't need it switched on. */
    static void minute(ServerPlayerEntity p, RunState s) {
        // No One Can Save You: there's only the one song now, and she puts it on for you herself.
        if (Kept.on(s)) {
            if (s.minute == REQUESTS_AT && RunManager.isHomeNow(p, s) && radios.containsKey(p.getUuid())) {
                send(p, true, HER_SONG);
                RunManager.tell(p, "The radio's come on by itself.", "It's the same song as last night. It's the only one it plays now.");
            }
            return;
        }
        if (s.day < FIRST_REQUEST || s.devoted || Living.active(s) || Kept.on(s) || !RunManager.isHomeNow(p, s) || !radios.containsKey(p.getUuid())) return;
        if (s.minute == REQUESTS_AT && RANDOM.nextFloat() < REQUEST_CHANCE) {
            boolean wasOff = !on(p);
            if (wasOff) send(p, true, s.day % TRACKS);
            RunManager.tell(p, wasOff ? "The radio's come on by itself." : "On the radio", request(s));
            Shadow.saw(s, "She phoned that request in from the end of your street.");
        }
        if (s.minute == BREAKS_IN_AT && Story.runs() >= Story.CARD_FROM && s.day % DAD_EVERY == 0) {
            send(p, false, 0);
            RunManager.tell(p, "The radio cuts out.", "Static, and a man's voice, a long way off: \"...can't... turn it... don't let her...\"");
        }
    }

    /** Going to bed with it on tells her you're there, and when you're out. */
    static void bedtime(ServerPlayerEntity p, RunState s) {
        if (!on(p)) return;
        send(p, false, 0);
        if (s.day < FIRST_REQUEST || RANDOM.nextInt(LOCK_ONE_IN) != 0) return;
        s.lockBrokenOn = s.day;
        s.markDirty();
        Shadow.saw(s, "You fell asleep with the radio on. She listened to it with you, from the other side of the door.");
    }

    /** The power's gone, and everything on it. */
    static void silence(ServerPlayerEntity p) {
        if (on(p)) send(p, false, 0);
    }
}
