package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Progress through one run, saved with the world. The mystery itself isn't
 * stored: it's rebuilt from the seed.
 */
public class RunState extends PersistentState {
    public enum Status { PLAYING, WON, LOST }

    /** False while the party is still in the menu room. */
    public boolean started = false;
    /** Everyone in the run, in the order they joined; their place here is their slot. */
    public final List<UUID> party = new ArrayList<>();
    public Difficulty difficulty = Difficulty.EASY;
    public long seed;
    public int day = 1;
    public int minute = Schedule.WAKE;
    public int guessesLeft;
    public int yen = 1000;
    public Status status = Status.PLAYING;
    public boolean studentsSpawned = false;
    public boolean townBuilt = false;
    /** Index into Town.homes for each student, then each player from Roster.TOTAL; null until assigned. */
    public int[] homes = null;
    /** Clue ids in the order you found them. */
    public final Set<Integer> found = new LinkedHashSet<>();
    /** Students who've told you about themselves. */
    public final Set<Integer> known = new LinkedHashSet<>();
    public final Set<Integer> accused = new LinkedHashSet<>();
    /** Yanderes you've named correctly. */
    public final Set<Integer> caught = new LinkedHashSet<>();
    /** Students who have died. */
    public final Set<Integer> dead = new LinkedHashSet<>();
    /** Clues that can only be found at a crime scene now. */
    public final Set<Integer> atScene = new LinkedHashSet<>();
    /** How each girl feels about you, from -5 to 5. Notes in class move it. */
    public final int[] fondness = new int[Roster.TOTAL];
    /** Each player's shoe locker, by slot. */
    public final int[] lockers = new int[Roster.MAX_PARTY];
    /** Wrong answers, absences and failed shifts. Enough of them and you're expelled. */
    public int strikes = 0;
    public boolean detention = false;
    /** Each player's front door, tonight, by slot. */
    public final boolean[] doorLocked = new boolean[Roster.MAX_PARTY];
    /** You can't work again before this day. */
    public int firedUntil = 0;
    /** Who paid your bill and means to collect, or -1; when her next note comes; how many she's sent. */
    public int blackmailer = -1, blackmailDue = 0, blackmailStage = 0;
    /** The day your bedroom lock doesn't work. */
    public int lockBrokenOn = 0;
    /** The mode where everyone knows who she is, and you only have to survive it; and whether you've said it today. */
    public boolean devoted = false, saidIt = false;
    /** Who you're going out with, or -1; since when; and the last day you went on a date. */
    public int dating = -1, datingSince = 0, dateOn = 0;
    /** What you've bought: nights of door chain left, sprays and boxes of sweets. */
    public int chains = 0, sprays = 0, gifts = 0;
    /** Whose house you live in now, or -1. How many times she's caught you, and whether she's stopped forgiving it. */
    public int livingWith = -1, jealous = 0;
    public boolean bonk = false;
    /** Whoever made her jealous today. */
    public int livingVictim = -1;
    /** The last day something happened to you, and what's already happened. */
    public int scenarioDay = 0;
    public final Set<Integer> scenarios = new LinkedHashSet<>();
    /** Haruka's diary pages you've found. */
    public final Set<Integer> pages = new LinkedHashSet<>();
    /** Things in your bag that aren't part of the mystery's own clues. */
    public final List<String> notes = new ArrayList<>();

    private Mystery mystery;

    /** The yandere still out there, for the endings. */
    public int freeYandere() {
        return mystery().yanderes.stream().filter(y -> !caught.contains(y)).findFirst().orElse(mystery().yanderes.get(0));
    }

    public Mystery mystery() {
        if (mystery == null) {
            mystery = new Mystery(seed, difficulty, Math.max(1, party.size()));
        }
        return mystery;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("Started", started);
        NbtList members = new NbtList();
        for (UUID id : party) members.add(NbtHelper.fromUuid(id));
        nbt.put("Party", members);
        nbt.putInt("Difficulty", difficulty.ordinal());
        nbt.putLong("Seed", seed);
        nbt.putInt("Day", day);
        nbt.putInt("Minute", minute);
        nbt.putInt("Guesses", guessesLeft);
        nbt.putInt("Yen", yen);
        nbt.putIntArray("Lockers", lockers);
        nbt.putIntArray("Fondness", fondness);
        nbt.putInt("Strikes", strikes);
        nbt.putBoolean("Detention", detention);
        nbt.putInt("FiredUntil", firedUntil);
        nbt.putInt("Blackmailer", blackmailer);
        nbt.putInt("BlackmailDue", blackmailDue);
        nbt.putInt("BlackmailStage", blackmailStage);
        nbt.putInt("LockBroken", lockBrokenOn);
        nbt.putInt("LivingWith", livingWith);
        nbt.putInt("Jealous", jealous);
        nbt.putBoolean("Bonk", bonk);
        nbt.putInt("Chains", chains);
        nbt.putInt("Sprays", sprays);
        nbt.putInt("Gifts", gifts);
        nbt.putInt("Dating", dating);
        nbt.putInt("DatingSince", datingSince);
        nbt.putInt("DateOn", dateOn);
        nbt.putBoolean("Devoted", devoted);
        nbt.putBoolean("SaidIt", saidIt);
        nbt.putInt("LivingVictim", livingVictim);
        nbt.putInt("ScenarioDay", scenarioDay);
        nbt.putIntArray("Scenarios", scenarios.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Pages", pages.stream().mapToInt(Integer::intValue).toArray());
        NbtList written = new NbtList();
        for (String note : notes) written.add(NbtString.of(note));
        nbt.put("Notes", written);
        nbt.putIntArray("Dead", dead.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("AtScene", atScene.stream().mapToInt(Integer::intValue).toArray());
        nbt.putInt("Status", status.ordinal());
        nbt.putBoolean("Spawned", studentsSpawned);
        nbt.putBoolean("TownBuilt", townBuilt);
        if (homes != null) nbt.putIntArray("Homes", homes);
        nbt.putIntArray("Found", found.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Known", known.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Accused", accused.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Caught", caught.stream().mapToInt(Integer::intValue).toArray());
        return nbt;
    }

    public static RunState fromNbt(NbtCompound nbt) {
        RunState s = new RunState();
        s.read(nbt);
        return s;
    }

    /** Everything from a saved run, over whatever this one held. */
    void read(NbtCompound nbt) {
        RunState s = this;
        for (java.util.Collection<?> c : List.of(party, found, known, accused, caught, dead, atScene, scenarios, pages, notes)) c.clear();
        java.util.Arrays.fill(fondness, 0);
        java.util.Arrays.fill(lockers, 0);
        java.util.Arrays.fill(doorLocked, false);
        mystery = null;
        s.started = nbt.getBoolean("Started");
        for (NbtElement id : nbt.getList("Party", NbtElement.INT_ARRAY_TYPE)) s.party.add(NbtHelper.toUuid(id));
        s.difficulty = Difficulty.values()[nbt.getInt("Difficulty")];
        s.seed = nbt.getLong("Seed");
        s.day = nbt.getInt("Day");
        s.minute = nbt.getInt("Minute");
        s.guessesLeft = nbt.getInt("Guesses");
        s.yen = nbt.getInt("Yen");
        int[] fond = nbt.getIntArray("Fondness");
        System.arraycopy(fond, 0, s.fondness, 0, Math.min(fond.length, s.fondness.length));
        int[] lockers = nbt.getIntArray("Lockers");
        System.arraycopy(lockers, 0, s.lockers, 0, Math.min(lockers.length, s.lockers.length));
        s.strikes = nbt.getInt("Strikes");
        s.detention = nbt.getBoolean("Detention");
        s.firedUntil = nbt.getInt("FiredUntil");
        s.blackmailer = nbt.contains("Blackmailer") ? nbt.getInt("Blackmailer") : -1;
        s.blackmailDue = nbt.getInt("BlackmailDue");
        s.blackmailStage = nbt.getInt("BlackmailStage");
        s.lockBrokenOn = nbt.getInt("LockBroken");
        s.livingWith = nbt.contains("LivingWith") ? nbt.getInt("LivingWith") : -1;
        s.jealous = nbt.getInt("Jealous");
        s.bonk = nbt.getBoolean("Bonk");
        s.chains = nbt.getInt("Chains");
        s.sprays = nbt.getInt("Sprays");
        s.gifts = nbt.getInt("Gifts");
        s.dating = nbt.contains("Dating") ? nbt.getInt("Dating") : -1;
        s.datingSince = nbt.getInt("DatingSince");
        s.dateOn = nbt.getInt("DateOn");
        s.devoted = nbt.getBoolean("Devoted");
        s.saidIt = nbt.getBoolean("SaidIt");
        s.livingVictim = nbt.contains("LivingVictim") ? nbt.getInt("LivingVictim") : -1;
        s.scenarioDay = nbt.getInt("ScenarioDay");
        for (int i : nbt.getIntArray("Scenarios")) s.scenarios.add(i);
        for (int i : nbt.getIntArray("Pages")) s.pages.add(i);
        for (NbtElement note : nbt.getList("Notes", NbtElement.STRING_TYPE)) s.notes.add(note.asString());
        for (int i : nbt.getIntArray("Dead")) s.dead.add(i);
        for (int i : nbt.getIntArray("AtScene")) s.atScene.add(i);
        s.status = Status.values()[nbt.getInt("Status")];
        s.studentsSpawned = nbt.getBoolean("Spawned");
        s.townBuilt = nbt.getBoolean("TownBuilt");
        if (nbt.contains("Homes")) s.homes = nbt.getIntArray("Homes");
        for (int i : nbt.getIntArray("Found")) s.found.add(i);
        for (int i : nbt.getIntArray("Known")) s.known.add(i);
        for (int i : nbt.getIntArray("Accused")) s.accused.add(i);
        for (int i : nbt.getIntArray("Caught")) s.caught.add(i);
    }
}
