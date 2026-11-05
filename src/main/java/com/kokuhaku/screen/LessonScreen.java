package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ArmView;
import com.kokuhaku.client.ChalkBoard;
import com.kokuhaku.client.StudentSkins;
import com.kokuhaku.game.Lessons;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A lesson. You're at your desk, the teacher's at the board with the question
 * chalked up behind them, and your answers run the whole width of the screen,
 * the same as anything else you'd say to someone. For some, the teacher wants
 * hands up: get yours up and get it right, and there's a little something from
 * the class merit tin. There's a note from a classmate, too, that only she cares
 * about. On the harder difficulties the last two glitch.
 * When it's done, right or wrong, time skips to the break.
 */
public class LessonScreen extends Screen {
    private static final long[] TIME = {30_000, 25_000, 18_000, 12_000};
    private static final long HANDS_MS = 5_000, TOLD_MS = 2_200;
    private static final int CALLED_ON_PERCENT = 75, ROW = 15, MARGIN = 10, ROWS = 4;
    private static final String NOISE = "#%&@$?!/\\|*^~";

    private enum Hand { NOT_ASKED, WAITING, UP, TOLD }

    private final String subject, teacher, intro;
    private final NbtList questions;
    private final int level;
    private final Vec3d board;
    /** The last two questions only come apart on the harder difficulties. */
    private final boolean glitches;
    private final Random random = new Random();
    private final List<int[]> fondness = new ArrayList<>();
    private Hand hand = Hand.NOT_ASKED;
    private String told = "";
    private int index = 0, wrong = 0, prizes = 0;
    private long questionStart = System.currentTimeMillis(), handUpAt = 0;
    private boolean sent = false;

    public LessonScreen(NbtCompound nbt) {
        super(KokuhakuClient.menuText(nbt.getString("Subject")));
        this.subject = nbt.getString("Subject");
        this.teacher = nbt.getString("Teacher");
        this.intro = nbt.getString("Intro");
        this.questions = nbt.getList("Questions", NbtElement.COMPOUND_TYPE);
        this.glitches = nbt.getBoolean("Glitches");
        this.level = nbt.getInt("Level");
        this.board = new Vec3d(nbt.getDouble("BoardX"), nbt.getDouble("BoardY"), nbt.getDouble("BoardZ"));
        begin();
    }

    private void begin() {
        questionStart = System.currentTimeMillis();
        hand = index < questions.size() && question().getInt("Kind") == Lessons.HANDS ? Hand.WAITING : Hand.NOT_ASKED;
        handUpAt = 0;
        told = "";
        // Up on the board, unless it's a note passed under the desk.
        if (index < questions.size() && question().getInt("Kind") != Lessons.NOTE) ChalkBoard.write(board, question().getString("Text"));
    }

    private boolean glitched() {
        return glitches && index >= 4;
    }

    private long limit() {
        return hand == Hand.WAITING ? HANDS_MS : hand == Hand.TOLD ? TOLD_MS : TIME[Math.min(level, TIME.length - 1)];
    }

    private NbtCompound question() {
        return questions.getCompound(index);
    }

    /** An answer: a line right across the screen, lit up when you're on it. */
    private final class Answer extends MenuButton {
        Answer(int y, String label, PressAction action) {
            super(MARGIN, y, LessonScreen.this.width - MARGIN * 2, ROW - 1, KokuhakuClient.menuText("> " + label), action);
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            if (isSelected()) context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x60E0283C);
            if (glitched()) setX(MARGIN + random.nextInt(7) - 3);
            context.drawTextWithShadow(textRenderer, getMessage(), getX() + 8, getY() + 3, isSelected() ? 0xFFFFFFFF : 0xFFD8D0D4);
        }
    }

    private int rowsTop() {
        return height - MARGIN - ROWS * ROW;
    }

    private void row(int at, String label, net.minecraft.client.gui.widget.ButtonWidget.PressAction action) {
        addDrawableChild(new Answer(rowsTop() + at * ROW, label, action));
    }

    @Override
    public void removed() {
        client.options.hudHidden = false;
        ChalkBoard.wipe();
    }

    @Override
    protected void init() {
        client.options.hudHidden = true;
        if (index >= questions.size() || hand == Hand.TOLD) return;
        NbtCompound q = question();
        if (hand == Hand.WAITING) {
            row(0, "Put your hand up (H)", b -> raise());
            return;
        }
        if (q.getInt("Kind") == Lessons.NOTE) {
            row(0, "Yes", b -> note(1));
            row(1, "No", b -> note(-1));
            return;
        }
        List<String> answers = q.getList("Answers", NbtElement.STRING_TYPE).stream().map(NbtElement::asString).toList();
        int correct = q.getInt("Correct");
        for (int i = 0; i < answers.size(); i++) {
            int choice = i;
            row(i, answers.get(i), b -> answer(choice == correct));
        }
    }

    /** Your hand's up. Whether the teacher picks you is another matter. */
    private void raise() {
        if (hand != Hand.WAITING) return;
        handUpAt = System.currentTimeMillis();
        if (random.nextInt(100) < CALLED_ON_PERCENT) {
            hand = Hand.UP;
            questionStart = System.currentTimeMillis();
            clearAndInit();
        } else {
            tell(question().getString("Other") + " gets picked instead. She gets it right.");
        }
    }

    private void tell(String what) {
        hand = Hand.TOLD;
        told = what;
        questionStart = System.currentTimeMillis();
        clearAndInit();
    }

    /** Her question, your answer. Nothing to do with your grades. */
    private void note(int yes) {
        NbtCompound q = question();
        fondness.add(new int[]{q.getInt("Girl"), yes * q.getInt("YesDelta")});
        next();
    }

    private void answer(boolean right) {
        if (hand == Hand.UP) {
            // Nobody's fined for trying.
            if (right) prizes++;
            tell(right ? "\"Correct. Well done.\" There's something in it for you." : "\"No. Good try. Sit down.\"");
            return;
        }
        if (!right) wrong++;
        next();
    }

    private void next() {
        index++;
        begin();
        if (index >= questions.size()) {
            finish();
        } else {
            clearAndInit();
        }
    }

    private void finish() {
        if (sent) return;
        sent = true;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(wrong);
        buf.writeVarInt(prizes);
        buf.writeVarInt(fondness.size());
        for (int[] change : fondness) {
            buf.writeVarInt(change[0]);
            buf.writeVarInt(change[1]);
        }
        ClientPlayNetworking.send(Net.LESSON_DONE, buf);
        close();
    }

    @Override
    public void tick() {
        if (sent || index >= questions.size() || System.currentTimeMillis() - questionStart <= limit()) return;
        // Ignoring a note isn't a wrong answer: she just never hears back. Nor is keeping your hand down.
        if (hand == Hand.WAITING) tell("Nobody? " + question().getString("Other") + " sighs, and tells everyone the answer.");
        else if (hand == Hand.TOLD || question().getInt("Kind") == Lessons.NOTE) next();
        else if (hand == Hand.UP) tell("\"Too slow. Sit down.\"");
        else answer(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_H) {
            raise();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private String scramble(String s) {
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < sb.length(); i++) {
            if (random.nextInt(9) == 0 && sb.charAt(i) != ' ') sb.setCharAt(i, NOISE.charAt(random.nextInt(NOISE.length())));
        }
        return sb.toString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (index >= questions.size()) return;
        NbtCompound q = question();
        boolean g = glitched(), note = q.getInt("Kind") == Lessons.NOTE;
        // Your own arm, going up.
        if (handUpAt > 0) {
            float up = Math.min(1f, (System.currentTimeMillis() - handUpAt) / 300f);
            ArmView.draw(context, StudentSkins.texture(0), false, width * 0.8f, height * (1.1f - up * 0.62f), -6f, height * 0.95f, 1f);
        }
        int ox = g ? random.nextInt(5) - 2 : 0, oy = g ? random.nextInt(5) - 2 : 0;
        String text = q.getString("Text");
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(g ? scramble(text) : text), width - MARGIN * 2 - 16);
        int top = rowsTop() - 24 - lines.size() * 10;
        context.fill(MARGIN, top, width - MARGIN, height - MARGIN + 2, 0xC0080608);
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(teacher.isEmpty() ? subject : teacher), MARGIN + 8, top + 6, 0xFFE0283C);
        Text corner = Text.literal(note ? "A note" : hand == Hand.WAITING ? "Hands up" : "Question " + (index + 1) + " of " + questions.size());
        context.drawTextWithShadow(textRenderer, corner, width - MARGIN - 8 - textRenderer.getWidth(corner), top + 6, 0xFF9A9AA2);
        int y = top + 19 + oy;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, MARGIN + 8 + ox, y, g ? 0xFFE04050 : 0xFFE8E2E4);
            y += 10;
        }
        if (hand == Hand.TOLD) context.drawTextWithShadow(textRenderer, Text.literal(told), MARGIN + 8, rowsTop() + 2, 0xFFF4E070);
        else if (index == 0 && !intro.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(intro), width / 2, top - 14, 0xFFD8D0D4);
        // Time left, as a line along the top of the box.
        float frac = 1f - Math.min(1f, (System.currentTimeMillis() - questionStart) / (float) limit());
        context.fill(MARGIN, top, MARGIN + (int) ((width - MARGIN * 2) * frac), top + 2, frac < 0.25f ? 0xFFC0182E : 0xFFE8E2E4);
        super.render(context, mouseX, mouseY, delta);
        if (g) {
            for (int i = 0; i < 9; i++) {
                int line = random.nextInt(Math.max(1, height));
                context.fill(0, line, width, line + 1 + random.nextInt(3), random.nextBoolean() ? 0x60C0182E : 0x70000000);
            }
        }
    }
}
