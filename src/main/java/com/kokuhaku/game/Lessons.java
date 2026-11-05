package com.kokuhaku.game;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Lessons: the teacher says something, then six questions. Maths is generated;
 * other subjects draw from a bank of things anyone would know. All four-way
 * multiple choice, some of them hands-up with a small prize in it, and one is a
 * yes or no note from a girl in the class, which changes how she feels about you.
 */
public final class Lessons {
    public static final int CHOICE = 0, NOTE = 2, HANDS = 3;

    /** yesDelta: how answering yes changes her fondness; no does the opposite. */
    public record Question(int kind, String text, List<String> answers, int correct, int girl, int yesDelta) {
        Question(String text, List<String> answers, int correct) {
            this(CHOICE, text, answers, correct, -1, 0);
        }
    }

    private static final Map<String, String[]> INTROS = Map.of(
            "Japanese", new String[]{"\"Books open. We're reading aloud today, so stay awake.\"", "\"Your essays were disappointing. Let's try again.\"",
                    "\"A character for everything, and still you can't find the words.\"", "\"Eyes on your own page, please.\""},
            "Mathematics", new String[]{"\"No calculators. I'll know.\"", "\"Yesterday's average was the lowest I've ever seen.\"",
                    "\"Show your working. Somebody here copied.\"", "\"It always comes out even, if you're careful.\""},
            "English", new String[]{"\"Repeat after me. No? Then we'll do this quietly.\"", "\"Today, the past tense. What's done is done.\"",
                    "\"Someone wrote a very strange love letter in last week's exercise.\"", "\"Please. Say it like you mean it.\""},
            "Science", new String[]{"\"Goggles on. Nobody touches anything yet.\"", "\"Something's missing from the supply cupboard. We'll speak later.\"",
                    "\"Observe first. Most of you never do.\"", "\"Mind the sink. It hasn't been draining properly.\""},
            "History", new String[]{"\"People always leave a record, even when they try not to.\"", "\"Pop quiz. Don't look so surprised.\"",
                    "\"The ones who were forgotten are the interesting ones.\"", "\"Same mistakes, different dates. Let's begin.\""});
    private static final String[] INTROS_ANY = {"\"Sit down. We're behind.\"", "\"Quiet. There's a lot to get through.\"",
            "\"Whoever's humming, stop.\"", "\"There's an empty desk today. We're not discussing it.\"",
            "The teacher writes the date on the board, and doesn't turn round for a long time.", "\"Not a word until the bell.\"",
            "The classroom clock has stopped. Nobody mentions it.", "\"Page after page. I won't be repeating myself.\""};

    /** {what she asks, how yes changes her fondness}. */
    private static final String[][] NOTES = {
            {"%s slides a note across: \"Will you walk home with me?\"", "1"},
            {"%s whispers: \"Did you see me at the station?\"", "1"},
            {"%s leans over: \"Do you think I'm strange?\"", "-1"},
            {"A note lands on your desk, from %s: \"Were you looking at someone else?\"", "-1"},
            {"%s taps your arm: \"Can I borrow your eraser?\"", "1"},
            {"%s writes in the corner of your book: \"Do you have a girlfriend?\"", "-1"},
            {"%s whispers: \"Would you tell me if something was wrong?\"", "1"},
            {"%s passes you a note: \"Do you like it here?\"", "1"},
            {"%s doesn't look up: \"Should I stop talking to you?\"", "-1"},
            {"%s mouths it at you: \"Are you busy after school?\"", "-1"},
            {"A folded note from %s: \"Did you keep what I gave you?\"", "1"},
            {"%s whispers: \"Would you notice if I wasn't here?\"", "1"},
    };

    private static final Map<String, String[][]> BANK = Map.ofEntries(
            Map.entry("Art", new String[][]{
                    {"Mixing red and yellow makes:", "Orange", "Green", "Purple", "Grey"},
                    {"Mixing blue and yellow makes:", "Green", "Red", "Orange", "Pink"},
                    {"Which of these is a primary colour?", "Blue", "Green", "Orange", "Purple"},
                    {"What do you call a painting of yourself?", "A self-portrait", "A landscape", "A still life", "A mural"},
                    {"A picture of a view of the countryside is a:", "Landscape", "Portrait", "Sculpture", "Sketch"},
                    {"What is pottery made from?", "Clay", "Chalk", "Wax", "Glass"},
                    {"Who painted the Mona Lisa?", "Leonardo da Vinci", "Picasso", "Hokusai", "Van Gogh"},
                    {"Which Japanese artist made The Great Wave?", "Hokusai", "Hiroshige", "Monet", "Kurosawa"},
                    {"What is origami the art of?", "Folding paper", "Arranging flowers", "Writing with a brush", "Pouring tea"},
                    {"What does a sculptor make?", "Statues", "Songs", "Clothes", "Poems"}}),
            Map.entry("Music", new String[][]{
                    {"How many lines does a musical stave have?", "5", "4", "6", "8"},
                    {"How many beats does a crotchet last?", "1", "2", "4", "8"},
                    {"How many keys does a piano have?", "88", "66", "72", "100"},
                    {"Which of these is a string instrument?", "Violin", "Flute", "Trumpet", "Drum"},
                    {"Which of these do you blow into?", "Flute", "Cello", "Harp", "Piano"},
                    {"How many strings does a guitar usually have?", "6", "4", "8", "12"},
                    {"What does 'forte' mean?", "Loud", "Quiet", "Fast", "Slow"},
                    {"What does 'piano' mean in music?", "Quiet", "Loud", "Fast", "Short"},
                    {"What is the koto?", "A Japanese string instrument", "A Japanese drum", "A kind of flute", "A singing style"},
                    {"How many musicians are in a quartet?", "4", "2", "3", "8"}}),
            Map.entry("Home Economics", new String[][]{
                    {"What do you add to make bread rise?", "Yeast", "Salt", "Sugar", "Vinegar"},
                    {"At what temperature does water boil?", "100°C", "50°C", "75°C", "150°C"},
                    {"What should you do before cooking?", "Wash your hands", "Open a window", "Sharpen a pencil", "Sit down"},
                    {"What is miso soup made from?", "Soybean paste", "Rice flour", "Fish scales", "Wheat bran"},
                    {"Which of these is high in protein?", "Eggs", "Lettuce", "Sugar", "Butter"},
                    {"How do you make a stitch stronger?", "Sew a double line", "Use less thread", "Wet the cloth", "Sew it faster"},
                    {"What do you use to sew a button on?", "A needle and thread", "A stapler", "Glue", "Scissors"},
                    {"Raw chicken should be stored:", "At the bottom of the fridge", "On the top shelf", "On the counter", "By the window"},
                    {"What is the main ingredient of tofu?", "Soybeans", "Rice", "Eggs", "Fish"},
                    {"Which vitamin do oranges give you most of?", "Vitamin C", "Vitamin D", "Vitamin K", "Vitamin B12"}}),
            Map.entry("Class Meeting", new String[][]{
                    {"Who should you tell if you find something lost?", "The class teacher", "Nobody", "The caretaker's dog", "Whoever's nearest"},
                    {"What must you do before leaving the classroom at the end of the day?", "Clean it", "Sign out", "Bow to the board", "Open the windows"},
                    {"Where do you change your shoes at school?", "The entrance hall", "The classroom", "The gym", "The roof"},
                    {"What time must students be off the street?", "Before curfew", "Before lunch", "After midnight", "Whenever they like"},
                    {"Who is your class representative chosen by?", "The class", "The head teacher", "The caretaker", "Their parents"},
                    {"What should you do if you're going to be absent?", "Tell the school", "Nothing", "Send a friend", "Leave a note on the gate"},
                    {"What do you say to a teacher in the morning?", "Good morning", "Goodbye", "Welcome back", "Thank you for the food"},
                    {"How often do class duties rotate?", "Every week", "Every year", "Every lesson", "Never"},
                    {"What should you do if you see something strange at school?", "Report it to a teacher", "Keep it to yourself", "Take it home", "Post it in the class chat"},
                    {"What goes in the suggestion box?", "Ideas for the class", "Lost property", "Homework", "Money"}}),
            Map.entry("Japanese", new String[][]{
                    {"What does 'arigatou' mean?", "Thank you", "Goodbye", "Sorry", "Hello"},
                    {"What does 'sayonara' mean?", "Goodbye", "Good morning", "Thank you", "Please"},
                    {"What is Japan's highest mountain?", "Mount Fuji", "Mount Aso", "Mount Everest", "Mount Zao"},
                    {"How many lines does a haiku have?", "3", "2", "5", "14"},
                    {"What does 'neko' mean?", "Cat", "Dog", "Bird", "Fish"},
                    {"What does 'inu' mean?", "Dog", "Cat", "Horse", "Rabbit"},
                    {"What is the capital of Japan?", "Tokyo", "Osaka", "Kyoto", "Sapporo"},
                    {"What does 'hai' mean?", "Yes", "No", "Maybe", "Stop"},
                    {"What does 'sakura' mean?", "Cherry blossom", "River", "Snow", "Mountain"},
                    {"Which of these do you eat with?", "Chopsticks", "A fan", "A kimono", "A lantern"}}),
            Map.entry("English", new String[][]{
                    {"What is the past tense of 'go'?", "went", "goed", "gone", "going"},
                    {"What is the plural of 'child'?", "children", "childs", "childes", "child"},
                    {"'She ___ to school every day.'", "goes", "go", "going", "gone"},
                    {"What is the opposite of 'hot'?", "cold", "warm", "big", "fast"},
                    {"Which word is a colour?", "blue", "run", "happy", "table"},
                    {"Which word is an animal?", "horse", "chair", "green", "river"},
                    {"What is the opposite of 'up'?", "down", "left", "over", "near"},
                    {"How many letters are in the alphabet?", "26", "24", "30", "20"},
                    {"'I ___ a student.'", "am", "is", "are", "be"},
                    {"What comes after Monday?", "Tuesday", "Sunday", "Friday", "Thursday"}}),
            Map.entry("Science", new String[][]{
                    {"What gas do we breathe in to live?", "Oxygen", "Helium", "Neon", "Carbon"},
                    {"What do plants need from the sky?", "Sunlight", "Sand", "Salt", "Metal"},
                    {"At what temperature does water boil?", "100\u00B0C", "10\u00B0C", "50\u00B0C", "500\u00B0C"},
                    {"Which is the largest planet?", "Jupiter", "Mars", "Earth", "Venus"},
                    {"What pulls things down to Earth?", "Gravity", "Magnets", "Wind", "Light"},
                    {"What planet do we live on?", "Earth", "Mars", "Saturn", "The Moon"},
                    {"Water freezes into what?", "Ice", "Steam", "Sand", "Oil"},
                    {"Which of these is a metal?", "Iron", "Wood", "Glass", "Paper"},
                    {"How many legs does an insect have?", "6", "4", "8", "2"},
                    {"What does the heart pump?", "Blood", "Air", "Water", "Food"}}),
            Map.entry("History", new String[][]{
                    {"What were Japanese warriors called?", "Samurai", "Sailors", "Monks", "Farmers"},
                    {"Which city was Japan's capital before Tokyo?", "Kyoto", "Osaka", "Nagoya", "Sendai"},
                    {"Who ruled Japan's warriors for centuries?", "The shogun", "The sheriff", "The pharaoh", "The mayor"},
                    {"What was a ninja known for?", "Stealth", "Singing", "Farming", "Baking"},
                    {"What did samurai carry?", "A sword", "A guitar", "A camera", "A phone"},
                    {"In what country is this school?", "Japan", "France", "Brazil", "Canada"},
                    {"What is a castle's main tall tower for?", "Keeping watch", "Cooking", "Swimming", "Farming"},
                    {"What do we study in history?", "The past", "The future", "Numbers", "Plants"},
                    {"What did people use before electric light?", "Candles", "Phones", "Mirrors", "Clocks"},
                    {"What is a museum for?", "Keeping old things", "Selling cars", "Growing rice", "Catching fish"}})
    );


    private Lessons() {
    }

    public static List<Question> make(String subject, Random r, List<Integer> girls, int level) {
        List<Question> out = new ArrayList<>(subject.equals("Mathematics") ? maths(r) : fromBank(subject, r));
        // One or two the teacher wants hands up for.
        hands(out, 1, r, girls);
        if (r.nextBoolean()) hands(out, 5, r, girls);
        // Somewhere in the easy half, someone wants your attention.
        String[] note = NOTES[r.nextInt(NOTES.length)];
        int girl = girls.get(r.nextInt(girls.size()));
        out.set(3, new Question(NOTE, String.format(note[0], Roster.get(girl).firstName()), List.of(), 0, girl, Integer.parseInt(note[1])));
        return out;
    }

    public static String intro(String subject, Random r) {
        String[] own = INTROS.get(subject);
        if (own != null && r.nextBoolean()) return own[r.nextInt(own.length)];
        return INTROS_ANY[r.nextInt(INTROS_ANY.length)];
    }

    /** The same question, put to the room. Whoever's beaten you to it is a girl in the class. */
    private static void hands(List<Question> questions, int index, Random r, List<Integer> girls) {
        Question q = questions.get(index);
        questions.set(index, new Question(HANDS, q.text(), q.answers(), q.correct(), girls.get(r.nextInt(girls.size())), 0));
    }

    private static List<Question> fromBank(String subject, Random r) {
        String[][] bank = BANK.get(subject);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bank.length; i++) order.add(i);
        Collections.shuffle(order, r);
        List<Question> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) out.add(fromBank(bank[order.get(i)], r));
        return out;
    }

    private static Question fromBank(String[] q, Random r) {
        return choices(q[0], q[1], List.of(q[2], q[3], q[4]), r);
    }

    private static Question choices(String text, String right, List<String> wrong, Random r) {
        List<String> all = new ArrayList<>(wrong);
        all.add(right);
        Collections.shuffle(all, r);
        return new Question(text, all, all.indexOf(right));
    }

    private static Question number(String text, int answer, Random r) {
        List<Integer> wrong = new ArrayList<>();
        while (wrong.size() < 3) {
            int w = answer + (r.nextInt(9) - 4) * (r.nextBoolean() ? 1 : 10);
            if (w != answer && !wrong.contains(w)) wrong.add(w);
        }
        return choices(text, String.valueOf(answer), wrong.stream().map(String::valueOf).toList(), r);
    }

    private static List<Question> maths(Random r) {
        List<Question> out = new ArrayList<>();
        int a = 4 + r.nextInt(15), b = 3 + r.nextInt(12);
        out.add(number(a + " + " + b + " = ?", a + b, r));
        int c = 2 + r.nextInt(8), d = 2 + r.nextInt(5);
        out.add(number(c + " \u00D7 " + d + " = ?", c * d, r));
        int e = 10 + r.nextInt(20), f = 2 + r.nextInt(8);
        out.add(number(e + " - " + f + " = ?", e - f, r));
        int g = 2 + r.nextInt(9);
        out.add(number("Half of " + g * 2 + " is?", g, r));
        int h = 2 + r.nextInt(7), i = 2 + r.nextInt(7);
        out.add(number("x + " + h + " = " + (h + i) + ". What is x?", i, r));
        int s = 2 + r.nextInt(6);
        out.add(number("A square has sides of " + s + ". What is its area?", s * s, r));
        return out;
    }
    public static NbtCompound toNbt(String subject, String intro, List<Question> questions, boolean glitches) {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Glitches", glitches);
        nbt.putString("Subject", subject);
        nbt.putString("Intro", intro);
        NbtList list = new NbtList();
        for (Question q : questions) {
            NbtCompound e = new NbtCompound();
            e.putString("Text", q.text());
            NbtList a = new NbtList();
            for (String s : q.answers()) a.add(NbtString.of(s));
            e.put("Answers", a);
            e.putInt("Correct", q.correct());
            e.putInt("Kind", q.kind());
            e.putInt("Girl", q.girl());
            e.putInt("YesDelta", q.yesDelta());
            if (q.kind() == HANDS) e.putString("Other", Roster.get(q.girl()).firstName());
            list.add(e);
        }
        nbt.put("Questions", list);
        return nbt;
    }
}
