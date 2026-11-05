package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * The radio's sound. You can hear it anywhere in the house, and it dies away as
 * you step outside. Going back in, it's still playing, wherever it had got to.
 * When a song ends the next one starts, however you've set it up.
 */
public final class RadioPlayer {
    /** The station, then hers. */
    private static final String[] SOUNDS = {"radio_1", "radio_2", "radio_3", "radio_her"};
    public static final String[] TITLES = {"BEZTEBYA", "Fever (instrumental)", "Refrain", "Truth"};
    public static final String[] ARTISTS = {"Unknown artist", "fakemink, prod. OSCAR18", "shytaupe", "akkiemi"};
    public static final float[] SECONDS = {110.7f, 144.4f, 202.4f, 86.3f};
    private static final float VOLUME = 0.28f, FADE_PER_TICK = 0.01f;
    /** Further from the radio than this and it's someone else's house, whatever's overhead. */
    private static final double HOUSE_REACH = 14;
    private static final java.util.Random RANDOM = new java.util.Random();
    private static RadioSound playing = null;
    private static BlockPos where = null;
    private static long startedAt = 0;

    /** What's on the station at the moment, and what's playing. */
    public static int[] playlist = {0, 1, 2};
    public static int track = 0;
    public static boolean shuffle = false, repeatOne = false;
    public static float level = 1f;

    private RadioPlayer() {
    }

    private static final class RadioSound extends MovingSoundInstance {
        private final BlockPos at;

        RadioSound(SoundEvent sound, BlockPos at) {
            super(sound, SoundCategory.RECORDS, Random.create());
            this.at = at;
            x = at.getX() + 0.5;
            y = at.getY() + 0.5;
            z = at.getZ() + 0.5;
            repeat = false;
            repeatDelay = 0;
            volume = VOLUME * level;
            attenuationType = SoundInstance.AttenuationType.LINEAR;
        }

        /** Under a roof and near enough to the radio to be the same house. */
        private boolean indoors(ClientPlayerEntity p) {
            return p.squaredDistanceTo(at.toCenterPos()) < HOUSE_REACH * HOUSE_REACH && !p.getWorld().isSkyVisible(p.getBlockPos().up());
        }

        @Override
        public void tick() {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) {
                setDone();
                return;
            }
            float wanted = indoors(p) ? VOLUME * level : 0f;
            volume = volume + MathHelper.clamp(wanted - volume, -FADE_PER_TICK, FADE_PER_TICK);
        }

        /** It keeps playing silently while you're out, or it'd start again from the top. */
        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }

    public static boolean on() {
        return playing != null;
    }

    public static float elapsed() {
        return on() ? Math.min(SECONDS[track], (System.currentTimeMillis() - startedAt) / 1000f) : 0f;
    }

    public static void stop(MinecraftClient client) {
        if (playing != null) client.getSoundManager().stop(playing);
        playing = null;
    }

    private static void start(MinecraftClient client, int which) {
        stop(client);
        track = which % SOUNDS.length;
        startedAt = System.currentTimeMillis();
        playing = new RadioSound(SoundEvent.of(new Identifier(KokuhakuClient.MOD_ID, SOUNDS[track])), where);
        client.getSoundManager().play(playing);
    }

    public static void set(MinecraftClient client, boolean on, BlockPos at, int which) {
        where = at;
        stop(client);
        // Her song is the only thing on her station, and the rest aren't on it.
        if (which == SOUNDS.length - 1) playlist = new int[]{which};
        else if (playlist.length == 1 && playlist[0] == SOUNDS.length - 1) playlist = new int[]{0, 1, 2};
        if (on) start(client, which);
    }

    /** The one after (or before) this one, the way it's been set up. */
    public static int neighbour(int step) {
        if (playlist.length == 1) return playlist[0];
        if (shuffle) {
            int next;
            do next = playlist[RANDOM.nextInt(playlist.length)]; while (next == track);
            return next;
        }
        int at = 0;
        for (int i = 0; i < playlist.length; i++) if (playlist[i] == track) at = i;
        return playlist[Math.floorMod(at + step, playlist.length)];
    }

    /** Every tick: when a song runs out, on to the next. */
    public static void tick(MinecraftClient client) {
        if (playing == null || (System.currentTimeMillis() - startedAt) / 1000f < SECONDS[track] + 0.5f) return;
        start(client, repeatOne ? track : neighbour(1));
    }
}
