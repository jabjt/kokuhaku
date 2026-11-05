package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.net.Net;
import com.kokuhaku.client.ClientRun;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.DevShots;
import com.kokuhaku.game.Monologues;
import com.kokuhaku.world.MenuRoom;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The end. You're in the room from the main menu and she's staring at you. No
 * HUD, no keys, no Esc. You get two things to ask her, different each time,
 * she gets one of her own, then a last choice. Tell her you don't love her and
 * she goes for the bat. Try to win her over and, one time in a hundred, hearts
 * appear over her head and she sends you back; otherwise all you can do is beg.
 * Say nothing for two minutes and she decides for you. Unless she lets you go,
 * the game closes. The host stays on the dark screen, as the others are in
 * their world.
 */
public class JumpscareScreen extends Screen {
    private enum Stage { ROOM, GREETING, HEARTS, WINDUP, SWING, FALLING, FREED, DEAD, WAITING, UNMASKED }

    /** Starts the text of the scene where you name her, alone: her manner, then a new line and how it ends. */
    public static final String UNMASK = "UNMASK:";
    /** Starts the text of the last scene after three strikes. There's nothing left to say. */
    private static final String FINAL = "FINAL:";
    private static final long UNMASK_LINE_MS = 4_600, FINAL_WAIT_MS = 4_800;

    /** How long she holds you, including running up and letting go. */
    private static final long GREETING_MS = 5_600, RUSH_MS = 550, LET_GO_MS = 900;
    private static final long GREET_AT_MS = 1_800;
    private static final String LAST_WORDS = "I don't love you.";
    private static final String SWEET_TALK = "You're the only one I've ever thought about.";
    private static final String STAY = "I'll stay with you.";
    private static final String PLEASE = "Please.";
    private static final int ESCAPE_ODDS = 100;
    private static final long HEARTS_MS = 4_000;
    private static final long STARE_MS = "wait".equals(System.getenv("KOKUHAKU_ROOMTEST")) ? 12_000 : 120_000;
    private static final long SWING_MS = 1_300, DEAD_MS = 4_000, FADE_MS = 1_500, LINE_MS = 1_500, PER_CHAR_MS = 55, STAY_MS = 3_800;
    private static final long TILT_AFTER_MS = 10_000, TILT_TAKES_MS = 1_500, TILT_SWAP_MS = 9_000;
    private static final float TILT = 0.6f;
    private static final int ROUNDS = 3, HER_ROUND = 1, CHOICES = 3;
    private static final int[] STRUGGLE_AT = {3, 7, 12};

    /** How far her head is on one side, for the renderer. */
    public static float headTilt = 0f;
    /** 0..1 through the swing of the bat, or negative when she isn't swinging, for the renderer. */
    public static float batSwing = -1f;
    /** She's going to let you go. */
    public static boolean hearts = false;
    /** 0..1: how far she's come across the room to hold you. */
    public static float closeness = 0f;

    private long opened = System.currentTimeMillis();
    /** You don't arrive in that room, you come round in it: dragged through the dark, then your eyes, then your head, then her. */
    private static final long WAKE_MS = 13_500, DARK_MS = 900, STRAIN_FROM_MS = 5_600, STRAIN_TO_MS = 7_500, HEAD_UP_FROM_MS = 8_000, HEAD_UP_TO_MS = 11_600, THERE_YOU_ARE_MS = 10_800;
    private static final float LAP_PITCH = 66f;
    private static final double EYE_HEIGHT = 1.4, EYE_FORWARD = 0.1;
    private static final long REACTION_MS = 3_400;
    /** When your eyes get to each height. They don't stay open the first couple of times. */
    private static final float[][] EYES = {{900, 0f}, {1400, 0.2f}, {2000, 0f}, {2800, 0.45f}, {3400, 0.12f}, {4300, 1f}};
    /** What she's doing about what you just said: for the renderer. */
    public static int herMood = 0;
    public static float herMoodWeight = 0f, herStep = 0f;
    /** Where she's got to, from where she started; which way she's facing; and how far into lying down or sitting she is. */
    public static float herX = 0f, herZ = 0f, herFacing = 0f, herToYou = 0f, herStride = 0f, herFlat = 0f, herSit = 0f, batRaise = 0f;
    /** Where her head's actually got to, how far through her walk, kneeling, kicking, reaching, and what's left of her idling. */
    public static float herLook = 0f, herPhase = 0f, herKneel = 0f, herKick = 0f, herReach = 0f, herIdle = 1f;
    /** After the hit: how far the bat's come down, how far she's looking down, and how hard she's breathing. */
    public static float batHang = 1f, herHeadDown = 0f, herWinded = 0f;
    public static final float SWING_STEP = 0.35f;
    public static boolean batHeld = false;
    /** 0..1 through you and the chair going over, for the renderer. */
    public static float youFall = 0f;
    public static final float FALL_DEGREES = 84f;
    private static final float LYING_YAW = 215f, FLOOR_TO_EYE = 1.4f, EYE_TO_PIVOT = 0.5f;
    private static final long FALL_TAKES_MS = 700;
    private boolean landed = false;
    /** Developer testing only (see EndingsScreen). */
    public static boolean gallery = false, forceLove = false;
    public static long stareOverride = 0;
    public static int runsOverride = -1;
    /** She's made up her mind: nothing else to look at now. */
    public static boolean herMindMade = false;
    private static final int STANDING = 0, ON_THE_RUG = 1, AT_YOUR_FEET = 2, RIGHT_THERE = 3, PACING = 4, BEHIND = 5, DESK = 6, OVER_YOU = 7;
    private static final float SLOW_WALK = 0.85f, BEHIND_Z = 3.9f, CHAIR_Z = 2.4f, SIDE_X = 1.7f, DESK_X = 0.9f, DESK_Z = -0.5f, DESK_FACING = -90f;
    private static final long BEHIND_AFTER_MS = 24_000, BEHIND_STAY_MS = 9_000, BEHIND_AGAIN_MS = 30_000, BREATH_MS = 2_600;
    private static final long AT_DESK_MS = 2_200, BEFORE_RAISE_MS = 600, RAISE_MS = 900, STRUGGLE_MS = 380, LOOK_AWAY_MS = 4_000;
    private static final long CALM_BEAT_MS = 1_000, FAST_BEAT_MS = 620, SHH_MS = 2_700;
    private float herGap = 0f, herPace = WALKS_PER_SEC, herSpeed = 0f, herDt = 0f, flatClock = 0f, sitClock = 0f;
    private long overYouAt = 0;
    private int herBefore = STANDING, windup = 0, struggles = 0, scolded = 0;
    private long behindSince = 0, behindAgain = 0, nextBreath = 0, nextBeat = 0, windupAt = 0, struggleUntil = 0, awaySince = 0;
    private String behindLine = "";
    private static final float WALKS_PER_SEC = 1.5f, THERE = 0.04f, SLOWEST_WALK = 0.3f, SLOWS_FROM = 2.4f, FACING_TO_WALK = 100f, STRIDES_PER_BLOCK = 5.2f;
    private static final float WALK_RATE = 6f, TURN_RATE = 5f, LOOK_RATE = 7f, MOOD_RATE = 5f, STEP_RATE = 3.6f, TILT_RATE = 4f, CAMERA_RATE = 5f, AFTER_RATE = 3f;
    private static final float LIE_DOWN_SECONDS = 2.2f, SIT_DOWN_SECONDS = 1.6f;
    /** Where you've landed, from where she started, and where she goes to stand over you. */
    private static final float FELL_X = -1.84f, FELL_Z = 2.9f, OVER_X = -1.2f, OVER_Z = 1.9f, ARM_AFTER_SWING = 0.345f;
    private static final long OVER_YOU_AFTER_MS = 700;
    private static final float RUG_Z = -0.3f, FEET_Z = 1.45f, CLOSE_Z = 1.05f, PACE_FROM_X = -2.6f, PACE_WIDE = 1.5f;
    private static final float DISTANCE = 3f;
    private static final long FALL_MS = 5_200, TYPE_MS = 26;
    private static final float MOUSE_YAW = 30f, MOUSE_PITCH = 9f;
    /** How far your head drops to watch her on the floor. */
    private static final float LOOK_DOWN_RUG = 26f, LOOK_DOWN_FEET = 16f;
    private int herScene = STANDING;
    private long lastHer = 0;
    private String typing = "";
    private long typedFrom = 0;
    private static final int DELIGHTED = 1, HURT = 2, LEANING_IN = 3;
    private long moodUntil = 0;
    private long waking = 0, nextWakeSound = 0;
    private int wakeStep = 0;
    private final Random random = new Random();
    private final Monologues.Voice voice;
    private final List<Monologues.Exchange> pool;
    private final List<String> queue = new ArrayList<>();
    private long nextLineAt = 0, stayAt = 0;
    private int refusals = 0, struggleSaid = 0;
    private Stage stage = Stage.ROOM;
    private int round = 0;
    private long lastAnswer = opened;
    private long stageStart = opened;
    private String said = "";
    private boolean closed = false;
    private boolean pleading = false;
    private boolean freed = false;
    private final String personality;
    /** Everything she has to say when you've found her out, and how far she's got. */
    private final List<String> speech;
    private final String ending;
    private final boolean finalBlow;
    private int spoken = -1;
    private long spokeAt = 0;
    private boolean greeted = false;
    private int devStep = 0;

    public JumpscareScreen(String personality) {
        super(Text.empty());
        this.finalBlow = personality.startsWith(FINAL);
        if (personality.startsWith(UNMASK)) {
            int split = personality.indexOf('\n');
            this.personality = personality.substring(UNMASK.length(), split);
            this.ending = personality.substring(split + 1);
            this.speech = Monologues.unmasked(this.personality);
        } else {
            this.personality = finalBlow ? personality.substring(FINAL.length()) : personality;
            this.ending = "";
            this.speech = List.of();
        }
        closeness = 0f;
        if (!this.personality.startsWith("ROAD")) {
            waking = opened;
            opened += WAKE_MS;
            stageStart = opened;
            lastAnswer = opened;
            Cutscene.tiedPitch = LAP_PITCH;
            Cutscene.tiedYaw = 180f;
            Cutscene.tiedShake = 0f;
            Cutscene.chair(new Vec3d(MenuRoom.SEAT_X, MenuRoom.GIRL_Y + EYE_HEIGHT, MenuRoom.SEAT_Z - EYE_FORWARD));
        }
        herMood = 0;
        herMoodWeight = 0f;
        herStep = 0f;
        herX = herZ = herFacing = herToYou = herStride = herFlat = herSit = batRaise = 0f;
        herLook = herPhase = herKneel = herKick = herReach = herHeadDown = herWinded = 0f;
        herIdle = 1f;
        batHang = 1f;
        batHeld = false;
        herMindMade = false;
        youFall = 0f;
        Cutscene.tiedRoll = 0f;
        Cutscene.tiedDrop = 0f;
        Cutscene.tiedSlide = 0f;
        this.voice = Monologues.voice(this.personality);
        this.pool = voice.questions(played());
        Collections.shuffle(pool, random);
        headTilt = 0f;
        batSwing = -1f;
        hearts = false;
    }

    @Override
    protected void init() {
        client.options.hudHidden = true;
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR,
                stage == Stage.ROOM ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
        if (stage != Stage.ROOM || finalBlow || !speech.isEmpty() || stayAt != 0) return;
        int y = height - 62;
        if (pleading) {
            addDrawableChild(new MenuButton(centred(PLEASE), y + 18, 220, 16, KokuhakuClient.menuText(PLEASE), b -> refused()));
            return;
        }
        if (round >= ROUNDS) {
            addDrawableChild(new MenuButton(centred(SWEET_TALK), y + 9, 220, 16, KokuhakuClient.menuText(SWEET_TALK), b -> sweetTalk()));
            addDrawableChild(new MenuButton(centred(LAST_WORDS), y + 27, 220, 16, KokuhakuClient.menuText(LAST_WORDS), b -> swing(played() > 0 ? Monologues.LAST_TIME : voice.oh())));
            if (ClientRun.party == 1) addDrawableChild(new MenuButton(centred(STAY), y + 45, 220, 16, KokuhakuClient.menuText(STAY), b -> agree()));
            return;
        }
        List<Monologues.Exchange> choices = round == HER_ROUND ? voice.herQuestion() : pool.subList(round == 0 ? 0 : CHOICES, round == 0 ? CHOICES : CHOICES * 2);
        for (int i = 0; i < CHOICES; i++) {
            Monologues.Exchange line = choices.get(i);
            addDrawableChild(new MenuButton(centred(line.ask()), y + i * 18, 220, 16, KokuhakuClient.menuText(line.ask()), b -> ask(line)));
        }
    }

    private static int played() {
        return runsOverride >= 0 ? runsOverride : com.kokuhaku.client.Profile.played();
    }

    /** Developer testing only: back to the list, with the room put back how it was. */
    private void backToTheList() {
        closed = true;
        youFall = 0f;
        closeness = 0f;
        headTilt = 0f;
        batSwing = -1f;
        hearts = false;
        client.world.setBlockState(MenuRoom.CHAIR_POS, MenuRoom.chair());
        Cutscene.stop();
        client.setScreen(new EndingsScreen());
    }

    private int centred(String label) {
        return width / 2 - textRenderer.getWidth(KokuhakuClient.menuText(label)) / 2;
    }

    /** She speaks first, each in her own way. The bubbly ones can't keep their hands off you. */
    private void greet() {
        greeted = true;
        lastAnswer = System.currentTimeMillis();
        if (finalBlow) {
            said = Monologues.last(personality);
            return;
        }
        List<String> lines = Monologues.taken(personality);
        if (lines.isEmpty()) return;
        say(lines);
        if (personality.equals("CHEERFUL") || personality.equals("SPORTY")) go(Stage.GREETING);
    }

    /** The next thing she says, each worse than the last, until there's nothing left of the girl you knew. */
    private void nextLine() {
        spoken++;
        spokeAt = System.currentTimeMillis();
        if (spoken >= speech.size()) {
            if (gallery) {
                backToTheList();
                return;
            }
            closeness = 0f;
            headTilt = 0f;
            client.options.hudHidden = false;
            client.setScreen(new EndScreen(true, ending));
            return;
        }
        said = speech.get(spoken);
        float tension = spoken / (float) speech.size();
        sound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.8f + tension * 0.5f);
        if (spoken == speech.size() - 1) sound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f);
    }

    /** She answers with all of her, for a moment. */
    private void react(int mood) {
        herMood = mood;
        moodUntil = System.currentTimeMillis() + REACTION_MS;
    }

    /** One line, and the rest when you've had time to read it. You don't get to answer until she's finished. */
    private void say(List<String> lines) {
        long now = System.currentTimeMillis();
        queue.clear();
        queue.addAll(lines);
        said = queue.remove(0);
        nextLineAt = now + LINE_MS + said.length() * PER_CHAR_MS;
        lastAnswer = now;
    }

    private boolean stillTalking(long now) {
        return !queue.isEmpty() || typing.equals(said) && (now - typedFrom) / TYPE_MS < said.length();
    }

    private void ask(Monologues.Exchange line) {
        List<String> reply = new ArrayList<>(line.reply());
        react(line.mood());
        herScene = round == 0 ? ON_THE_RUG : round == HER_ROUND ? AT_YOUR_FEET : RIGHT_THERE;
        if (round == 0) reply.add(voice.asks());
        say(reply);
        round++;
        clearAndInit();
    }

    private void sweetTalk() {
        lastAnswer = System.currentTimeMillis();
        herScene = RIGHT_THERE;
        if (forceLove || "1".equals(System.getenv("KOKUHAKU_LOVE")) || random.nextInt(ESCAPE_ODDS) == 0) {
            react(DELIGHTED);
            said = voice.letGo();
            hearts = true;
            freed = true;
            go(Stage.HEARTS);
        } else {
            pleading = true;
            refused();
        }
    }

    /** Back to first person, in the room, on your own feet. */
    private void stay() {
        if (gallery) {
            backToTheList();
            return;
        }
        Cutscene.stop();
        client.options.hudHidden = false;
        ClientPlayNetworking.send(Net.STAY, PacketByteBufs.empty());
        client.setScreen(null);
    }

    /** She'll listen to you beg for a while. Then that's that. */
    private void refused() {
        if (refusals >= voice.refusals().size()) {
            swing(voice.oh());
            return;
        }
        say(List.of(voice.refusals().get(refusals++)));
        react(LEANING_IN);
        herScene = RIGHT_THERE;
        clearAndInit();
    }

    /** She gets to enjoy it for a moment before you're let up. */
    private void agree() {
        react(DELIGHTED);
        herScene = RIGHT_THERE;
        say(List.of(voice.stay()));
        stayAt = System.currentTimeMillis() + STAY_MS;
        clearAndInit();
    }

    private void go(Stage next) {
        stage = next;
        stageStart = System.currentTimeMillis();
        clearAndInit();
    }

    /** She doesn't shout. She gets up, she's very quiet, and she comes over. */
    private void swing(String first) {
        if (freed) {
            batSwing = 0f;
            said = "";
            go(Stage.SWING);
            return;
        }
        queue.clear();
        said = first;
        closeness = 0f;
        herMood = 0;
        herMindMade = true;
        herScene = DESK;
        herGap = 1f;
        windup = 0;
        batRaise = 0f;
        go(Stage.WINDUP);
    }

    /** You can't get out. You'll try anyway, and she'll notice. */
    private void struggle(long now) {
        if (stage != Stage.ROOM || now < opened || now < struggleUntil) return;
        struggleUntil = now + STRUGGLE_MS;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_LEASH_KNOT_PLACE, 0.7f + random.nextFloat() * 0.2f, 0.7f));
        struggles++;
        if (struggleSaid >= STRUGGLE_AT.length || struggles < STRUGGLE_AT[struggleSaid] || stillTalking(now)) return;
        boolean last = struggleSaid == STRUGGLE_AT.length - 1;
        said = voice.struggle().get(struggleSaid++);
        lastAnswer = now;
        react(last ? HURT : LEANING_IN);
        if (last) herScene = RIGHT_THERE;
    }

    /** From where she is, so you can hear which side of you she's on. */
    private void fromHer(net.minecraft.sound.SoundEvent event, float volume, float pitch) {
        client.getSoundManager().play(new PositionedSoundInstance(event, net.minecraft.sound.SoundCategory.PLAYERS, volume, pitch, net.minecraft.util.math.random.Random.create(),
                MenuRoom.SEAT_X + herX, MenuRoom.GIRL_Y + 0.1, MenuRoom.GIRL_Z + herZ));
    }

    private void sound(net.minecraft.sound.SoundEvent event, float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, 1f));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (gallery && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            backToTheList();
            return true;
        }
        struggle(System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (gallery && System.currentTimeMillis() < opened) {
            opened = stageStart = lastAnswer = System.currentTimeMillis();
            Cutscene.tiedShake = 0f;
            return true;
        }
        if (System.currentTimeMillis() < opened) return true;
        if (stage != Stage.ROOM) return true;
        long now = System.currentTimeMillis();
        if (stillTalking(now)) {
            // Click through what she's saying. She won't mind. She'll say it again.
            if ((now - typedFrom) / TYPE_MS < said.length()) typedFrom = 0;
            else nextLineAt = 0;
            return true;
        }
        if (!super.mouseClicked(mouseX, mouseY, button)) struggle(now);
        return true;
    }

    @Override
    public void tick() {
        if (stage != Stage.FREED) client.options.hudHidden = true; // the scene before this one gives the HUD back when it stops
        devScript(System.currentTimeMillis() - opened);
        long now = System.currentTimeMillis(), t = now - opened, inStage = now - stageStart;
        if (t < 0) wakeSounds(now - waking, now);
        if (t > 0 && now > nextBeat && (stage == Stage.WINDUP || stage == Stage.ROOM && (herScene == BEHIND || round >= ROUNDS))) {
            nextBeat = now + (stage == Stage.WINDUP ? FAST_BEAT_MS : CALM_BEAT_MS);
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.6f));
        }
        if (personality.startsWith("ROAD") && stage != Stage.DEAD && stage != Stage.WAITING) {
            go(Stage.DEAD);
            return;
        }

        if (!speech.isEmpty() && stage == Stage.ROOM && t > GREET_AT_MS) {
            go(Stage.UNMASKED);
            nextLine();
            return;
        }
        if (stage == Stage.ROOM && !greeted && t > GREET_AT_MS) greet();
        if (stage == Stage.ROOM && !queue.isEmpty() && now > nextLineAt) {
            said = queue.remove(0);
            nextLineAt = now + LINE_MS + said.length() * PER_CHAR_MS;
            lastAnswer = now;
        }
        if (stayAt != 0 && now > stayAt) {
            stayAt = 0;
            stay();
            return;
        }
        if (stage == Stage.ROOM && finalBlow && t > GREET_AT_MS + FINAL_WAIT_MS) {
            swing(said);
            return;
        }
        switch (stage) {
            case UNMASKED -> {
                if (now - spokeAt > UNMASK_LINE_MS) nextLine();
                else if (client.currentScreen == this && DevShots.ON && now - spokeAt > 2_500 && now - spokeAt < 2_560) DevShots.take();
            }
            case GREETING -> {
                if (inStage > GREETING_MS) {
                    closeness = 0f;
                    lastAnswer = now;
                    go(Stage.ROOM);
                }
            }
            case ROOM -> {
                if (t > (stareOverride > 0 ? stareOverride : STARE_MS) && stayAt == 0) swing(voice.silence());
            }
            case HEARTS -> {
                if (inStage > HEARTS_MS) swing(said);
            }
            case WINDUP -> {
                // To the desk with her back to you, then all the way back, in no hurry.
                if (windup == 0 && herGap <= THERE) {
                    windup = 1;
                    windupAt = now;
                }
                if (windup == 1 && !batHeld && now - windupAt > AT_DESK_MS * 0.55f) {
                    batHeld = true;
                    fromHer(SoundEvents.ENTITY_ARMOR_STAND_HIT, 0.5f, 0.7f);
                }
                if (windup == 1 && now - windupAt > AT_DESK_MS) {
                    windup = 2;
                    said = voice.iSee();
                    herScene = RIGHT_THERE;
                    herGap = 1f;
                    herPace = SLOW_WALK;
                }
                if (windup == 2 && herGap <= THERE) {
                    windup = 3;
                    windupAt = now;
                }
                if (windup == 3) {
                    if (now - windupAt > BEFORE_RAISE_MS + RAISE_MS + 200) {
                        said = "";
                        batSwing = 0f;
                        go(Stage.SWING);
                    }
                }
            }
            case FALLING -> {
                if (!landed && inStage > FALL_TAKES_MS) {
                    landed = true;
                    sound(SoundEvents.ENTITY_PLAYER_BIG_FALL, 0.7f);
                    sound(SoundEvents.BLOCK_WOOD_BREAK, 0.6f);
                }
                if (inStage > SHH_MS) said = voice.shh();
                if (inStage > FALL_MS) go(Stage.DEAD);
            }
            case SWING -> {
                if (inStage > SWING_MS) {
                    sound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f);
                    batSwing = -1f;
                    if (freed && gallery) {
                        backToTheList();
                        return;
                    }
                    // She carries on from exactly where the swing left her.
                    batRaise = ARM_AFTER_SWING;
                    batHang = 0f;
                    herZ += SWING_STEP;
                    herScene = OVER_YOU;
                    herPace = SLOW_WALK;
                    overYouAt = now + OVER_YOU_AFTER_MS;
                    go(freed ? Stage.FREED : Stage.FALLING);
                    if (!freed) {
                        // The renderer's chair takes over from the real one, so it can go over with you.
                        youFall = 0.001f;
                        client.world.setBlockState(MenuRoom.CHAIR_POS, net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                    if (freed) {
                        // Back to life: the wake-up scene takes over from here.
                        client.options.hudHidden = false;
                        hearts = false;
                        Cutscene.stop();
                        ClientPlayNetworking.send(Net.LET_OUT, PacketByteBufs.empty());
                    }
                }
            }
            case FREED -> client.options.hudHidden = false;
            case WAITING -> {
                if (ClientRun.alive == 0) stop();
            }
            case DEAD -> {
                if (inStage > DEAD_MS) quit();
            }

        }
    }

    /** The host's game is the world the others are playing in, so it can't close while anyone's alive. */
    private void quit() {
        if (ClientRun.party > 1 && ClientRun.alive > 0) {
            go(Stage.WAITING);
        } else {
            stop();
        }
    }

    private void stop() {
        if (closed) return;
        if (gallery) {
            backToTheList();
            return;
        }
        closed = true;
        Cutscene.stop();
        com.kokuhaku.client.Profile.finished(false);
        client.options.hudHidden = false;
        if (ClientRun.reel == null) {
            client.scheduleStop();
            return;
        }
        String reel = ClientRun.reel;
        ClientRun.reel = null;
        client.setScreen(new ReelScreen(reel, client::scheduleStop));
    }

    /** Developer testing only (KOKUHAKU_ROOMTEST=talk or wait, with KOKUHAKU_TOUR=1): plays the scene by itself. */
    private void devScript(long t) {
        String test = System.getenv("KOKUHAKU_ROOMTEST");
        if (test == null) return;
        if (test.equals("full") || test.equals("behind") || test.equals("bat") || test.equals("lay")) {
            long[][] steps = test.equals("lay") ? new long[][]{{2500, 1}, {3600, 0}, {4100, 0}, {4500, 0}, {4900, 0}, {5300, 0}, {5800, 0}, {6600, 0}, {7400, 0}} : test.equals("bat") ? new long[][]{{3000, 2}, {12000, 0}, {12400, 0}, {12800, 0}, {13200, 0}, {13800, 0}, {14600, 0}, {15400, 0}, {16200, 0}} : test.equals("behind") ? new long[][]{{5000, 3}, {5500, 3}, {6000, 3}, {7500, 0}, {33000, 0}, {35000, 0}}
                    : new long[][]{{9000, 0}, {10000, 1}, {13000, 0}, {19000, 0}, {20000, 1}, {23000, 0}, {27000, 1}, {30000, 0}, {31000, 1}, {33500, 0}, {35000, 1}, {37500, 0}, {39000, 1}, {41500, 0}, {43000, 1}, {45500, 0}, {47000, 1}, {49500, 0}, {54000, 0}};
            if (devStep < steps.length && t > steps[devStep][0]) {
                long what = steps[devStep++][1];
                if (what == 0) DevShots.take();
                else if (what == 2) swing(voice.oh());
                else if (what == 3) struggle(System.currentTimeMillis());
                else children().stream().filter(c -> c instanceof MenuButton).map(c -> (MenuButton) c).findFirst().ifPresent(MenuButton::onPress);
            }
            return;
        }
        long[][] talk = {{3000, 1}, {4000, 1}, {4500, 0}, {5000, 1}, {5900, 0}, {8000, 0}, {14000, 0}};
        long[][] wait = {{11800, 0}, {13000, 0}, {15000, 0}};
        if (test.equals("greet")) {
            long[] shots = {1500, 2600, 3800, 6000, 9000};
            if (devStep < shots.length && t > shots[devStep]) {
                devStep++;
                DevShots.take();
            }
            return;
        }
        if (test.equals("stay")) {
            if (devStep < 4 && t > 3000 + devStep * 1000L) {
                List<MenuButton> buttons = children().stream().filter(c -> c instanceof MenuButton).map(c -> (MenuButton) c).toList();
                if (buttons.isEmpty()) return;
                devStep++;
                buttons.get(buttons.size() - 1).onPress();
            }
            return;
        }
        long[][] script = test.equals("talk") ? talk : wait;
        if (devStep >= script.length || t < script[devStep][0]) return;
        if (script[devStep++][1] == 0) {
            DevShots.take();
        } else {
            children().stream().filter(c -> c instanceof MenuButton).map(c -> (MenuButton) c).findFirst().ifPresent(b -> b.onPress());
        }
    }

    private void walkHer(long now) {
        float seconds = lastHer == 0 ? 0f : Math.min(0.1f, (now - lastHer) / 1000f);
        lastHer = now;
        if (stage == Stage.ROOM && now - lastAnswer > TILT_AFTER_MS && herScene == STANDING) herScene = PACING;
        // Say nothing for long enough and she goes where you can't see her.
        if (stage == Stage.ROOM && herScene != BEHIND && now - lastAnswer > BEHIND_AFTER_MS && now > behindAgain) {
            herBefore = herScene;
            herScene = BEHIND;
            herGap = 1f;
            behindSince = 0;
            behindLine = voice.behind().get(random.nextInt(voice.behind().size()));
        }
        if (herScene != BEHIND) behindSince = 0;
        if (herScene == BEHIND && herGap <= THERE) {
            if (behindSince == 0) behindSince = now;
            if (now > nextBreath) {
                nextBreath = now + BREATH_MS;
                fromHer(SoundEvents.ENTITY_PLAYER_BREATH, 0.3f, 0.7f);
            }
            if (now - behindSince > BEHIND_STAY_MS) {
                herScene = herBefore;
                behindAgain = now + BEHIND_AGAIN_MS;
            }
        }
        float toX = 0f, toZ = 0f;
        switch (herScene) {
            case ON_THE_RUG -> toZ = RUG_Z;
            case AT_YOUR_FEET -> toZ = FEET_Z;
            case RIGHT_THERE -> toZ = CLOSE_Z;
            case PACING -> {
                toX = PACE_FROM_X + (float) (Math.sin(now / 2_400.0) + 1f) * PACE_WIDE;
                toZ = 0.1f;
            }
            case BEHIND -> toZ = BEHIND_Z;
            case OVER_YOU -> {
                toX = now < overYouAt ? herX : OVER_X;
                toZ = now < overYouAt ? herZ : OVER_Z;
            }
            case DESK -> {
                toX = DESK_X;
                toZ = DESK_Z;
            }
            default -> {
            }
        }
        // Round the chair, not through it.
        if ((herZ > CHAIR_Z) != (toZ > CHAIR_Z) && Math.abs(herX) < SIDE_X - 0.1f) {
            toX = -SIDE_X;
            toZ = herZ;
        }
        boolean lying = herScene == ON_THE_RUG, sitting = herScene == AT_YOUR_FEET;
        float dx = toX - herX, dz = toZ - herZ, gap = (float) Math.sqrt(dx * dx + dz * dz);
        herGap = gap;
        boolean arrived = gap <= THERE, upright = flatClock < 0.02f && sitClock < 0.02f;
        // She turns to where she's going first, sets off, and slows as she gets there.
        float heading = arrived ? herFacing : (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float off = Math.abs(MathHelper.wrapDegrees(heading - herFacing));
        float wantSpeed = arrived || !upright ? 0f : Math.max(SLOWEST_WALK, Math.min(herPace, gap * SLOWS_FROM)) * MathHelper.clamp(1f - off / FACING_TO_WALK, 0f, 1f);
        herSpeed = toward(herSpeed, wantSpeed, WALK_RATE, seconds);
        if (!arrived && herSpeed > 0.01f) {
            float step = Math.min(gap, herSpeed * seconds);
            herX += dx / gap * step;
            herZ += dz / gap * step;
            int foot = (int) (herPhase / MathHelper.PI);
            herPhase += step * STRIDES_PER_BLOCK;
            if ((int) (herPhase / MathHelper.PI) != foot) {
                fromHer(SoundEvents.BLOCK_WOOD_STEP, 0.4f, 0.85f + random.nextFloat() * 0.15f);
                if (batHeld) fromHer(SoundEvents.ENTITY_ARMOR_STAND_HIT, 0.22f, 0.55f);
            }
        }
        herStride = toward(herStride, MathHelper.clamp(herSpeed / WALKS_PER_SEC, 0f, 1f), WALK_RATE, seconds);
        // Down in stages and back up the same way, at the speed a person does it.
        flatClock = MathHelper.clamp(flatClock + (arrived && lying ? 1f : -1f) * seconds / LIE_DOWN_SECONDS, 0f, 1f);
        sitClock = MathHelper.clamp(sitClock + (arrived && sitting ? 1f : -1f) * seconds / SIT_DOWN_SECONDS, 0f, 1f);
        herKneel = eased(flatClock / 0.4f);
        herFlat = eased((flatClock - 0.3f) / 0.7f);
        herKick = eased((flatClock - 0.8f) / 0.2f);
        herSit = eased(sitClock);
        boolean overYou = herScene == OVER_YOU;
        herToYou = (float) Math.toDegrees(Math.atan2((overYou ? FELL_Z : DISTANCE) - herZ, (overYou ? FELL_X : 0f) - herX)) - 90f;
        float facing = !arrived ? heading : herScene == DESK ? DESK_FACING : herToYou;
        if (herScene == DESK) herToYou = facing;
        herFacing += MathHelper.wrapDegrees(facing - herFacing) * (1f - (float) Math.exp(-TURN_RATE * seconds));
        herLook += MathHelper.wrapDegrees(herToYou - herLook) * (1f - (float) Math.exp(-LOOK_RATE * seconds));
        herDt = seconds;
    }

    private static float toward(float now, float want, float rate, float seconds) {
        return now + (want - now) * (1f - (float) Math.exp(-rate * seconds));
    }

    /** Into a reaction and back out of it, and a little closer when she's got something to say to you. */
    private void updateHer(long now) {
        walkHer(now);
        if (now > moodUntil && herMoodWeight < 0.02f) herMood = 0;
        herMoodWeight = toward(herMoodWeight, now < moodUntil ? 1f : 0f, MOOD_RATE, herDt);
        float wantStep = now < moodUntil && herMood == LEANING_IN ? 0.75f : now < moodUntil && herMood == HURT ? -0.2f : 0f;
        herStep = toward(herStep, wantStep, STEP_RATE, herDt);
        herIdle = toward(herIdle, herMindMade ? 0f : 1f, MOOD_RATE, herDt);
        if (stage == Stage.WINDUP) {
            if (windup == 1) {
                float at = (now - windupAt) / (float) AT_DESK_MS;
                herReach = eased(at / 0.45f) * (1f - eased((at - 0.55f) / 0.4f));
            } else {
                herReach = toward(herReach, 0f, MOOD_RATE, herDt);
            }
            if (windup == 3) batRaise = eased((now - windupAt - BEFORE_RAISE_MS) / (float) RAISE_MS);
            batHang = 1f - batRaise;
        }
        if (stage == Stage.FALLING) {
            // It's done. The bat comes down, her breath catches up with her, and she looks at what she's done.
            batRaise = toward(batRaise, 0f, AFTER_RATE, herDt);
            batHang = toward(batHang, 1f, AFTER_RATE, herDt);
            herHeadDown = toward(herHeadDown, 1f, AFTER_RATE, herDt);
            herWinded = toward(herWinded, 1f, AFTER_RATE, herDt);
        }
    }

    private void updateTilt(long now) {
        long idle = now - lastAnswer;
        float target = 0f;
        if (stage == Stage.ROOM && idle > TILT_AFTER_MS) {
            // Slowly over to one side, and if you still say nothing, over to the other.
            float side = (idle - TILT_AFTER_MS) / TILT_SWAP_MS % 2 == 0 ? 1f : -1f;
            target = side * TILT * Math.min(1f, (idle - TILT_AFTER_MS) / (float) TILT_TAKES_MS);
        }
        headTilt = toward(headTilt, target, TILT_RATE, herDt);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis(), t = now - opened, inStage = now - stageStart;
        if (stage == Stage.SWING) batSwing = Math.min(1f, inStage / (float) SWING_MS);
        updateTilt(now);
        updateHer(now);
        switch (stage) {
            case UNMASKED -> {
                float tension = (spoken + 1) / (float) speech.size();
                closeness = Math.min(1f, tension * tension * 1.15f);
                headTilt = (float) Math.sin(now / 340.0) * 0.45f * tension;
                renderUnmasked(context, tension);
            }
            case FALLING -> {
                // Over goes the chair, and the floor comes up. She's still looking.
                float fall = Math.min(1f, inStage / (float) FALL_TAKES_MS);
                fall = fall * fall;
                youFall = Math.max(0.001f, fall);
                // Your eyes ride the same arc as the rest of you, round the chair's edge, so they land above the floor.
                float angle = FALL_DEGREES * fall * MathHelper.RADIANS_PER_DEGREE, cos = MathHelper.cos(angle), sin = MathHelper.sin(angle);
                Cutscene.tiedRoll = -FALL_DEGREES * fall;
                Cutscene.tiedSlide = EYE_TO_PIVOT - (EYE_TO_PIVOT * cos - FLOOR_TO_EYE * sin);
                Cutscene.tiedDrop = FLOOR_TO_EYE - (EYE_TO_PIVOT * sin + FLOOR_TO_EYE * cos);
                Cutscene.tiedYaw = toward(Cutscene.tiedYaw, LYING_YAW, CAMERA_RATE, herDt);
                Cutscene.tiedPitch = toward(Cutscene.tiedPitch, 0f, CAMERA_RATE, herDt);
                Cutscene.tiedShake = inStage > FALL_TAKES_MS && inStage < FALL_TAKES_MS + 220 ? 5f : 0f;
                renderRoom(context, mouseX, mouseY, delta, now, t);
                context.fill(0, 0, width, height, (int) (Math.max(0f, 1f - inStage / 320f) * 170) << 24 | 0x900008);
                float dark = Math.max(0f, (inStage - FALL_MS * 0.7f) / (FALL_MS * 0.3f));
                context.fill(0, 0, width, height, (int) (Math.min(1f, dark) * 255) << 24);
            }
            case ROOM, GREETING, HEARTS, WINDUP, SWING -> {
                if (stage == Stage.GREETING) {
                    float in = Math.min(1f, inStage / (float) RUSH_MS);
                    float out = Math.min(1f, (GREETING_MS - inStage) / (float) LET_GO_MS);
                    float eased = in * in * (3f - 2f * in);
                    closeness = Math.min(eased, out * out * (3f - 2f * out));
                }
                renderRoom(context, mouseX, mouseY, delta, now, t);
            }
            case DEAD -> renderDead(context);
            case FREED -> context.fill(0, 0, width, height, 0xFF000000);
            case WAITING -> {
                renderDead(context);
                context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("Leave this open. They're still out there."), width / 2, height / 2 + 28, 0xFF8C7C80);
            }
        }
    }

    /** Dragged along the floor, then your own heart, and then you can hear the candles. */
    private void wakeSounds(long since, long now) {
        if (now > nextWakeSound) {
            nextWakeSound = now + 1_250 - Math.min(400, since / 40);
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.7f));
        }
        if (wakeStep == 0 && since > 1_400) {
            wakeStep = 1;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_BREATH, 0.5f, 0.8f));
        }
        if (wakeStep == 1 && since > STRAIN_FROM_MS) {
            wakeStep = 2;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_LEASH_KNOT_PLACE, 0.7f, 0.8f));
        }
        if (wakeStep == 2 && since > STRAIN_FROM_MS + 900) {
            wakeStep = 3;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_LEASH_KNOT_BREAK, 0.6f, 0.6f));
        }
        if (DevShots.ON && (wakeStep == 3 && since > STRAIN_FROM_MS + 1_000 || wakeStep == 4 && since > 10_900 || wakeStep == 5 && since > WAKE_MS - 300)) {
            wakeStep++;
            DevShots.take();
        }
    }

    private static float eased(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float eyesOpen(float since) {
        for (int i = 1; i < EYES.length; i++) {
            if (since < EYES[i][0]) return EYES[i - 1][1] + (EYES[i][1] - EYES[i - 1][1]) * eased((since - EYES[i - 1][0]) / (EYES[i][0] - EYES[i - 1][0]));
        }
        return 1f;
    }

    /** Your head comes up off the floor, your eyes won't stay open, and the first thing in focus is her. */
    private void renderWaking(DrawContext context, long since) {
        float up = eased((since - HEAD_UP_FROM_MS) / (float) (HEAD_UP_TO_MS - HEAD_UP_FROM_MS));
        Cutscene.tiedPitch = LAP_PITCH * (1f - up);
        // You pull at them. They don't give.
        Cutscene.tiedShake = since > STRAIN_FROM_MS && since < STRAIN_TO_MS ? 2.2f : 0f;
        headTilt = 0.32f * eased((since - THERE_YOU_ARE_MS) / 1_400f);
        float open = since < EYES[0][0] ? 0f : eyesOpen(since);
        int lid = (int) (height * 0.5f * (1f - open));
        int soft = (int) (height * 0.16f);
        context.fill(0, 0, width, lid, 0xFF000000);
        context.fill(0, height - lid, width, height, 0xFF000000);
        context.fillGradient(0, lid, width, lid + soft, 0xFF000000, 0);
        context.fillGradient(0, height - lid - soft, width, height - lid, 0, 0xFF000000);
        // Nothing's in focus yet: the whole room swims a little dark.
        int dim = (int) ((1f - eased((since - 3_400) / 3_000f)) * 170);
        if (dim > 0) context.fill(0, 0, width, height, dim << 24);
        if (since > STRAIN_FROM_MS + 400 && since < HEAD_UP_FROM_MS) {
            context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("You can't move your arms."), width / 2, height - 80, 0xFF9A9AA2);
        }
        if (since > THERE_YOU_ARE_MS && since < WAKE_MS - 200) {
            context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("There you are."), width / 2, height - 80, 0xFFE8D0D4);
        }
    }

    private void renderRoom(DrawContext context, int mouseX, int mouseY, float delta, long now, long t) {
        if (t < 0) {
            renderWaking(context, now - waking);
            return;
        }
        if (waking == 0 && t < FADE_MS) {
            context.fill(0, 0, width, height, (int) ((1f - t / (float) FADE_MS) * 255) << 24);
            return;
        }
        // You can't move, but you can look. A little.
        if ((stage == Stage.ROOM || stage == Stage.WINDUP) && waking != 0) {
            float wantYaw = 180f + (MathHelper.clamp((float) mouseX / width, 0f, 1f) - 0.5f) * MOUSE_YAW, wantPitch = (MathHelper.clamp((float) mouseY / height, 0f, 1f) - 0.5f) * MOUSE_PITCH + herFlat * LOOK_DOWN_RUG + herSit * LOOK_DOWN_FEET;
            Cutscene.tiedYaw = toward(Cutscene.tiedYaw, wantYaw, CAMERA_RATE, herDt);
            Cutscene.tiedPitch = toward(Cutscene.tiedPitch, wantPitch, CAMERA_RATE, herDt);
        }
        // The candles won't keep still.
        int flicker = (int) (14 + Math.sin(now / 170.0) * 10 + Math.sin(now / 61.0) * 8);
        if (flicker > 0) context.fill(0, 0, width, height, flicker << 24);
        if (stage == Stage.WINDUP || stage == Stage.SWING) {
            int edge = (int) ((windup + 1) / 4f * 170);
            context.fillGradient(0, 0, width, height / 3, edge << 24 | 0x140004, 0x00140004);
            context.fillGradient(0, height * 2 / 3, width, height, 0x00140004, edge << 24 | 0x140004);
        }
        if (stage == Stage.ROOM) {
            Cutscene.tiedShake = toward(Cutscene.tiedShake, now < struggleUntil ? 2.4f : 0f, 16f, herDt);
            // Look away from her for long enough and she'll say so.
            if (mouseX > width * 0.1f && mouseX < width * 0.9f || herScene == BEHIND || scolded >= 2) {
                awaySince = 0;
            } else if (awaySince == 0) {
                awaySince = now;
            } else if (now - awaySince > LOOK_AWAY_MS) {
                awaySince = 0;
                scolded++;
                said = voice.lookAtMe();
                lastAnswer = now;
                react(LEANING_IN);
            }
        }
        boolean silent = stage == Stage.ROOM && now - lastAnswer > TILT_AFTER_MS;
        String line = stage == Stage.ROOM && herScene == BEHIND && herGap <= THERE ? behindLine : silent ? "...?" : said;
        if (!line.equals(typing)) {
            typing = line;
            typedFrom = now;
        }
        String shown = line.substring(0, (int) Math.min(line.length(), (now - typedFrom) / TYPE_MS));
        if (!shown.isEmpty()) {
            context.fillGradient(0, height - 112, width, height, 0, 0xB0000000);
            var wrapped = textRenderer.wrapLines(KokuhakuClient.menuText(shown), Math.min(width - 40, 420));
            int y = height - 80 - (wrapped.size() - 1) * 11;
            for (var part : wrapped) {
                context.drawCenteredTextWithShadow(textRenderer, part, width / 2, y, 0xFFE8D0D4);
                y += 11;
            }
        }
        if (stage == Stage.WINDUP || stage == Stage.SWING || stillTalking(now) || !greeted) return;
        super.render(context, mouseX, mouseY, delta);
    }

    /** The room going red round the edges, the words shaking harder, and her getting closer with every line. */
    private void renderUnmasked(DrawContext context, float tension) {
        int edge = (int) (tension * 170);
        context.fillGradient(0, 0, width, height / 3, edge << 24 | 0x400008, 0x00400008);
        context.fillGradient(0, height * 2 / 3, width, height, 0x00400008, edge << 24 | 0x400008);
        if (random.nextFloat() < tension * 0.12f) context.fill(0, 0, width, height, 0x50FF0000);
        boolean shouting = said.equals(said.toUpperCase());
        int shake = (int) (tension * (shouting ? 6 : 2));
        int count = (int) Math.min(said.length(), (System.currentTimeMillis() - spokeAt) / 1000f * 42f);
        var wrapped = textRenderer.wrapLines(KokuhakuClient.menuText(said.substring(0, count)), Math.min(width - 40, 360));
        int y = height - 56 - wrapped.size() * 11;
        context.fill(width / 2 - 190, y - 8, width / 2 + 190, y + wrapped.size() * 11 + 6, 0x90000000);
        for (var line : wrapped) {
            int dx = shake == 0 ? 0 : random.nextInt(shake * 2 + 1) - shake, dy = shake == 0 ? 0 : random.nextInt(shake + 1) - shake / 2;
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2 + dx, y + dy, shouting ? 0xFFFF3040 : 0xFFE8D0D4);
            y += 11;
        }
    }

    private void renderDead(DrawContext context) {
        context.fill(0, 0, width, height, 0xFF000000);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f - 12, 0);
        context.getMatrices().scale(3f, 3f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("You died"), 0, 0, 0xFFFFFFFF);
        context.getMatrices().pop();
        if (personality.startsWith("ROAD")) context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("You were hit by a " + personality.substring(5) + "."), width / 2, height / 2 + 16, 0xFF8C7C80);
    }
}
