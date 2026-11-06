package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.net.Net;
import com.kokuhaku.world.town.Town;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Your phone. The class chat never shuts up, whoever you're going out with says
 * goodnight, and from the third day there's a number you don't know. It always
 * knows where you are and what you did today. You can answer it, if you like.
 */
public final class Phone {
    private record Message(int day, int minute, String from, String body, boolean mine) {
    }

    private record Reply(String label, String answer, int fondness) {
    }

    private static final String UNKNOWN = "Unknown number", CHAT = "Class 2-A";
    private static final int LUNCH_TEXT = Schedule.LUNCH + 15, AFTER_TEXT = Schedule.AFTER_SCHOOL + 35, LATE_TEXT = Schedule.EVENING + 70;
    private static final int UNKNOWN_FROM = 3, HARUKA_DAY = 9, NEAR_PLACE = 26;
    private static final float UNKNOWN_CHANCE = 0.75f;
    private static final String[][] PLACES = {{"station", "the station"}, {"shops", "the shops"}, {"park", "the park"}, {"beach", "the beach"},
            {"field", "the sports field"}, {"gym", "the gym"}, {"hospital", "the hospital"}, {"center:Library", "the library"}};
    private static final List<String> CHATTER = List.of(
            "does anyone have the maths answers", "who keeps leaving the art room window open", "the vending machine by the gate ate my money again",
            "is it just me or is the caretaker never actually here", "why is the third floor toilet always locked", "someone drew that face on the board again",
            "the last train was running at 3am again. i live by the line", "who took the class photo down from the corridor");
    private static final List<String> AFTER_A_DEATH = List.of(
            "has anyone heard from %s", "they took %s's name off the seating plan already", "please stop posting about %s. her mum is in this chat",
            "the teachers won't say anything about %s", "there were flowers at %s's gate this morning. no card");
    private static final List<String> ANY_DAY = List.of(
            "You look tired. Are you eating?", "I like the way you hold your bag.", "You never look up on the stairs. You should. I'm usually there.",
            "Good night. I'll know if you're not asleep.", "I've saved this number under your whole name. It looks so nice.");
    private static final List<String> LATE_DAYS = List.of(
            "There are fewer people for you to worry about now.", "I've tidied everything up. You'll see.", "Do you still have my note? Don't lie. I can see the corner of it.");
    private static final List<String> AT_HOME = List.of(
            "Your light's on.", "That's a nice shirt. Is it new?", "You should close your curtains properly. Other people might look in.");
    private static final List<String> HER_ROOM = List.of(
            "I'm only downstairs. I just like sending these.", "Who are you looking at?", "Dinner's at seven. I made it the way you like.");
    private static final List<Reply> REPLIES = List.of(
            new Reply("Who is this?", "You know who it is. You've always known.", 1),
            new Reply("Leave me alone.", "Okay. I'll be very quiet. You won't hear me at all.", -1),
            new Reply("How did you get this number?", "You gave it to the school office. I was standing behind you.", 0));

    private static final List<Message> feed = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int unread = 0;
    private static List<Reply> waiting = List.of();

    private Phone() {
    }

    static void clear() {
        feed.clear();
        unread = 0;
        waiting = List.of();
    }

    static int unread() {
        return unread;
    }

    private static void receive(MinecraftServer server, RunState s, String from, String body) {
        feed.add(new Message(s.day, s.minute, from, body, false));
        unread++;
        Kokuhaku.LOG.info("Phone: {}: {}", from, body);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.5f, 0.6f);
            RunManager.later(6, () -> p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.5f, 0.6f));
        }
    }

    /** Someone other than her, or her with something to say at a particular moment. */
    static void text(MinecraftServer server, RunState s, String from, String body) {
        receive(server, s, from, body);
    }

    static void fromHer(MinecraftServer server, RunState s, String body) {
        receive(server, s, herName(s), body);
    }

    /** What her messages come from: a number you don't know, until you're living with her. */
    private static String herName(RunState s) {
        return Living.active(s) ? Roster.get(s.livingWith).firstName() : UNKNOWN;
    }

    private static String pick(List<String> lines) {
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    /** Wherever you are, if it's somewhere with a name. */
    private static String whereYouAre(ServerPlayerEntity p, Town town) {
        for (String[] place : PLACES) {
            BlockPos at = town.graph.pos(town.place(place[0]));
            if (p.getPos().squaredDistanceTo(Vec3d.ofCenter(at)) < NEAR_PLACE * NEAR_PLACE) return place[1];
        }
        return null;
    }

    private static void chat(MinecraftServer server, RunState s) {
        String dead = RunManager.lastDeadName(s);
        if (dead != null && RANDOM.nextBoolean()) receive(server, s, CHAT, String.format(pick(AFTER_A_DEATH), dead));
        else receive(server, s, CHAT, pick(CHATTER));
    }

    private static void hers(MinecraftServer server, RunState s, ServerPlayerEntity p, Town town) {
        if (Living.active(s)) {
            fromHer(server, s, pick(HER_ROOM));
            return;
        }
        if (s.day < UNKNOWN_FROM || Romance.datingHer(s) || RANDOM.nextFloat() > UNKNOWN_CHANCE) return;
        String where = whereYouAre(p, town);
        String talked = Whispers.lastGirl();
        List<String> open = new ArrayList<>();
        if (where != null) open.add("Why are you at " + where + "?");
        if (talked != null) open.add("You talked to " + talked + " today. What did she want?");
        if (RunManager.isHomeNow(p, s)) open.addAll(AT_HOME);
        else open.add(pick(s.day > 6 ? LATE_DAYS : ANY_DAY));
        fromHer(server, s, pick(open));
        waiting = RANDOM.nextBoolean() ? REPLIES : List.of();
    }

    /** Checked each game minute. */
    static void minute(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        if (living.isEmpty() || s.day > Schedule.DAYS || s.devoted && s.minute != AFTER_TEXT) return;
        ServerPlayerEntity p = living.get(0);
        if (s.minute == Festival.INVITE_TEXT && s.day == Festival.DAY - 1) receive(server, s, CHAT, "festival tomorrow!! everyone's getting the 9am train from chinetsu station, come");
        if (s.minute == LUNCH_TEXT && Schedule.isSchoolDay(s.day) && !Living.active(s)) chat(server, s);
        if (s.minute == AFTER_TEXT) hers(server, s, p, town);
        if (s.minute == LATE_TEXT) {
            if (s.day == HARUKA_DAY && !Living.active(s)) {
                receive(server, s, "Haruka M.", "is this still your number? don't go home the way you usually go");
            } else if (s.dating >= 0 && !Living.active(s) && !s.dead.contains(s.dating)) {
                receive(server, s, Roster.get(s.dating).firstName(), "Goodnight. Lock your door, okay? I worry.");
            } else if (s.day >= 4 && RANDOM.nextInt(3) == 0) {
                hers(server, s, p, town);
            }
        }
    }

    /** Developer testing only: there's something to answer. */
    static void devReplies() {
        waiting = REPLIES;
    }

    /** The phone asks again every couple of seconds while it's open, so new messages turn up as they arrive. */
    public static void open(ServerPlayerEntity p, boolean refresh) {
        int fresh = unread;
        unread = 0;
        send(p, fresh, refresh);
    }

    /**
     * Everything the screen shows. Each message's From is the conversation it's in, yours included.
     * Unread is how many of the newest weren't seen before this opening. Devoted is No One Will Save
     * You, where she has your phone and nothing gets out; Watched is how close something is, 0..1.
     * Refresh only updates a phone that's already open, and never takes it out.
     */
    private static void send(ServerPlayerEntity p, int fresh, boolean refresh) {
        RunState s = RunManager.state(p.server);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Refresh", refresh);
        NbtList messages = new NbtList();
        for (Message m : feed) {
            NbtCompound e = new NbtCompound();
            e.putString("When", "Day " + m.day() + ", " + Schedule.clock(m.minute()));
            e.putInt("Day", m.day());
            e.putString("Clock", Schedule.clock(m.minute()));
            e.putString("From", m.from());
            e.putString("Body", m.body());
            e.putBoolean("Mine", m.mine());
            messages.add(e);
        }
        nbt.put("Messages", messages);
        NbtList replies = new NbtList();
        for (Reply r : waiting) replies.add(NbtString.of(r.label()));
        nbt.put("Replies", replies);
        nbt.putString("ReplyIn", herName(s));
        nbt.putInt("Unread", fresh);
        nbt.putBoolean("Devoted", s.devoted);
        nbt.putFloat("Watched", Chase.secondsLeft(p) >= 0 ? 1f : Stalker.closeness(p));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(p, Net.PHONE, buf);
    }

    public static void reply(ServerPlayerEntity p, int option) {
        if (option < 0 || option >= waiting.size()) return;
        RunState s = RunManager.state(p.server);
        Reply r = waiting.get(option);
        waiting = List.of();
        feed.add(new Message(s.day, s.minute, herName(s), r.label(), true));
        int her = s.freeYandere();
        s.fondness[her] = Math.max(-5, Math.min(5, s.fondness[her] + r.fondness()));
        s.markDirty();
        Shadow.saw(s, "You answered her text. She read it forty times.");
        send(p, 0, true);
        RunManager.later(100, () -> fromHer(p.server, s, r.answer()));
    }
}
