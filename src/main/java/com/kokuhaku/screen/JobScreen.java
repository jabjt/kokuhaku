package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.game.Job;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** A job board: work a shift, or leave it. */
public class JobScreen extends Screen {
    private final Job job;
    private final int yen;

    public JobScreen(Job job, int yen) {
        super(KokuhakuClient.menuText(job.title));
        this.job = job;
        this.yen = yen;
    }

    @Override
    protected void init() {
        int x = width / 2 - 120, y = height / 2 + 8;
        addDrawableChild(new MenuButton(x, y, 240, 16,
                KokuhakuClient.menuText("Work a " + job.hours + " hour shift   +¥" + job.pay()), b -> {
            client.setScreen(new TillScreen(job));
        }));
        addDrawableChild(new MenuButton(x, y + 20, 240, 16, KokuhakuClient.menuText("Not today"), b -> close()));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        int x = width / 2 - 120, y = height / 2 - 40;
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFE8E2E4);
        context.drawTextWrapped(textRenderer, Text.literal(job.description + " Mistakes come off your pay. Too many and you're sent home. Coworkers love to gossip."), x, y + 14, 240, 0xFF9A9AA2);
        context.drawTextWithShadow(textRenderer, Text.literal("You have ¥" + yen), x, y + 36, 0xFFD8B860);
        super.render(context, mouseX, mouseY, delta);
    }
}
