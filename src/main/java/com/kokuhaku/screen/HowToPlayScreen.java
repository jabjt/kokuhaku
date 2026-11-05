package com.kokuhaku.screen;

import com.kokuhaku.Difficulty;
import com.kokuhaku.KokuhakuClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The guide: the basics first, then playing with friends, then the modes, one topic to a page. */
public class HowToPlayScreen extends Screen {
    private static final Identifier ICONS = new Identifier(KokuhakuClient.MOD_ID, "textures/gui/icons.png");
    private static final int ICON_COUNT = 10;
    // Indexes into icons.png (see tools/ArtGen.java).
    private static final int NOTE = 0, CALENDAR = 1, BAG = 2, CLUE = 3, TALK = 4, HOUSE = 5, CLOCK = 6, ACCUSE = 7,
            BAT = 8, EYE = 9;

    private enum Section { BASICS("Basics"), MULTIPLAYER("Multiplayer"), MODES("Modes");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private record Card(int icon, String title, String body) {
    }

    private record Page(String heading, Card... cards) {
    }

    private static final Page[] BASICS_PAGES = {
            new Page("The story",
                    new Card(NOTE, "The note", "On your first morning there's a note in your shoe locker: \"I love you.\" One of the girls wrote it, and she isn't well."),
                    new Card(CALENDAR, "Two weeks", "Find out who she is before day 14. There are 20 girls and 20 boys at school, and someone always knows something."),
                    new Card(ACCUSE, "Name her", "When only one girl fits, find her and say it to her face. A wrong guess costs you one of your few guesses, and she'll never forgive you.")),
            new Page("A school day",
                    new Card(HOUSE, "Mornings", "Someone calls you from downstairs. Go to your front door and you walk in together. Answer them, ask them things. Space skips the walk."),
                    new Card(CLOCK, "Lessons", "Press T for your diary. Be in the right room when the bell goes. Answer in time; put your hand up (H) when asked, for a small prize."),
                    new Card(CLOCK, "PE and practicals", "PE is at the pool one day and on the outdoor court the next. Some Science lessons are in the upstairs lab, and some Home Economics in the kitchens."),
                    new Card(BAT, "Marks", "Absences and bad lessons earn marks. Too many and you're expelled. A teacher will let you work one off after school.")),
            new Page("Finding her",
                    new Card(TALK, "Ask everyone", "Right-click people. Ask about them, and what they've noticed. Listen to what they avoid saying."),
                    new Card(BAG, "Your bag", "Press E. Every clue goes in here, and Suspects lets you cross off anyone who doesn't fit."),
                    new Card(CLUE, "Look around", "Clubs and neighbourhoods have registers. She drops things. Someone who dies may leave something in their room."),
                    new Card(TALK, "Your phone", "Press G. Your class never stops messaging, and from day 3 there's a number you don't know.")),
            new Page("Staying alive",
                    new Card(BAT, "Curfew", "Be home before curfew. Hold your key (slot 2) and right-click your front door to lock it. Then sleep in your own bed to reach morning."),
                    new Card(CLOCK, "Green tea", "Drink one from a vending machine, and if you're still out at curfew you get a chance: 2:30 to reach home. Slow down and the clock runs faster."),
                    new Card(BAT, "Alone", "Stay where people can see you, above all after dark. If she gets hold of you, press the keys as they appear."),
                    new Card(HOUSE, "The roads", "Cross at the white lines. A car or train means hospital on Easy and Medium, and worse on anything harder. Hospital costs money.")),
            new Page("Money and people",
                    new Card(CLOCK, "Work", "Job boards pay for a shift, and coworkers gossip. The shop boards sell a door chain, pepper spray and sweets."),
                    new Card(TALK, "Favours", "Ask people if they need a hand. Find what they've lost for money and what they know."),
                    new Card(TALK, "Going out", "Say something nice: each girl wants to hear one thing. Once she likes you, you can ask her out. She walks you to school from then on."),
                    new Card(CALENDAR, "The School Festival", "Day 13. Take the train from Chinetsu Station, try the stalls, and choose carefully who you watch the fireworks with.")),
    };

    private static final Page[] MULTIPLAYER_PAGES = {
            new Page("Playing together",
                    new Card(TALK, "Your party", "Click a + in the corner of the menu to invite up to two friends, or to join theirs. They need the game too."),
                    new Card(ACCUSE, "More of them", "Each friend adds 3 girls, 3 boys, a guess and another yandere. Name them all to win. Clues say which one they mean."),
                    new Card(BAG, "What you share", "You share a bag, money and guesses. Talk near each other: your voice carries about 24 blocks, and V mutes you."),
                    new Card(EYE, "Your box", "Click a face in the corner to see their profile. Click your own to sit a run out as a spectator: you start as a ghost.")),
            new Page("Ghosts",
                    new Card(BAT, "Getting caught", "If she gets a friend they're gone, and the rest of you only hear about it in the morning. They can't see who she was."),
                    new Card(EYE, "Calling them back", "Open your bag (E). A button at the bottom left calls them back for ¥15,000 of your shared money."),
                    new Card(CLUE, "As a ghost", "They can fly, nothing can touch them, and they can search where people died and tell you what they find. They can't talk to anyone else.")),
    };

    private static final Page[] MODES_PAGES = {
            new Page("No One Will Save You",
                    new Card(EYE, "For one player", "Picked on the difficulty screen. Everyone knows who she is, you included. There's nothing to solve: survive the two weeks."),
                    new Card(BAT, "Her rules", "Every morning she gives you a rule. Stand near anyone and the line at the bottom of the screen fills. When it's full, she looks at you."),
                    new Card(TALK, "Nobody helps", "They'll get out of your way. If you beg them, they'll apologise and tell her. Whoever you were near won't be at school tomorrow.")),
            new Page("No One Can Save You",
                    new Card(BAT, "On Impossible", "No One Will Save You on Impossible is something else. She leads you everywhere. Pull away and something breaks. Stop and you lose hours."),
                    new Card(CALENDAR, "Two weeks isn't enough", "Nobody comes on the last morning. There's only one way to win, and that's to get out."),
                    new Card(CLUE, "Haruka tried", "She wrote it all down. Read every diary page you can reach, and read the margins.")),
            new Page("The Accomplice",
                    new Card(ACCUSE, "For a party", "Picked on the difficulty screen. One of you secretly works for her."),
                    new Card(CLUE, "False leads", "They can plant up to 3 false leads in your shared bag, and they win if everyone else is gone."),
                    new Card(BAG, "No voices", "Voice chat is off, so leave notes in your shared bag. Only the girls can be named, and a name on a note proves nothing.")),
            new Page("Music",
                    new Card(NOTE, "On the radio", "BEZTEBYA. Fever (instrumental) by fakemink, prod. OSCAR18. Refrain by shytaupe. Each belongs to its own artist."),
                    new Card(NOTE, "Her request", "Truth, by akkiemi.")),
    };

    private final Screen parent;
    private Section section = Section.BASICS;
    private int page = 0;
    private MenuButton prev, next;

    public HowToPlayScreen(Screen parent) {
        super(KokuhakuClient.menuText("How to Play"));
        this.parent = parent;
    }

    private Page[] pages() {
        return switch (section) {
            case BASICS -> BASICS_PAGES;
            case MULTIPLAYER -> MULTIPLAYER_PAGES;
            case MODES -> MODES_PAGES;
        };
    }

    /** Modes end with the difficulties. */
    private int pageCount() {
        return pages().length + (section == Section.MODES ? 1 : 0);
    }

    @Override
    protected void init() {
        int y = height - 26;
        int x = width / 2 - 120;
        for (Section s : Section.values()) {
            addDrawableChild(new MenuButton(x + s.ordinal() * 85, 36, 80, 16, KokuhakuClient.menuText(s == section ? "> " + s.label : s.label), b -> {
                section = s;
                page = 0;
                clearAndInit();
            }));
        }
        prev = addDrawableChild(new MenuButton(width / 2 - 160, y, 70, 18, KokuhakuClient.menuText("< Prev"), b -> turn(-1)));
        addDrawableChild(new MenuButton(width / 2 - 20, y, 60, 18, KokuhakuClient.menuText("Back"), b -> close()));
        next = addDrawableChild(new MenuButton(width / 2 + 90, y, 70, 18, KokuhakuClient.menuText("Next >"), b -> turn(1)));
        updateButtons();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void turn(int dir) {
        page = Math.max(0, Math.min(pageCount() - 1, page + dir));
        updateButtons();
    }

    private void updateButtons() {
        prev.active = page > 0;
        next.active = page < pageCount() - 1;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC8000000);

        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 12, 0);
        context.getMatrices().scale(2f, 2f, 1f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFE8E2E4);
        context.getMatrices().pop();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pageCount()), width / 2, height - 38, 0xFF6C6C74);

        if (page < pages().length) {
            renderPage(context, pages()[page]);
        } else {
            renderDifficulties(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    /** One topic to a page, one thing to a line. */
    private void renderPage(DrawContext context, Page p) {
        int wide = Math.min(420, width - 40), x = (width - wide) / 2, y = 58;
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(p.heading()), x, y, 0xFFE0283C);
        y += 14;
        int rowH = Math.min(46, (height - 44 - y) / p.cards().length);
        for (Card card : p.cards()) {
            drawIcon(context, card.icon, x, y + 2, 1);
            context.drawTextWithShadow(textRenderer, Text.literal(card.title), x + 24, y, 0xFFF2D6DA);
            context.drawTextWrapped(textRenderer, Text.literal(card.body), x + 24, y + 11, wide - 24, 0xFFB0A8AC);
            y += rowH;
        }
    }

    private void drawIcon(DrawContext context, int icon, int x, int y, int scale) {
        context.drawTexture(ICONS, x, y, 16 * scale, 16 * scale, icon * 16, 0, 16, 16, 16 * ICON_COUNT, 16);
    }

    private void renderDifficulties(DrawContext context) {
        int rowH = 34;
        int w = Math.min(420, width - 40);
        int x = (width - w) / 2;
        int y = 58;
        for (Difficulty d : Difficulty.values()) {
            context.fill(x, y, x + w, y + rowH - 4, 0xAA141016);
            int accent = switch (d) {
                case EASY -> 0xFF4A8A5A;
                case MEDIUM -> 0xFFB08A3A;
                case HARD -> 0xFFB0502A;
                case IMPOSSIBLE -> 0xFFC0182E;
            };
            context.fill(x, y, x + 2, y + rowH - 4, accent);
            context.drawTextWithShadow(textRenderer, Text.literal(d.label), x + 10, y + 5, accent);
            context.drawTextWithShadow(textRenderer, Text.literal(d.description), x + 10, y + 17, 0xFF9A9AA2);

            // Guesses as small crosshair icons, plus an eye if hints are on.
            int ix = x + w - 8;
            for (int i = 0; i < d.guesses; i++) {
                ix -= 18;
                drawIcon(context, ACCUSE, ix, y + 6, 1);
            }
            if (d.hints) {
                drawIcon(context, EYE, ix - 24, y + 6, 1);
            }
            y += rowH;
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Each crosshair is one guess. The eye means hints are on."), width / 2, y + 4, 0xFF6C6C74);
    }
}
