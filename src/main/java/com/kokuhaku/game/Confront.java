package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * You don't name her from a list. You walk up to her and say it, to her face,
 * and you find out what she does. Get it wrong and that's a person you've just
 * said that to, in front of everyone.
 */
final class Confront {
    static final String OPTION = "It's you. It's been you.";
    private static final Random RANDOM = new Random();

    private static final Map<Personality, String> SHE_SMILES = Map.of(
            Personality.CHEERFUL, "She doesn't stop smiling. If anything, she looks relieved. \"I knew you'd be the one to work it out! Isn't that lovely?\"",
            Personality.SHY, "For the first time she looks you right in the eye, and doesn't stammer at all. \"Took you long enough.\"",
            Personality.COLD, "\"Yes.\" She goes back to what she was doing. \"Was that all?\"",
            Personality.GOSSIP, "\"Oh, finally. Do you know how hard it's been, not telling anyone?\"",
            Personality.POLITE, "\"Thank you for telling me in person. That was very considerate of you.\" She bows.",
            Personality.SPORTY, "She laughs, out of breath, as if you've just caught up with her. \"There you are. I've been waiting.\"");

    private static final Map<Personality, List<String>> NOT_HER = Map.of(
            Personality.CHEERFUL, List.of("The smile goes out like a light. She didn't know it could. \"...Why would you say that?\""),
            Personality.SHY, List.of("She goes white. Everyone's looking. \"I-I didn't... I never...\" She can't finish it."),
            Personality.COLD, List.of("\"Say that again.\" You don't. She stares at you until you look away."),
            Personality.GOSSIP, List.of("For once she has nothing to say. By lunchtime, everybody else does."),
            Personality.POLITE, List.of("\"I see.\" She picks up her bag very carefully. \"I think I'd like to go home now.\""),
            Personality.SPORTY, List.of("\"Are you serious? Me?\" She shoves past you. You can hear her crying before she gets to the stairs."));

    private Confront() {
    }

    static boolean canSay(RunState s, Student who) {
        return who.female() && s.guessesLeft > 0 && !s.devoted && !Living.active(s) && !s.accused.contains(who.id()) && !s.dead.contains(who.id());
    }

    static void ask(ServerPlayerEntity p, RunState s, Student who) {
        Talk.say(p, "You", "You could say it. Once it's said, it's said. In front of everyone.",
                Talk.option("Say it.", q -> say(q, s, who)),
                Talk.leave("Not yet."));
    }

    private static void say(ServerPlayerEntity p, RunState s, Student who) {
        String name = who.firstName();
        if (Accomplice.is(p)) {
            RunManager.accuse(p, who.id());
            return;
        }
        boolean her = s.mystery().isYandere(who.id());
        List<String> hurt = NOT_HER.get(who.personality());
        String line = her ? SHE_SMILES.get(who.personality()) : hurt.get(RANDOM.nextInt(hurt.size()));
        if (!her) s.fondness[who.id()] = -5;
        s.markDirty();
        Talk.say(p, name, line, Talk.option("...", q -> RunManager.accuse(q, who.id())));
    }
}
