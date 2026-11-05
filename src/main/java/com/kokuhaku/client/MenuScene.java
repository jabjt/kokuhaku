package com.kokuhaku.client;

import com.kokuhaku.Difficulty;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.net.Net;
import com.kokuhaku.screen.KokuhakuTitleScreen;
import com.kokuhaku.world.MenuRoom;
import com.kokuhaku.world.RunLauncher;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The main menu is a real place: the bedroom from {@link MenuRoom}, seen in
 * third person from behind the player, with the menu screen drawn on top.
 * It's in the same world as the town, so a party stays together when the host
 * starts the run: pressing play just moves everyone down to their beds.
 */
public final class MenuScene {
    private static final GlitchCycle CYCLE = new GlitchCycle();
    private static boolean active = false;
    private static boolean shown = false;

    private MenuScene() {
    }

    public static GlitchCycle cycle() {
        return CYCLE;
    }

    public static boolean isActive() {
        return active;
    }

    /** In the menu the only student around is the girl. */
    public static boolean isMenuGirl(StudentEntity entity) {
        return active && entity.getWorld().isClient && entity.getY() > 90;
    }

    public static StudentEntity girl(MinecraftClient client) {
        if (client.world == null) {
            return null;
        }
        for (Entity e : client.world.getEntities()) {
            if (e instanceof StudentEntity s) {
                return s;
            }
        }
        return null;
    }

    private static void show(MinecraftClient client) {
        active = true;
        shown = false;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        client.options.hudHidden = true;
    }

    /** Loads the world. The title screen appears once you're standing in the bedroom. */
    public static void enter(MinecraftClient client) {
        if (active || client.world != null) {
            return;
        }
        show(client);
        RunLauncher.startNewRun(client);
    }

    /** You've arrived in a friend's menu room. */
    public static void joined(MinecraftClient client) {
        if (!active) show(client);
        Profile.send();
    }

    /** Called every client tick. */
    public static void tick(MinecraftClient client) {
        if (active && !shown && client.world != null && client.player != null && client.currentScreen == null) {
            shown = true;
            boolean auto = "1".equals(System.getenv("KOKUHAKU_AUTOPLAY"));
            client.setScreen(auto ? new KokuhakuTitleScreen() : com.kokuhaku.screen.HeadphonesScreen.first());
            autoplayIn = auto ? 100 : -1;
        }
        if (shown) devPlay(client);
    }

    private static int autoplayIn = -1;
    private static boolean devInvited = false, devJoined = false;
    private static int devInviteShot = 0;

    /**
     * Developer testing only. KOKUHAKU_AUTOPLAY=1 photographs the menu and starts an Easy run,
     * once KOKUHAKU_WAITFOR players are here. KOKUHAKU_INVITE=path makes an invite and writes
     * the code there; KOKUHAKU_JOIN=path reads a code from there and joins it.
     */
    private static void devPlay(MinecraftClient client) {
        String invite = System.getenv("KOKUHAKU_INVITE"), join = System.getenv("KOKUHAKU_JOIN");
        try {
            if (invite != null && !devInvited) {
                devInvited = true;
                Party.invite(client);
            }
            if (invite != null && !Party.code.isEmpty() && !Files.exists(Path.of(invite))) Files.writeString(Path.of(invite), Party.code);
            if (join != null && !devJoined && Files.exists(Path.of(join))) {
                devJoined = true;
                String code = Files.readString(Path.of(join));
                client.send(() -> Party.join(client, code));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (DevShots.ON && invite != null && ++devInviteShot == 240) {
            client.setScreen(new com.kokuhaku.screen.PartyScreen(client.currentScreen));
        } else if (devInviteShot == 300) {
            DevShots.take();
        }
        if (Party.isGuest(client) || join != null || autoplayIn <= 0) return;
        String wanted = System.getenv("KOKUHAKU_WAITFOR");
        if (wanted != null && Party.members.size() < Integer.parseInt(wanted)) return;
        if (--autoplayIn == 0) {
            if (DevShots.ON) DevShots.take();
            if (System.getenv("KOKUHAKU_CONTINUE") != null) continueRun();
            else start(Difficulty.EASY);
        }
    }

    /** The host starts the run for everyone in the room. */
    public static void start(Difficulty difficulty) {
        start(difficulty, System.getenv("KOKUHAKU_DEVOTED") != null, System.getenv("KOKUHAKU_ACCOMPLICE") != null);
    }

    /** devoted: she's known from the start, and you've only to survive. accomplice: one of the party is secretly on her side. */
    public static void start(Difficulty difficulty, boolean devoted, boolean accomplice) {
        MinecraftClient.getInstance().setScreen(new com.kokuhaku.screen.BlackoutScreen(() -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(difficulty.ordinal());
            buf.writeBoolean(devoted);
            buf.writeBoolean(accomplice);
            ClientPlayNetworking.send(Net.START, buf);
        }));
    }

    /** The host carries on the saved run, everyone in the room with them. */
    public static void continueRun() {
        MinecraftClient.getInstance().setScreen(new com.kokuhaku.screen.BlackoutScreen(() -> ClientPlayNetworking.send(Net.CONTINUE, PacketByteBufs.empty())));
    }

    /** The run has started: the menu goes away and the camera goes back to normal. */
    public static void runStarted(MinecraftClient client) {
        Profile.newRun();
        ClientRun.ghost = false;
        active = false;
        shown = false;
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.options.hudHidden = false;
        // It stays black until the waking scene takes over.
        if (!(client.currentScreen instanceof com.kokuhaku.screen.BlackoutScreen)) client.setScreen(new com.kokuhaku.screen.BlackoutScreen(null));
    }

    public static void disconnected() {
        active = false;
        shown = false;
    }

    /** Walks out of this world altogether. */
    public static void leave(MinecraftClient client) {
        disconnected();
        if (client.world != null) {
            client.world.disconnect();
        }
        client.disconnect(new MessageScreen(Text.literal("...")));
    }
}
