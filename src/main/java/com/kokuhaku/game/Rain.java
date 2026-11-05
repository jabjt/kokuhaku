package com.kokuhaku.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.Random;

/**
 * It rains here. Worked out from the day, so everyone's plans can know: the
 * yard empties, tennis moves indoors, and nobody's at the beach.
 */
final class Rain {
    private static final int RAINY_IN_100 = 30, FROM_HOUR = 6, HOURS_IT_COULD_START = 14, SHORTEST = 180, LONGEST_MORE = 420, HOLD = 24000, KEEP_IT_UP = 30;
    private static boolean was = false;

    private Rain() {
    }

    static void clear() {
        was = false;
    }

    static boolean now(RunState s) {
        if (Festival.on(s)) return false;
        if ("1".equals(System.getenv("KOKUHAKU_RAIN"))) return true;
        Random today = new Random(s.seed ^ s.day * 19937L);
        if (today.nextInt(100) >= RAINY_IN_100) return false;
        int from = (FROM_HOUR + today.nextInt(HOURS_IT_COULD_START)) * 60, length = SHORTEST + today.nextInt(LONGEST_MORE);
        return s.minute >= from && s.minute < from + length;
    }

    static void minute(MinecraftServer server, RunState s, List<ServerPlayerEntity> living) {
        boolean wet = now(s);
        if (wet == was && s.minute % KEEP_IT_UP != 0) return;
        ServerWorld world = server.getOverworld();
        world.setWeather(wet ? 0 : HOLD, wet ? HOLD : 0, wet, false);
        if (wet && !was) for (ServerPlayerEntity p : living) RunManager.tell(p, "", "It's started to rain.");
        was = wet;
    }
}
