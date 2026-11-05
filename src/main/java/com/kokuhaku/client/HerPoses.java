package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Everything to do with being taken: her hauling you down the hall, you limp on
 * the floor behind her, you tied to the chair, and her in the room, who's never
 * still and who answers what you say with her whole body.
 */
final class HerPoses {
    /** What she's doing about what you just said. */
    static final int AT_EASE = 0, DELIGHTED = 1, HURT = 2, LEANING_IN = 3;

    private enum Idle { BREATHE, HAIR, BEHIND_HER_BACK, LOOKS_AT_THE_ROPES, HANDS_TOGETHER, TILTS, SWAYS }

    private static final int SLOT = 150;
    private static final float FADE = 0.18f, PX = 1f / 16f;
    private static final Identifier ROPE = new Identifier(KokuhakuClient.MOD_ID, "textures/entity/rope.png");

    private HerPoses() {
    }

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void arm(ModelPart part, float w, float pitch, float yaw, float roll) {
        part.pitch = MathHelper.lerp(w, part.pitch, pitch);
        part.yaw = MathHelper.lerp(w, part.yaw, yaw);
        part.roll = MathHelper.lerp(w, part.roll, roll);
    }

    private static void sleeves(PlayerEntityModel<StudentEntity> m) {
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.jacket.copyTransform(m.body);
    }

    /** Going backwards with your whole weight in one hand: heaving, stepping, and checking what's behind her. */
    static void hauling(PlayerEntityModel<StudentEntity> m, float age, float pull, float look, int foot) {
        float behind = Math.abs(look), shake = MathHelper.sin(age * 2.3f) * 0.035f * pull;
        // Her body goes back as she heaves, so the arm has further to reach.
        arm(m.rightArm, 1f, -0.55f - 0.55f * pull + shake, -0.1f, 0f);
        // The free arm goes out behind her for balance, then feels for the wall.
        arm(m.leftArm, 1f, 0.1f + 0.5f * pull + 0.65f * behind, 0f, -0.14f - 0.2f * behind);
        // Eyes on you, teeth gritted when she heaves, and over her shoulder when she stops.
        m.head.pitch = MathHelper.lerp(behind, -0.12f + 0.25f * pull, -0.3f);
        m.head.yaw = MathHelper.lerp(behind, MathHelper.sin(age * 0.05f) * 0.1f, look * 1.35f);
        m.head.roll = MathHelper.sin(age * 0.03f) * 0.1f * (1f - behind) + shake;
        // One heel dug in behind her, the other planted, changing feet each heave.
        m.rightLeg.pitch = foot * 0.5f * pull;
        m.leftLeg.pitch = -foot * 0.42f * pull;
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
        sleeves(m);
    }
    /** Dragged by the feet: your arms trail back past your head, and your head rolls with the floor. */
    static void dragged(PlayerEntityModel<StudentEntity> m, float age) {
        // Your feet are up in her hands. You're looking out of the head.
        m.head.visible = false;
        m.hat.visible = false;
        // One foot up in her hand. The other drags.
        m.leftLeg.pitch = -0.6f;
        m.rightLeg.pitch = -0.06f + MathHelper.sin(age * 0.5f) * 0.05f;
        m.rightLeg.yaw = 0f;
        m.leftLeg.yaw = 0f;
        arm(m.rightArm, 1f, -2.9f + MathHelper.sin(age * 0.3f) * 0.06f, 0f, 0.22f);
        arm(m.leftArm, 1f, -2.95f + MathHelper.sin(age * 0.26f + 1f) * 0.06f, 0f, -0.22f);
        m.head.pitch = 0.2f;
        m.head.yaw = MathHelper.sin(age * 0.33f) * 0.45f;
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
        sleeves(m);
    }

    /** Sat in the chair with your hands behind you. Only your own body: you're looking out of the head. */
    static void tied(PlayerEntityModel<StudentEntity> m) {
        m.head.visible = false;
        m.hat.visible = false;
        arm(m.rightArm, 1f, 0.45f, 0f, 0.1f);
        arm(m.leftArm, 1f, 0.45f, 0f, -0.1f);
        // Your legs are tied together.
        m.rightLeg.yaw = 0f;
        m.leftLeg.yaw = 0f;
        m.rightLeg.roll = 0f;
        m.leftLeg.roll = 0f;
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
        sleeves(m);
    }

    private static Idle idleNow(long slot, float age) {
        Idle[] all = Idle.values();
        int roll = (int) ((slot * 2654435761L) >>> 7 & Integer.MAX_VALUE);
        return all[roll % all.length];
    }

    private static void idle(PlayerEntityModel<StudentEntity> m, float age, float how) {
        long slot = (long) (age / SLOT);
        float t = (age % SLOT) / SLOT, w = ease(t / FADE) * ease((1f - t) / FADE) * how;
        switch (idleNow(slot, age)) {
            case HAIR -> {
                arm(m.rightArm, w, -2.55f, -0.35f, -0.3f + MathHelper.sin(age * 0.5f) * 0.1f);
                m.head.roll += 0.14f * w;
            }
            case BEHIND_HER_BACK -> {
                arm(m.rightArm, w, 0.5f, 0f, 0.14f);
                arm(m.leftArm, w, 0.5f, 0f, -0.14f);
            }
            case LOOKS_AT_THE_ROPES -> {
                m.head.pitch += 0.55f * w;
                m.head.yaw += MathHelper.sin(age * 0.06f) * 0.25f * w;
            }
            case HANDS_TOGETHER -> {
                float fuss = MathHelper.sin(age * 0.35f) * 0.05f;
                arm(m.rightArm, w, -0.75f + fuss, -0.48f, 0f);
                arm(m.leftArm, w, -0.75f - fuss, 0.48f, 0f);
            }
            case TILTS -> m.head.roll += MathHelper.sin(age * 0.04f) * 0.32f * w;
            case SWAYS -> m.head.yaw += MathHelper.sin(age * 0.05f) * 0.3f * w;
            default -> {
            }
        }
    }

    /** Her in the room. weight is how far into the reaction she is, so nothing ever snaps. */
    static void inTheRoom(PlayerEntityModel<StudentEntity> m, float age, int mood, float weight, float idling, float winded) {
        idle(m, age, idling);
        // She's breathing. It's the first thing you notice. After the bat, you can see it from across the room.
        float breath = MathHelper.sin(age * (0.09f + 0.16f * winded)), deep = 1f + 3f * winded;
        m.rightArm.roll += breath * 0.025f * deep;
        m.leftArm.roll -= breath * 0.025f * deep;
        m.head.pitch += breath * 0.018f * deep;
        switch (mood) {
            case DELIGHTED -> {
                float bounce = MathHelper.sin(age * 0.55f) * 0.05f;
                arm(m.rightArm, weight, -1.9f + bounce, -0.5f, 0.1f);
                arm(m.leftArm, weight, -1.9f - bounce, 0.5f, -0.1f);
                m.head.pitch -= 0.1f * weight;
                m.head.roll += MathHelper.sin(age * 0.2f) * 0.12f * weight;
            }
            case HURT -> {
                arm(m.rightArm, weight, -1.15f, -0.35f, 0f);
                arm(m.leftArm, weight, 0.05f, 0f, -0.05f);
                m.head.pitch += 0.5f * weight;
                m.head.yaw += 0.2f * weight;
            }
            case LEANING_IN -> {
                arm(m.rightArm, weight, -1.1f, -0.1f, 0f);
                m.head.pitch += 0.2f * weight;
                m.head.roll += 0.2f * weight;
            }
            default -> {
            }
        }
        sleeves(m);
    }

    /**
     * Walking, lying on her front, or sat on the floor. Lying down goes in stages:
     * onto her knees, hands to the floor, down onto her front, and the feet come up last.
     */
    static void placed(PlayerEntityModel<StudentEntity> m, float age, float stride, float phase, float kneel, float flat, float kick, float sit) {
        if (stride > 0.01f) {
            float swing = MathHelper.sin(phase) * 0.62f * stride;
            m.rightLeg.pitch = swing;
            m.leftLeg.pitch = -swing;
            m.rightArm.pitch -= swing * 0.6f;
            m.leftArm.pitch += swing * 0.6f;
        }
        if (kneel > 0.01f) {
            float hands = ease(flat / 0.5f), under = ease((flat - 0.5f) / 0.5f);
            arm(m.rightArm, hands, -1.3f, -0.2f, 0f);
            arm(m.leftArm, hands, -1.3f, 0.2f, 0f);
            arm(m.rightArm, under, -2.2f, -0.42f, 0f);
            arm(m.leftArm, under, -2.2f, 0.42f, 0f);
            m.head.pitch = MathHelper.lerp(flat, m.head.pitch, -1.7f);
            m.head.yaw = MathHelper.lerp(flat, m.head.yaw, 0f);
            m.head.roll = MathHelper.lerp(kick, m.head.roll, MathHelper.sin(age * 0.11f) * 0.42f);
            float right = 0.75f + MathHelper.sin(age * 0.2f) * 0.42f, left = 0.75f + MathHelper.sin(age * 0.2f + 2.4f) * 0.42f;
            m.rightLeg.pitch = MathHelper.lerp(kick, 0.18f * flat, right);
            m.leftLeg.pitch = MathHelper.lerp(kick, 0.18f * flat, left);
        }
        if (sit > 0.01f) {
            // On the floor at your feet: down first, then the arms come round her knees, then she looks up.
            float arms = ease((sit - 0.5f) / 0.5f), up = ease((sit - 0.7f) / 0.3f);
            m.rightLeg.pitch = MathHelper.lerp(sit, m.rightLeg.pitch, -1.35f);
            m.leftLeg.pitch = MathHelper.lerp(sit, m.leftLeg.pitch, -1.35f);
            m.rightLeg.yaw = 0.22f * sit;
            m.leftLeg.yaw = -0.22f * sit;
            arm(m.rightArm, arms, -1.25f, -0.32f, 0f);
            arm(m.leftArm, arms, -1.25f, 0.32f, 0f);
            m.head.pitch -= 0.38f * up;
            m.head.roll += MathHelper.sin(age * 0.05f) * 0.2f * up;
        }
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
        sleeves(m);
    }

    /** She can't stand quite still: a slow shift from one foot to the other. */
    static float lean(float age) {
        return MathHelper.sin(age * 0.022f) * 2.2f;
    }

    private static void band(VertexConsumer vc, Matrix4f m, Matrix3f n, float halfX, float front, float back, float top, float bottom, int light) {
        float[][][] faces = {
                {{-halfX, top, front}, {halfX, top, front}, {halfX, bottom, front}, {-halfX, bottom, front}},
                {{halfX, top, front}, {halfX, top, back}, {halfX, bottom, back}, {halfX, bottom, front}},
                {{-halfX, top, back}, {-halfX, top, front}, {-halfX, bottom, front}, {-halfX, bottom, back}}};
        for (float[][] face : faces) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, face[i][0] * PX, face[i][1] * PX, face[i][2] * PX).color(255, 255, 255, 255).texture(i == 1 || i == 2 ? 1f : 0f, i < 2 ? 0f : 1f)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0f, 0f, -1f).next();
            }
        }
    }

    /** The matrix is already on your body: round your chest, your arms, and your waist. */
    static void ropes(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ROPE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        Matrix3f n = matrices.peek().getNormalMatrix();
        band(vc, m, n, 8.2f, -3.2f, 3.2f, 1.2f, 4.2f, light);
        band(vc, m, n, 8.2f, -3.2f, 3.2f, 5.6f, 8.6f, light);
        band(vc, m, n, 4.8f, -2.9f, 2.9f, 9.2f, 12.0f, light);
    }

    /** The matrix is on your right leg: one rope round both knees, and one lower down. */
    static void legRopes(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ROPE));
        matrices.translate(2f * PX, 0f, 0f);
        Matrix4f m = matrices.peek().getPositionMatrix();
        Matrix3f n = matrices.peek().getNormalMatrix();
        band(vc, m, n, 4.8f, -2.8f, 2.8f, 2.4f, 5.2f, light);
        band(vc, m, n, 4.8f, -2.8f, 2.8f, 7.6f, 10.4f, light);
    }
}
