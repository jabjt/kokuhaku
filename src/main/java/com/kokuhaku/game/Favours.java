package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Mystery.Clue;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.world.town.Town;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Something to do between clues. Anyone can ask you to find what they've lost.
 * Bring it back and you've earned a little money and their trust, and they'll
 * tell you what they know, even if they wouldn't have told you yet.
 */
final class Favours {
    static final String ASK = "Need a hand with anything?";
    private static final int FIRST_DAY = 2, PAYS = 400, FONDNESS = 2;
    /** What's gone missing, where it is, and how you'd say that. */
    private static final String[][] LOST = {
            {"pencil case", "center:Library", "the library"}, {"gym towel", "gym", "the gym"}, {"lunch money", "center:Cafeteria", "the cafeteria"},
            {"umbrella", "station", "the station"}, {"sketchbook", "center:06", "the art room"}, {"hair clip", "park", "the park"},
            {"notebook", "foyer", "the entrance hall"}, {"bus pass", "shops", "the shops"}, {"good pen", "center:Darkroom", "the darkroom"}};
    private static final Random RANDOM = new Random();

    private static int asker = -1;
    private static boolean found = false;
    private static BlockPos at = null;
    private static String thing = "";
    private static final Set<Integer> done = new HashSet<>();

    private Favours() {
    }

    static void clear() {
        asker = -1;
        found = false;
        at = null;
        done.clear();
    }

    static boolean canAsk(RunState s, Student who) {
        return asker < 0 && s.day >= FIRST_DAY && !done.contains(who.id()) && !Living.active(s);
    }

    /** Where to go next, for the arrow. */
    static BlockPos target(RunState s, ServerWorld world) {
        if (asker < 0) return null;
        if (!found) return at;
        return world.getEntitiesByType(Kokuhaku.STUDENT, e -> e.getSkin() == asker).stream().findFirst().map(StudentEntity::getBlockPos).orElse(null);
    }

    static String objective() {
        if (asker < 0) return null;
        String name = Roster.get(asker).firstName();
        return found ? "Take the " + thing + " back to " + name + "." : "Find " + name + "'s " + thing + ".";
    }

    static void ask(ServerPlayerEntity p, RunState s, Student who, Town town) {
        String[] lost = LOST[RANDOM.nextInt(LOST.length)];
        Talk.say(p, who.firstName(), "Actually, yes. I've lost my " + lost[0] + ". I think it was in " + lost[2] + ". Could you have a look? I'd owe you one.",
                Talk.option("I'll find it.", q -> {
                    asker = who.id();
                    thing = lost[0];
                    found = false;
                    at = town.graph.pos(town.graph.wander(town.place(lost[1]), 3, RANDOM));
                    q.getServerWorld().setBlockState(at, ModBlocks.EVIDENCE.getDefaultState());
                    Kokuhaku.LOG.info("{} lost a {} at {}", who.firstName(), thing, at.toShortString());
                    RunManager.tell(q, "", "Look for it in " + lost[2] + ".");
                }),
                Talk.leave("Not right now."));
    }

    /** Was that what you just picked up? */
    static boolean pickedUp(ServerPlayerEntity p, BlockPos pos) {
        if (at == null || !at.equals(pos) || found) return false;
        found = true;
        p.getServerWorld().breakBlock(pos, false);
        RunManager.tell(p, "You found the " + thing + ".", "Take it back to " + Roster.get(asker).firstName() + ".");
        return true;
    }

    static boolean bringing(Student who) {
        return found && asker == who.id();
    }

    /** They're so relieved they tell you something they'd been keeping to themselves. */
    static void handIn(ServerPlayerEntity p, RunState s, Student who) {
        done.add(who.id());
        asker = -1;
        found = false;
        at = null;
        s.yen += PAYS;
        if (who.female()) s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + FONDNESS);
        s.known.add(who.id());
        Clue held = s.mystery().clues.stream().filter(c -> c.witness() == who.id() && !s.found.contains(c.id())).findFirst().orElse(null);
        s.markDirty();
        String line = "You found it! Thank you. Here, for your trouble: ¥" + PAYS + ".";
        if (held != null) {
            s.found.add(held.id());
            Whispers.foundClue();
            line += " And I owe you this. " + Dialogue.reveal(held);
            RunManager.tell(p, "", "Clue added to your bag");
        }
        Kokuhaku.LOG.info("Favour done for {}, clue {}", who.firstName(), held != null);
        Talk.say(p, who.firstName(), line, Talk.leave("Glad I could help."));
    }
}
