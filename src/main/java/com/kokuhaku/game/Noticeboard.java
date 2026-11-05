package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * With nobody able to speak in The Accomplice, this is all you've got: a note
 * in the shared bag with a name on it. Three a day each. The one working for her
 * can sign someone else's name, so the name proves nothing.
 */
public final class Noticeboard {
    private static final int MOST_A_DAY = 3, LONGEST = 80;
    private static final Map<UUID, Integer> written = new HashMap<>();
    private static int writtenOn = 0;

    private Noticeboard() {
    }

    static void clear() {
        written.clear();
        writtenOn = 0;
    }

    /** Anyone may ask to sign as someone else. Only one of them gets to. */
    public static void post(ServerPlayerEntity p, String text, String signedAs) {
        RunState s = RunManager.state(p.server);
        String clean = text.strip();
        if (clean.isEmpty() || clean.length() > LONGEST || s.status != RunState.Status.PLAYING) return;
        if (writtenOn != s.day) {
            writtenOn = s.day;
            written.clear();
        }
        if (written.merge(p.getUuid(), 1, Integer::sum) > MOST_A_DAY) {
            RunManager.tell(p, "", "That's enough for today.");
            return;
        }
        String own = p.getGameProfile().getName();
        boolean forged = Accomplice.is(p) && RunManager.alive(p.server).stream().anyMatch(q -> q.getGameProfile().getName().equals(signedAs));
        String name = forged ? signedAs : own;
        s.notes.add("Day " + s.day + ", from " + name + ": " + clean);
        s.markDirty();
        Kokuhaku.LOG.info("Note from {} signed {}", own, name);
        List<ServerPlayerEntity> everyone = RunManager.alive(p.server);
        for (ServerPlayerEntity q : everyone) if (q != p) RunManager.tell(q, "", "Someone's left a note in the bag.");
    }
}
