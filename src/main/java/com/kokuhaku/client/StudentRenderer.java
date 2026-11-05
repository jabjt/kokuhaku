package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster;
import com.kokuhaku.game.Traits;
import com.kokuhaku.item.ModItems;
import com.kokuhaku.screen.JumpscareScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import com.kokuhaku.world.MenuRoom;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Random;

/** Draws students with the player model. The menu girl also gets the glitch-spin. */
public class StudentRenderer extends EntityRenderer<StudentEntity> {
    private final PlayerEntityModel<StudentEntity> model;
    private final Random random = new Random();
    private static final double FACE_DISTANCE = 0.62;
    /** How far through a throw the bat leaves her hand. */
    public static final float RELEASE = 0.5f;
    private static final float BAT_HANGS = 55f, WALK_BOB = 0.04f;
    /** Hips on the seat, which puts their eyes level with yours when you're sat down too. */
    private static final double SEATED_LOW = 0.62, LABEL_AT = 2.05;
    private static final float LABEL_SIZE = 0.72f;
    private static final double TOO_CLOSE_FOR_A_NAME = 4;
    /** From your middle to the floor under the chair's left edge. */
    private static final float FALL_PIVOT_X = -0.5f, FALL_PIVOT_Y = -0.25f;
    private static final Identifier HEART = new Identifier(KokuhakuClient.MOD_ID, "textures/entity/heart.png");
    private final HeldItemRenderer heldItems;
    private final GlassesModel glasses = new GlassesModel();

    /** Once you've gone over you're nowhere near your own hitbox, and you still need to be drawn. */
    @Override
    public boolean shouldRender(StudentEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
        return JumpscareScreen.youFall > 0f && entity.getPeek() == StudentEntity.TIED || super.shouldRender(entity, frustum, x, y, z);
    }

    public StudentRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.heldItems = ctx.getHeldItemRenderer();
        this.model = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.5f;
    }

    @Override
    public Identifier getTexture(StudentEntity entity) {
        return StudentSkins.texture(skinOf(entity));
    }

    private int skinOf(StudentEntity entity) {
        if (MenuScene.isMenuGirl(entity)) return MenuScene.cycle().girl();
        // In a party you never get a proper look at her, so nobody caught has anything to tell.
        if (ClientRun.party > 1 && (entity.getY() > 90 || !entity.getMainHandStack().isEmpty())) {
            return (int) (System.currentTimeMillis() / 90 % StudentSkins.GIRL_COUNT);
        }
        return entity.getSkin();
    }

    /** The girls as the mystery has them; a few of the boys and the townsfolk for the look of it. */
    private static boolean wearsGlasses(int skin) {
        if (skin >= Roster.TOTAL) return (skin - Roster.TOTAL) % 5 == 0;
        Roster.Student student = Roster.get(skin);
        return student.female() ? student.glasses() == Traits.Glasses.YES : skin % 5 == 2;
    }

    /** Hearts over her head, if she's going to let you go. */
    private void renderHearts(StudentEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, float age) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(HEART));
        for (int i = -1; i <= 1; i++) {
            float bob = MathHelper.sin(age * 0.12f + i * 1.7f) * 0.08f;
            matrices.push();
            matrices.translate(i * 0.42, 2.25 + bob + (i == 0 ? 0.14 : 0), 0);
            matrices.multiply(dispatcher.getRotation());
            float size = i == 0 ? 0.22f : 0.17f;
            Matrix4f m = matrices.peek().getPositionMatrix();
            Matrix3f n = matrices.peek().getNormalMatrix();
            corner(vc, m, n, -size, -size, 1, 1);
            corner(vc, m, n, size, -size, 0, 1);
            corner(vc, m, n, size, size, 0, 0);
            corner(vc, m, n, -size, size, 1, 0);
            matrices.pop();
        }
    }

    private void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(n, 0, 1, 0).next();
    }

    private static final net.minecraft.block.Block[] UMBRELLAS = {net.minecraft.block.Blocks.BLACK_CONCRETE, net.minecraft.block.Blocks.BLUE_CONCRETE, net.minecraft.block.Blocks.WHITE_CONCRETE, net.minecraft.block.Blocks.RED_CONCRETE, net.minecraft.block.Blocks.PINK_CONCRETE, net.minecraft.block.Blocks.GRAY_CONCRETE};

    private void box(net.minecraft.block.Block block, MatrixStack matrices, VertexConsumerProvider consumers, int light, float y, float sizeX, float sizeY) {
        matrices.push();
        matrices.translate(-sizeX / 2f, y, -sizeX / 2f);
        matrices.scale(sizeX, sizeY, sizeX);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(block.getDefaultState(), matrices, consumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    /** Held up on the right, canopy in three steps, in whatever colour they own. */
    private void umbrella(StudentEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        net.minecraft.block.Block colour = UMBRELLAS[Math.floorMod(entity.getId(), UMBRELLAS.length)];
        matrices.push();
        matrices.translate(0.3, 0, -0.32);
        box(net.minecraft.block.Blocks.BLACK_CONCRETE, matrices, consumers, light, 1.15f, 0.05f, 1.15f);
        box(colour, matrices, consumers, light, 2.2f, 1.5f, 0.07f);
        box(colour, matrices, consumers, light, 2.27f, 1.0f, 0.07f);
        box(colour, matrices, consumers, light, 2.34f, 0.45f, 0.06f);
        matrices.pop();
    }

    /** Just over their actual head, sat down or not, and not filling the screen from across a table. */
    @Override
    protected void renderLabelIfPresent(StudentEntity entity, net.minecraft.text.Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float seated = entity.getY() <= 90 && entity.getPeek() == 0 ? Mannerisms.sat(entity) : 0f;
        matrices.push();
        matrices.translate(0, LABEL_AT - SEATED_LOW * seated - entity.getNameLabelHeight() * LABEL_SIZE, 0);
        matrices.scale(LABEL_SIZE, LABEL_SIZE, LABEL_SIZE);
        super.renderLabelIfPresent(entity, text, matrices, consumers, light);
        matrices.pop();
    }

    /** Names only show when you're looking right at someone. */
    @Override
    protected boolean hasLabel(StudentEntity entity) {
        return entity.hasCustomName() && entity == dispatcher.targetedEntity && !MenuScene.isMenuGirl(entity) && !Cutscene.active() && dispatcher.getSquaredDistanceToCamera(entity) > TOO_CLOSE_FOR_A_NAME;
    }

    /** What she's doing on the difficulty screen. */
    public enum Pose { NONE, HANDS_CLASPED, HOLDING_HER_FACE, REACHING, BAT, BAT_WALKING, WALKING, THROWING, BENDING }

    public static Pose pose = Pose.NONE;
    /** Which way she's meant to be turned, how far through a swing of the bat (or -1), how far she's bending, and where her hand's going. */
    public static float turned = 0f, batSwing = -1f, bend = 0f, handDown = 0f;
    /** Where she's looking, as a nod of the head and a turn past her shoulders. */
    public static float lookDown = 0f, lookAside = 0f;

    private static final int R_PITCH = 0, R_YAW = 1, R_ROLL = 2, L_PITCH = 3, L_YAW = 4, L_ROLL = 5, H_PITCH = 6, H_YAW = 7, H_ROLL = 8, TURN = 9, BEND_AT = 10, STRIDE = 11, ANGLES = 12;
    private static final float[] wanted = new float[ANGLES], eased = new float[ANGLES];
    private static long lastPoseNanos = 0;
    /** Everything she does eases towards where it's going, so nothing snaps from one pose to the next. */
    private static final float EASING = 8f, WAIST = 12f, SHOULDER = 10f, LEG_SPEED = 0.42f, STRIDE_SIZE = 0.7f;

    private static float smooth(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** The arm through a swing: up and back, whipped down, carried through, and back to the top. */
    private static float swingArm(float s, float from, float to) {
        if (s < 0.42f) return MathHelper.lerp(smooth(s / 0.42f), from, -3.0f);
        if (s < 0.58f) {
            float strike = (s - 0.42f) / 0.16f;
            return MathHelper.lerp(strike * strike, -3.0f, -0.25f);
        }
        return MathHelper.lerp(smooth((s - 0.58f) / 0.42f), -0.25f, to);
    }

    /** The whole top half turns into it, the way you'd swing anything heavy. */
    private static float swingTwist(float s) {
        if (s < 0.42f) return MathHelper.lerp(smooth(s / 0.42f), 0f, 0.5f);
        if (s < 0.58f) return MathHelper.lerp((s - 0.42f) / 0.16f, 0.5f, -0.55f);
        return MathHelper.lerp(smooth((s - 0.58f) / 0.42f), -0.55f, 0f);
    }

    private void twist(float yaw) {
        model.body.yaw = yaw;
        model.rightArm.pivotZ = MathHelper.sin(yaw) * 5f;
        model.rightArm.pivotX = -MathHelper.cos(yaw) * 5f;
        model.leftArm.pivotZ = -MathHelper.sin(yaw) * 5f;
        model.leftArm.pivotX = MathHelper.cos(yaw) * 5f;
        model.jacket.copyTransform(model.body);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
    }

    /** Forward from the waist. The legs stay exactly where they are. */
    private void bendAtTheWaist(float a) {
        if (a <= 0f) return;
        float cos = MathHelper.cos(a), sin = MathHelper.sin(a);
        model.body.pitch = a;
        model.body.pivotY = WAIST - WAIST * cos;
        model.body.pivotZ = -WAIST * sin;
        model.head.pivotY = model.body.pivotY;
        model.head.pivotZ = model.body.pivotZ;
        float shoulderY = WAIST - SHOULDER * cos, shoulderZ = -SHOULDER * sin;
        model.rightArm.pivotY = shoulderY;
        model.rightArm.pivotZ = shoulderZ;
        model.leftArm.pivotY = shoulderY;
        model.leftArm.pivotZ = shoulderZ;
        model.jacket.copyTransform(model.body);
    }

    private void plead(float age) {
        java.util.Arrays.fill(wanted, 0f);
        float shake = MathHelper.sin(age * 0.8f) * 0.03f, rock = MathHelper.sin(age * 0.06f);
        wanted[TURN] = turned;
        switch (pose) {
            case HANDS_CLASPED -> {
                wanted[R_PITCH] = -1.25f + shake;
                wanted[L_PITCH] = -1.25f - shake;
                wanted[R_YAW] = -0.62f;
                wanted[L_YAW] = 0.62f;
                wanted[H_PITCH] = -0.08f;
                wanted[H_ROLL] = rock * 0.16f;
            }
            case HOLDING_HER_FACE -> {
                wanted[R_PITCH] = -1.98f;
                wanted[L_PITCH] = -1.98f;
                wanted[R_YAW] = -0.3f;
                wanted[L_YAW] = 0.3f;
                wanted[R_ROLL] = 0.72f;
                wanted[L_ROLL] = -0.72f;
                wanted[H_PITCH] = 0.16f;
                wanted[H_YAW] = MathHelper.sin(age * 0.5f) * 0.14f;
            }
            case BAT -> {
                wanted[R_PITCH] = -1.9f;
                wanted[R_ROLL] = 0.4f;
                wanted[L_PITCH] = 0.1f;
                wanted[H_PITCH] = lookDown;
                wanted[H_YAW] = lookAside;
            }
            case BAT_WALKING -> {
                wanted[R_PITCH] = -1.9f;
                wanted[R_ROLL] = 0.4f;
                wanted[STRIDE] = 1f;
                wanted[H_PITCH] = lookDown;
                wanted[H_YAW] = lookAside;
            }
            case WALKING -> {
                wanted[STRIDE] = 1f;
                wanted[H_PITCH] = lookDown;
                wanted[H_YAW] = lookAside;
            }
            case THROWING -> {
                wanted[R_PITCH] = -1.9f;
                wanted[R_ROLL] = 0.4f;
                wanted[H_YAW] = lookAside;
            }
            case BENDING -> {
                wanted[BEND_AT] = bend;
                wanted[R_PITCH] = handDown;
                wanted[L_PITCH] = -0.1f;
                // Eyes on what she's doing.
                wanted[H_PITCH] = lookDown;
                wanted[H_YAW] = lookAside;
            }
            case REACHING -> {
                wanted[R_PITCH] = -1.6f + shake * 2;
                wanted[R_YAW] = -0.12f;
                wanted[L_PITCH] = -0.2f;
                wanted[H_PITCH] = 0.02f;
                wanted[H_ROLL] = rock * 0.28f;
            }
            default -> {
            }
        }
        long now = System.nanoTime();
        float seconds = lastPoseNanos == 0 ? 1f : Math.min(0.1f, (now - lastPoseNanos) / 1_000_000_000f);
        lastPoseNanos = now;
        float step = 1f - (float) Math.exp(-EASING * seconds);
        for (int i = 0; i < ANGLES; i++) eased[i] += (wanted[i] - eased[i]) * step;
        model.rightArm.pitch = eased[R_PITCH];
        model.rightArm.yaw = eased[R_YAW];
        model.rightArm.roll = eased[R_ROLL];
        model.leftArm.pitch = eased[L_PITCH];
        model.leftArm.yaw = eased[L_YAW];
        model.leftArm.roll = eased[L_ROLL];
        model.head.pitch = eased[H_PITCH];
        model.head.yaw = eased[H_YAW];
        model.head.roll = eased[H_ROLL];
        float stride = MathHelper.sin(age * LEG_SPEED) * STRIDE_SIZE * eased[STRIDE];
        model.rightLeg.pitch = stride;
        model.leftLeg.pitch = -stride;
        model.rightPants.copyTransform(model.rightLeg);
        model.leftPants.copyTransform(model.leftLeg);
        // The arm she's not holding anything in swings as she walks.
        model.leftArm.pitch += stride * 0.8f;
        if (pose == Pose.WALKING) model.rightArm.pitch += -stride * 0.8f;
        if (batSwing >= 0f) {
            model.rightArm.pitch = swingArm(batSwing, -1.9f, -1.9f);
            twist(swingTwist(batSwing));
        }
        bendAtTheWaist(eased[BEND_AT]);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
    }

    @Override
    public void render(StudentEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        int skin = skinOf(entity);

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
        float pitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        float limbPos = entity.limbAnimator.getPos(tickDelta);
        float limbSpeed = entity.limbAnimator.getSpeed(tickDelta);
        float age = entity.age + tickDelta;

        boolean sitting = entity.isSittingDown();
        model.child = false;
        boolean inTown = entity.getY() <= 90 && entity.getPeek() == 0;
        Vec3d exactly = entity.getLerpedPos(tickDelta);
        if (inTown) Mannerisms.frame(entity, exactly);
        float seated = inTown ? Mannerisms.sat(entity) : sitting ? 1f : 0f;
        model.riding = sitting || seated > 0.01f; // the vanilla riding pose is a seated pose
        model.setAngles(entity, limbPos, limbSpeed, age, MathHelper.wrapDegrees(headYaw - bodyYaw), pitch);
        // Everyone shares this model, and nothing else clears a tilted head, or a body she bent at the waist.
        model.head.roll = 0f;
        model.head.pivotZ = 0f;
        model.body.pivotZ = 0f;
        model.head.visible = true;
        model.hat.visible = true;
        int who = entity.getPeek();
        boolean dragged = who == StudentEntity.DRAGGED, tiedUp = who == StudentEntity.TIED;
        if (who == StudentEntity.HAULING) HerPoses.hauling(model, age, Cutscene.heave(), Cutscene.heaveLook(), Cutscene.heaveFoot());
        if (dragged) HerPoses.dragged(model, age);
        if (tiedUp) HerPoses.tied(model);
        if (entity.isFlustered()) {
            // Eyes on the floor, and a little shake of the head.
            model.head.pitch = 0.5f;
            model.head.yaw = MathHelper.sin(age * 0.45f) * 0.16f;
        }
        if (pose != Pose.NONE) plead(age);
        boolean humanNow = pose == Pose.NONE && !MenuScene.isMenuGirl(entity) && entity.getY() <= 90 && entity.getPeek() == 0 && entity.getMainHandStack().isEmpty();
        if (humanNow) Mannerisms.apply(model, entity, age, limbPos, limbSpeed, sitting);
        boolean umbrella = humanNow && !sitting && entity.getWorld().isRaining() && entity.getWorld().isSkyVisible(entity.getBlockPos().up(2));
        if (umbrella) {
            model.rightArm.pitch = -1.2f;
            model.rightArm.yaw = -0.25f;
            model.rightArm.roll = 0f;
            model.rightSleeve.copyTransform(model.rightArm);
        }
        model.hat.copyTransform(model.head);
        ItemStack held = entity.getMainHandStack();
        // In the room at the end, the last scene drives her head and the bat.
        boolean endRoom = entity.getY() > 90 && !MenuScene.isActive() && who < StudentEntity.DRAGGED;
        float swing = endRoom ? JumpscareScreen.batSwing : -1f;
        boolean batInHand = pose == Pose.BAT || pose == Pose.BAT_WALKING || pose == Pose.THROWING && batSwing < RELEASE;
        if (batInHand) held = new ItemStack(ModItems.BAT);
        if (endRoom) {
            model.head.roll = JumpscareScreen.headTilt;
            model.hat.copyTransform(model.head);
        }
        float hold = endRoom ? JumpscareScreen.closeness : 0f;
        boolean inTheRoom = endRoom && who == 0 && hold <= 0f && swing < 0f;
        boolean herPlaced = endRoom && hold <= 0f;
        if (inTheRoom) HerPoses.inTheRoom(model, age, JumpscareScreen.herMood, JumpscareScreen.herMoodWeight, JumpscareScreen.herIdle, JumpscareScreen.herWinded);
        if (herPlaced) {
            // Her head stays on you wherever the rest of her is going.
            float toYou = MathHelper.wrapDegrees(JumpscareScreen.herLook - JumpscareScreen.herFacing) * MathHelper.RADIANS_PER_DEGREE;
            model.head.yaw = MathHelper.clamp(toYou, -1.25f, 1.25f) + (model.head.yaw - MathHelper.clamp(MathHelper.wrapDegrees(headYaw - bodyYaw) * MathHelper.RADIANS_PER_DEGREE, -1.25f, 1.25f));
            if (JumpscareScreen.herMindMade) {
                // Nothing else to look at now.
                model.head.pitch = 0.08f + 0.42f * JumpscareScreen.herReach + 0.55f * JumpscareScreen.herHeadDown;
                model.head.roll = 0f;
            }
            HerPoses.placed(model, age, JumpscareScreen.herStride, JumpscareScreen.herPhase, JumpscareScreen.herKneel, JumpscareScreen.herFlat, JumpscareScreen.herKick, JumpscareScreen.herSit);
        }
        if (inTheRoom || herPlaced) model.hat.copyTransform(model.head);
        if (hold > 0f) {
            // Straight at you, whatever way she was facing, and looking you in the eye.
            model.head.yaw = 0f;
            model.head.pitch = -0.08f * hold;
            model.head.roll = MathHelper.sin(age * 0.09f) * 0.05f * hold;
            model.hat.copyTransform(model.head);
            // Both hands out to your shoulders.
            model.rightArm.pitch = -1.05f * hold;
            model.leftArm.pitch = -1.05f * hold;
            model.rightArm.roll = -0.12f * hold;
            model.leftArm.roll = 0.12f * hold;
            model.rightSleeve.copyTransform(model.rightArm);
            model.leftSleeve.copyTransform(model.leftArm);
        }
        if (swing >= 0f || endRoom && JumpscareScreen.batHeld) held = new ItemStack(ModItems.BAT);
        if (!held.isEmpty() && !batInHand) {
            // Raised, ready to come down; in the swing, wound up over her shoulder and then whipped down at you.
            model.rightArm.pitch = swing < 0f ? MathHelper.lerp(endRoom ? JumpscareScreen.batRaise : 1f, 0.35f, -2.4f) : swingArm(swing, -2.4f, -0.6f);
            model.rightArm.roll = -0.14f;
            model.rightSleeve.copyTransform(model.rightArm);
            if (swing >= 0f) {
                twist(swingTwist(swing));
                // She steps into it, and the other arm goes out to balance her.
                float into = MathHelper.sin(MathHelper.PI * swing);
                model.rightLeg.pitch = -0.5f * into;
                model.leftLeg.pitch = 0.42f * into;
                model.leftArm.pitch = 0.5f * into;
                model.rightPants.copyTransform(model.rightLeg);
                model.leftPants.copyTransform(model.leftLeg);
                model.leftSleeve.copyTransform(model.leftArm);
            }
        }
        if (endRoom && JumpscareScreen.herReach > 0f) {
            model.rightArm.pitch = MathHelper.lerp(JumpscareScreen.herReach, model.rightArm.pitch, -1.25f);
            model.rightSleeve.copyTransform(model.rightArm);
        }

        int peek = entity.getPeek();
        boolean leaning = Math.abs(peek) == 1;
        // How far she's eased out from behind the wall.
        float leanIn = MathHelper.clamp(age / 14f, 0f, 1f);
        leanIn = leanIn * leanIn * (3f - 2f * leanIn);
        if (leaning) {
            // One hand reaching for the wall, her head tipping out past it and slowly swaying.
            var hand = peek > 0 ? model.leftArm : model.rightArm;
            hand.pitch = -1.45f * leanIn;
            hand.yaw = -peek * 0.55f * leanIn; // across to the corner of the wall beside her
            (peek > 0 ? model.leftSleeve : model.rightSleeve).copyTransform(hand);
            model.head.roll = peek * -0.45f * leanIn + MathHelper.sin(age * 0.07f) * 0.07f;
            model.hat.copyTransform(model.head);
        } else if (peek == StudentEntity.RUNNING) {
            float stride = MathHelper.sin(age * 0.9f) * 1.25f;
            model.rightLeg.pitch = stride;
            model.leftLeg.pitch = -stride;
            model.rightArm.pitch = -stride;
            model.leftArm.pitch = stride;
            model.rightPants.copyTransform(model.rightLeg);
            model.leftPants.copyTransform(model.leftLeg);
            model.rightSleeve.copyTransform(model.rightArm);
            model.leftSleeve.copyTransform(model.leftArm);
        }

        float spin = 0f;
        boolean glitch = MenuScene.isMenuGirl(entity) && MenuScene.cycle().glitching();
        if (glitch) {
            spin = MenuScene.cycle().progress() * 360f;
        }

        // She's only ever a shape: nobody can say who it was.
        float r = peek != 0 && peek < StudentEntity.DRAGGED ? 0.1f : 1f, g = r, b = r;
        // The candles reach this far: you've got to be able to see the ropes.
        if (tiedUp) light = LightmapTextureManager.pack(11, 0);

        matrices.push();
        if (tiedUp && JumpscareScreen.youFall > 0f) {
            // Over the chair's left edge, you and it together, the same way the camera's going.
            matrices.translate(FALL_PIVOT_X, FALL_PIVOT_Y, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(JumpscareScreen.FALL_DEGREES * JumpscareScreen.youFall));
            matrices.translate(-FALL_PIVOT_X, -FALL_PIVOT_Y, 0);
            matrices.push();
            matrices.translate(FALL_PIVOT_X, FALL_PIVOT_Y, -MenuRoom.SEAT_Z);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(MenuRoom.chair(), matrices, consumers, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        if (seated > 0f) matrices.translate(0, -(inTown ? SEATED_LOW : 0.4) * seated, 0);
        Vec3d shift = inTown ? Mannerisms.seatShift(entity, exactly) : null;
        if (shift != null) matrices.translate(shift.x, shift.y, shift.z);
        if (hold > 0f) {
            Vec3d to = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().subtract(entity.getPos());
            double distance = to.horizontalLength();
            Vec3d way = new Vec3d(to.x, 0, to.z).normalize();
            double step = Math.max(0, distance - FACE_DISTANCE) * hold;
            matrices.translate(way.x * step, 0, way.z * step);
            bodyYaw = MathHelper.lerpAngleDegrees(hold, bodyYaw, (float) (Math.toDegrees(Math.atan2(way.z, way.x)) - 90));
            // Close enough for the candles to catch her face.
            light = LightmapTextureManager.pack(Math.round(11 * hold), 0);
        } else if (swing > 0f) {
            // She closes the gap as she swings.
            float step = smooth(swing) * JumpscareScreen.SWING_STEP, radians = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
            matrices.translate(-MathHelper.sin(radians) * step, 0, MathHelper.cos(radians) * step);
        }
        if (herPlaced) {
            matrices.translate(JumpscareScreen.herX, Math.abs(MathHelper.sin(JumpscareScreen.herPhase)) * WALK_BOB * JumpscareScreen.herStride, JumpscareScreen.herZ);
            bodyYaw = JumpscareScreen.herFacing;
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw + spin + (pose == Pose.NONE ? 0f : eased[TURN])));
        if (leaning) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-peek * 13f * leanIn));
        if (peek == StudentEntity.RUNNING) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18f));
        if (who == StudentEntity.HAULING) {
            // Bent to your ankle between heaves, thrown back into each one, and turning to look.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Cutscene.heaveLook() * 24f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(Cutscene.heave(), -28f, 5f)));
        }
        if (dragged) {
            matrices.translate(0, 0.14, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        }
        if (inTheRoom) {
            matrices.translate(0, 0, -JumpscareScreen.herStep);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(HerPoses.lean(age) * (1f - JumpscareScreen.herKneel)));
            matrices.translate(0, -0.62 * JumpscareScreen.herKneel * (1f - JumpscareScreen.herFlat) - 0.06 * JumpscareScreen.herFlat - 0.62 * JumpscareScreen.herSit, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-72f * JumpscareScreen.herFlat));
        }
        if (humanNow) {
            matrices.translate(0, Mannerisms.bob(entity, limbPos, limbSpeed), 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(Mannerisms.lean(entity, age, limbPos, limbSpeed, sitting)));
        }
        if (umbrella) umbrella(entity, matrices, consumers, light);
        matrices.scale(-1f, -1f, 1f);
        matrices.scale(0.9375f, 0.9375f, 0.9375f);
        matrices.translate(0.0, -1.501, 0.0);

        boolean youItself = (dragged || tiedUp) && MinecraftClient.getInstance().player != null;
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(youItself ? MinecraftClient.getInstance().player.getSkinTexture() : StudentSkins.texture(skin)));
        if (glitch) {
            // Chromatic ghosts either side, jittering, then the real girl.
            float jitter = (random.nextFloat() - 0.5f) * 0.12f;
            drawAt(matrices, vc, light, jitter - 0.08f, 1f, 0.15f, 0.2f, 0.5f);
            drawAt(matrices, vc, light, jitter + 0.08f, 0.15f, 0.9f, 1f, 0.5f);
            drawAt(matrices, vc, light, jitter, r, g, b, 1f);
        } else {
            model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, r, g, b, 1f);
        }
        if (tiedUp) {
            matrices.push();
            model.body.rotate(matrices);
            HerPoses.ropes(matrices, consumers, light);
            matrices.pop();
            matrices.push();
            model.rightLeg.rotate(matrices);
            HerPoses.legRopes(matrices, consumers, light);
            matrices.pop();
        }
        if (wearsGlasses(skin) && !youItself) {
            matrices.push();
            model.head.rotate(matrices);
            glasses.render(matrices, consumers, light, r, g, b);
            matrices.pop();
        }
        if (!held.isEmpty()) {
            matrices.push();
            model.setArmAngle(Arm.RIGHT, matrices);
            // Grip in her fist. Carried low it hangs down from her hand; lifted, it stands out from it.
            float hanging = endRoom && swing < 0f ? JumpscareScreen.batHang * BAT_HANGS : 0f;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f + hanging));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            matrices.translate(1f / 16f, 0.125f, -0.625f);
            heldItems.renderItem(entity, held, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND, false, matrices, consumers, light);
            matrices.pop();
        }
        matrices.pop();
        if (endRoom && JumpscareScreen.hearts) renderHearts(entity, matrices, consumers, age);

        super.render(entity, yaw, tickDelta, matrices, consumers, light); // name label
    }

    private void drawAt(MatrixStack matrices, VertexConsumer vc, int light, float dx, float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(dx, 0, 0);
        model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, r, g, b, a);
        matrices.pop();
    }
}
