package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ArmView;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.StudentSkins;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Invisible screen held open during a cutscene: it swallows every key and
 * click (Esc included) and draws the fades and any news on the black.
 */
public class CutsceneScreen extends Screen {
    private static final float ARM_LENGTH = 0.95f, ARM_ANGLE = -14f, HER_ANGLE = 62f;
    private final Random random = new Random();

    public CutsceneScreen() {
        super(Text.empty());
    }

    /** Your own arm, up in front of you and shaking; on the worse days, her hand closed round it. */
    private void renderArm(DrawContext context) {
        float raised = Cutscene.armRaised();
        if (raised <= 0f || client.player == null) return;
        int strike = Cutscene.strikes;
        int shake = 2 + strike * 2;
        float dx = random.nextInt(shake * 2 + 1) - shake, dy = random.nextInt(shake * 2 + 1) - shake;
        float size = height * ARM_LENGTH;
        float tipX = width * 0.56f + dx, tipY = height * 0.34f + (1f - raised) * height * 0.8f + dy;
        ArmView.draw(context, client.player.getSkinTexture(), ArmView.thin(client.player), tipX, tipY, ARM_ANGLE, size, 1f);
        if (strike >= 2) {
            // Her arm comes in from the side and takes you by the wrist. Hers isn't shaking.
            float wristX = width * 0.56f + size * 0.05f, wristY = tipY + size * 0.3f;
            ArmView.draw(context, StudentSkins.texture(Cutscene.herSkin), true, wristX - dx, wristY - dy, HER_ANGLE, size, 1f);
        }
    }

    /** Lunch, held out in front of you and rocking a little with each step. */
    protected void renderTray(DrawContext context) {
        var tray = com.kokuhaku.client.ClientRun.tray;
        if (tray == null || Cutscene.type() != Cutscene.Type.WALK) return;
        int half = width / 6, top = height - height / 5 + (int) (Math.sin(System.currentTimeMillis() / 170.0) * 3);
        context.fill(width / 2 - half, top, width / 2 + half, height, 0xFF8F8B82);
        context.fill(width / 2 - half + 6, top + 5, width / 2 + half - 6, height, 0xFFB9B4A8);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f - 24f, top - 14f, 0f);
        context.getMatrices().scale(3f, 3f, 1f);
        context.drawItem(tray, 0, 0);
        context.getMatrices().pop();
    }

    /** Your eyes won't stay open: the dark comes in soft from the top and the bottom. */
    private void renderLids(DrawContext context, float open) {
        int lid = (int) (height * 0.5f * (1f - open));
        int soft = (int) (height * 0.2f);
        context.fill(0, 0, width, lid, 0xFF000000);
        context.fill(0, height - lid, width, height, 0xFF000000);
        context.fillGradient(0, lid, width, lid + soft, 0xFF000000, 0);
        context.fillGradient(0, height - lid - soft, width, height - lid, 0, 0xFF000000);
    }

    protected boolean swallowsInput() {
        return true;
    }

    @Override
    protected void init() {
        // Not Mouse.lockCursor(): that closes the current screen.
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return swallowsInput() || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        if (!Cutscene.active()) close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int hurt = (int) (Cutscene.hurt() * 255);
        if (hurt > 0) context.fill(0, 0, width, height, hurt << 24 | 0x500008);
        int white = (int) (Cutscene.flash() * 255);
        if (white > 0) context.fill(0, 0, width, height, white << 24 | 0xFFFFFF);
        int black = (int) (Cutscene.blackness() * 255);
        if (black > 0) context.fill(0, 0, width, height, black << 24);
        renderArm(context);
        renderTray(context);
        if (Cutscene.type() == Cutscene.Type.DRAG) renderLids(context, Cutscene.dragEyes());
        String[] lines = Cutscene.text().split("\n");
        int y = Cutscene.type() == Cutscene.Type.JEALOUS ? height - 40 : height / 2 - lines.length * 7;
        for (String line : lines) {
            if (!line.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, KokuhakuClient.menuText(line), width / 2, y, 0xFFD01C30);
            y += 14;
        }
    }
}
