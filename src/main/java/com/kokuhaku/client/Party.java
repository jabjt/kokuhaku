package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.NetworkUtils;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

/**
 * The party in the corner of the menu. Your friends join your menu room over
 * e4mc, which gives an open world an address anyone can reach. It announces
 * that address in chat, so we take it from there and show it as your invite.
 */
public final class Party {
    public record Member(UUID id, String name, boolean host, boolean spectator, int wins, int losses, int badges) {
    }

    private static final String E4MC_DOMAIN = "text.e4mc_minecraft.domainAssigned";
    private static final String E4MC_ERROR = "text.e4mc_minecraft.error";

    public static List<Member> members = List.of();
    /** Your invite code, once there is one. */
    public static String code = "";
    public static boolean inviting = false;
    /** What went wrong last, for the menu to show. */
    public static String error = "";

    private Party() {
    }

    /** In someone else's menu room. */
    public static boolean isGuest(MinecraftClient client) {
        return client.world != null && !client.isIntegratedServerRunning();
    }

    public static void reset() {
        members = List.of();
        code = "";
        inviting = false;
    }

    /** Opens your world to the internet. The code turns up in {@link #onChat} a moment later. */
    public static void invite(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null || !code.isEmpty() || inviting) return;
        error = "";
        int port = NetworkUtils.findLocalPort();
        // Developer testing only: two test accounts on one machine can't sign in to each other.
        boolean local = "1".equals(System.getenv("KOKUHAKU_LOCAL"));
        if (local || "1".equals(System.getenv("KOKUHAKU_OFFLINE"))) server.setOnlineMode(false);
        if (!server.openToLan(GameMode.ADVENTURE, false, port)) {
            error = "Couldn't open your game to friends.";
            return;
        }
        if (local) code = "localhost:" + port;
        else inviting = true;
    }

    /** Leaves your own menu room and walks into a friend's. */
    public static void join(MinecraftClient client, String address) {
        String trimmed = address.trim();
        if (trimmed.isEmpty()) return;
        error = "";
        MenuScene.leave(client);
        ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(trimmed), new ServerInfo("Party", trimmed, false), false);
    }

    /** Every chat line comes through here. True if it was for us, and shouldn't be shown. */
    public static boolean onChat(Text message) {
        if (!(message.getContent() instanceof TranslatableTextContent content)) return false;
        if (content.getKey().equals(E4MC_DOMAIN)) {
            code = domainIn(content);
            inviting = false;
            return true;
        }
        if (content.getKey().equals(E4MC_ERROR)) {
            error = "Couldn't make an invite. Check your connection.";
            inviting = false;
            return true;
        }
        return false;
    }

    private static String domainIn(TranslatableTextContent content) {
        Object arg = content.getArgs().length > 0 ? content.getArgs()[0] : "";
        if (arg instanceof Text text) {
            // A hidden domain only says "click to copy", so the copy action is the surest place to look.
            String copied = copyValue(text);
            return copied != null ? copied : text.getString();
        }
        return String.valueOf(arg);
    }

    private static String copyValue(Text text) {
        ClickEvent click = text.getStyle().getClickEvent();
        if (click != null && click.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) return click.getValue();
        for (Text sibling : text.getSiblings()) {
            String found = copyValue(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
