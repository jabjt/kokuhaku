package com.kokuhaku.screen;

import com.kokuhaku.client.ClientRun;
import com.kokuhaku.client.GhostGlyphs;
import com.kokuhaku.client.KeyBindings;
import com.kokuhaku.client.PhotoAlbum;
import com.kokuhaku.game.Phone;
import com.kokuhaku.game.Schedule;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Your phone, held up in front of you while the world carries on behind it. A list of
 * chats, and each chat a thread of bubbles: theirs grey on the left, yours green on the
 * right with ticks for sent, delivered and read. You can still walk while you look at it.
 *
 * In No One Will Save You she has your phone, and it never finds a signal: what you send
 * gets one tick and goes nowhere, though she answers anyway. When something is close
 * behind you the screen tears and jitters. And the dead can still read their messages,
 * though not all the letters (see {@link GhostGlyphs}), and can't answer any of them.
 *
 * Everything is drawn in the phone's own units, then scaled to fill most of the window's
 * height at a whole number of real pixels per unit, so it stays sharp at any size or GUI scale.
 */
public class PhoneScreen extends Screen {
    private static final int W = 170, H = 330, BEZEL = 4;
    private static final int SX = BEZEL, SY = BEZEL, SR = W - BEZEL, SB = H - BEZEL;
    private static final int STATUS_H = 12, HEADER_H = 26, BANNER_H = 11, HOME_H = 9, INPUT_H = 18, CHIP_H = 15, CHIP_GAP = 3, ROW_H = 30;
    private static final int BUBBLE_MAX = 112, PAD_X = 5, PAD_Y = 3, LINE = 9, META_GAP = 6, TAIL = 4, TYPING_H = 18;
    /** Small print: about 70%, snapped to whole real pixels, or full size where the phone is drawn too small for it. */
    private float small = 0.7f;
    private static final int REFRESH_TICKS = 40;
    private static final long TOAST_MS = 2200;

    private static final int FRAME = 0xFF0D0D0F, FRAME_EDGE = 0xFF2C2C31, WALL = 0xFF0B141A, BAR = 0xFF202C33, DOODLE = 0x0CFFFFFF;
    private static final int IN = 0xFF202C33, OUT = 0xFF005C4B, TEXT = 0xFFE9EDEF, META = 0xFF8696A0, OUT_META = 0xFF9FC5BD;
    private static final int GREEN = 0xFF00A884, BLUE = 0xFF53BDEB, PILL = 0xFF182229, DIVIDER = 0xFF222D34, HOVER = 0xFF2A3942;
    private static final int INPUT_BG = 0xFF2A3942, AMBER = 0xFFFFD279, AMBER_DIM = 0xFFB8954F, BANNER_TEXT = 0xFF111B21, HER = 0xFFE0283C;
    private static final int[] AVATARS = {0xFF7B5EA7, 0xFF3E7CB1, 0xFFB5651D, 0xFF2E8B57, 0xFFC2185B, 0xFF00897B};
    private static final String UNKNOWN = "Unknown number", CLASS_CHAT = "Class 2-A", ELLIPSIS = "…";

    /**
     * One message as shown. Sender is who wrote it in the class chat. A deleted one has only its
     * notice for a body; dead is a ghost's, mostly lost letters for the living; photo is the
     * picture's number, or -1.
     */
    private record Msg(int index, int day, String clock, String from, String body, boolean mine, String sender, boolean deleted, int photo, boolean dead) {
    }

    /** How many letters in a hundred of a ghost's message the living lose. */
    public static final int DEAD_LOST = 60;
    private static final int PHOTO_W = 104, PHOTO_H = 58, WORD_H = 12, WORD_GAP = 3;
    private static final int[] NAMES = {0xFFF7A35C, 0xFF53BDEB, 0xFFE57BB6, 0xFF8BD17C, 0xFFC79BF2, 0xFFF2D06B, 0xFF6FD8D1};

    private static final class Chat {
        final String name, shown;
        final List<Msg> msgs = new ArrayList<>();
        int last, lastIncoming = -1;

        Chat(String name) {
            this.name = name;
            this.shown = GhostGlyphs.filter(name, name.hashCode());
        }
    }

    /** One thing in a conversation: a bubble, a day pill or the unread strip. */
    private static final class Row {
        static final int BUBBLE = 0, DAY = 1, UNREAD = 2;
        int kind, y, h, x, w, metaW;
        Msg msg;
        List<OrderedText> lines;
        boolean tail, inlineMeta, named;
        String label;
    }

    /** The test bot reads this. */
    private NbtList messages;
    private List<String> replies = List.of();
    private String replyIn = "";
    private boolean devoted;
    private float watched;

    private final List<Chat> chats = new ArrayList<>();
    private final Set<Integer> unread = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private Chat open;
    private int contentH, dividerAt = -1, dividerCount;
    /** How far up from the newest message you've scrolled, in phone units. */
    private double scroll;
    private double listScroll;
    /** Replying: the message count when you sent, until an answer comes. */
    private int awaitingAfter = -1;
    /** Where she's typing (from the server), until when, and the pattern of her stopping and starting. */
    private String typingChat;
    private long typingEnd, typingSeed;
    /** A ghost's words so far, as places in Phone.GHOST_WORDS, and where each word's pill was drawn. */
    private final List<Integer> composing = new ArrayList<>();
    private final List<int[]> wordBoxes = new ArrayList<>();
    private static final long GHOST_EVERY_MS = 30000;
    private static long lastWords;
    private String toast;
    private long toastUntil;

    private float scale = 1f, ox, oy;
    private int dx;
    private int bodyTop, bodyBottom, chipsTop;
    private int ticks;
    private final Random random = new Random();
    private long glitchUntil, glitchSeed;
    private int glitchDx;

    public PhoneScreen(NbtCompound data) {
        super(Text.literal("Messages"));
        // Laying out the bubbles needs it now, before the screen is shown.
        textRenderer = MinecraftClient.getInstance().textRenderer;
        // Developer testing only: see it as the dead do.
        if ("1".equals(System.getenv("KOKUHAKU_PHONEGHOST"))) ClientRun.ghost = true;
        update(data);
        // There's something to answer: straight to it.
        if (!replies.isEmpty() && System.getenv("KOKUHAKU_PHONELIST") == null) for (Chat c : chats) if (c.name.equals(replyIn)) openChat(c);
    }

    /** A phone packet arrives: shown in the open phone, or the phone is taken out, unless it's only a refresh. */
    public static void receive(MinecraftClient client, NbtCompound nbt) {
        if (client.currentScreen instanceof PhoneScreen phone) phone.update(nbt);
        else if (!nbt.getBoolean("Refresh")) client.setScreen(new PhoneScreen(nbt));
    }

    // ---- What's on the phone ----

    public void update(NbtCompound data) {
        boolean first = messages == null;
        int before = first ? 0 : messages.size();
        messages = data.getList("Messages", NbtElement.COMPOUND_TYPE);
        replies = data.getList("Replies", NbtElement.STRING_TYPE).stream().map(NbtElement::asString).toList();
        replyIn = data.getString("ReplyIn");
        devoted = data.getBoolean("Devoted");
        watched = data.getFloat("Watched");
        typingChat = data.contains("TypingIn") ? data.getString("TypingIn") : null;
        typingEnd = System.currentTimeMillis() + data.getInt("TypingFor");
        typingSeed = data.getLong("TypingSeed");
        String openName = open == null ? null : open.name;

        if (first) {
            int left = data.getInt("Unread");
            for (int i = messages.size() - 1; i >= 0 && left > 0; i--) {
                if (!messages.getCompound(i).getBoolean("Mine")) {
                    unread.add(i);
                    left--;
                }
            }
        } else {
            // Arrived while you were looking: unread unless it's in the chat in front of you.
            for (int i = before; i < messages.size(); i++) {
                NbtCompound m = messages.getCompound(i);
                if (!m.getBoolean("Mine") && !m.getString("From").equals(openName)) unread.add(i);
            }
        }

        Map<String, Chat> byName = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            NbtCompound m = messages.getCompound(i);
            String from = m.getString("From");
            String clock = m.contains("Clock") ? m.getString("Clock") : m.getString("When");
            boolean mine = m.getBoolean("Mine"), deleted = m.getBoolean("Deleted"), dead = m.getBoolean("Dead");
            String raw = m.getString("Body"), body;
            long seed = i * 7919L + from.hashCode();
            if (deleted) body = GhostGlyphs.filter(mine ? "You deleted this message" : "This message was deleted", seed);
            else if (dead && !ghost()) body = GhostGlyphs.scramble(raw, seed, DEAD_LOST);
            else body = GhostGlyphs.filter(raw, seed);
            String sender = dead ? "Unknown" : GhostGlyphs.filter(m.getString("Sender"), m.getString("Sender").hashCode());
            Msg msg = new Msg(i, m.getInt("Day"), clock, from, body, mine, sender, deleted, m.contains("Photo") ? m.getInt("Photo") : -1, dead);
            Chat chat = byName.computeIfAbsent(from, Chat::new);
            chat.msgs.add(msg);
            chat.last = i;
            if (!msg.mine()) chat.lastIncoming = i;
        }
        chats.clear();
        chats.addAll(byName.values());
        chats.sort(Comparator.comparingInt((Chat c) -> c.last).reversed());

        // Developer testing only: go straight to this chat once it's there.
        String devChat = System.getenv("KOKUHAKU_PHONECHAT") == null ? null : System.getenv("KOKUHAKU_PHONECHAT").replace('_', ' ');
        if (devChat != null && open == null && byName.containsKey(devChat)) {
            openChat(byName.get(devChat));
            openName = devChat;
        }
        if (awaitingAfter >= 0 && openName != null) {
            Chat c = byName.get(openName);
            if (c != null && c.lastIncoming >= awaitingAfter) awaitingAfter = -1;
        }
        if (openName != null) {
            Chat c = byName.get(openName);
            if (c == null) {
                open = null;
            } else {
                int oldH = contentH;
                open = c;
                layout();
                // Reading further up: stay on what you were reading.
                if (scroll > 0) scroll += contentH - oldH;
            }
        }
    }

    /** The unread strip stays where it was for as long as the chat is open, though they're read now. */
    private void openChat(Chat c) {
        open = c;
        scroll = 0;
        dividerAt = -1;
        dividerCount = 0;
        for (Msg m : c.msgs) {
            if (!unread.contains(m.index())) continue;
            if (dividerAt < 0) dividerAt = m.index();
            dividerCount++;
        }
        unread.removeIf(i -> c.msgs.stream().anyMatch(m -> m.index() == i));
        layout();
    }

    private boolean noSignal() {
        return devoted || ClientRun.temper >= 0;
    }

    private boolean ghost() {
        return ClientRun.ghost;
    }

    /** She's typing in the chat open in front of you. */
    private boolean typing() {
        return open != null && typingIn(open.name);
    }

    /**
     * Whether she's typing in this chat right now. She types in bursts: worked back from the
     * moment it's due, the last second or two is always typing, and before that she stops and
     * starts. The pattern comes from the server's seed, so it's the same on every refresh.
     */
    private boolean typingIn(String chat) {
        long left = typingEnd - System.currentTimeMillis();
        if (typingChat == null || !typingChat.equals(chat) || left <= 0) return false;
        Random r = new Random(typingSeed);
        long at = 0;
        for (boolean on = true; ; on = !on) {
            at += on ? 1200 + r.nextInt(1300) : 600 + r.nextInt(1400);
            if (left <= at) return on;
        }
    }

    private List<String> chips() {
        return open != null && open.name.equals(replyIn) && awaitingAfter < 0 ? replies : List.of();
    }

    /** How much something's after you, 0..1: the screen tears in proportion. */
    private float glitch() {
        float g = watched;
        if (ClientRun.chase >= 0) g = 1f;
        if (ClientRun.temper >= 0) g = Math.max(g, (ClientRun.temper - 40) / 60f);
        g = Math.max(g, (ClientRun.dread - 70) / 60f);
        String forced = System.getenv("KOKUHAKU_PHONEWATCHED");
        if (forced != null) g = Float.parseFloat(forced);
        return MathHelper.clamp(g, 0f, 1f);
    }

    // ---- Layout: worked out once per change, since the phone is always the same size inside ----

    private void layout() {
        rows.clear();
        int y = 6, lastDay = Integer.MIN_VALUE;
        Boolean lastMine = null;
        String lastSender = null;
        for (Msg m : open.msgs) {
            if (m.day() != lastDay) {
                y += marker(Row.DAY, dayLabel(m.day()), y);
                lastDay = m.day();
                lastMine = null;
            }
            if (m.index() == dividerAt) {
                y += marker(Row.UNREAD, dividerCount + (dividerCount == 1 ? " UNREAD MESSAGE" : " UNREAD MESSAGES"), y);
                lastMine = null;
            }
            // In the group, each new person starts a new run, with their name on it.
            boolean tail = lastMine == null || lastMine != m.mine() || !m.mine() && !m.sender().equals(lastSender);
            if (tail && lastMine != null) y += 4;
            y += bubble(m, tail, y) + 2;
            lastMine = m.mine();
            lastSender = m.sender();
        }
        contentH = y + 4;
    }

    private int marker(int kind, String label, int y) {
        Row r = new Row();
        r.kind = kind;
        r.label = GhostGlyphs.filter(label, label.hashCode());
        r.y = y;
        r.h = 16;
        rows.add(r);
        return r.h;
    }

    /**
     * The bubble fits its text, wrapped at the widest a bubble goes. The time and ticks sit
     * at the end of the last line if there's room for them there, or on a line of their own.
     */
    private int bubble(Msg m, boolean tail, int y) {
        Row r = new Row();
        r.kind = Row.BUBBLE;
        r.msg = m;
        r.tail = tail;
        r.y = y;
        // A deleted one is a grey notice in italics behind a no-entry sign.
        int indent = m.deleted() ? 9 : 0;
        Text body = m.deleted() ? Text.literal(m.body()).formatted(Formatting.ITALIC) : Text.literal(m.body());
        r.lines = m.body().isEmpty() ? List.of() : textRenderer.wrapLines(body, BUBBLE_MAX - indent);
        boolean photo = m.photo() >= 0;
        if (r.lines.isEmpty() && !photo) r.lines = List.of(OrderedText.EMPTY);
        int widest = photo ? PHOTO_W : 0;
        for (OrderedText l : r.lines) widest = Math.max(widest, textRenderer.getWidth(l) + indent);
        int last = r.lines.isEmpty() ? PHOTO_W : textRenderer.getWidth(r.lines.get(r.lines.size() - 1)) + indent;
        r.metaW = smallWidth(m.clock()) + (m.mine() && !m.deleted() ? 10 : 0);
        // Over a photo with no caption, the time sits on the picture's corner.
        r.inlineMeta = !r.lines.isEmpty() && last + META_GAP + r.metaW <= BUBBLE_MAX;
        int textW = r.inlineMeta ? Math.max(widest, last + META_GAP + r.metaW) : Math.max(widest, r.metaW);
        r.named = r.tail && !m.mine() && !m.sender().isEmpty();
        if (r.named) textW = Math.max(textW, smallWidth(m.sender()));
        r.w = textW + PAD_X * 2;
        r.h = PAD_Y * 2 + (r.named ? metaH() + 3 : 0) + (photo ? PHOTO_H + (r.lines.isEmpty() ? 0 : 3) : 0)
                + (r.lines.isEmpty() ? 0 : r.lines.size() * LINE - 1) + (r.inlineMeta || photo && r.lines.isEmpty() ? 0 : metaH() + 2);
        r.x = m.mine() ? SR - 7 - r.w : SX + 7;
        rows.add(r);
        return r.h;
    }

    private static String dayLabel(int day) {
        if (day == ClientRun.day) return "TODAY";
        if (day == ClientRun.day - 1) return "YESTERDAY";
        return "DAY " + day;
    }

    // ---- Sizing ----

    @Override
    protected void init() {
        double gui = client.getWindow().getScaleFactor();
        float fit = Math.min((height - 16) / (float) H, (width - 16) / (float) W);
        // A whole number of real pixels per phone unit, where there's room for at least one.
        float pixels = (float) Math.floor(fit * gui);
        scale = pixels >= 1 ? (float) (pixels / gui) : fit;
        ox = (width - W * scale) / 2f;
        oy = (height - H * scale) / 2f;
        // Below three real pixels a unit, shrunk letters lose strokes: they're drawn full size instead.
        double real = scale * gui;
        float was = small;
        small = real < 2.5 ? 1f : (float) (Math.round(0.7 * real) / real);
        if (small != was && open != null) layout();
    }

    /** How tall small print stands, in phone units. */
    private int metaH() {
        return Math.round(7 * small);
    }

    private double localX(double mouseX) {
        return (mouseX - ox) / scale - dx;
    }

    private double localY(double mouseY) {
        return (mouseY - oy) / scale;
    }

    private void scissor(DrawContext ctx, int x1, int y1, int x2, int y2) {
        ctx.enableScissor(Math.round(ox + (x1 + dx) * scale), Math.round(oy + y1 * scale), Math.round(ox + (x2 + dx) * scale), Math.round(oy + y2 * scale));
    }

    // ---- Real time: the game goes on, and so can you ----

    @Override
    public boolean shouldPause() {
        return false;
    }

    private KeyBinding[] walking() {
        return new KeyBinding[]{client.options.forwardKey, client.options.backKey, client.options.leftKey, client.options.rightKey, client.options.sneakKey};
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % REFRESH_TICKS == 0 && ClientRun.active) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(true);
            ClientPlayNetworking.send(Net.OPEN_PHONE, buf);
        }
        long now = System.currentTimeMillis();
        float g = glitch();
        if (now > glitchUntil && g > 0.05f && random.nextFloat() < g * 0.03f) {
            glitchUntil = now + 60 + random.nextInt(160);
            glitchDx = (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(1 + (int) (g * 2)));
            glitchSeed = random.nextLong();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.PHONE.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && open != null) {
            open = null;
            return true;
        }
        for (KeyBinding k : walking()) {
            if (k.matchesKey(keyCode, scanCode)) {
                k.setPressed(true);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (KeyBinding k : walking()) if (k.matchesKey(keyCode, scanCode)) k.setPressed(false);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        for (KeyBinding k : walking()) k.setPressed(false);
    }

    // ---- Touch ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (open != null) scroll += amount * 16;
        else listScroll -= amount * 16;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = localX(mouseX), y = localY(mouseY);
        if (x < -2 || x > W + 2 || y < 0 || y > H) {
            close();
            return true;
        }
        if (open == null) {
            if (y >= bodyTop && y < bodyBottom && x >= SX && x < SR) {
                int i = (int) ((y - bodyTop + listScroll) / ROW_H);
                if (i >= 0 && i < chats.size()) openChat(chats.get(i));
            }
            return true;
        }
        if (y >= SY + STATUS_H && y < SY + STATUS_H + HEADER_H && x < SX + 18) {
            open = null;
            return true;
        }
        if (scroll > 20 && Math.hypot(x - (SR - 14), y - (bodyBottom - 12)) <= 8) {
            scroll = 0;
            return true;
        }
        if (composer()) {
            for (int[] b : wordBoxes) {
                if (x >= b[0] && x < b[2] && y >= b[1] && y < b[3]) {
                    if (composing.size() < Phone.GHOST_MOST) composing.add(b[4]);
                    return true;
                }
            }
            int inputTop = SB - HOME_H - INPUT_H - 2;
            // The green button sends; the box itself clears what you've picked.
            if (y >= inputTop && y < inputTop + INPUT_H) {
                if (x >= SR - 23) sendWords();
                else composing.clear();
                return true;
            }
        }
        List<String> chips = chips();
        for (int i = 0; i < chips.size(); i++) {
            int[] c = chipBox(chips.get(i), i);
            if (x >= c[0] && x < c[2] && y >= c[1] && y < c[3]) {
                choose(i);
                return true;
            }
        }
        return true;
    }

    /** How many answers there are to give: the test bot picks one. */
    public int replyCount() {
        return chips().size();
    }

    /** Answers with one of the replies on offer. */
    public void choose(int option) {
        if (option < 0 || option >= replies.size()) return;
        if (ghost()) {
            toast = "Nobody can hear you now.";
            toastUntil = System.currentTimeMillis() + TOAST_MS;
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(option);
        ClientPlayNetworking.send(Net.PHONE_REPLY, buf);
        awaitingAfter = messages.size();
        scroll = 0;
    }

    /** A ghost in the class chat: words to post instead of a keyboard. */
    private boolean composer() {
        return ghost() && open != null && open.name.equals(CLASS_CHAT);
    }

    /** Posts the ghost's words, if there are any, and if enough time has passed since the last. */
    private void sendWords() {
        if (composing.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastWords < GHOST_EVERY_MS) {
            toast = "Not yet. Words come slowly now.";
            toastUntil = now + TOAST_MS;
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(composing.size());
        for (int w : composing) buf.writeVarInt(w);
        ClientPlayNetworking.send(Net.GHOST_SAY, buf);
        composing.clear();
        lastWords = now;
        scroll = 0;
        toast = "Sent. They won't see all of it.";
        toastUntil = now + TOAST_MS;
    }

    private int[] chipBox(String label, int i) {
        int w = Math.min(textRenderer.getWidth(label) + 14, SR - SX - 14);
        int y = chipsTop + i * (CHIP_H + CHIP_GAP);
        return new int[]{SR - 7 - w, y, SR - 7, y + CHIP_H};
    }

    // ---- Drawing ----

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x66000000);
        long now = System.currentTimeMillis();
        float g = glitch();
        boolean burst = now < glitchUntil;
        dx = burst ? glitchDx : 0;
        int mx = (int) Math.floor(localX(mouseX)), my = (int) Math.floor(localY(mouseY));

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(scale, scale, 1);
        m.translate(dx, 0, 0);
        drawFrame(ctx);
        int top = drawTop(ctx, now, g, burst);
        if (open == null) drawList(ctx, top, mx, my);
        else drawChat(ctx, top, mx, my, now);
        roundRect(ctx, W / 2 - 20, SB - 5, W / 2 + 20, SB - 3, 1, 0xFF8A8F94);
        if (toast != null && now < toastUntil) {
            int tw = textRenderer.getWidth(toast) + 12, ty = SB - HOME_H - INPUT_H - 22;
            roundRect(ctx, W / 2 - tw / 2, ty, W / 2 + tw / 2, ty + 13, 6, 0xEE000000);
            ctx.drawText(textRenderer, toast, W / 2 - tw / 2 + 6, ty + 3, TEXT, false);
        }
        if (burst) drawTears(ctx, g);
        m.pop();
    }

    private void drawFrame(DrawContext ctx) {
        roundRect(ctx, -1, -1, W + 1, H + 1, 17, FRAME_EDGE);
        roundRect(ctx, 0, 0, W, H, 16, FRAME);
        ctx.fill(-2, 62, 0, 80, FRAME_EDGE);
        ctx.fill(-2, 86, 0, 104, FRAME_EDGE);
        ctx.fill(W, 76, W + 2, 100, FRAME_EDGE);
        roundRect(ctx, SX, SY, SR, SB, 13, WALL);
    }

    /** The status bar, the header and, with no signal, the banner. Returns where the body starts. */
    private int drawTop(DrawContext ctx, long now, float g, boolean burst) {
        int headerTop = SY + STATUS_H, headerBottom = headerTop + HEADER_H;
        roundRect(ctx, SX, SY, SR, headerBottom, 13, BAR, true, false);

        String clock = burst && g > 0.5f ? "--:--" : Schedule.clock(ClientRun.minute);
        drawSmall(ctx, clock, SX + 11, SY + 4, TEXT);
        circle(ctx, W / 2, SY + 6, 2, 0xFF000000);
        int battery = MathHelper.clamp(100 - Math.max(0, ClientRun.minute - 7 * 60) * 85 / (17 * 60), 8, 100);
        int bx = SR - 21, by = SY + 4;
        ctx.fill(bx, by, bx + 10, by + 5, 0xFF8A8F94);
        ctx.fill(bx + 1, by + 1, bx + 9, by + 4, BAR);
        ctx.fill(bx + 1, by + 1, bx + 1 + Math.max(1, battery * 8 / 100), by + 4, battery <= 20 ? HER : TEXT);
        ctx.fill(bx + 10, by + 1, bx + 11, by + 4, 0xFF8A8F94);
        boolean flicker = g > 0.3f && random.nextFloat() < g * 0.2f;
        for (int i = 0; i < 4; i++) {
            int h = 2 + i, x = bx - 14 + i * 3;
            boolean lit = !noSignal() && !flicker;
            ctx.fill(x, by + 5 - h, x + 2, by + 5, lit ? TEXT : 0xFF3B4A54);
        }
        if (noSignal()) drawSmall(ctx, "x", bx - 18, by, HER);

        int cy = headerTop + HEADER_H / 2;
        if (open == null) {
            ctx.drawText(textRenderer, GhostGlyphs.filter("Chats", 1), SX + 9, cy - 4, TEXT, false);
        } else {
            // Back.
            for (int i = 0; i < 4; i++) {
                ctx.fill(SX + 5 + i, cy - i - 1, SX + 7 + i, cy - i, TEXT);
                ctx.fill(SX + 5 + i, cy + i, SX + 7 + i, cy + i + 1, TEXT);
            }
            avatar(ctx, open, SX + 22, cy, 8, true);
            String name = trim(open.shown, SR - 14 - (SX + 33));
            if (burst) {
                ctx.drawText(textRenderer, name, SX + 34, cy - 8, 0x80FF2040, false);
                ctx.drawText(textRenderer, name, SX + 32, cy - 8, 0x8020E0FF, false);
            }
            ctx.drawText(textRenderer, name, SX + 33, cy - 8, TEXT, false);
            boolean typing = typing();
            String sub = burst && g > 0.6f ? "watching" : typing ? "typing" + ELLIPSIS : subtitle(open);
            drawSmall(ctx, GhostGlyphs.filter(sub, sub.hashCode()), SX + 33, cy + 3, typing ? GREEN : META);
        }
        for (int i = 0; i < 3; i++) ctx.fill(SR - 8, cy - 4 + i * 3, SR - 6, cy - 2 + i * 3, TEXT);

        if (!noSignal()) return headerBottom;
        boolean on = now / 650 % 3 != 2;
        ctx.fill(SX, headerBottom, SR, headerBottom + BANNER_H, on ? AMBER : AMBER_DIM);
        String what = now / 2600 % 2 == 0 ? "Searching for signal" : "Connecting";
        String banner = what + ".".repeat((int) (now / 400 % 4));
        drawSmall(ctx, banner, SX + 8, headerBottom + 3, BANNER_TEXT);
        return headerBottom + BANNER_H;
    }

    private String subtitle(Chat c) {
        if (c.name.equals(UNKNOWN)) return "online";
        if (c.name.equals(CLASS_CHAT)) return "29 participants";
        if (c.lastIncoming < 0) return "";
        Msg last = null;
        for (Msg m : c.msgs) if (m.index() == c.lastIncoming) last = m;
        return last == null ? "" : (last.day() == ClientRun.day ? "last seen today at " : "last seen day " + last.day() + " at ") + last.clock();
    }

    private void drawList(DrawContext ctx, int top, int mx, int my) {
        int bottom = SB - HOME_H;
        bodyTop = top;
        bodyBottom = bottom;
        if (chats.isEmpty()) {
            String none = GhostGlyphs.filter("No chats yet.", 2);
            ctx.drawText(textRenderer, none, W / 2 - textRenderer.getWidth(none) / 2, top + 40, META, false);
            return;
        }
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, chats.size() * ROW_H - (bottom - top)));
        scissor(ctx, SX, top, SR, bottom);
        for (int i = 0; i < chats.size(); i++) {
            int y = top + i * ROW_H - (int) listScroll;
            if (y >= bottom) break;
            if (y + ROW_H <= top) continue;
            Chat c = chats.get(i);
            Msg last = c.msgs.get(c.msgs.size() - 1);
            if (mx >= SX && mx < SR && my >= Math.max(y, top) && my < Math.min(y + ROW_H, bottom)) ctx.fill(SX, y, SR, y + ROW_H, HOVER);
            avatar(ctx, c, SX + 17, y + 15, 10, false);
            long fresh = c.msgs.stream().filter(m -> unread.contains(m.index())).count();
            String when = last.day() == ClientRun.day ? last.clock() : last.day() == ClientRun.day - 1 ? "Yesterday" : "Day " + last.day();
            int whenW = smallWidth(when);
            drawSmall(ctx, when, SR - 7 - whenW, y + 7, fresh > 0 ? GREEN : META);
            int x = SX + 32;
            ctx.drawText(textRenderer, trim(c.shown, SR - 11 - whenW - x), x, y + 5, c.name.equals(UNKNOWN) ? 0xFFF0A0A8 : TEXT, false);
            int px = x;
            if (typingIn(c.name)) {
                drawSmall(ctx, GhostGlyphs.filter("typing" + ELLIPSIS, 4), px, y + 18, GREEN);
            } else {
                if (last.mine() && !last.deleted()) {
                    ticks(ctx, px, y + 18, tickState(c, last));
                    px += 11;
                }
                String who = last.mine() || last.sender().isEmpty() ? "" : last.sender() + ": ";
                String what = last.photo() >= 0 ? GhostGlyphs.filter("Photo", 5) + (last.body().isEmpty() ? "" : " " + last.body()) : last.body();
                drawSmall(ctx, trimSmall(who + what, SR - 22 - px), px, y + 18, META);
            }
            if (fresh > 0) {
                circle(ctx, SR - 12, y + 20, 5, GREEN);
                String n = fresh > 9 ? "9+" : String.valueOf(fresh);
                drawSmall(ctx, n, SR - 12 - smallWidth(n) / 2, y + 18, BANNER_TEXT);
            }
            if (i < chats.size() - 1) ctx.fill(x, y + ROW_H - 1, SR, y + ROW_H, DIVIDER);
        }
        ctx.disableScissor();
    }

    private void drawChat(DrawContext ctx, int top, int mx, int my, long now) {
        int inputTop = SB - HOME_H - INPUT_H - 2;
        List<String> chips = chips();
        chipsTop = inputTop - chips.size() * (CHIP_H + CHIP_GAP) - (chips.isEmpty() ? 0 : 1);
        if (composer()) chipsTop = layWords(inputTop);
        int bottom = chipsTop - 2;
        bodyTop = top;
        bodyBottom = bottom;

        for (int yy = top + 5; yy < inputTop; yy += 12) {
            for (int xx = SX + 5 + (yy / 12 % 2) * 6; xx < SR - 2; xx += 12) ctx.fill(xx, yy, xx + 1, yy + 1, DOODLE);
        }

        boolean typing = typing();
        int total = contentH + (typing ? TYPING_H : 0), view = bottom - top;
        int maxScroll = Math.max(0, total - view);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);
        int offset = maxScroll - (int) scroll;
        scissor(ctx, SX, top, SR, bottom);
        for (Row r : rows) {
            int y = top + r.y - offset;
            if (y > bottom || y + r.h < top) continue;
            if (r.kind == Row.BUBBLE) drawBubble(ctx, r, y);
            else drawMarker(ctx, r, y);
        }
        if (typing) {
            int y = top + contentH - offset;
            roundRect(ctx, SX + 7, y, SX + 33, y + 13, 4, IN);
            ctx.fill(SX + 7, y, SX + 11, y + 4, IN);
            for (int k = 0; k < TAIL; k++) ctx.fill(SX + 3 + k, y + k, SX + 7, y + k + 1, IN);
            for (int d = 0; d < 3; d++) {
                int lift = (now / 180 + 3 - d) % 3 == 0 ? 1 : 0;
                circle(ctx, SX + 14 + d * 6, y + 7 - lift, 1, META);
            }
        }
        ctx.disableScissor();

        if (scroll > 20) {
            circle(ctx, SR - 14, bottom - 12, 7, BAR);
            for (int i = 0; i < 3; i++) ctx.fill(SR - 16 + i, bottom - 14 + i, SR - 12 - i, bottom - 13 + i, META);
        }

        for (int i = 0; i < chips.size(); i++) {
            String label = GhostGlyphs.filter(chips.get(i), chips.get(i).hashCode());
            int[] c = chipBox(chips.get(i), i);
            boolean over = mx >= c[0] && mx < c[2] && my >= c[1] && my < c[3];
            roundRect(ctx, c[0], c[1], c[2], c[3], 7, over ? HOVER : BAR);
            ctx.drawText(textRenderer, trim(label, c[2] - c[0] - 10), c[0] + 7, c[1] + 4, ghost() ? META : GREEN, false);
        }

        if (composer()) drawWords(ctx, mx, my);

        roundRect(ctx, SX + 4, inputTop, SR - 24, inputTop + INPUT_H, 9, INPUT_BG);
        boolean words = composer() && !composing.isEmpty();
        String hint = words ? trim(String.join(" ", composing.stream().map(Phone.GHOST_WORDS::get).toList()), SR - 30 - (SX + 12))
                : composer() ? "Pick your words" : ghost() ? GhostGlyphs.scramble("Message", 3) : "Message";
        ctx.drawText(textRenderer, hint, SX + 12, inputTop + 5, words ? TEXT : META, false);
        circle(ctx, SR - 13, inputTop + INPUT_H / 2, 9, GREEN);
        int cx = SR - 13, cy = inputTop + INPUT_H / 2;
        if (words) {
            // Send: an arrow pointing right.
            for (int i = 0; i < 4; i++) {
                ctx.fill(cx - 3 + i, cy - 4 + i, cx - 2 + i, cy + 4 - i, TEXT);
            }
            ctx.fill(cx - 5, cy, cx - 3, cy + 1, TEXT);
            return;
        }
        roundRect(ctx, cx - 2, cy - 5, cx + 2, cy + 1, 1, TEXT);
        ctx.fill(cx - 4, cy, cx - 3, cy + 2, TEXT);
        ctx.fill(cx + 3, cy, cx + 4, cy + 2, TEXT);
        ctx.fill(cx - 3, cy + 2, cx + 3, cy + 3, TEXT);
        ctx.fill(cx, cy + 3, cx + 1, cy + 5, TEXT);
    }

    /** Places every word a ghost can post as a pill, flowing in rows up from the input bar. Returns where they start. */
    private int layWords(int inputTop) {
        wordBoxes.clear();
        List<List<int[]>> lines = new ArrayList<>();
        List<int[]> line = new ArrayList<>();
        int x = SX + 6;
        for (int i = 0; i < Phone.GHOST_WORDS.size(); i++) {
            int w = smallWidth(Phone.GHOST_WORDS.get(i)) + 8;
            if (x + w > SR - 6 && !line.isEmpty()) {
                lines.add(line);
                line = new ArrayList<>();
                x = SX + 6;
            }
            line.add(new int[]{x, 0, x + w, 0, i});
            x += w + WORD_GAP;
        }
        if (!line.isEmpty()) lines.add(line);
        int top = inputTop - 3 - lines.size() * (WORD_H + WORD_GAP);
        for (int r = 0; r < lines.size(); r++) {
            for (int[] b : lines.get(r)) {
                b[1] = top + r * (WORD_H + WORD_GAP);
                b[3] = b[1] + WORD_H;
                wordBoxes.add(b);
            }
        }
        return top - 2;
    }

    private void drawWords(DrawContext ctx, int mx, int my) {
        boolean full = composing.size() >= Phone.GHOST_MOST;
        for (int[] b : wordBoxes) {
            boolean over = !full && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
            roundRect(ctx, b[0], b[1], b[2], b[3], 5, over ? HOVER : BAR);
            drawSmall(ctx, Phone.GHOST_WORDS.get(b[4]), b[0] + 4, b[1] + (WORD_H - metaH()) / 2, full ? META : 0xFFB9C3C9);
        }
    }

    private void drawMarker(DrawContext ctx, Row r, int y) {
        int tw = smallWidth(r.label);
        if (r.kind == Row.UNREAD) {
            ctx.fill(SX, y + 3, SR, y + 13, PILL);
            drawSmall(ctx, r.label, W / 2 - tw / 2, y + 5, META);
            return;
        }
        int pw = tw + 10, px = W / 2 - pw / 2;
        roundRect(ctx, px, y + 3, px + pw, y + 13, 4, PILL);
        drawSmall(ctx, r.label, px + 5, y + 5, META);
    }

    private void drawBubble(DrawContext ctx, Row r, int y) {
        Msg m = r.msg;
        int col = m.mine() ? OUT : IN, x = r.x, x2 = r.x + r.w;
        roundRect(ctx, x, y, x2, y + r.h, 4, col);
        if (r.tail) {
            if (m.mine()) {
                ctx.fill(x2 - 4, y, x2, y + 4, col);
                for (int k = 0; k < TAIL; k++) ctx.fill(x2, y + k, x2 + TAIL - k, y + k + 1, col);
            } else {
                ctx.fill(x, y, x + 4, y + 4, col);
                for (int k = 0; k < TAIL; k++) ctx.fill(x - TAIL + k, y + k, x, y + k + 1, col);
            }
        }
        int ly = y + PAD_Y;
        if (r.named) {
            // Who said it, in the group, in their own colour. A ghost is nobody.
            int nameCol = m.dead() ? 0xFFB0505C : NAMES[Math.floorMod(m.sender().hashCode(), NAMES.length)];
            drawSmall(ctx, m.sender(), x + PAD_X, ly, nameCol);
            ly += metaH() + 3;
        }
        boolean picture = m.photo() >= 0;
        if (picture) {
            drawPhoto(ctx, m.photo(), x + PAD_X, ly);
            ly += PHOTO_H + 3;
        }
        int indent = 0;
        if (m.deleted()) {
            noEntry(ctx, x + PAD_X + 3, ly + 3);
            indent = 9;
        }
        for (OrderedText l : r.lines) {
            ctx.drawText(textRenderer, l, x + PAD_X + indent, ly, m.deleted() ? META : TEXT, false);
            ly += LINE;
        }
        int metaX = x2 - PAD_X - r.metaW, metaY = y + r.h - PAD_Y - metaH();
        if (picture && r.lines.isEmpty()) {
            // On the picture's corner, over a dark patch so it reads.
            metaY -= 2;
            ctx.fill(metaX - 2, metaY - 1, x2 - PAD_X, metaY + metaH() + 1, 0x80000000);
        }
        drawSmall(ctx, m.clock(), metaX, metaY, m.mine() ? OUT_META : META);
        if (m.mine() && !m.deleted()) ticks(ctx, metaX + smallWidth(m.clock()) + 2, metaY + 1, tickState(open, m));
    }

    /** Her photo of you, or a grey square where your game never took one. */
    private void drawPhoto(DrawContext ctx, int id, int x, int y) {
        Identifier pic = PhotoAlbum.get(id);
        if (pic != null) {
            ctx.drawTexture(pic, x, y, 0, 0, PHOTO_W, PHOTO_H, PHOTO_W, PHOTO_H);
            return;
        }
        ctx.fill(x, y, x + PHOTO_W, y + PHOTO_H, 0xFF111A20);
        // A camera, faintly.
        ctx.fill(x + PHOTO_W / 2 - 8, y + PHOTO_H / 2 - 4, x + PHOTO_W / 2 + 8, y + PHOTO_H / 2 + 6, 0xFF2A3942);
        ctx.fill(x + PHOTO_W / 2 - 3, y + PHOTO_H / 2 - 6, x + PHOTO_W / 2 + 3, y + PHOTO_H / 2 - 4, 0xFF2A3942);
        circle(ctx, x + PHOTO_W / 2, y + PHOTO_H / 2 + 1, 3, 0xFF111A20);
    }

    /** A circle with a line through it: what's left of a deleted message. */
    private static void noEntry(DrawContext ctx, int cx, int cy) {
        for (int i = -3; i <= 3; i++) {
            int half = (int) Math.round(Math.sqrt(9.5 - i * i));
            ctx.fill(cx - half, cy + i, cx - half + 1, cy + i + 1, META);
            ctx.fill(cx + half - 1, cy + i, cx + half, cy + i + 1, META);
        }
        ctx.fill(cx - 1, cy - 3, cx + 2, cy - 2, META);
        ctx.fill(cx - 1, cy + 3, cx + 2, cy + 4, META);
        for (int i = -2; i <= 2; i++) ctx.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, META);
    }

    /** 1: sent and gone nowhere. 2: delivered. 3: read. */
    private int tickState(Chat c, Msg m) {
        if (noSignal()) return 1;
        return c.lastIncoming > m.index() ? 3 : 2;
    }

    private static void ticks(DrawContext ctx, int x, int y, int state) {
        int col = state == 3 ? BLUE : META;
        tick(ctx, x, y, col);
        if (state > 1) tick(ctx, x + 3, y, col);
    }

    private static void tick(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x, y + 2, x + 1, y + 3, col);
        ctx.fill(x + 1, y + 3, x + 2, y + 4, col);
        ctx.fill(x + 2, y + 2, x + 3, y + 3, col);
        ctx.fill(x + 3, y + 1, x + 4, y + 2, col);
        ctx.fill(x + 4, y, x + 5, y + 1, col);
    }

    private void avatar(DrawContext ctx, Chat c, int cx, int cy, int r, boolean tiny) {
        int col = c.name.equals(UNKNOWN) ? 0xFF5A1820 : AVATARS[Math.floorMod(c.name.hashCode(), AVATARS.length)];
        circle(ctx, cx, cy, r, col);
        String label = c.name.equals(UNKNOWN) ? "?" : c.name.startsWith("Class ") ? c.name.substring(6).replace("-", "") : c.name.substring(0, 1).toUpperCase();
        if (tiny) {
            drawSmall(ctx, label, cx - smallWidth(label) / 2, cy - metaH() / 2, TEXT);
        } else {
            ctx.drawText(textRenderer, label, cx - textRenderer.getWidth(label) / 2, cy - 3, c.name.equals(UNKNOWN) ? HER : TEXT, false);
        }
    }

    /** Something's close: thin bands torn sideways, coloured fringes, and a little static. */
    private void drawTears(DrawContext ctx, float g) {
        Random r = new Random(glitchSeed);
        int bands = 2 + (int) (g * 3);
        for (int i = 0; i < bands; i++) {
            int y = SY + r.nextInt(SB - SY - 4), h = 1 + r.nextInt(3), shift = r.nextInt(9) - 4;
            int col = switch (r.nextInt(3)) {
                case 0 -> 0x40FF2040;
                case 1 -> 0x4020E0FF;
                default -> 0x60000000;
            };
            ctx.fill(Math.max(SX, SX + shift), y, Math.min(SR, SR + shift), y + h, col);
        }
        for (int i = 0; i < (int) (40 * g); i++) {
            int x = SX + r.nextInt(SR - SX), y = SY + r.nextInt(SB - SY);
            ctx.fill(x, y, x + 1, y + 1, 0x50FFFFFF);
        }
    }

    // ---- Small helpers ----

    private void drawSmall(DrawContext ctx, String s, int x, int y, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(small, small, 1);
        ctx.drawText(textRenderer, s, 0, 0, color, false);
        m.pop();
    }

    private int smallWidth(String s) {
        return (int) Math.ceil(textRenderer.getWidth(s) * small);
    }

    private String trim(String s, int max) {
        if (textRenderer.getWidth(s) <= max) return s;
        return textRenderer.trimToWidth(s, Math.max(0, max - textRenderer.getWidth(ELLIPSIS))) + ELLIPSIS;
    }

    private String trimSmall(String s, int max) {
        return trim(s, (int) (max / small));
    }

    private static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        roundRect(ctx, x1, y1, x2, y2, r, color, true, true);
    }

    /** A rectangle with its top corners, bottom corners or both rounded to radius r. */
    private static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color, boolean top, boolean bottom) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        int inner1 = top ? y1 + r : y1, inner2 = bottom ? y2 - r : y2;
        if (inner2 > inner1) ctx.fill(x1, inner1, x2, inner2, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int in = (int) Math.round(r - Math.sqrt(r * r - d * d));
            if (top) ctx.fill(x1 + in, y1 + i, x2 - in, y1 + i + 1, color);
            if (bottom) ctx.fill(x1 + in, y2 - i - 1, x2 - in, y2 - i, color);
        }
    }

    private static void circle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int i = -r; i < r; i++) {
            double d = i + 0.5;
            int half = (int) Math.round(Math.sqrt(r * r - d * d));
            ctx.fill(cx - half, cy + i, cx + half, cy + i + 1, color);
        }
    }
}
