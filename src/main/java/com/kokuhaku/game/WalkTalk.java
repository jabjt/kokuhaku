package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.WalkScene.Answer;
import com.kokuhaku.game.WalkScene.Step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** What gets said on the walk in: questions for you, the odd question for them, and how the town's mood changes. */
final class WalkTalk {
    /** A question and how each answer goes down. */
    private record Chat(String ask, String a, String replyA, int fondA, String b, String replyB, int fondB) {
    }

    private static final int TICKS_PER_BEAT = 190;

    private static final List<Chat> CHATS = List.of(
            new Chat("Did you do the maths homework?", "Yes.", "Of course you did. Can I see it? For checking.", 1, "No.", "Thank god. We can fail together.", 1),
            new Chat("Be honest. Do you actually like it here?", "It's alright.", "Everyone says that for the first month.", 0, "Not really.", "Yeah. Something about this town, right?", 1),
            new Chat("Morning person or night person?", "Morning.", "That's a sickness. You should see someone.", 0, "Night.", "Careful with that around here.", 1),
            new Chat("If you could leave town tomorrow, would you?", "In a heartbeat.", "Nobody ever does, though. Have you noticed?", 0, "No. I'd stay.", "Huh. Most people say yes.", 1),
            new Chat("Do you think the teachers know more than they say?", "Definitely.", "Right? Watch how they stop talking when you walk in.", 1, "No.", "Must be nice, thinking that.", 0),
            new Chat("What do you do after school?", "Work.", "Saving up? Good. Everybody here needs a way out.", 1, "Nothing much.", "Maybe don't tell people that. They might take you up on it.", 0),
            new Chat("Do you lock your door at night?", "Always.", "Good. I started doing that too.", 1, "Why would I?", "...Just do it. Okay? Just do it.", -1),
            new Chat("Have you got a favourite lesson?", "Art.", "You should see what's on the art room wall. Someone keeps drawing the same face.", 1, "Lunch.", "Finally, someone with sense.", 1),
            new Chat("Do you ever get the feeling you're being watched?", "Sometimes.", "Yeah. Don't turn round to check. It only makes it worse.", 1, "No.", "You'd be the only one.", 0),
            new Chat("Would you tell someone if you got a love letter?", "Yes.", "Don't. Whatever you do, don't show it around. That's how it starts.", 0, "No.", "Smart.", 1),
            new Chat("What's the worst thing about this road?", "The cars.", "They don't slow down. Stick to the white lines or you'll end up in the hospital.", 1, "The walk.", "Try it in the rain. Or don't.", 0),
            new Chat("Did you hear the train last night? After the last one?", "Yes.", "So it's not just me. There's no train at that hour.", 1, "No.", "You sleep well, then. Keep doing that.", 0),
            new Chat("Do you believe people can change?", "Yes.", "I hope you're right about that one.", 1, "No.", "Same. Sorry. Same.", 0),
            new Chat("Is it me, or are there fewer of us than last term?", "It's you.", "Yeah. Maybe it's me.", -1, "I noticed that too.", "Nobody talks about it. Have you seen how nobody talks about it?", 2),
            new Chat("Do you have anyone to eat lunch with?", "Yes.", "Good. Eating alone's how they notice you.", 0, "Not really.", "Sit with us, then. Safety in numbers.", 2),
            new Chat("If someone was in love with you, would you want to know?", "Of course.", "Everyone says that. Until they know.", 0, "I'd rather not.", "Yeah. Too late to choose, probably.", 1)
    );

    private static final List<String> RUMOURS = List.of(
            "They say a girl from this school went missing three years ago. They took her name off the honour board.",
            "Someone said if you find a note in your locker, you're not supposed to answer it.",
            "There's a room on the top floor of the hospital they don't let anyone into. All the old files.",
            "My sister was here a few years ago. She won't tell me why she stopped talking to her best friend.",
            "The last train doesn't stop at North. Nobody ever gets on. So why does it slow down?",
            "There are marks scratched into the rocks down past the beach. Haruka used to go and sit by them.",
            "The library's got a book with a page cut out. I always wonder who'd bother.",
            "The darkroom's meant to be locked. There's always something drying in it.",
            "I found a bit of an old diary on the beach once. I threw it back. I wish I hadn't read it.",
            "Next time you're in a crowd, watch their heads. Everyone looks where they're going. Almost everyone.",
            "If you see someone standing a long way off after school, don't look away. They say it only moves when you do.",
            "My cousin left her door unlocked once. She found a flower by her bed. She moved away that summer.",
            "Have you noticed how few people are on the streets now? I asked my dad. He told me to stop asking.");

    private static final List<String> WEEK_ONE = List.of("It's nice having someone new around. Things have been a bit strange.", "You'll get used to it here. Everyone does.", "If anyone asks you to go somewhere alone, say no. I'm serious. It's just a thing.");
    private static final List<String> WEEK_TWO = List.of("Have you noticed the teachers walk in pairs now?", "My mum won't let me out after dark any more. She won't say why.", "People keep asking what I know. I don't know anything.");
    private static final List<String> WEEK_THREE = List.of("I don't sleep much now. Do you?", "I counted the desks. I wish I hadn't.", "It's somebody we know. You know that, right? It's somebody who says good morning.");
    private static final List<String> WEEK_FOUR = List.of("Only a few days of term left. We'll be fine. We'll be fine.", "Whatever you're going to do, do it soon.", "Someone's been walking behind us since the corner. Don't look.");
    private static final List<String> AFTER_DEATH = List.of("%s's desk was empty again. They've taken her name off the register already.", "I keep thinking I'll see %s at the gate.", "Nobody's said what happened to %s. Nobody's going to.");

    private WalkTalk() {
    }

    static List<Step> script(RunState s, List<Integer> people, int ticks) {
        Random r = new Random(s.seed ^ s.day * 65537L);
        if (Living.active(s)) return withHer(s, ticks, r);
        if (s.day == 1 && people.get(0) == Roster.GIRLS) return firstDay();
        // Your girlfriend always has her own things to say to you.
        if (people.contains(s.dating)) return Sweethearts.walk(s, people.indexOf(s.dating));
        List<Step> steps = new ArrayList<>();
        int beats = Math.max(3, Math.min(8, ticks / TICKS_PER_BEAT));
        boolean two = people.size() > 1;
        List<Chat> chats = new ArrayList<>(CHATS);
        Collections.shuffle(chats, r);

        steps.add(new Step(0, opener(Roster.get(people.get(0)), r), List.of()));
        Integer dead = null;
        for (int d : s.dead) dead = d;
        if (dead != null && r.nextBoolean()) steps.add(new Step(two ? 1 : 0, String.format(pick(AFTER_DEATH, r), Roster.get(dead).firstName()), List.of()));
        else steps.add(new Step(two ? 1 : 0, mood(s, r), List.of()));

        Chat first = chats.get(0);
        steps.add(ask(0, first, 0, two));
        if (two) {
            steps.add(new Step(1, reaction(Roster.get(people.get(1)).personality(), r), List.of()));
            steps.add(ask(1, chats.get(1), 1, false));
        }
        steps.add(new Step(two ? 1 : 0, pick(RUMOURS, r), List.of()));
        steps.add(yourTurn(s, people, r));
        if (steps.size() > beats + 1) return new ArrayList<>(steps.subList(steps.size() - beats - 1, steps.size()));
        return steps;
    }

    private static Step ask(int speaker, Chat c, int replier, boolean secondChimes) {
        return new Step(speaker, c.ask(), List.of(
                new Answer(c.a(), replier, c.replyA(), c.fondA(), null),
                new Answer(c.b(), replier, c.replyB(), c.fondB(), null),
                new Answer("Say nothing.", replier, "...Right. Not a morning person.", 0, null)));
    }

    /** You get to ask them something: the walk in is the best time to find things out. */
    private static Step yourTurn(RunState s, List<Integer> people, Random r) {
        int who = people.get(r.nextInt(people.size()));
        int speaker = people.indexOf(who);
        Student st = Roster.get(who);
        String name = st.firstName();
        return new Step(speaker, "What about you? Anything you want to know? We've got a minute.", List.of(
                new Answer("Tell me about yourself, " + name + ".", speaker, Dialogue.about(st, s.mystery()), 1, q -> RunManager.learned(q, st)),
                new Answer("Noticed anything strange lately?", speaker, RunManager.askedAboutStrange(s, st), 0, q -> RunManager.tellClueFound(q, s, st)),
                new Answer("No, nothing.", speaker, "Suit yourself.", 0, null)));
    }

    /** Your first morning, and the only person who'll ever explain anything to you. */
    private static List<Step> firstDay() {
        return List.of(
                new Step(0, "You're the new one from up the road, right? I'm Haruto. Come on, I'll walk you in. It's easy to get lost.", List.of()),
                new Step(0, "Nervous?", List.of(
                        new Answer("A bit.", 0, "Don't be. It's only school. Mostly.", 0, null),
                        new Answer("Not really.", 0, "That's the spirit. Wrong, but the spirit.", 0, null))),
                new Step(0, "Right, how it works. Press T for your diary: it's got every lesson and which room. Be in the room when the bell goes, or you're marked absent.", List.of()),
                new Step(0, "Too many absences or wrong answers and they'll expel you. It's happened.", List.of()),
                new Step(0, "People will talk to you if you right-click them. Ask them about themselves and it all goes in your bag. Press E to look through it.", List.of()),
                new Step(0, "Your bag's got every girl's name in it, under Suspects. I don't know why I said suspects. Girls. It's got the girls in it.", List.of()),
                new Step(0, "Look, I'm not supposed to say this. Check your shoe locker. Entrance hall, right where we're headed. People leave things.", List.of()),
                new Step(0, "If it's what I think it is, don't throw it away. Ask around. Find out who it was before she does anything. You've got thirty days.", List.of()),
                new Step(0, "And get home before curfew, sleep in your own bed, and lock your front door. Hold your key and right-click it. Every night. I mean it.", List.of()),
                new Step(0, "Oh, and cross at the white lines. Cars don't stop for anybody round here. See you in class.", List.of()));
    }

    /** She walks you in herself now. */
    private static List<Step> withHer(RunState s, int ticks, Random r) {
        Personality manner = Roster.get(s.livingWith).personality();
        String[] moods = Living.moods(manner);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(0, "Did you sleep well? I watched. You did.", List.of()));
        steps.add(new Step(0, "Who are you going to talk to today?", List.of(
                new Answer("Only you.", 0, "Good answer. You're getting better at it.", 2, null),
                new Answer("Nobody.", 0, "Nobody. Mm. I'll be checking.", 0, null),
                new Answer("Say nothing.", 0, "It wasn't a hard question.", -1, null))));
        steps.add(new Step(0, moods[r.nextInt(moods.length)].replace("\"", ""), List.of()));
        if (s.devoted) {
            steps.add(new Step(0, "Do you see how they all look at the ground when we go past? I asked them to.", List.of()));
            steps.add(new Step(0, "Is there anybody here you'd like to speak to?", List.of(
                    new Answer("No.", 0, "No. There isn't, is there.", 1, null),
                    new Answer("Say nothing.", 0, "Good.", 0, null))));
            steps.add(new Step(0, "Nobody's coming, you know. I want you to stop hoping. It's making you tired.", List.of()));
        }
        steps.add(new Step(0, "Day " + s.day + ". Only " + (Schedule.DAYS - s.day) + " left, and then you'll never have to think about leaving again.", List.of()));
        return steps;
    }

    private static String opener(Student st, Random r) {
        return switch (st.personality()) {
            case CHEERFUL -> pick(List.of("Hey! Wait up! Walk with me, it's too nice to be alone!", "Morning! You look like you need cheering up. Lucky for you."), r);
            case SHY -> pick(List.of("U-um. Can I walk with you? Just to the gate.", "Oh. It's you. Is it okay if I... thanks."), r);
            case COLD -> pick(List.of("You're going my way. Don't make it weird.", "Walk faster or walk behind me."), r);
            case GOSSIP -> pick(List.of("There you are. I've got so much to tell you. Keep walking, act normal.", "Perfect, a witness. I mean a friend. Morning."), r);
            case POLITE -> pick(List.of("Good morning. May I walk with you?", "Oh, good morning. It's safer to go in together, I think."), r);
            case SPORTY -> pick(List.of("Yo! I'm cooling down anyway. Keep up.", "You walk so slow. Fine. Today's a rest day."), r);
        };
    }

    private static String reaction(Personality p, Random r) {
        return switch (p) {
            case CHEERFUL -> "Hee. You two are funny.";
            case SHY -> "...Oh. Um. I think so too.";
            case COLD -> "Are you always like this?";
            case GOSSIP -> "Oh, I'm remembering that for later.";
            case POLITE -> "That's an interesting way to look at it.";
            case SPORTY -> "Can we walk and talk any faster?";
        };
    }

    private static String mood(RunState s, Random r) {
        return pick(s.day <= 3 ? WEEK_ONE : s.day <= 7 ? WEEK_TWO : s.day <= 10 ? WEEK_THREE : WEEK_FOUR, r);
    }

    private static String pick(List<String> lines, Random r) {
        return lines.get(r.nextInt(lines.size()));
    }
}
