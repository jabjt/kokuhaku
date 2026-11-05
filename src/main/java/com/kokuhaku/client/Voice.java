package com.kokuhaku.client;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proximity voice. It goes through the game's own connection, so it works
 * wherever an invite works, with no extra ports. Your mic only runs when
 * someone else is in the game, and only sends while you're actually talking.
 * The server decides who's near enough to hear (see RunManager.voice).
 *
 * Packets never arrive evenly: the game hands them on in bursts, a tick at a
 * time. So each speaker has a small buffer and their own thread, and sound
 * only starts once a few frames are waiting. A little delay beats stuttering.
 *
 * An audio line must never run dry: on Windows, when it does, it plays whatever's
 * left in its buffer round and round, which is the last words of a sentence
 * repeating. So a speaker's line is always being fed, with silence if nothing else.
 */
public final class Voice {
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int FRAME = 640; // 40 ms of samples
    private static final int GATE = 350;  // quieter than this isn't speech
    private static final int HANGOVER = 10; // frames to keep sending after you stop, so words don't get clipped
    private static final int FADE = 64; // samples, at either end of what you say, so it never starts with a click
    private static final double NOISE_MARGIN = 2.5;
    private static final int START_AFTER = 4; // frames in hand before a speaker starts: 160 ms
    private static final int MOST_BEHIND = 10; // frames: any later than this and we skip ahead
    private static final int LINE_FRAMES = 4, LATE_FRAMES = 3, MEANINGFUL_NAME = 4; // a short line buffer, and how long a pause counts as a pause
    private static final boolean TEST_TONE = "1".equals(System.getenv("KOKUHAKU_VOICETEST"));

    public static volatile boolean muted = false;
    private static volatile int micChanges = 0;
    private static volatile boolean wanted = false;
    private static Thread thread;
    private static final Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();

    private Voice() {
    }

    /** Called every client tick: the mic runs only while there's someone to talk to. */
    public static void tick(MinecraftClient client) {
        wanted = client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerList().size() > 1;
        if (wanted && thread == null) {
            thread = new Thread(() -> capture(client), "Kokuhaku voice");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static void stopAll() {
        speakers.values().forEach(Speaker::stop);
        speakers.clear();
    }

    private static void capture(MinecraftClient client) {
        try {
            if (TEST_TONE) {
                tone(client);
                return;
            }
            TargetDataLine mic = microphone();
            mic.open(FORMAT, FRAME * 8);
            mic.start();
            byte[] raw = new byte[FRAME * 2];
            short[] samples = new short[FRAME];
            Adpcm encoder = new Adpcm();
            int hangover = 0;
            double noise = GATE;
            int heard = micChanges;
            while (wanted && heard == micChanges) {
                int read = mic.read(raw, 0, raw.length);
                if (read < raw.length) continue;
                long energy = 0;
                for (int i = 0; i < FRAME; i++) {
                    samples[i] = (short) ((raw[i * 2 + 1] << 8) | (raw[i * 2] & 0xFF));
                    energy += Math.abs(samples[i]);
                }
                long level = energy / FRAME;
                // The gate follows the room's own hum, so a noisy fan doesn't hold it open.
                noise = level < noise ? noise * 0.8 + level * 0.2 : noise * 0.995 + level * 0.005;
                boolean loud = level > Math.max(GATE, noise * NOISE_MARGIN) && !muted;
                if (loud && hangover <= 0) fade(samples, true);
                if (!loud && hangover == 1) fade(samples, false);
                hangover = loud ? HANGOVER : hangover - 1;
                if (hangover > 0) {
                    send(client, encoder.encode(samples));
                } else {
                    encoder.reset();
                }
            }
            mic.close();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            // No microphone: you can still hear everyone.
            Kokuhaku.LOG.warn("No microphone for voice chat: {}", e.getMessage());
            while (wanted) sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            thread = null;
        }
    }

    private static void fade(short[] samples, boolean in) {
        for (int i = 0; i < FADE; i++) {
            int at = in ? i : samples.length - 1 - i;
            samples[at] = (short) (samples[at] * i / FADE);
        }
    }

    /** Developer testing only: a steady tone instead of the mic. */
    private static void tone(MinecraftClient client) throws InterruptedException {
        short[] samples = new short[FRAME];
        Adpcm encoder = new Adpcm();
        double phase = 0;
        while (wanted) {
            for (int i = 0; i < FRAME; i++) {
                samples[i] = (short) (Math.sin(phase) * 8000);
                phase += 2 * Math.PI * 330 / 16000;
            }
            send(client, encoder.encode(samples));
            Thread.sleep(40);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void send(MinecraftClient client, byte[] encoded) {
        // Straight from this thread: waiting for the next game tick would bunch frames up.
        if (client.getNetworkHandler() == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByteArray(encoded);
        ClientPlayNetworking.send(Net.VOICE, buf);
    }

    /** Someone near enough is talking. Called as packets arrive, not on the game thread. */
    public static void play(MinecraftClient client, UUID who, float gain, byte[] encoded) {
        float volume = gain * client.options.getSoundVolume(SoundCategory.MASTER) * client.options.getSoundVolume(SoundCategory.VOICE);
        speakers.computeIfAbsent(who, Speaker::new).add(encoded, volume);
    }

    private record Frame(byte[] data, float volume) {
    }

    /** One person's voice: its own buffer, thread and audio line. */
    private static final class Speaker {
        private final BlockingQueue<Frame> frames = new ArrayBlockingQueue<>(MOST_BEHIND + 4);
        private final Thread thread;
        private volatile boolean running = true;
        private int played = 0;

        Speaker(UUID who) {
            thread = new Thread(this::run, "Kokuhaku voice " + who);
            thread.setDaemon(true);
            thread.start();
        }

        void add(byte[] data, float volume) {
            if (frames.size() >= MOST_BEHIND) frames.poll();
            frames.offer(new Frame(data, volume));
        }

        void stop() {
            running = false;
            thread.interrupt();
        }

        private void run() {
            SourceDataLine line = null;
            float volume = 0f;
            try {
                line = outputLine();
                line.open(FORMAT, FRAME * 2 * LINE_FRAMES);
                line.start();
                byte[] pcm = new byte[FRAME * 2], silence = new byte[FRAME * 2];
                boolean talking = false;
                int quiet = 0;
                while (running) {
                    // Wait for a few frames, so a late packet doesn't mean a gap.
                    if (!talking && frames.size() >= START_AFTER) talking = true;
                    Frame frame = talking ? frames.poll() : null;
                    if (frame == null) {
                        // Writing this blocks for as long as it takes to play, so it keeps the time.
                        line.write(silence, 0, silence.length);
                        if (talking && ++quiet > LATE_FRAMES) talking = false;
                        continue;
                    }
                    quiet = 0;
                    short[] samples = Adpcm.decode(frame.data());
                    for (int i = 0; i < samples.length; i++) {
                        // Eased across the frame, so walking away doesn't step the volume.
                        float at = volume + (frame.volume() - volume) * i / samples.length;
                        int sample = (int) (samples[i] * at);
                        pcm[i * 2] = (byte) sample;
                        pcm[i * 2 + 1] = (byte) (sample >> 8);
                    }
                    volume = frame.volume();
                    line.write(pcm, 0, samples.length * 2);
                    if (TEST_TONE && ++played % 50 == 0) Kokuhaku.LOG.info("VOICE played {} frames, {} waiting", played, frames.size());
                }
            } catch (LineUnavailableException | IllegalArgumentException e) {
                Kokuhaku.LOG.warn("Can't play voice chat: {}", e.getMessage());
            } finally {
                if (line != null) line.close();
            }
        }
    }

    /** Every microphone Java can open, for the Settings screen. */
    public static List<String> microphones() {
        List<String> names = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mixer : AudioSystem.getMixerInfo()) {
            if (mixer.getName().startsWith("Port") || mixer.getName().startsWith("Primary")) continue;
            if (AudioSystem.getMixer(mixer).isLineSupported(info)) names.add(mixer.getName());
        }
        return names;
    }

    /** The mic thread lets go and starts again on the new one. */
    public static void restartMicrophone() {
        micChanges++;
    }

    private static TargetDataLine microphone() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mixer : AudioSystem.getMixerInfo()) {
            if (mixer.getName().equals(GameSettings.microphone) && AudioSystem.getMixer(mixer).isLineSupported(info)) return (TargetDataLine) AudioSystem.getMixer(mixer).getLine(info);
        }
        return AudioSystem.getTargetDataLine(FORMAT);
    }

    /** The same speakers or headphones the game's using, if Java can find them. */
    private static SourceDataLine outputLine() throws LineUnavailableException {
        String chosen = MinecraftClient.getInstance().options.getSoundDevice().getValue();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        for (Mixer.Info mixer : AudioSystem.getMixerInfo()) {
            String name = mixer.getName();
            if (name.startsWith("Port") || name.length() < MEANINGFUL_NAME || !chosen.contains(name)) continue;
            Mixer m = AudioSystem.getMixer(mixer);
            if (m.isLineSupported(info)) return (SourceDataLine) m.getLine(info);
        }
        return AudioSystem.getSourceDataLine(FORMAT);
    }
}
