package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;

/**
 * You've named every girl you could, and you were wrong. Nobody says anything.
 * They just stop being near you, all of them, one after another, until there's
 * nobody left to see what happens next. Then she's behind you.
 */
final class Cornered {
    private static final double SEEN_BY = 34;
    private static final int LEFT_ALONE_AFTER = 130;

    private Cornered() {
    }

    static void begin(net.minecraft.server.MinecraftServer server, RunState s, Iterable<ServerPlayerEntity> living) {
        for (ServerPlayerEntity p : new ArrayList<ServerPlayerEntity>() {{ living.forEach(this::add); }}) {
            if (Accomplice.is(p)) continue;
            RunManager.setBusy(p, true);
            RunManager.holdClock(s.party.size() == 1);
            Kokuhaku.LOG.info("{} is out of guesses", p.getGameProfile().getName());
            for (StudentEntity e : RunManager.studentsOf(server)) {
                if (e.squaredDistanceTo(p) < SEEN_BY * SEEN_BY) e.walkAwayFrom(p.getPos());
            }
            Talk.say(p, "", "That was your last guess. Someone near you stops talking. Then they all do. Nobody looks at you. One by one, they turn and walk away.");
            RunManager.later(LEFT_ALONE_AFTER, () -> Grab.hopeless(p, s));
        }
    }
}
