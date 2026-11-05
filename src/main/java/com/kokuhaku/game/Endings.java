package com.kokuhaku.game;

/** How it can end, other than badly. The first line of each is the name on the screen. */
public final class Endings {
    private static final int SECRET = 7;
    /** The first line when the accomplice wins. */
    public static final String KEPT_HER_PROMISE = "She kept her promise";

    private Endings() {
    }

    private static String secret(int number, String name, String... lines) {
        return "Secret ending " + number + " of " + SECRET + ": " + name + "\n\n" + String.join("\n", lines);
    }

    /** Nobody came, and you lived. */
    private static String noOneCame(RunState s, String name) {
        int gone = s.dead.size();
        return "You survived\n\n"
                + "Two weeks. Nobody came.\n"
                + (gone == 0 ? "You didn't let anybody get close enough to hurt.\n" : gone + (gone == 1 ? " person" : " people") + " stood too near you. You can still say their names.\n")
                + "On the last morning, " + name + " opens the front door and stands aside.\n"
                + "\"You did so well,\" she says. \"I'm so proud of you. You can go.\"\n"
                + "You look down the empty street for a long time.\n"
                + "Then you close the door yourself.";
    }

    /** You kept your side of it, and everyone else is gone. */
    static String accompliceKept(String names) {
        return KEPT_HER_PROMISE + "\n\n"
                + "Nobody came for you. Nobody was ever going to.\n"
                + "Your friends' seats are empty, and the teachers have stopped reading out their names.\n"
                + "Your phone buzzes. \"Thank you. I knew you'd understand.\"\n"
                + "You've still got the messages. You read them sometimes.\n"
                + "It was " + names + ". You're the only one who knows.";
    }

    /** They found her, in spite of everything you did. */
    static String accompliceFailed(String names) {
        return "They found her\n\n"
                + "It was " + names + ", as you knew all along.\n"
                + "They're happy. They're hugging you. Nobody's checked your phone.\n"
                + "It buzzes in your pocket, once, from a number that should be disconnected.\n"
                + "\"I told you what would happen.\"";
    }

    /** You lasted the two weeks in her house. */
    static String stayed(RunState s) {
        if (s.devoted) return noOneCame(s, Roster.get(s.livingWith).firstName());
        String name = Roster.get(s.livingWith >= 0 ? s.livingWith : s.freeYandere()).firstName();
        if (s.jealous == 0) {
            return secret(1, "Home",
                    "Two weeks. You never once looked at anyone else.",
                    "On the last night " + name + " took the note out of her pocket and put it in your hand.",
                    "\"You can leave now,\" she said. \"The door was never locked.\"",
                    "You look at the door for a long time.",
                    "Then you lie back down.");
        }
        return secret(2, "Good Behaviour",
                "Two weeks. You've learned where to look, and where not to.",
                "Nobody at school says your name any more. There's nobody left who would.",
                name + " is making breakfast. She's humming.",
                "Your arm has almost stopped shaking.");
    }

    /** You got out. It took everything. */
    static String escaped(RunState s) {
        return secret(7, "Trapped",
                "The doors close. Nobody else is on the train.",
                "You don't know how far it goes. You don't care.",
                "There's a girl's name scratched into the window beside you. Haruka. She got this far.",
                "You count your fingers. You count them again.",
                "Your phone buzzes, once. You drop it out of the window without reading it.");
    }

    /** Two weeks was never going to be enough. */
    static String neverLeft(RunState s) {
        return "Family\n\n"
                + "Two weeks. You did everything she asked.\n"
                + "On the last morning she doesn't open the door.\n"
                + "\"There isn't an after,\" she says. \"I told you.\"\n"
                + "She's already set a place for you, for tomorrow.";
    }

    /** You never found out. Or you did, and you stayed. */
    static String confessed(String name) {
        return secret(5, "Kokuhaku",
                "Day 31. Everyone thinks it's over.",
                name + " takes your hand on the way to school, the way she has every morning.",
                "\"I wrote that note,\" she says. \"The first one. You kept it.\"",
                "She doesn't let go. She doesn't look at you. She's smiling.",
                "\"You said you'd go out with me. So that's that.\"");
    }

    /** Found her, and there was someone left to go home to. */
    static String stayedTogether(String names, String name) {
        return secret(6, "Someone Who Stayed",
                "It was " + names + ".",
                name + " is waiting at the gate. She's been waiting there all day.",
                "She doesn't ask you anything. She just takes your hand.",
                "Neither of you locks the door that night.",
                "Neither of you sleeps.");
    }

    /** Found her, with her whole story in your bag. */
    static String lastPage(String names) {
        return secret(3, "The Last Page",
                "It was " + names + ".",
                "You put Haruka's diary on the desk, every page of it.",
                "She reads the first line and all the colour goes out of her face.",
                "\"Where did you get this? She promised she'd burned it.\"",
                "In a locker across town, someone is folding a new note.");
    }

    /** Found her, after she'd held your debt over you. */
    static String paidInFull(String names) {
        return secret(4, "Paid in Full",
                "It was " + names + ".",
                "You lay every envelope out in front of the police, one by one.",
                "They were never sent from anyone else. She never needed to sign them.",
                "\"I paid for you,\" she says as they take her out.",
                "The hospital sends you a letter a week later. Your account is in credit.",
                "Nobody will tell you who's paying.");
    }
}
