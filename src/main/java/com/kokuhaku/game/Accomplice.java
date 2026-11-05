package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * For a party. One of you got a message before the first day: she won't touch
 * you, as long as you keep the others looking the wrong way. You can leave false
 * leads in the shared bag. If the rest are all gone, you've won. If they name
 * her, you've lost. Nobody can be named but the girls, or a handful of guesses
 * would just be a way of working through your friends, and nobody's voice
 * carries, or the one who wasn't lying would simply say so.
 */
public final class Accomplice {
    private static final int LEADS = 3;
    private static final String[] HEARD = {"A note on the noticeboard: ", "Scratched into a desk: ", "Someone in the corridor said: "};
    private static final Random RANDOM = new Random();

    private static UUID who = null;
    private static String name = "";
    private static boolean on = false;
    private static int plantedOn = 0;
    private static final List<String> planted = new ArrayList<>();

    private Accomplice() {
    }

    static void clear() {
        who = null;
        name = "";
        on = false;
        plantedOn = 0;
        planted.clear();
    }

    static void pick(ServerPlayerEntity p) {
        who = p.getUuid();
        name = p.getGameProfile().getName();
        on = true;
        Kokuhaku.LOG.info("The accomplice is {}", name);
    }

    static boolean is(ServerPlayerEntity p) {
        return who != null && who.equals(p.getUuid());
    }

    static boolean active() {
        return on;
    }

    /** How many false leads you've got left, or -1 if you're not the one. */
    static int leadsLeft(ServerPlayerEntity p) {
        return is(p) ? LEADS - planted.size() : -1;
    }


    /** For the end, once it can't hurt anyone. */
    static String reveal() {
        if (!on) return null;
        return name + " was working for her" + (planted.isEmpty() ? "." : ", and left " + planted.size() + (planted.size() == 1 ? " false lead" : " false leads") + " in your bag.");
    }

    /** Before the first morning. Nobody else gets this message. */
    static void brief(ServerPlayerEntity p, RunState s, Runnable then) {
        String names = String.join(" and ", s.mystery().yanderes.stream().map(y -> Roster.get(y).firstName()).toList());
        Talk.say(p, "Unknown number", "I know you can keep a secret. I won't touch you. Not you, not ever. It's " + names + ". All you have to do is keep the others looking the wrong way.",
                Talk.option("...What if I don't?", q -> Talk.say(q, "Unknown number", "Your bag has a new button: you can plant a false lead for them to find. Three, no more. If they're all gone by day 14, you've won. If they find me, you haven't.",
                        Talk.option("Okay.", r -> then.run()))));
    }

    /** Something untrue about a girl who didn't do it, in with everything else. */
    public static void plant(ServerPlayerEntity p) {
        RunState s = RunManager.state(p.server);
        if (!is(p) || planted.size() >= LEADS) return;
        if (plantedOn == s.day) {
            RunManager.tell(p, "", "Not twice in a day. Someone would notice.");
            return;
        }
        List<Integer> innocent = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.mystery().isYandere(g) && !s.dead.contains(g) && !s.accused.contains(g)) innocent.add(g);
        if (innocent.isEmpty()) return;
        Student decoy = Roster.get(innocent.get(RANDOM.nextInt(innocent.size())));
        String lead = HEARD[RANDOM.nextInt(HEARD.length)] + decoy.hair().phrase(1);
        plantedOn = s.day;
        planted.add(lead);
        s.notes.add(lead);
        s.markDirty();
        Kokuhaku.LOG.info("False lead planted: {}", lead);
        Shadow.saw(s, name + " left a false lead in your bag for her.");
        RunManager.tell(p, "", "It's in with the others now. Nobody saw you.");
    }
}
