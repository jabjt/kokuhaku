package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * People standing around find each other. Friends first, anyone nearby if not:
 * they walk over, face each other and talk until the bell goes. Walk past and
 * you'll catch a bit of it. Anyone who likes you puts a hand up when they see you.
 */
final class Chatter {
    private static final double REACH = 8, HEARD_AT = 5.5, WAVE_AT = 7;
    private static final float START_CHANCE = 0.3f, KEEPS_TO_THEMSELVES = 0.07f;
    private static final int SHORTEST = 240, LONGEST = 700, HEARD_EVERY = 1200, WAVE_TICKS = 34, LIKES_YOU = 2;
    private static final Random RANDOM = new Random();

    private static final List<List<String>> BY_WEEK = List.of(
            List.of("...did you do the maths? No, the other maths...", "...she said that? To his face?...", "...I'm so tired. Is it Friday? Why isn't it Friday...", "...melon bread, every day, I swear...", "...no, I told you, the one with the glasses..."),
            List.of("...my mum wants me to quit club. She won't say why...", "...they don't tell us anything. You noticed that?...", "...I'm not walking home on my own. Are you?...", "...the teachers keep whispering..."),
            List.of("...it isn't an accident. Three isn't an accident...", "...keep your voice down...", "...I've started locking my window. I'm on the second floor...", "...did you hear something last night?..."),
            List.of("...my parents are looking at other schools...", "...I don't want to talk about it...", "...if it's one of us, then...", "...don't. Not here..."),
            List.of("...it's nearly over. It has to be...", "...I stopped counting...", "...is anyone even coming tomorrow?..."));
    private static final List<String> ABOUT_YOU = List.of("...don't look now. That's the one who's been asking questions...", "...why does everyone around that one end up...", "...shh. Shh, they're right there...", "...I heard someone's been following them home...");

    private static int heardAt = -HEARD_EVERY;
    private static int wavedOn = 0;
    private static final Set<Integer> waved = new HashSet<>();

    private Chatter() {
    }

    static void reset() {
        heardAt = -HEARD_EVERY;
        wavedOn = 0;
        waved.clear();
    }

    private static boolean free(StudentEntity e) {
        return e.getSkin() < Roster.TOTAL && !e.hasRoute() && !e.isSittingDown() && !e.isTalking() && !e.isChatting() && !e.isAiDisabled() && !e.isHeld() && !e.hasVehicle() && !e.isKeptClose();
    }

    private static boolean friends(StudentEntity a, StudentEntity b) {
        return Routines.group(Roster.get(a.getSkin())) == Routines.group(Roster.get(b.getSkin()));
    }

    /** Called every couple of seconds. */
    static void tick(RunState s, List<? extends StudentEntity> all, List<ServerPlayerEntity> players, int ticks) {
        if (Living.active(s) || s.devoted) return;
        pair(all);
        if (wavedOn != s.day) {
            wavedOn = s.day;
            waved.clear();
        }
        for (ServerPlayerEntity p : players) {
            if (LunchScene.active(p)) continue;
            greet(s, p, all);
            hear(s, p, all, ticks);
            Noticed.tick(s, all, p);
        }
    }

    private static void pair(List<? extends StudentEntity> all) {
        List<StudentEntity> idle = new ArrayList<>();
        for (StudentEntity e : all) if (free(e)) idle.add(e);
        for (StudentEntity e : idle) {
            var kind = Roster.get(e.getSkin()).personality();
            boolean alone = kind == Roster.Personality.COLD || kind == Roster.Personality.SHY;
            if (!free(e) || RANDOM.nextFloat() > (alone ? KEEPS_TO_THEMSELVES : START_CHANCE)) continue;
            StudentEntity best = null;
            double closest = REACH * REACH;
            for (StudentEntity o : idle) {
                if (o == e || !free(o)) continue;
                // A friend across the room beats a stranger next to you.
                double d = e.squaredDistanceTo(o) * (friends(e, o) ? 0.3 : 1);
                if (d < closest) {
                    closest = d;
                    best = o;
                }
            }
            if (best != null) e.startChat(best, SHORTEST + RANDOM.nextInt(LONGEST - SHORTEST));
        }
    }

    private static void greet(RunState s, ServerPlayerEntity p, List<? extends StudentEntity> all) {
        for (StudentEntity e : all) {
            int id = e.getSkin();
            if (id >= Roster.TOTAL || e.isSittingDown() || e.isTalking() || e.squaredDistanceTo(p) > WAVE_AT * WAVE_AT || !waved.add(id)) continue;
            boolean likes = s.fondness[id] >= LIKES_YOU || Roster.get(id).personality() == Roster.Personality.CHEERFUL && s.fondness[id] >= 0;
            if (likes && e.canSee(p)) e.wave(WAVE_TICKS);
        }
    }

    private static void hear(RunState s, ServerPlayerEntity p, List<? extends StudentEntity> all, int ticks) {
        if (ticks - heardAt < HEARD_EVERY || RunManager.inScene(p)) return;
        for (StudentEntity e : all) {
            if (!e.isChatting() || e.isTalking() || !e.isSpeaking() || e.squaredDistanceTo(p) > HEARD_AT * HEARD_AT) continue;
            heardAt = ticks;
            List<String> week = BY_WEEK.get(Math.min(BY_WEEK.size() - 1, (s.day - 1) / 3));
            String line = s.day > 3 && RANDOM.nextInt(4) == 0 ? ABOUT_YOU.get(RANDOM.nextInt(ABOUT_YOU.size())) : week.get(RANDOM.nextInt(week.size()));
            p.sendMessage(Text.literal(line).formatted(Formatting.GRAY, Formatting.ITALIC), true);
            if (System.getenv("KOKUHAKU_BOT") != null) Kokuhaku.LOG.info("Heard: {}", line);
            return;
        }
    }
}
