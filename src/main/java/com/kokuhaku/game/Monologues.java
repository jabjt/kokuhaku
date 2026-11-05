package com.kokuhaku.game;

import java.util.List;
import java.util.Map;

/**
 * What comes out of her when the act drops. It's never hate. Every one of them
 * loves you more than anyone has ever loved anything, and that's the horror.
 * She'll tell you why. She'll never tell you how. Single player only: her
 * manner would give her away to a party, so a party gets the cold one.
 */
public final class Monologues {
    public static final int PLEASED = 1, HURT = 2, CLOSER = 3;

    /** The same things to ask, whoever she is. Her answers are in the Voice, in this order. */
    private static final String[] ASKS = {"Where am I?", "Why me?", "How long have you been watching me?", "How did you do all this?",
            "Where is everyone?", "Is anyone coming?", "Are you going to hurt me?", "Let me go."};
    private static final int[] ASK_MOODS = {PLEASED, PLEASED, PLEASED, CLOSER, PLEASED, CLOSER, HURT, CLOSER};
    private static final int WHY_ME = 1, IS_ANYONE_COMING = 5;
    /** Her answers to her own question: you tried, you didn't know, you pushed back. */
    private static final int[] ANSWER_MOODS = {PLEASED, CLOSER, HURT};
    private static final String HE_WRITES = "The one who writes to you? He can't get in.|He's tried. I let him keep the pen.";
    private static final String HE_SAID = "Someone said your name to me, once. He was very frightened.|I'd never heard anything so lovely.";
    public static final String LAST_TIME = "You said that last time, too.";

    /** Something you can say, and all of what she says back, a line at a time. */
    public record Exchange(String ask, int mood, List<String> reply) {
    }

    /** Everything one of them says to you in the room. */
    public record Voice(List<String> replies, String asks, List<String> answers, List<String> responses, List<String> refusals,
                        String stay, List<String> struggle, List<String> behind, String lookAtMe, String silence, String oh, String iSee,
                        String shh, String letGo) {
        public List<Exchange> questions(int played) {
            java.util.ArrayList<Exchange> all = new java.util.ArrayList<>();
            for (int i = 0; i < ASKS.length; i++) {
                String reply = i == IS_ANYONE_COMING && played >= Story.CARD_FROM ? HE_WRITES : i == WHY_ME && played >= Story.LETTER_FROM ? HE_SAID : replies.get(i);
                all.add(new Exchange(ASKS[i], ASK_MOODS[i], List.of(reply.split("\\|"))));
            }
            return all;
        }

        public List<Exchange> herQuestion() {
            java.util.ArrayList<Exchange> all = new java.util.ArrayList<>();
            for (int i = 0; i < answers.size(); i++) all.add(new Exchange(answers.get(i), ANSWER_MOODS[i], List.of(responses.get(i).split("\\|"))));
            return all;
        }
    }

    private static final Voice CHEERFUL = new Voice(List.of(
            "Home! Our home! I picked everything!|The window doesn't open, but the view's really nice!",
            "Because you said good morning back!|Nobody says it back. You did. So that's that!",
            "Since the first day! I wrote it all down! Do you want to hear your whole year?",
            "Silly! You don't ask a magician that!|You just clap!",
            "It's just us! Isn't that the best? I don't have to share you anymore!",
            "Nope! I told everyone you moved away!|They were sad for about a day!",
            "Hurt you? Me?|...Why would you say that? Why would you even think that?",
            "Hahaha! You're so funny!|...You are being funny, aren't you?"),
            "Quick, quick! What's my favourite colour? You know this one!",
            List.of("Pink?", "I don't know.", "Why would I know that?"),
            List.of("Close! You tried! You actually tried!", "That's okay! You've got forever to learn!", "Because I know yours.|I know all of yours."),
            List.of("Aww! Say it again!|...No. You've got your lying face on. I know all your faces!", "Nicer!", "Nicer than that!"),
            "Really? REALLY? I knew it! I knew you'd love it here!",
            List.of("Careful! You'll get marks! You'll ruin you!", "That's my best knot, you know!", "Stop it stop it stop it. Hahaha. Stop."),
            List.of("Guess where I am!", "Your hair does a little swirl back here! I'm keeping it!", "Boo!"),
            "Hey! Eyes on me! Eyes on me!",
            "You're not playing properly.",
            "Oh.", "That's okay! That's okay. I can fix that.", "All better!",
            "Okay, but you will come back to me, won't you?");

    private static final Voice SHY = new Voice(List.of(
            "...it's a room I made. For when you came.|I practised saying that.",
            "You picked up my pencil.|You don't remember. You said \"here you go\". I still have it. The pencil.",
            "I don't know. I don't... count it like that.|There wasn't a before.",
            "...please don't make me talk about that.|It isn't nice, and you'd look at me differently.",
            "...I don't like it when there's a lot of people.",
            "...no.|Sorry. I'm sorry. I checked.",
            "...you're scared of me.|Nobody's ever been scared of me. I don't know what to do with my hands.",
            "...if I let you go, you won't look at me again.|You didn't look at me for two years."),
            "...what colour are my eyes? Without looking.",
            List.of("I think... brown?", "I never noticed.", "I'm looking now."),
            List.of("...you guessed. That's okay. You guessed.", "...I know. Nobody does.", "...oh.|Oh. Don't. Don't stop."),
            List.of("...you don't have to say that.", "...please stop. It's worse when you try.", "...I wish you meant it."),
            "...you will? You'll stay? I'll be so quiet. You won't even know I'm here.",
            List.of("...please don't. They'll hurt.", "...I'm sorry. I did them tight.", "...you're frightening me."),
            List.of("...sorry. I just like it here.", "...you can't see me. That's easier.", "...it's okay. I'm only standing."),
            "...please look at me. It took so long.",
            "...you can stop pretending I'm not here.",
            "...oh.", "...I knew. I think I always knew.", "...sorry.",
            "...okay. But you'll come back. Won't you?");

    private static final Voice COLD = new Voice(List.of(
            "Somewhere you will stop asking that.",
            "You held a door for me. You didn't look up.|It was the first thing anyone had done without wanting something.",
            "Long enough that the question doesn't matter.",
            "No.",
            "Elsewhere.",
            "No one is looking for you. I would know.",
            "That depends entirely on you.",
            "No."),
            "Tell me what you had for breakfast on the ninth of March.",
            List.of("I can't remember.", "How would I know that?", "You tell me."),
            List.of("I can.", "Toast. You left half.", "Toast. You left half.|You're learning."),
            List.of("You don't lie well.", "I said no.", "Stop."),
            "Yes. You will.",
            List.of("That won't work.", "I don't make mistakes with knots.", "Enough."),
            List.of("Don't turn around. You can't, but don't.", "I can see your pulse from here.", "Sit still."),
            "Look at me when I'm here.",
            "Very well. I'll answer for you.",
            "I see.", "That's a pity.", "Quiet now.",
            "Go. I know where you live.");

    private static final Voice GOSSIP = new Voice(List.of(
            "Everyone's dying to know! Well. Everyone would be, if I told them.",
            "Everybody at school has a story. You were the only one I couldn't finish.|So I decided to be in it.",
            "Oh, honey. Who do you think starts all the stories about you?",
            "A girl doesn't reveal her sources.",
            "You haven't heard? Oh, you've missed so much.|I'll tell you later. It's a long one.",
            "I told them you ran off. By lunch it was a girl from the next town.|They added that part themselves. People are wonderful.",
            "Ooh, who told you that? No, really. I need to know who's saying that.",
            "And go where? Everyone already knows what happened to you. I wrote it."),
            "Go on then, your turn. Tell me a secret. Just one.",
            List.of("I was scared of you.", "I don't have any.", "I hate you."),
            List.of("Ooh. That's a good one. That's going in.", "Everyone has one, sweetie. I've got yours in a folder.", "Oh, that's nothing. Everyone says that.|You should hear what they say about you."),
            List.of("Oh, that's good! Can I quote you?", "That's what they all say.", "Nobody's going to believe that one, sweetie."),
            "Oh, this is the best one yet. Wait until they hear. Nobody's ever going to hear.",
            List.of("Pull as hard as you like, nobody's listening. I'd know.", "Everybody says those ropes are very good.", "Don't make me tell people about this."),
            List.of("Psst. Want to hear something nobody knows?", "I'm not here. That's what I'll tell them, anyway.", "You've got a very quiet heart, for someone so scared."),
            "Don't you want to hear the end of this?",
            "Fine. I'll tell it how I like.",
            "Well.", "That's not how I'd written it.", "And that's the whole story.",
            "Go on, then. I'll tell them you came back.");

    private static final Voice POLITE = new Voice(List.of(
            "You're home. I've aired the room for you.",
            "You thanked the lunch lady, the day nobody else did.|I decided then. I hope that's all right.",
            "I'd rather say looking after. Since the spring. I've kept the receipts.",
            "With care. It would be rude to go into detail.",
            "They've been taken care of. Please don't trouble yourself.",
            "I sent a note to the school on your behalf. It was very well received.",
            "Please. What a thing to say, after I've gone to all this trouble.",
            "I'm afraid I can't do that. You haven't had your dinner."),
            "Before we go on, may I ask: is the room to your liking?",
            List.of("It's nice.", "I don't know.", "No."),
            List.of("Thank you. I chose the candles myself.", "You'll settle. Everyone does, in time.", "I see. I'll try harder.|You'll have to give me time. We have plenty."),
            List.of("That's very kind. You needn't.", "Please don't. It's unbecoming.", "I'd rather you didn't."),
            "How lovely. I'll set a second place. I always have.",
            List.of("Please don't. You'll chafe.", "I did ask you to sit comfortably.", "That's enough, thank you."),
            List.of("Excuse me. I'm only straightening your collar.", "I counted your breaths while you were resting. Four thousand, one hundred and six.", "Pardon me. You're shaking."),
            "It's polite to look at someone when they're in the room.",
            "No? I'll take that as an answer, then.",
            "I beg your pardon?", "No, I heard you. Excuse me a moment.", "There we are.",
            "Of course. Mind the step. You will come back, won't you?");

    private static final Voice SPORTY = new Voice(List.of(
            "Finish line. You made it. Took you long enough.",
            "You were the only one who didn't clap when I won.|You just looked out the window. I've never wanted to beat anything so badly.",
            "Every morning. Five K out of my way, just to see your light come on.",
            "Training. Don't ask. Nobody asks how you got fast.",
            "Lapped. All of them.",
            "Not fast enough, if they are.",
            "Depends. You going to make me chase you?",
            "Hah! Yeah? Go on, then. Get up. I'll give you a head start."),
            "Be honest. Did you ever hear me behind you?",
            List.of("Sometimes.", "I don't know.", "Never."),
            List.of("Liar. That's okay. Nice try.", "Means I'm good.", "Never. That's a personal best."),
            List.of("Hah. Try again.", "Weak. Again.", "You're out of breath, you know that?"),
            "Hah! Yes! Best day of my life! No contest!",
            List.of("That's it. Warm up.", "You've got no grip. We'll work on it.", "Save your strength. I want it to count."),
            List.of("Heh. Didn't hear me, did you?", "Your heart rate's way up. Not bad.", "Tag."),
            "Hey. Head up. Eyes on me.",
            "Time's up.",
            "Huh.", "Okay. Okay, sure. Round two.", "Good game.",
            "Okay. Go. I'll give you ten seconds. You'll come back, right?");

    private static final Map<String, Voice> VOICES = Map.of("CHEERFUL", CHEERFUL, "SHY", SHY, "COLD", COLD, "GOSSIP", GOSSIP, "POLITE", POLITE, "SPORTY", SPORTY);

    private static final Map<String, List<String>> UNMASKED = Map.of(
            "CHEERFUL", List.of(
                    "...Eh?",
                    "Hahaha! You found me! You looked for me! Nobody's ever looked for me!",
                    "Six hundred and twelve good mornings. I kept every one. Do you want to hear them? I do all the voices!",
                    "I know your shoe size. I know what you're allergic to. I know which stair you skip.",
                    "So it's okay! You don't have to be scared! Everything I did, I did for you! All of it!",
                    "You're not smiling. Why aren't you smiling? I'm smiling.",
                    "I LOVE YOU. I LOVE YOU I LOVE YOU I LOVE YOU. SAY IT BACK."),
            "SHY", List.of(
                    "...",
                    "...you noticed me.",
                    "Nobody notices me. I could stand at the end of your street all night and nobody would know.",
                    "I did. Forty-one nights. I never did anything. I only wanted to be near where you were.",
                    "You said my name just now. I've waited my whole life. Could you say it again? Please?",
                    "I'm so sorry about everything. I would do it all again. I'm so sorry.",
                    "I'LL BE SO QUIET. YOU WON'T KNOW I'M THERE. I'LL ALWAYS BE THERE."),
            "COLD", List.of(
                    "So you know.",
                    "Nobody at this school has ever heard me raise my voice. Nobody will.",
                    "You held a door for me, once. I have not thought about anything else since.",
                    "I have never said this to anyone. There is a shape in me, and it is yours.",
                    "The others were in the way, so they aren't anymore. It wasn't cruelty. It was tidying.",
                    "Run, if you like. Tell them. Hate me. It changes nothing.",
                    "I will be there every day until one of us is dead. And then I'll follow you."),
            "GOSSIP", List.of(
                    "Oh! Oh, you've no idea how long I've wanted you to say that.",
                    "I've told everyone everything about everyone. Do you know the one thing I never told a soul? You.",
                    "Every rumour was so they'd keep away from you. The girl behind the gym? Me. The photo? Me. I cleared you a space.",
                    "I know who you like and who you lied to and what you said about them, and I adore all of it.",
                    "You're the only secret I ever kept. Do you know what that costs someone like me?",
                    "By morning the whole town will say we're together. I've already started it.",
                    "And it'll be true. Everything I say comes true."),
            "POLITE", List.of(
                    "I see. Thank you for telling me.",
                    "Forgive me. I'd like to sit down. I've been holding this in for a very long time.",
                    "Your lunches. Your notes. Your hospital bill. Your friends, who weren't good enough. I saw to all of it.",
                    "That's what love is. Not flowers. Getting up at five for someone who doesn't know your name.",
                    "I've chosen our house. I've planned the next sixty years, one at a time. I do hope you'll like them.",
                    "Please don't cry. There's no need. It's all arranged.",
                    "You're coming with me now. I do insist."),
            "SPORTY", List.of(
                    "Hah. Finally.",
                    "You know I run past your house every morning? Five kilometres out of my way. Just to see your light come on.",
                    "I've never wanted to win anything like this. Not nationals. Not anything.",
                    "I know how far you get before you're out of breath. I've timed it. I've been training for it.",
                    "You could run. I'd love that. I've never been so happy as when I'm catching up.",
                    "I don't get tired. Not of this. Not of you.",
                    "So go on. Run. I'll even count. One. Two. Three.")
    );

    private static final Map<String, List<String>> TAKEN = Map.of(
            "CHEERFUL", List.of("You're awake! I was so worried I'd done it wrong!", "Look, I decorated! It took ages! Say you love it!"),
            "SHY", List.of("...you're really here.", "...I'm sorry about your head. I measured. I was so careful."),
            "COLD", List.of("Good.", "You don't need to say anything. You'll want to. Most of it won't help."),
            "GOSSIP", List.of("Everyone's been talking about you, you know.", "They think you ran away. I told them to. So now it's only us."),
            "POLITE", List.of("Welcome home. Please, make yourself comfortable.", "I've written to your parents. You live here now."),
            "SPORTY", List.of("Got you. Told you I was faster.", "Best time I've ever run, too. Shame nobody saw.")
    );

    private Monologues() {
    }

    public static Voice voice(String personality) {
        return VOICES.getOrDefault(personality, COLD);
    }

    public static List<String> unmasked(String personality) {
        return UNMASKED.getOrDefault(personality, UNMASKED.get("COLD"));
    }

    /** Three strikes. She's said everything she's going to say. */
    public static String last(String personality) {
        return switch (personality) {
            case "CHEERFUL" -> "I still love you! Isn't that funny? Hold still!";
            case "SHY" -> "...this way you'll never have to look at anyone else.";
            case "GOSSIP" -> "Nobody's ever going to hear about this part. It's just for us.";
            case "POLITE" -> "I did warn you. Hold still, please.";
            case "SPORTY" -> "Game over.";
            default -> "I told you. I don't let go.";
        };
    }

    public static List<String> taken(String personality) {
        return TAKEN.getOrDefault(personality, List.of());
    }
}
