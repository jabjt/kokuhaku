package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.List;

/** Writing something for the others to find in the bag. */
public class NoteScreen extends Screen {
    private static final int WIDE = 300, LONGEST = 80;

    private final Screen parent;
    /** Whose name goes on it. Everyone but the accomplice only has one to pick from. */
    private final List<String> names;
    private int signed = 0;
    private TextFieldWidget field;

    public NoteScreen(Screen parent, List<String> names) {
        super(KokuhakuClient.menuText("Leave a note"));
        this.parent = parent;
        this.names = names;
    }

    @Override
    protected void init() {
        int x = (width - WIDE) / 2, y = height / 2 - 30;
        field = new TextFieldWidget(textRenderer, x, y, WIDE, 18, Text.empty());
        field.setMaxLength(LONGEST);
        addDrawableChild(field);
        setInitialFocus(field);
        if (names.size() > 1) addDrawableChild(new MenuButton(x, y + 24, WIDE, 14, signLabel(), b -> {
            signed = (signed + 1) % names.size();
            clearAndInit();
        }));
        addDrawableChild(new MenuButton(x, y + 44, 90, 14, KokuhakuClient.menuText("Leave it"), b -> send()));
        addDrawableChild(new MenuButton(x + 100, y + 44, 90, 14, KokuhakuClient.menuText("Back"), b -> client.setScreen(parent)));
    }

    private Text signLabel() {
        return KokuhakuClient.menuText("Sign it: " + names.get(signed));
    }

    private void send() {
        if (field.getText().isBlank()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(field.getText());
        buf.writeString(names.get(signed));
        ClientPlayNetworking.send(Net.NOTE, buf);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0000000);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 52, 0xFFE8E2E4);
        super.render(context, mouseX, mouseY, delta);
    }
}
