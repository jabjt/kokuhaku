package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
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

import java.util.List;

/** Your phone: every message, newest at the bottom, and an answer if there's one to give. */
public class PhoneScreen extends Screen {
    private static final int WIDE = 210, MARGIN = 10, PADDING = 5, LINE_HEIGHT = 10, BUTTON_HEIGHT = 15;
    private final NbtList messages;
    private final List<String> replies;
    private double scroll = 0;

    public PhoneScreen(NbtCompound data) {
        super(KokuhakuClient.menuText("Messages"));
        this.messages = data.getList("Messages", NbtElement.COMPOUND_TYPE);
        this.replies = data.getList("Replies", NbtElement.STRING_TYPE).stream().map(NbtElement::asString).toList();
    }

    private int left() {
        return width / 2 - WIDE / 2;
    }

    @Override
    protected void init() {
        int y = height - MARGIN - 6 - replies.size() * BUTTON_HEIGHT;
        for (int i = 0; i < replies.size(); i++) {
            int option = i;
            addDrawableChild(new MenuButton(left() + 6, y + i * BUTTON_HEIGHT, WIDE - 12, 14, KokuhakuClient.menuText("> " + replies.get(i)), b -> {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(option);
                ClientPlayNetworking.send(Net.PHONE_REPLY, buf);
            }));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (com.kokuhaku.client.KeyBindings.PHONE.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, scroll + amount * 14);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        int left = left(), right = left + WIDE, top = MARGIN, bottom = height - MARGIN;
        context.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFF2A2A30);
        context.fill(left, top, right, bottom, 0xFF0C0C10);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 5, 0xFFE8E2E4);
        int listTop = top + 18, listBottom = bottom - 6 - replies.size() * BUTTON_HEIGHT;
        if (messages.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No messages."), width / 2, listTop + 20, 0xFF6C6C74);
        } else {
            drawMessages(context, left, right, listTop, listBottom);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    /** From the bottom up, so the newest is always where you're looking. */
    private void drawMessages(DrawContext context, int left, int right, int top, int bottom) {
        context.enableScissor(left, top, right, bottom);
        int y = bottom + (int) scroll, room = WIDE - 44;
        for (int i = messages.size() - 1; i >= 0 && y > top; i--) {
            NbtCompound m = messages.getCompound(i);
            boolean mine = m.getBoolean("Mine");
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(m.getString("Body")), room);
            int wide = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0) + PADDING * 2;
            int high = lines.size() * LINE_HEIGHT + PADDING * 2 - 2;
            y -= high + 13;
            int x = mine ? right - wide - 6 : left + 6;
            if (!mine) context.drawTextWithShadow(textRenderer, Text.literal(m.getString("From")), x, y, m.getString("From").equals("Unknown number") ? 0xFFE0283C : 0xFF9A9AA2);
            String when = m.getString("When");
            context.drawTextWithShadow(textRenderer, Text.literal(when), right - textRenderer.getWidth(when) - 6, y, 0xFF55505A);
            context.fill(x, y + 10, x + wide, y + 10 + high, mine ? 0xFF8A1A26 : 0xFF26262C);
            int ly = y + 10 + PADDING;
            for (OrderedText l : lines) {
                context.drawTextWithShadow(textRenderer, l, x + PADDING, ly, 0xFFE8E2E4);
                ly += LINE_HEIGHT;
            }
        }
        context.disableScissor();
    }
}
