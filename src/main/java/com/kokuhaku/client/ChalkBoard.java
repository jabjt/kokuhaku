package com.kokuhaku.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Whatever the teacher's written up, in chalk, on the real board at the front of the room. */
public final class ChalkBoard {
    private static final float SIZE = 0.028f;
    private static final int LINE_WIDTH = 190, LINE_HEIGHT = 11, CHALK = 0xFFEDE8DC, FULL_BRIGHT = 0xF000F0;

    private static volatile String text = "";
    private static volatile Vec3d at = Vec3d.ZERO;

    private ChalkBoard() {
    }

    public static void write(Vec3d where, String words) {
        at = where;
        text = words;
    }

    public static void wipe() {
        text = "";
    }

    public static void render(WorldRenderContext context) {
        String words = text;
        VertexConsumerProvider consumers = context.consumers();
        if (words.isEmpty() || consumers == null) return;
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        List<OrderedText> lines = font.wrapLines(Text.literal(words), LINE_WIDTH);
        matrices.push();
        matrices.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        matrices.scale(SIZE, -SIZE, SIZE);
        int y = -lines.size() * LINE_HEIGHT / 2;
        for (OrderedText line : lines) {
            font.draw(line, -font.getWidth(line) / 2f, y, CHALK, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, FULL_BRIGHT);
            y += LINE_HEIGHT;
        }
        matrices.pop();
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
    }
}
