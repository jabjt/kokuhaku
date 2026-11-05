package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * PE on the court: five three-pointers. The marker swings across the bar; jump
 * when it's in the red and the ball goes in, anywhere else and it doesn't.
 */
public class HoopScreen extends Screen {
    public static final int SHOTS = 5;
    private static final float[] SWING = {2.0f, 2.6f, 3.3f, 4.1f}, ZONE = {0.2f, 0.16f, 0.12f, 0.09f};
    private static final long FLIGHT_MS = 900, RESULT_MS = 700;
    private static final int BAR = 180, BALL = 7;

    private final float swing, zone;
    private final long opened = System.currentTimeMillis();
    private int shot = 0, score = 0;
    private long thrownAt = 0;
    private boolean made = false, sent = false;

    public HoopScreen(int level) {
        super(KokuhakuClient.menuText("PE"));
        this.swing = SWING[level];
        this.zone = ZONE[level];
    }

    @Override
    protected void init() {
        client.options.hudHidden = true;
    }

    @Override
    public void removed() {
        client.options.hudHidden = false;
    }

    /** Where the marker is on the bar, from -1 to 1. */
    private float marker() {
        return (float) Math.sin((System.currentTimeMillis() - opened) / 1000f * swing);
    }

    private void shoot() {
        if (thrownAt != 0 || sent) return;
        made = Math.abs(marker()) <= zone;
        thrownAt = System.currentTimeMillis();
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_SNOWBALL_THROW, 0.7f));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client.options.jumpKey.matchesKey(keyCode, scanCode)) shoot();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        shoot();
        return true;
    }

    @Override
    public void tick() {
        if (thrownAt == 0 || sent) return;
        long since = System.currentTimeMillis() - thrownAt;
        if (since < FLIGHT_MS + RESULT_MS) return;
        if (made) score++;
        shot++;
        thrownAt = 0;
        client.getSoundManager().play(PositionedSoundInstance.master(made ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 0.4f));
        if (shot >= SHOTS) {
            sent = true;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(score);
            buf.writeVarInt(SHOTS);
            ClientPlayNetworking.send(Net.PE_DONE, buf);
            close();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void ball(DrawContext context, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            int across = (int) Math.sqrt(radius * radius - dy * dy);
            context.fill(x - across, y + dy, x + across, y + dy + 1, 0xFFD8691E);
        }
        context.fill(x - radius, y, x + radius, y + 1, 0xFF3A1C08);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int mid = width / 2, barY = height - 44;
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText("Shot " + Math.min(shot + 1, SHOTS) + " of " + SHOTS + "   ·   " + score + " in"), mid, 12, 0xFFF4F0F0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Press Jump when the marker's in the red"), mid, barY - 14, 0xFFD8D0D4);
        context.fill(mid - BAR / 2 - 1, barY - 1, mid + BAR / 2 + 1, barY + 9, 0xC0000000);
        context.fill(mid - BAR / 2, barY, mid + BAR / 2, barY + 8, 0xFF4A4A52);
        int red = (int) (BAR / 2f * zone);
        context.fill(mid - red, barY, mid + red, barY + 8, 0xFFE0283C);
        int at = mid + (int) (marker() * BAR / 2f);
        context.fill(at - 1, barY - 3, at + 1, barY + 11, 0xFFFFFFFF);
        if (thrownAt == 0) {
            ball(context, mid, height - 12, BALL);
            return;
        }
        long since = System.currentTimeMillis() - thrownAt;
        float t = Math.min(1f, since / (float) FLIGHT_MS);
        // Up in an arc, towards the rim in the middle of your view, getting smaller. A miss drifts wide and drops.
        int wide = made ? 0 : (int) (t * t * 34);
        int x = mid + wide, y = (int) (height - 12 + (height / 2f - (height - 12)) * t - Math.sin(t * Math.PI) * height * 0.28f);
        if (since < FLIGHT_MS) ball(context, x, y, Math.max(2, (int) (BALL * (1f - t * 0.7f))));
        else context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(made ? "Three!" : "Off the rim."), mid, height / 2 + 14, made ? 0xFF70E070 : 0xFFE0283C);
    }
}
