package com.kokuhaku.screen;

import com.kokuhaku.Difficulty;
import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.MenuScene;
import com.kokuhaku.client.Party;
import com.kokuhaku.client.StudentRenderer;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster;
import com.kokuhaku.item.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Pick Easy / Medium / Hard / Impossible before starting a run. */
public class DifficultyScreen extends Screen {
    private static final String FIND_HER = "Mode: Find Her", SURVIVE = "Mode: No One Will Save You";
    private static final String ACCOMPLICE_OFF = "Mode: Together", ACCOMPLICE_ON = "Mode: The Accomplice";
    private static boolean devoted = false, accomplice = false;
    private static final Identifier HEART = new Identifier(KokuhakuClient.MOD_ID, "textures/entity/heart.png");
    private static final String[] BEGGING = {"Please.", "Play with me.", "Just two weeks.", "I'll be so good to you.", "Don't go. Don't go. Don't go.", "You'll love it here.", "I've made up your room.", "You can't leave me here."};
    /** Her cycle: clasped hands, holding her face, clasped again, reaching out to you. */
    private static final StudentRenderer.Pose[] POSES = {StudentRenderer.Pose.HANDS_CLASPED, StudentRenderer.Pose.HOLDING_HER_FACE, StudentRenderer.Pose.HANDS_CLASPED, StudentRenderer.Pose.REACHING};
    private static final long[] POSE_MS = {3500, 2500, 3000, 3000};
    private static final long SLIDE_MS = 700, LINE_MS = 2600;
    private static final int HEARTS = 16, BACKGROUND = 0xFF0A0609;
    private static final float TO_THE_LEFT = -70f, TO_THE_RIGHT = 70f;
    private final Screen parent;
    private boolean begging = false;
    private long beggingSince = 0;

    public DifficultyScreen(Screen parent) {
        super(KokuhakuClient.menuText("Choose your difficulty"));
        this.parent = parent;
    }

    /** One thing she's doing, and how long it takes. */
    private record Beat(StudentRenderer.Pose pose, long ms, MenuButton smash, float toX, String says) {
    }

    /** A bit of a button, on its way to the floor. */
    private record Shard(float x, float y, float vx, float vy, long born) {
    }

    private static final long HOVER_MS = 10_000, SWING_MS = 760, WALK_MS = 760, THROW_MS = 1000, BAT_FLIES_MS = 800, BEND_MS = 2300, PULL_MS = 1000, SHARD_MS = 900;
    private static final float PRESS_AT = 0.72f;
    private static final int SHARDS = 22;
    private final java.util.List<MenuButton> others = new java.util.ArrayList<>();
    private final java.util.List<Beat> beats = new java.util.ArrayList<>();
    private final java.util.List<Shard> shards = new java.util.ArrayList<>();
    private final java.util.Random random = new java.util.Random();
    private MenuButton impossibleButton;
    private long hoverSince = 0, beatSince = 0, thrownAt = 0;
    private boolean smashing = false, hitThisBeat = false, pressed = false;
    private float thrownFrom = 0f;
    private int beat = 0;
    private float herX = -1f, fromX = 0f;

    private int impossibleY0() {
        return height / 2 - 50 + 72;
    }

    private int sizeOfHer() {
        return Math.max(70, height / 3);
    }

    private int restingX() {
        return width - sizeOfHer() - 16;
    }

    /** How much of her body height is shoulder, once she's bent over, and how far forward that puts it. */
    private static final float SHOULDER_UP = 1.26f, SHOULDER_ON = 0.17f, ARM = 0.7f, SIDE_ON = 0.92f, BAT_REACH = 1.15f, WAIST_BEND = 0.3f, EYES_UP = 1.72f;
    /** A turn of the head past her shoulders, towards the side she's facing, and how far down it'll tip. */
    private static final float LOOK_PAST = 0.34f, MOST_NOD = 0.95f;

    /** How far below her shoulder the button is, in her own heights. */
    private float buttonDrop() {
        float high = (height - 22 - (impossibleButton.getY() + 10)) / (float) sizeOfHer();
        return MathHelper.clamp(SHOULDER_UP - high, 0.05f, ARM - 0.05f);
    }

    /** The nod that puts her eyes on something at this height, from this far away. */
    private float nodAt(float x, MenuButton target) {
        float down = target.getY() + 10 - (height - 22 - EYES_UP * sizeOfHer());
        float across = Math.max(8f, x - (target.getX() + textRenderer.getWidth(target.getMessage()) * 0.5f));
        return MathHelper.clamp((float) Math.atan2(down, across), -0.5f, MOST_NOD);
    }

    /** What she's about to go for next. */
    private MenuButton nextTarget() {
        for (int i = beat; i < beats.size(); i++) if (beats.get(i).smash() != null) return beats.get(i).smash();
        return impossibleButton;
    }

    /** Where her feet need to be for her hand to land on the word. */
    private float standToClick() {
        float forward = MathHelper.sqrt(ARM * ARM - buttonDrop() * buttonDrop());
        return impossibleButton.getX() + textRenderer.getWidth(impossibleButton.getMessage()) * 0.6f + (SHOULDER_ON + forward) * SIDE_ON * sizeOfHer();
    }

    /** Ten seconds is long enough. She's decided for you. */
    private void startSmashing() {
        smashing = true;
        begging = true;
        herX = restingX();
        beats.add(new Beat(StudentRenderer.Pose.BAT, PULL_MS, null, herX, "You're taking too long."));
        String[] says = {"You don't need this.", "Or this.", "Not this one.", "There's only one way to play.", "You're not leaving."};
        float last = herX;
        for (int i = 0; i < others.size(); i++) {
            MenuButton b = others.get(i);
            last = b.getX() + textRenderer.getWidth(b.getMessage()) + sizeOfHer() * BAT_REACH;
            beats.add(new Beat(StudentRenderer.Pose.BAT_WALKING, WALK_MS, null, last, says[Math.min(i, says.length - 1)]));
            beats.add(new Beat(StudentRenderer.Pose.BAT, SWING_MS, b, last, says[Math.min(i, says.length - 1)]));
        }
        beats.add(new Beat(StudentRenderer.Pose.THROWING, THROW_MS, null, last, "There."));
        beats.add(new Beat(StudentRenderer.Pose.WALKING, WALK_MS, null, standToClick(), "Let me help."));
        beats.add(new Beat(StudentRenderer.Pose.BENDING, BEND_MS, null, standToClick(), "Let me help."));
        beat = 0;
        beatSince = System.currentTimeMillis();
        fromX = herX;
    }

    private void smash(MenuButton b) {
        for (int i = 0; i < SHARDS; i++) {
            shards.add(new Shard(b.getX() + random.nextFloat() * textRenderer.getWidth(b.getMessage()), b.getY() + 8 + random.nextFloat() * 6, (random.nextFloat() - 0.7f) * 0.09f, -0.03f - random.nextFloat() * 0.08f, System.currentTimeMillis()));
        }
        remove(b);
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_GLASS_BREAK, 0.8f + random.nextFloat() * 0.3f, 1f));
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 0.8f));
    }

    /** Moves everything along. Returns her current beat. */
    private Beat stepBeats(long now) {
        Beat b = beats.get(beat);
        long in = now - beatSince;
        // The moment the bat comes down.
        if (b.smash() != null && !hitThisBeat && in > SWING_MS * 0.54f) {
            hitThisBeat = true;
            smash(b.smash());
        }
        if (b.pose() == StudentRenderer.Pose.THROWING && thrownAt == 0 && in > THROW_MS * StudentRenderer.RELEASE) {
            thrownAt = now;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_SNOWBALL_THROW, 0.5f, 1f));
        }
        if (b.pose() == StudentRenderer.Pose.BENDING && !pressed && in > BEND_MS * PRESS_AT) {
            pressed = true;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1f));
        }
        if (in < b.ms()) return b;
        herX = b.toX();
        fromX = herX;
        hitThisBeat = false;
        beat++;
        beatSince = now;
        if (beat >= beats.size()) {
            beat = beats.size() - 1;
            smashing = false;
            start(Difficulty.IMPOSSIBLE);
            return b;
        }
        return beats.get(beat);
    }

    private void renderSmashing(DrawContext context, int mouseX, int mouseY, long now) {
        Beat b = stepBeats(now);
        StudentEntity her = MenuScene.girl(client);
        if (her == null) return;
        long in = now - beatSince;
        float t = Math.min(1f, in / (float) b.ms());
        boolean walking = b.pose() == StudentRenderer.Pose.BAT_WALKING || b.pose() == StudentRenderer.Pose.WALKING;
        // Setting off and stopping take a moment, like they do on feet.
        float eased = t * t * (3f - 2f * t);
        float x = walking ? MathHelper.lerp(eased, fromX, b.toX()) : b.toX();
        boolean bending = b.pose() == StudentRenderer.Pose.BENDING, throwing = b.pose() == StudentRenderer.Pose.THROWING;
        if (pressed && impossibleButton != null) {
            // It goes in under her finger.
            impossibleButton.setY(impossibleY0() + 2);
            context.fill(impossibleButton.getX() - 4, impossibleButton.getY() + 3, impossibleButton.getX() + 70, impossibleButton.getY() + 17, 0x70E0283C);
        }
        StudentRenderer.pose = b.pose();
        StudentRenderer.turned = throwing ? TO_THE_RIGHT : walking || b.smash() != null || bending ? TO_THE_LEFT : b.pose() == StudentRenderer.Pose.BAT && beat > 0 ? TO_THE_LEFT : 0f;
        StudentRenderer.batSwing = b.smash() != null || throwing ? t : -1f;
        // Whatever she's going for, she looks at it first.
        boolean towards = walking || b.smash() != null || bending || b.pose() == StudentRenderer.Pose.BAT && beat > 0;
        StudentRenderer.lookDown = towards ? nodAt(x, nextTarget()) : 0f;
        StudentRenderer.lookAside = StudentRenderer.turned < 0f ? LOOK_PAST : StudentRenderer.turned > 0f ? -LOOK_PAST : 0f;
        if (bending) {
            // Down first, then the arm goes out, then it comes down on the button.
            StudentRenderer.bend = WAIST_BEND * Math.min(1f, t / 0.4f);
            float forward = MathHelper.sqrt(ARM * ARM - buttonDrop() * buttonDrop());
            float reach = (float) -Math.atan2(forward, buttonDrop());
            StudentRenderer.handDown = t < 0.35f ? -0.3f : t < PRESS_AT ? reach - 0.25f : reach;
        }
        boolean hidden = her.isInvisible();
        her.setInvisible(false);
        InventoryScreen.drawEntity(context, (int) x, height - 22, sizeOfHer(), 0f, 0f, her);
        her.setInvisible(hidden);
        StudentRenderer.pose = StudentRenderer.Pose.NONE;
        StudentRenderer.turned = 0f;
        StudentRenderer.batSwing = -1f;
        StudentRenderer.bend = 0f;
        StudentRenderer.lookDown = 0f;
        StudentRenderer.lookAside = 0f;
        Text said = KokuhakuClient.menuText("\"" + b.says() + "\"");
        // Beside her head: up above it, it'd run into everything else on the page.
        int textX = Math.min((int) x + sizeOfHer() / 2 + 6, width - 8 - textRenderer.getWidth(said));
        context.drawTextWithShadow(textRenderer, said, textX, height - 22 - sizeOfHer() * 2 + 8, 0xFFF08098);
        // The bat, end over end, off the side of the screen.
        if (thrownAt != 0 && now - thrownAt < BAT_FLIES_MS) {
            float fly = (now - thrownAt) / (float) BAT_FLIES_MS;
            var m = context.getMatrices();
            m.push();
            float handX = thrownFrom == 0f ? thrownFrom = x + sizeOfHer() * 0.4f : thrownFrom;
            m.translate(handX + (width - handX + 40) * fly, height - 22 - sizeOfHer() * 1.7f + fly * fly * height * 0.45f - (float) Math.sin(fly * Math.PI) * height * 0.22f, 100);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fly * 900f));
            m.scale(2f, 2f, 1f);
            context.drawItem(new ItemStack(ModItems.BAT), -8, -8);
            m.pop();
        }
    }

    private void renderShards(DrawContext context, long now) {
        shards.removeIf(s -> now - s.born() > SHARD_MS);
        for (Shard s : shards) {
            float age = now - s.born();
            int x = (int) (s.x() + s.vx() * age), y = (int) (s.y() + s.vy() * age + 0.00012f * age * age);
            context.fill(x, y, x + 2, y + 2, 0xFFD8D0D4);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return smashing || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !smashing;
    }

    @Override
    protected void init() {
        int x = Math.max(24, width / 12);
        int y = height / 2 - 50;
        others.clear();
        for (Difficulty d : Difficulty.values()) {
            MenuButton button = addDrawableChild(new MenuButton(x, y, 150, 20, KokuhakuClient.menuText(d.label), b -> start(d)));
            if (d == Difficulty.IMPOSSIBLE) impossibleButton = button;
            else others.add(button);
            y += 24;
        }
        if (alone()) {
            others.add(addDrawableChild(new MenuButton(x, y + 6, 190, 20, KokuhakuClient.menuText(devoted ? SURVIVE : FIND_HER), b -> {
                devoted = !devoted;
                clearAndInit();
            })));
            y += 22;
        } else {
            addDrawableChild(new MenuButton(x, y + 6, 190, 20, KokuhakuClient.menuText(accomplice ? ACCOMPLICE_ON : ACCOMPLICE_OFF), b -> {
                accomplice = !accomplice;
                clearAndInit();
            }));
            y += 22;
        }
        others.add(addDrawableChild(new MenuButton(x, y + 14, 150, 20, KokuhakuClient.menuText("Back"), b -> close())));
    }

    private boolean alone() {
        return Party.members.size() <= 1;
    }

    private void start(Difficulty d) {
        MenuScene.start(d, devoted && alone(), accomplice && !alone());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private StudentRenderer.Pose poseNow(long since) {
        long total = 0;
        for (long ms : POSE_MS) total += ms;
        long at = since % total;
        for (int i = 0; i < POSES.length; i++) {
            if (at < POSE_MS[i]) return POSES[i];
            at -= POSE_MS[i];
        }
        return POSES[0];
    }

    /** On the right of the screen, on her own, with hearts going up round her, asking you to stay. */
    private void renderHer(DrawContext context, int mouseX, int mouseY) {
        StudentEntity her = MenuScene.girl(client);
        if (her == null) return;
        long since = System.currentTimeMillis() - beggingSince;
        float in = Math.min(1f, since / (float) SLIDE_MS);
        int size = Math.max(70, height / 3), mid = width - size - 16 + (int) ((1f - in * in) * (size * 2));
        int feet = height - 22, top = feet - size * 2;
        long now = System.currentTimeMillis();
        for (int i = 0; i < HEARTS; i++) {
            float rise = ((now / 25 + i * 137L) % 400) / 400f;
            int across = (int) (Math.sin(i * 2.4 + now / 900.0) * size * 0.95);
            int y = feet - (int) (rise * (feet - top + 24));
            int heart = 7 + i % 3 * 3 + (int) (Math.sin(now / 180.0 + i) * 1.5);
            float fade = rise < 0.15f ? rise / 0.15f : rise > 0.8f ? (1f - rise) / 0.2f : 1f;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, in * fade);
            context.drawTexture(HEART, mid + across - heart / 2, y, heart, heart, 0, 0, 16, 16, 16, 16);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        StudentRenderer.Pose pose = poseNow(since);
        StudentRenderer.pose = pose;
        boolean hidden = her.isInvisible();
        her.setInvisible(false);
        InventoryScreen.drawEntity(context, mid, feet, size, 0f, 0f, her);
        her.setInvisible(hidden);
        StudentRenderer.pose = StudentRenderer.Pose.NONE;
        String line = BEGGING[(int) (since / LINE_MS % BEGGING.length)];
        Text said = KokuhakuClient.menuText("\"" + line + "\"");
        int textX = Math.max(8, Math.min(mid - textRenderer.getWidth(said) / 2, width - 8 - textRenderer.getWidth(said)));
        if (in >= 1f) context.drawTextWithShadow(textRenderer, said, textX, top - 16, 0xFFF08098);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        int x = Math.max(24, width / 12);
        // Once she's seen you looking at it, she doesn't let it go until you look at something else.
        int impossibleY = height / 2 - 50 + 72;
        boolean overImpossible = mouseX >= x && mouseX < x + 150 && mouseY >= impossibleY && mouseY < impossibleY + 20;
        boolean overAnother = mouseX >= x && mouseX < x + 150 && mouseY >= height / 2 - 50 && mouseY < impossibleY;
        if (smashing) {
            renderSmashing(context, mouseX, mouseY, System.currentTimeMillis());
        } else {
            if (!devoted || !alone() || overAnother) begging = false;
            else if (overImpossible && !begging) {
                begging = true;
                beggingSince = System.currentTimeMillis();
            }
            hoverSince = begging && overImpossible ? hoverSince == 0 ? System.currentTimeMillis() : hoverSince : 0;
            if (hoverSince != 0 && System.currentTimeMillis() - hoverSince > HOVER_MS) startSmashing();
            if (begging) renderHer(context, mouseX, mouseY);
        }
        renderShards(context, System.currentTimeMillis());
        if (!smashing) context.drawTextWithShadow(textRenderer, title, x, height / 2 - 72, 0xFFE8E2E4);
        super.render(context, mouseX, mouseY, delta);

        if (smashing) {
            // Nothing's left to read but what she's saying.
        } else if (begging) {
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("No One Can Save You"), x, 8, 0xFFE0283C);
            context.drawTextWithShadow(textRenderer, Text.literal("She leads you everywhere. Two weeks isn't enough."), x, 20, 0xFF9A9AA2);
            context.drawTextWithShadow(textRenderer, Text.literal("You can't survive this. You have to get out."), x, 31, 0xFF9A9AA2);
        } else if (devoted && alone()) {
            int line = x + 166;
            context.drawTextWithShadow(textRenderer, Text.literal("Everyone knows who she is. So do you."), line, height / 2 + 52, 0xFFE0283C);
            context.drawTextWithShadow(textRenderer, Text.literal("Survive two weeks beside her."), line, height / 2 + 64, 0xFF9A9AA2);
            context.drawTextWithShadow(textRenderer, Text.literal("Stay away from everyone."), line, height / 2 + 76, 0xFF9A9AA2);
            context.drawTextWithShadow(textRenderer, Text.literal("No one will save you."), line, height / 2 + 88, 0xFF9A9AA2);
        }
        if (accomplice && !alone()) {
            int line = x + 206;
            context.drawTextWithShadow(textRenderer, Text.literal("One of you is secretly working for her."), line, height / 2 + 52, 0xFFE0283C);
            context.drawTextWithShadow(textRenderer, Text.literal("Nobody but them will know who."), line, height / 2 + 64, 0xFF9A9AA2);
            context.drawTextWithShadow(textRenderer, Text.literal("No voice chat in this mode."), line, height / 2 + 76, 0xFF9A9AA2);
        }
        int party = Math.max(1, Party.members.size());
        if (party > 1) {
            int extra = party - 1;
            context.drawTextWithShadow(textRenderer, Text.literal("Party of " + party + ": " + Roster.girlsFor(party) + " girls, " + Roster.boysFor(party)
                    + " boys, " + party + " yanderes, +" + extra + (extra == 1 ? " guess" : " guesses")), x, height / 2 + 66, 0xFF9A9AA2);
        }

        int y = height / 2 - 50;
        for (Difficulty d : Difficulty.values()) {
            if (!begging && mouseX >= x && mouseX < x + 150 && mouseY >= y && mouseY < y + 20) {
                context.drawTextWithShadow(textRenderer, Text.literal(d.description), x + 166, y + 6, 0xFF9A9AA2);
            }
            y += 24;
        }
    }
}
