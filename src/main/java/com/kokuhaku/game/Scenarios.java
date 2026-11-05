package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Things that happen to you. One a day at most, each only once a run, in the
 * gaps between lessons, on the walk home and at the weekend. Every one of them
 * changes something: a clue, a girl's feelings, your money, or your nerve.
 */
final class Scenarios {
    private static final int BREAK = Schedule.PERIOD_START[1] - 8, LUNCH = Schedule.LUNCH + 6, AFTER = Schedule.AFTER_SCHOOL + 12,
            EVENING = Schedule.EVENING + 45, WEEKEND = 11 * 60;
    private static final float CHANCE = 0.55f;
    private static final int FORTUNE_YEN = 100;
    private static final Random RANDOM = new Random();

    private enum Scene { BENTO, PHOTO, SHOES, POLICE, FOOTSTEPS, CONFESSION, ALLY, DIARY_MOVED, SHRINE, NOTE_IN_CLASS, FLOWERS, ROOFTOP }

    private Scenarios() {
    }

    /** Checked each minute. Returns nothing: whatever happens, happens to the first living player. */
    static void tick(ServerPlayerEntity p, RunState s) {
        if (s.scenarioDay == s.day || Living.active(s) || s.day < 2 || Festival.on(s) || LunchScene.active(p)) return;
        boolean school = Schedule.isSchoolDay(s.day);
        int m = s.minute;
        boolean slot = school ? m == BREAK || m == LUNCH || m == AFTER || m == EVENING : m == WEEKEND;
        if (!slot || RANDOM.nextFloat() > CHANCE && System.getenv("KOKUHAKU_SCENARIO") == null) return;
        List<Scene> open = new ArrayList<>();
        for (Scene c : Scene.values()) {
            if (s.scenarios.contains(c.ordinal())) continue;
            boolean fits = switch (c) {
                case BENTO -> m == LUNCH;
                case PHOTO, NOTE_IN_CLASS -> m == BREAK;
                case SHOES, FLOWERS -> m == BREAK || m == AFTER;
                case POLICE -> m == AFTER && !s.dead.isEmpty();
                case FOOTSTEPS -> m == EVENING;
                case CONFESSION -> m == AFTER && girlWhoLikesYou(s) >= 0;
                case ALLY -> m == LUNCH;
                case DIARY_MOVED -> m == EVENING;
                case SHRINE -> !school;
                case ROOFTOP -> m == AFTER && s.day >= 5;
            };
            if (fits) open.add(c);
        }
        if (open.isEmpty()) return;
        String wanted = System.getenv("KOKUHAKU_SCENARIO");
        Scene c = open.get(RANDOM.nextInt(open.size()));
        if (wanted != null) for (Scene o : open) if (o.name().equals(wanted)) c = o;
        s.scenarios.add(c.ordinal());
        s.scenarioDay = s.day;
        s.markDirty();
        Kokuhaku.LOG.info("Scenario on day {}: {}", s.day, c);
        switch (c) {
            case BENTO -> Shadow.saw(s, "She got up at five to make that bento. She'd been learning what you like for weeks.");
            case PHOTO -> Shadow.saw(s, "She took that photo from the foot of your bed.");
            case SHOES -> Shadow.saw(s, "She laid every petal in your shoes by hand.");
            case DIARY_MOVED -> Shadow.saw(s, "She let herself in, tidied your books and lay on your pillow for a while.");
            case FOOTSTEPS -> Shadow.saw(s, "Those were her footsteps behind you in the dark.");
            default -> {
            }
        }
        play(c, p, s);
    }

    private static int girlWhoLikesYou(RunState s) {
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) {
            if (s.fondness[g] >= 3 && !s.dead.contains(g) && !s.mystery().isYandere(g)) return g;
        }
        return -1;
    }

    private static int freeGirl(RunState s) {
        List<Integer> girls = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.dead.contains(g) && !s.mystery().isYandere(g)) girls.add(g);
        return girls.get(RANDOM.nextInt(girls.size()));
    }

    private static void fond(RunState s, int girl, int change) {
        s.fondness[girl] = Math.max(-5, Math.min(5, s.fondness[girl] + change));
        s.markDirty();
    }

    private static void play(Scene c, ServerPlayerEntity p, RunState s) {
        int her = s.freeYandere();
        switch (c) {
            case BENTO -> Talk.say(p, "On your desk", "A bento box, wrapped in cloth. There's no name on it. It's still warm. Inside, everything you like, and nothing you don't.",
                    Talk.option("Eat it.", q -> {
                        fond(s, her, 2);
                        RunManager.tell(q, "", "It's the best thing you've ever eaten. How did they know?");
                    }),
                    Talk.option("Leave it.", q -> {
                        fond(s, her, -1);
                        RunManager.tell(q, "", "When you come back it's gone. There's one grain of rice on your chair.");
                    }));
            case PHOTO -> Talk.say(p, "In your bag", "A photograph you didn't put there. It's you, asleep, taken from the foot of your bed.",
                    Talk.option("Keep it.", q -> {
                        s.notes.add("A photo of you asleep. It was taken from inside your room.");
                        s.markDirty();
                        RunManager.tell(q, "", "It's in your bag now. Lock your door.");
                    }),
                    Talk.option("Burn it.", q -> RunManager.tell(q, "", "It doesn't help. You've seen it now.")));
            case SHOES -> Talk.say(p, "Your shoes", "Someone's filled your outdoor shoes with white petals. They've been arranged, one by one, in a spiral.",
                    Talk.option("Shake them out.", q -> {
                        fond(s, her, -1);
                        RunManager.tell(q, "", "As you shake them out, you feel someone stop watching.");
                    }),
                    Talk.option("Wear them anyway.", q -> {
                        fond(s, her, 1);
                        RunManager.tell(q, "", "Petals crush under your heels all day. Somewhere, someone's delighted.");
                    }));
            case FLOWERS -> {
                int g = freeGirl(s);
                Talk.say(p, "On a desk", "Fresh flowers in a vase on " + Roster.get(g).firstName() + "'s desk. It's what they do for someone who's died. She's sitting right there, staring at them. She's crying.",
                        Talk.option("Take them away.", q -> {
                            fond(s, g, 2);
                            RunManager.tell(q, "", Roster.get(g).firstName() + " looks up at you. She doesn't say thank you. She doesn't need to.");
                        }),
                        Talk.option("Leave them.", q -> RunManager.tell(q, "", "Nobody in the room is looking at her. Everybody in the room knows.")));
            }
            case NOTE_IN_CLASS -> {
                int g = freeGirl(s);
                Talk.say(p, "Passed along the row", "A folded note comes down the row: \"Whoever you're looking for sits closer than you think. Don't look up. - " + Roster.get(g).firstName().charAt(0) + ".\"",
                        Talk.option("Look up.", q -> RunManager.tell(q, "", "Twenty faces look straight down at their desks. All of them, at the same moment.")),
                        Talk.option("Keep your head down.", q -> {
                            s.known.add(g);
                            s.markDirty();
                            RunManager.tell(q, "", Roster.get(g).firstName() + " catches you at the door and tells you about herself. Her profile's in your bag.");
                        }));
            }
            case POLICE -> {
                Student dead = Roster.get(s.dead.iterator().next());
                Talk.say(p, "Detective", "You knew " + dead.firstName() + ", didn't you? Sit down. Did you see anyone hanging around the school gate that week?",
                        Talk.option("I got a note.", q -> Talk.say(q, "Detective", "A note. ...Kid, I've been doing this twenty-two years. I was here for the last one. Keep that note, and stay out of the hospital's top floor.",
                                Talk.leave("Why?"))),
                        Talk.option("I didn't see anything.", q -> Talk.say(q, "Detective", "No. Nobody ever does. Mind how you go.", Talk.leave("Okay."))));
            }
            case FOOTSTEPS -> Talk.say(p, "Behind you", "It's dark. There are footsteps behind you. They stop when you stop. They start when you start.",
                    Talk.option("Turn round.", q -> {
                        boolean seen = RANDOM.nextBoolean();
                        if (seen) {
                            s.notes.add("Someone followed you home. " + Roster.get(her).hair().phrase(1));
                            s.markDirty();
                        }
                        RunManager.tell(q, "", seen ? "A figure slips round the corner, but not before the streetlight catches their hair. You write it down." : "There's nobody there. There's nobody there at all.");
                    }),
                    Talk.option("Don't look. Keep walking.", q -> RunManager.tell(q, "", "They follow you all the way to your street. Then, a little further.")));
            case CONFESSION -> {
                int g = girlWhoLikesYou(s);
                Student who = Roster.get(g);
                Talk.say(p, who.firstName(), "Can I say something? Behind the gym, where nobody can see. I... I wrote this out and now I can't read it. I like you. I really like you. That's it. That's the whole thing.",
                        Talk.option("I like you too.", q -> {
                            fond(s, g, 2);
                            fond(s, her, -2);
                            s.livingVictim = g;
                            s.markDirty();
                            Talk.say(q, who.firstName(), "Really? I... Really? Okay. Okay! Don't tell anyone yet. I think someone's watching us.", Talk.leave("Okay."));
                        }),
                        Talk.option("I'm sorry.", q -> {
                            fond(s, g, -2);
                            fond(s, her, 1);
                            Talk.say(q, who.firstName(), "No. No, it's fine. It's fine. Forget I said it.", Talk.leave("..."));
                        }));
            }
            case ALLY -> {
                int boy = Roster.GIRLS + RANDOM.nextInt(Roster.boysFor(s.party.size()));
                String name = Roster.get(boy).firstName();
                Talk.say(p, name, "You've been asking around. So was my brother, three years ago. Go to the library, the darkroom, the station, the park and the beach. She left pages. She left them for someone like you.",
                        Talk.option("Who did?", q -> Talk.say(q, name, "Haruka. They're the only thing left of her. You'll know one when you see it. Don't let anyone see you reading.", Talk.leave("Thanks."))),
                        Talk.leave("Leave me alone."));
            }
            case DIARY_MOVED -> Talk.say(p, "At home, later", "You get the feeling something's different. Your school books are stacked in size order. You never do that. Your pillow's been turned over. It's still warm.",
                    Talk.option("Check the window.", q -> RunManager.tell(q, "", "Locked. From the inside.")),
                    Talk.option("Try not to think about it.", q -> RunManager.tell(q, "", "You do try.")));
            case SHRINE -> Talk.say(p, "A paper slip", "An old woman on the street presses a fortune slip at you. \"Fortunes, ¥" + FORTUNE_YEN + ". Everyone at your school's been buying them. One girl bought all of mine.\"",
                    Talk.option("Pay ¥" + FORTUNE_YEN + ".", q -> {
                        s.yen -= FORTUNE_YEN;
                        int g = freeGirl(s);
                        s.notes.add("Fortune slip: \"Not " + Roster.get(g).firstName() + ". Look nearer.\"");
                        s.markDirty();
                        RunManager.tell(q, "", "\"Not " + Roster.get(g).firstName() + ". Look nearer.\" It's in your bag.");
                    }),
                    Talk.leave("No, thank you."));
            case ROOFTOP -> Talk.say(p, "Your locker", "A note that isn't like the others. Different hand. \"I know what she is. She's looking at you the way she looked at my sister. Run if you can. Don't answer this.\"",
                    Talk.option("Keep it.", q -> {
                        s.notes.add("A warning, in a different hand: \"I know what she is. Run if you can.\"");
                        s.markDirty();
                        RunManager.tell(q, "", "Someone else knows. You'll never find out who.");
                    }),
                    Talk.leave("Put it back."));
        }
    }

    /** One line for the morning, about what went on while you were asleep. */
    static String night(RunState s, boolean locked, boolean brokenLock) {
        if (s.day < 3) return "";
        List<String> lines = new ArrayList<>();
        if (brokenLock) lines.add("The lock on your door is scratched. You didn't do that.");
        else if (locked) lines.add("At 3:12 someone tried your door handle, slowly. The lock held.");
        else lines.add("You woke up and the front door was open. You remember locking it.");
        lines.add("Something tapped on your window for a long time. You're upstairs.");
        lines.add("You heard someone breathing. It stopped when you held your breath.");
        lines.add("A train went past, long after the last one.");
        return RANDOM.nextInt(3) == 0 ? lines.get(RANDOM.nextInt(lines.size())) : "";
    }
}
