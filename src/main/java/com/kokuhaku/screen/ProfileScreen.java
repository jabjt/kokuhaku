package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.Party;
import com.kokuhaku.client.Profile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** Someone's record: wins in green, losses in red, the ratio in yellow, their badges underneath, and them on the right. */
public class ProfileScreen extends Screen {
    private static final int GREEN = 0xFF55D66B, RED = 0xFFE0283C, YELLOW = 0xFFF4C430, COLUMN = 80, BADGE_W = 176, BADGE_H = 27, PER_ROW = 2;
    private static final int WIDE = 510, FIGURE_SIZE = 56, FIGURE_FROM_EDGE = 44, GLOW = 40;

    private final Screen parent;
    private final Party.Member member;

    public ProfileScreen(Screen parent, Party.Member member) {
        super(KokuhakuClient.menuText("Profile"));
        this.parent = parent;
        this.member = member;
    }

    @Override
    protected void init() {
        addDrawableChild(new MenuButton(width / 2 - 20, height - 26, 60, 18, KokuhakuClient.menuText("Back"), b -> close()));
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void stat(DrawContext context, int x, int y, String label, String value, int colour) {
        var m = context.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(2.5f, 2.5f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(value), 0, 0, colour);
        m.pop();
        context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(label), x, y + 26, colour);
    }

    private static String ratio(int wins, int losses) {
        return losses == 0 ? wins + ".00" : String.format("%.2f", wins / (double) losses);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0000000);
        int left = Math.max(16, width / 2 - WIDE / 2), right = Math.min(width - 16, width / 2 + WIDE / 2);
        int y = 20;
        context.drawTextWithShadow(textRenderer, Text.literal(member.name()), left, y, 0xFFE8E2E4);
        y += 22;
        stat(context, left + COLUMN / 2, y, "Wins", Integer.toString(member.wins()), GREEN);
        stat(context, left + COLUMN * 3 / 2, y, "Losses", Integer.toString(member.losses()), RED);
        stat(context, left + COLUMN * 5 / 2, y, "W/L", ratio(member.wins(), member.losses()), YELLOW);

        y += 50;
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("Badges"), left, y, 0xFFD8D0D4);
        y += 14;
        Profile.Badge[] badges = Profile.Badge.values();
        for (int i = 0; i < badges.length; i++) {
            Profile.Badge b = badges[i];
            boolean earned = b.in(member.badges());
            int x = left + i % PER_ROW * BADGE_W, top = y + i / PER_ROW * BADGE_H;
            if (!earned) RenderSystem.setShaderColor(0.3f, 0.3f, 0.3f, 1f);
            Icons.draw(context, b.icon, x, top + 2, 1);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            context.drawTextWithShadow(textRenderer, Text.literal(b.label), x + 20, top + 1, earned ? 0xFFF2D6DA : 0xFF5A5A62);
            context.drawTextWithShadow(textRenderer, Text.literal(b.how), x + 20, top + 12, earned ? 0xFF9A9AA2 : 0xFF4A4A52);
        }
        // Them, stood there, turning to look at the mouse.
        PlayerEntity who = client.world == null ? null : client.world.getPlayerByUuid(member.id());
        if (who != null) {
            int x = right - FIGURE_FROM_EDGE, feet = height - 34;
            // Enough light to stand in: dark trousers would vanish against black.
            context.fillGradient(x - GLOW, feet - FIGURE_SIZE * 2, x + GLOW, feet + 8, 0x00000000, 0x12FFFFFF);
            // Everyone's invisible in the menu room, so only for this picture.
            boolean hidden = who.isInvisible();
            who.setInvisible(false);
            // Your own skin here, not the character you play.
            com.kokuhaku.client.StudentSkins.ownSkin = true;
            InventoryScreen.drawEntity(context, x, feet, FIGURE_SIZE, x - mouseX, feet - FIGURE_SIZE * 1.6f - mouseY, who);
            com.kokuhaku.client.StudentSkins.ownSkin = false;
            who.setInvisible(hidden);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
