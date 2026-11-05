package com.kokuhaku.client;

/**
 * Drives the menu girl's cycle: hold one girl for a few seconds, then a fast
 * glitch-spin, swapping to the next girl halfway round while she's side-on.
 */
public class GlitchCycle {
    private static final long HOLD_MS = 3200;
    private static final long GLITCH_MS = 650;

    private int girl = 0;
    private long cycleStart = System.currentTimeMillis();
    private boolean swapped = false;
    private float progress = 0f;

    /** Call once per frame. */
    public void update() {
        long now = System.currentTimeMillis();
        long t = now - cycleStart;
        if (t > HOLD_MS + GLITCH_MS) {
            cycleStart = now;
            swapped = false;
            progress = 0f;
        } else if (t > HOLD_MS) {
            progress = (t - HOLD_MS) / (float) GLITCH_MS;
            if (progress >= 0.5f && !swapped) {
                girl = (girl + 1) % StudentSkins.GIRL_COUNT;
                swapped = true;
            }
        } else {
            progress = 0f;
        }
    }

    public int girl() {
        return girl;
    }

    /** 0 when calm, 0..1 during a glitch. */
    public float progress() {
        return progress;
    }

    public boolean glitching() {
        return progress > 0f;
    }
}
