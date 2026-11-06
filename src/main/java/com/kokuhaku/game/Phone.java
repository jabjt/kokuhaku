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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Your phone. The class chat never shuts up, whoever you're going out with says
 * goodnight, and from the third day there's a number you don't know. It always
 * knows where you are and what you did today. You can answer it, if you like.
 *
 * She types before she sends, and stops, and starts again; some nights she types
 * and nothing comes. Some of what she sends she takes back a minute later, and late
 * in the month something you sent is gone too, though you never deleted it. She
 * sends photos of you, taken from behind. The dead post in the class chat now and
 * then, and so do ghosts, a few words at a time, most of the letters lost.
 */
public final class Phone {
    /** One message. A deleted one keeps its place in the thread; a photo carries the id of the picture each phone took. */
    private static final class Message {
        final int day, minute, photo;
        final String from, body, sender;
        final boolean mine, dead;
        boolean deleted;

        Message(int day, int minute, String from, String body, boolean mine, String sender, boolean dead, int photo) {
            this.day = day;
            this.minute = minute;
            this.from = from;
            this.body = body;
            this.mine = mine;
            this.sender = sender;
            this.dead = dead;
            this.photo = photo;
        }
    }

    private record Reply(String label, String answer, int fondness) {
    }

    private static final String UNKNOWN = "Unknown number", CHAT = "Class 2-A";
    private static final int LUNCH_TEXT = Schedule.LUNCH + 15, AFTER_TEXT = Schedule.AFTER_SCHOOL + 35, LATE_TEXT = Schedule.EVENING + 70;
    private static final int PHOTO_TEXT = AFTER_TEXT + 50, SILENT_TEXT = LATE_TEXT + 40, UNSEND_AT = Schedule.EVENING;
    private static final int UNKNOWN_FROM = 3, HARUKA_DAY = 9, NEAR_PLACE = 26;
    /** The days things start: she takes messages back, sends photos, types and sends nothing, unsends yours, the dead post. */
    private static final int DELETES_FROM = 5, PHOTOS_FROM = 4, SILENT_FROM = 5, UNSENDS_FROM = 8, DEAD_POST_FROM = 6;
    private static final float UNKNOWN_CHANCE = 0.75f, DELETE_CHANCE = 0.25f;
    /** In ticks: how long before a photo she took arrives, and how often a ghost can post. */
    private static final int PHOTO_DELAY = 20 * 45, GHOST_EVERY = 20 * 30;
    private static final String[][] PLACES = {{"station", "the station"}, {"shops", "the shops"}, {"park", "the park"}, {"beach", "the beach"},
            {"field", "the sports field"}, {"gym", "the gym"}, {"hospital", "the hospital"}, {"center:Library", "the library"}};
    private static final List<String> CHATTER = List.of(
            "does anyone have the maths answers", "who keeps leaving the art room window open", "the vending machine by the gate ate my money again",
            "is it just me or is the caretaker never actually here", "why is the third floor toilet always locked", "someone drew that face on the board again",
            "the last train was running at 3am again. i live by the line", "who took the class photo down from the corridor");
    private static final List<String> AFTER_A_DEATH = List.of(
            "has anyone heard from %s", "they took %s's name off the seating plan already", "please stop posting about %s. her mum is in this chat",
            "the teachers won't say anything about %s", "there were flowers at %s's gate this morning. no card");
    /** From a classmate who's dead. Nobody answers these. */
    private static final List<String> FROM_THE_DEAD = List.of(
            "is anyone still at school", "why did everyone go home", "it's so cold in here", "can someone come and get me", "i can hear you all talking");
    private static final List<String> ANY_DAY = List.of(
            "You look tired. Are you eating?", "I like the way you hold your bag.", "You never look up on the stairs. You should. I'm usually there.",
            "Good night. I'll know if you're not asleep.", "I've saved this number under your whole name. It looks so nice.");
    private static final List<String> LATE_DAYS = List.of(
            "There are fewer people for you to worry about now.", "I've tidied everything up. You'll see.", "Do you still have my note? Don't lie. I can see the corner of it.");
    private static final List<String> AT_HOME = List.of(
            "Your light's on.", "That's a nice shirt. Is it new?", "You should close your curtains properly. Other people might look in.");
    private static final List<String> HER_ROOM = List.of(
            "I'm only downstairs. I just like sending these.", "Who are you looking at?", "Dinner's at seven. I made it the way you like.");
    private static final List<String> CAPTIONS = List.of(
            "You didn't see me.", "I was so close.", "You walk so nicely when you think no one's there.", "Turn around next time.", "I'm keeping this one.");
    private static final List<Reply> REPLIES = List.of(
            new Reply("Who is this?", "You know who it is. You've always known.", 1),
            new Reply("Leave me alone.", "Okay. I'll be very quiet. You won't hear me at all.", -1),
            new Reply("How did you get this number?", "You gave it to the school office. I was standing behind you.", 0));
    /** All a ghost can say. Nothing that names anyone: the dead can't remember her face. */
    public static final List<String> GHOST_WORDS = List.of(
            "behind", "you", "she", "her", "is", "not", "don't", "go", "run", "home", "now", "alone", "trust", "watching",
            "locker", "lies", "stay", "help", "late", "sorry");
    public static final int GHOST_MOST = 4;

    private static final List<Message> feed = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Integer> ghostSaid = new HashMap<>();
    private static int unread = 0, photos = 0;
    private static List<Reply> waiting = List.of();
    /** Where she's typing, until when (server ticks), and the seed for when she stops and starts. */
    private static String typingIn;
    private static int typingUntil;
    private static long typingSeed;

    private Phone() {
    }

    static void clear() {
        feed.clear();
        unread = 0;
        waiting = List.of();
        typingIn = null;
        ghostSaid.clear();
    }

    static int unread() {
        return unread;
    }

    private static Message receive(MinecraftServer server, RunState s, String from, String body, String sender, boolean dead, int photo) {
        Message m = new Message(s.day, s.minute, from, body, false, sender, dead, photo);
        feed.add(m);
        unread++;
        Kokuhaku.LOG.info("Phone: {}{}: {}", from, sender.isEmpty() ? "" : " (" + sender + ")", photo >= 0 ? "[photo] " + body : body);
        PacketByteBuf ping = PacketByteBufs.create();
        ping.writeString(from);
        ping.writeString(sender);
        ping.writeString(body);
        ping.writeBoolean(dead);
        ping.writeBoolean(photo >= 0);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.5f, 0.6f);
            RunManager.later(6, () -> p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.5f, 0.6f));
            ServerPlayNetworking.send(p, Net.PHONE_PING, new PacketByteBuf(ping.copy()));
        }
        return m;
    }

    /** Someone other than her, or her with something to say at a particular moment. */
    static void text(MinecraftServer server, RunState s, String from, String body) {
        if (from.equals(CHAT)) classChat(server, s, body, null);
        else receive(server, s, from, body, "", false, -1);
    }

    static void fromHer(MinecraftServer server, RunState s, String body) {
        type(server, s, body, -1, null);
    }

    static void fromHer(MinecraftServer server, RunState s, String body, Runnable then) {
        type(server, s, body, -1, then);
    }

    /**
     * She types in her chat for a few seconds, on and off, then it arrives; with no body,
     * nothing ever does. Then runs once it's arrived. Now and then, a minute later, she
     * takes it back.
     */
    private static void type(MinecraftServer server, RunState s, String body, int photo, Runnable then) {
        String chat = herName(s);
        if (typingIn != null) {
            // Already typing something else: this one just arrives.
            if (body != null) receive(server, s, chat, body, "", false, photo);
            if (then != null) then.run();
            return;
        }
        int ticks = body == null ? 140 + RANDOM.nextInt(160) : 80 + Math.min(160, body.length() * 3) + RANDOM.nextInt(60);
        typingIn = chat;
        typingUntil = server.getTicks() + ticks;
        typingSeed = RANDOM.nextLong();
        RunManager.later(ticks, () -> {
            typingIn = null;
            if (body == null) return;
            Message m = receive(server, s, chat, body, "", false, photo);
            if (then != null) then.run();
            if (s.day >= DELETES_FROM && photo < 0 && RANDOM.nextFloat() < DELETE_CHANCE) RunManager.later(400 + RANDOM.nextInt(800), () -> m.deleted = true);
        });
    }

    /** What her messages come from: a number you don't know, until you're living with her. */
    private static String herName(RunState s) {
        return Living.active(s) ? Roster.get(s.livingWith).firstName() : UNKNOWN;
    }

    private static String pick(List<String> lines) {
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    /** As people have each other in their phones: first name and an initial. */
    private static String shortName(Roster.Student st) {
        String[] n = st.name().split(" ");
        return n.length > 1 ? n[0] + " " + n[n.length - 1].charAt(0) + "." : n[0];
    }

    /** A classmate who's still about, never the one named in the message. */
    private static String someone(RunState s, String notNamed) {
        List<Roster.Student> about = new ArrayList<>();
        for (int id : Roster.active(Math.max(1, s.party.size()))) {
            Roster.Student st = Roster.get(id);
            if (!s.dead.contains(id) && !s.caught.contains(id) && !st.firstName().equals(notNamed)) about.add(st);
        }
        return about.isEmpty() ? "" : shortName(about.get(RANDOM.nextInt(about.size())));
    }

    private static void classChat(MinecraftServer server, RunState s, String body, String about) {
        receive(server, s, CHAT, body, someone(s, about), false, -1);
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
        if (!s.dead.isEmpty() && s.day >= DEAD_POST_FROM && RANDOM.nextInt(5) == 0) {
            List<Integer> dead = new ArrayList<>(s.dead);
            receive(server, s, CHAT, pick(FROM_THE_DEAD), shortName(Roster.get(dead.get(RANDOM.nextInt(dead.size())))), false, -1);
            return;
        }
        String dead = RunManager.lastDeadName(s);
        if (dead != null && RANDOM.nextBoolean()) classChat(server, s, String.format(pick(AFTER_A_DEATH), dead), dead);
        else classChat(server, s, pick(CHATTER), null);
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
        boolean ask = RANDOM.nextBoolean();
        fromHer(server, s, pick(open), () -> waiting = ask ? REPLIES : List.of());
    }

    private static boolean hasChat(String name) {
        for (Message m : feed) if (m.from.equals(name)) return true;
        return false;
    }

    /** Each phone photographs its owner from behind; a while later she sends it. */
    static void photo(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, int delay) {
        int id = ++photos;
        for (ServerPlayerEntity p : living) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(id);
            ServerPlayNetworking.send(p, Net.PHOTO_TAKE, buf);
        }
        RunManager.later(delay, () -> type(server, s, pick(CAPTIONS), id, null));
    }

    /** Something you sent is gone. You didn't delete it. */
    private static void unsendOneOfYours() {
        List<Message> yours = new ArrayList<>();
        for (Message m : feed) if (m.mine && !m.deleted) yours.add(m);
        if (!yours.isEmpty()) yours.get(RANDOM.nextInt(yours.size())).deleted = true;
    }

    /** Checked each game minute. */
    static void minute(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        if (living.isEmpty() || s.day > Schedule.DAYS || s.devoted && s.minute != AFTER_TEXT) return;
        ServerPlayerEntity p = living.get(0);
        if (s.minute == Festival.INVITE_TEXT && s.day == Festival.DAY - 1) classChat(server, s, "festival tomorrow!! everyone's getting the 9am train from chinetsu station, come", null);
        if (s.minute == LUNCH_TEXT && Schedule.isSchoolDay(s.day) && !Living.active(s)) chat(server, s);
        if (s.minute == AFTER_TEXT) hers(server, s, p, town);
        if (s.minute == PHOTO_TEXT && s.day >= PHOTOS_FROM && !Living.active(s) && RANDOM.nextInt(3) == 0) photo(server, s, living, PHOTO_DELAY);
        if (s.minute == UNSEND_AT && s.day >= UNSENDS_FROM && RANDOM.nextBoolean()) unsendOneOfYours();
        if (s.minute == SILENT_TEXT && s.day >= SILENT_FROM && hasChat(herName(s)) && RANDOM.nextInt(3) == 0) type(server, s, null, -1, null);
        if (s.minute == LATE_TEXT) {
            if (s.day == HARUKA_DAY && !Living.active(s)) {
                text(server, s, "Haruka M.", "is this still your number? don't go home the way you usually go");
            } else if (s.dating >= 0 && !Living.active(s) && !s.dead.contains(s.dating)) {
                text(server, s, Roster.get(s.dating).firstName(), "Goodnight. Lock your door, okay? I worry.");
            } else if (s.day >= 4 && RANDOM.nextInt(3) == 0) {
                hers(server, s, p, town);
            }
        }
    }

    /** Developer testing only: there's something to answer. */
    static void devReplies() {
        waiting = REPLIES;
    }

    /** Developer testing only: one of hers taken back, one of yours gone, and something from the dead. */
    static void devStrange(MinecraftServer server, RunState s) {
        for (int i = feed.size() - 1; i >= 0; i--) {
            if (!feed.get(i).mine && feed.get(i).from.equals(herName(s))) {
                feed.get(i).deleted = true;
                break;
            }
        }
        unsendOneOfYours();
        receive(server, s, CHAT, pick(FROM_THE_DEAD), "Aiko S.", false, -1);
        receive(server, s, CHAT, "behind you she is watching", "", true, -1);
    }

    /** The phone asks again every couple of seconds while it's open, so new messages turn up as they arrive. */
    public static void open(ServerPlayerEntity p, boolean refresh) {
        int fresh = unread;
        unread = 0;
        send(p, fresh, refresh);
    }

    /**
     * Everything the screen shows. Each message's From is the conversation it's in, yours included,
     * and Sender who wrote it, in the class chat. Unread is how many of the newest weren't seen
     * before this opening. Devoted is No One Will Save You, where she has your phone and nothing gets
     * out; Watched is how close something is, 0..1. TypingIn is the chat she's typing in, for
     * TypingFor more milliseconds. Refresh only updates a phone that's already open.
     */
    private static void send(ServerPlayerEntity p, int fresh, boolean refresh) {
        RunState s = RunManager.state(p.server);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Refresh", refresh);
        NbtList messages = new NbtList();
        for (Message m : feed) {
            NbtCompound e = new NbtCompound();
            e.putString("When", "Day " + m.day + ", " + Schedule.clock(m.minute));
            e.putInt("Day", m.day);
            e.putString("Clock", Schedule.clock(m.minute));
            e.putString("From", m.from);
            e.putString("Sender", m.sender);
            e.putString("Body", m.deleted ? "" : m.body);
            e.putBoolean("Mine", m.mine);
            e.putBoolean("Deleted", m.deleted);
            e.putBoolean("Dead", m.dead);
            e.putInt("Photo", m.deleted ? -1 : m.photo);
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
        int left = typingUntil - p.server.getTicks();
        if (typingIn != null && left > 0) {
            nbt.putString("TypingIn", typingIn);
            nbt.putInt("TypingFor", left * 50);
            nbt.putLong("TypingSeed", typingSeed);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(p, Net.PHONE, buf);
    }

    public static void reply(ServerPlayerEntity p, int option) {
        if (option < 0 || option >= waiting.size() || RunManager.isGhost(p)) return;
        RunState s = RunManager.state(p.server);
        Reply r = waiting.get(option);
        waiting = List.of();
        feed.add(new Message(s.day, s.minute, herName(s), r.label(), true, "", false, -1));
        int her = s.freeYandere();
        s.fondness[her] = Math.max(-5, Math.min(5, s.fondness[her] + r.fondness()));
        s.markDirty();
        Shadow.saw(s, "You answered her text. She read it forty times.");
        send(p, 0, true);
        RunManager.later(100, () -> fromHer(p.server, s, r.answer()));
    }

    /** A ghost's few words, posted to the class chat from nobody. Once every half a minute at most. */
    public static void ghostSays(ServerPlayerEntity p, int[] words) {
        if (!RunManager.isGhost(p) || words.length == 0 || words.length > GHOST_MOST) return;
        int now = p.server.getTicks();
        Integer last = ghostSaid.get(p.getUuid());
        if (last != null && now - last < GHOST_EVERY) return;
        StringBuilder said = new StringBuilder();
        for (int w : words) {
            if (w < 0 || w >= GHOST_WORDS.size()) return;
            if (!said.isEmpty()) said.append(' ');
            said.append(GHOST_WORDS.get(w));
        }
        ghostSaid.put(p.getUuid(), now);
        receive(p.server, RunManager.state(p.server), CHAT, said.toString(), "", true, -1);
        send(p, 0, true);
    }
}
