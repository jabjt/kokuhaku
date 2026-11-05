package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.Party;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Make an invite code for your friends, or type in one of theirs. */
public class PartyScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget field;
    private String typed = "";
    private String shownCode = "";
    private boolean shownInviting = false;

    public PartyScreen(Screen parent) {
        super(KokuhakuClient.menuText("Party"));
        this.parent = parent;
    }

    private int left() {
        return width / 2 - 130;
    }

    @Override
    protected void init() {
        shownCode = Party.code;
        shownInviting = Party.inviting;
        int x = left(), y = height / 2 - 66;
        if (Party.code.isEmpty()) {
            addDrawableChild(new MenuButton(x, y + 14, 200, 16, KokuhakuClient.menuText(Party.inviting ? "..." : "Make an invite code"), b -> {
                Party.invite(client);
                clearAndInit();
            }));
        } else {
            addDrawableChild(new MenuButton(x, y + 36, 200, 16, KokuhakuClient.menuText("Copy it"), b -> client.keyboard.setClipboard(Party.code)));
        }

        field = new TextFieldWidget(textRenderer, x, y + 92, 260, 12, Text.empty());
        field.setDrawsBackground(false);
        field.setMaxLength(128);
        field.setText(typed);
        field.setChangedListener(text -> typed = text);
        addDrawableChild(field);
        addDrawableChild(new MenuButton(x, y + 110, 200, 16, KokuhakuClient.menuText("Join them"), b -> Party.join(client, typed)));
        addDrawableChild(new MenuButton(x, y + 144, 200, 16, KokuhakuClient.menuText("Back"), b -> close()));
    }

    @Override
    public void tick() {
        field.tick();
        if (!Party.code.equals(shownCode) || Party.inviting != shownInviting) clearAndInit();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC8000000);
        int x = left(), y = height / 2 - 66;
        context.getMatrices().push();
        context.getMatrices().translate(x, y - 30, 0);
        context.getMatrices().scale(2f, 2f, 1f);
        context.drawTextWithShadow(textRenderer, title, 0, 0, 0xFFE8E2E4);
        context.getMatrices().pop();

        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("Invite your friends"), x, y, 0xFF8C7C80);
        if (!Party.code.isEmpty()) context.drawTextWithShadow(textRenderer, Text.literal(Party.code), x, y + 20, 0xFFE0283C);
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("Or join theirs"), x, y + 74, 0xFF8C7C80);
        context.fill(x, y + 104, x + 260, y + 105, field.isFocused() ? 0xFFE0283C : 0x60FFFFFF);
        if (!Party.error.isEmpty()) context.drawTextWithShadow(textRenderer, Text.literal(Party.error), x, y + 130, 0xFFD01C30);
        super.render(context, mouseX, mouseY, delta);
    }
}
