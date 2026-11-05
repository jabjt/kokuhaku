package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.game.Drink;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/** A vending machine: pick a drink. */
public class VendingScreen extends Screen {
    private final int yen;

    public VendingScreen(int yen) {
        super(KokuhakuClient.menuText("Vending Machine"));
        this.yen = yen;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110, y = height / 2 - 30;
        for (Drink d : Drink.values()) {
            MenuButton b = new MenuButton(x, y, 220, 16, KokuhakuClient.menuText(d.label + "   ¥" + d.price), button -> {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(d.ordinal());
                ClientPlayNetworking.send(Net.BUY, buf);
                close();
            });
            b.active = yen >= d.price;
            addDrawableChild(b);
            y += 24;
        }
        addDrawableChild(new MenuButton(x, y + 6, 220, 16, KokuhakuClient.menuText("Walk away"), b -> close()));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        int x = width / 2 - 110, y = height / 2 - 56;
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFE8E2E4);
        context.drawTextWithShadow(textRenderer, Text.literal("You have ¥" + yen), x, y + 12, 0xFFD8B860);
        super.render(context, mouseX, mouseY, delta);
        int row = height / 2 - 30;
        for (Drink d : Drink.values()) {
            if (mouseX >= x && mouseX < x + 220 && mouseY >= row && mouseY < row + 16) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(d.description), width / 2, height / 2 + 62, 0xFF9A9AA2);
            }
            row += 24;
        }
    }
}
