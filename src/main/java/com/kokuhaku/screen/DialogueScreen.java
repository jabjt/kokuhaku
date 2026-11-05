package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.DevShots;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Talk;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Conversation box at the bottom of the screen. The line types itself out; click to skip. */
public class DialogueScreen extends Screen {
    /** Someone sitting down has their eyes this far below where the game thinks they are. */
    private static final double SEATED_EYES = -0.72;
    private static final float CHARS_PER_SECOND = 45f;
    private static final int WIDE_ENOUGH = 424, TEXT_COLUMN = 170;

    private final int student;
    private final int speaker;
    private final String name;
    private final String line;
    private final List<String> options;
    private final long opened = System.currentTimeMillis();
    private boolean skipped = false;
    private boolean photographed = false;
    private final boolean driven;

    public DialogueScreen(NbtCompound nbt) {
        super(Text.literal(nbt.getString("Name")));
        this.student = nbt.getInt("Student");
        if (nbt.getString("Name").equals(com.kokuhaku.game.Pages.TRUE_NAME)) com.kokuhaku.client.Profile.readTheName();
        this.speaker = nbt.contains("Speaker") ? nbt.getInt("Speaker") : -1;
        this.name = nbt.getString("Name");
        this.line = nbt.getString("Line");
        NbtList list = nbt.getList("Options", NbtElement.STRING_TYPE);
        this.options = list.stream().map(NbtElement::asString).toList();
        this.driven = student == Talk.ID;
    }

    private int rowHeight() {
        return options.size() > 6 ? 12 : 15;
    }

    /** Side by side if there's room: what she says on the left, what you can say on the right. */
    private boolean sideBySide() {
        return !options.isEmpty() && width >= WIDE_ENOUGH;
    }

    private int boxLeft() {
        return width / 2 - (sideBySide() ? WIDE_ENOUGH / 2 - 8 : 180);
    }

    private int boxRight() {
        return width / 2 + (sideBySide() ? WIDE_ENOUGH / 2 - 8 : 180);
    }

    private int textWidth() {
        return sideBySide() ? TEXT_COLUMN : boxRight() - boxLeft() - 20;
    }

    /** Only as tall as it has to be, right at the bottom, so you can see who you're talking to. */
    private int boxTop() {
        int words = 22 + textRenderer.wrapLines(Text.literal(line), textWidth()).size() * 10 + 6;
        int answers = options.size() * rowHeight();
        return height - 12 - (sideBySide() ? Math.max(words, answers + 12) : words + answers + 4);
    }

    /** Your eyes go to their face. */
    private void lookAtThem() {
        if (client.player == null || Cutscene.active()) return;
        Entity them = speaker >= 0 ? client.world.getEntityById(speaker) : client.targetedEntity;
        if (them instanceof StudentEntity student) client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, them.getEyePos().add(0, student.isSittingDown() ? SEATED_EYES : 0, 0));
    }

    @Override
    protected void init() {
        lookAtThem();
        int row = rowHeight();
        int x = sideBySide() ? boxLeft() + TEXT_COLUMN + 16 : width / 2 - 170;
        int wide = sideBySide() ? boxRight() - x - 4 : 260;
        int y = sideBySide() ? boxTop() + 6 : height - 16 - options.size() * row;
        for (int i = 0; i < options.size(); i++) {
            int option = i;
            boolean goodbye = i == options.size() - 1;
            addDrawableChild(new MenuButton(x, y + i * row, wide, row - 1, KokuhakuClient.menuText("> " + options.get(i)),
                    b -> {
                        if (driven) {
                            client.setScreen(null);
                            choose(option);
                        } else if (goodbye) {
                            close();
                        } else {
                            choose(option);
                        }
                    }));
        }
    }

    private void choose(int option) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(student);
        buf.writeVarInt(option);
        ClientPlayNetworking.send(Net.CHOOSE, buf);
    }

    @Override
    public boolean shouldPause() {
        return false; // the clock keeps running while you talk
    }

    @Override
    public void tick() {
        if (DevShots.ON && !photographed && System.currentTimeMillis() - opened > 2_500) {
            photographed = true;
            DevShots.take();
        }
    }

    private int revealed() {
        if (skipped) return line.length();
        return (int) Math.min(line.length(), (System.currentTimeMillis() - opened) / 1000f * CHARS_PER_SECOND);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealed() < line.length()) {
            skipped = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int black = Cutscene.active() ? (int) (Cutscene.blackness() * 255) : 0;
        if (black > 0) context.fill(0, 0, width, height, black << 24);
        int left = boxLeft(), right = boxRight();
        int top = boxTop();
        context.fill(left, top, right, height - 12, 0xB0080608);

        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(name), left + 10, top + 8, 0xFFE0283C);
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(line.substring(0, revealed())), textWidth());
        int y = top + 22;
        for (OrderedText l : lines) {
            context.drawTextWithShadow(textRenderer, l, left + 10, y, 0xFFE8E2E4);
            y += 10;
        }
        if (revealed() >= line.length()) {
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
