package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.EndingsBook;
import com.kokuhaku.client.KeyBindings;
import com.kokuhaku.client.MenuScene;
import com.kokuhaku.client.Party;
import com.kokuhaku.client.Voice;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster;
import com.kokuhaku.net.Net;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import com.kokuhaku.sound.MenuAmbience;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

/**
 * Main menu, drawn over the bedroom world. Only "Play Game" and "How to Play"
 * exist. Her head follows the mouse; stop moving and she stares at you, and
 * if you stay still long enough, she ends it.
 */
public class KokuhakuTitleScreen extends Screen {
    private static final long IDLE_STARE_MS = 2_000;
    private static final long IDLE_FIRST_MS = 24_000;  // "are you there?"
    private static final long IDLE_SECOND_MS = 27_000; // "too scared?"
    private static final long IDLE_QUIT_MS = 30_000;   // game closes
    private static final float MAX_HEAD_TURN = 60f;    // degrees either side of her body
    private static final int BOXES = Roster.MAX_PARTY, BOX = 36, BOX_GAP = 6, MENU_W = 100, MENU_H = 38;
    private static final float SPECTATOR_ALPHA = 0.3f;
    /** Whose box has its little menu open, or -1. */
    private int menuFor = -1;

    private final Random random = new Random();
    private final MenuAmbience ambience = new MenuAmbience();
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    private long lastMoveTime = System.currentTimeMillis();
    private boolean quitting = false;
    /** Only the first time: coming back from Settings shouldn't go dark again. */
    private static boolean cameUp = false;

    private final long opened = cameUp ? 0L : System.currentTimeMillis();
    private static final long FADE_IN_MS = 1800;

    public KokuhakuTitleScreen() {
        super(Text.literal("Kokuhaku"));
        cameUp = true;
    }

    /** It comes up out of the dark, rather than just being there. */
    private void renderFadeIn(DrawContext context, long now) {
        float left = 1f - (now - opened) / (float) FADE_IN_MS;
        if (left <= 0f) return;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 600);
        context.fill(0, 0, width, height, (int) (left * left * 255) << 24);
        context.getMatrices().pop();
    }

    @Override
    protected void init() {
        lastMoveTime = System.currentTimeMillis();
        int x = Math.max(24, width / 12);
        int y = height / 2;
        if (Party.isGuest(client)) {
            addDrawableChild(new MenuButton(x, y + 24, 150, 20, KokuhakuClient.menuText("Leave Party"), b -> {
                MenuScene.leave(client);
                client.setScreen(new TitleScreen());
            }));
        } else if (com.kokuhaku.game.SaveSlot.exists()) {
            // One run at a time: it has to be finished before there's another.
            com.kokuhaku.game.SaveSlot.Summary saved = com.kokuhaku.game.SaveSlot.summary();
            addDrawableChild(new MenuButton(x, y, 150, 20, KokuhakuClient.menuText("Continue  \u00b7  Day " + saved.day()), b -> MenuScene.continueRun()));
            MenuButton play = new MenuButton(x, y + 24, 150, 20, KokuhakuClient.menuText("Play Game"), b -> {
            });
            play.active = false;
            play.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Finish your saved run first. It's Day " + saved.day() + (saved.players().isEmpty() ? "" : ", with " + saved.players()) + ".")));
            addDrawableChild(play);
        } else {
            addDrawableChild(new MenuButton(x, y, 150, 20, KokuhakuClient.menuText("Play Game"),
                    b -> client.setScreen(new DifficultyScreen(this))));
        }
        int below = !Party.isGuest(client) && com.kokuhaku.game.SaveSlot.exists() ? 24 : 0;
        addDrawableChild(new MenuButton(x, y + below + (Party.isGuest(client) ? 48 : 24), 150, 20, KokuhakuClient.menuText("How to Play"),
                b -> client.setScreen(new HowToPlayScreen(this))));
        addDrawableChild(new MenuButton(x, y + below + (Party.isGuest(client) ? 72 : 48), 150, 20, KokuhakuClient.menuText("Settings"),
                b -> client.setScreen(new SettingsScreen(this))));
        memberMenu();
    }

    private boolean mine(Party.Member m) {
        return m.id().equals(client.player.getUuid());
    }

    /** Someone's box has been clicked. */
    private void memberMenu() {
        if (menuFor < 0 || menuFor >= Party.members.size()) return;
        Party.Member m = Party.members.get(menuFor);
        int x = menuX(), y = menuY();
        addDrawableChild(new MenuButton(x + 6, y + 4, MENU_W - 8, 14, KokuhakuClient.menuText("View profile"), b -> {
            menuFor = -1;
            client.setScreen(new ProfileScreen(this, m));
        }));
        if (!mine(m)) return;
        addDrawableChild(new MenuButton(x + 6, y + 20, MENU_W - 8, 14, KokuhakuClient.menuText(m.spectator() ? "Stop spectating" : "Spectator"), b -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(!m.spectator());
            ClientPlayNetworking.send(Net.SPECTATE, buf);
            menuFor = -1;
            clearAndInit();
        }));
    }

    private int menuX() {
        return boxX(menuFor) + BOX - MENU_W;
    }

    private int menuY() {
        return boxY() - MENU_H - 4;
    }

    private int boxX(int index) {
        return width - 14 - (BOXES - index) * (BOX + BOX_GAP) + BOX_GAP;
    }

    private int boxY() {
        return height - 14 - BOX;
    }

    /** You and your friends in the bottom right: a face for each, and a + for each space left. */
    private void renderParty(DrawContext context, int mouseX, int mouseY) {
        List<Party.Member> members = Party.members;
        boolean guest = Party.isGuest(client);
        for (int i = 0; i < BOXES; i++) {
            int x = boxX(i), y = boxY();
            boolean hover = mouseX >= x && mouseX < x + BOX && mouseY >= y && mouseY < y + BOX;
            context.fill(x, y, x + BOX, y + BOX, hover && !guest ? 0x70200810 : 0x60000000);
            context.drawBorder(x, y, BOX, BOX, hover && !guest ? 0xFFE0283C : 0x50FFFFFF);
            if (i < members.size()) {
                Party.Member m = members.get(i);
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(m.id());
                Identifier skin = entry != null ? entry.getSkinTexture() : DefaultSkinHelper.getTexture(m.id());
                // A spectator's barely there.
                if (m.spectator()) {
                    RenderSystem.enableBlend();
                    RenderSystem.setShaderColor(1f, 1f, 1f, SPECTATOR_ALPHA);
                }
                PlayerSkinDrawer.draw(context, skin, x + 4, y + 4, BOX - 8);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                if (hover && menuFor < 0) context.drawCenteredTextWithShadow(textRenderer, Text.literal(m.name() + (m.spectator() ? " (spectating)" : "")), x + BOX / 2, y - 12, 0xFFE8E2E4);
            } else if (!guest) {
                context.getMatrices().push();
                context.getMatrices().translate(x + BOX / 2f, y + BOX / 2f - 7, 0);
                context.getMatrices().scale(2f, 2f, 1f);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("+"), 0, 0, hover ? 0xFFE0283C : 0xFFB0A8AC);
                context.getMatrices().pop();
            }
        }
        if (members.size() > 1) {
            String mic = Voice.muted ? "Muted. Press V to talk." : "Mic on. Press V to mute.";
            context.drawTextWithShadow(textRenderer, Text.literal(mic), width - 14 - textRenderer.getWidth(mic), boxY() - 24, 0xFF8C7C80);
        }
        if (!Party.error.isEmpty()) {
            int w = textRenderer.getWidth(Party.error);
            context.drawTextWithShadow(textRenderer, Text.literal(Party.error), width - 14 - w, boxY() - 12, 0xFFD01C30);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuFor >= 0) {
            boolean inside = mouseX >= menuX() && mouseX < menuX() + MENU_W && mouseY >= menuY() && mouseY < menuY() + MENU_H;
            if (inside) return super.mouseClicked(mouseX, mouseY, button);
            menuFor = -1;
            clearAndInit();
        }
        for (int i = 0; i < Party.members.size() && i < BOXES; i++) {
            int x = boxX(i), y = boxY();
            if (mouseX >= x && mouseX < x + BOX && mouseY >= y && mouseY < y + BOX) {
                menuFor = i;
                clearAndInit();
                return true;
            }
        }
        if (!Party.isGuest(client)) {
            for (int i = Party.members.size(); i < BOXES; i++) {
                int x = boxX(i), y = boxY();
                if (mouseX >= x && mouseX < x + BOX && mouseY >= y && mouseY < y + BOX) {
                    client.setScreen(new PartyScreen(this));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(new QuitScreen(this));
            return true;
        }
        if (KeyBindings.MUTE.matchesKey(keyCode, scanCode)) {
            Voice.muted = !Voice.muted;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            lastMoveTime = now;
        }
        // She only comes for you when you're alone: a party can wait for each other.
        if (Party.isGuest(client) || Party.members.size() > 1 || Party.inviting || !Party.code.isEmpty()) lastMoveTime = now;
        long idle = now - lastMoveTime;
        ambience.tick();
        MenuScene.cycle().update();

        aimGirlHead(mouseX, mouseY, idle > IDLE_STARE_MS);

        // Glitch: torn horizontal bars across the screen while she spins.
        if (MenuScene.cycle().glitching()) {
            for (int i = 0; i < 6; i++) {
                int y = random.nextInt(Math.max(1, height));
                int h = 1 + random.nextInt(3);
                context.fill(0, y, width, y + h, random.nextBoolean() ? 0x30FFFFFF : 0x60000000);
            }
        }
        // Shade the left side so the menu text stays readable.
        for (int i = 0; i < 8; i++) {
            context.fill(0, 0, width * (8 - i) / 20, height, 0x10000000);
        }

        int x = Math.max(24, width / 12);
        context.getMatrices().push();
        context.getMatrices().translate(x, height / 2f - 50, 0);
        context.getMatrices().scale(3f, 3f, 1f);
        // Minecraft's own &k: the letters never hold still, and there's nothing underneath to read.
        context.drawTextWithShadow(textRenderer, Text.literal("xxxxxxxx").formatted(Formatting.OBFUSCATED), 0, 0, 0xFFE8E2E4);
        context.getMatrices().pop();
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(EndingsBook.summary()), x, height - 18, 0xFF6C6C74);
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("someone left a note in your locker"),
                x, height / 2 - 18, 0xFF8C7C80);

        if (Party.isGuest(client)) {
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("Waiting for the host..."), x, height / 2 + 6, 0xFF8C7C80);
        }
        if (menuFor >= 0) {
            context.fill(menuX(), menuY(), menuX() + MENU_W, menuY() + MENU_H, 0xE0100C10);
            context.drawBorder(menuX(), menuY(), MENU_W, MENU_H, 0x50FFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
        renderParty(context, mouseX, mouseY);
        renderIdle(context, idle);
        renderFadeIn(context, now);
    }

    /**
     * Turns her head towards the point in the room that sits under the mouse,
     * or straight at the camera when you've gone still.
     */
    private void aimGirlHead(int mouseX, int mouseY, boolean stare) {
        StudentEntity girl = MenuScene.girl(client);
        if (girl == null) {
            return;
        }
        Camera cam = client.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        Vec3d target = camPos;
        if (!stare) {
            // Un-project the mouse onto a plane 2 blocks in front of the camera.
            Vec3d forward = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
            Vec3d right = Vec3d.fromPolar(0, cam.getYaw() + 90f);
            Vec3d up = right.crossProduct(forward).normalize();
            double nx = mouseX / (double) width * 2 - 1;
            double ny = mouseY / (double) height * 2 - 1;
            double d = 2.0;
            double halfH = d * Math.tan(Math.toRadians(client.options.getFov().getValue() / 2.0));
            double halfW = halfH * width / (double) height;
            target = camPos.add(forward.multiply(d)).add(right.multiply(nx * halfW)).add(up.multiply(-ny * halfH));
        }

        Vec3d eye = girl.getEyePos();
        Vec3d dir = target.subtract(eye);
        float wantYaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90f;
        float wantPitch = (float) -(MathHelper.atan2(dir.y, dir.horizontalLength()) * 180.0 / Math.PI);
        float body = girl.bodyYaw;
        wantYaw = body + MathHelper.clamp(MathHelper.wrapDegrees(wantYaw - body), -MAX_HEAD_TURN, MAX_HEAD_TURN);
        wantPitch = MathHelper.clamp(wantPitch, -40f, 40f);

        // Ease towards the target so the head turns smoothly rather than snapping.
        float yaw = girl.headYaw + MathHelper.wrapDegrees(wantYaw - girl.headYaw) * 0.15f;
        float pitch = girl.getPitch() + (wantPitch - girl.getPitch()) * 0.15f;
        girl.headYaw = girl.prevHeadYaw = yaw;
        girl.setPitch(pitch);
        girl.prevPitch = pitch;
    }

    private void renderIdle(DrawContext context, long idle) {
        if (idle < IDLE_FIRST_MS) {
            return;
        }
        // Screen slowly darkens while you're gone.
        float dark = Math.min(1f, (idle - IDLE_FIRST_MS) / (float) (IDLE_QUIT_MS - IDLE_FIRST_MS));
        context.fill(0, 0, width, height, 400, ((int) (dark * 200) << 24));

        String line = idle < IDLE_SECOND_MS ? "are you there?" : "too scared?";
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f - 8, 500);
        context.getMatrices().scale(2f, 2f, 1f);
        int flicker = (System.currentTimeMillis() / 90) % 7 == 0 ? 0xFF5A0A12 : 0xFFD01C30;
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(line), 0, 0, flicker);
        context.getMatrices().pop();

        if (idle >= IDLE_QUIT_MS && !quitting) {
            quitting = true;
            client.scheduleStop();
        }
    }
}
