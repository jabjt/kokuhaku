package com.kokuhaku.client;

import com.kokuhaku.game.PathWalker;
import com.kokuhaku.game.Peeker;
import com.kokuhaku.screen.CutsceneScreen;
import com.kokuhaku.screen.GrabScreen;
import com.kokuhaku.screen.WalkScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import com.kokuhaku.world.MenuRoom;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The bedtime scenes, seen through your own eyes, with no HUD or input.
 * SLEEP: you walk to the bed, sit, lie back, and the picture fades to black.
 * BLACK: the night, with any news on the screen.
 * WAKE: fade in on the ceiling; the alarm; you blink, sit up, swing your legs
 * round and stand beside the bed, where the game hands you back control.
 * BAT: footsteps behind you, the hit, the ground coming up, and her over you.
 * NIGHT: out too late. You look left, you look right, and everything stops.
 * PEEK: your head turns to the figure at the corner, she bolts, you run after
 * her and round the corner, and there's nobody there.
 * CAR: a horn, your head snaps round, the hit, and you land in the road.
 * WARD: you come round flat on your back, and your head turns to whoever's there. It holds until the next scene.
 * JEALOUS: you turn to her, she's already looking at you, and then you look down at your arm.
 * WALK: you walk along a route with people beside you, turning to whoever's talking.
 * RIDE: you stand in the carriage of the real train, carried along with it, watching the town go by.
 */
public final class Cutscene {
    public enum Type { SLEEP, WAKE, BLACK, BAT, NIGHT, PEEK, CAR, WARD, JEALOUS, WALK, GRAB, RIDE, DRAG, TIED }

    /** A camera pose at a point in the scene (time 0..1); the camera eases between them. */
    private record Key(float time, Vec3d pos, float yaw, float pitch, float roll) {
        Key(float time, Vec3d pos, float yaw, float pitch) {
            this(time, pos, yaw, pitch, 0f);
        }
    }

    private static final float[] SHOT_TIMES = {0.22f, 0.55f, 0.75f};
    private static final float[] BAT_SHOTS = {0.2f, 0.28f, 0.34f, 0.5f, 0.68f, 0.8f};
    private static final float[] NIGHT_SHOTS = {0.32f, 0.68f, 0.8f};
    private static final float[] CAR_SHOTS = {0.1f, 0.2f, 0.34f, 0.6f};
    private static final float[] JEALOUS_SHOTS = {0.3f, 0.75f};
    private static final float BAT_HIT = 0.24f, BAT_FINISH = 0.86f, NIGHT_STEP = 0.7f, NIGHT_HIT = 0.76f;
    private static final float CAR_HIT = 0.16f, CAR_FADE = 0.7f;
    /** Her footsteps, getting closer. */
    private static final float[] STEPS = {0.03f, 0.09f, 0.14f, 0.18f, 0.21f};
    private static final double BED_TOP = 0.56, LYING_EYE = BED_TOP + 0.3, SITTING_EYE = BED_TOP + 0.7, STANDING_EYE = 1.62;

    private static Type type;
    private static Vec3d bedHead, bedFoot, standSpot, herPos;
    private static float roll = 0f;
    private static float lookYaw, lookPitch;
    /** Where you're looking from the chair, for the room scene to set. */
    public static float tiedPitch = 0f, tiedYaw = 180f, tiedShake = 0f, tiedRoll = 0f, tiedDrop = 0f, tiedSlide = 0f;
    /** Your head, on the floor, a body's length behind your feet, looking up along yourself at her. */
    private static final double DRAG_HEAD_BACK = 1.62, DRAG_HEAD_HIGH = 0.36, DRAG_PITCH = -36, DRAG_BUMP = 0.035;
    /** How far your eyes get, and when. You're not awake for most of this. */
    private static final float[][] DRAG_EYES = {{0f, 0f}, {0.1f, 0f}, {0.17f, 0.3f}, {0.23f, 0.05f}, {0.33f, 0f}, {0.42f, 0.55f}, {0.5f, 0.75f}, {0.57f, 0.1f}, {0.64f, 0f}, {0.72f, 1f}, {0.8f, 0.9f}, {0.87f, 0.15f}, {0.93f, 0f}, {1f, 0f}};
    private static final long CHAIR_FOREVER_MS = 3_600_000L;
    private static final float[] DRAG_SHOTS = {0.479f, 0.52f, 0.77f, 0.79f, 0.8125f, 0.84f};
    private static int heavesHeard = 0;
    /** The way you walk in the PEEK scene, set just before it starts. */
    public static java.util.List<Vec3d> walkPath = java.util.List.of();
    private static long lastStep = 0;
    private static int nextSound;
    private static Vec3d startEye;
    private static float startYaw, startPitch;
    private static String text = "";
    private static long startMs;
    private static long durationMs;
    private static boolean alarmPlayed;
    private static int nextShot;
    private static boolean hudWasHidden;
    /** The walk, and who's talking on it. */
    private static PathWalker walker;
    private static float walkYaw, walkPitch = 1f;
    private static long heldSince = 0;
    private static final float CLOCK_CATCH_UP = 0.3f;
    /** East, out of the carriage window. */
    private static final float RIDE_YAW = -90f;
    private static final double RIDE_RATTLE = 0.012;
    private static Entity carriage;
    private static Vec3d rideOffset = Vec3d.ZERO;
    private static float rideFrom = 0f;
    private static float struggling = 0f;
    private static long lastFrame;
    public static int speaker = -1;
    /** When the camera goes back to the window. */
    public static long speakerUntil = 0;
    private static final double WARD_DOWN_THE_BED = 0.8;
    private static final float WARD_FADE = 0.3f, JEALOUS_ARM = 0.42f, DARK_ROOM = 0.88f;
    /** Sent as the text of a ward scene: it's the middle of the night, and the room is dark. */
    public static final String LIGHTS_OUT = "dark";
    private static boolean lightsOut = false;
    private static final float STRIDE_BOB = 0.05f, STRIDE_ROLL = 0.35f, STRIDE_SWAY = 0.018f;
    /** Which time this is, and whose hand. */
    public static int strikes = 0, herSkin = 0;

    private Cutscene() {
    }

    public static boolean active() {
        return type != null;
    }

    public static Type type() {
        return type;
    }

    public static String text() {
        return text;
    }

    public static void start(Type t, Vec3d cam, Vec3d look, Vec3d head, Vec3d foot, int ticks, String news) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (System.getenv("KOKUHAKU_BOT") != null) com.kokuhaku.Kokuhaku.LOG.info("Scene {} starts, {} ms after the last one ended, replacing {}", t, type == null ? System.currentTimeMillis() - lastStopped : 0, type);
        if (type == null) hudWasHidden = client.options.hudHidden;
        type = t;
        standSpot = cam;
        herPos = look;
        nextSound = 0;
        bedHead = head;
        bedFoot = foot;
        lightsOut = LIGHTS_OUT.equals(news);
        text = lightsOut ? "" : news;
        if (client.player != null) {
            startEye = client.player.getCameraPosVec(1f);
            startYaw = client.player.getYaw();
            startPitch = client.player.getPitch();
        } else {
            startEye = foot.add(0, STANDING_EYE, 0);
        }
        startMs = System.currentTimeMillis();
        durationMs = ticks * 50L;
        alarmPlayed = false;
        heavesHeard = 0;
        nextShot = 0;
        client.options.hudHidden = true;
        if (t == Type.JEALOUS) {
            strikes = (int) head.x;
            herSkin = (int) head.y;
            startEye = cam.add(0, STANDING_EYE, 0);
        }
        heldSince = 0;
        if (t == Type.GRAB) {
            client.setScreen(new GrabScreen((int) head.x, (float) head.y));
            return;
        }
        if (t == Type.RIDE) {
            carriage = carriage(client);
            rideOffset = carriage == null ? Vec3d.ZERO : cam.subtract(carriage.getPos());
            rideFrom = carriageYaw();
            walkYaw = RIDE_YAW;
            lastFrame = System.currentTimeMillis();
            speaker = -1;
            client.setScreen(new WalkScreen());
        } else if (t == Type.WALK) {
            walker = new PathWalker(walkPath);
            walkYaw = yawOf(walker.heading(0));
            walkPitch = 1f;
            lastFrame = System.currentTimeMillis();
            speaker = -1;
            client.setScreen(new WalkScreen());
        } else if (t != Type.WARD || !(client.currentScreen instanceof CutsceneScreen)) {
            client.setScreen(new CutsceneScreen());
        }
    }

    /** Stood at the line, waiting: the scene's clock stops with you. */
    public static void holdWalk(boolean hold) {
        long now = System.currentTimeMillis();
        if (hold && heldSince == 0) {
            heldSince = now;
        } else if (!hold && heldSince != 0) {
            startMs += now - heldSince;
            heldSince = 0;
        }
    }

    /** The server's own place in the walk. The camera eases back to it, so it's never off ahead of the people beside you. */
    public static void chair(Vec3d eye) {
        start(Type.TIED, eye, eye, eye, eye, (int) (CHAIR_FOREVER_MS / 50), "");
    }

    /** Out of your own eyes: your legs going away from you, her at the end of them, and the ceiling sliding by. */
    private static double[] dragCamera() {
        float p = Math.min(1f, progress());
        Vec3d way = herPos.subtract(standSpot).normalize();
        Vec3d at = standSpot.lerp(herPos, MenuRoom.pulled(p)).subtract(way.multiply(DRAG_HEAD_BACK)).add(0, DRAG_HEAD_HIGH, 0);
        float pull = heave();
        double bump = Math.abs(Math.sin(System.currentTimeMillis() / 180.0)) * DRAG_BUMP * pull;
        roll = (float) Math.sin(System.currentTimeMillis() / 520.0) * 3f + pull * 1.5f;
        return new double[]{at.x, at.y + bump, at.z, yawOf(way), DRAG_PITCH - pull * 3f};
    }

    private static float heavePhase() {
        float at = Math.min(0.9999f, progress()) * MenuRoom.HEAVES;
        return at - (int) at;
    }

    /** 0..1: how hard she's pulling. Each heave is the first half of its turn; the rest is getting her breath and her grip. */
    public static float heave() {
        if (type != Type.DRAG) return 0f;
        return MathHelper.sin(MathHelper.PI * MathHelper.clamp(heavePhase() / 0.5f, 0f, 1f));
    }

    /** Which foot's behind her this time. */
    public static int heaveFoot() {
        return type == Type.DRAG && (int) (progress() * MenuRoom.HEAVES) % 2 == 1 ? -1 : 1;
    }

    /** -1..1: over which shoulder she's looking. Every other stop, and every stop once the end of the hall is close. */
    public static float heaveLook() {
        if (type != Type.DRAG) return 0f;
        int heave = (int) (progress() * MenuRoom.HEAVES);
        if (heave % 2 == 0 && heave < MenuRoom.HEAVES - 3) return 0f;
        float phase = heavePhase();
        float looking = smooth((phase - 0.52f) / 0.14f) * smooth((1f - phase) / 0.14f);
        return (heave % 4 < 2 ? 1f : -1f) * looking;
    }

    private static float smooth(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** 1 with your eyes open, 0 shut. */
    public static float dragEyes() {
        float p = Math.min(1f, progress());
        for (int i = 1; i < DRAG_EYES.length; i++) {
            if (p < DRAG_EYES[i][0]) {
                float t = (p - DRAG_EYES[i - 1][0]) / (DRAG_EYES[i][0] - DRAG_EYES[i - 1][0]);
                t = t * t * (3f - 2f * t);
                return DRAG_EYES[i - 1][1] + (DRAG_EYES[i][1] - DRAG_EYES[i - 1][1]) * t;
            }
        }
        return 0f;
    }

    public static void walkClock(int age) {
        if (type != Type.WALK || heldSince != 0) return;
        long wanted = System.currentTimeMillis() - age * 50L;
        startMs += (long) ((wanted - startMs) * CLOCK_CATCH_UP);
    }

    /** A jolt of the head, each time you get a key right. */
    public static void struggle() {
        struggling = 1f;
    }

    /** How far your arm has come up into view, and how hard it's shaking. */
    public static float armRaised() {
        return type == Type.JEALOUS ? MathHelper.clamp((progress() - JEALOUS_ARM) / 0.12f, 0f, 1f) : 0f;
    }

    private static long lastStopped = 0;

    public static void stop() {
        if (type == null) return;
        lastStopped = System.currentTimeMillis();
        Type ended = type;
        type = null;
        roll = 0f;
        MinecraftClient client = MinecraftClient.getInstance();
        if (ended == Type.PEEK && client.player != null) {
            // You keep facing the corner, so you can go and look.
            client.player.setYaw(lookYaw);
            client.player.setPitch(lookPitch);
        }
        client.options.hudHidden = hudWasHidden;
        if (client.currentScreen instanceof CutsceneScreen) client.setScreen(null);
    }

    /** 0..1 through the scene. */
    public static float progress() {
        long now = heldSince != 0 ? heldSince : System.currentTimeMillis();
        return MathHelper.clamp((now - startMs) / (float) durationMs, 0f, 1f);
    }

    /** How black the screen is. */
    public static float blackness() {
        float p = progress();
        return switch (type) {
            case BLACK -> 1f;
            case PEEK -> 0f;
            case CAR -> MathHelper.clamp((p - CAR_FADE) / 0.2f, 0f, 1f);
            case WARD -> Math.max(Math.max(lightsOut ? DARK_ROOM : 0f, 1f - MathHelper.clamp(p / WARD_FADE, 0f, 1f)), Math.max(blink(p, 0.4f), blink(p, 0.48f)));
            case JEALOUS, GRAB -> 0f;
            case RIDE -> 1f - MathHelper.clamp((System.currentTimeMillis() - startMs) / 700f, 0f, 1f);
            case WALK -> Math.max(1f - MathHelper.clamp(p / 0.05f, 0f, 1f), MathHelper.clamp((p - 0.96f) / 0.04f, 0f, 1f));
            case NIGHT -> p >= NIGHT_HIT ? 1f : 0f;
            case SLEEP -> MathHelper.clamp((p - 0.62f) / 0.3f, 0f, 1f);
            case DRAG -> 0f;
            case TIED -> 0f;
            case WAKE -> Math.max(1f - MathHelper.clamp(p / 0.14f, 0f, 1f), Math.max(blink(p, 0.27f), blink(p, 0.33f)));
            case BAT -> {
                if (p >= BAT_FINISH) yield 1f;
                if (p < BAT_HIT) yield 0f;
                // Going in and out.
                float swim = 0.32f + 0.22f * (float) Math.sin((p - BAT_HIT) * 38f);
                yield Math.max(swim * (1f - MathHelper.clamp((p - 0.6f) / 0.1f, 0f, 0.6f)), Math.max(blink(p, 0.5f), blink(p, 0.56f)));
            }
        };
    }

    /** White at the moment of the hit, gone in a blink. */
    public static float flash() {
        if (type != Type.BAT && type != Type.CAR) return 0f;
        float since = progress() - (type == Type.CAR ? CAR_HIT : BAT_HIT);
        return since < 0f ? 0f : MathHelper.clamp(1f - since / 0.05f, 0f, 1f);
    }

    /** The red that stays after you're hit. */
    public static float hurt() {
        if (type == Type.CAR) return progress() >= CAR_HIT ? 0.3f : 0f;
        return type == Type.BAT && progress() >= BAT_HIT ? 0.3f : 0f;
    }

    /** Sideways tilt of the camera this frame. */
    public static float roll() {
        return type == null ? 0f : roll;
    }

    private static void sound(net.minecraft.sound.SoundEvent event, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(event, pitch, volume));
    }

    /** A quick shut of the eyes centred on a moment. */
    private static float blink(float p, float at) {
        return 1f - MathHelper.clamp(Math.abs(p - at) / 0.022f, 0f, 1f);
    }

    public static void tick() {
        if (type == null) return;
        float p = progress();
        if (type == Type.WAKE && !alarmPlayed && p > 0.16f) {
            alarmPlayed = true;
            MinecraftClient.getInstance().getSoundManager()
                    .play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.4f, 0.25f));
        }
        if (type == Type.BAT) {
            while (nextSound < STEPS.length && p > STEPS[nextSound]) {
                sound(SoundEvents.BLOCK_STONE_STEP, 0.6f, 0.25f + nextSound * 0.18f);
                nextSound++;
            }
            if (nextSound == STEPS.length && p > BAT_HIT) {
                nextSound++;
                sound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.7f, 1f);
                sound(SoundEvents.ENTITY_PLAYER_BIG_FALL, 0.6f, 1f);
            }
            if (nextSound == STEPS.length + 1 && p > 0.38f) {
                nextSound++;
                sound(SoundEvents.ENTITY_PLAYER_BIG_FALL, 0.8f, 0.8f);
            }
            if (nextSound == STEPS.length + 2 && p > BAT_FINISH) {
                nextSound++;
                sound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1f);
            }
        }
        if (type == Type.DRAG && (int) (p * MenuRoom.HEAVES + 0.92f) > heavesHeard) {
            heavesHeard++;
            sound(SoundEvents.BLOCK_WOOL_STEP, 0.7f, 0.55f);
            sound(SoundEvents.BLOCK_WOOD_STEP, 0.6f, 0.65f);
            sound(SoundEvents.ENTITY_PLAYER_BREATH, 0.32f, 1.3f);
        }
        if (type == Type.DRAG) {
            if (DevShots.ON && nextShot < DRAG_SHOTS.length && p > DRAG_SHOTS[nextShot]) {
                nextShot++;
                DevShots.take();
            }
        }
        if (type == Type.PEEK && p > walkStart() && p < walkEnd() && System.currentTimeMillis() - lastStep > 300) {
            lastStep = System.currentTimeMillis();
            sound(SoundEvents.BLOCK_STONE_STEP, 1f, 0.35f);
        }
        if (type == Type.CAR) {
            if (nextSound == 0) {
                nextSound++;
                sound(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 1.5f, 1f);
            }
            if (nextSound == 1 && p > CAR_HIT) {
                nextSound++;
                sound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1f);
                sound(SoundEvents.ENTITY_PLAYER_BIG_FALL, 0.6f, 1f);
            }
            if (nextSound == 2 && p > 0.46f) {
                nextSound++;
                sound(SoundEvents.ENTITY_PLAYER_BIG_FALL, 0.8f, 0.9f);
            }
        }
        if (type == Type.NIGHT) {
            if (nextSound == 0 && p > NIGHT_STEP) {
                nextSound++;
                sound(SoundEvents.BLOCK_STONE_STEP, 0.6f, 0.5f);
            }
            if (nextSound == 1 && p > NIGHT_HIT) {
                nextSound++;
                sound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.7f, 1f);
            }
        }
        if (type == Type.WARD && !lightsOut && System.currentTimeMillis() - lastStep > 1100) {
            lastStep = System.currentTimeMillis();
            sound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 1.9f, 0.12f);
        }
        if (type == Type.JEALOUS && System.currentTimeMillis() - lastStep > (p > JEALOUS_ARM ? 520 : 800)) {
            lastStep = System.currentTimeMillis();
            sound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1f, 0.9f);
        }
        if (type == Type.WALK && heldSince == 0 && System.currentTimeMillis() - lastStep > 430 && blackness() < 0.5f) {
            lastStep = System.currentTimeMillis();
            sound(SoundEvents.BLOCK_STONE_STEP, 1f, 0.12f);
        }
        float[] shots = type == Type.BAT ? BAT_SHOTS : type == Type.NIGHT ? NIGHT_SHOTS : type == Type.CAR ? CAR_SHOTS : type == Type.JEALOUS ? JEALOUS_SHOTS : SHOT_TIMES;
        if (DevShots.ON && nextShot < shots.length && p > shots[nextShot]) {
            nextShot++;
            DevShots.take();
        }
        if (p >= 1f && type != Type.WARD) stop();
    }

    private static float yawOf(Vec3d d) {
        return (float) (MathHelper.atan2(d.z, d.x) * 180.0 / Math.PI) - 90f;
    }

    private static Key[] keys(Vec3d playerEye, float playerYaw, float playerPitch) {
        Vec3d dir = bedFoot.subtract(bedHead).normalize();
        Vec3d perp = new Vec3d(-dir.z, 0, dir.x);
        Vec3d mid = bedHead.add(bedFoot).multiply(0.5);
        if (type == Type.BAT) return batKeys(playerEye, playerYaw, playerPitch);
        if (type == Type.CAR) return carKeys(playerEye, playerYaw, playerPitch);
        if (type == Type.PEEK) {
            // Here standSpot is round the corner and bedFoot the way she went; walkPath is how you get there.
            Vec3d toHer = herPos.subtract(playerEye);
            float herYaw = yawOf(toHer);
            float herPitch = (float) -(MathHelper.atan2(toHer.y, toHer.horizontalLength()) * 180.0 / Math.PI);
            lookYaw = yawOf(bedFoot.subtract(standSpot));
            lookPitch = 0f;
            float walkFrom = walkStart(), walkTo = walkEnd();
            double length = 0;
            for (int i = 1; i < walkPath.size(); i++) length += walkPath.get(i).distanceTo(walkPath.get(i - 1));
            java.util.List<Key> all = new java.util.ArrayList<>();
            all.add(new Key(0f, playerEye, playerYaw, playerPitch));
            all.add(new Key(0.1f, playerEye, herYaw, herPitch));
            all.add(new Key(walkFrom - 0.01f, playerEye, herYaw, herPitch));
            double done = 0;
            for (int i = 0; i < walkPath.size(); i++) {
                if (i > 0) done += walkPath.get(i).distanceTo(walkPath.get(i - 1));
                Vec3d ahead = walkPath.get(Math.min(walkPath.size() - 1, i + 6)).subtract(walkPath.get(i));
                float yaw = ahead.horizontalLengthSquared() < 0.01 ? lookYaw : yawOf(ahead);
                float time = walkFrom + (float) (done / Math.max(0.1, length)) * (walkTo - walkFrom);
                all.add(new Key(time, feetSmoothed(i).add(0, STANDING_EYE, 0), yaw, 3f));
            }
            Vec3d round = walkPath.get(walkPath.size() - 1).add(0, STANDING_EYE, 0);
            float rest = (1f - walkTo) / 3f;
            all.add(new Key(walkTo + rest * 0.6f, round, lookYaw, 0f));
            all.add(new Key(walkTo + rest * 1.4f, round, lookYaw - 40f, 0f));
            all.add(new Key(walkTo + rest * 2.4f, round, lookYaw + 30f, 0f));
            all.add(new Key(1f, round, lookYaw, 0f));
            return all.toArray(new Key[0]);
        }
        if (type == Type.NIGHT) {
            return new Key[]{
                    new Key(0f, playerEye, playerYaw, playerPitch),
                    new Key(0.1f, playerEye, playerYaw, playerPitch),
                    new Key(0.28f, playerEye, playerYaw - 75f, 0f),
                    new Key(0.4f, playerEye, playerYaw - 75f, 0f),
                    new Key(0.6f, playerEye, playerYaw + 75f, 0f),
                    new Key(0.74f, playerEye, playerYaw + 75f, 0f),
                    new Key(1f, playerEye, playerYaw + 75f, 0f),
            };
        }
        if (type == Type.JEALOUS) {
            Vec3d toHer = herPos.subtract(playerEye);
            float herYaw = yawOf(toHer);
            float herPitch = (float) -(MathHelper.atan2(toHer.y, toHer.horizontalLength()) * 180.0 / Math.PI);
            return new Key[]{
                    new Key(0f, playerEye, playerYaw, playerPitch),
                    new Key(0.1f, playerEye, playerYaw, playerPitch),
                    new Key(0.2f, playerEye, herYaw, herPitch),
                    new Key(JEALOUS_ARM - 0.05f, playerEye, herYaw, herPitch),
                    new Key(JEALOUS_ARM + 0.04f, playerEye, playerYaw, 62f),
                    new Key(0.92f, playerEye, playerYaw, 64f),
                    new Key(1f, playerEye, playerYaw, playerPitch),
            };
        }
        // The way out of bed: towards the clear spot the server found beside it.
        Vec3d toSpot = new Vec3d(standSpot.x - mid.x, 0, standSpot.z - mid.z);
        Vec3d side = toSpot.lengthSquared() < 0.01 ? perp : toSpot.normalize();

        Vec3d lying = bedHead.add(dir.multiply(-0.1)).add(0, LYING_EYE, 0);
        Vec3d sitting = bedHead.add(dir.multiply(0.25)).add(0, SITTING_EYE, 0);
        Vec3d edge = mid.add(side.multiply(0.75)).add(0, SITTING_EYE - 0.1, 0);
        Vec3d standing = standSpot.add(0, STANDING_EYE, 0);
        float alongBed = yawOf(dir), outward = yawOf(side);

        if (type == Type.WARD) {
            // A little down the bed: there's a shelf over the pillow, and you'd be looking up at the underside of it.
            Vec3d onPillow = bedHead.add(dir.multiply(WARD_DOWN_THE_BED)).add(0, LYING_EYE, 0);
            Vec3d toThem = herPos.subtract(onPillow);
            float theirYaw = yawOf(toThem);
            float theirPitch = (float) -(MathHelper.atan2(toThem.y, toThem.horizontalLength()) * 180.0 / Math.PI);
            return new Key[]{
                    new Key(0f, onPillow, alongBed, -82f),
                    new Key(0.55f, onPillow, alongBed, -78f),
                    new Key(1f, onPillow, theirYaw, theirPitch),
            };
        }
        if (type == Type.SLEEP) {
            return new Key[]{
                    new Key(0f, playerEye, playerYaw, playerPitch),
                    new Key(0.28f, standing, yawOf(mid.subtract(standing)), 20f),
                    new Key(0.42f, edge, outward, 12f),
                    new Key(0.56f, sitting, alongBed, 15f),
                    new Key(0.74f, lying, alongBed, -82f),
                    new Key(1f, lying, alongBed, -82f),
            };
        }
        return new Key[]{
                new Key(0f, lying, alongBed, -82f),
                new Key(0.36f, lying, alongBed, -78f),
                new Key(0.52f, sitting, alongBed, 12f),
                new Key(0.60f, sitting, alongBed, 18f),
                new Key(0.72f, edge, outward, 25f),
                new Key(0.86f, standing, outward, 5f),
                new Key(1f, playerEye, playerYaw, playerPitch),
        };
    }

    /** You're looking ahead; you half turn; you go down on your side; you're turned towards her. */
    private static Key[] batKeys(Vec3d eye, float yaw, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d forward = Vec3d.fromPolar(0f, yaw);
        Vec3d floor = eye.subtract(0, STANDING_EYE - 0.22, 0);
        Vec3d ground = floor.add(forward.multiply(0.8));
        BlockPos at = BlockPos.ofFloored(ground);
        if (client.world != null && !client.world.getBlockState(at).getCollisionShape(client.world, at).isEmpty()) ground = floor;
        Vec3d toHer = herPos.add(0, 1.0, 0).subtract(ground);
        float herYaw = yawOf(toHer);
        float herPitch = (float) -(MathHelper.atan2(toHer.y, toHer.horizontalLength()) * 180.0 / Math.PI);
        return new Key[]{
                new Key(0f, eye, yaw, pitch),
                new Key(0.19f, eye, yaw, pitch),
                new Key(BAT_HIT - 0.005f, eye, yaw + 28f, pitch),
                new Key(BAT_HIT + 0.02f, eye.add(forward.multiply(0.15)).subtract(0, 0.12, 0), yaw + 18f, pitch + 12f, 8f),
                new Key(0.38f, ground, yaw + 50f, 38f, 82f),
                new Key(0.42f, ground.add(0, 0.07, 0), yaw + 47f, 32f, 76f),
                new Key(0.46f, ground, yaw + 50f, 35f, 80f),
                new Key(0.60f, ground, yaw + 50f, 35f, 80f),
                new Key(0.74f, ground, herYaw, herPitch, 24f),
                new Key(1f, ground, herYaw, herPitch, 20f),
        };
    }

    /** You look round at the horn, you're thrown back through the air, and you come down hard. */
    private static Key[] carKeys(Vec3d eye, float yaw, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d feet = eye.subtract(0, STANDING_EYE, 0);
        Vec3d toCar = herPos.subtract(feet);
        Vec3d away = new Vec3d(-toCar.x, 0, -toCar.z).normalize();
        float carYaw = yawOf(toCar);
        Vec3d floor = feet.add(0, 0.22, 0);
        Vec3d ground = floor.add(away.multiply(3.2));
        BlockPos at = BlockPos.ofFloored(ground);
        if (client.world != null && !client.world.getBlockState(at).getCollisionShape(client.world, at).isEmpty()) ground = floor.add(away.multiply(0.8));
        return new Key[]{
                new Key(0f, eye, yaw, pitch),
                new Key(0.1f, eye, carYaw, 4f),
                new Key(CAR_HIT, eye, carYaw, 4f),
                new Key(0.3f, feet.add(away.multiply(1.6)).add(0, 1.9, 0), carYaw + 150f, -35f, 50f),
                new Key(0.46f, ground, carYaw + 215f, 28f, 88f),
                new Key(0.5f, ground.add(0, 0.1, 0), carYaw + 212f, 24f, 80f),
                new Key(0.54f, ground, carYaw + 215f, 26f, 86f),
                new Key(1f, ground, carYaw + 215f, 26f, 86f),
        };
    }

    private static float walkStart() {
        return Peeker.WALK_AT * 50f / durationMs;
    }

    private static float walkEnd() {
        return 1f - Peeker.LOOK_TICKS * 50f / durationMs;
    }

    /** The ground under point i, averaged with its neighbours so stairs climb evenly. */
    private static Vec3d feetSmoothed(int i) {
        double y = 0;
        int from = Math.max(0, i - 3), to = Math.min(walkPath.size() - 1, i + 3);
        for (int n = from; n <= to; n++) y += walkPath.get(n).y;
        Vec3d at = walkPath.get(i);
        return new Vec3d(at.x, y / (to - from + 1), at.z);
    }

    /** Steady along the route, looking where you're going, and round at whoever's speaking. */
    private static double[] walkCamera() {
        float p = progress();
        Vec3d at = walker.at(p);
        float wanted = yawOf(walker.heading(p));
        // Downhill, you look where you're going: down the slope, not out at the sky.
        float pitch = 1f + (float) Math.toDegrees(Math.atan(Math.max(0, walker.drop(p)))) * 0.75f;
        MinecraftClient client = MinecraftClient.getInstance();
        if (speaker >= 0 && client.world != null) {
            Entity them = client.world.getEntityById(speaker);
            if (them != null) {
                Vec3d to = them.getPos().subtract(at);
                wanted = wanted + MathHelper.wrapDegrees(yawOf(to) - wanted) * 0.35f;
                pitch = 4f;
            }
        }
        long now = System.currentTimeMillis();
        float turn = 1f - (float) Math.exp(-(now - lastFrame) / 260.0);
        lastFrame = now;
        walkYaw += MathHelper.wrapDegrees(wanted - walkYaw) * turn;
        walkPitch += (pitch - walkPitch) * turn;
        pitch = walkPitch;
        double stride = (now - startMs) / 215.0;
        // Going down a slope this steep you're gliding, not stepping.
        boolean walking = heldSince == 0 && walker.drop(p) < 0.3;
        double bob = walking ? Math.abs(Math.sin(stride)) * STRIDE_BOB : 0;
        roll = walking ? (float) Math.sin(stride) * STRIDE_ROLL : 0f;
        Vec3d sway = Vec3d.fromPolar(0f, walkYaw + 90f).multiply(walking ? Math.sin(stride) * STRIDE_SWAY : 0);
        return new double[]{at.x + sway.x, at.y + STANDING_EYE + bob, at.z + sway.z, walkYaw, pitch};
    }

    /** Which way the train's facing: it goes round corners now, and you with it. */
    private static float carriageYaw() {
        return carriage instanceof com.simibubi.create.content.contraptions.OrientedContraptionEntity train ? train.getYaw() : 0f;
    }

    /** The train's body, however far down the line it's got. */
    private static Entity carriage(MinecraftClient client) {
        if (client.world == null) return null;
        for (Entity e : client.world.getEntities()) if (e instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity) return e;
        return null;
    }

    /** Carried along with the carriage, looking out of the window, and round at whoever's speaking. */
    private static double[] rideCamera(float tickDelta) {
        if (carriage == null || carriage.isRemoved()) carriage = carriage(MinecraftClient.getInstance());
        float turned = carriage == null ? 0f : MathHelper.wrapDegrees(carriageYaw() - rideFrom);
        Vec3d at = carriage == null ? standSpot : carriage.getLerpedPos(tickDelta).add(rideOffset.rotateY(-turned * MathHelper.RADIANS_PER_DEGREE));
        float wanted = RIDE_YAW + turned;
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        if (speaker >= 0 && now < speakerUntil && client.world != null && client.world.getEntityById(speaker) != null) wanted = RIDE_YAW + turned + 62f;
        float turn = 1f - (float) Math.exp(-(now - lastFrame) / 340.0);
        lastFrame = now;
        walkYaw += MathHelper.wrapDegrees(wanted - walkYaw) * turn;
        double rattle = Math.sin(now / 95.0) * RIDE_RATTLE;
        return new double[]{at.x, at.y + STANDING_EYE + rattle, at.z, walkYaw, 2f};
    }

    /** Where the camera is this frame: {x, y, z, yaw, pitch}. */
    public static double[] camera(Entity player, float tickDelta) {
        if (type == Type.BLACK) return new double[]{bedHead.x, bedHead.y + LYING_EYE, bedHead.z, 0, -82};
        if (type == Type.DRAG) return dragCamera();
        if (type == Type.TIED) {
            double shake = tiedShake == 0f ? 0 : Math.sin(System.currentTimeMillis() / 38.0) * 0.012 * tiedShake;
            roll = tiedRoll;
            return new double[]{standSpot.x + shake - tiedSlide, standSpot.y - tiedDrop, standSpot.z, tiedYaw, tiedPitch};
        }
        if (type == Type.WALK) return walkCamera();
        if (type == Type.RIDE) return rideCamera(tickDelta);
        if (type == Type.GRAB) {
            // Dragged backwards, head pulled up, fighting it. You can't turn round.
            long now = System.currentTimeMillis();
            struggling *= 0.9f;
            float wrench = (float) Math.sin(now / 37.0) * struggling * 7f, heave = (float) Math.sin(now / 280.0) * 1.6f;
            roll = heave + wrench * 0.6f;
            Vec3d back = Vec3d.fromPolar(0f, startYaw).multiply(-0.25 - Math.sin(now / 400.0) * 0.05);
            Vec3d at = startEye.add(back).add(0, -0.12, 0);
            return new double[]{at.x, at.y, at.z, startYaw + wrench, -16f + heave + wrench * 0.4f};
        }
        Vec3d eye = type == Type.WAKE ? player.getCameraPosVec(tickDelta) : startEye;
        float yawNow = type == Type.WAKE ? player.getYaw(tickDelta) : startYaw;
        float pitchNow = type == Type.WAKE ? player.getPitch(tickDelta) : startPitch;
        Key[] k = keys(eye, yawNow, pitchNow);
        float p = progress();
        int i = 0;
        while (i < k.length - 2 && p > k[i + 1].time()) i++;
        Key a = k[i], b = k[i + 1];
        float t = MathHelper.clamp((p - a.time()) / (b.time() - a.time()), 0f, 1f);
        // Walking is steady: easing every little stretch of it would make you lurch.
        if (type != Type.PEEK || p < walkStart() || p >= walkEnd()) t = t * t * (3 - 2 * t);
        Vec3d pos = a.pos().lerp(b.pos(), t);
        float yaw = a.yaw() + MathHelper.wrapDegrees(b.yaw() - a.yaw()) * t;
        float pitch = MathHelper.lerp(t, a.pitch(), b.pitch());
        roll = MathHelper.lerp(t, a.roll(), b.roll());
        if (type == Type.PEEK && p > walkStart() && p < walkEnd()) {
            // A sprinting stride.
            pos = pos.add(0, Math.abs(Math.sin((System.currentTimeMillis() - startMs) / 100.0)) * 0.1 - 0.05, 0);
        }
        if (type == Type.BAT || type == Type.CAR) {
            // The whole world jolts with the hit, then settles.
            float jolt = MathHelper.clamp(1f - Math.abs(p - (type == Type.CAR ? CAR_HIT : BAT_HIT)) / 0.09f, 0f, 1f) * 5f;
            yaw += (float) Math.sin(System.currentTimeMillis() / 17.0) * jolt;
            pitch += (float) Math.cos(System.currentTimeMillis() / 23.0) * jolt;
        }
        // Breathing, so a still shot isn't a frozen one.
        double breath = Math.sin((System.currentTimeMillis() - startMs) / 700.0) * 0.012;
        return new double[]{pos.x, pos.y + breath, pos.z, yaw, pitch};
    }
}
