package com.kokuhaku;

public enum Difficulty {
    EASY("Easy", 5, true, 24 * 60, "Hints on. 5 guesses. Home by midnight."),
    MEDIUM("Medium", 3, false, 23 * 60 + 30, "No easy hints. 3 guesses. Home by 23:30."),
    HARD("Hard", 2, false, 23 * 60, "No hints. 2 guesses. Home by 23:00."),
    IMPOSSIBLE("Impossible", 1, false, 22 * 60 + 30, "No hints. 1 guess. Home by 22:30.");

    public final String label;
    public final int guesses;
    public final boolean hints;
    /** Be inside your home by this time (minutes since midnight of that day), or she finds you. */
    public final int curfew;
    public final String description;

    Difficulty(String label, int guesses, boolean hints, int curfew, String description) {
        this.label = label;
        this.guesses = guesses;
        this.hints = hints;
        this.curfew = curfew;
        this.description = description;
    }
}
