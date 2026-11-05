package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.DevShots;
import com.kokuhaku.game.Talk;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** The walk in: the camera keeps going while people talk to you in a box at the bottom of the screen. */
public class WalkScreen extends CutsceneScreen {
    private static final float CHARS_PER_SECOND = 45f;
    private static final int BOX_HEIGHT = 96;
    private static final long LINGER_MS = 8000, LOOKS_AT_THEM_MS = 5000;

    private String name = "", line = "";
    private List<String> options = List.of();
    private long shown = 0;
    private int photos = 0;

    /** What someone just said, and any answers you can give. */
    public void show(NbtCompound nbt) {
        name = nbt.getString("Name");
        line = nbt.getString("Line");
        NbtList list = nbt.getList("Options", NbtElement.STRING_TYPE);
        options = list.stream().map(NbtElement::asString).toList();
        shown = System.currentTimeMillis();
        Cutscene.speaker = nbt.getInt("Speaker");
        Cutscene.speakerUntil = shown + LOOKS_AT_THEM_MS;
        clearAndInit();
    }

    @Override
    protected boolean swallowsInput() {
        return false;
    }

    @Override
    protected void init() {
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, options.isEmpty() ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        int x = width / 2 - 170, y = height - 12 - options.size() * 15;
        for (int i = 0; i < options.size(); i++) {
            int option = i;
            addDrawableChild(new MenuButton(x, y + i * 15, 340, 14, KokuhakuClient.menuText("> " + options.get(i)), b -> answer(option)));
        }
    }

    private void answer(int option) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(Talk.ID);
        buf.writeVarInt(option);
        ClientPlayNetworking.send(Net.CHOOSE, buf);
        options = List.of();
        clearAndInit();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) ClientPlayNetworking.send(Net.SKIP_WALK, PacketByteBufs.empty());
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (DevShots.ON && photos < 4 && Cutscene.progress() > 0.12f + photos * 0.24f) {
            photos++;
            DevShots.take();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int black = (int) (Cutscene.blackness() * 255);
        if (black > 0) context.fill(0, 0, width, height, black << 24);
        renderTray(context);
        if (com.kokuhaku.client.ClientRun.tray == null) context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("Space: skip"), width - 72, 8, 0x80FFFFFF);
        if (line.isEmpty() || options.isEmpty() && System.currentTimeMillis() - shown > LINGER_MS) return;
        int left = width / 2 - 180, right = width / 2 + 180, top = height - BOX_HEIGHT;
        context.fill(left, top, right, height - 8, 0xB0080608);
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(name), left + 10, top + 7, 0xFFE0283C);
        int count = (int) Math.min(line.length(), (System.currentTimeMillis() - shown) / 1000f * CHARS_PER_SECOND);
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(line.substring(0, count)), right - left - 20);
        int y = top + 20;
        for (OrderedText l : lines) {
            context.drawTextWithShadow(textRenderer, l, left + 10, y, 0xFFE8E2E4);
            y += 10;
        }
        for (var child : children()) if (child instanceof MenuButton button) button.render(context, mouseX, mouseY, delta);
    }
}
