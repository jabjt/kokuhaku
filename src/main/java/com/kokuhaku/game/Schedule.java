package com.kokuhaku.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shape of a day. Times are minutes since midnight. Day 1 is a Monday;
 * there's school Monday to Friday and the weekend is yours.
 */
public final class Schedule {
    public static final int DAYS = 14;
    /**
     * One in-game minute is four real seconds. Sleeping in your own bed after school
     * skips to the next morning.
     * Developer testing only: KOKUHAKU_FAST=n makes a minute pass every n ticks.
     */
    public static final int TICKS_PER_MINUTE = System.getenv("KOKUHAKU_FAST") != null
            ? Integer.parseInt(System.getenv("KOKUHAKU_FAST")) : 45;

    public static final int WAKE = 6 * 60 + 30;
    public static final int HOMEROOM = 8 * 60 + 25;
        /** Fifteen minutes between lessons, a minute of real time, to cross the school. */
    public static final int[] PERIOD_START = {8 * 60 + 45, 9 * 60 + 50, 10 * 60 + 55, 12 * 60, 13 * 60 + 40, 14 * 60 + 45};
    public static final int PERIOD_LENGTH = 50;
    /** You can still walk in this many minutes after a lesson starts. After that you're absent. */
    public static final int LATE_GRACE = 5;
    public static final int LUNCH = 12 * 60 + 50;
    public static final int AFTER_SCHOOL = 15 * 60 + 40;
    public static final int EVENING = 18 * 60;
    // Curfew depends on difficulty: see Difficulty.curfew.

    public static final String[] WEEKDAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private static final List<List<String>> TIMETABLE = List.of(
            List.of("Japanese", "Mathematics", "English", "Science", "History", "PE"),
            List.of("Mathematics", "English", "Art", "Japanese", "Science", "Music"),
            List.of("English", "History", "Mathematics", "PE", "Japanese", "Home Economics"),
            List.of("Science", "Japanese", "Music", "Mathematics", "English", "History"),
            List.of("Mathematics", "PE", "Japanese", "English", "Art", "Class Meeting")
    );

    private static final Map<Long, List<List<String>>> WEEKS = new ConcurrentHashMap<>();

    private Schedule() {
    }

    /** 0 = Monday ... 6 = Sunday. */
    public static int weekday(int day) {
        return (day - 1) % 7;
    }

    public static boolean isSchoolDay(int day) {
        return weekday(day) < 5;
    }

    /** Subjects for a weekday (0-4), or an empty list at weekends. Every seed makes a different week. */
    public static List<String> subjects(long seed, int weekday) {
        if (weekday >= 5) return List.of();
        return WEEKS.computeIfAbsent(seed, key -> {
            Random r = new Random(key);
            List<List<String>> days = new ArrayList<>();
            for (List<String> day : TIMETABLE) {
                List<String> shuffled = new ArrayList<>(day);
                Collections.shuffle(shuffled, r);
                days.add(shuffled);
            }
            Collections.shuffle(days, r);
            return days;
        }).get(weekday);
    }

    /** Twelve numbered rooms: 01-06 on the ground floor, 07-12 upstairs. 0 = no room (the gym). */
    public static final int ROOMS = 12, HOMEROOM_2A = 9;

    /** Where 2-A has a subject. */
    public static int roomNumber(String subject) {
        return switch (subject) {
            case "Japanese" -> 1;
            case "Mathematics" -> 2;
            case "English" -> 3;
            case "History" -> 4;
            case "Science" -> 5;
            case "Art" -> 6;
            case "Music" -> 7;
            case "Home Economics" -> 8;
            case "PE" -> 0;
            default -> HOMEROOM_2A;
        };
    }

    /** Classes: each of 2-A and 2-B is split into three groups, spread round the rooms. You're in group 0. */
    public static final int GROUPS = 6;

    /** Where a group has a subject. Group 0 is in the room your diary says. */
    public static int roomFor(int group, String subject) {
        int room = roomNumber(subject);
        return room == 0 ? 0 : (room - 1 + group * 2) % ROOMS + 1;
    }

    public static int homeroomFor(int group) {
        return (HOMEROOM_2A - 1 + group) % ROOMS + 1;
    }

    public static String roomKey(int number) {
        return String.format("%02d", number);
    }

    /** PE goes back and forth: the pool one day, the court outside the next. */
    public static boolean swimming(int day) {
        return day % 2 == 1;
    }

    /** Every other Science is in the lab upstairs, and every other Home Economics in the kitchens. */
    public static boolean practical(String subject, int day) {
        return day % 2 == 0 && (subject.equals("Science") || subject.equals("Home Economics"));
    }

    public static String practicalPlace(String subject) {
        return subject.equals("Science") ? "lab" : "kitchen";
    }

    public static String room(String subject, int day) {
        if (practical(subject, day)) return subject.equals("Science") ? "the lab, upstairs" : "the kitchens";
        int n = roomNumber(subject);
        return n == 0 ? (swimming(day) ? "the pool" : "the court") : "Room " + roomKey(n);
    }

    public static String clock(int minute) {
        return String.format("%02d:%02d", minute / 60 % 24, minute % 60);
    }

    /** Minecraft time of day for a clock time (0 ticks = 06:00). */
    public static long worldTime(int day, int minute) {
        return (day - 1) * 24000L + Math.floorMod((minute - 360) * 1000 / 60, 24000);
    }
}
