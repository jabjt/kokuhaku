package com.kokuhaku.client;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.game.Schedule;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Things you hear, for headphones. Nothing here is real and nothing can hurt
 * you, and each player gets their own, so your friends won't have heard it.
 *
 * Mostly the footsteps: they walk when you walk and stop when you stop, one step
 * late, always behind you whichever way you turn, and a little nearer each time.
 */
public final class Unease {
    private static final int FIRST_DAY = 2, BREATH_DAY = 6, SECOND = 20, STILL_FOR = 100, LATE_STEP = 6;
    private static final int ECHO_MIN = 300, ECHO_MORE = 300, ECHO_REST = 3600, BREATH_REST = 6000, NOISE_REST = 2400;
    private static final int WALK_STEP = 9, RUN_STEP = 6;
    private static final float ECHO_CHANCE = 0.012f, ECHO_CHANCE_A_DAY = 0.0012f, BREATH_CHANCE = 0.04f, NOISE_CHANCE = 0.01f;
    private static final double FAR = 8, NEAR = 3.5, BREATH_AT = 2, NOISE_AT = 10, MOVING = 0.002;
    private static final boolean FORCED = System.getenv("KOKUHAKU_UNEASE") != null;
    private static final Random RANDOM = new Random();

    private static int echoLeft = 0, echoFull = 1, stepIn = 0, still = 0, ticks = 0;
    private static int echoRest = 0, breathRest = 0, noiseRest = 0;
    private static boolean trailing = false;

    private Unease() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || !ClientRun.active || ClientRun.ghost || ClientRun.day < FIRST_DAY || client.currentScreen != null || client.isPaused() || Cutscene.active()) return;
        boolean late = ClientRun.minute >= Schedule.EVENING;
        double dx = p.getX() - p.prevX, dz = p.getZ() - p.prevZ;
        boolean moving = FORCED ? ticks % 800 < 400 : p.isOnGround() && dx * dx + dz * dz > MOVING;
        still = moving ? 0 : still + 1;
        ticks++;
        echoRest--;
        breathRest--;
        noiseRest--;

        if (echoLeft > 0) {
            echo(client, p, moving);
        } else if (late && moving && echoRest <= 0 && ticks % SECOND == 0 && (FORCED || RANDOM.nextFloat() < ECHO_CHANCE + ClientRun.day * ECHO_CHANCE_A_DAY)) {
            echoFull = echoLeft = ECHO_MIN + RANDOM.nextInt(ECHO_MORE);
            echoRest = ECHO_REST;
            stepIn = WALK_STEP;
            if (FORCED) Kokuhaku.LOG.info("Unease: footsteps");
        }
        if (late && ClientRun.day >= BREATH_DAY && still > STILL_FOR && breathRest <= 0 && ticks % SECOND == 0 && (FORCED || RANDOM.nextFloat() < BREATH_CHANCE)) {
            breathRest = BREATH_REST;
            behind(client, p, BREATH_AT, SoundEvents.ENTITY_PLAYER_BREATH, 0.18f, 0.75f);
            if (FORCED) Kokuhaku.LOG.info("Unease: breathing");
        }
        if (late && noiseRest <= 0 && ticks % SECOND == 0 && (FORCED || RANDOM.nextFloat() < NOISE_CHANCE)) {
            noiseRest = NOISE_REST;
            noise(client, p);
            if (FORCED) Kokuhaku.LOG.info("Unease: a noise");
        }
    }

    private static void echo(MinecraftClient client, ClientPlayerEntity p, boolean moving) {
        echoLeft--;
        if (moving) {
            trailing = true;
            if (--stepIn <= 0) {
                step(client, p);
                stepIn = p.isSprinting() ? RUN_STEP : WALK_STEP;
            }
        } else if (trailing && still == LATE_STEP) {
            // You've stopped. One more, and then nothing.
            step(client, p);
            trailing = false;
        }
    }

    private static void step(MinecraftClient client, ClientPlayerEntity p) {
        BlockState under = client.world.getBlockState(p.getBlockPos().down());
        double distance = MathHelper.lerp(1f - echoLeft / (float) echoFull, FAR, NEAR);
        behind(client, p, distance, under.isAir() ? SoundEvents.BLOCK_STONE_STEP : under.getSoundGroup().getStepSound(), 0.45f, 0.9f);
        if (FORCED) Kokuhaku.LOG.info("Unease: step at {}", String.format("%.1f", distance));
    }

    private static void behind(MinecraftClient client, ClientPlayerEntity p, double distance, SoundEvent sound, float volume, float pitch) {
        Vec3d at = p.getPos().subtract(Vec3d.fromPolar(0f, p.getYaw()).multiply(distance));
        client.world.playSound(at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch, false);
    }

    /** Off to one side, through a wall or round a corner, where you can't quite tell. */
    private static void noise(MinecraftClient client, ClientPlayerEntity p) {
        Vec3d at = p.getPos().add(Vec3d.fromPolar(0f, RANDOM.nextFloat() * 360f).multiply(NOISE_AT));
        SoundEvent sound = switch (RANDOM.nextInt(3)) {
            case 0 -> SoundEvents.BLOCK_WOODEN_DOOR_CLOSE;
            case 1 -> SoundEvents.BLOCK_WOODEN_DOOR_OPEN;
            default -> SoundEvents.AMBIENT_CAVE.value();
        };
        client.world.playSound(at.x, at.y, at.z, sound, SoundCategory.HOSTILE, 0.4f, 0.8f, false);
    }
}
