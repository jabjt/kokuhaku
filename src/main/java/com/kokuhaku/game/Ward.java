package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.world.town.Town;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Being hit and getting up again: the hospital, the bill, and who comes to see
 * you if you can't pay it. One girl visits, picked from all of them, so the
 * chance it's the yandere is one in twenty, two in 23 or three in 26. If it
 * is, your bill gets paid, and she has something to hold over you.
 */
public final class Ward {
    private static final int SIREN_TICKS = 90, WAKE_TICKS = 120, VISIT_TICKS = 30, LATER_TICKS = 50;
    private static final int[] STAY_MINUTES = {180, 360}, BILL = {500, 1500};
    /** On Medium the welfare fund pays for some, so a settled bill doesn't prove who settled it. */
    private static final float[] FUND_CHANCE = {0.15f, 0.3f};
    private static final int FIRST_NOTE_AFTER = 3, BETWEEN_NOTES = 4, DEMAND_YEN = 3000;
    private static final Random RANDOM = new Random();
    private static final String DOCTOR = "Dr. Mori", NURSE = "Nurse Ono";
    private static final int DOCTOR_SKIN = 2, NURSE_SKIN = 5;

    private static final double PAST_THE_FOOT = 1.6;
    private static final int TIME_TO_GET_HOME = 150;
    /** Who's in a hospital bed right now. */
    private static final Set<UUID> admitted = new HashSet<>();
    private static final Map<UUID, List<StudentEntity>> people = new HashMap<>();

    private Ward() {
    }

    public static void clear() {
        admitted.clear();
        people.clear();
    }

    public static boolean isAdmitted(ServerPlayerEntity p) {
        return admitted.contains(p.getUuid());
    }

    public static void discharged(ServerPlayerEntity p) {
        admitted.remove(p.getUuid());
        dismiss(p);
    }

    private static void dismiss(ServerPlayerEntity p) {
        List<StudentEntity> here = people.remove(p.getUuid());
        if (here != null) here.forEach(StudentEntity::discard);
    }

    private static StudentEntity stand(ServerPlayerEntity p, int skin, String name, Vec3d at, Vec3d looking) {
        ServerWorld world = p.getServerWorld();
        StudentEntity e = Kokuhaku.STUDENT.create(world);
        if (e == null) return null;
        float yaw = (float) (Math.toDegrees(Math.atan2(looking.z - at.z, looking.x - at.x)) - 90);
        e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        e.setSkin(skin);
        if (name != null) e.setCustomName(Text.literal(name));
        e.setAiDisabled(true);
        world.spawnEntity(e);
        people.computeIfAbsent(p.getUuid(), k -> new ArrayList<>()).add(e);
        return e;
    }

    /** Where anyone who's come to see you stands, so you can actually see them from the pillow. */
    private static Vec3d footOfBed(Town.Home bed) {
        BlockPos head = bed.bedHead(), foot = bed.bedFoot();
        return Vec3d.ofBottomCenter(foot).add((foot.getX() - head.getX()) * PAST_THE_FOOT, 0, (foot.getZ() - head.getZ()) * PAST_THE_FOOT);
    }

    /** Off the road and into a bed. The whole thing happens around you; you only wake up for the end of it. */
    static void admit(ServerPlayerEntity p, RunState s, String what) {
        int level = s.difficulty.ordinal();
        int bill = BILL[level];
        s.yen -= bill;
        s.markDirty();
        admitted.add(p.getUuid());
        boolean alone = s.party.size() == 1;
        RunManager.holdClock(alone);
        RunManager.setBusy(p, true);
        Town.Home bed = RunManager.ward(s, p);
        Vec3d stand = RunManager.bedsideOf(p, bed);
        Vec3d foot = footOfBed(bed);
        RunManager.sendBlack(p, bed, SIREN_TICKS, "...\n\nA siren, a long way off.\nSomeone keeps saying your name.");
        RunManager.later(4, () -> p.teleport(p.getServerWorld(), stand.x, stand.y, stand.z, p.getYaw(), 0f));
        RunManager.later(SIREN_TICKS, () -> {
            Vec3d head = bed.bedCenter();
            Vec3d doctor = foot;
            Vec3d nurse = foot.add(head.subtract(foot).normalize().multiply(-1.4)).add(1.0, 0, 0.3);
            stand(p, DOCTOR_SKIN + Roster.TOTAL, DOCTOR, doctor, head);
            stand(p, NURSE_SKIN + Roster.TOTAL, NURSE, nurse, head);
            RunManager.sendWard(p, bed, doctor.add(0, 1.5, 0), WAKE_TICKS);
        });
        RunManager.later(SIREN_TICKS + WAKE_TICKS, () -> doctor(p, s, what, bill));
    }

    private static void doctor(ServerPlayerEntity p, RunState s, String what, int bill) {
        Kokuhaku.LOG.info("{} woke in hospital after a {}: bill {}, now {} yen", p.getGameProfile().getName(), what, bill, s.yen);
        String wound = "train".equals(what) ? "The driver saw you at the last second. You're luckier than you know." : "The driver swears you walked straight out in front of him.";
        Talk.say(p, DOCTOR, "Easy. Don't try to sit up. You were hit by a " + what + ". " + wound,
                Talk.option("How bad is it?", q -> Talk.say(q, DOCTOR, "Nothing broken, somehow. You'll limp until you've had a proper night's sleep. Use the crossings next time. That's what they're for.",
                        Talk.option("Thank you.", r -> nurse(r, s, bill)))));
    }

    private static void nurse(ServerPlayerEntity p, RunState s, int bill) {
        if (Living.active(s)) {
            s.yen = Math.max(s.yen, 0);
            s.markDirty();
            Talk.say(p, NURSE, "Your... friend has paid. She's waiting. Please don't ask me anything.", Talk.option("Okay.", q -> leave(q, s, false)));
            return;
        }
        if (s.yen >= 0) {
            Talk.say(p, NURSE, "That's ¥" + bill + " for the treatment. It's come out of your money. You've ¥" + s.yen + " left.",
                    Talk.option("Okay.", q -> leave(q, s, false)));
            return;
        }
        Talk.say(p, NURSE, "It's ¥" + bill + " for the treatment, and you haven't got it. So that's a debt of ¥" + (-s.yen) + " in your name. Anything you earn goes against it until it's cleared.",
                Talk.option("I'll pay it back.", q -> Talk.say(q, NURSE, "They all say that. Rest. Somebody's asked to see you.",
                        Talk.option("Who?", r -> visitor(r, s)))));
    }

    /** Anyone at all might come. Most of the time, it's only a girl from school. */
    private static void visitor(ServerPlayerEntity p, RunState s) {
        List<Integer> girls = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.dead.contains(g) && !s.caught.contains(g)) girls.add(g);
        int forced = System.getenv("KOKUHAKU_VISITOR") == null ? -1 : "her".equals(System.getenv("KOKUHAKU_VISITOR")) ? s.freeYandere() : girls.stream().filter(g -> !s.mystery().isYandere(g)).findFirst().orElse(0);
        int girl = forced >= 0 ? forced : girls.get(RANDOM.nextInt(girls.size()));
        boolean her = s.mystery().isYandere(girl);
        Student who = Roster.get(girl);
        Town.Home bed = RunManager.ward(s, p);
        dismiss(p);
        Vec3d head = bed.bedCenter();
        Vec3d chair = footOfBed(bed);
        Kokuhaku.LOG.info("{} came to the hospital (yandere: {})", who.firstName(), her);
        if (her) Shadow.saw(s, "She came to see you in hospital. She counted your breaths.");
        RunManager.sendBlack(p, bed, VISIT_TICKS, "");
        RunManager.later(VISIT_TICKS, () -> {
            StudentEntity e = stand(p, girl, who.name(), chair, head);
            RunManager.sendWard(p, bed, chair.add(0, 1.5, 0), WAKE_TICKS / 2);
            RunManager.later(WAKE_TICKS / 2, () -> visit(p, s, who, her, e == null ? -1 : e.getId()));
        });
    }

    private static void visit(ServerPlayerEntity p, RunState s, Student who, boolean her, int speaker) {
        s.known.add(who.id());
        s.markDirty();
        String[] lines = visiting(who.personality());
        Talk.say(p, who.firstName(), lines[0], speaker,
                Talk.option("You didn't have to come.", q -> Talk.say(q, who.firstName(), lines[1], speaker,
                        Talk.option("Why are you here?", r -> Talk.say(r, who.firstName(), lines[2], speaker,
                                Talk.option("Thank you.", t -> afterVisit(t, s, who, her))))
                        , Talk.option("Say nothing.", r -> afterVisit(r, s, who, her)))),
                Talk.option("How did you know I was here?", q -> Talk.say(q, who.firstName(), lines[3], speaker,
                        Talk.option("Oh.", r -> afterVisit(r, s, who, her)))));
    }

    /** She tells you what she knows, if she knows anything, the way anyone would. If it's her, she takes care of the rest. */
    private static void afterVisit(ServerPlayerEntity p, RunState s, Student who, boolean her) {
        s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + 1);
        boolean paid = false;
        if (her) {
            s.yen = 0;
            s.blackmailer = who.id();
            s.blackmailDue = s.day + FIRST_NOTE_AFTER;
            paid = true;
        } else if (RANDOM.nextFloat() < FUND_CHANCE[Math.min(1, s.difficulty.ordinal())]) {
            s.yen = 0;
            paid = true;
        }
        s.markDirty();
        boolean settled = paid;
        Talk.say(p, NURSE, settled ? "Good news. Your bill's been settled in full. No, they didn't leave a name. It's not my place to say." : "You can go once you're ready. Don't forget what you owe us.",
                Talk.option("Okay.", q -> leave(q, s, settled)));
    }

    /** Time passes in the bed. Alone it's hours of your day; with a party the others can't be kept waiting. */
    private static void leave(ServerPlayerEntity p, RunState s, boolean paid) {
        dismiss(p);
        if (paid) s.notes.add("Someone paid your hospital bill and didn't leave a name.");
        int level = s.difficulty.ordinal();
        int minutes = s.party.size() == 1 ? STAY_MINUTES[level] : 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 60 * 20, level, false, false, false));
        // Nobody's sent out limping with no chance of getting home in time.
        if (s.party.size() == 1 && s.minute + minutes >= RunManager.curfewOf(s) - TIME_TO_GET_HOME) {
            s.minute = RunManager.curfewOf(s) - 30;
            RunManager.overnight(p, s);
            return;
        }
        RunManager.skipMinutes(p.server, s, minutes);
        Town.Home bed = RunManager.ward(s, p);
        RunManager.sendBlack(p, bed, LATER_TICKS, minutes > 0 ? (minutes / 60) + " hours later" : "");
        RunManager.later(LATER_TICKS, () -> RunManager.getUp(p, s, bed));
    }

    // ------------------------------------------------------------- blackmail

    /** The morning a note is waiting, you get told where to look. */
    static boolean noteDue(RunState s) {
        return s.blackmailer >= 0 && s.blackmailStage < DEMANDS && s.day >= s.blackmailDue;
    }

    private static final int DEMANDS = 3;

    /** Paper smelling of something that's true of her: the note is a threat and a clue at once. */
    private static String tell(RunState s, int stage) {
        Mystery m = s.mystery();
        Student her = Roster.get(s.blackmailer);
        int level = Math.max(1, s.difficulty.ordinal() + 1);
        return switch (stage) {
            case 0 -> m.clubs[her.id()].phrase(level);
            case 1 -> m.districts[her.id()].phrase(level);
            default -> her.hair().phrase(level);
        };
    }

    static void openNote(ServerPlayerEntity p, RunState s) {
        int stage = s.blackmailStage;
        String clue = tell(s, stage);
        Kokuhaku.LOG.info("Blackmail note {} for {}", stage, p.getGameProfile().getName());
        Runnable done = () -> {
            s.blackmailStage++;
            s.blackmailDue = s.day + BETWEEN_NOTES;
            s.notes.add(clue);
            s.markDirty();
            RunManager.tell(p, "", "You keep the envelope. Something about it stays with you. It's in your bag.");
        };
        String name = "The envelope";
        switch (stage) {
            case 0 -> Talk.say(p, name, "\"I paid for you. Every yen. Isn't that what people do for someone they love? Bring ¥" + DEMAND_YEN + " to the shoe lockers. I want to see what you'd give up for me.\"",
                    Talk.option("Leave the money.", q -> {
                        s.yen -= DEMAND_YEN;
                        s.markDirty();
                        RunManager.tell(q, "", "You leave it in the locker. When you look back it's gone.");
                        done.run();
                    }),
                    Talk.option("Tear it up.", q -> refuse(q, s, done, "The next morning, every teacher has a copy of your hospital bill.")));
            case 1 -> Talk.say(p, name, "\"You've been asking about me. I don't mind. But I'd like what you've written down. Leave it, and I'll know you're sorry.\"",
                    Talk.option("Give up two clues.", q -> {
                        burn(s);
                        RunManager.tell(q, "", "You tear out the pages. Your bag feels lighter than it should.");
                        done.run();
                    }),
                    Talk.option("Keep them.", q -> refuse(q, s, done, "That night, the lock on your bedroom door is broken.")));
            default -> Talk.say(p, name, "\"Last thing, and we're even. Leave your bedroom door unlocked tonight. I only want to look at you.\"",
                    Talk.option("Leave it unlocked.", q -> {
                        s.lockBrokenOn = s.day;
                        s.fondness[s.blackmailer] = Math.min(5, s.fondness[s.blackmailer] + 3);
                        s.markDirty();
                        RunManager.tell(q, "", "You've never been so scared of a door.");
                        done.run();
                    }),
                    Talk.option("Lock it.", q -> refuse(q, s, done, "Somebody stood outside your door all night.")));
        }
    }

    /** Saying no always costs something, and she always knows. */
    private static void refuse(ServerPlayerEntity p, RunState s, Runnable done, String cost) {
        s.fondness[s.blackmailer] = Math.max(-5, s.fondness[s.blackmailer] - 2);
        if (cost.contains("lock")) s.lockBrokenOn = s.day;
        else RunManager.addStrike(p.server, s, 2);
        s.markDirty();
        RunManager.tell(p, "\"Wrong answer.\"", cost);
        done.run();
    }

    private static void burn(RunState s) {
        List<Integer> traits = new ArrayList<>();
        for (int id : s.found) if (id > 0 && id < s.mystery().clues.size()) traits.add(id);
        java.util.Collections.shuffle(traits, RANDOM);
        for (int i = 0; i < 2 && i < traits.size(); i++) {
            s.found.remove(traits.get(i));
            s.atScene.add(traits.get(i)); // gone for good: nobody tells you twice
        }
        s.markDirty();
    }

    /** What she says at your bedside. Nothing in it tells you which sort of visitor she is. */
    private static String[] visiting(Personality p) {
        return switch (p) {
            case CHEERFUL -> new String[]{"You're awake! I brought melon bread. I ate one on the way. It was a long way.",
                    "Of course I did! Nobody should wake up in here alone. It smells weird. Have you noticed?",
                    "Because you'd have come for me. Wouldn't you?",
                    "Everybody knows! The whole class saw the ambulance go past."};
            case SHY -> new String[]{"I-I'm sorry. Were you asleep? I can go. I only wanted to see that you were breathing.",
                    "I know. I wanted to. I sat outside for a while before I dared knock.",
                    "...You're one of the only people who ever says hello to me.",
                    "I heard someone talking about it. I... I listen, sometimes. Sorry."};
            case COLD -> new String[]{"You look terrible. Good. Maybe now you'll look where you're going.",
                    "I know. Nobody asked me to, either.",
                    "I was passing. Don't read anything into it.",
                    "It's a small town. There's nothing else to talk about."};
            case GOSSIP -> new String[]{"Don't move, I need to remember this. People have been asking how bad it is, and I intend to exaggerate.",
                    "And miss this? You're the most interesting thing that's happened all week.",
                    "Somebody ought to find out what really happened. Did you step out, or were you pushed?",
                    "Please. I knew before your parents did."};
            case POLITE -> new String[]{"I hope I'm not intruding. I brought you some fruit, and the notes from today's lessons.",
                    "It's no trouble. You'd be behind otherwise, and I'd hate that for you.",
                    "Someone ought to look after you. You don't seem to do it yourself.",
                    "The teacher mentioned it in homeroom. I thought I'd better come and see."};
            case SPORTY -> new String[]{"Hey, champ. Walk it off yet? Kidding. Stay down. You look awful.",
                    "I run past here every morning anyway. Figured I'd stop.",
                    "Team doesn't leave people behind. You're sort of on the team now. Don't argue.",
                    "Saw the lights from the track and followed them. Fast, right?"};
        };
    }
}
