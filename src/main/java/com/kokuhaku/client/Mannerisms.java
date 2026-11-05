package com.kokuhaku.client;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster;
import com.kokuhaku.game.Roster.Personality;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Nobody stands perfectly still. What people do with their hands when they're
 * doing nothing, how they carry themselves when they walk, how they sit through
 * a lesson. It's all worked out from who they are and the time, so there's
 * nothing to send over the network and no two of them move in step.
 */
final class Mannerisms {
    private enum Gesture { NOTHING, PHONE, CROSSED, HIP, LOOK_ROUND, STRETCH, YAWN, FIDDLE, NECK, CHECK_WATCH }

    private enum Sitting { WRITING, CHIN, BOARD, WINDOW }

    private static final int IDLE_SLOT = 170, SEAT_SLOT = 130, CHAT_SLOT = 90, GLANCE_SLOT = 150, GLANCE_LENGTH = 26;
    private static final float WALK_STARTS = 0.04f, WALK_FULL = 0.26f, FADE = 0.16f;
    private static final float CHANGE_RATE = 7f, TURN_RATE = 9f, SHUFFLE_FROM = 45f, SHUFFLE_BELOW = 0.1f, STRIDE_RATE = 0.6662f;
    private static final float RIDING_ARM = 0.6283f;
    private static final float SIT_SECONDS = 1.5f, STAND_SECONDS = 0.6f, STEP_PART = 0.45f, SEAT_HEIGHT = 0.5f, OVER_THE_CHAIR = 0.42f, FURTHEST_STEP = 3.2f, STEP_RATE = 0.8f, STEP_SIZE = 0.42f;
    private static final int LUNCH_SLOT = 120, HALL_FLOOR = 25, HALL_WEST = -601, HALL_EAST = -585, HALL_NORTH = -120, HALL_SOUTH = -102;

    /** Everyone goes from one thing to the next at their own pace, so each of them keeps track of how far they've got. */
    private static final class Easing {
        float sat, chat, speak, wave, turn, lastYaw, clock, lastTurn, lastMoved;
        long last, lastFrame;
        boolean wasSat;
        /** Where they last stood, and where they stepped off from to sit. */
        Vec3d stood, from;
    }

    private static final Map<StudentEntity, Easing> EASING = new WeakHashMap<>();

    private Mannerisms() {
    }

    private static float toward(float now, float want, float rate, float seconds) {
        return now + (want - now) * (1f - (float) Math.exp(-rate * seconds));
    }

    private static Easing easing(StudentEntity e) {
        return EASING.computeIfAbsent(e, k -> {
            Easing s = new Easing();
            s.sat = k.isSittingDown() ? 1f : 0f;
            s.clock = s.sat;
            s.wasSat = k.isSittingDown();
            s.lastYaw = k.bodyYaw;
            s.last = s.lastFrame = System.currentTimeMillis();
            return s;
        });
    }

    /** How far down into the seat they've got, 0..1. */
    static float sat(StudentEntity e) {
        return easing(e).sat;
    }

    /** Each frame, before anything's drawn: step into the seat first, then lower onto it. Getting up is quicker. */
    static void frame(StudentEntity e, Vec3d at) {
        Easing s = easing(e);
        long now = System.currentTimeMillis();
        float seconds = Math.min(0.1f, (now - s.lastFrame) / 1000f);
        s.lastFrame = now;
        boolean sitting = e.isSittingDown();
        if (HEAD_TEST && sitting) headTest(e, s);
        if (sitting && !s.wasSat) {
            // Too far to have walked it: they were put there, so they're simply sat.
            s.from = s.stood != null && s.stood.squaredDistanceTo(at) < FURTHEST_STEP * FURTHEST_STEP ? s.stood : null;
            s.clock = s.from == null ? 1f : 0f;
        }
        s.wasSat = sitting;
        if (sitting) s.clock = Math.min(1f, s.clock + seconds / SIT_SECONDS);
        else {
            s.clock = Math.max(0f, s.clock - seconds / STAND_SECONDS);
            s.stood = at;
            s.from = null;
        }
        s.sat = ease((s.clock - STEP_PART) / (1f - STEP_PART));
    }

    /** Developer testing only: how often a seated head changes direction hard from one frame to the next. */
    private static final boolean HEAD_TEST = "lunchscene".equals(System.getenv("KOKUHAKU_STAGE"));
    private static int frames = 0, reversals = 0;

    private static void headTest(StudentEntity e, Easing s) {
        float turn = MathHelper.wrapDegrees(e.headYaw - e.bodyYaw), moved = turn - s.lastTurn;
        if (Math.abs(moved) > 2f && Math.abs(s.lastMoved) > 2f && Math.signum(moved) != Math.signum(s.lastMoved)) reversals++;
        s.lastMoved = moved;
        s.lastTurn = turn;
        if (++frames % 20000 == 0) com.kokuhaku.Kokuhaku.LOG.info("HEADS {} reversals in {} frames", reversals, frames);
    }

    /** Where they are on their way into the seat, from where they really are. A little up and over, so they don't go through the chair. */
    static Vec3d seatShift(StudentEntity e, Vec3d at) {
        Easing s = easing(e);
        if (!e.isSittingDown() || s.from == null || s.clock >= 1f) return null;
        Vec3d floor = at.add(0, -SEAT_HEIGHT, 0);
        if (s.clock >= STEP_PART) return new Vec3d(0, -SEAT_HEIGHT * (1f - s.sat), 0);
        float t = ease(s.clock / STEP_PART);
        return s.from.lerp(floor, t).add(0, MathHelper.sin(MathHelper.PI * t) * OVER_THE_CHAIR, 0).subtract(at);
    }

    private static float walkAmount(float limbSpeed) {
        return ease((limbSpeed - WALK_STARTS) / (WALK_FULL - WALK_STARTS));
    }

    private static int mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        return (int) (h & Integer.MAX_VALUE);
    }

    private static Personality kind(StudentEntity e) {
        return e.getSkin() < Roster.TOTAL ? Roster.get(e.getSkin()).personality() : Personality.POLITE;
    }

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Up, held, and back down again over a slot. */
    private static float held(float t) {
        return ease(t / FADE) * ease((1f - t) / FADE);
    }

    private static void arm(ModelPart part, float weight, float pitch, float yaw, float roll) {
        part.pitch = MathHelper.lerp(weight, part.pitch, pitch);
        part.yaw = MathHelper.lerp(weight, part.yaw, yaw);
        part.roll = MathHelper.lerp(weight, part.roll, roll);
    }

    private static Gesture idleGesture(Personality kind, int roll) {
        Gesture[] mine = switch (kind) {
            case CHEERFUL -> new Gesture[]{Gesture.LOOK_ROUND, Gesture.PHONE, Gesture.STRETCH, Gesture.HIP};
            case SHY -> new Gesture[]{Gesture.FIDDLE, Gesture.FIDDLE, Gesture.NECK, Gesture.PHONE};
            case COLD -> new Gesture[]{Gesture.CROSSED, Gesture.CROSSED, Gesture.PHONE, Gesture.YAWN};
            case GOSSIP -> new Gesture[]{Gesture.PHONE, Gesture.PHONE, Gesture.HIP, Gesture.LOOK_ROUND};
            case POLITE -> new Gesture[]{Gesture.FIDDLE, Gesture.CHECK_WATCH, Gesture.LOOK_ROUND, Gesture.YAWN};
            case SPORTY -> new Gesture[]{Gesture.STRETCH, Gesture.NECK, Gesture.HIP, Gesture.YAWN};
        };
        // Often enough, nothing at all: that's most of what standing around is.
        return roll % 10 < 4 ? Gesture.NOTHING : mine[roll % mine.length];
    }

    private static void idle(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount) {
        long slot = (long) (clock / IDLE_SLOT);
        float t = (clock % IDLE_SLOT) / IDLE_SLOT, w = held(t) * amount;
        // Feet turned out a little, the knee on the resting side gone soft, and eyes that don't stay in one place.
        float side = MathHelper.sin((age + e.getId() * 91f) * 0.017f);
        m.rightLeg.yaw += 0.07f * amount;
        m.leftLeg.yaw -= 0.07f * amount;
        m.rightLeg.pitch -= 0.08f * Math.max(0f, side) * amount;
        m.leftLeg.pitch -= 0.08f * Math.max(0f, -side) * amount;
        m.head.yaw += MathHelper.sin(clock * 0.013f) * 0.09f * amount;
        m.head.pitch += MathHelper.sin(clock * 0.019f) * 0.03f * amount;
        Gesture g = idleGesture(kind(e), mix(slot, e.getId()));
        if (e.getMood() == StudentEntity.GRIEVING && g != Gesture.NOTHING) g = Gesture.FIDDLE;
        switch (g) {
            case PHONE -> {
                arm(m.rightArm, w, -1.15f, -0.4f, 0f);
                arm(m.leftArm, w, -0.9f, 0.3f, 0f);
                m.head.pitch += 0.5f * w;
            }
            case CROSSED -> {
                arm(m.rightArm, w, -0.95f, -0.72f, 0f);
                arm(m.leftArm, w, -0.8f, 0.72f, 0f);
                m.head.yaw += MathHelper.sin(clock * 0.03f) * 0.35f * w;
            }
            case HIP -> arm(m.rightArm, w, -0.35f, 0.5f, 0.6f);
            case LOOK_ROUND -> {
                m.head.yaw += MathHelper.sin(clock * 0.05f) * 0.75f * w;
                m.head.pitch += MathHelper.sin(clock * 0.031f) * 0.14f * w;
            }
            case STRETCH -> {
                arm(m.rightArm, w, -2.95f, -0.15f, 0.12f);
                arm(m.leftArm, w, -2.95f, 0.15f, -0.12f);
                m.head.pitch -= 0.22f * w;
            }
            case YAWN -> {
                arm(m.rightArm, w, -2.05f, -0.55f, 0f);
                m.head.pitch -= 0.2f * w;
            }
            case FIDDLE -> {
                float fuss = MathHelper.sin(clock * 0.4f) * 0.06f;
                arm(m.rightArm, w, -0.55f + fuss, -0.42f, 0f);
                arm(m.leftArm, w, -0.55f - fuss, 0.42f, 0f);
                m.head.pitch += 0.28f * w;
            }
            case NECK -> {
                arm(m.rightArm, w, -2.55f, -0.1f, -0.4f);
                m.head.roll += 0.2f * w;
            }
            case CHECK_WATCH -> {
                arm(m.leftArm, w, -1.3f, 0.5f, 0f);
                m.head.pitch += 0.42f * w;
                m.head.yaw += 0.25f * w;
            }
            default -> {
            }
        }
    }

    /** The stride's own shape: the hips go round, the shoulders go the other way, and the head stays level. */
    private static void gait(PlayerEntityModel<StudentEntity> m, StudentEntity e, float limbPos, float amount) {
        float step = switch (kind(e)) {
            case SHY -> 0.72f;
            case COLD -> 0.84f;
            case SPORTY -> 1.06f;
            default -> 0.92f;
        };
        float phase = limbPos * STRIDE_RATE, turn = MathHelper.sin(phase) * 0.09f * amount;
        m.rightLeg.pitch *= MathHelper.lerp(amount, 1f, step);
        m.leftLeg.pitch *= MathHelper.lerp(amount, 1f, step);
        m.body.yaw += turn;
        m.rightArm.pivotZ += MathHelper.sin(turn) * 5f;
        m.leftArm.pivotZ -= MathHelper.sin(turn) * 5f;
        m.rightArm.pitch -= 0.07f * amount;
        m.leftArm.pitch -= 0.07f * amount;
        m.head.yaw -= turn * 0.6f;
    }

    private static void walking(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        Personality kind = kind(e);
        float swing = switch (kind) {
            case SHY -> 0.45f;
            case COLD -> 0.22f;
            case SPORTY -> 1.2f;
            case CHEERFUL -> 1.05f;
            default -> 0.8f;
        };
        m.rightArm.pitch *= MathHelper.lerp(amount, 1f, swing);
        m.leftArm.pitch *= MathHelper.lerp(amount, 1f, swing);
        m.head.pitch += amount * switch (kind) {
            case SHY -> 0.24f;
            case COLD -> 0.08f;
            case SPORTY -> -0.04f;
            default -> 0.02f;
        };
        if (e.getMood() == StudentEntity.GRIEVING) m.head.pitch += 0.26f * amount;
        // By the third week, nobody walks anywhere without checking behind them.
        if (e.getMood() == StudentEntity.AFRAID) {
            long slot = (long) (clock / GLANCE_SLOT);
            float at = clock % GLANCE_SLOT;
            int roll = mix(slot, e.getId() * 31L);
            if (roll % 3 == 0 && at < GLANCE_LENGTH) m.head.yaw += (roll % 2 == 0 ? 1f : -1f) * 1.05f * held(at / GLANCE_LENGTH) * amount;
        }
        if (speaking > 0.01f) {
            arm(m.rightArm, 0.6f * speaking * amount, -0.65f + MathHelper.sin(age * 0.3f) * 0.3f, -0.2f, 0f);
            m.head.pitch += MathHelper.sin(age * 0.26f) * 0.04f * speaking * amount;
        }
    }

    /** At a table in the hall: picking at their food, talking with their mouth half full, watching whoever's speaking. */
    private static void atLunch(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        long slot = (long) (clock / LUNCH_SLOT);
        float t = (clock % LUNCH_SLOT) / LUNCH_SLOT, w = held(t) * amount * (1f - speaking);
        int roll = mix(slot, e.getId() * 13L);
        // Hands on the table to begin with: that's where they come back to.
        arm(m.rightArm, amount, -0.85f, -0.14f, 0f);
        arm(m.leftArm, amount, -0.8f, 0.14f, 0f);
        switch (roll % 5) {
            case 0, 1 -> {
                // A mouthful: down to the tray, up, and chew.
                float bite = Math.max(0f, MathHelper.sin(clock * 0.14f));
                arm(m.rightArm, w, MathHelper.lerp(bite * bite, -0.85f, -1.95f), -0.3f, 0f);
                m.head.pitch += (0.26f - 0.16f * bite) * w;
                m.head.pitch += MathHelper.sin(age * 0.85f) * 0.018f * w;
            }
            case 2 -> {
                arm(m.leftArm, w, -2.05f, 0.34f, 0f);
                m.head.pitch -= 0.26f * w;
            }
            case 3 -> m.head.yaw += (roll % 2 == 0 ? 0.65f : -0.65f) * w;
            default -> m.head.pitch += (0.2f + MathHelper.sin(clock * 0.1f) * 0.06f) * w;
        }
        if (speaking > 0.01f) {
            float talking = amount * speaking;
            arm(m.rightArm, talking, -1.05f + MathHelper.sin(age * 0.33f) * 0.3f, -0.25f + MathHelper.sin(age * 0.19f) * 0.18f, 0f);
            m.head.pitch += MathHelper.sin(age * 0.24f) * 0.05f * talking;
        }
    }

    private static boolean inLunchHall(StudentEntity e) {
        return e.getBlockY() >= HALL_FLOOR && e.getBlockY() <= HALL_FLOOR + 1 && e.getX() >= HALL_WEST && e.getX() <= HALL_EAST && e.getZ() >= HALL_NORTH && e.getZ() <= HALL_SOUTH;
    }

    private static void seated(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        if (inLunchHall(e)) {
            atLunch(m, e, clock, age, amount, speaking);
            return;
        }
        long slot = (long) (clock / SEAT_SLOT);
        float t = (clock % SEAT_SLOT) / SEAT_SLOT, w = held(t) * amount;
        int roll = mix(slot, e.getId() * 7L);
        Sitting what = Sitting.values()[kind(e) == Personality.SPORTY && roll % 2 == 0 ? 3 : roll % Sitting.values().length];
        switch (what) {
            case WRITING -> {
                arm(m.rightArm, w, -0.95f + MathHelper.sin(clock * 1.1f) * 0.04f, -0.25f + MathHelper.sin(clock * 0.45f) * 0.07f, 0f);
                arm(m.leftArm, w, -0.85f, 0.2f, 0f);
                m.head.pitch += 0.46f * w;
            }
            case CHIN -> {
                arm(m.leftArm, w, -2.05f, 0.5f, 0f);
                m.head.roll -= 0.14f * w;
                m.head.pitch += 0.08f * w;
            }
            case WINDOW -> m.head.yaw += (roll % 4 < 2 ? 0.7f : -0.7f) * w;
            default -> m.head.pitch += MathHelper.sin(clock * 0.02f) * 0.05f * amount;
        }
    }

    private static void chatting(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        long slot = (long) (clock / CHAT_SLOT);
        int roll = mix(slot, e.getId() * 11L);
        float talking = amount * speaking, listening = amount * (1f - speaking);
        if (talking > 0.01f) {
            float hands = (kind(e) == Personality.COLD || kind(e) == Personality.SHY ? 0.45f : 1f) * talking;
            arm(m.rightArm, hands, -0.6f + MathHelper.sin(age * 0.33f) * 0.34f, -0.25f + MathHelper.sin(age * 0.19f) * 0.2f, 0f);
            // The other hand joins in and drops out gently, not on the turn of the slot.
            float other = hands * held((clock % CHAT_SLOT) / CHAT_SLOT);
            if (roll % 3 == 0) arm(m.leftArm, other, -0.5f + MathHelper.sin(age * 0.27f + 1f) * 0.28f, 0.25f, 0f);
            m.head.pitch += MathHelper.sin(age * 0.24f) * 0.05f * talking;
        }
        if (listening > 0.01f) {
            // Listening: a nod now and then, and the odd laugh.
            float t = (clock % CHAT_SLOT) / CHAT_SLOT, w = held(t) * listening;
            if (roll % 3 == 0) m.head.pitch += MathHelper.sin(age * 0.42f) * 0.09f * w;
            if (roll % 7 == 1) m.head.pitch -= (0.2f + MathHelper.sin(age * 1.1f) * 0.05f) * w;
            m.head.roll += 0.07f * (roll % 2 == 0 ? 1f : -1f) * w;
        }
    }


    /** A hand up, and a couple of waggles. */
    private static void waving(PlayerEntityModel<StudentEntity> m, float age, float amount) {
        arm(m.leftArm, amount, -2.75f, 0f, -0.2f + MathHelper.sin(age * 0.8f) * 0.3f);
    }

    /** Everything but the head, which the caller has already pointed at what they're looking at. */
    static void apply(PlayerEntityModel<StudentEntity> m, StudentEntity e, float age, float limbPos, float limbSpeed, boolean sitting) {
        float clock = age + e.getId() * 173f;
        Easing s = easing(e);
        long now = System.currentTimeMillis();
        float seconds = Math.min(0.1f, (now - s.last) / 1000f);
        s.last = now;

        s.chat = toward(s.chat, e.isChatting() ? 1f : 0f, CHANGE_RATE, seconds);
        s.speak = toward(s.speak, e.isSpeaking() ? 1f : 0f, CHANGE_RATE, seconds);
        s.wave = toward(s.wave, e.isWaving() ? 1f : 0f, CHANGE_RATE, seconds);
        float turning = seconds > 0f ? Math.abs(MathHelper.wrapDegrees(e.bodyYaw - s.lastYaw)) / seconds : 0f;
        s.lastYaw = e.bodyYaw;
        s.turn = toward(s.turn, turning > SHUFFLE_FROM && limbSpeed < SHUFFLE_BELOW ? 1f : 0f, TURN_RATE, seconds);

        // Sitting down and standing up go through the pose, not round it.
        float up = 1f - s.sat;
        if (up > 0.001f && m.riding) {
            m.rightLeg.pitch *= s.sat;
            m.leftLeg.pitch *= s.sat;
            m.rightLeg.yaw *= s.sat;
            m.leftLeg.yaw *= s.sat;
            m.rightArm.pitch += RIDING_ARM * up;
            m.leftArm.pitch += RIDING_ARM * up;
        }
        float walk = walkAmount(limbSpeed) * up;
        if (s.sat > 0.01f) seated(m, e, clock, age, s.sat, s.speak);
        if (walk > 0.001f) {
            walking(m, e, clock, age, walk, s.speak);
            gait(m, e, limbPos, walk);
        }
        float still = (1f - walk) * up;
        if (still > 0.001f) {
            idle(m, e, clock, age, still * (1f - s.chat));
            chatting(m, e, clock, age, still * s.chat, s.speak);
        }
        // Turning on the spot takes your feet with you.
        float stepping = sitting && s.from != null && s.clock < STEP_PART ? STEP_SIZE * MathHelper.sin(MathHelper.PI * s.clock / STEP_PART) : 0f;
        float shuffle = MathHelper.sin(age * 0.95f) * 0.32f * s.turn * up + MathHelper.sin(age * STEP_RATE) * stepping;
        m.rightLeg.pitch += shuffle;
        m.leftLeg.pitch -= shuffle;
        if (s.wave > 0.01f) waving(m, age, s.wave);

        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }

    /** Side to side, degrees: standing on one leg more than the other, or rolling from foot to foot as they walk. */
    static float lean(StudentEntity e, float age, float limbPos, float limbSpeed, boolean sitting) {
        if (sitting) return 0f;
        float walk = walkAmount(limbSpeed), sway = kind(e) == Personality.COLD ? 0.7f : kind(e) == Personality.SPORTY ? 1.8f : 1.3f;
        return MathHelper.lerp(walk, MathHelper.sin((age + e.getId() * 91f) * 0.017f) * 1.5f, MathHelper.cos(limbPos * STRIDE_RATE) * sway);
    }

    /** Up onto each step and down off it. */
    static float bob(StudentEntity e, float limbPos, float limbSpeed) {
        float height = kind(e) == Personality.SPORTY ? 0.05f : kind(e) == Personality.COLD ? 0.02f : 0.035f;
        return Math.abs(MathHelper.sin(limbPos * STRIDE_RATE)) * height * walkAmount(limbSpeed);
    }
}
