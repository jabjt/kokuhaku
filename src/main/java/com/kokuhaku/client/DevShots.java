package com.kokuhaku.client;

import com.kokuhaku.screen.JumpscareScreen;
import com.kokuhaku.screen.LessonScreen;
import com.kokuhaku.screen.TimetableScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;

/** Developer testing only (KOKUHAKU_TOUR=1): saves screenshots to the game folder. */
public final class DevShots {
    public static final boolean ON = "1".equals(System.getenv("KOKUHAKU_TOUR")) || "1".equals(System.getenv("KOKUHAKU_SHOTS"));

    private DevShots() {
    }

    private static final boolean UI = "1".equals(System.getenv("KOKUHAKU_UI"));
    private static int uiTicks = 0, menuShot = 0;
    private static net.minecraft.client.gui.screen.Screen menuTitle;

    /** Opens each game screen in turn and photographs it (KOKUHAKU_UI=1). */
    public static void uiTest(MinecraftClient client) {
        DevBot.tick(client);
        if ("swim".equals(System.getenv("KOKUHAKU_STAGE")) && client.player != null) client.options.jumpKey.setPressed(client.player.isTouchingWater());
        if (System.getenv("KOKUHAKU_CAR") != null && ClientRun.active && ++uiTicks > 240 && uiTicks % 30 == 0) {
            take();
            int seen = 0;
            double nearest = 9999;
            for (var e : client.world.getEntities()) {
                if (!(e instanceof net.minecraft.entity.mob.MobEntity) || !net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).getNamespace().equals("trepscars")) continue;
                seen++;
                nearest = Math.min(nearest, e.distanceTo(client.player));
            }
            com.kokuhaku.Kokuhaku.LOG.info("Client sees {} cars, nearest {}", seen, nearest);
            StringBuilder where = new StringBuilder();
            for (var e : client.world.getEntities()) {
                if (e instanceof net.minecraft.entity.mob.MobEntity && net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).getNamespace().equals("trepscars")) where.append(String.format("%.0f,%.0f ", e.getX(), e.getZ()));
                if (e instanceof com.kokuhaku.entity.StudentEntity s && !s.hasVehicle() && com.kokuhaku.game.Townsfolk.isOne(s) && s.distanceTo(client.player) < 60) where.append(String.format("[%.0f,%.0f] ", e.getX(), e.getZ()));
            }
            com.kokuhaku.Kokuhaku.LOG.info("CLIENT: {}", where);
        }
        if ("1".equals(System.getenv("KOKUHAKU_MENUSHOTS")) && client.currentScreen instanceof com.kokuhaku.screen.KokuhakuTitleScreen | menuShot > 0) {
            menuShot++;
            var title = menuTitle != null ? menuTitle : client.currentScreen;
            menuTitle = title;
            if ("beg".equals(System.getenv("KOKUHAKU_MENUSHOTS2"))) {
                if (menuShot == 60) {
                    client.setScreen(new com.kokuhaku.screen.DifficultyScreen(title));
                    client.currentScreen.mouseClicked(40, client.currentScreen.height / 2 - 50 + 96 + 14, 0);
                }
                int s = client.getWindow().getScaleFactor() > 0 ? (int) client.getWindow().getScaleFactor() : 1;
                if (menuShot == 70) org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getHandle(), 60 * s, (client.getWindow().getScaledHeight() / 2 - 50 + 78) * s);
                if (menuShot == 110 || menuShot == 170 || menuShot == 230 || menuShot == 300) take();
                if (menuShot > 340 && menuShot % 7 == 0 && menuShot < 760) take();
                return;
            }
            if ("profile".equals(System.getenv("KOKUHAKU_MENUSHOTS2"))) {
                if (menuShot == 60) title.mouseClicked(title.width - 14 - 3 * 42 + 6 + 10, title.height - 14 - 36 + 10, 0);
                if (menuShot == 90) take();
                if (menuShot == 100 && !Party.members.isEmpty()) client.setScreen(new com.kokuhaku.screen.ProfileScreen(title, new Party.Member(Party.members.get(0).id(), Party.members.get(0).name(), true, false, 7, 3, 1 | 2 | 16)));
                if (menuShot == 130) take();
                return;
            }
            if (menuShot == 60) client.setScreen(new com.kokuhaku.screen.HowToPlayScreen(title));
            if (menuShot == 80 || menuShot == 130 || menuShot == 180 || menuShot == 250 || menuShot == 300 || menuShot == 350) take();
            if (menuShot == 100) client.currentScreen.mouseClicked(client.currentScreen.width / 2 - 120 + 90, 44, 0);
            if (menuShot == 150) client.currentScreen.mouseClicked(client.currentScreen.width / 2 - 120 + 175, 44, 0);
            if (menuShot == 200) client.setScreen(new com.kokuhaku.screen.SettingsScreen(title));
            if (menuShot == 270) client.currentScreen.mouseClicked(client.currentScreen.width / 2 - 100 + 75, 44, 0);
            if (menuShot == 320) client.currentScreen.mouseClicked(client.currentScreen.width / 2 - 100 + 145, 44, 0);
        }
        if (!UI || !ClientRun.active) return;
        uiTicks++;
        try {
            switch (uiTicks) {
                case 300 -> {
                    var r = new java.util.Random(7);
                    var nbt = com.kokuhaku.game.Lessons.toNbt("History", com.kokuhaku.game.Lessons.intro("History", r), com.kokuhaku.game.Lessons.make("History", r, java.util.List.of(0, 1, 2), 0), true);
                    client.setScreen(new LessonScreen(nbt));
                }
                case 340, 400, 430, 460 -> take();
                case 350 -> {
                    var f = LessonScreen.class.getDeclaredField("index");
                    f.setAccessible(true);
                    f.setInt(client.currentScreen, 2);
                    client.currentScreen.init(client, client.currentScreen.width, client.currentScreen.height);
                }
                case 405, 435 -> {
                    var f = LessonScreen.class.getDeclaredField("index");
                    f.setAccessible(true);
                    f.setInt(client.currentScreen, uiTicks == 405 ? 3 : 5);
                    client.currentScreen.init(client, client.currentScreen.width, client.currentScreen.height);
                }
                case 470 -> {
                    client.setScreen(null);
                    var server = client.getServer();
                    server.execute(() -> server.getPlayerManager().getPlayerList().get(0).networkHandler.requestTeleport(-591.5, 25, -61.5, 90f, 0f));
                }
                case 500, 530 -> take();
                case 505 -> client.setScreen(new TimetableScreen());
                case 540 -> client.setScreen(new JumpscareScreen(""));
                case 670 -> take();
                case 690 -> take();
                default -> {
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void take() {
        MinecraftClient client = MinecraftClient.getInstance();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), text -> {
        });
    }
}
