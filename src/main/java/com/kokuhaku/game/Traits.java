package com.kokuhaku.game;

/**
 * Everything a clue can say about her. Each value carries four phrasings, one
 * per difficulty: Easy states it, Medium is a sighting, Hard is physical
 * evidence, Impossible only implies it.
 */
public final class Traits {
    private Traits() {
    }

    public interface Phrased {
        String label();

        String phrase(int level);
    }

    public enum Hair implements Phrased {
        BLACK("Black", "She has black hair.",
                "A girl with black hair was seen near your locker before school.",
                "A dark hair was caught in the hinge of your locker.",
                "Her hair is the colour of a crow's wing."),
        BROWN("Brown", "She has brown hair.",
                "Someone saw a brown-haired girl at the shoe lockers before anyone else arrived.",
                "A brown hair was stuck to the tape on your note.",
                "Her hair is the colour of the desk you sit at."),
        BLONDE("Blonde", "She has blonde hair.",
                "A blonde girl was asking which locker is yours.",
                "There was a pale gold hair on your locker shelf.",
                "Her hair is the colour of late summer wheat."),
        RED("Red", "She has red hair.",
                "A red-haired girl was standing outside your house last night.",
                "A red hair was tied around the handle of your locker.",
                "Her hair is the colour of a warning."),
        PINK("Pink", "She has pink hair.",
                "Someone saw a pink-haired girl at your locker after school.",
                "A pink hair was folded inside the note.",
                "Her hair is the colour of the blossoms by the school gate."),
        SILVER("Silver", "She has silver hair.",
                "A silver-haired girl was seen waiting by your shoe locker.",
                "A silver hair glinted in your locker's lock.",
                "Her hair looks like the moon through your window.");

        private final String label;
        private final String[] phrases;

        Hair(String label, String... phrases) {
            this.label = label;
            this.phrases = phrases;
        }

        public String label() {
            return label;
        }

        public String phrase(int level) {
            return phrases[level];
        }
    }

    public enum Length implements Phrased {
        SHORT("Short", "Her hair is short.",
                "The girl someone saw had short hair.",
                "The hair from your locker was barely a finger long.",
                "The wind never gets a grip on her hair."),
        LONG("Long", "Her hair is long.",
                "The girl someone saw had long hair down her back.",
                "The hair from your locker was longer than your arm.",
                "When she leans over your desk, her hair gets there first.");

        private final String label;
        private final String[] phrases;

        Length(String label, String... phrases) {
            this.label = label;
            this.phrases = phrases;
        }

        public String label() {
            return label;
        }

        public String phrase(int level) {
            return phrases[level];
        }
    }

    public enum Glasses implements Phrased {
        YES("Glasses", "She wears glasses.",
                "Someone saw her cleaning her glasses outside your classroom.",
                "A tiny screw from a pair of glasses was rolling around your locker.",
                "She sees you more clearly than anyone, but only with help."),
        NO("No glasses", "She doesn't wear glasses.",
                "The girl at your locker wasn't wearing glasses.",
                "A witness swears there was nothing in front of her eyes at all.",
                "Nothing ever comes between her eyes and you.");

        private final String label;
        private final String[] phrases;

        Glasses(String label, String... phrases) {
            this.label = label;
            this.phrases = phrases;
        }

        public String label() {
            return label;
        }

        public String phrase(int level) {
            return phrases[level];
        }
    }

    /** You are in 2-A. */
    public enum Homeroom implements Phrased {
        A("2-A", "She's in your class, 2-A.",
                "Someone saw her leaving 2-A with your notebook.",
                "Your note was folded from 2-A's attendance sheet.",
                "She can watch you all lesson without turning her head."),
        B("2-B", "She's in class 2-B.",
                "Someone saw her slip back into 2-B after visiting your locker.",
                "Your note was folded from 2-B's attendance sheet.",
                "She only sees you between lessons, and she counts every one.");

        private final String label;
        private final String[] phrases;

        Homeroom(String label, String... phrases) {
            this.label = label;
            this.phrases = phrases;
        }

        public String label() {
            return label;
        }

        public String phrase(int level) {
            return phrases[level];
        }
    }

    public enum Club implements Phrased {
        ART("Art", "She's in the art club.",
                "Someone saw her sketching you from the art room window.",
                "There was dried paint on the edge of your note.",
                "She knows the exact colour of your eyes. She's had to mix it."),
        COOKING("Cooking", "She's in the cooking club.",
                "Someone saw her leave a bento on your desk.",
                "Your note smelled faintly of sugar and vanilla.",
                "She knows what you eat for lunch, and that it isn't enough."),
        DRAMA("Drama", "She's in the drama club.",
                "Someone heard her rehearsing your name in the drama room.",
                "There was stage makeup on the corner of your note.",
                "She smiles at everyone. She practises it in the mirror."),
        KENDO("Kendo", "She's in the kendo club.",
                "Someone saw her watching you from the kendo hall.",
                "A splinter of bamboo was caught in the fold of your note.",
                "She's very good at hitting exactly where she means to."),
        LITERATURE("Literature", "She's in the literature club.",
                "Someone saw her copying your handwriting in the library.",
                "Your note was written on a page torn from a poetry book.",
                "She's read every book you've borrowed, right after you."),
        MUSIC("Music", "She's in the music club.",
                "Someone heard her humming your name in the music room.",
                "Your note was written on the back of sheet music.",
                "She has a song about you. It doesn't have a happy ending."),
        PHOTOGRAPHY("Photography", "She's in the photography club.",
                "Someone saw her developing photos of you in the darkroom.",
                "Your note smelled of darkroom chemicals.",
                "She has more pictures of you than you do."),
        TENNIS("Tennis", "She's in the tennis club.",
                "Someone saw her watching you from the tennis courts.",
                "There was a fleck of yellow-green felt stuck to your note.",
                "She can run faster than you. She's checked.");

        private final String label;
        private final String[] phrases;

        Club(String label, String... phrases) {
            this.label = label;
            this.phrases = phrases;
        }

        public String label() {
            return label;
        }

        public String phrase(int level) {
            return phrases[level];
        }
    }

    public enum District implements Phrased {
        NORTH_HILL("North Hill", "up on North Hill", "She lives on North Hill.",
                "Someone saw her walking up North Hill after school.",
                "There was hill clay on the floor in front of your locker.",
                "From her window she can see your house. She's higher up."),
        RIVERSIDE("Riverside", "down by the river", "She lives by the river.",
                "Someone saw her walking home along the river.",
                "Your note was slightly damp, and smelled of river water.",
                "She falls asleep to the sound of running water."),
        STATION("Station Street", "near the station", "She lives near the station.",
                "Someone saw her get off the train at the station.",
                "A crumpled train ticket was stuck to your note.",
                "She hears the last train every night. Then she thinks of you."),
        SHRINE("Shrine Road", "on Shrine Road", "She lives on Shrine Road.",
                "Someone saw her praying at the shrine. She said your name.",
                "There was incense ash on your note.",
                "She has already asked the gods for you. Twice.");

        private final String label;
        public final String livesPhrase;
        private final String[] phrases;

        District(String label, String livesPhrase, String... phrases) {
            this.label = label;
            this.livesPhrase = livesPhrase;
            this.phrases = phrases;
        }

        public String label() {
            return label;
        }

        public String phrase(int level) {
            return phrases[level];
        }
    }
}
