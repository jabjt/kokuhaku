package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ArmView;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.StudentSkins;
import com.kokuhaku.client.DevShots;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

/**
 * Held from behind. Two black sleeves round your chest and a hand coming up
 * over your mouth, and that's all you ever see of her. The letters come up one
 * at a time; every right one buys you air, every wrong one costs you it.
 */
public class GrabScreen extends CutsceneScreen {
    private static final int[] KEYS = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_F};
    private static final String[] LETTERS = {"W", "A", "S", "D", "Q", "E", "F"};
    private static final List<String> WHISPERS = List.of("Shh.", "Shh, shh, shh. It's only me.", "Don't fight. I've got you.", "You're shaking. Why are you shaking?", "You smell so nice.", "Nearly. Nearly. There.");
    private static final float RIGHT_GIVES = 0.09f, WRONG_TAKES = 0.14f, WARNING = 0.3f;
    private static final float ARM_LENGTH = 1.05f, BLACK = 0.12f;
    private static final long WHISPER_MS = 1500, BEGIN_MS = 900;
    private static final List<String> LAST_WHISPERS = List.of("Shh.", "They've all gone.", "I told you. Nobody's coming.", "That's it. Fight. I like it when you try.", "You're doing so well.", "It doesn't matter how many you press.",
            "You can't get away from me.", "I've got you.", "Your heart's going so fast.", "Slower now.", "Nearly.", "There you go.", "Goodnight.");
    /** The longest anyone can hold out, and how long your eyes take to close. */
    private static final long LONGEST_FIGHT_MS = 19_000, EYES_CLOSE_MS = 2_400;
    private static final float WEAKER_EVERY_TIME = 0.65f;

    private final Random random = new Random();
    private final int keysNeeded;
    private final float drainPerMs;
    private final long opened = System.currentTimeMillis();
    private float air = 1f;
    private int done = 0, letter;
    private long last = opened, flash = 0, nextGasp = 0, nextAuto = 0;
    private boolean over = false, wrong = false;
    /** The one you can't win. */
    private final boolean hopeless;
    private long eyesClosing = 0;
    private int shots = 0;

    public GrabScreen(int keysNeeded, float seconds) {
        this.hopeless = keysNeeded <= 0;
        this.keysNeeded = Math.max(0, keysNeeded);
        this.drainPerMs = 1f / (seconds * 1000f);
        this.letter = random.nextInt(KEYS.length);
    }

    private void finish(boolean free) {
        if (over && !hopeless) return;
        over = true;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(free);
        ClientPlayNetworking.send(Net.GRAB_DONE, buf);
    }

    private void hit(boolean right) {
        wrong = !right;
        flash = System.currentTimeMillis();
        if (right) {
            done++;
            air = Math.min(1f, air + RIGHT_GIVES * (hopeless ? WEAKER_EVERY_TIME : 1f));
            Cutscene.struggle();
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_WOOL_BREAK, 0.7f + Math.min(done, 12) * 0.04f, 0.9f));
            if (!hopeless && done >= keysNeeded) {
                finish(true);
                return;
            }
            int next = letter;
            while (next == letter) next = random.nextInt(KEYS.length);
            letter = next;
        } else {
            air -= WRONG_TAKES;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_HURT, 0.6f, 0.5f));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (over || System.currentTimeMillis() - opened < BEGIN_MS) return true;
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] == keyCode) {
                hit(i == letter);
                return true;
            }
        }
        return true;
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        String auto = System.getenv("KOKUHAKU_GRABAUTO");
        if ("win".equals(auto) && now - opened > BEGIN_MS && now > nextAuto && !over) {
            nextAuto = now + 480;
            hit(true);
        }
        if (DevShots.ON && shots < 3 && now - opened > 700 + shots * 1500L) {
            shots++;
            DevShots.take();
        }
        if (eyesClosing > 0) {
            if (now - eyesClosing > EYES_CLOSE_MS) {
                eyesClosing = 0;
                finish(false);
            }
            return;
        }
        if (over) return;
        if (now - opened > BEGIN_MS) air -= (now - last) * drainPerMs;
        last = now;
        // You can slow her down. You can't stop her: the most you can have keeps shrinking.
        if (hopeless) air = Math.min(air, 1f - (now - opened) / (float) LONGEST_FIGHT_MS);
        if (now > nextGasp) {
            nextGasp = now + 420 + (long) (air * 500);
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1f + (1f - air) * 0.4f, 0.9f));
        }
        if (air <= 0f) {
            if (!hopeless) {
                finish(false);
                return;
            }
            over = true;
            eyesClosing = now;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_BREATH, 0.6f, 0.7f));
        }
    }

    /** A real arm, gone black: you can see how she's holding you, and nothing about who she is. */
    private void arm(DrawContext context, float tipX, float tipY, float degrees, float jitter) {
        float x = tipX + (random.nextFloat() - 0.5f) * jitter, y = tipY + (random.nextFloat() - 0.5f) * jitter;
        ArmView.draw(context, StudentSkins.texture(0), true, x, y, degrees, height * ARM_LENGTH, BLACK);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float gone = 1f - Math.max(0f, air);
        float jitter = 2f + gone * 6f;
        int edge = (int) (Math.min(1f, gone * 1.1f) * 225);
        int band = height / 3;
        context.fillGradient(0, 0, width, band, edge << 24, 0);
        context.fillGradient(0, height - band, width, height, 0, edge << 24);
        // One round your chest, fingers dug into your shoulder.
        arm(context, width * 0.34f, height * 0.66f, -58f, jitter);
        // The other comes up over your mouth as your air goes.
        arm(context, width * 0.74f, height * (0.8f - gone * 0.34f), 40f - gone * 22f, jitter);
        long since = now - opened;
        if (since > 300) {
            List<String> lines = hopeless ? LAST_WHISPERS : WHISPERS;
            String whisper = lines.get((int) Math.min(lines.size() - 1, since / WHISPER_MS));
            context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(whisper), width / 2, 22, 0xFFE0283C);
        }
        if (eyesClosing > 0) {
            // Your eyes go, slowly, from the top and the bottom.
            float shut = Math.min(1f, (now - eyesClosing) / (float) EYES_CLOSE_MS);
            int lid = (int) (height * 0.5f * shut * shut * (3f - 2f * shut));
            context.fill(0, 0, width, lid, 0xFF000000);
            context.fill(0, height - lid, width, height, 0xFF000000);
            context.fillGradient(0, lid, width, lid + 24, 0xFF000000, 0);
            context.fillGradient(0, height - lid - 24, width, height - lid, 0, 0xFF000000);
        }
        if (over || since < BEGIN_MS) return;
        // The letter, huge, and how much air's left.
        boolean justMissed = wrong && now - flash < 160;
        var m = context.getMatrices();
        m.push();
        m.translate(width / 2f + (random.nextFloat() - 0.5f) * jitter * 2, height * 0.4f + (random.nextFloat() - 0.5f) * jitter * 2, 0);
        m.scale(5f, 5f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(LETTERS[letter]), 0, -4, justMissed ? 0xFFFF2030 : 0xFFFFFFFF);
        m.pop();
        int barWidth = 180, x = width / 2 - barWidth / 2, y = (int) (height * 0.4f) + 26;
        context.fill(x - 1, y - 1, x + barWidth + 1, y + 5, 0xA0000000);
        context.fill(x, y, x + (int) (barWidth * Math.max(0f, air)), y + 4, air < WARNING ? 0xFFE0283C : 0xFFE8E2E4);
        for (int i = 0; i < keysNeeded; i++) {
            int dot = width / 2 - keysNeeded * 4 + i * 8;
            context.fill(dot, y + 10, dot + 5, y + 13, i < done ? 0xFFE0283C : 0x60FFFFFF);
        }
    }
}
