package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** What students say. The yandere answers about herself truthfully; she just sounds wrong on easier modes. */
public final class Dialogue {
    public static final List<String> OPTIONS = List.of("Tell me about yourself.", "Noticed anything strange lately?", "How are you doing?", "Who do you trust around here?", "Goodbye.");
    public static final int ABOUT = 0, STRANGE = 1, CHAT = 2, TRUST = 3;

    private static final Map<Personality, List<String>> GREETINGS = Map.of(
            Personality.CHEERFUL, List.of("Hiii! What's up?", "Oh, it's you! Hey!", "Morning! Or afternoon? Whatever, hi!", "You look like you need a snack. Hi.", "There you are! I was so bored.", "Hey hey! Sit with us at lunch sometime?"),
            Personality.SHY, List.of("O-oh... hi.", "...Um. Hello.", "You're talking to me? ...Hi.", "S-sorry, I didn't see you there.", "Oh. It's you. Um, hi.", "...Did I do something wrong?"),
            Personality.COLD, List.of("What.", "Make it quick.", "...Yes?", "I'm busy.", "You again.", "Did you need something, or are you just standing there?"),
            Personality.GOSSIP, List.of("Oh, perfect timing. I've got news.", "Hey! Did you hear? No? Come here.", "You look like someone who needs to hear something.", "Don't tell anyone I told you this, okay? Well. Anything yet.", "Finally, someone interesting.", "I was just talking about you. Kidding. Mostly."),
            Personality.POLITE, List.of("Hello. Is everything alright?", "Good day. Can I help with something?", "Oh, hello. Do you need something?", "Pardon me, were you looking for me?", "It's nice to see you.", "Good to see you. Have you eaten?"),
            Personality.SPORTY, List.of("Yo! Wanna race later?", "Hey! Just got back from practice.", "Sup. You look like you need some exercise.", "Can't stop long, coach'll kill me.", "Hey! You run? You should run.", "Yo. Bit out of breath. One sec.")
    );

    private static final Map<Personality, List<String>> SMALL_TALK = Map.of(
            Personality.CHEERFUL, List.of("Strange? Nope! Everything's great!", "The cafeteria ran out of melon bread. That's strange AND tragic.", "Only that it's somehow already this late in the term.", "Someone keeps leaving flowers by the gate. Cute, right?"),
            Personality.SHY, List.of("N-no... I don't really notice things. Sorry.", "I... try not to look at people too much.", "Sometimes it feels like someone's behind me. It's probably nothing.", "I stayed in the library. It's quiet there."),
            Personality.COLD, List.of("No.", "Nothing I'd tell you.", "People asking questions. That's strange.", "Mind your own business. It's safer."),
            Personality.GOSSIP, List.of("Not about that. But did you know the art teacher's getting married?", "Nothing yet. But I'm listening. I'm always listening.", "Everyone's being weird lately. Nobody's saying why.", "Somebody cried in the second floor bathroom. I'll find out who."),
            Personality.POLITE, List.of("I'm afraid not. I'll tell you if I see anything.", "Nothing comes to mind, sorry.", "Only that the corridor lights flicker in the evenings.", "I'd rather not spread rumours, if that's alright."),
            Personality.SPORTY, List.of("Only that I keep beating everyone at practice!", "Nah. I'm too busy running to notice stuff.", "Saw someone at the track at night once. Weird time to train.", "Someone's been taking the bats out of the equipment shed.")
    );

    private static final Map<Personality, List<String>> CHAT_LINES = Map.of(
            Personality.CHEERFUL, List.of("Great! I got a new charm for my bag!", "So good! Exams can't hurt me if I don't look at them.", "I'm thinking about ramen. Always.", "Today's a good day. I've decided."),
            Personality.SHY, List.of("I'm okay. Thank you for asking. Nobody really asks.", "A bit tired. I read all night.", "F-fine. Um. How are you?", "I had a nice lunch. By myself. It was nice."),
            Personality.COLD, List.of("Fine.", "Why do you care?", "Tired of being asked.", "Better when people leave me alone."),
            Personality.GOSSIP, List.of("Exhausted. Knowing everything is hard work.", "Fine, but ask me who isn't.", "I'd be better if people told me more.", "Terrible. Nobody's done anything interesting all day."),
            Personality.POLITE, List.of("Very well, thank you. And you?", "I can't complain. How are your studies?", "A little busy with club duties, but well.", "Well, thanks. Do take care walking home."),
            Personality.SPORTY, List.of("Pumped! Regional qualifiers soon.", "My legs are dead. It's great.", "Starving. Training does that.", "Good! Pulled a muscle, but good.")
    );

    private static final Map<Personality, List<String>> MORE_CHAT = Map.of(
            Personality.CHEERFUL, List.of("Honestly? I laugh so nobody asks.", "I'm fine! Everyone keeps asking if I'm fine. I'm fine!", "I made a list of nice things today. It's a short list, but it's a list."),
            Personality.SHY, List.of("I keep a list of who says hello to me. You're on it.", "I dream about the school a lot. There's always one more floor.", "S-sorry. I forgot what you asked. I was listening to something."),
            Personality.COLD, List.of("I sleep with the light on. Tell anyone and I'll end you.", "Nobody's fine. That's the whole trick.", "Ask me again when there are more of us left."),
            Personality.GOSSIP, List.of("I've stopped writing things down. People kept asking to see it.", "There's a rumour about me, and I didn't start it. That's new.", "Three people have asked me about you this week. Guess who."),
            Personality.POLITE, List.of("I'm keeping up appearances, thank you for asking.", "My mother says we should move away. My father won't discuss it.", "I've been told it's best not to ask questions. I do hope you aren't."),
            Personality.SPORTY, List.of("Coach cancelled night practice. Wouldn't say why.", "I run faster when I think someone's behind me. Lately I've been really fast.", "Good, good. Shin splints. Terror. The usual.")
    );

    private static final Map<Personality, List<String>> TRUST_LINES = Map.of(
            Personality.CHEERFUL, List.of("Everybody! Hm. Well. %s, I suppose. She's never once asked me for anything.", "I'd have said everyone, last month. Now? %s, maybe?"),
            Personality.SHY, List.of("...%s was kind to me once. I think about it a lot.", "I don't, really. But %s doesn't scare me."),
            Personality.COLD, List.of("No one. If you made me pick, %s, because she's too lazy to lie.", "Trust is for people who haven't been paying attention."),
            Personality.GOSSIP, List.of("Not %s, I can tell you that. She's got a whole other face. But then, who hasn't?", "Trust? Oh, you're adorable. Ask %s what she does on Thursdays."),
            Personality.POLITE, List.of("I wouldn't like to say. %s has always been very correct with me.", "%s is from a good family, for whatever that's worth nowadays."),
            Personality.SPORTY, List.of("%s. You can tell a lot from how someone loses.", "My team. And %s, I guess, she doesn't flinch.")
    );

    private static final List<String> MORNING = List.of("Did you do the homework? ...Me neither.", "Too early for this.", "I nearly missed the train.");
    private static final List<String> EVENING = List.of("Shouldn't you be heading home?", "It gets quiet around here after dark.", "Don't stay out too late, okay?", "My mum says nobody should walk alone lately.");
    private static final List<String> AFTER_DEATH = List.of("Did you hear about %s? I can't stop thinking about it.", "Nobody's saying anything about %s. It's like the teachers want us to forget.", "%s sat right there. Right there. And now...", "They said it was an accident. %s. Do you believe that?");
    private static final List<String> WAIT_UP = List.of("Wait up!", "Hey! Hold on, I'll walk with you!", "Wait, wait! You're fast this morning!", "Oi! Don't pretend you didn't see me!", "Hey, wait for me!");
    private static final List<String> WALKING = List.of("Thanks. I hate walking this bit alone.", "Did you sleep at all? You look terrible.", "If we're late again we're dead.", "Did you finish the maths? Please say yes.", "I swear this road gets longer every day.", "Have you noticed how quiet everyone's been?");

    private static final List<String> CLUE_LEADS = List.of("Actually... yeah. There's something.", "I wasn't going to say anything, but...", "Okay, don't freak out, but...", "Since you asked...", "You didn't hear this from me.");
    private static final List<String> HERRING_LEADS = List.of("I don't know if it means anything, but...", "This is probably nothing, but...", "Promise you won't repeat this...");

    private static final List<String> HER_GREETING_EASY = List.of("You came to see me! I knew you would. I always know.", "There you are. I was watching the door for you.", "You took a different way to school today. Is something wrong?");
    private static final List<String> HER_GREETING_MEDIUM = List.of("Oh! Hi! I was just thinking about you. Haha. Kidding.", "You look tired. Are you sleeping okay?", "You were talking to someone at lunch. Who was that?");
    private static final List<String> HER_STRANGE_EASY = List.of("Strange? ...Who have you been talking to? You don't need to talk to them.", "Why are you asking everyone questions? You can just ask me. Only me.");
    private static final List<String> HER_STRANGE_MEDIUM = List.of("No. Everything's perfect now that you're here.", "Strange? No. Everything's going exactly how it should.");

    private static final Random RANDOM = new Random();

    private Dialogue() {
    }

    public static String greeting(Student s, boolean isHer, Difficulty d, int minute, String lastDead) {
        if (isHer && d == Difficulty.EASY) return pick(HER_GREETING_EASY);
        if (isHer && d == Difficulty.MEDIUM) return pick(HER_GREETING_MEDIUM);
        if (lastDead != null && RANDOM.nextInt(3) == 0) return String.format(pick(AFTER_DEATH), lastDead);
        List<String> pool = new ArrayList<>(GREETINGS.get(s.personality()));
        if (minute < Schedule.HOMEROOM) pool.addAll(MORNING);
        if (minute >= Schedule.EVENING) pool.addAll(EVENING);
        return pick(pool);
    }

    public static String about(Student s, Mystery m) {
        String opener = switch (s.personality()) {
            case CHEERFUL -> "Me? Okay!";
            case SHY -> "M-me? Um...";
            case COLD -> "Fine.";
            case GOSSIP -> "Finally, someone asks about ME.";
            case POLITE -> "Of course.";
            case SPORTY -> "Quick version:";
        };
        return opener + " I'm in class " + s.homeroom().label() + ". I'm in the " + m.clubs[s.id()].label().toLowerCase()
                + " club, and I live " + m.districts[s.id()].livesPhrase + ".";
    }

    public static String smallTalk(Student s, boolean isHer, Difficulty d) {
        if (isHer && d == Difficulty.EASY) return pick(HER_STRANGE_EASY);
        if (isHer && d == Difficulty.MEDIUM) return pick(HER_STRANGE_MEDIUM);
        return pick(SMALL_TALK.get(s.personality()));
    }

    public static String chat(Student s) {
        List<String> pool = new ArrayList<>(CHAT_LINES.get(s.personality()));
        pool.addAll(MORE_CHAT.get(s.personality()));
        return pick(pool);
    }

    /** Who they'd vouch for, which says as much about them as about her. */
    public static String trust(Student s, String someone) {
        return String.format(pick(TRUST_LINES.get(s.personality())), someone);
    }

    /** What fondness adds to a greeting. */
    public static String mood(Student s, int fondness) {
        if (!s.female() || Math.abs(fondness) < 2) return "";
        return fondness > 0 ? " She looks pleased to see you." : " She doesn't really look at you.";
    }

    /** What strangers say, which gets worse as the month goes on. */
    public static String passerBy(int day) {
        List<String> lines = new ArrayList<>(List.of("\"Not now, I'm late.\"", "\"Sorry, I don't know anything about that.\"", "\"You should get home before dark.\"", "\"Do I know you?\"", "\"Mind yourself.\"", "They walk past without looking at you.",
                "\"Use the crossing, love. They don't stop.\"", "\"You're from the school? Hm.\" They hurry on.", "\"Hospital's over the main road, if you're looking for it. You look like you might be.\""));
        if (day > 5) lines.addAll(List.of("\"Another one, was it? Terrible. Terrible.\"", "\"My daughter went to that school. Went.\"", "\"Don't tell me anything. I don't want to know.\""));
        if (day > 9) lines.addAll(List.of("They cross the road when they see your uniform.", "\"You still here? I'd have thought you'd be gone by now.\""));
        return pick(lines);
    }

    public static String waitUp() {
        return pick(WAIT_UP);
    }

    public static String walking() {
        return pick(WALKING);
    }

    public static String reveal(Mystery.Clue clue) {
        List<String> leads = clue.kind() == Mystery.Kind.HERRING ? HERRING_LEADS : CLUE_LEADS;
        return pick(leads) + "\n\n\"" + clue.text() + "\"";
    }

    public static String notYet() {
        return pick(List.of("Hmm... no. Not yet, anyway.", "Nothing I can put my finger on.", "Ask me another time. I'm not sure yet."));
    }

    public static String alreadyTold() {
        return "I already told you everything I know.";
    }

    private static String pick(List<String> lines) {
        return lines.get(RANDOM.nextInt(lines.size()));
    }
}
