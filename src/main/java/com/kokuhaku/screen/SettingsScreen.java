package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.GameSettings;
import com.kokuhaku.client.Voice;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Under How to Play: Display, Audio and Shader, in the same plain text as the rest of the menu. */
public class SettingsScreen extends Screen {
    private enum Tab { DISPLAY("Display"), AUDIO("Audio"), SHADER("Shader");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int WIDE = 300, ROW = 22, TOP = 62, LIST_ROW = 13;

    private final Screen parent;
    private Tab tab = Tab.DISPLAY;
    /** Which dropdown's open: 1 speakers, 2 microphone, 0 neither. */
    private int open = 0;
    private String note = "";

    public SettingsScreen(Screen parent) {
        super(KokuhakuClient.menuText("Settings"));
        this.parent = parent;
    }

    /** A bar you can click or drag along. */
    private final class Bar extends MenuButton {
        private final String label;
        private final Supplier<Double> get;
        private final Consumer<Double> set;

        Bar(int x, int y, String label, Supplier<Double> get, Consumer<Double> set) {
            super(x, y, WIDE, 18, Text.empty(), b -> {
            });
            this.label = label;
            this.get = get;
            this.set = set;
        }

        private void at(double mouseX) {
            set.accept(MathHelper.clamp((mouseX - getX() - WIDE / 2.0) / (WIDE / 2.0), 0, 1));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            at(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
            at(mouseX);
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(label), getX(), getY() + 5, 0xFFD8D0D4);
            int left = getX() + WIDE / 2, right = getX() + WIDE - 34;
            context.fill(left, getY() + 8, right, getY() + 10, 0xFF4A4A52);
            context.fill(left, getY() + 8, left + (int) ((right - left) * get.get()), getY() + 10, 0xFFE0283C);
            context.drawTextWithShadow(textRenderer, Text.literal((int) Math.round(get.get() * 100) + "%"), right + 6, getY() + 5, 0xFF9A9AA2);
        }
    }

    private int left() {
        return width / 2 - WIDE / 2;
    }

    private void row(int line, String label, Runnable click) {
        addDrawableChild(new MenuButton(left(), TOP + line * ROW, WIDE, 18, KokuhakuClient.menuText(label), b -> {
            click.run();
            clearAndInit();
        }));
    }

    private void bar(int line, String label, SimpleOption<Double> option) {
        addDrawableChild(new Bar(left(), TOP + line * ROW, label, option::getValue, v -> {
            option.setValue(v);
            client.options.write();
        }));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        for (Tab t : Tab.values()) {
            addDrawableChild(new MenuButton(x + t.ordinal() * 70, 36, 60, 16, KokuhakuClient.menuText(t == tab ? "> " + t.label : t.label), b -> {
                tab = t;
                open = 0;
                note = "";
                clearAndInit();
            }));
        }
        addDrawableChild(new MenuButton(width / 2 - 20, height - 24, 60, 18, KokuhakuClient.menuText("Back"), b -> close()));
        switch (tab) {
            case DISPLAY -> display();
            case AUDIO -> audio();
            case SHADER -> shader();
        }
    }

    private void display() {
        row(0, "Window: " + GameSettings.windowMode.label, () -> {
            GameSettings.WindowMode[] all = GameSettings.WindowMode.values();
            GameSettings.windowMode = all[(GameSettings.windowMode.ordinal() + 1) % all.length];
            GameSettings.applyWindow(client);
            GameSettings.save();
        });
        row(1, "No blood: " + (GameSettings.noBlood ? "On" : "Off"), () -> {
            GameSettings.noBlood = !GameSettings.noBlood;
            GameSettings.save();
            if (client.worldRenderer != null) client.worldRenderer.reload();
        });
        row(2, "VSync: " + (client.options.getEnableVsync().getValue() ? "On" : "Off"), () -> {
            client.options.getEnableVsync().setValue(!client.options.getEnableVsync().getValue());
            client.options.write();
        });
        row(3, "View bobbing: " + (client.options.getBobView().getValue() ? "On" : "Off"), () -> {
            client.options.getBobView().setValue(!client.options.getBobView().getValue());
            client.options.write();
        });
    }

    private static String shortName(String device) {
        if (device.isEmpty()) return "Default";
        return device.replace("OpenAL Soft on ", "");
    }

    /** The label and, if it's open, everything you could pick. */
    private int dropdown(int line, int which, String label, String current, List<String> names, Consumer<String> pick) {
        row(line, label + ": " + shortName(current) + (open == which ? "  ^" : "  v"), () -> open = open == which ? 0 : which);
        if (open != which) return line + 1;
        int y = TOP + (line + 1) * ROW - 4;
        for (String name : names) {
            String shown = shortName(name);
            addDrawableChild(new MenuButton(left() + 10, y, WIDE - 10, LIST_ROW - 1, KokuhakuClient.menuText((name.equals(current) ? "> " : "") + shown), b -> {
                pick.accept(name);
                open = 0;
                clearAndInit();
            }));
            y += LIST_ROW;
        }
        return line + 1 + (names.size() * LIST_ROW + ROW - 1) / ROW;
    }

    private void audio() {
        bar(0, "Master volume", client.options.getSoundVolumeOption(SoundCategory.MASTER));
        bar(1, "Voices", client.options.getSoundVolumeOption(SoundCategory.VOICE));
        List<String> speakers = new ArrayList<>(List.of(""));
        speakers.addAll(client.getSoundManager().getSoundDevices());
        String current = client.options.getSoundDevice().getValue();
        int next = dropdown(2, 1, "Speakers", current.equals("") ? "" : current, speakers, name -> {
            client.options.getSoundDevice().setValue(name);
            client.options.write();
            client.getSoundManager().reloadSounds();
        });
        List<String> mics = new ArrayList<>(List.of(""));
        mics.addAll(Voice.microphones());
        dropdown(next, 2, "Microphone", GameSettings.microphone, mics, name -> {
            GameSettings.microphone = name;
            GameSettings.save();
            Voice.restartMicrophone();
        });
    }

    private void shader() {
        boolean on = GameSettings.shadersOn();
        row(0, "Shaders: " + (on ? "On" : "Off"), () -> {
            if (!GameSettings.setShaders(!on)) note = "Shaders aren't available here.";
            else if (!on && !GameSettings.shaderPackRunning()) note = "Switched on, but there's no shader pack installed to run.";
            else note = "";
        });
        row(1, "Quality: " + GameSettings.quality.label, () -> {
            GameSettings.Quality[] all = GameSettings.Quality.values();
            GameSettings.quality = all[(GameSettings.quality.ordinal() + 1) % all.length];
            GameSettings.save();
            GameSettings.applyQuality(client);
        });
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC8000000);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 12, 0);
        context.getMatrices().scale(2f, 2f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFE8E2E4);
        context.getMatrices().pop();
        if (!note.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(note), width / 2, height - 40, 0xFFE0283C);
        super.render(context, mouseX, mouseY, delta);
    }
}
