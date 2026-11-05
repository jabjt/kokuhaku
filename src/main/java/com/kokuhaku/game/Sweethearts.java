package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.WalkScene.Answer;
import com.kokuhaku.game.WalkScene.Step;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Talking to your girlfriend at school, and walking in with her. Nothing you say
 * to her comes round again until you've run out of things to say.
 */
final class Sweethearts {
    private record Line(String say, String reply) {
    }

    private record Walk(String ask, String a, String replyA, String b, String replyB) {
    }

    /** What's on offer each time you stop to talk. */
    private static final int CHOICES = 3, DAYS_FOR_A_FONDNESS = 1;

    private static final List<Line> LINES = List.of(
            new Line("I saved you a seat at lunch.", "You did? Nobody's ever done that. I'll be there. Don't let anyone take it."),
            new Line("How was your last lesson?", "I spent it looking at the back of your head. Very educational."),
            new Line("You look nice today.", "I look exactly the same as yesterday. ...Say it again, though."),
            new Line("Did you eat breakfast?", "Half of it. You're not my mother. ...Thanks for asking."),
            new Line("I was thinking about you in maths.", "That explains your marks."),
            new Line("Want to walk me to my next class?", "It's twenty metres. Yes. Obviously yes."),
            new Line("What are you doing at the weekend?", "Whatever you're doing, apparently."),
            new Line("Have you got my pencil case?", "I do. It's mine now. It smells like you, which is strange, and I'm keeping it."),
            new Line("Can I sit with you?", "I'll move my bag. Don't tell anyone I moved my bag."),
            new Line("I had a weird dream about you.", "Good weird or bad weird? Don't answer. Tell me at lunch."),
            new Line("People keep staring at us.", "Let them. I've been stared at for worse."),
            new Line("What's your favourite thing about me?", "You ask questions and then actually listen. Nobody does that here."),
            new Line("I don't like walking home alone.", "Then don't. I'll wait at the gate."),
            new Line("Are you okay? You seem quiet.", "I'm fine. I just like it when we don't have to say anything."),
            new Line("I brought you something from the vending machine.", "It's warm. How long were you carrying that? ...I'll drink it anyway."),
            new Line("Teach me how to do this homework.", "No. Sit next to me and I'll do it badly, and we'll both fail."),
            new Line("Do you ever think about leaving this town?", "All the time. But I think about it differently since you turned up."),
            new Line("Your hair looks different.", "I tied it the other way. You're the only person who noticed."),
            new Line("I told my parents about you.", "What did you say? No. Don't tell me. I'll only worry about it."),
            new Line("What song are you listening to?", "It's embarrassing. Here. Take one earphone. Don't laugh."),
            new Line("I missed you this morning.", "It's been an hour. ...I missed you too."),
            new Line("Are you coming to the club today?", "If you come and get me. I like being collected."),
            new Line("Can I borrow your notes?", "They're mostly drawings. Some of them are of you. Don't look at those."),
            new Line("You're the only good thing about this place.", "Don't say that so loud. Someone will hear you and take it away."),
            new Line("Do you think we'd have met anywhere else?", "No. I'd have walked straight past you. I'm glad there's only one road."),
            new Line("What's for lunch?", "I made too much. On purpose. Don't make a big thing of it."),
            new Line("Did you hear what they're saying about us?", "I started some of it. Keep up."),
            new Line("I'm tired.", "Put your head down. I'll wake you before the teacher looks."),
            new Line("Thanks for yesterday.", "You don't have to thank me. Just do it again."),
            new Line("Stay safe going home, okay?", "You stay safe. You're the one who gets all the letters."),
            new Line("Tell me something nobody else knows.", "I practised saying your name before I ever said it to you."),
            new Line("Are we being too obvious?", "You're holding my bag. Yes. Keep holding it."),
            new Line("I wrote you something.", "Give it to me later. I want to read it on my own, slowly."),
            new Line("Do you trust me?", "I do. It's very inconvenient."),
            new Line("What do you want to do after we graduate?", "Graduate first. Then ask me again. I'd like you to have to ask me again."),
            new Line("You were laughing in class. What was funny?", "You. You frown when you read. I'll stop looking. I won't, though."),
            new Line("Your hands are cold.", "Then hold onto them. That's how that works."),
            new Line("I got a mark from the teacher.", "Again? Sit up straight, look sorry, and don't argue. I'll help you make it up."),
            new Line("Who else have you told about us?", "Everybody who needed telling. There's less of them than there used to be."),
            new Line("What are you thinking about?", "Nothing. Just that it's nice, for once, not being on my own."));

    private static final List<Walk> WALKS = List.of(
            new Walk("Did you sleep at all? You look like you didn't.", "A bit.", "Liar. Lean on me if you need to.", "Not really.", "Me neither. I was thinking about you. Don't let it go to your head."),
            new Walk("Which side do you want to walk on?", "The inside.", "Good. Then if a car comes it hits me. That's how it works.", "The outside.", "Very gallant. You'd make a terrible bollard."),
            new Walk("Did you think about me last night?", "Yes.", "Good. Same time tonight.", "I was asleep.", "Then I hope you dreamt about me, or I'm going home."),
            new Walk("Hold my hand till the corner?", "Okay.", "Just till the corner. Then you've got a reputation to think of.", "People will see.", "That's the idea."),
            new Walk("Are you going to do something stupid today?", "Probably.", "Tell me first. I'd like to watch.", "No.", "That's what you said last time."),
            new Walk("Would you notice if I changed my hair?", "Instantly.", "We'll see about that.", "Maybe.", "Honest. Awful, but honest."),
            new Walk("What's the first thing you thought when you saw me?", "That you were pretty.", "Wrong. You thought I looked scary. Everyone does.", "I was scared of you.", "There it is. Correct."),
            new Walk("Are you still getting those notes?", "Yes.", "Show me. Whatever it says, you show me. Promise.", "I'd rather not say.", "Fine. But I'll be walking you home."),
            new Walk("If I asked you to skip school, would you?", "Yes.", "Don't. You'd only get in trouble. But thank you.", "No.", "Good. I'd have thought less of you."),
            new Walk("Do I talk too much in the morning?", "Never.", "That's the right answer. Remember it.", "A little.", "I'll stop. ...I won't stop."),
            new Walk("Coffee or tea?", "Coffee.", "We have nothing in common. Keep walking.", "Tea.", "Good. The machine by the gate does it for a hundred and thirty."),
            new Walk("What did you dream about?", "You.", "That's so bad. Say it again.", "Nothing.", "Then I'll have to try harder."),
            new Walk("Do you like the rain?", "I do.", "Then you can carry the umbrella.", "I hate it.", "Then we're staying under this one together."),
            new Walk("Are you embarrassed to be seen with me?", "Never.", "Good. You'd be the first.", "A bit.", "Honestly, same. It's the shoes."),
            new Walk("What's the worst lesson today?", "All of them.", "Then I'll pass you a note in the worst one.", "Maths.", "I'll wave at you through the door."),
            new Walk("Do you think anyone's watching us?", "Everyone is.", "Then smile. It annoys them.", "No.", "I always think someone is. It's only since I met you that I don't mind."),
            new Walk("Would you still like me if I was boring?", "Of course.", "You'd be asleep in a week.", "You could never be.", "Correct. Good. Carry on."),
            new Walk("What should we do after school?", "Anything.", "Anything's the most expensive thing on the menu.", "Stay out of trouble.", "How dull. Fine. Tomorrow, then."),
            new Walk("How long do you think we'll last?", "Forever.", "Don't say that here. Not in this town.", "Let's see.", "Let's. I'd like to."),
            new Walk("Did you lock up properly?", "I did.", "Good. Check it twice tonight. For me.", "I forgot.", "Go back. I'm serious. No, you can't, we're late. Tonight, then. Promise."),
            new Walk("Do you ever miss where you used to live?", "Sometimes.", "I'd like to see it. If we ever get out of here.", "Not any more.", "That's the nicest thing anyone's said to me before nine o'clock."),
            new Walk("Tell me a secret.", "I'm scared.", "I know. So am I. That's why I walk on the outside.", "I haven't got any.", "Everyone in this town's got one."));

    private static final List<String> HELLOS = List.of(
            "You're late. I waited anyway.", "Here. Rice ball. Don't tell anyone I can cook.", "There you are. I've been stood here so long the bus driver waved at me.",
            "Morning. Your collar's wrong. Hold still.", "I was about to go without you. I wasn't. I'd never.", "You took your time. Was the mirror good to you?",
            "I brought an extra umbrella. It's for sharing, so I only brought the one.", "Morning, you. Don't make that face, I've seen it before breakfast now.",
            "I got here early so I could pretend I hadn't.", "Walk on the inside. I mean it. I don't like the road.", "Did you lock your door last night? Promise me you did.",
            "Good morning. I've decided today's going to be a good one, so don't ruin it.", "Look at you, up and walking. I'm proud.", "I saved you the last of my tea. It's cold. It's the thought.");

    private static final List<String> STILL_TALKING = List.of(
            "I like this bit of the walk. It's the only bit where nobody needs anything.", "Tell me when you're tired of me. I'd like some warning.",
            "If anything happens today, you come and find me first. Before anyone.", "You can let go of my sleeve at the gate. Not before.",
            "I keep thinking this is the part where something goes wrong. It hasn't yet.", "Almost there. Quick, look like you've not been enjoying this.");

    private static final Set<Integer> said = new HashSet<>();
    private static final Random RANDOM = new Random();

    private Sweethearts() {
    }

    static void clear() {
        said.clear();
    }

    /** Three things you haven't said to her yet. When you've said them all, you start again. */
    private static List<Integer> fresh() {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < LINES.size(); i++) if (!said.contains(i)) open.add(i);
        if (open.size() < CHOICES) {
            said.clear();
            for (int i = 0; i < LINES.size(); i++) if (!open.contains(i)) open.add(i);
        }
        Collections.shuffle(open, RANDOM);
        return open.subList(0, CHOICES);
    }

    /** Stopping to talk to her at school. */
    static void talk(ServerPlayerEntity p, RunState s, Student who, String opener, Talk.Option... rest) {
        String name = who.firstName();
        List<Talk.Option> options = new ArrayList<>();
        for (int i : fresh()) {
            Line line = LINES.get(i);
            options.add(Talk.option(line.say(), q -> {
                said.add(i);
                if (s.fondness[who.id()] < 5 && s.day - lastFondness >= DAYS_FOR_A_FONDNESS) {
                    lastFondness = s.day;
                    s.fondness[who.id()]++;
                    s.markDirty();
                }
                Talk.say(q, name, line.reply(), Talk.leave("..."));
            }));
        }
        options.addAll(List.of(rest));
        Talk.say(p, name, opener, options.toArray(new Talk.Option[0]));
    }

    private static int lastFondness = 0;

    /** The walk in, with her. A shuffled order that's the same all month, so nothing comes round again for days. */
    static List<Step> walk(RunState s, int speaker) {
        Random order = new Random(s.seed ^ 0x5745L);
        List<Walk> walks = new ArrayList<>(WALKS);
        List<String> hellos = new ArrayList<>(HELLOS), more = new ArrayList<>(STILL_TALKING);
        Collections.shuffle(walks, order);
        Collections.shuffle(hellos, order);
        Collections.shuffle(more, order);
        int d = s.day;
        Walk first = walks.get(d * 2 % walks.size()), second = walks.get((d * 2 + 1) % walks.size());
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(speaker, hellos.get(d % hellos.size()), List.of()));
        steps.add(ask(first, speaker));
        steps.add(new Step(speaker, more.get(d % more.size()), List.of()));
        steps.add(ask(second, speaker));
        return steps;
    }

    private static Step ask(Walk w, int speaker) {
        return new Step(speaker, w.ask(), List.of(
                new Answer(w.a(), speaker, w.replyA(), 1, null),
                new Answer(w.b(), speaker, w.replyB(), 0, null)));
    }
}
