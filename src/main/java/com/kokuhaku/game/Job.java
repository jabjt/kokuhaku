package com.kokuhaku.game;

/** Part-time work: a shift skips ahead and pays, and coworkers gossip about one of the girls. */
public enum Job {
    KONBINI("Grocery Store", "Scan shopping at the till. Customers hate mistakes.", 1000, 3),
    KIOSK("Station Kiosk", "Sell tickets and snacks at the train station.", 900, 2),
    LIBRARY("Library Aide", "Shelve returns in the school library.", 800, 2);

    public final String title;
    public final String description;
    public final int yenPerHour;
    public final int hours;

    Job(String title, String description, int yenPerHour, int hours) {
        this.title = title;
        this.description = description;
        this.yenPerHour = yenPerHour;
        this.hours = hours;
    }

    public int pay() {
        return yenPerHour * hours;
    }
}
