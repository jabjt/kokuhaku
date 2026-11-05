package com.kokuhaku.net;

import com.kokuhaku.Kokuhaku;
import net.minecraft.util.Identifier;

/** Packet ids. The game is single-player, but client and integrated server still talk through packets. */
public final class Net {
    // Server -> client
    /** day, minute, guessesLeft, difficulty, status */
    public static final Identifier STATE = id("state");
    /** title, subtitle, style (0 = normal, 1 = sleep fade) */
    public static final Identifier ANNOUNCE = id("announce");
    /** NBT: student, name, line, options[] — or empty to close */
    public static final Identifier DIALOGUE = id("dialogue");
    /** NBT snapshot of clues and the suspect board */
    public static final Identifier BAG = id("bag");
    /** kind (0 lost, 1 won, 2 the room and the jumpscare), the yandere, text */
    public static final Identifier END = id("end");
    /** type (0 sleep, 1 wake, 2 black screen), camera x/y/z, look-at x/y/z, bed head x/y/z, bed foot x/y/z, duration ticks, text */
    public static final Identifier CUTSCENE = id("cutscene");
    /** yen */
    public static final Identifier OPEN_VENDING = id("open_vending");
    /** job ordinal, yen */
    public static final Identifier OPEN_JOB = id("open_job");

    /** NBT lesson (see Lessons.toNbt) */
    public static final Identifier LESSON = id("lesson");
    /** Developer tour only: take a screenshot now. */
    public static final Identifier SHOT = id("shot");
    /** What you're carrying to your table, by item id, or nothing. */
    public static final Identifier TRAY = id("tray");
    /** Everyone in the party: count, then uuid, name, host for each. */
    public static final Identifier PARTY = id("party");
    public static final Identifier RUN_START = id("run_start");
    /** The way you walk to the corner: count, then x, y, z for each point. */
    public static final Identifier PEEK_PATH = id("peek_path");
    /** The walk in has stopped for a train, or started again: whether it's stopped. */
    public static final Identifier WALK_HOLD = id("walk_hold");
    /** on, where, which track */
    public static final Identifier RADIO = id("radio");
    /** Open the radio: on, can you look underneath, and the tracks on the station. */
    public static final Identifier RADIO_OPEN = id("radio_open");
    /** A track to play, or -1 for off, or -2 to look underneath. */
    public static final Identifier RADIO_PICK = id("radio_pick");
    /** text, whose name goes on it */
    public static final Identifier NOTE = id("note");
    /** How far through the walk the server really is. */
    public static final Identifier WALK_CLOCK = id("walk_clock");
    /** Someone near you is talking: uuid, gain, sound. */
    public static final Identifier VOICE_OUT = id("voice_out");

    /** Everything she did, for after the ending: text. */
    public static final Identifier REEL = id("reel");
    /** NBT: every message, and any answers you can give. */
    public static final Identifier PHONE = id("phone");

    // Client -> server
    /** You got your phone out. */
    public static final Identifier OPEN_PHONE = id("open_phone");
    /** Your answer: option. */
    public static final Identifier PHONE_REPLY = id("phone_reply");
    /** The accomplice leaves something false in the bag. */
    public static final Identifier PLANT = id("plant");
    /** student, option */
    /** Your mic: sound. */
    public static final Identifier VOICE = id("voice");
    /** You said you'd stay with her. */
    public static final Identifier STAY = id("stay");
    /** She let you go. */
    public static final Identifier LET_OUT = id("let_out");
    /** The host pressed play: difficulty. */
    public static final Identifier START = id("start");
    public static final Identifier CHOOSE = id("choose");
    public static final Identifier OPEN_BAG = id("open_bag");
    /** girl id */
    public static final Identifier ACCUSE = id("accuse");
    /** drink ordinal */
    public static final Identifier BUY = id("buy");
    /** job ordinal, mistakes */
    public static final Identifier SHIFT_DONE = id("shift_done");
    /** wrong answers */
    public static final Identifier LESSON_DONE = id("lesson_done");

    /** A practical: whether it's the kitchen, difficulty. */
    public static final Identifier PRACTICAL = id("practical");
    /** Your record, for your party: wins, losses, badges. */
    public static final Identifier PROFILE = id("profile");
    /** Whether you're sitting this run out as a ghost. */
    public static final Identifier SPECTATE = id("spectate");
    /** The host wants the saved run back. */
    public static final Identifier CONTINUE = id("continue");
    /** PE on the court: difficulty. */
    public static final Identifier HOOPS = id("hoops");
    /** Baskets made, shots taken. */
    public static final Identifier PE_DONE = id("pe_done");
    /** Bring this friend back, as a ghost: their name. */
    public static final Identifier CALL_GHOST = id("call_ghost");
    /** You've been called back. */
    public static final Identifier GHOST = id("ghost");
    /** How the struggle went: whether you got free. */
    public static final Identifier GRAB_DONE = id("grab_done");
    /** You'd rather skip the walk in. */
    public static final Identifier SKIP_WALK = id("skip_walk");

    private Net() {
    }

    private static Identifier id(String path) {
        return new Identifier(Kokuhaku.MOD_ID, path);
    }
}
