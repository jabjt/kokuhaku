package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.WalkScene.Answer;
import com.kokuhaku.game.WalkScene.Step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** What gets said over lunch: the girls talking to each other, a question for you, and your chance to ask. */
final class LunchTalk {
    private record Back(String first, String second) {
    }

    private record Ask(String ask, String a, String replyA, String b, String replyB) {
    }

    private static final List<String> HELLO = List.of("Oh good, you found us. Sit, sit.", "We saved you that one. Nobody else wanted it.", "You're late. We started without you.",
            "Is that all you're having?", "There you are. Budge up, everyone.");
    private static final List<String> BACK_AGAIN = List.of("Look who remembered where the hall is. We saved you a seat.", "We've been saving that chair for days, you know.", "Oh. You do eat, then. Sit down.");
    private static final List<Back> BANTER = List.of(
            new Back("Is this the same curry as Monday?", "It's always the same curry. It's just got more confident."),
            new Back("Did you finish the English reading?", "I read the back of the book. That counts."),
            new Back("Mrs. Mori gave me extra rice again.", "She only does that when you look ill."),
            new Back("Someone took my umbrella from the stand.", "Everyone's umbrella is someone else's umbrella."),
            new Back("I'm never getting up that early again.", "You said that yesterday. And the day before."),
            new Back("Are you actually going to eat that?", "...Do you want it?"),
            new Back("Who's got club after school?", "Everyone but me. I've got a very important nap."),
            new Back("Coach made us run the whole field again.", "That's why you're eating like that, then."),
            new Back("My brother says this place had a pool once.", "It still has a pool. That's not what he means, is it?"),
            new Back("The vending machine ate my money again.", "It's the only one in this school getting fed properly."));
    private static final List<Back> UNEASY = List.of(
            new Back("Did you see the empty desk in Room 04?", "Don't. Not while I'm eating."),
            new Back("My mum wants me home before dark now.", "Everybody's mum does."),
            new Back("Someone was standing at the gate last night.", "Someone's always standing at the gate. Eat your lunch."),
            new Back("Do you ever feel like it's too quiet in here?", "It's a room full of people. ...Yeah. I know what you mean."));
    private static final List<Ask> ASKS = List.of(
            new Ask("Be honest. How's the food at your old school?", "Better.", "Everywhere's better. We just don't talk about it.", "Worse.", "You're just being polite. I respect that."),
            new Ask("Have you joined anything yet?", "Not yet.", "Don't leave it too long. People start to notice who's on their own.", "I'm thinking about it.", "That's what everyone says in April."),
            new Ask("Do you always eat that fast?", "I'm hungry.", "Good. Eat. Nobody here eats enough.", "Am I?", "Like someone's going to take it off you."),
            new Ask("Who do you sit with when we're not here?", "Nobody much.", "Well. Now you've got us.", "Whoever's around.", "Careful. That's how people end up with no one."),
            new Ask("Have you been getting enough sleep?", "Yes.", "Your eyes say something else.", "Not really.", "None of us are. It's going round."));
    private static final List<String> BELL = List.of("That's nearly the bell. Finish up.", "Come on, or we'll be late.", "I hate that bell. Same time tomorrow?", "Take your tray back or Mrs. Mori will come and find you.");

    /** One thing said, and who to: a place at the table, or YOU. */
    record Beat(Step step, int to) {
    }

    static final int YOU = -1;

    private LunchTalk() {
    }

    private static Beat toThem(int speaker, int to, String line) {
        return new Beat(new Step(speaker, line, List.of()), speaker == to ? YOU : to);
    }

    private static Beat toYou(Step step) {
        return new Beat(step, YOU);
    }

    private static <T> T pick(List<T> from, Random r) {
        return from.get(r.nextInt(from.size()));
    }

    static List<Beat> script(RunState s, List<Integer> people, Random r) {
        List<Beat> steps = new ArrayList<>();
        int count = people.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(i);
        Collections.shuffle(order, r);
        int a = order.get(0), b = order.get(1 % count), c = order.get(2 % count);
        steps.add(toYou(new Step(a, LunchScene.cameBack() ? pick(BACK_AGAIN, r) : pick(HELLO, r), List.of())));
        Back first = pick(BANTER, r);
        steps.add(toThem(b, c, first.first()));
        steps.add(toThem(c, b, first.second()));
        Ask ask = pick(ASKS, r);
        steps.add(toYou(new Step(a, ask.ask(), List.of(
                new Answer(ask.a(), a, ask.replyA(), 1, null),
                new Answer(ask.b(), a, ask.replyB(), 1, null),
                new Answer("Say nothing. Keep eating.", b, "Leave them alone. They're eating.", 0, null)))));
        Back second = pick(s.day > 4 ? UNEASY : BANTER, r);
        Integer gone = null;
        for (int d : s.dead) gone = d;
        // The place she used to sit, with flowers on it, and nobody looking.
        if (gone != null && r.nextBoolean()) second = new Back("Nobody's sat at " + Roster.get(gone).firstName() + "'s place since.", "...Don't. Don't look at it.");
        steps.add(toThem(c, a, second.first()));
        steps.add(toThem(a, c, second.second()));
        // On the harder difficulties, not everything anyone tells you is so.
        if (s.difficulty.ordinal() >= com.kokuhaku.Difficulty.HARD.ordinal() && r.nextBoolean()) {
            String heard = s.mystery().falseRumour(r);
            steps.add(new Beat(new Step(b, Dialogue.reveal(new com.kokuhaku.game.Mystery.Clue(0, com.kokuhaku.game.Mystery.Kind.TRAIT, heard, b, s.day, -1)), List.of(new Answer("Write that down.", b, "Don't say it came from me.", 0, q -> {
                s.notes.add("Heard at lunch: \"" + heard + "\"");
                s.markDirty();
                RunManager.tell(q, "", "Clue added to your bag");
            }), new Answer("Who told you that?", b, "Everyone's saying it.", 0, null))), YOU));
        }
        steps.add(toYou(yourTurn(s, people, r)));
        steps.add(toThem(b, a, pick(BELL, r)));
        return steps;
    }

    /** Whoever knows something first: this is the best place to find things out. */
    private static Step yourTurn(RunState s, List<Integer> people, Random r) {
        List<Integer> knows = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) {
            int id = people.get(i), index = i;
            boolean holds = s.mystery().clues.stream().anyMatch(c -> c.witness() == id && c.day() <= s.day && !s.found.contains(c.id()));
            (holds ? knows : rest).add(index);
        }
        Collections.shuffle(rest, r);
        knows.addAll(rest);
        int asked = knows.get(0), other = knows.get(1 % knows.size());
        Student askedGirl = Roster.get(people.get(asked)), otherGirl = Roster.get(people.get(other));
        return new Step(asked, "What about you? You've been quiet. Anything you want to know?", List.of(
                new Answer("Noticed anything strange lately, " + askedGirl.firstName() + "?", asked, RunManager.askedAboutStrange(s, askedGirl), 0, q -> RunManager.tellClueFound(q, s, askedGirl)),
                new Answer("Have you heard anything, " + otherGirl.firstName() + "?", other, RunManager.askedAboutStrange(s, otherGirl), 0, q -> RunManager.tellClueFound(q, s, otherGirl)),
                new Answer("Tell me about yourself, " + askedGirl.firstName() + ".", asked, Dialogue.about(askedGirl, s.mystery()), 1, q -> RunManager.learned(q, askedGirl)),
                new Answer("No. I'm just eating.", asked, "Fair enough.", 0, null)));
    }
}
