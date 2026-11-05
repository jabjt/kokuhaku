package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.world.town.Town;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * You wake at 3:12. You're in bed, it's dark, and something's wrong. Each of
 * these only happens once, you choose what to do, and the brave choice is the
 * one that can find out something true about her, and the one that can get you
 * taken. Alone only: nobody else can be left waiting for you to decide.
 */
final class Nightmares {
    private static final int FIRST_DAY = 2, WAKE_TICKS = 90, SEEN_OFFSET = 100;
    private static final float CHANCE = 0.3f, EXTRA_DANGER = 2f;
    private static final Random RANDOM = new Random();
    private static final String TIME = "3:12";

    private enum Terror { HANDLE, WINDOW, UNDER_BED, BESIDE_YOU, FRONT_DOOR, BREATHING }

    private Nightmares() {
    }

    /** Runs one, then carries on with the night. Returns false if tonight's a quiet one. */
    static boolean play(ServerPlayerEntity p, RunState s, Town.Home home, Runnable morning) {
        if (s.party.size() != 1 || s.day < FIRST_DAY || Living.active(s) || Ward.isAdmitted(p)) return false;
        String wanted = System.getenv("KOKUHAKU_TERROR");
        if (wanted == null && RANDOM.nextFloat() > CHANCE) return false;
        List<Terror> left = new ArrayList<>();
        for (Terror t : Terror.values()) if (!s.scenarios.contains(SEEN_OFFSET + t.ordinal())) left.add(t);
        if (left.isEmpty()) return false;
        Terror terror = left.get(RANDOM.nextInt(left.size()));
        if (wanted != null) terror = Terror.valueOf(wanted);
        s.scenarios.add(SEEN_OFFSET + terror.ordinal());
        s.markDirty();
        Kokuhaku.LOG.info("Nightmare: {}", terror);
        Terror chosen = terror;
        Vec3d look = home.bedCenter().add(new Vec3d(home.cameraSpot().x - home.bedCenter().x, 0, home.cameraSpot().z - home.bedCenter().z).normalize().multiply(2.5)).add(0, 0.9, 0);
        RunManager.sendDark(p, home, look, WAKE_TICKS);
        RunManager.later(WAKE_TICKS / 2, () -> sound(p, soundOf(chosen)));
        RunManager.later(WAKE_TICKS, () -> scene(p, s, chosen, morning));
        return true;
    }

    private static SoundEvent soundOf(Terror t) {
        return switch (t) {
            case HANDLE, FRONT_DOOR -> SoundEvents.BLOCK_WOODEN_DOOR_CLOSE;
            case WINDOW -> SoundEvents.BLOCK_GLASS_HIT;
            default -> SoundEvents.ENTITY_WARDEN_HEARTBEAT;
        };
    }

    private static void sound(ServerPlayerEntity p, SoundEvent event) {
        ServerWorld world = p.getServerWorld();
        world.playSound(null, p.getX(), p.getY(), p.getZ(), event, SoundCategory.AMBIENT, 0.5f, 0.6f);
    }

    /** What you found, in your bag in the morning: true, and about her. */
    private static void learn(ServerPlayerEntity p, RunState s, String what) {
        s.notes.add(what);
        s.markDirty();
        Kokuhaku.LOG.info("Nightmare clue: {}", what);
    }

    /** The door was open and you drew attention to yourself. She doesn't need asking twice. */
    private static boolean takenBy(ServerPlayerEntity p, RunState s) {
        return !s.doorLocked[0] && RANDOM.nextFloat() < RunManager.abductChance(s) * EXTRA_DANGER;
    }

    private static void scene(ServerPlayerEntity p, RunState s, Terror t, Runnable morning) {
        Student her = Roster.get(s.freeYandere());
        String name = TIME;
        java.util.function.Consumer<ServerPlayerEntity> goOn = q -> morning.run();
        switch (t) {
            case HANDLE -> Talk.say(p, name, "You're awake and you don't know why. Then you hear it. Your door handle is turning, very slowly, all the way down. It stops. It stays there.",
                    Talk.option("Hold your breath.", q -> Talk.say(q, name, "It stays there for a long time. Then, just as slowly, it comes back up. The floorboards don't creak. Whoever it is knows which ones to miss.", Talk.option("...", goOn))),
                    Talk.option("Look at the gap under the door.", q -> {
                        learn(q, s, "Under your door at 3:12: two feet, not moving. " + her.length().phrase(1));
                        Talk.say(q, name, "Two shadows in the strip of light. Feet. They don't move. Then something slides out of the dark and stops by your bed: a single hair. You'll look at it in the morning.", Talk.option("...", goOn));
                    }),
                    Talk.option("Say \"Who's there?\"", q -> {
                        if (takenBy(q, s)) {
                            RunManager.takenInTheNight(q, s);
                            return;
                        }
                        s.fondness[her.id()] = Math.min(5, s.fondness[her.id()] + 1);
                        s.markDirty();
                        Talk.say(q, name, "Silence. And then, right against the door, so quiet you might have imagined it: \"You said something to me.\" Somebody on the other side is crying with happiness.", Talk.option("...", goOn));
                    }));
            case WINDOW -> Talk.say(p, name, "Tapping. Fingernails on the glass, slow and even. Your curtains are closed. Your room is not on the ground floor.",
                    Talk.option("Pull the covers over your head.", q -> Talk.say(q, name, "It goes on until the birds start. It never once speeds up.", Talk.option("...", goOn))),
                    Talk.option("Open the curtains.", q -> {
                        learn(q, s, "At your window at 3:12: " + her.hair().phrase(1));
                        Talk.say(q, name, "Nobody. Only your own breath on the glass, and, on the outside, a word written with a finger. It says your name. There's a hair stuck in it.", Talk.option("...", goOn));
                    }));
            case UNDER_BED -> Talk.say(p, name, "You're nearly asleep when you feel it. A small, patient breath, from under the bed. It isn't yours. It's slower than yours.",
                    Talk.option("Lean over and look.", q -> {
                        learn(q, s, "Under your bed: a nest of folded blankets, still warm. " + s.mystery().districts[her.id()].phrase(2));
                        Talk.say(q, name, "Nothing's there now. There's a folded blanket, though, and a hollow in it the shape of someone small. It's warm. How long has this been here?", Talk.option("...", goOn));
                    }),
                    Talk.option("Stay perfectly still.", q -> Talk.say(q, name, "After an hour it sighs, the way you sigh when you're happy. You don't sleep again.", Talk.option("...", goOn))));
            case BESIDE_YOU -> Talk.say(p, name, "Someone is lying next to you. You can feel the bed dip. An arm comes slowly round you. It's very gentle. A voice in your hair says: \"Don't turn round. Not yet.\"",
                    Talk.option("Turn round.", q -> Talk.say(q, name, "There's nobody there. The other side of the bed is warm, and the pillow has been kissed.", Talk.option("...", goOn))),
                    Talk.option("Don't turn round.", q -> {
                        s.fondness[her.id()] = Math.min(5, s.fondness[her.id()] + 2);
                        s.markDirty();
                        Talk.say(q, name, "\"Good,\" it says, and the arm tightens, just a little, and then you're alone. You will never be sure you were awake.", Talk.option("...", goOn));
                    }));
            case FRONT_DOOR -> Talk.say(p, name, "You're not in bed. You're standing at the front door of the house in your socks with your hand on the latch. The door's open. There's a pair of shoes outside, neatly side by side, pointing in.",
                    Talk.option("Shut the door.", q -> Talk.say(q, name, "You lock it, go back upstairs, and lie there. When you check in the morning, the shoes are inside.", Talk.option("...", goOn))),
                    Talk.option("Look at the shoes.", q -> {
                        learn(q, s, "Shoes at your door, size written inside in a neat hand. " + her.homeroom().phrase(1));
                        Talk.say(q, name, "School shoes. There's a class label inside, the kind they make you sew in. You pull it out before you've thought about it.", Talk.option("...", goOn));
                    }));
            case BREATHING -> Talk.say(p, name, "The house is completely silent. You've never heard it so quiet. Then you work out why: you've been breathing in time with someone else.",
                    Talk.option("Stop breathing.", q -> Talk.say(q, name, "A moment later, so do they.", Talk.option("...", goOn))),
                    Talk.option("Breathe faster.", q -> Talk.say(q, name, "They keep up. From somewhere in the room you hear a small, delighted laugh.", Talk.option("...", goOn))));
        }
    }
}
