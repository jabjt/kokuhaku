package com.kokuhaku.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Mostly silence. Every so often, something very quiet: footsteps in the
 * hallway, a floorboard, a door somewhere in the house.
 */
public class MenuAmbience {
    private record Pending(long at, SoundEvent sound, float pitch, float volume) {
    }

    private final Random random = new Random();
    private final Deque<Pending> queue = new ArrayDeque<>();
    private long nextEventAt = System.currentTimeMillis() + 12_000 + random.nextInt(15_000);

    public void tick() {
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && queue.peekFirst().at <= now) {
            Pending p = queue.pollFirst();
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(p.sound, p.pitch, p.volume));
        }
        if (now >= nextEventAt && queue.isEmpty()) {
            scheduleEvent(now);
            nextEventAt = now + 25_000 + random.nextInt(35_000);
        }
    }

    private void scheduleEvent(long now) {
        int roll = random.nextInt(10);
        if (roll < 7) {
            // Someone walking slowly past the door, getting quieter, then stopping.
            int steps = 3 + random.nextInt(4);
            long t = now;
            float volume = 0.09f + random.nextFloat() * 0.03f;
            boolean approaching = random.nextBoolean();
            for (int i = 0; i < steps; i++) {
                float v = approaching ? volume * (0.6f + 0.4f * i / steps) : volume * (1f - 0.4f * i / steps);
                queue.add(new Pending(t, SoundEvents.BLOCK_WOOD_STEP, 0.75f + random.nextFloat() * 0.15f, v));
                t += 560 + random.nextInt(140) + i * 40L; // slowing down
            }
        } else if (roll < 9) {
            queue.add(new Pending(now, SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.45f + random.nextFloat() * 0.1f, 0.05f));
        } else {
            queue.add(new Pending(now, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, 0.6f, 0.05f));
            queue.add(new Pending(now + 2_500 + random.nextInt(2_000), SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, 0.6f, 0.04f));
        }
    }
}
