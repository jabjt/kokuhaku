package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.Kokuhaku;
import com.kokuhaku.block.LockerBlock;
import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Mystery.Clue;
import com.kokuhaku.game.Mystery.Kind;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.item.ModItems;
import com.kokuhaku.net.Net;
import com.kokuhaku.world.MenuRoom;
import com.kokuhaku.world.town.Town;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Runs the two weeks on the server side: the clock, school, jobs, deaths, talking, the bag and accusations. */
public final class RunManager {
    public static final String LEVEL_NAME = "Kokuhaku";
    private static final int[] EXPEL_AT = {24, 18, 14, 10};
    /** A bad lesson is one strike, however bad, and a warning comes before the end. */
    private static final int BAD_LESSON = 3, FINE_PER_WRONG = 50, WARN_BEFORE = 3;
    private static final float[] ABDUCT_CHANCE = {0.10f, 0.18f, 0.28f, 0.40f};
    private static final float[] DEATH_CHANCE = {0.28f, 0.36f, 0.44f, 0.5f};
    private static final int SCENE_OVERLAP = 16;
    private static final int SLEEP_TICKS = 130, NEWS_TICKS = 110, QUIET_TICKS = 25, WAKE_TICKS = 240, BAT_TICKS = 220, BAT_HIT_TICK = 66, NIGHT_TICKS = 170;

    private static final Random RANDOM = new Random();
    private static Town town;
    private static int ticks = 0;
    private static boolean transitioning = false;
    private static boolean devRoomSeen = false;
    private static boolean friendCameToday = false;
    private static boolean partyChanged = false;
    /** The last day someone watched from a corner. One a day is plenty. */
    private static int peekedOn = 0;
    private static final String[] PASSERS_BY = {"\"Not now, I'm late.\"", "\"Sorry, I don't know anything about that.\"",
            "\"You should get home before dark.\"", "\"Do I know you?\"", "\"Mind yourself.\"", "They walk past without looking at you."};
    private static final float PEEK_CHANCE = 0.4f;
    private static int peekTries = 0;
    private static UUID peekWatcher = null;
    /** Who was indoors at school a moment ago. */
    private static final Map<UUID, Boolean> insideSchool = new HashMap<>();
    /** Players she has. They're in the room at the end, and out of the game. */
    private static final Set<UUID> gone = new HashSet<>();
    /** Someone's at the door for you. */
    private static final Set<UUID> calledDown = new HashSet<>();
    private static final Map<BlockPos, BlockPos> frontDoors = new HashMap<>();
    /** Friends called back: they can't be touched, and they can't touch much. */
    private static final Set<UUID> ghosts = new HashSet<>();
    private static final int GHOST_COST = 15000, GHOST_GLOW_EVERY = 12;
    /** Which aisle you get up into, after a lesson. */
    private static final Map<UUID, Integer> desks = new HashMap<>();
    private static final int HAND_PRIZE = 50, MOST_HANDS = 2;
    private static final double BOARD_HEIGHT = 2.3, BOARD_FACE = 1.02;
    private static final Map<BlockPos, Integer> doorOwners = new HashMap<>();
    private static final int PORCH = 5, DOOR_SEARCH = 30, DOOR_REACH = 20, GIVE_UP_WAITING = 8 * 60 + 12;
    private static final double AT_THE_DOOR = 3;
    /** What the black screen will say tonight. */
    private static final List<String> nightNews = new ArrayList<>();
    private static int lessonEnd = 0, lessonGraceEnd = 0;
    /** day * 10 + period of the lesson being settled. */
    private static int lessonKey = -1;
    /** Who's still answering, and who's sat down or been marked absent. */
    private static final Set<UUID> lessonPending = new HashSet<>(), lessonSettled = new HashSet<>();
    private static boolean lessonAttended = false;
    /** Who's in bed, waiting for the others. */
    private static final Set<UUID> asleep = new HashSet<>();
    private static final List<Object[]> scheduled = new ArrayList<>();
    /** Whose house you're sleeping at tonight. */
    private static final Map<UUID, Integer> sleepover = new HashMap<>();
    /** What she told you, for the morning. */
    private static final Map<UUID, String[]> confided = new HashMap<>();
    /** The question on the screen, if there is one, and the half hour each girl last asked in. */
    private static final Map<UUID, Questions.Question> asking = new HashMap<>();
    private static final Map<UUID, Map<Integer, Integer>> asked = new HashMap<>();
    /** Nobody gets hit twice in one go. */
    private static final Map<UUID, Integer> carSafeUntil = new HashMap<>();
    private static final int CAR_TICKS = 70, CAR_GRACE = 200, SLEEPOVER_TICKS = 60;
    private static final int OVERHEAR_EVERY = 10, OVERHEAR_REACH = 7;
    private static final float OVERHEAR_CHANCE = 0.2f;
    /** How often, each day, she can't take her eyes off you. */
    private static final float[] STARE_CHANCE = {0.8f, 0.5f, 0.2f, 0.05f};
    private static final String[][] CHAPTERS = {{"The First Days", "Someone left you a note."}, {"Day Four", "People have stopped asking questions."}, {"Day Seven", "There are empty desks now."}, {"Day Ten", "She has stopped hiding."}, {"The Last Days", "She's got everything ready."}};
    /** Who's in the middle of a scene of their own, and can't be run over during it. */
    private static final Set<UUID> busy = new HashSet<>();
    private static boolean devStaged = false;
    /** Developer testing only: no walk to school, so a test starts where it means to. */
    private static final boolean DEV_NO_WALK = java.util.stream.Stream.of("KOKUHAKU_NOWALK", "KOKUHAKU_CAR", "KOKUHAKU_ASK", "KOKUHAKU_SLEEPOVER", "KOKUHAKU_SIT", "KOKUHAKU_PEEK", "KOKUHAKU_BAT", "KOKUHAKU_TOUR").anyMatch(k -> System.getenv(k) != null);
    /** Where evidence lies, by clue id, for the crime scenes. */
    private static final Map<BlockPos, Integer> evidence = new HashMap<>();
    /** What she's dropped, and where. */
    private static final Map<BlockPos, String> trail = new HashMap<>();

    private RunManager() {
    }

    public static Town town() {
        return town;
    }

    public static boolean isRun(MinecraftServer server) {
        return LEVEL_NAME.equals(server.getSaveProperties().getLevelName());
    }

    public static RunState state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(RunState::fromNbt, RunManager::newState, "kokuhaku_run");
    }

    private static RunState newState() {
        RunState s = new RunState();
        s.seed = new Random().nextLong();
        s.markDirty();
        return s;
    }

    // ------------------------------------------------------------ lifecycle

    public static void onStarted(MinecraftServer server) {
        RunState s = state(server);
        ServerWorld world = server.getOverworld();
        scheduled.clear();
        evidence.clear();
        transitioning = false;
        lessonPending.clear();
        lessonSettled.clear();
        lessonKey = -1;
        asleep.clear();
        Seats.clear();
        for (UUID id : watchers) {
            ServerPlayerEntity w = server.getPlayerManager().getPlayer(id);
            if (w == null) continue;
            w.setCameraEntity(w);
            w.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
        }
        watchers.clear();
        gone.clear();
        spectators.clear();
        profiles.clear();
        calledDown.clear();
        frontDoors.clear();
        doorOwners.clear();
        Chase.clear();
        Sweethearts.clear();
        desks.clear();

        failedLinks.clear();
        ghosts.clear();
        Pe.clear();
        Practical.clear();
        Kept.clear();
        nightNews.clear();
        sleepover.clear();
        confided.clear();
        asking.clear();
        asked.clear();
        carSafeUntil.clear();
        busy.clear();
        Ward.clear();
        Talk.clear();
        WalkScene.clear();
        LunchScene.clear();
        Paths.clear();
        ForestScares.clear();
        SchoolLife.clear();
        Rain.clear();
        HomeChanges.clear();
        Living.clear();
        Stalker.clear();
        Favours.clear();
        Shadow.clear();
        Phone.clear();
        Teachers.clear();
        Festival.clear();
        TrainRide.clear();
        Accomplice.clear();
        Whispers.reset();
        Turning.reset();
        Hallway.clear();
        Radio.clear();
        Noticed.clear();
        Noticeboard.clear();
        SkiSlope.clear();
        People.reset();
        Chatter.reset();
        Romance.clear();
        Devotion.clear();
        StudentEntity.setShunning(s.devoted, s.livingWith);
        Grab.clear();
        trail.clear();
        Traffic.clear();
        Railway.reset();

        // Keep the whole town loaded so students keep walking when you're far away.
        for (int cx = Town.MIN_X >> 4; cx <= Town.MAX_X >> 4; cx++)
            for (int cz = Town.MIN_Z >> 4; cz <= Town.MAX_Z >> 4; cz++)
                world.setChunkForced(cx, cz, true);

        long started = System.currentTimeMillis();
        town = new Town(s.townBuilt ? null : world);
        s.townBuilt = true;
        Kokuhaku.LOG.info("Town ready in {} ms: {} waypoints ({} unreachable), {} homes, {} seats in Room 01",
                System.currentTimeMillis() - started, town.graph.size(), town.graph.unreachableFrom(town.foyer),
                town.homes.size(), town.seats.get("01").size());
        s.markDirty();
        GraphCheck.run(world, town);
        MenuRoom.build(world);
    }

    /** The host pressed play: everyone in the menu room is the party, and the run starts. */
    public static void begin(ServerPlayerEntity host, int difficulty, boolean devoted, boolean accomplice) {
        MinecraftServer server = host.server;
        RunState s = state(server);
        if (s.started || !server.isHost(host.getGameProfile())) return;
        // A saved run can't be played over: it has to be finished first.
        if (SaveSlot.exists() && System.getenv("KOKUHAKU_AUTOPLAY") == null) return;
        s.started = true;
        if (System.getenv("KOKUHAKU_SPECTATE") != null) spectators.add(players(server).get(players(server).size() - 1).getUuid());
        // Somebody's got to actually play.
        if (players(server).stream().allMatch(p -> spectators.contains(p.getUuid()))) spectators.clear();
        s.difficulty = Difficulty.values()[System.getenv("KOKUHAKU_DIFF") != null ? Integer.parseInt(System.getenv("KOKUHAKU_DIFF")) : difficulty];
        for (ServerPlayerEntity p : players(server)) if (!spectators.contains(p.getUuid())) s.party.add(p.getUuid());
        s.guessesLeft = s.difficulty.guesses + s.party.size() - 1;
        List<Integer> lockers = new ArrayList<>();
        for (int i = 1; i <= Town.LOCKERS; i++) lockers.add(i);
        Collections.shuffle(lockers, new Random(s.seed));
        for (int i = 0; i < s.party.size(); i++) s.lockers[i] = lockers.get(i);
        s.homes = assignHomes(s, town);
        // Alone only: there's only one of you for her to keep.
        s.devoted = devoted && s.party.size() == 1;
        if (s.devoted) {
            s.livingWith = s.freeYandere();
            Kokuhaku.LOG.info("No One Will Save You: it's {}", Roster.get(s.livingWith).name());
        }
        StudentEntity.setShunning(s.devoted, s.livingWith);
        if (accomplice && !s.devoted) {
            List<ServerPlayerEntity> everyone = players(server).stream().filter(p -> !spectators.contains(p.getUuid())).toList();
            String forced = System.getenv("KOKUHAKU_ACCOMPLICE");
            Accomplice.pick(everyone.get("friend".equals(forced) ? everyone.size() - 1 : "host".equals(forced) ? 0 : RANDOM.nextInt(everyone.size())));
        }
        Kokuhaku.LOG.info("Run started: party of {}, {} girls, {} boys, {} yanderes, {} guesses", s.party.size(),
                Roster.girlsFor(s.party.size()), Roster.boysFor(s.party.size()), s.mystery().yanderes.size(), s.guessesLeft);
        ServerWorld world = server.getOverworld();
        spawnStudents(world, s);
        Townsfolk.spawn(world, town);
        Teachers.spawn(world, town);
        LunchHall.spawnCook(world);
        Traffic.spawn(world);
        s.studentsSpawned = true;
        world.setSpawnPos(town.graph.pos(town.homes.get(s.homes[Roster.TOTAL]).inside()), 0f);
        world.setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        s.markDirty();
        transitioning = true;
        for (ServerPlayerEntity p : players(server)) {
            p.clearStatusEffects();
            if (p.getInventory().count(ModItems.ROOM_KEY) == 0) p.getInventory().setStack(1, new ItemStack(ModItems.ROOM_KEY));
            sendState(p, s);
            ServerPlayNetworking.send(p, Net.RUN_START, PacketByteBufs.empty());
            if (spectators.contains(p.getUuid())) {
                gone.add(p.getUuid());
                // Somebody playing alone: you watch it through their eyes. A party: you haunt it.
                later(40, () -> {
                    if (s.party.size() == 1) watch(p, s);
                    else becomeGhost(p, s);
                });
            } else {
                later(30, () -> wakeUp(p, s));
            }
        }
        // The slot's taken from the first morning: you can quit now and come back to it.
        later(40, () -> SaveSlot.write(s, false, gone, Accomplice.who(), players(server)));
    }

    /** Party members who weren't back yet when the host pressed Continue, and what they had on them. */
    private static final Set<UUID> toWake = new HashSet<>();
    private static NbtCompound savedBags = new NbtCompound();

    /** The host pressed Continue: the saved run, back in this fresh town, waking up that morning. */
    public static void resume(ServerPlayerEntity host) {
        MinecraftServer server = host.server;
        RunState s = state(server);
        if (s.started || !server.isHost(host.getGameProfile())) return;
        NbtCompound save = SaveSlot.read();
        if (save == null) return;
        boolean townBuilt = s.townBuilt;
        long menuSeed = s.seed;
        s.read(save.getCompound("State"));
        s.townBuilt = townBuilt;
        s.started = true;
        s.studentsSpawned = true;
        if (s.seed == 0) s.seed = menuSeed;
        Set<UUID> wasGone = new HashSet<>();
        for (net.minecraft.nbt.NbtElement e : save.getList("Gone", net.minecraft.nbt.NbtElement.STRING_TYPE)) wasGone.add(UUID.fromString(e.asString()));
        savedBags = save.getCompound("Bags");
        // Nobody here was in it (a new account, or one that changed): whoever's here takes the places in it, the host first.
        if (players(server).stream().noneMatch(p -> s.party.contains(p.getUuid()))) {
            List<ServerPlayerEntity> here = new ArrayList<>(players(server));
            here.remove(host);
            here.add(0, host);
            NbtCompound bags = new NbtCompound();
            Set<UUID> gone2 = new HashSet<>();
            for (int i = 0; i < Math.min(here.size(), s.party.size()); i++) {
                UUID was = s.party.get(i), now = here.get(i).getUuid();
                if (savedBags.contains(was.toString())) bags.put(now.toString(), savedBags.get(was.toString()));
                if (wasGone.contains(was)) gone2.add(now);
                s.party.set(i, now);
            }
            savedBags = bags;
            wasGone = gone2;
            Kokuhaku.LOG.info("Continued under new names: {}", here.stream().limit(s.party.size()).map(p -> p.getGameProfile().getName()).toList());
        }
        // Anyone here who wasn't in it sits it out and watches.
        for (ServerPlayerEntity p : players(server)) if (!s.party.contains(p.getUuid())) spectators.add(p.getUuid());
        toWake.clear();
        for (UUID id : s.party) if (server.getPlayerManager().getPlayer(id) == null) toWake.add(id);
        StudentEntity.setShunning(s.devoted, s.livingWith);
        if (save.contains("Accomplice")) {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(UUID.fromString(save.getString("Accomplice")));
            if (a != null) Accomplice.pick(a);
        }
        ServerWorld world = server.getOverworld();
        spawnStudents(world, s);
        for (StudentEntity e : students(server)) if (s.dead.contains(e.getSkin())) e.discard();
        Townsfolk.spawn(world, town);
        Teachers.spawn(world, town);
        LunchHall.spawnCook(world);
        Traffic.spawn(world);
        world.setSpawnPos(town.graph.pos(town.homes.get(s.homes[Roster.TOTAL]).inside()), 0f);
        s.markDirty();
        boolean morning = save.getBoolean("Morning");
        // Developer testing only: as if it was saved after night N.
        if (System.getenv("KOKUHAKU_SAVENIGHT") != null) {
            s.day = Integer.parseInt(System.getenv("KOKUHAKU_SAVENIGHT"));
            morning = true;
        }
        Kokuhaku.LOG.info("Run continued: day {}{}, party of {}, {} still to join", morning ? s.day + 1 : s.day, morning ? " (after the night)" : "", s.party.size(), toWake.size());
        transitioning = true;
        for (ServerPlayerEntity p : players(server)) {
            p.clearStatusEffects();
            restoreBag(p);
            sendState(p, s);
            ServerPlayNetworking.send(p, Net.RUN_START, PacketByteBufs.empty());
            if (spectators.contains(p.getUuid())) {
                gone.add(p.getUuid());
                later(40, () -> {
                    if (s.party.size() == 1) watch(p, s);
                    else becomeGhost(p, s);
                });
            } else if (wasGone.contains(p.getUuid())) {
                gone.add(p.getUuid());
                later(40, () -> becomeGhost(p, s));
            }
        }
        if (morning) {
            later(30, () -> newDay(server, s));
        } else {
            world.setTimeOfDay(Schedule.worldTime(s.day, s.minute));
            for (ServerPlayerEntity p : living(server)) later(30, () -> wakeUp(p, s));
        }
    }

    private static void restoreBag(ServerPlayerEntity p) {
        String id = p.getUuid().toString();
        if (savedBags.contains(id)) {
            p.getInventory().clear();
            p.getInventory().readNbt(savedBags.getList(id, net.minecraft.nbt.NbtElement.COMPOUND_TYPE));
        }
        if (p.getInventory().count(ModItems.ROOM_KEY) == 0) p.getInventory().setStack(1, new ItemStack(ModItems.ROOM_KEY));
    }

    /** Everyone lives in the district the mystery says they do; players get whatever's left, anywhere. */
    private static int[] assignHomes(RunState s, Town town) {
        Random r = new Random(s.seed ^ 0x48304D45L);
        Map<Traits.District, List<Town.Home>> free = new EnumMap<>(Traits.District.class);
        for (Town.Home h : town.homes) free.computeIfAbsent(h.district(), d -> new ArrayList<>()).add(h);
        free.values().forEach(list -> Collections.shuffle(list, r));
        int[] homes = new int[Roster.TOTAL + Roster.MAX_PARTY];
        for (int i : Roster.active(s.party.size())) {
            List<Town.Home> district = free.get(s.mystery().districts[i]);
            if (district.isEmpty()) district = free.values().stream().filter(l -> !l.isEmpty()).findFirst().orElseThrow();
            homes[i] = district.remove(0).index();
        }
        List<Town.Home> rest = new ArrayList<>();
        free.values().forEach(rest::addAll);
        Collections.shuffle(rest, r);
        for (int i = 0; i < s.party.size(); i++) homes[Roster.TOTAL + i] = rest.get(i).index();
        return homes;
    }

    /** Into the menu room, or back into a run you dropped out of. Nobody new gets into a run. */
    public static void onJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.server;
        RunState s = state(server);
        if (!s.started) {
            MenuRoom.placePlayer(player);
            partyChanged = true; // they're not on the player list until a moment after this
        } else if (!s.party.contains(player.getUuid()) && !spectators.contains(player.getUuid())) {
            player.networkHandler.disconnect(Text.literal("That run has already started."));
        } else {
            sendState(player, s);
            ServerPlayNetworking.send(player, Net.RUN_START, PacketByteBufs.empty());
            if (toWake.remove(player.getUuid())) {
                restoreBag(player);
                later(30, () -> wakeUp(player, s));
                return;
            }
            if (watchers.contains(player.getUuid()) || spectators.contains(player.getUuid()) && s.party.size() == 1) watch(player, s);
            else if (gone.contains(player.getUuid())) taken(player, s);
        }
    }

    public static void onLeave(ServerPlayerEntity player) {
        MinecraftServer server = player.server;
        RunState s = state(server);
        if (!s.started) {
            partyChanged = true;
            return;
        }
        lessonPending.remove(player.getUuid());
        lessonSettled.remove(player.getUuid());
        asleep.remove(player.getUuid());
        finishLesson(server, s);
    }

    private static final Map<UUID, int[]> profiles = new HashMap<>();
    /** Sitting this run out, as ghosts. */
    private static final Set<UUID> spectators = new HashSet<>();
    /** Spectators of a solo run, seeing it as the player does. */
    private static final Set<UUID> watchers = new HashSet<>();

    private static ServerPlayerEntity soloPlayer(MinecraftServer server, RunState s) {
        if (s.party.size() != 1) return null;
        ServerPlayerEntity solo = server.getPlayerManager().getPlayer(s.party.iterator().next());
        return solo == null || solo.isRemoved() ? null : solo;
    }

    /** Over their shoulder, through their eyes: whatever they see, you see. */
    private static void watch(ServerPlayerEntity p, RunState s) {
        ServerPlayerEntity solo = soloPlayer(p.server, s);
        if (solo == null) {
            becomeGhost(p, s);
            return;
        }
        watchers.add(p.getUuid());
        p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
        p.teleport(solo.getServerWorld(), solo.getX(), solo.getY(), solo.getZ(), solo.getYaw(), solo.getPitch());
        p.setCameraEntity(solo);
        announce(p, "You're watching.", solo.getGameProfile().getName() + " is playing this one alone. You see what they see.", 0);
    }

    /** Sneaking lets a spectator go, so they're put back. If the one playing is gone, so is the view. */
    private static void keepWatching(MinecraftServer server, RunState s) {
        ServerPlayerEntity solo = soloPlayer(server, s);
        for (UUID id : watchers) {
            ServerPlayerEntity w = server.getPlayerManager().getPlayer(id);
            if (w == null) continue;
            if (solo == null || gone.contains(solo.getUuid())) {
                if (w.getCameraEntity() != w) w.setCameraEntity(w);
                continue;
            }
            if (w.getCameraEntity() != solo) {
                if (w.getServerWorld() != solo.getServerWorld() || w.squaredDistanceTo(solo) > 64 * 64) w.teleport(solo.getServerWorld(), solo.getX(), solo.getY(), solo.getZ(), solo.getYaw(), solo.getPitch());
                w.setCameraEntity(solo);
            }
        }
    }

    public static void profile(ServerPlayerEntity p, int wins, int losses, int badges) {
        profiles.put(p.getUuid(), new int[]{wins, losses, badges});
        partyChanged = true;
    }

    public static void spectate(ServerPlayerEntity p, boolean on) {
        if (state(p.server).started) return;
        if (on) spectators.add(p.getUuid());
        else spectators.remove(p.getUuid());
        partyChanged = true;
    }

    /** Who's in the party, for the panel in the corner of the menu. */
    private static void sendParty(MinecraftServer server) {
        List<ServerPlayerEntity> members = new ArrayList<>(players(server));
        while (members.size() > Roster.MAX_PARTY) {
            members.remove(members.size() - 1).networkHandler.disconnect(Text.literal("That party is full."));
        }
        for (ServerPlayerEntity to : members) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(members.size());
            for (ServerPlayerEntity p : members) {
                buf.writeUuid(p.getUuid());
                buf.writeString(p.getGameProfile().getName());
                buf.writeBoolean(server.isHost(p.getGameProfile()));
                buf.writeBoolean(spectators.contains(p.getUuid()));
                int[] record = profiles.getOrDefault(p.getUuid(), new int[3]);
                for (int value : record) buf.writeVarInt(value);
            }
            ServerPlayNetworking.send(to, Net.PARTY, buf);
        }
    }

    private static void spawnStudents(ServerWorld world, RunState s) {
        for (int i : Roster.active(s.party.size())) {
            StudentEntity e = Kokuhaku.STUDENT.create(world);
            if (e == null) continue;
            var pos = Routines.homePos(i, s, town);
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0f, 0f);
            e.setSkin(i);
            e.setCustomName(Text.literal(Roster.get(i).name()));
            world.spawnEntity(e);
        }
    }

    private static List<? extends StudentEntity> students(MinecraftServer server) {
        return server.getOverworld().getEntitiesByType(Kokuhaku.STUDENT, e -> e.getY() < 90 && !e.isAiDisabled() && !Townsfolk.isOne(e) && e.getPeek() == 0); // not the ones who are only there for a scene, or just passing
    }

    private static int slot(RunState s, ServerPlayerEntity p) {
        return Math.max(0, s.party.indexOf(p.getUuid()));
    }

    private static Town.Home homeOf(RunState s, ServerPlayerEntity p) {
        if (Ward.isAdmitted(p)) return ward(s, p);
        Integer host = sleepover.get(p.getUuid());
        if (host == null && Living.active(s)) host = s.livingWith;
        return town.homes.get(s.homes[host != null ? host : Roster.TOTAL + slot(s, p)]);
    }

    static Town.Home ward(RunState s, ServerPlayerEntity p) {
        return town.wards.get(slot(s, p));
    }

    static List<StudentEntity> studentsOf(MinecraftServer server) {
        return new ArrayList<>(students(server));
    }

    static int curfewOf(RunState s) {
        return Kept.curfew(s, Living.active(s) ? Living.MIDNIGHT : s.difficulty.curfew);
    }

    /** Sat in a lesson, or waiting for one to be over. */
    static boolean inLesson(ServerPlayerEntity p) {
        return lessonPending.contains(p.getUuid());
    }

    static List<ServerPlayerEntity> alive(MinecraftServer server) {
        return living(server);
    }

    /** How many more marks before you're expelled. */
    static int strikesLeft(RunState s) {
        return EXPEL_AT[s.difficulty.ordinal()] - s.strikes;
    }

    /** Developer testing only: what the playthrough bot needs to find its way. */
    public static Town.Home devHome(ServerPlayerEntity p, RunState s) {
        return homeOf(s, p);
    }

    public static boolean devWard(ServerPlayerEntity p) {
        return Ward.isAdmitted(p);
    }

    private static final Map<Long, Integer> failedLinks = new HashMap<>();
    private static final int STREET = 22, SCHOOL_WEST = -640, FAILS_TO_DROP = 3;

    /** Somebody tried this stretch and couldn't get along it. Nobody else need try, so long as there's another way. */
    public static void noWayThrough(Vec3d from, Vec3d to) {
        Integer a = town.graph.at(BlockPos.ofFloored(from)), b = town.graph.at(BlockPos.ofFloored(to));
        if (a == null || b == null || !town.graph.neighbours(a).contains(b)) return;
        // Never stairs or a slope, never the school, and it takes more than one bad moment in a crowd.
        BlockPos at = town.graph.pos(a), to2 = town.graph.pos(b);
        if (at.getY() != to2.getY() || at.getY() != STREET || at.getX() > SCHOOL_WEST || failedLinks.merge(Math.min(a, b) * 100000L + Math.max(a, b), 1, Integer::sum) < FAILS_TO_DROP) return;
        town.graph.unlink(a, b);
        if (town.graph.path(a, b).size() < 2) town.graph.link(a, b);
        else Kokuhaku.LOG.info("No way through from {} to {}", town.graph.pos(a).toShortString(), town.graph.pos(b).toShortString());
    }

    public static List<Vec3d> devRoute(ServerPlayerEntity p, BlockPos to) {
        return WalkRoute.fit(p.getServerWorld(), WalkRoute.routeTo(p, town, town.graph.nearest(Vec3d.ofCenter(to))), p);
    }

    public static BlockPos devFrontDoor(ServerPlayerEntity p, RunState s) {
        return frontDoor(p.getServerWorld(), homeOf(s, p));
    }

    public static BlockPos devStall(int kind) {
        return Festival.stallAt(kind);
    }

    static Town.Home homeFor(ServerPlayerEntity p, RunState s) {
        return homeOf(s, p);
    }

    public static void radioPick(ServerPlayerEntity p, int action) {
        Radio.pick(p, state(p.server), action);
    }

    static void holdClock(boolean held) {
        if (held) transitioning = true;
    }

    static void setBusy(ServerPlayerEntity p, boolean scene) {
        if (scene) busy.add(p.getUuid());
        else busy.remove(p.getUuid());
    }

    static boolean scenePlaying() {
        return transitioning;
    }

    /** Anything going on that she shouldn't walk into. */
    static boolean inScene(ServerPlayerEntity p) {
        return transitioning || busy.contains(p.getUuid()) || gone.contains(p.getUuid()) || Peeker.active();
    }

    static float abductChance(RunState s) {
        return ABDUCT_CHANCE[s.difficulty.ordinal()] * (1f - s.fondness[s.freeYandere()] * 0.12f);
    }

    /** Held fast, with your feet off the ground. */
    static void sendGrab(ServerPlayerEntity p, int keys, float air) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(10);
        for (Vec3d v : List.of(p.getPos(), p.getPos(), new Vec3d(keys, air, 0), Vec3d.ZERO)) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(Grab.LONGEST_TICKS);
        buf.writeString("");
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
    }

    /** The end of it, whichever way it went. */
    static void freed(ServerPlayerEntity p, RunState s) {
        transitioning = false;
        busy.remove(p.getUuid());
        sendBlack(p, homeOf(s, p), 10, "");
    }

    /** It all goes dark, and she carries you off. */
    static void carriedOff(ServerPlayerEntity p, RunState s) {
        transitioning = false;
        busy.remove(p.getUuid());
        found(p);
        taken(p, s);
    }

    static void clearEvidence(BlockPos pos) {
        trail.remove(pos);
    }

    /** Her hand on your shoulder, out in the dark. */
    static void caughtOutside(ServerPlayerEntity p, RunState s) {
        found(p);
        batScene(p, s);
        later(BAT_TICKS - 4, () -> taken(p, s));
    }

    /** You got her attention, in the middle of the night, with the door open. */
    static void takenInTheNight(ServerPlayerEntity p, RunState s) {
        Kokuhaku.LOG.info("Taken from bed in a nightmare");
        found(p);
        taken(p, s);
    }

    static boolean isHomeNow(ServerPlayerEntity p, RunState s) {
        return isHome(p, s);
    }

    static Vec3d bedsideOf(ServerPlayerEntity p, Town.Home home) {
        return bedside(p.getServerWorld(), home);
    }

    static void addStrike(MinecraftServer server, RunState s, int count) {
        strike(server, s, count);
    }

    static void later(int delayTicks, Runnable task) {
        scheduled.add(new Object[]{ticks + delayTicks, task});
    }

    // --------------------------------------------------------------- blocks

    /** Right-clicking blocks: bed, lockers, vending machines, job boards, your door, crime scenes. */
    public static ActionResult useBlock(ServerPlayerEntity player, BlockPos pos) {
        if (town == null) return ActionResult.PASS;
        BlockState state = player.getServerWorld().getBlockState(pos);
        RunState s = state(player.server);
        if (!s.started) return ActionResult.PASS;
        Town.Home home = homeOf(s, player);
        if (isGhost(player) && !town.trueRock.contains(pos) && !town.pages.containsKey(pos) && !trail.containsKey(pos) && !state.isOf(ModBlocks.EVIDENCE)) {
            return state.getBlock() instanceof DoorBlock ? ActionResult.PASS : ActionResult.SUCCESS;
        }
        if (town.trueRock.contains(pos)) {
            Kept.rock();
            Talk.say(player, Pages.TRUE_NAME, Pages.NAME_REVEALED, Talk.leave("..."));
            return ActionResult.SUCCESS;
        }
        if (Practical.use(player, s, state)) return ActionResult.SUCCESS;
        if (town.pages.containsKey(pos)) {
            readPage(player, s, pos);
            return ActionResult.SUCCESS;
        }
        if (state.isOf(ModBlocks.RADIO)) {
            Radio.use(player, s);
            return ActionResult.SUCCESS;
        }
        if (Festival.use(player, s, pos)) return ActionResult.SUCCESS;
        if (Seats.isSeat(state)) {
            Seats.sit(player, pos, state);
            return ActionResult.SUCCESS;
        }

        if (state.getBlock() instanceof BedBlock) {
            if (!home.isBed(pos) && !home.isBed(pos.offset(BedBlock.getOppositePartDirection(state)))) {
                announce(player, "", "This isn't your bed.", 0);
            } else if (s.minute < Schedule.AFTER_SCHOOL && !(s.devoted && !Schedule.isSchoolDay(s.day))) {
                announce(player, "", "You're not tired yet. Maybe after school.", 0);
            } else if (s.status == RunState.Status.PLAYING) {
                intoBed(player, s);
            }
            return ActionResult.SUCCESS;
        }
        if (state.isOf(ModBlocks.VENDING_MACHINE)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(s.yen);
            ServerPlayNetworking.send(player, Net.OPEN_VENDING, buf);
            return ActionResult.SUCCESS;
        }
        if (town.jobBoards.containsKey(pos)) {
            openJob(player, s, town.jobBoards.get(pos));
            return ActionResult.SUCCESS;
        }
        if ("shop".equals(town.registers.get(pos))) {
            Shop.open(player, s);
            return ActionResult.SUCCESS;
        }
        if (Favours.pickedUp(player, pos)) return ActionResult.SUCCESS;
        if (town.registers.containsKey(pos)) {
            Talk.say(player, town.registers.get(pos).startsWith("club") ? "Club register" : "Residents' board", Sleuthing.read(s, town.registers.get(pos)), Talk.leave("Noted."));
            return ActionResult.SUCCESS;
        }
        if (trail.containsKey(pos)) {
            String text = trail.remove(pos);
            player.getServerWorld().breakBlock(pos, false);
            s.notes.add(text);
            s.markDirty();
            Whispers.foundClue();
            Talk.say(player, "You found something", text, Talk.leave("Keep it."));
            return ActionResult.SUCCESS;
        }
        if (state.isOf(ModBlocks.EVIDENCE)) {
            Integer clue = evidence.remove(pos);
            player.getServerWorld().breakBlock(pos, false);
            if (clue != null && s.found.add(clue)) {
                s.markDirty();
                announce(player, "You found something.", s.mystery().clue(clue).text(), 0);
            } else {
                announce(player, "", "Whatever happened here, it tells you nothing more.", 0);
            }
            return ActionResult.SUCCESS;
        }
        if (state.isOf(ModBlocks.LOCKER)) {
            int locker = s.lockers[slot(s, player)];
            if (state.get(LockerBlock.NUMBER) != locker) {
                announce(player, "", "That's not your locker. Yours is number " + locker + ".", 0);
            } else if (Kept.locker(player, s)) {
                return ActionResult.SUCCESS;
            } else if (s.found.add(0)) {
                s.markDirty();
                announce(player, "\"I love you.\"", "A note, folded neatly in your locker. It's in your bag now.", 0);
                later(100, () -> announce(player, "One of the girls wrote this.", "Find out who before day 14. Talk to people, and press E to see what you've got.", 0));
            } else if (Ward.noteDue(s)) {
                Ward.openNote(player, s);
            } else {
                Clue note = s.mystery().clues.stream().filter(c -> c.kind() == Kind.NOTE && c.id() != 0 && c.day() <= s.day && !s.found.contains(c.id())).findFirst().orElse(null);
                if (note == null) {
                    announce(player, "", "Just your shoes.", 0);
                } else {
                    s.found.add(note.id());
                    s.markDirty();
                    announce(player, "", note.text(), 0);
                }
            }
            return ActionResult.SUCCESS;
        }
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lower = state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER ? pos.down() : pos;
            ServerWorld world = player.getServerWorld();
            boolean key = player.getMainHandStack().isOf(ModItems.ROOM_KEY);
            boolean mine = lower.equals(frontDoor(world, home));
            // Everybody else's front door stays shut. Nobody's letting you in.
            if (!mine && houseDoors(world).containsKey(lower)) {
                announce(player, "", key ? "Your key doesn't fit this door." : "It's locked. This isn't your house.", 0);
                return ActionResult.SUCCESS;
            }
            if (mine && !key && s.doorLocked[slot(s, player)]) {
                announce(player, "", "It's locked. Your key's in slot 2.", 0);
                return ActionResult.SUCCESS;
            }
            // The key's for the way in. Every other door just opens.
            if (!mine || !key) return ActionResult.PASS;
            {
                int slot = slot(s, player);
                if (s.lockBrokenOn == s.day) {
                    announce(player, "", "The key just turns. The lock's been broken from the inside.", 0);
                    return ActionResult.SUCCESS;
                }
                s.doorLocked[slot] = !s.doorLocked[slot];
                s.markDirty();
                if (s.doorLocked[slot] && state.getBlock() instanceof DoorBlock door) door.setOpen(null, world, state, pos, false);
                Kokuhaku.LOG.info("Front door at {} {}", lower.toShortString(), s.doorLocked[slot] ? "locked" : "unlocked");
                announce(player, "", s.doorLocked[slot] ? "You lock the front door." : "You unlock the front door.", 0);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** Whether going this way from a door gets you outdoors: under a porch still counts. */
    private static boolean outdoors(ServerWorld world, BlockPos door, Direction way) {
        for (int i = 1; i <= PORCH; i++) {
            BlockPos at = door.offset(way, i);
            if (world.isSkyVisible(at)) return true;
            if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) return false;
        }
        return false;
    }

    /** A door with the street on one side of it and the house on the other. */
    private static boolean opensOutside(ServerWorld world, BlockPos door, BlockState state) {
        Direction facing = state.get(DoorBlock.FACING);
        return outdoors(world, door, facing) != outdoors(world, door, facing.getOpposite());
    }

    /** The door you actually leave through: the first one on the way out to the street, walked from inside the house. */
    private static BlockPos doorOutOf(ServerWorld world, Town.Home home) {
        List<Integer> way = town.graph.path(home.inside(), town.place("station"));
        for (int i = 1; i < way.size() && i < DOOR_SEARCH; i++) {
            Vec3d a = Vec3d.ofBottomCenter(town.graph.pos(way.get(i - 1))), b = Vec3d.ofBottomCenter(town.graph.pos(way.get(i)));
            int steps = (int) Math.ceil(a.distanceTo(b) * 2);
            for (int n = 0; n <= steps; n++) {
                BlockPos at = BlockPos.ofFloored(a.lerp(b, n / (double) Math.max(1, steps)));
                for (BlockPos near : BlockPos.iterate(at.add(-1, -1, -1), at.add(1, 1, 1))) {
                    BlockState state = world.getBlockState(near);
                    if (state.getBlock() instanceof DoorBlock && state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER && opensOutside(world, near, state)) return near.toImmutable();
                }
            }
        }
        return null;
    }

    /** When the way out doesn't go through one: whichever outside door's nearest the bed, or failing that any door at all. */
    private static BlockPos nearestOutsideDoor(ServerWorld world, Town.Home home) {
        BlockPos best = null, any = null;
        for (BlockPos p : BlockPos.iterateOutwards(home.bedHead(), DOOR_REACH, 8, DOOR_REACH)) {
            BlockState state = world.getBlockState(p);
            if (!(state.getBlock() instanceof DoorBlock) || state.get(DoorBlock.HALF) != net.minecraft.block.enums.DoubleBlockHalf.LOWER) continue;
            if (any == null) any = p.toImmutable();
            if (best == null && opensOutside(world, p, state)) best = p.toImmutable();
        }
        return best != null ? best : any;
    }

    /** The way into a home. Null if there's no such thing. */
    static BlockPos frontDoor(ServerWorld world, Town.Home home) {
        if (home.index() < 0) return null;
        houseDoors(world);
        return frontDoors.get(home.bedHead());
    }

    /** Every house's front door, and whose it is. */
    private static Map<BlockPos, Integer> houseDoors(ServerWorld world) {
        if (doorOwners.isEmpty()) {
            for (Town.Home home : town.homes) {
                BlockPos door = doorOutOf(world, home);
                if (door == null) door = nearestOutsideDoor(world, home);
                if (door == null) {
                    Kokuhaku.LOG.info("No front door found for the home at {}", home.bedHead().toShortString());
                    continue;
                }
                frontDoors.put(home.bedHead(), door);
                doorOwners.put(door, home.index());
            }
            Kokuhaku.LOG.info("{} front doors for {} homes", doorOwners.size(), town.homes.size());
        }
        return doorOwners;
    }

    /** Whoever's calling up the stairs, if anyone. They're waiting at your front door. */
    private static void callDownstairs(ServerPlayerEntity p, RunState s) {
        BlockPos door = frontDoor(p.getServerWorld(), homeOf(s, p));
        if (door == null || homeOf(s, p).contains(Vec3d.ofCenter(door))) {
            WalkScene.start(p, s, town);
            return;
        }
        calledDown.add(p.getUuid());
        Talk.say(p, "From downstairs", "\"" + p.getGameProfile().getName() + "! Are you coming or not? I'm at the front door. We're going to be late!\"", Talk.leave("Coming!"));
    }

    /** They only wait so long. */
    private static void watchFrontDoors(MinecraftServer server, RunState s) {
        for (ServerPlayerEntity p : living(server)) {
            if (!calledDown.contains(p.getUuid())) continue;
            BlockPos door = frontDoor(p.getServerWorld(), homeOf(s, p));
            if (door != null && p.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(door)) < AT_THE_DOOR * AT_THE_DOOR && !inScene(p)) {
                calledDown.remove(p.getUuid());
                WalkScene.start(p, s, town);
            } else if (s.minute >= GIVE_UP_WAITING) {
                calledDown.remove(p.getUuid());
                announce(p, "", "Nobody's at the door any more. You'll have to walk in on your own.", 0);
            }
        }
    }

    private static void readPage(ServerPlayerEntity p, RunState s, BlockPos pos) {
        int page = town.pages.get(pos);
        if (!s.pages.add(page)) {
            announce(p, "", "You've already read this one.", 0);
            return;
        }
        p.getServerWorld().breakBlock(pos, false);
        String text = Pages.text(page, s);
        s.notes.add(text);
        s.markDirty();
        Kokuhaku.LOG.info("Diary page {} of {}", s.pages.size(), Pages.COUNT);
        if (Kept.on(s)) {
            Kept.pageFound(p, s, page);
            Talk.say(p, "Page " + s.pages.size() + " of " + Pages.COUNT, text, Talk.option("Keep it.", q -> {
                if (page >= Pages.PLACES.length) Kept.fileFound(q, s);
            }));
            return;
        }
        // The last page is in the records room, and from the fourth run there's something clipped behind it.
        if (page >= Pages.PLACES.length && Story.runs() >= Story.CHART_FROM) {
            s.notes.add(Story.chart());
            Talk.say(p, "Page " + s.pages.size() + " of " + Pages.COUNT, text, Talk.option("There's something behind it.", q -> Talk.say(q, "In the records room", Story.chart(), Talk.leave("..."))));
            return;
        }
        Talk.say(p, "Page " + s.pages.size() + " of " + Pages.COUNT, text, Talk.leave(s.pages.size() == Pages.COUNT ? "That's all of them." : "Keep it."));
    }

    // ---------------------------------------------------------------- clock

    public static void tick(MinecraftServer server) {
        RunState s = state(server);
        Railway.tick(server, players(server));
        if (!s.started) {
            if (partyChanged) {
                partyChanged = false;
                sendParty(server);
            }
            return;
        }
        if (s.status != RunState.Status.PLAYING) return;
        ticks++;
        Peeker.tick();
        Seats.tick();
        if (ticks % 40 == 0) Townsfolk.tick(server, s, town);
        Crossings.tick(living(server));
        Traffic.tick(server, living(server));
        if (ticks % CHATTER_EVERY == 0) Chatter.tick(s, students(server), living(server), ticks);
        devMeasureSpeed(server);
        WalkScene.tick();
        if (ticks % 10 == 0 && !watchers.isEmpty()) keepWatching(server, s);
        Paths.tick();
        ForestScares.tick(s, living(server));
        LunchScene.tick(server, s, living(server), town);
        SchoolLife.tick(server, s, living(server), town);
        TrainRide.tick(s, living(server), town);
        if (ticks % 20 == 0) SkiSlope.tick(s, living(server), town);
        Pe.tick(s);
        Chase.tick(s);
        if (ticks % GHOST_GLOW_EVERY == 0) {
            for (ServerPlayerEntity p : players(server)) if (isGhost(p)) p.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 2, 0.2, 0.4, 0.2, 0.01);
        }
        Living.tick(server, s, living(server), ticks);
        Stalker.tick(s, living(server), town, !transitioning, ticks);
        Grab.tick(s, living(server), players(server), !transitioning, ticks);
        devAutoTalk(server);
        if (ticks % 10 == 0) {
            watchExits(server, s);
            watchFrontDoors(server, s);
        }
        for (Object[] task : new ArrayList<>(scheduled)) {
            if ((int) task[0] <= ticks) {
                scheduled.remove(task);
                ((Runnable) task[1]).run();
            }
        }
        if (ticks % 20 == 0) {
            for (ServerPlayerEntity p : players(server)) {
                sendState(p, s);
                p.setStepHeight(1f); // no jumping, so a one-block step can't be allowed to trap you
            }
        }
        if (!transitioning && !lessonHeld(s) && !living(server).isEmpty() && ticks % Schedule.TICKS_PER_MINUTE == 0) advanceMinute(server, s);
    }

    private static List<ServerPlayerEntity> players(MinecraftServer server) {
        return server.getPlayerManager().getPlayerList();
    }

    private static List<ServerPlayerEntity> living(MinecraftServer server) {
        return players(server).stream().filter(p -> !gone.contains(p.getUuid())).toList();
    }

    /** Your voice carries this far, fading as it goes. In the menu room, everyone's together. */
    private static final double VOICE_RANGE = 24;
    private static final int CHATTER_EVERY = 40;

    public static void voice(ServerPlayerEntity from, byte[] data) {
        MinecraftServer server = from.server;
        boolean started = state(server).started;
        // One of you is lying, and a voice would settle it too easily.
        if (started && Accomplice.active()) return;
        boolean dead = gone.contains(from.getUuid()) && !isGhost(from);
        for (ServerPlayerEntity to : players(server)) {
            // The dead can't tell the living anything. Ghosts can.
            if (to == from || gone.contains(to.getUuid()) && !isGhost(to) != dead) continue;
            double distance = to.distanceTo(from);
            if (started && distance >= VOICE_RANGE) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(from.getUuid());
            buf.writeFloat(started ? (float) (1 - distance / VOICE_RANGE) : 1f);
            buf.writeByteArray(data);
            ServerPlayNetworking.send(to, Net.VOICE_OUT, buf);
        }
    }

    private static void advanceMinute(MinecraftServer server, RunState s) {
        s.minute++;
        s.markDirty();
        server.getOverworld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        int m = s.minute;
        // Day 31: there's only leaving the bedroom, and she won't wait long for that either.
        if (s.day > Schedule.DAYS) {
            for (ServerPlayerEntity p : living(server)) {
                if (!isHome(p, s) || m >= Schedule.WAKE + 20) {
                    struck(server, s);
                    return;
                }
            }
            return;
        }
        boolean school = Schedule.isSchoolDay(s.day);

        if (school) {
            if (m == Schedule.HOMEROOM) announceAll(server, "Homeroom", "Room " + Schedule.roomKey(Schedule.HOMEROOM_2A));
            List<String> subjects = Schedule.subjects(s.seed, Schedule.weekday(s.day));
            for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
                int start = Schedule.PERIOD_START[p];
                if (m == start) announceAll(server, "Period " + (p + 1), subjects.get(p) + "  ·  " + Schedule.room(subjects.get(p), s.day));
                if (m >= start && m <= start + Schedule.LATE_GRACE) checkLesson(server, s, p, subjects.get(p));
            }
            if (m == Schedule.LUNCH) announceAll(server, "Lunch", "");
            if (m == Schedule.AFTER_SCHOOL) afterSchool(server, s);
            if (m >= Schedule.WAKE + 60 && m < Schedule.HOMEROOM) maybeFriend(server, s);
        }
        if (m == Schedule.EVENING) announceAll(server, "Evening", "The streetlights are coming on.");
        int curfew = curfewOf(s);
        if (m == curfew - 60) announceAll(server, "", "Be home by " + Schedule.clock(curfew) + ".");
        if (m == curfew - 10) {
            for (ServerPlayerEntity p : living(server)) if (!isHome(p, s)) announce(p, "", "You hear footsteps behind you.", 0);
        }
        if (m >= curfew) {
            if (Living.active(s)) {
                if (!transitioning && !living(server).isEmpty()) Living.midnight(living(server).get(0), s);
                return;
            }
            curfewCaught(server, s);
            return;
        }
        var all = students(server);
        for (StudentEntity e : all) if (e.getSkin() != s.livingWith) Routines.update(e, s, town);
        for (ServerPlayerEntity p : living(server)) {
            Radio.minute(p, s);
            HomeChanges.minute(p, s);
            if (!transitioning && !WalkScene.active(p)) Story.minute(p, s);
        }
        Blackout.minute(s, living(server), town);
        Rain.minute(server, s, living(server));
        Teachers.update(server, s, town);
        Festival.minute(server, s, living(server), town);
        Phone.minute(server, s, living(server), town);
        if (!transitioning && lessonPending.isEmpty() && !living(server).isEmpty() && !WalkScene.active(living(server).get(0))) {
            Scenarios.tick(living(server).get(0), s);
            for (ServerPlayerEntity p : living(server)) overhear(p, s, all);
        }
        if (m % 30 == 0) {
            if (System.getenv("KOKUHAKU_BOT") != null) {
                long due = 0, there = 0;
                for (StudentEntity e : all) {
                    if (e.getSkin() >= Roster.TOTAL) continue;
                    if (town.graph.pos(Routines.target(e.getSkin(), s, town).node()).getY() < Town.UPPER_FLOOR) continue;
                    due++;
                    if (e.getY() >= Town.UPPER_FLOOR - 1) there++;
                }
                Kokuhaku.LOG.info("Upstairs: {} of {} who should be are", there, due);
            }
            long arrived = all.stream().filter(e -> !e.hasRoute()).count();
            int left = all.stream().mapToInt(StudentEntity::waypointsLeft).sum();
            Kokuhaku.LOG.info("Day {} {}: {}/{} students at their destination, {} waypoints left in total, {} stuck-hops so far",
                    s.day, Schedule.clock(m), arrived, all.size(), left, com.kokuhaku.entity.RouteGoal.stuckHops);
            ServerWorld overworld = server.getOverworld();
            var onFoot = overworld.getEntitiesByType(Kokuhaku.STUDENT, e -> e.getY() < 25 && !e.hasVehicle());
            long dipped = onFoot.stream().filter(e -> e.getY() < 21.9 && e.getY() > 20).count();
            if (dipped > 0) Kokuhaku.LOG.info("Below street level: {} of {} at {}", dipped, onFoot.size(), onFoot.stream().filter(e -> e.getY() < 21.9 && e.getY() > 20).map(e -> e.getBlockX() + "," + (float) e.getY() + "," + e.getBlockZ() + " on " + overworld.getBlockState(e.getBlockPos()).getBlock()).limit(4).toList());
            long onRoad = onFoot.stream().filter(e -> Town.isRoad(overworld, e.getBlockX(), e.getBlockZ(), Town.WIDE_TURN)).count();
            if (onRoad > 0) Kokuhaku.LOG.info("On the road outside a crossing: {} of {} at {}", onRoad, onFoot.size(), onFoot.stream().filter(e -> Town.isRoad(overworld, e.getBlockX(), e.getBlockZ(), Town.WIDE_TURN)).map(e -> e.getBlockX() + "," + e.getBlockZ()).limit(6).toList());
        }
    }

    private static boolean isHome(ServerPlayerEntity player, RunState s) {
        return homeOf(s, player).contains(player.getPos());
    }

    // --------------------------------------------------------------- school

    /** The clock waits for whoever's still answering, once the last latecomers have had their chance. */
    private static boolean lessonHeld(RunState s) {
        return !lessonPending.isEmpty() && s.minute >= lessonGraceEnd;
    }

    /** In the room: your lesson starts. Not there when the grace period runs out: absent. */
    private static void checkLesson(MinecraftServer server, RunState s, int period, String subject) {
        int start = Schedule.PERIOD_START[period];
        int key = s.day * 10 + period;
        if (lessonKey != key) {
            lessonKey = key;
            lessonPending.clear();
            lessonSettled.clear();
            lessonAttended = false;
            lessonGraceEnd = start + Schedule.LATE_GRACE;
            lessonEnd = start + Schedule.PERIOD_LENGTH;
        }
        for (ServerPlayerEntity p : living(server)) {
            if (lessonSettled.contains(p.getUuid())) continue;
            int number = Schedule.roomNumber(subject);
            boolean practical = Schedule.practical(subject, s.day);
            if (practical ? Practical.there(p.getPos(), subject) : number == 0 ? Pe.there(p.getPos(), s.day) : town.rooms.get(Schedule.roomKey(number)).contains(p.getPos())) {
                lessonSettled.add(p.getUuid());
                lessonPending.add(p.getUuid());
                if (!lessonAttended) {
                    lessonAttended = true;
                    for (StudentEntity e : students(server)) if (e.getSkin() != s.livingWith) Routines.settle(e, s, town);
                    Teachers.settle(server, s, town);
                }
                StudentEntity partner = Living.partner(p, s);
                if (partner != null) {
                    Living.classTime(p, s, partner);
                    continue;
                }
                if (practical) {
                    Practical.start(p, s, subject);
                    continue;
                }
                if (number == 0) {
                    Pe.start(p, s);
                    continue;
                }
                Random r = new Random(s.seed + s.day * 101L + period);
                PacketByteBuf buf = PacketByteBufs.create();
                List<Integer> girls = new ArrayList<>();
                // Only the girls actually in the room with you.
                for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.dead.contains(g) && Routines.group(Roster.get(g)) == 0) girls.add(g);
                if (girls.isEmpty()) girls.add(0);
                String intro = Lessons.intro(subject, r);
                int level = s.difficulty.ordinal();
                takeSeat(p, s, number);
                NbtCompound lesson = Lessons.toNbt(subject, intro, Lessons.make(subject, r, girls, level), level >= 2);
                Town.Room board = town.rooms.get(Schedule.roomKey(number));
                lesson.putDouble("BoardX", (board.x1() + board.x2() + 1) / 2.0);
                lesson.putDouble("BoardY", board.y() + BOARD_HEIGHT);
                lesson.putDouble("BoardZ", board.z1() + BOARD_FACE);
                lesson.putString("Teacher", Teachers.nameFor(subject));
                lesson.putInt("Level", level);
                buf.writeNbt(lesson);
                ServerPlayNetworking.send(p, Net.LESSON, buf);
            } else if (s.minute == start + Schedule.LATE_GRACE) {
                lessonSettled.add(p.getUuid());
                strike(server, s, 1);
                Whispers.absent();
                announce(p, "", "You missed " + subject + " in " + Schedule.room(subject, s.day) + ". " + Teachers.nameFor(subject) + " marked you absent.", 0);
            } else if (s.minute == start + 1) {
                announce(p, "", "You're late. " + subject + " is in " + Schedule.room(subject, s.day) + ".", 0);
            }
        }
        finishLesson(server, s);
    }

    /** Once everyone's done or marked absent, time skips to the end of the lesson. */
    private static void finishLesson(MinecraftServer server, RunState s) {
        if (!lessonAttended || !lessonPending.isEmpty() || s.minute >= lessonEnd) return;
        for (ServerPlayerEntity p : living(server)) if (!lessonSettled.contains(p.getUuid())) return;
        s.minute = lessonEnd;
        server.getOverworld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        s.markDirty();
        for (ServerPlayerEntity p : living(server)) announceNext(p, s);
    }

    /** Tells you where to be next, as a lesson ends. */
    private static void announceNext(ServerPlayerEntity player, RunState s) {
        List<String> subjects = Schedule.subjects(s.seed, Schedule.weekday(s.day));
        for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
            if (Schedule.PERIOD_START[p] > s.minute) {
                String when = Schedule.clock(Schedule.PERIOD_START[p]);
                announce(player, "", "Next: " + subjects.get(p) + ", " + Schedule.room(subjects.get(p), s.day) + " at " + when + ".", 0);
                return;
            }
        }
    }

    private static boolean isAtSchool(ServerPlayerEntity p) {
        return p.getX() > -620 && p.getX() < -500 && p.getZ() > -135 && p.getZ() < 0;
    }

    /** Your answers are in. Wrong ones cost strikes, and two or more mean detention. */
    public static void lessonDone(ServerPlayerEntity player, int wrong, int prizes, List<int[]> notes) {
        RunState s = state(player.server);
        if (!lessonPending.remove(player.getUuid())) return;
        leaveSeat(player);
        int won = Math.max(0, Math.min(MOST_HANDS, prizes)) * HAND_PRIZE;
        if (won > 0) {
            s.yen += won;
            s.markDirty();
            later(60, () -> announce(player, "Hand up", "¥" + won + " from the class merit tin.", 0));
        }
        if (wrong > 0) {
            int fine = wrong * FINE_PER_WRONG;
            if (s.yen > 0) s.yen = Math.max(0, s.yen - fine);
            announce(player, wrong + " wrong.", "Your parents were called. They took ¥" + fine + " of your allowance.", 0);
        } else {
            announce(player, "All correct.", "The teacher barely looks up.", 0);
        }
        if (wrong >= BAD_LESSON && !s.detention) {
            s.detention = true;
            announce(player, "", "Detention after school.", 0);
        }
        for (int[] note : notes) {
            int girl = note[0], change = Integer.signum(note[1]);
            if (girl < 0 || girl >= Roster.girlsFor(s.party.size()) || change == 0) continue;
            s.fondness[girl] = Math.max(-5, Math.min(5, s.fondness[girl] + change));
            s.markDirty();
            String name = Roster.get(girl).firstName();
            announce(player, "", change > 0 ? name + " smiles down at her desk." : name + " doesn't look at you again.", 0);
        }
        if (wrong >= BAD_LESSON) strike(player.server, s, 1);
        finishLesson(player.server, s);
    }


    /** Into your seat: the empty one behind everyone in your group, facing the board. */
    private static void takeSeat(ServerPlayerEntity p, RunState s, int number) {
        List<Town.Seat> chairs = town.seats.get(Schedule.roomKey(number)).stream().filter(seat -> seat.chair() != null).toList();
        int taken = 0;
        for (Roster.Student st : Roster.STUDENTS) if (Routines.group(st) == 0) taken++;
        Town.Seat seat = chairs.get(Math.min(chairs.size() - 1, taken + slot(s, p)));
        BlockPos chair = BlockPos.ofFloored(seat.chair());
        if (p.hasVehicle() || !Seats.isSeat(p.getServerWorld().getBlockState(chair))) return;
        p.teleport(p.getServerWorld(), seat.chair().x, seat.chair().y + 1, seat.chair().z, 180f, 0f);
        Seats.sit(p, chair, p.getServerWorld().getBlockState(chair));
        desks.put(p.getUuid(), seat.node());
    }

    /** Out of the chair and into the aisle. */
    private static void leaveSeat(ServerPlayerEntity p) {
        Integer aisle = desks.remove(p.getUuid());
        if (aisle == null) return;
        p.stopRiding();
        BlockPos at = town.graph.pos(aisle);
        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYaw(), 0f);
    }

    private static boolean isGhost(ServerPlayerEntity p) {
        return ghosts.contains(p.getUuid());
    }

    /** Who's gone and could be called back, if somebody finds the money. */
    private static List<String> callable(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity p : players(server)) if (gone.contains(p.getUuid()) && !isGhost(p) && !Accomplice.is(p)) names.add(p.getGameProfile().getName());
        return names;
    }

    /** It costs what it costs. They come back, but not all the way. */
    public static void callBack(ServerPlayerEntity payer, String name) {
        RunState s = state(payer.server);
        if (s.status != RunState.Status.PLAYING || gone.contains(payer.getUuid()) || !callable(payer.server).contains(name)) return;
        if (s.yen < GHOST_COST) {
            announce(payer, "", "It costs ¥" + GHOST_COST + ". You've got ¥" + s.yen + ".", 0);
            return;
        }
        s.yen -= GHOST_COST;
        s.markDirty();
        for (ServerPlayerEntity p : players(payer.server)) if (p.getGameProfile().getName().equals(name)) becomeGhost(p, s);
    }

    private static void becomeGhost(ServerPlayerEntity p, RunState s) {
        ghosts.add(p.getUuid());
        Kokuhaku.LOG.info("{} came back as a ghost", p.getGameProfile().getName());
        p.clearStatusEffects();
        p.setInvisible(true);
        p.setInvulnerable(true);
        p.getAbilities().invulnerable = true;
        p.getAbilities().allowFlying = true;
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
        BlockPos at = town.graph.pos(town.foyer);
        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        ServerPlayNetworking.send(p, Net.GHOST, PacketByteBufs.empty());
        boolean watching = spectators.contains(p.getUuid());
        for (ServerPlayerEntity q : players(p.server)) if (!watching) announce(q, "", p.getGameProfile().getName() + " has come back. Not all the way.", 0);
        announce(p, "You're a ghost.", watching ? "You're watching this one. Search where people die, and tell the others what you find." : "Nothing can touch you. Search where people died, and tell the others what you find. You can't remember her face.", 0);
    }

    /** Where someone died and nobody's looked yet, for a ghost's arrow. */
    private static BlockPos nearestScene(ServerPlayerEntity p) {
        BlockPos best = null;
        List<BlockPos> scenes = new ArrayList<>(evidence.keySet());
        scenes.addAll(trail.keySet());
        for (BlockPos at : scenes) if (best == null || at.getSquaredDistance(p.getPos()) < best.getSquaredDistance(p.getPos())) best = at;
        return best;
    }

    /** Nobody left to be haunted for. */
    private static void nobodyLeft(MinecraftServer server, RunState s) {
        if (s.status != RunState.Status.PLAYING || !living(server).isEmpty() || ghosts.isEmpty()) return;
        s.status = RunState.Status.LOST; SaveSlot.delete();
        s.markDirty();
        for (ServerPlayerEntity p : players(server)) {
            if (!isGhost(p)) continue;
            Shadow.send(p, s, false);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(0);
            buf.writeVarInt(s.freeYandere());
            buf.writeString("There's nobody left who can hear you.");
            ServerPlayNetworking.send(p, Net.END, buf);
        }
    }

    /** PE's over: how it went, and maybe a little something from the coach. */
    static void peDone(ServerPlayerEntity p, int score, int outOf, String who, String line, int prize) {
        RunState s = state(p.server);
        if (!lessonPending.remove(p.getUuid())) return;
        busy.remove(p.getUuid());
        Kokuhaku.LOG.info("PE: {} of {}", score, outOf);
        if (prize > 0) {
            s.yen += prize;
            s.markDirty();
        }
        announce(p, who, line + (prize > 0 ? " +¥" + prize : ""), 0);
        finishLesson(p.server, s);
    }

    /** She's done with you. The bell's about to go anyway. */
    static void classOver(ServerPlayerEntity p, RunState s) {
        if (!lessonPending.remove(p.getUuid())) return;
        Kokuhaku.LOG.info("Class over at {}", Schedule.clock(s.minute));
        finishLesson(p.server, s);
    }

    private static void afterSchool(MinecraftServer server, RunState s) {
        announceAll(server, "After School", "Clubs have started.");
        if (!s.detention) return;
        s.detention = false;
        s.minute += 90;
        announceAll(server, "Detention", "Ninety minutes of nothing. The sky is already going dark.");
    }

    private static void strike(MinecraftServer server, RunState s, int count) {
        s.strikes += count;
        s.markDirty();
        if (count > 0 && s.strikes == EXPEL_AT[s.difficulty.ordinal()] - WARN_BEFORE) announceAll(server, "Final warning", "The head teacher has written to your parents. One more bad week and you're out.");
        if (s.status == RunState.Status.PLAYING && s.strikes >= EXPEL_AT[s.difficulty.ordinal()]) {
            end(server, s, 0, "You were expelled.\n\nYou never found out who she was.\nShe knows where you live.");
        }
    }

    /**
     * Walking out of the school after classes, sometimes, someone's watching
     * from round a corner. Checked once a day, the first time you step outside.
     */
    private static void watchExits(MinecraftServer server, RunState s) {
        if (Living.active(s)) return; // she doesn't need to watch from a corner any more
        boolean forced = System.getenv("KOKUHAKU_PEEK") != null;
        if (peekTries > 0 && !living(server).isEmpty()) {
            peekTries--;
            ServerPlayerEntity p = living(server).get(0);
            if (peekWatcher != null) for (ServerPlayerEntity q : living(server)) if (q.getUuid().equals(peekWatcher)) p = q;
            if (peekTries == 0) Kokuhaku.LOG.info("No corner to watch from near {}", p.getBlockPos());
            if (Peeker.start(p, s.freeYandere())) {
                peekTries = 0;
                Shadow.saw(s, "She followed you out of school and watched you from round the corner.");
                peekScene(p, s);
                if ("auto".equals(System.getenv("KOKUHAKU_PEEK"))) {
                    ServerPlayerEntity viewer = p;
                    for (int delay : new int[]{30, 60, 110, 130, 150}) later(delay, () -> ServerPlayNetworking.send(viewer, Net.SHOT, PacketByteBufs.empty()));
                }
            }
        }
        for (ServerPlayerEntity p : living(server)) {
            boolean inside = nearSchool(p) && !p.getServerWorld().isSkyVisible(p.getBlockPos().up());
            Boolean was = insideSchool.put(p.getUuid(), inside);
            if (Boolean.TRUE.equals(was) && !inside && nearSchool(p) && peekedOn != s.day
                    && s.minute >= Schedule.AFTER_SCHOOL && (forced || Schedule.isSchoolDay(s.day))) {
                peekedOn = s.day;
                Kokuhaku.LOG.info("Left the school at {}", p.getBlockPos());
                if (forced || RANDOM.nextFloat() < PEEK_CHANCE) {
                    peekTries = 20; // ten seconds to find a corner as you walk
                    peekWatcher = p.getUuid();
                }
            }
        }
    }

    private static final Map<Integer, Vec3d> devSpeed = new HashMap<>();

    /** Developer testing only (KOKUHAKU_SPEEDTEST=1): how fast students really walk. */
    private static void devMeasureSpeed(MinecraftServer server) {
        if (System.getenv("KOKUHAKU_SPEEDTEST") == null || ticks % 40 != 0) return;
        List<Double> speeds = new ArrayList<>();
        for (StudentEntity e : students(server)) {
            Vec3d was = devSpeed.put(e.getSkin(), e.getPos());
            if (was != null && e.hasRoute()) speeds.add(Math.sqrt(was.squaredDistanceTo(e.getX(), was.y, e.getZ())) / 2);
        }
        Collections.sort(speeds);
        if (speeds.size() > 3) Kokuhaku.LOG.info("SPEED walkers {} median {} top {}", speeds.size(), speeds.get(speeds.size() / 2), speeds.get(speeds.size() - 2));
    }

    /** Your head turns to her, she bolts, you run round the corner, and you're left standing there. */
    private static void peekScene(ServerPlayerEntity p, RunState s) {
        Vec3d round = Peeker.hiding(), away = Peeker.escape();
        int length = Peeker.sceneTicks();
        PacketByteBuf path = PacketByteBufs.create();
        path.writeVarInt(Peeker.route().size());
        for (Vec3d v : Peeker.route()) {
            path.writeDouble(v.x);
            path.writeDouble(v.y);
            path.writeDouble(v.z);
        }
        ServerPlayNetworking.send(p, Net.PEEK_PATH, path);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(5);
        for (Vec3d v : List.of(round, Peeker.face(), Peeker.corner(), away)) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(length);
        buf.writeString("");
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
        float yaw = (float) (Math.toDegrees(Math.atan2(away.z - round.z, away.x - round.x)) - 90);
        later(length - 2, () -> p.teleport(p.getServerWorld(), round.x, round.y, round.z, yaw, 0f));
    }

    /** Developer testing only (KOKUHAKU_SIT=1): sits you in the first chair of Room 01. */
    private static void devSit(ServerPlayerEntity p) {
        Vec3d chair = town.seats.get("01").get(0).chair();
        BlockPos pos = BlockPos.ofFloored(chair);
        p.teleport(p.getServerWorld(), chair.x, chair.y + 1, chair.z + 1, 180f, 0f);
        later(10, () -> {
            Kokuhaku.LOG.info("Sitting on {}", p.getServerWorld().getBlockState(pos));
            if (Seats.isSeat(p.getServerWorld().getBlockState(pos))) Seats.sit(p, pos, p.getServerWorld().getBlockState(pos));
            later(30, () -> {
                Kokuhaku.LOG.info("Riding: {} at {}", p.hasVehicle(), p.getPos());
                ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
            });
        });
    }

    private static boolean nearSchool(ServerPlayerEntity p) {
        return p.getX() > -640 && p.getX() < -520 && p.getZ() > -140 && p.getZ() < 15;
    }

    /** Developer testing only (KOKUHAKU_PEEK=1): it's after school and you're in the foyer, so walk out of the entrance. */
    private static void devPeek(ServerPlayerEntity p, RunState s) {
        s.minute = Schedule.AFTER_SCHOOL + 1;
        p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        BlockPos at = town.graph.pos(town.foyer);
        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
        if ("auto".equals(System.getenv("KOKUHAKU_PEEK"))) {
            later(50, () -> {
                BlockPos out = town.graph.pos(town.graph.nearest(new Vec3d(-622.5, 23, -83.5)));
                p.teleport(p.getServerWorld(), out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 90f, 0f);
            });
        }
    }

    /** On the way to school, someone sometimes calls out and catches you up. */
    private static void maybeFriend(MinecraftServer server, RunState s) {
        if (friendCameToday || RANDOM.nextInt(20) != 0) return;
        for (ServerPlayerEntity p : living(server)) {
            if (isHome(p, s) || isAtSchool(p)) continue;
            StudentEntity best = null;
            double bestD = 40 * 40;
            for (StudentEntity e : students(server)) {
                double d = e.squaredDistanceTo(p);
                if (d < bestD && d > 9 && !s.mystery().isYandere(e.getSkin())) {
                    bestD = d;
                    best = e;
                }
            }
            if (best == null) continue;
            friendCameToday = true;
            best.catchUpWith(p);
            announce(p, "", Roster.get(best.getSkin()).firstName() + ": \"" + Dialogue.waitUp() + "\"", 0);
        }
    }

    public static void friendArrived(ServerPlayerEntity player, StudentEntity student) {
        sendDialogue(player, Roster.get(student.getSkin()), Dialogue.walking());
    }

    // -------------------------------------------------------- sleep and wake

    public static boolean inBedScene() {
        return transitioning;
    }

    /** The night only starts once the whole party is in bed. */
    private static void intoBed(ServerPlayerEntity player, RunState s) {
        Radio.bedtime(player, s);
        asleep.add(player.getUuid());
        List<ServerPlayerEntity> all = living(player.server);
        if (all.stream().allMatch(p -> asleep.contains(p.getUuid()))) {
            goToSleep(player.server, s);
            return;
        }
        announce(player, "", "You lie awake, waiting for the others.", 0);
        for (ServerPlayerEntity p : all) {
            if (p != player && !asleep.contains(p.getUuid())) announce(p, "", player.getGameProfile().getName() + " has gone to bed.", 0);
        }
    }

    /** You walk to bed, the camera fades out, and the night happens in the dark. */
    private static void goToSleep(MinecraftServer server, RunState s) {
        if (transitioning) return;
        transitioning = true;
        asleep.clear();
        Stalker.vanish();
        // Each scene runs a little past the start of the next, so there's never a gap showing you stood by the bed.
        for (ServerPlayerEntity p : living(server)) sendCutscene(p, 0, homeOf(s, p), SLEEP_TICKS + SCENE_OVERLAP, "");
        later(SLEEP_TICKS, () -> {
            List<ServerPlayerEntity> all = living(server);
            if (all.isEmpty() || !Nightmares.play(all.get(0), s, homeOf(s, all.get(0)), () -> nightFalls(server, s))) nightFalls(server, s);
        });
    }

    private static void nightFalls(MinecraftServer server, RunState s) {
        for (ServerPlayerEntity p : new ArrayList<>(living(server))) {
            Integer host = sleepover.get(p.getUuid());
            if (host != null) {
                if (s.mystery().isYandere(host) && !s.caught.contains(host)) {
                    // There was never going to be a morning.
                    Kokuhaku.LOG.info("The sleepover was with her: {} is taken", p.getGameProfile().getName());
                    sleepover.remove(p.getUuid());
                    found(p);
                    taken(p, s);
                } else {
                    confide(p, s, host);
                }
                continue;
            }
            if (Living.active(s) || Ward.isAdmitted(p) || Accomplice.is(p)) continue;
            // She's less likely to come for someone she thinks loves her back.
            float chance = Festival.safeTonight(s) ? 0f : Romance.nightChance(s, ABDUCT_CHANCE[s.difficulty.ordinal()] * (1f - s.fondness[s.freeYandere()] * 0.12f));
            // A chain holds whatever she's done to the lock.
            boolean chained = s.chains > 0;
            if (chained) {
                s.chains--;
                s.markDirty();
            }
            boolean broken = s.lockBrokenOn == s.day && !chained, locked = s.doorLocked[slot(s, p)] && !broken || chained;
            Whispers.door(locked);
            if (locked && s.day >= 2 && RANDOM.nextInt(4) == 0) Shadow.saw(s, "She stood outside your locked door until it got light.");
            if (!locked && RANDOM.nextFloat() < chance) {
                found(p);
                taken(p, s);
            } else {
                // Left unlocked, and she came anyway. She only looked, this time.
                if (!locked && s.day >= 2 && Whispers.leaveFlower(p.getServerWorld(), homeOf(s, p))) {
                    nightNews.add("There's a red flower beside your bed. There are " + Whispers.flowers() + " now.");
                    Shadow.saw(s, "You left your door open. She sat on the end of your bed for an hour, then left a flower.");
                } else if (s.party.size() == 1) {
                    String heard = Scenarios.night(s, locked, broken);
                    if (!heard.isEmpty()) nightNews.add(heard);
                }
            }
        }
        if (living(server).isEmpty()) return;
        Romance.night(s);
        String death = maybeDeath(server, s);
        if (!death.isEmpty()) nightNews.add(death);
        String habit = Habits.night(s);
        if (!habit.isEmpty()) nightNews.add(habit);
        String news = String.join("\n", nightNews);
        nightNews.clear();
        Kokuhaku.LOG.info("Night {}: \"{}\"", s.day, news.replace("\n", " / "));
        int hold = news.isEmpty() ? QUIET_TICKS : NEWS_TICKS;
        for (ServerPlayerEntity p : living(server)) sendCutscene(p, 2, homeOf(s, p), hold + SCENE_OVERLAP, news);
        later(hold, () -> newDay(server, s));
    }

    /** Awake in the dark, she tells you what she's seen. */
    private static void confide(ServerPlayerEntity p, RunState s, int host) {
        Mystery m = s.mystery();
        Student who = Roster.get(host);
        s.known.add(host);
        s.fondness[host] = Math.min(5, s.fondness[host] + 1);
        Clue told = m.clues.stream().filter(c -> c.kind() == Kind.TRAIT && !s.found.contains(c.id()) && !s.atScene.contains(c.id())).findFirst().orElse(null);
        String text = "She fell asleep before she could say anything useful.";
        if (told != null) {
            s.found.add(told.id());
            text = told.text();
        }
        s.markDirty();
        Kokuhaku.LOG.info("Sleepover: {} told {} \"{}\"", who.firstName(), p.getGameProfile().getName(), text);
        confided.put(p.getUuid(), new String[]{who.firstName() + " couldn't sleep, so you talked.", text});
    }

    private static void newDay(MinecraftServer server, RunState s) {
        if (s.status == RunState.Status.PLAYING) SaveSlot.write(s, true, gone, Accomplice.who(), players(server));
        s.day++;
        if (s.day > Schedule.DAYS && s.party.size() == 1 && Romance.datingHer(s)) {
            end(server, s, 1, Endings.confessed(Roster.get(s.dating).firstName()));
            return;
        }
        if (Kept.on(s) && s.day > Schedule.DAYS) {
            Kokuhaku.LOG.info("Two weeks, and nobody's coming");
            end(server, s, 0, Endings.neverLeft(s));
            return;
        }
        if (Living.active(s) && s.day > Schedule.DAYS) {
            Kokuhaku.LOG.info("Two weeks in her house, {} strikes", s.jealous);
            end(server, s, 1, Endings.stayed(s));
            return;
        }
        s.minute = Schedule.WAKE;
        if (Kept.on(s)) {
            Kept.nightSearch(s);
            Kept.morning(s);
        }
        Arrays.fill(s.doorLocked, false);
        friendCameToday = false;
        s.markDirty();
        for (StudentEntity e : students(server)) {
            e.placeAt("home", Routines.homePos(e.getSkin(), s, town));
            e.setMood(People.mood(s, e.getSkin()));
            e.setStaring(s.mystery().isYandere(e.getSkin()) && !Living.active(s) && RANDOM.nextFloat() < STARE_CHANCE[s.difficulty.ordinal()]);
        }
        if (s.day == Festival.DAY) {
            Festival.setUp(server.getOverworld(), town);
            TrainRide.meet(s, town, server);
        } else if (s.day == Festival.DAY + 1) {
            Festival.packUp(server.getOverworld());
        }
        Townsfolk.thinOut(server, s.day);
        Sleuthing.Drop drop = Sleuthing.todaysDrop(s, town);
        if (drop != null) {
            server.getOverworld().setBlockState(drop.at(), ModBlocks.EVIDENCE.getDefaultState());
            trail.put(drop.at(), drop.text());
            nightNews.add(drop.hint());
        }
        server.getOverworld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        for (ServerPlayerEntity p : living(server)) wakeUp(p, s);
    }

    /** Fade in on you asleep; the alarm; a moment later you're up and walking. */
    private static void wakeUp(ServerPlayerEntity p, RunState s) {
        // Developer testing only: straight to the room at the end.
        if ("4".equals(System.getenv("KOKUHAKU_BAT")) && !devRoomSeen) {
            devRoomSeen = true;
            transitioning = false;
            found(p);
            taken(p, s);
            return;
        }
        transitioning = true;
        p.removeStatusEffect(StatusEffects.SLOWNESS); // a night's sleep sees to the leg
        Town.Home home = homeOf(s, p);
        BlockPos inside = town.graph.pos(home.inside());
        p.setSpawnPoint(p.getServerWorld().getRegistryKey(), inside, 0f, true, false);
        var bed = home.bedCenter();
        Vec3d stand = bedside(p.getServerWorld(), home);
        float yaw = (float) (Math.toDegrees(Math.atan2(stand.z - bed.z, stand.x - bed.x)) - 90);
        p.teleport(p.getServerWorld(), stand.x, stand.y, stand.z, yaw, 0f);
        later(4, () -> sendCutscene(p, 1, home, WAKE_TICKS, ""));
        later(WAKE_TICKS + 4, () -> {
            transitioning = false;
            int left = Schedule.DAYS - s.day;
            String sub = s.day > Schedule.DAYS ? "" : Schedule.WEEKDAYS[Schedule.weekday(s.day)] + (Schedule.isSchoolDay(s.day) ? "" : "  ·  No school today") + "  ·  " + (left == 0 ? "Last day" : left + (left == 1 ? " day left" : " days left"));
            if ((s.day - 1) % 3 == 0 && s.day <= Schedule.DAYS) {
                String[] chapter = CHAPTERS[(s.day - 1) / 3];
                announce(p, chapter[0], chapter[1], 0);
            }
            if (Festival.on(s)) announce(p, "The School Festival", "No lessons. The whole school's on the sports field. Fireworks at 19:00.", 0);
            Turning.morning(p, s);
            if (s.day == 1) Radio.place(p, homeOf(s, p));
            if (s.day == 1 && Accomplice.active()) announce(p, "One of you is lying.", "Someone in your party is working for her. Your voices won't carry.", 0);
            announce(p, "Day " + s.day, sub, 0);
            sleepover.remove(p.getUuid());
            boolean fromWard = Ward.isAdmitted(p);
            Ward.discharged(p);
            if (Ward.noteDue(s)) announce(p, "", "There's something in your shoe locker. An envelope, by the look of it.", 0);
            Runnable walk = () -> {
                if (!fromWard && !DEV_NO_WALK && !transitioning && Schedule.isSchoolDay(s.day) && s.day <= Schedule.DAYS) callDownstairs(p, s);
            };
            String note = Living.active(s) ? null : Whispers.morningNote(s);
            if (s.devoted && !s.bonk) {
                later(60, () -> {
                    if (s.day == 1) Devotion.introduce(p, s, walk);
                    else Devotion.giveRule(p, s, walk);
                });
            } else if (s.day == 1 && Accomplice.is(p)) {
                later(60, () -> Accomplice.brief(p, s, walk));
            } else if (note == null) {
                later(70, walk);
            } else {
                // It's there on the floor when you stand up. It always is.
                Kokuhaku.LOG.info("Under the door: {}", note);
                Shadow.saw(s, "She slid a note under your door before you woke.");
                later(60, () -> Talk.say(p, "Under your door", "\"" + note + "\"", Talk.option("Keep it.", q -> {
                    s.notes.add("Under your door, day " + s.day + ": \"" + note + "\"");
                    s.markDirty();
                    later(10, walk);
                })));
            }
            String[] told = confided.remove(p.getUuid());
            if (told != null) announce(p, told[0], told[1], 0);
            for (Clue c : s.mystery().clues) {
                if (c.kind() == Kind.HINT && c.day() == s.day) {
                    s.found.add(c.id());
                    announce(p, "", c.text(), 0);
                }
            }
            if (!s.found.contains(0) && !Living.active(s)) announce(p, "", "Something's waiting in shoe locker " + s.lockers[slot(s, p)] + " at school.", 0);
            else if (s.mystery().clues.stream().anyMatch(c -> c.kind() == Kind.NOTE && c.day() == s.day)) announce(p, "", "You get the feeling you should check your locker.", 0);
            if (!devStaged && System.getenv("KOKUHAKU_CAR") != null) {
                devStaged = true;
                later(60, () -> Traffic.devRoad(p));
            } else if (!devStaged && System.getenv("KOKUHAKU_ASK") != null) {
                devStaged = true;
                later(40, () -> devAsk(p, s));
            } else if (!devStaged && System.getenv("KOKUHAKU_SLEEPOVER") != null) {
                devStaged = true;
                later(40, () -> devSleepover(p, s));
            } else if (!devStaged && System.getenv("KOKUHAKU_STAGE") != null) {
                devStaged = true;
                later(40, () -> devStage(p, s));
            } else if ("1".equals(System.getenv("KOKUHAKU_SIT"))) later(40, () -> devSit(p));
            else if (System.getenv("KOKUHAKU_PEEK") != null) later(40, () -> devPeek(p, s));
            else if ("1".equals(System.getenv("KOKUHAKU_BAT"))) later(40, () -> struck(p.server, s));
            else if (System.getenv("KOKUHAKU_BAT") != null) later(40, () -> devCurfew(p, s));
            else if ("1".equals(System.getenv("KOKUHAKU_TOUR"))) devTour(p, s);
        });
    }

    /** A block you could stand in: clear for two blocks, with something solid underfoot. */
    public static boolean standable(ServerWorld world, BlockPos pos) {
        if (com.kokuhaku.world.town.Furniture.at(world, pos.down())) return false;
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
                && !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
    }

    /** The nearest clear spot beside your bed: along its sides first, then past the foot, then further out. */
    private static Vec3d bedside(ServerWorld world, Town.Home home) {
        BlockPos head = home.bedHead(), foot = home.bedFoot();
        Direction along = Direction.getFacing(foot.getX() - head.getX(), 0, foot.getZ() - head.getZ());
        Direction left = along.rotateYCounterclockwise(), right = along.rotateYClockwise();
        List<BlockPos> spots = new ArrayList<>();
        for (int out = 1; out <= 3; out++) {
            spots.add(foot.offset(left, out));
            spots.add(foot.offset(right, out));
            spots.add(head.offset(left, out));
            spots.add(head.offset(right, out));
            spots.add(foot.offset(along, out));
            spots.add(foot.offset(along, out).offset(left));
            spots.add(foot.offset(along, out).offset(right));
        }
        for (BlockPos spot : spots) {
            // Your room only: three blocks out from the bed can be through a wall.
            if (standable(world, spot) && home.containsBlock(spot)) return new Vec3d(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        }
        BlockPos inside = town.graph.pos(home.inside());
        return new Vec3d(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
    }

    /** Watching it from behind, at floor level, all the way down. */
    static void sendDrag(ServerPlayerEntity p, Vec3d start, Vec3d end, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(12);
        for (Vec3d v : List.of(start, end, Vec3d.ZERO, Vec3d.ZERO)) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(ticks);
        buf.writeString("");
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
    }

    private static void sendCutscene(ServerPlayerEntity p, int type, Town.Home home, int ticks, String text) {
        sendCutscene(p, type, home, bedside(p.getServerWorld(), home), home.bedCenter(), ticks, text);
    }

    private static void sendCutscene(ServerPlayerEntity p, int type, Town.Home home, Vec3d stand, Vec3d look, int ticks, String text) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(type);
        for (var v : List.of(stand, look)) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        for (BlockPos b : List.of(home.bedHead(), home.bedFoot())) {
            buf.writeDouble(b.getX() + 0.5);
            buf.writeDouble(b.getY());
            buf.writeDouble(b.getZ() + 0.5);
        }
        buf.writeVarInt(ticks);
        buf.writeString(text);
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
    }

    // --------------------------------------------------------------- deaths

    /** Sometimes someone doesn't see the morning. Returns what the black screen should say. */
    private static String maybeDeath(MinecraftServer server, RunState s) {
        // Whoever she caught you with goes first, and she doesn't wait for a quiet night.
        boolean marked = s.livingVictim >= 0 && !s.dead.contains(s.livingVictim);
        if (!marked && (s.day < 2 || RANDOM.nextFloat() >= DEATH_CHANCE[s.difficulty.ordinal()] * Habits.killing(s))) return "";
        List<Integer> alive = new ArrayList<>();
        for (int i : Roster.active(s.party.size())) if (!s.mystery().isYandere(i) && !s.dead.contains(i)) alive.add(i);
        if (alive.isEmpty()) return "";
        int victim = marked ? s.livingVictim : alive.get(RANDOM.nextInt(alive.size()));
        s.livingVictim = -1;
        s.dead.add(victim);
        People.died(victim, s.day);
        Shadow.saw(s, Roster.get(victim).firstName() + (marked ? " had got too close to you. She saw to that." : " never knew why. She only wanted you to be frightened."));
        s.markDirty();
        for (StudentEntity e : students(server)) if (e.getSkin() == victim) e.discard();

        // Whatever they knew is left in their room now.
        Town.Home home = town.homes.get(s.homes[victim]);
        BlockPos scene = s.difficulty.ordinal() < 2 ? town.graph.pos(home.inside()) : home.bedFoot().offset(Direction.UP, 0);
        Clue held = s.mystery().clues.stream().filter(c -> c.witness() == victim && !s.found.contains(c.id())).findFirst().orElse(null);
        ServerWorld world = server.getOverworld();
        if (held != null) {
            BlockPos at = s.difficulty.ordinal() < 2 ? scene : town.graph.pos(home.inside()).add(1, 0, 1);
            world.setBlockState(at, ModBlocks.EVIDENCE.getDefaultState());
            evidence.put(at, held.id());
            s.atScene.add(held.id());
        }
        String name = Roster.get(victim).name();
        return switch (s.difficulty) {
            case EASY -> name + " was found dead at home.\nSomeone should look around that room.";
            case MEDIUM -> name + " was found dead at home.";
            case HARD -> name + " has been missing since last night.";
            case IMPOSSIBLE -> name + " won't be coming to school any more.";
        };
    }

    public static String lastDeadName(RunState s) {
        Integer last = null;
        for (int d : s.dead) last = d;
        return last == null ? null : Roster.get(last).firstName();
    }

    // ----------------------------------------------------------------- jobs

    private static void openJob(ServerPlayerEntity player, RunState s, Job job) {
        if (s.day < s.firedUntil) {
            announce(player, "", "They won't take you back yet. Try again on day " + s.firedUntil + ".", 0);
            return;
        }
        if (Schedule.isSchoolDay(s.day) && s.minute < Schedule.AFTER_SCHOOL) {
            announce(player, "", "Shifts start after school.", 0);
            return;
        }
        if (s.minute + job.hours * 60 >= s.difficulty.curfew - 45) {
            announce(player, "", "Too late for a shift. Curfew's coming.", 0);
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(job.ordinal());
        buf.writeVarInt(s.yen);
        ServerPlayNetworking.send(player, Net.OPEN_JOB, buf);
    }

    /** A finished shift. Time jumps on, mistakes come off your pay, and too many get you fired. */
    public static void shiftDone(ServerPlayerEntity player, int jobId, int mistakes) {
        RunState s = state(player.server);
        if (jobId < 0 || jobId >= Job.values().length || transitioning || s.status != RunState.Status.PLAYING) return;
        Job job = Job.values()[jobId];
        Whispers.worked();
        s.minute += job.hours * 60;
        player.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
        int pay = Math.max(0, job.pay() - mistakes * 400);
        s.yen += pay;
        s.markDirty();
        announce(player, job.hours + " hours later", "You earned ¥" + pay + (mistakes > 0 ? " (" + mistakes + " mistakes docked)" : ""), 1);
        if (mistakes >= 4) {
            s.firedUntil = s.day + 4;
            announce(player, "", "\"Don't come back this week.\" You were sent home.", 0);
            strike(player.server, s, 1);
            return;
        }
        List<Integer> unknown = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.known.contains(g)) unknown.add(g);
        if (!unknown.isEmpty() && mistakes <= 1) {
            int g = unknown.get(RANDOM.nextInt(unknown.size()));
            s.known.add(g);
            announce(player, "", "A coworker gossiped about " + Roster.get(g).firstName() + ". Profile updated in your bag.", 0);
        }
        sendState(player, s);
    }

    public static void buy(ServerPlayerEntity player, int drinkId) {
        RunState s = state(player.server);
        if (drinkId < 0 || drinkId >= Drink.values().length) return;
        Drink d = Drink.values()[drinkId];
        if (s.yen < d.price) return;
        s.yen -= d.price;
        s.markDirty();
        Whispers.bought(d.label);
        if (d == Drink.GREEN_TEA) Chase.drank(s);
        Devotion.spent(player, s);
        player.addStatusEffect(new StatusEffectInstance(d.effect, 20 * 60, 0, false, false));
        if (d == Drink.GREEN_TEA) Kept.boughtTea(player, s);
        announce(player, "", d.label + ". " + d.description, 0);
        sendState(player, s);
    }

    // -------------------------------------------------------------- endings

    /** Somewhere clear behind you, for her to have been standing. */
    private static Vec3d behind(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        // She has to be seen from the floor, so nothing between you and her, not even the bed.
        Vec3d low = p.getPos().add(0, 0.4, 0);
        for (double reach : new double[]{3.0, 2.4, 1.8, 1.3}) {
            for (float turn : new float[]{180f, 150f, 210f, 120f, 240f, 90f, 270f, 60f, 300f, 30f, 330f, 0f}) {
                Vec3d spot = p.getPos().add(Vec3d.fromPolar(0f, p.getYaw() + turn).multiply(reach));
                if (!standable(world, BlockPos.ofFloored(spot))) continue;
                var ray = new RaycastContext(low, spot.add(0, 0.4, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p);
                if (world.raycast(ray).getType() == HitResult.Type.MISS) return spot;
            }
        }
        return p.getPos();
    }

    /** Developer testing only: a minute before curfew, on the street (KOKUHAKU_BAT=2) or in a classroom (3). */
    private static void devCurfew(ServerPlayerEntity p, RunState s) {
        BlockPos at = "2".equals(System.getenv("KOKUHAKU_BAT")) ? town.graph.pos(town.graph.nearest(new Vec3d(-640.5, 22, -85.5))) : town.graph.pos(town.place("center:01"));
        // In a party only the host goes out, so there's someone left at home.
        if (slot(s, p) == 0) p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        s.minute = s.difficulty.curfew - 5; // 20 seconds to go
        p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
    }

    /**
     * Curfew. Anyone not home is on their own: out in the open you look left,
     * you look right, and it all goes black. Indoors, it's the bat. Everyone
     * else just goes to sleep, and finds out from the news in the dark.
     */
    private static void curfewCaught(MinecraftServer server, RunState s) {
        if (transitioning) return;
        for (ServerPlayerEntity p : new ArrayList<>(living(server))) {
            if (isHome(p, s) || Accomplice.is(p) || Chase.begin(p, s)) continue;
            found(p);
            if (p.getServerWorld().isSkyVisible(p.getBlockPos().up())) {
                sendCutscene(p, 4, homeOf(s, p), p.getPos(), p.getPos(), NIGHT_TICKS, "");
                later(NIGHT_TICKS - 4, () -> taken(p, s));
            } else {
                batScene(p, s);
                later(BAT_TICKS - 4, () -> taken(p, s));
            }
        }
        if (!living(server).isEmpty() && !Chase.any()) goToSleep(server, s);
    }

    static void hitByCar(ServerPlayerEntity p, Vec3d car) {
        runOver(p, car, "car");
    }

    /** Create's trains run people over on their own: this turns that into the same as a car. */
    public static void hitByTrain(ServerPlayerEntity p) {
        runOver(p, Railway.nearestPoint(p.getPos()), "train");
    }

    /** You stepped out in front of it. On the easier difficulties you get up again. */
    private static void runOver(ServerPlayerEntity p, Vec3d car, String what) {
        RunState s = state(p.server);
        UUID id = p.getUuid();
        if (transitioning || busy.contains(id) || s.status != RunState.Status.PLAYING || p.hasVehicle() || gone.contains(id)) return;
        if (ticks < carSafeUntil.getOrDefault(id, 0)) return;
        carSafeUntil.put(id, ticks + CAR_TICKS + CAR_GRACE);
        Kokuhaku.LOG.info("{} was hit by a {} at {} on {}", p.getGameProfile().getName(), what, p.getBlockPos().toShortString(), s.difficulty);
        sendCutscene(p, 6, homeOf(s, p), p.getPos(), car, CAR_TICKS, "");
        if (s.difficulty.ordinal() >= 2) {
            gone.add(id);
            nightNews.add(p.getGameProfile().getName() + " was hit by a " + what + ".");
            later(CAR_TICKS - 2, () -> killedOnTheRoad(p, s, what));
        } else {
            Whispers.hurt();
            later(CAR_TICKS - 2, () -> Ward.admit(p, s, what));
        }
    }

    private static void killedOnTheRoad(ServerPlayerEntity p, RunState s, String what) {
        Shadow.send(p, s, stillOutThere(s, p));
        MenuRoom.hide(p);
        p.teleport(p.getServerWorld(), MenuRoom.GIRL_X, MenuRoom.GIRL_Y, MenuRoom.GIRL_Z + 2.9, 180f, 0f);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(4);
        buf.writeVarInt(s.freeYandere());
        buf.writeString("ROAD:" + what);
        ServerPlayNetworking.send(p, Net.END, buf);
    }

    /** A black screen with words on it, wherever you are. */
    static void sendBlack(ServerPlayerEntity p, Town.Home home, int length, String text) {
        sendCutscene(p, 2, home, length, text);
    }

    /** Coming round in a hospital bed, turning your head to whoever's standing there. Holds until the next scene. */
    static void sendWard(ServerPlayerEntity p, Town.Home home, Vec3d look, int length) {
        sendCutscene(p, 7, home, bedside(p.getServerWorld(), home), look, length, "");
    }

    /** The same, at 3:12 in the morning, with the lights out. */
    static void sendDark(ServerPlayerEntity p, Town.Home home, Vec3d look, int length) {
        sendCutscene(p, 7, home, bedside(p.getServerWorld(), home), look, length, "dark");
    }

    /** She looks at you, you look at her, and then at your own arm. */
    static void sendJealous(ServerPlayerEntity p, Vec3d her, int length, String text, int strike, int skin) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(8);
        for (Vec3d v : List.of(p.getPos(), her, new Vec3d(strike, skin, 0), Vec3d.ZERO)) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(length);
        buf.writeString(text);
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
    }

    /** The way to school, then the walk itself. */
    static void sendWalk(ServerPlayerEntity p, List<Vec3d> route, int length) {
        PacketByteBuf path = PacketByteBufs.create();
        path.writeVarInt(route.size());
        for (Vec3d v : route) {
            path.writeDouble(v.x);
            path.writeDouble(v.y);
            path.writeDouble(v.z);
        }
        ServerPlayNetworking.send(p, Net.PEEK_PATH, path);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(9);
        for (Vec3d v : List.of(p.getPos(), p.getPos(), p.getPos(), p.getPos())) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(length);
        buf.writeString("");
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
    }

    /** Aboard the train, carried along by it, for as long as the journey takes. */
    static void sendRide(ServerPlayerEntity p, Vec3d seat, int length) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(11);
        for (Vec3d v : List.of(seat, seat, Vec3d.ZERO, Vec3d.ZERO)) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(length);
        buf.writeString("");
        ServerPlayNetworking.send(p, Net.CUTSCENE, buf);
    }

    /** The walk in stops for a train, and starts again. */
    static void sendWalkHold(ServerPlayerEntity p, boolean hold) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(hold);
        ServerPlayNetworking.send(p, Net.WALK_HOLD, buf);
    }

    /** Kept in overnight: the night goes by in the ward, with nobody at your door. */
    static void overnight(ServerPlayerEntity p, RunState s) {
        transitioning = true;
        later(10, () -> nightFalls(p.server, s));
    }

    /** Time passing in a hospital bed. It's the only way you'll ever miss a lesson without trouble. */
    static void skipMinutes(MinecraftServer server, RunState s, int minutes) {
        s.minute += minutes;
        s.markDirty();
        server.getOverworld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
    }

    /** Out of bed, on one good leg, and free to go. */
    static void getUp(ServerPlayerEntity p, RunState s, Town.Home bed) {
        sendCutscene(p, 1, bed, WAKE_TICKS, "");
        later(WAKE_TICKS, () -> {
            transitioning = false;
            busy.remove(p.getUuid());
            Ward.discharged(p);
            announce(p, "Discharged", s.yen < 0 ? "You owe \u00A5" + (-s.yen) + ". You can barely walk." : "You can barely walk. It'll be better after a night's sleep.", 0);
        });
    }

    /** It's time for bed whether you like it or not. */
    static void sleepNow(MinecraftServer server, RunState s) {
        goToSleep(server, s);
    }

    /** She has someone. The others will hear about it when they sleep. */
    private static void found(ServerPlayerEntity p) {
        gone.add(p.getUuid());
        nightNews.add(p.getGameProfile().getName() + " was found dead.");
    }

    /** Day 31: the bat, for everyone. */
    private static void struck(MinecraftServer server, RunState s) {
        if (transitioning) return;
        for (ServerPlayerEntity p : new ArrayList<>(living(server))) {
            if (Accomplice.is(p)) continue;
            found(p);
            batScene(p, s);
            later(BAT_TICKS - 4, () -> taken(p, s));
        }
    }

    /** The bat: footsteps behind you, the hit, the ground, her standing over you. */
    private static void batScene(ServerPlayerEntity p, RunState s) {
        ServerWorld world = p.getServerWorld();
        Vec3d her = behind(p);
        sendCutscene(p, 3, homeOf(s, p), p.getPos(), her, BAT_TICKS, "");
        later(BAT_HIT_TICK, () -> {
            StudentEntity e = Kokuhaku.STUDENT.create(world);
            if (e == null) return;
            float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - her.z, p.getX() - her.x)) - 90);
            e.refreshPositionAndAngles(her.x, her.y, her.z, yaw, 30f);
            e.setBodyYaw(yaw);
            e.setHeadYaw(yaw);
            e.setSkin(s.freeYandere());
            if (stillOutThere(s, p)) e.setPeek(StudentEntity.SILHOUETTE);
            e.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BAT));
            e.setAiDisabled(true);
            world.spawnEntity(e);
            later(BAT_TICKS - BAT_HIT_TICK - 4, e::discard);
        });
    }

    /**
     * She gets you: out after curfew, an unlocked door, or the 31st day. You
     * wake in the room from the main menu, and she's the last thing you talk to.
     */
    private static void taken(ServerPlayerEntity p, RunState s) {
        // Her manner would give her away to a party, so only a lone player sees it.
        Hallway.drag(p, s, () -> toTheRoom(p, s, 2, s.party.size() == 1 ? manner(s) : ""));
    }

    private static String manner(RunState s) {
        String personality = System.getenv("KOKUHAKU_PERSONALITY");
        return personality != null ? personality : Roster.get(s.freeYandere()).personality().name();
    }

    /** Friends still alive, who mustn't learn anything from you. */
    private static boolean stillOutThere(RunState s, ServerPlayerEntity p) {
        return s.status == RunState.Status.PLAYING && living(p.server).stream().anyMatch(q -> q != p);
    }


    /** Everyone else is gone, and she was as good as her word. */
    private static void lastOneLeft(MinecraftServer server, RunState s) {
        List<ServerPlayerEntity> left = living(server);
        if (s.status != RunState.Status.PLAYING || left.size() != 1 || !Accomplice.is(left.get(0))) return;
        ServerPlayerEntity them = left.get(0);
        String names = String.join(" and ", s.mystery().yanderes.stream().map(y -> Roster.get(y).name()).toList());
        Kokuhaku.LOG.info("The accomplice is the last one left");
        s.status = RunState.Status.LOST; SaveSlot.delete();
        gone.add(them.getUuid());
        s.markDirty();
        for (ServerPlayerEntity p : players(server)) {
            sendState(p, s);
            Shadow.send(p, s, false);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(1);
        buf.writeVarInt(s.freeYandere());
        buf.writeString(Endings.accompliceKept(names));
        ServerPlayNetworking.send(them, Net.END, buf);
    }

    private static void toTheRoom(ServerPlayerEntity p, RunState s, int kind, String text) {
        Shadow.send(p, s, stillOutThere(s, p));
        if (Accomplice.active()) later(40, () -> lastOneLeft(p.server, s));
        later(60, () -> nobodyLeft(p.server, s));
        ServerWorld world = p.getServerWorld();
        // While the others are still out there you can't know who she was, in case you're called back.
        boolean masked = stillOutThere(s, p);
        for (StudentEntity e : world.getEntitiesByType(Kokuhaku.STUDENT, e -> e.getY() > 90 && e.getPeek() < StudentEntity.DRAGGED)) {
            e.setSkin(s.freeYandere());
            e.setPeek(masked ? StudentEntity.SILHOUETTE : 0);
            e.refreshPositionAndAngles(MenuRoom.SEAT_X, MenuRoom.GIRL_Y, MenuRoom.GIRL_Z, 0f, 0f);
            e.setHeadYaw(0f);
            e.setBodyYaw(0f);
        }
        MenuRoom.hide(p);
        p.teleport(world, MenuRoom.SEAT_X, MenuRoom.GIRL_Y + 0.5, MenuRoom.SEAT_Z, 180f, 0f);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(kind);
        buf.writeVarInt(s.freeYandere());
        buf.writeString(text);
        ServerPlayNetworking.send(p, Net.END, buf);
    }

    /** Three strikes. She doesn't ask you anything this time, and there's no staying. */
    static void finalBlow(ServerPlayerEntity p, RunState s) {
        if (transitioning) return;
        found(p);
        batScene(p, s);
        later(BAT_TICKS - 4, () -> toTheRoom(p, s, 2, "FINAL:" + manner(s)));
    }

    /** You told her you'd stay. Only when you're alone: nobody else can be kept waiting on it. */
    public static void stay(ServerPlayerEntity p) {
        RunState s = state(p.server);
        if (s.party.size() != 1 || !gone.remove(p.getUuid())) return;
        Hallway.untie();
        nightNews.removeIf(line -> line.startsWith(p.getGameProfile().getName() + " "));
        Living.begin(p, s);
        if (s.status == RunState.Status.PLAYING) newDay(p.server, s);
    }

    /** It's over one way or another: a secret ending, and back to the menu. */
    static void endGame(MinecraftServer server, RunState s, String text) {
        end(server, s, 1, text);
    }

    static void tell(ServerPlayerEntity p, String title, String subtitle) {
        announce(p, title, subtitle, 0);
    }

    /** The one time in a hundred: she lets you go. You wake in your own bed. */
    public static void letOut(ServerPlayerEntity p) {
        RunState s = state(p.server);
        if (!gone.remove(p.getUuid())) return;
        Hallway.untie();
        p.clearStatusEffects();
        nightNews.removeIf(line -> line.startsWith(p.getGameProfile().getName() + " "));
        if (living(p.server).size() == 1) newDay(p.server, s); // the night was waiting on you
        else if (!transitioning) wakeUp(p, s);
    }

    /** kind: 0 you lost, 1 you won, 2 the room and the jumpscare. */
    private static void end(MinecraftServer server, RunState s, int kind, String text) {
        s.status = kind == 1 ? RunState.Status.WON : RunState.Status.LOST;
        // Finished, either way: the slot's free for the next one.
        SaveSlot.delete();
        s.markDirty();
        String names = String.join(" and ", s.mystery().yanderes.stream().map(y -> Roster.get(y).name()).toList());
        for (ServerPlayerEntity p : players(server)) {
            Shadow.send(p, s, false);
            boolean lied = kind == 1 && Accomplice.is(p);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(lied ? 0 : kind);
            buf.writeVarInt(s.freeYandere());
            buf.writeString(lied ? Endings.accompliceFailed(names) : text);
            ServerPlayNetworking.send(p, Net.END, buf);
        }
    }

    // -------------------------------------------------------------- talking

    public static void talk(ServerPlayerEntity player, StudentEntity student) {
        RunState s = state(player.server);
        if (!s.started || s.status != RunState.Status.PLAYING) return;
        if (isGhost(player)) {
            announce(player, "", "They look straight through you.", 0);
            return;
        }
        if (Townsfolk.isOne(student)) {
            if (s.devoted) {
                announce(player, "", Devotion.stranger(), 0);
                return;
            }
            if (Teachers.isOne(student)) {
                Teachers.talk(player, s, student);
                return;
            }
            if (Townsfolk.RECEPTIONIST.equals(student.getName().getString())) {
                Talk.say(player, Townsfolk.RECEPTIONIST, s.yen < 0 ? "You owe us \u00A5" + (-s.yen) + ". There's no hurry. It doesn't go away." : "Visiting hours are until eight. Records are on the top floor, and staff only. Not that anyone's up there.", Talk.leave("Thanks."));
                return;
            }
            announce(player, "", Dialogue.passerBy(s.day), 0);
            return;
        }
        student.getNavigation().stop();
        student.getLookControl().lookAt(player, 30f, 30f);
        Student who = Roster.get(student.getSkin());
        boolean isHer = s.mystery().isYandere(who.id());
        UUID id = player.getUuid();
        asking.remove(id);
        if (who.id() == s.livingWith) {
            Living.chat(player, s, student);
            return;
        }
        if (Favours.bringing(who)) {
            Favours.handIn(player, s, who);
            return;
        }
        if (s.devoted) {
            Devotion.plead(player, s, student);
            return;
        }
        if (Living.caught(player, s, who)) return;
        if (who.female() && !isHer) Whispers.talkedTo(s, who.id());
        if (isHer) Shadow.saw(s, "You stopped to talk to her. She went over every word that night.");
        if (who.female()) {
            int window = Questions.window(s.day, s.minute);
            Map<Integer, Integer> seen = asked.computeIfAbsent(id, k -> new HashMap<>());
            if (seen.getOrDefault(who.id(), -1) != window) {
                Questions.Question q = Questions.pick(s.seed, s.day, s.minute, who.id(), s.difficulty.curfew, sleepover.containsKey(id) || Living.active(s));
                if (q != null) {
                    seen.put(who.id(), window);
                    asking.put(id, q);
                    sendDialogue(player, who, q.ask(), Questions.ANSWERS);
                    return;
                }
            }
        }
        String usual = Dialogue.greeting(who, isHer, s.difficulty, s.minute, lastDeadName(s));
        sendDialogue(player, who, People.greet(s, who, isHer, student.isSittingDown(), usual) + Dialogue.mood(who, s.fondness[who.id()]) + Rumours.line(s));
    }

    public static void choose(ServerPlayerEntity player, int studentId, int option) {
        if (studentId == Talk.ID) {
            Talk.choose(player, option);
            return;
        }
        if (studentId < 0 || studentId >= Roster.TOTAL) return;
        RunState s = state(player.server);
        if (s.status != RunState.Status.PLAYING) return;
        Mystery m = s.mystery();
        Student who = Roster.get(studentId);
        boolean isHer = m.isYandere(studentId);

        Questions.Question question = asking.remove(player.getUuid());
        if (question != null) {
            answer(player, s, who, isHer, question, option == 0);
            return;
        }

        String line;
        if (option == Dialogue.ABOUT) {
            line = Dialogue.about(who, m);
            if (s.known.add(studentId)) {
                s.markDirty();
                if (who.female()) announce(player, "", who.firstName() + "'s profile updated in your bag", 0);
            }
        } else if (option == Dialogue.CHAT) {
            line = Dialogue.chat(who);
        } else if (option < optionsFor(s, who).size() && Confront.OPTION.equals(optionsFor(s, who).get(option))) {
            Confront.ask(player, s, who);
            return;
        } else if (option < optionsFor(s, who).size() && Favours.ASK.equals(optionsFor(s, who).get(option))) {
            Favours.ask(player, s, who, town);
            return;
        } else if (option < optionsFor(s, who).size() && GIVE_SWEETS.equals(optionsFor(s, who).get(option))) {
            s.gifts--;
            s.fondness[studentId] = Math.min(5, s.fondness[studentId] + SWEETS_FONDNESS);
            s.markDirty();
            line = "For me? ...You really didn't have to. Thank you.";
        } else if (option < optionsFor(s, who).size() && Romance.NICE.equals(optionsFor(s, who).get(option))) {
            Romance.compliment(player, s, who, students(player.server).stream().filter(e -> e.getSkin() == studentId).findFirst().orElse(null));
            return;
        } else if (option < optionsFor(s, who).size() && optionsFor(s, who).get(option).equals(Romance.option(s, who))) {
            Romance.talk(player, s, who);
            return;
        } else if (option == Dialogue.TRUST) {
            List<Integer> others = new ArrayList<>();
            for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (g != studentId && !s.dead.contains(g)) others.add(g);
            line = Dialogue.trust(who, Roster.get(others.get(RANDOM.nextInt(others.size()))).firstName());
        } else if (option == Dialogue.STRANGE) {
            line = askedAboutStrange(s, who);
            tellClueFound(player, s, who);
            People.askedAround(studentId);
        } else {
            return;
        }
        sendDialogue(player, who, line);
    }

    /** What someone says when you ask if they've seen anything: what they know, if they know it yet. */
    static String askedAboutStrange(RunState s, Student who) {
        Mystery m = s.mystery();
        Clue held = m.clues.stream().filter(c -> c.witness() == who.id()).findFirst().orElse(null);
        if (held == null) return Dialogue.smallTalk(who, m.isYandere(who.id()), s.difficulty);
        if (s.found.contains(held.id())) return Dialogue.alreadyTold();
        if (held.day() > s.day) return Dialogue.notYet();
        return Dialogue.reveal(held);
    }

    /** Stand near people for long enough and you hear things they'd never tell you. */
    private static void overhear(ServerPlayerEntity p, RunState s, List<? extends StudentEntity> all) {
        if (s.minute % OVERHEAR_EVERY != 0 || Living.active(s) || LunchScene.active(p)) return;
        for (StudentEntity e : all) {
            if (e.squaredDistanceTo(p) > OVERHEAR_REACH * OVERHEAR_REACH || e.isTalking() || RANDOM.nextFloat() > OVERHEAR_CHANCE) continue;
            Student who = Roster.get(e.getSkin());
            Clue held = s.mystery().clues.stream().filter(c -> c.witness() == who.id() && c.day() <= s.day && !s.found.contains(c.id())).findFirst().orElse(null);
            if (held == null) continue;
            Kokuhaku.LOG.info("Overheard {}", who.firstName());
            announce(p, "", "You overhear " + who.firstName() + ", whispering to someone.", 0);
            tellClueFound(p, s, who);
            return;
        }
    }

    /** Whatever they just told you goes in your bag. */
    static void tellClueFound(ServerPlayerEntity p, RunState s, Student who) {
        Clue held = s.mystery().clues.stream().filter(c -> c.witness() == who.id()).findFirst().orElse(null);
        if (held == null || held.day() > s.day || !s.found.add(held.id())) return;
        s.markDirty();
        Whispers.foundClue();
        boolean first = s.found.stream().filter(id -> s.mystery().clue(id).kind() == Kind.TRAIT).count() == 1 && held.kind() == Kind.TRAIT;
        announce(p, "", "Clue added to your bag", 0);
        if (first) later(80, () -> announce(p, "That's your first real clue.", "Check it against the girls on the Suspects tab in your bag. When only one fits, accuse her.", 0));
    }

    static void learned(ServerPlayerEntity p, Student who) {
        RunState s = state(p.server);
        if (s.known.add(who.id())) {
            s.markDirty();
            if (who.female()) announce(p, "", who.firstName() + "'s profile updated in your bag", 0);
        }
    }

    private static void answer(ServerPlayerEntity player, RunState s, Student who, boolean isHer, Questions.Question q, boolean yes) {
        if (yes && q.reward() == Questions.Reward.SLEEPOVER) {
            sleepOver(player, s, who);
            return;
        }
        int change = yes ? q.onYes() : q.onNo();
        if (change != 0) {
            s.fondness[who.id()] = Math.max(-5, Math.min(5, s.fondness[who.id()] + change));
            s.markDirty();
        }
        if (yes && q.reward() == Questions.Reward.PROFILE && s.known.add(who.id())) {
            s.markDirty();
            announce(player, "", who.firstName() + "'s profile updated in your bag", 0);
        }
        // Her manner would give her away to a party, so only a lone player sees it.
        String reply = Questions.reply(q, yes, isHer && s.party.size() == 1, s.difficulty);
        Kokuhaku.LOG.info("{} answered {}: {} ({} fondness)", who.firstName(), yes, reply, change);
        sendDialogue(player, who, reply);
    }

    /** You go home with her. Tonight her bed is your bed, and her door has no lock you can turn. */
    private static void sleepOver(ServerPlayerEntity p, RunState s, Student who) {
        sleepover.put(p.getUuid(), who.id());
        Town.Home home = homeOf(s, p);
        sendCutscene(p, 2, home, SLEEPOVER_TICKS, "You walked home with " + who.firstName() + ".");
        later(4, () -> {
            Vec3d spot = bedside(p.getServerWorld(), home);
            p.teleport(p.getServerWorld(), spot.x, spot.y, spot.z, p.getYaw(), 0f);
            for (StudentEntity e : students(p.server)) {
                if (e.getSkin() == who.id()) e.placeAt("home", Routines.homePos(who.id(), s, town));
            }
        });
    }

    /** Developer testing only: asks you the nearest girl's question and answers yes. */
    private static void devAsk(ServerPlayerEntity p, RunState s) {
        boolean wantGlasses = "glasses".equals(System.getenv("KOKUHAKU_ASK"));
        for (StudentEntity e : students(p.server)) {
            if (!Roster.get(e.getSkin()).female()) continue;
            if (wantGlasses && Roster.get(e.getSkin()).glasses() != Traits.Glasses.YES) continue;
            if (wantGlasses) {
                // Out on the pavement in daylight, face to face, so the shot shows her properly.
                p.teleport(p.getServerWorld(), -641.5, 22, -30.5, 90f, 0f);
                e.setAiDisabled(true);
                e.refreshPositionAndAngles(-643.0, 22, -30.5, -90f, 0f);
                e.setHeadYaw(-90f);
                e.setBodyYaw(-90f);
            } else {
                p.teleport(p.getServerWorld(), e.getX() + 1.3, e.getY(), e.getZ(), 90f, 0f);
            }
            talk(p, e);
            Kokuhaku.LOG.info("Question at {}: {}", Schedule.clock(s.minute), asking.get(p.getUuid()));
            later(100, () -> choose(p, e.getSkin(), 0));
            later(160, () -> {
                talk(p, e);
                Kokuhaku.LOG.info("Second talk in the same half hour asks: {}", asking.get(p.getUuid()));
            });
            return;
        }
    }

    /** Developer testing only: KOKUHAKU_SLEEPOVER=her or friend, straight to bed at her house. */
    private static void devSleepover(ServerPlayerEntity p, RunState s) {
        s.minute = Schedule.AFTER_SCHOOL + 30;
        boolean her = "her".equals(System.getenv("KOKUHAKU_SLEEPOVER"));
        int host = her ? s.freeYandere() : Roster.active(s.party.size()).stream().filter(i -> Roster.get(i).female() && !s.mystery().isYandere(i)).findFirst().orElseThrow();
        Kokuhaku.LOG.info("Sleepover test with {} (yandere: {})", Roster.get(host).name(), her);
        sleepOver(p, s, Roster.get(host));
        later(SLEEPOVER_TICKS + 20, () -> intoBed(p, s));
    }

    private static final String GIVE_SWEETS = "Give her a box of sweets.";
    private static final int SWEETS_FONDNESS = 3;

    private static void sendDialogue(ServerPlayerEntity player, Student who, String line) {
        sendDialogue(player, who, line, optionsFor(state(player.server), who));
    }

    /** Everyone has the same four things you can ask. Girls have a little more. */
    private static List<String> optionsFor(RunState s, Student who) {
        List<String> options = new ArrayList<>(Dialogue.OPTIONS);
        if (Favours.canAsk(s, who)) options.add(options.size() - 1, Favours.ASK);
        if (!who.female() || Living.active(s)) return options;
        options.add(options.size() - 1, Romance.NICE);
        if (Confront.canSay(s, who)) options.add(options.size() - 1, Confront.OPTION);
        if (s.gifts > 0) options.add(options.size() - 1, GIVE_SWEETS);
        String extra = Romance.option(s, who);
        if (extra != null) options.add(options.size() - 1, extra);
        return options;
    }

    private static void sendDialogue(ServerPlayerEntity player, Student who, String line, List<String> answers) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Student", who.id());
        nbt.putString("Name", who.name());
        nbt.putString("Line", line);
        NbtList options = new NbtList();
        for (String o : answers) options.add(NbtString.of(o));
        nbt.put("Options", options);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, Net.DIALOGUE, buf);
    }

    // --------------------------------------------------------- bag & accuse

    public static void openBag(ServerPlayerEntity player) {
        RunState s = state(player.server);
        Mystery m = s.mystery();
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Guesses", s.guessesLeft);
        nbt.putInt("Difficulty", s.difficulty.ordinal());
        nbt.putInt("Locker", s.lockers[slot(s, player)]);
        nbt.putInt("Strikes", s.strikes);
        nbt.putInt("ExpelAt", EXPEL_AT[s.difficulty.ordinal()]);
        nbt.putInt("Leads", Accomplice.leadsLeft(player));
        NbtList signs = new NbtList();
        signs.add(NbtString.of(player.getGameProfile().getName()));
        if (Accomplice.is(player)) for (ServerPlayerEntity q : alive(player.server)) if (q != player) signs.add(NbtString.of(q.getGameProfile().getName()));
        nbt.put("Signs", signs);
        nbt.putInt("GhostCost", GHOST_COST);
        NbtList callable = new NbtList();
        if (!gone.contains(player.getUuid())) for (String name : callable(player.server)) callable.add(net.minecraft.nbt.NbtString.of(name));
        nbt.put("Callable", callable);

        NbtList clues = new NbtList();
        for (int id : s.found) {
            Clue c = m.clue(id);
            NbtCompound e = new NbtCompound();
            e.putString("Text", c.text());
            e.putInt("Kind", c.kind().ordinal());
            e.putString("From", c.witness() >= 0 ? Roster.get(c.witness()).name() : "");
            e.putInt("Day", c.day());
            clues.add(e);
        }
        for (String note : s.notes) {
            NbtCompound e = new NbtCompound();
            e.putString("Text", note);
            e.putInt("Kind", Kind.NOTE.ordinal());
            e.putString("From", "");
            e.putInt("Day", 0);
            clues.add(e);
        }
        nbt.put("Clues", clues);
        int fits = 0;
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.dead.contains(g) && !s.caught.contains(g) && m.stillFits(g, s.found)) fits++;
        nbt.putInt("Fits", s.difficulty.ordinal() < 2 ? fits : -1);
        nbt.putBoolean("Pruned", s.difficulty.ordinal() == 0);

        NbtList girls = new NbtList();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) {
            Student st = Roster.get(g);
            boolean known = s.known.contains(g);
            NbtCompound e = new NbtCompound();
            e.putInt("Id", g);
            e.putString("Name", st.name());
            e.putString("Hair", st.hair().label());
            e.putString("Length", st.length().label());
            e.putString("Glasses", st.glasses().label());
            e.putBoolean("Known", known);
            if (known) {
                e.putString("Class", st.homeroom().label());
                e.putString("Club", m.clubs[g].label());
                e.putString("District", m.districts[g].label());
            }
            e.putBoolean("Accused", s.accused.contains(g));
            e.putInt("DaysOff", People.daysOff(s, g));
            e.putBoolean("Caught", s.caught.contains(g));
            e.putInt("Fondness", s.fondness[g]);
            e.putBoolean("Dead", s.dead.contains(g));
            e.putBoolean("Dating", s.dating == g);
            e.putBoolean("Fits", m.stillFits(g, s.found));
            girls.add(e);
        }
        nbt.put("Girls", girls);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, Net.BAG, buf);
    }

    public static void accuse(ServerPlayerEntity player, int girl) {
        RunState s = state(player.server);
        if (s.status != RunState.Status.PLAYING || girl < 0 || girl >= Roster.girlsFor(s.party.size()) || s.accused.contains(girl)) return;
        if (isGhost(player)) {
            announce(player, "", "Nobody can hear you. Tell one of the others.", 0);
            return;
        }
        if (Living.active(s)) {
            announce(player, "", "She's reading over your shoulder.", 0);
            return;
        }
        Mystery m = s.mystery();
        // They'd only be spending everyone else's guesses, and the last one would win it for them.
        if (Accomplice.is(player)) {
            announce(player, "", "You open your mouth. Your phone buzzes. You say nothing.", 0);
            return;
        }
        if (m.isYandere(girl)) {
            s.accused.add(girl);
            s.caught.add(girl);
            s.markDirty();
            if (s.caught.size() == m.yanderes.size()) {
                String names = String.join(" and ", m.yanderes.stream().map(y -> Roster.get(y).name()).toList());
                String ending = s.dating >= 0 && !Romance.datingHer(s) && !s.dead.contains(s.dating) ? Endings.stayedTogether(names, Roster.get(s.dating).firstName())
                        : s.pages.size() == Pages.COUNT ? Endings.lastPage(names)
                        : s.blackmailStage > 0 ? Endings.paidInFull(names)
                        : "It was " + names + (m.yanderes.size() > 1 ? ".\n\nThey're still smiling." : ".\n\nShe's still smiling.");
                if (s.party.size() == 1) {
                    // Alone, you have to stand there and hear it.
                    s.status = RunState.Status.WON; SaveSlot.delete();
                    s.caught.remove(girl);
                    String manner = manner(s);
                    s.caught.add(girl);
                    s.markDirty();
                    Kokuhaku.LOG.info("Found out: {} ({})", Roster.get(girl).name(), manner);
                    toTheRoom(player, s, 5, manner + "\n" + ending);
                    return;
                }
                end(player.server, s, 1, ending);
                return;
            }
            for (StudentEntity e : students(player.server)) if (e.getSkin() == girl) e.discard();
            int left = m.yanderes.size() - s.caught.size();
            announceAll(player.server, "You found one.", Roster.get(girl).firstName() + " was one of them. " + left + " left.");
            return;
        }
        s.accused.add(girl);
        Whispers.wrongGuess(girl);
        Shadow.saw(s, "You named " + Roster.get(girl).firstName() + ". She heard about it before lunch, and smiled.");
        loseGuess(player, s);
        if (s.guessesLeft <= 0) return;
        announce(player, "Wrong.", "It wasn't " + Roster.get(girl).firstName() + ". " + s.guessesLeft
                + (s.guessesLeft == 1 ? " guess left." : " guesses left."), 0);
    }

    /** One fewer, and if that was the last, she stops waiting. */
    static void loseGuess(ServerPlayerEntity player, RunState s) {
        s.guessesLeft--;
        s.markDirty();
        if (s.guessesLeft > 0) return;
        Cornered.begin(player.server, s, living(player.server));
    }

    // -------------------------------------------------------------- sending

    /** One line per difficulty: Easy spells it out, Impossible barely says anything. */
    private static String tier(RunState s, String easy, String medium, String hard, String impossible) {
        return new String[]{easy, medium, hard, impossible}[s.difficulty.ordinal()];
    }

    /** What you should be doing right now, in the corner of the screen. */
    private static String objective(ServerPlayerEntity p, RunState s) {
        if (!s.started) return "";
        if (isGhost(p)) return "You're a ghost. Search where people died and tell the others what you find.";
        if (Chase.active(p)) return "";
        if (Ward.isAdmitted(p)) return "Rest.";
        if (sleepover.containsKey(p.getUuid())) return tier(s, "Sleep in her bed.", "Get some sleep.", "It's late.", "");
        if (Living.active(s)) {
            if (s.bonk) return "Go home.";
            if (Kept.free(s)) return tier(s, "", "", "", "She's asleep. Three o'clock. Chinetsu Station.");
            if (Kept.on(s)) return "\"" + Devotion.ruleWords(s) + "\"  (" + Kept.fractures() + " broken)";
            if (s.devoted) return "\"" + Devotion.ruleWords(s) + "\"  (" + s.jealous + " of " + Living.limit(s) + ")";
            return "Stay beside her. Don't talk to other girls (" + s.jealous + " of " + Living.STRIKES + "). Home by midnight. Day " + s.day + " of " + Schedule.DAYS + ".";
        }
        int m = s.minute, curfew = s.difficulty.curfew;
        int locker = s.lockers[slot(s, p)];
        if (s.day > Schedule.DAYS) return tier(s, "Leave.", "Leave.", "Leave.", "");
        if (calledDown.contains(p.getUuid())) return tier(s, "Someone's waiting for you at the front door.", "Go down to the front door.", "The front door.", "Someone's at the door.");
        if (m >= curfew - 150) {
            return tier(s, "Go home and lock the front door before " + Schedule.clock(curfew) + ".",
                    "Be home by " + Schedule.clock(curfew) + ".", "It's getting late.", "She's closer than you think.");
        }
        if (Festival.on(s)) {
            String train = TrainRide.objective();
            if (train != null) return train;
            if (m < Festival.CLOSES) return tier(s, "Enjoy the School Festival on the sports field. Try the stalls. Fireworks at 19:00.", "The festival's on the sports field. Fireworks at 19:00.", "The festival.", "Everyone's here. So is she.");
        }
        boolean school = Schedule.isSchoolDay(s.day);
        if (Favours.objective() != null && m >= Schedule.AFTER_SCHOOL) return Favours.objective();
        if (Ward.noteDue(s) && school) return tier(s, "There's an envelope in your shoe locker.", "Check your locker.", "Your locker.", "She wants something.");
        if (!s.found.contains(0) && school) {
            return tier(s, "Find what's waiting in shoe locker " + locker + " at school.", "Check locker " + locker + ".", "Your locker.", "Someone left you something.");
        }
        if (school && m < Schedule.AFTER_SCHOOL) {
            List<String> subjects = Schedule.subjects(s.seed, Schedule.weekday(s.day));
            for (int i = 0; i < Schedule.PERIOD_START.length; i++) {
                int start = Schedule.PERIOD_START[i];
                if (m <= start + Schedule.LATE_GRACE) {
                    String subject = subjects.get(i), room = Schedule.room(subject, s.day), when = Schedule.clock(start);
                    return tier(s, "Get to " + subject + " in " + room + " by " + when + ".", subject + ", " + room + ".", "Don't be late.", "They're counting the empty desks.");
                }
            }
        }
        if (s.day % 3 == 0) return tier(s, "Check your locker for anything new.", "Your locker might have something.", "She writes to you.", "Read what she leaves.");
        if (m >= Schedule.AFTER_SCHOOL && s.yen < 0) return tier(s, "You owe \u00A5" + (-s.yen) + ". Work a shift at the shop by the station.", "You owe \u00A5" + (-s.yen) + ".", "You're in debt.", "You owe.");
        if (s.difficulty.ordinal() < 2 && s.found.size() > 1) {
            long fits = java.util.stream.IntStream.range(0, Roster.girlsFor(s.party.size())).filter(g -> !s.dead.contains(g) && !s.caught.contains(g) && s.mystery().stillFits(g, s.found)).count();
            if (fits == 1) return "Only one girl still fits. Open your bag (E) and accuse her.";
        }
        if (s.day <= 3) return tier(s, "Right-click people to ask about themselves and what they've seen. Press E for your bag.", "Ask around. Press E for your bag.", "Someone knows something.", "Don't stop looking.");
        return tier(s, "Ask people what they've noticed, then compare the clues with the Suspects in your bag (E).", "Ask around.", "Someone knows something.", "Don't stop looking.");
    }

    /** Where the arrow points, on the difficulties that have one: wherever the objective says you should be. */
    private static BlockPos waypoint(ServerPlayerEntity p, RunState s) {
        if (!s.started || s.difficulty.ordinal() >= 2 || s.day > Schedule.DAYS || Ward.isAdmitted(p)) return null;
        if (isGhost(p)) return nearestScene(p);
        int m = s.minute, curfew = curfewOf(s);
        if (calledDown.contains(p.getUuid())) return frontDoor(p.getServerWorld(), homeOf(s, p));
        if (m >= curfew - 150) return isHome(p, s) ? null : town.graph.pos(homeOf(s, p).inside());
        if (Festival.on(s) && m < Festival.CLOSES) return town.graph.pos(town.place(TrainRide.waiting() ? "station" : "field"));
        boolean school = Schedule.isSchoolDay(s.day);
        if (!Living.active(s) && m >= Schedule.AFTER_SCHOOL && Favours.objective() != null) return Favours.target(s, p.getServerWorld());
        if (!Living.active(s) && school && (!s.found.contains(0) || Ward.noteDue(s))) return town.graph.pos(town.foyer);
        if (school && m < Schedule.AFTER_SCHOOL) {
            List<String> subjects = Schedule.subjects(s.seed, Schedule.weekday(s.day));
            for (int i = 0; i < Schedule.PERIOD_START.length; i++) {
                if (m > Schedule.PERIOD_START[i] + Schedule.LATE_GRACE) continue;
                int room = Schedule.roomNumber(subjects.get(i));
                if (Schedule.practical(subjects.get(i), s.day)) return town.graph.pos(town.place(Schedule.practicalPlace(subjects.get(i))));
                return town.graph.pos(town.place(room == 0 ? Schedule.swimming(s.day) ? "pool" : "gym" : "center:" + Schedule.roomKey(room)));
            }
        }
        return null;
    }

    /** How much the edges of the screen should close in: 0 to 100. The month, the hour and how near she is. */
    private static int dread(ServerPlayerEntity p, RunState s) {
        if (!s.started) return 0;
        float d = s.day * 0.7f + Stalker.closeness(p) * 65f;
        int curfew = curfewOf(s);
        if (s.minute >= Schedule.EVENING) d += 10;
        if (s.minute >= curfew - 90 && !isHome(p, s)) d += 25;
        if (Living.active(s)) d += 15;
        return Math.min(100, (int) d);
    }

    private static void sendState(ServerPlayerEntity player, RunState s) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(s.day);
        buf.writeVarInt(s.minute);
        buf.writeVarInt(s.guessesLeft);
        buf.writeVarInt(s.difficulty.ordinal());
        buf.writeVarInt(s.status.ordinal());
        buf.writeVarInt(s.yen);
        buf.writeVarInt(s.lockers[slot(s, player)]);
        buf.writeVarInt(s.party.size());
        buf.writeVarInt(living(player.server).size());
        buf.writeLong(s.seed);
        buf.writeString(objective(player, s));
        buf.writeVarInt(dread(player, s));
        buf.writeVarInt(s.devoted ? Devotion.temper() : -1);
        BlockPos to = waypoint(player, s);
        buf.writeBoolean(to != null);
        if (to != null) buf.writeBlockPos(to);
        buf.writeVarInt(Phone.unread());
        buf.writeVarInt(Chase.secondsLeft(player));
        buf.writeBoolean(Chase.slowing(player));
        ServerPlayNetworking.send(player, Net.STATE, buf);
    }

    static void announceAll(MinecraftServer server, String title, String subtitle) {
        for (ServerPlayerEntity p : living(server)) announce(p, title, subtitle, 0);
    }

    private static void announce(ServerPlayerEntity player, String title, String subtitle, int style) {
        if (System.getenv("KOKUHAKU_BOT") != null) Kokuhaku.LOG.info("Told: {} | {}", title, subtitle);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(title);
        buf.writeString(subtitle);
        buf.writeVarInt(style);
        ServerPlayNetworking.send(player, Net.ANNOUNCE, buf);
    }

    /** Developer testing only (KOKUHAKU_AUTOTALK=n): answers every question with option n a couple of seconds later. */
    private static void devAutoTalk(MinecraftServer server) {
        String auto = System.getenv("KOKUHAKU_AUTOTALK");
        if (auto == null || ticks % 50 != 0) return;
        for (ServerPlayerEntity p : players(server)) {
            if (Talk.waiting(p)) {
                Kokuhaku.LOG.info("Auto-answering");
                Talk.choose(p, Math.min(Integer.parseInt(auto), Talk.answers(p) - 1));
            }
        }
    }

    static void sendWalkClock(ServerPlayerEntity p, int age) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(age);
        ServerPlayNetworking.send(p, Net.WALK_CLOCK, buf);
    }

    /** Developer testing only: KOKUHAKU_STAGE sets up one of the new scenes. */
    private static void devStage(ServerPlayerEntity p, RunState s) {
        switch (System.getenv("KOKUHAKU_STAGE")) {
            case "hurt" -> {
                s.yen = "broke".equals(System.getenv("KOKUHAKU_MONEY")) ? 0 : s.yen;
                p.teleport(p.getServerWorld(), -647.5, 22, -120.5, 0f, 0f);
                later(20, () -> hitByCar(p, p.getPos().add(0, 0, 6)));
            }
            case "live" -> {
                gone.add(p.getUuid());
                stay(p);
                later(WAKE_TICKS + 120, () -> devJealous(p, s));
            }
            case "liveend" -> {
                s.day = Schedule.DAYS;
                gone.add(p.getUuid());
                stay(p);
                later(WAKE_TICKS + 120, () -> {
                    s.minute = Schedule.AFTER_SCHOOL + 10;
                    goToSleep(p.server, s);
                });
            }
            case "accuse" -> accuse(p, s.freeYandere());
            case "ride" -> {
                s.chains = 9;
                s.day = Festival.DAY - 1;
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
                later(SLEEP_TICKS + NEWS_TICKS + WAKE_TICKS + 120, () -> {
                    BlockPos at = town.graph.pos(town.place("station"));
                    p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
                });
            }
            case "fest" -> {
                s.chains = 9;
                s.day = Festival.DAY - 1;
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
                later(SLEEP_TICKS + NEWS_TICKS + WAKE_TICKS + 120, () -> {
                    TrainRide.clear();
                    BlockPos at = town.graph.pos(town.place("field"));
                    p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 12.5, 180f, 8f);
                    s.minute = 10 * 60;
                    later(40, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                    later(60, () -> {
                        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() - 12.5, 0f, 8f);
                        s.yen = 2000;
                    });
                    later(90, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                    later(100, () -> {
                        s.minute = 19 * 60 - 1;
                        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90f, -25f);
                    });
                    later(300, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                    later(420, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                });
            }
            case "teach" -> {
                s.minute = Schedule.PERIOD_START[0] - 6;
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                later(30, () -> Teachers.update(p.server, s, town));
                later(80, () -> {
                    Teachers.settle(p.server, s, town);
                    List<StudentEntity> staff = new ArrayList<>(p.getServerWorld().getEntitiesByType(Kokuhaku.STUDENT, Teachers::isOne));
                    for (StudentEntity e : staff) Kokuhaku.LOG.info("Teacher {} at {} heading {}", e.getName().getString(), e.getBlockPos().toShortString(), e.hasRoute());
                    StudentEntity one = staff.stream().filter(e -> e.getY() > 24 && e.getY() < 30).findFirst().orElse(staff.get(0));
                    p.teleport(p.getServerWorld(), one.getX(), one.getY(), one.getZ() + 3, 180f, 0f);
                    later(30, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                    later(50, () -> Teachers.talk(p, s, one));
                    later(80, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                });
            }
            case "phone" -> {
                s.day = 5;
                s.minute = Schedule.AFTER_SCHOOL + 20;
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                if (System.getenv("KOKUHAKU_PHONEDEVOTED") != null) s.devoted = true;
                s.day = 4;
                Phone.text(p.server, s, "Haruka M.", "are you walking home the usual way tonight?");
                Phone.text(p.server, s, "Class 2-A", "who keeps leaving the art room window open");
                s.day = 5;
                Phone.text(p.server, s, "Class 2-A", "does anyone have the maths answers");
                Phone.text(p.server, s, "Class 2-A", "the last train was running at 3am again. i live by the line");
                Phone.fromHer(p.server, s, "Why are you at the station?");
                Phone.devReplies();
                later(100, () -> {
                    Kokuhaku.LOG.info("Unread {}", Phone.unread());
                    ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
                    Phone.open(p, false);
                });
                later(140, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                later(160, () -> Phone.reply(p, 0));
                later(290, () -> Phone.open(p, false));
                later(320, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "reel" -> {
                s.day = 9;
                Shadow.saw(s, "She took that photo from the foot of your bed.");
                Shadow.saw(s, "You left your door open. She sat on the end of your bed for an hour, then left a flower.");
                s.day = 14;
                Shadow.saw(s, "You chose her for the fireworks. She kept the ticket stub, and left you alone for three nights.");
                s.day = 21;
                Shadow.saw(s, "Sakura had got too close to you. She saw to that.");
                end(p.server, s, 1, "It was over.\n\nShe's still smiling.");
            }
            case "sweets" -> {
                s.gifts = 1;
                StudentEntity her = students(p.server).stream().filter(e -> Roster.get(e.getSkin()).female()).findFirst().orElseThrow();
                Student who = Roster.get(her.getSkin());
                her.setAiDisabled(true);
                int before = s.fondness[who.id()];
                p.teleport(p.getServerWorld(), her.getX() + 1.5, her.getY(), her.getZ(), 90f, 0f);
                Kokuhaku.LOG.info("Options: {}", optionsFor(s, who));
                sendDialogue(p, who, "Oh, hello.");
                later(30, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                later(60, () -> {
                    choose(p, who.id(), optionsFor(s, who).indexOf(GIVE_SWEETS));
                    Kokuhaku.LOG.info("Sweets given: fondness {} to {}, gifts {}", before, s.fondness[who.id()], s.gifts);
                });
            }
            case "stalls" -> {
                s.chains = 9;
                s.day = Festival.DAY - 1;
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
                later(SLEEP_TICKS + NEWS_TICKS + WAKE_TICKS + 160, () -> {
                    TrainRide.clear();
                    BlockPos at = town.graph.pos(town.place("field"));
                    p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                    s.minute = 10 * 60;
                    s.yen = 2000;
                    s.strikes = 2;
                    for (int kind = 0; kind < 4; kind++) {
                        int stall = kind;
                        later(10 + kind * 70, () -> Festival.devUse(p, s, stall));
                    }
                    later(300, () -> {
                        Kokuhaku.LOG.info("Stalls done: yen {} gifts {} notes {}", s.yen, s.gifts, s.notes);
                        s.minute = Schedule.AFTER_SCHOOL + 10;
                        StudentEntity teacher = p.getServerWorld().getEntitiesByType(Kokuhaku.STUDENT, Teachers::isOne).get(0);
                        Teachers.talk(p, s, teacher);
                        later(5, () -> Talk.choose(p, 1));
                        later(20, () -> Kokuhaku.LOG.info("Strikes after making up: {}, minute {}", s.strikes, s.minute));
                    });
                    later(360, () -> Festival.devWatch(p, s, s.freeYandere()));
                    later(520, () -> Kokuhaku.LOG.info("With her: safe tonight {}, fondness {}", Festival.safeTonight(s), s.fondness[s.freeYandere()]));
                    later(540, () -> Festival.devWatch(p, s, -1));
                    later(700, () -> Kokuhaku.LOG.info("Alone: notes {}, busy {}", s.notes.size(), busy.contains(p.getUuid())));
                });
            }

            case "betray" -> later(260, () -> {
                s.guessesLeft = 1;
                loseGuess(p, s);
                later(300, () -> Kokuhaku.LOG.info("Status {} alive {}", s.status, alive(p.server).size()));
            });
            case "lying" -> later(200, () -> {
                Accomplice.plant(p);
                Kokuhaku.LOG.info("Notes {} leads left {}", s.notes, Accomplice.leadsLeft(p));
                accuse(p, s.freeYandere());
                Kokuhaku.LOG.info("Caught {}", s.caught);
                openBag(p);
                later(30, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            });
            case "chores" -> {
                s.day = 2;
                s.yen = 5000;
                s.minute = Schedule.AFTER_SCHOOL + 20;
                StudentEntity him = students(p.server).stream().filter(e -> !Roster.get(e.getSkin()).female()).findFirst().orElseThrow();
                Student who = Roster.get(him.getSkin());
                him.setAiDisabled(true);
                p.teleport(p.getServerWorld(), him.getX() + 1.5, him.getY(), him.getZ(), 90f, 0f);
                Kokuhaku.LOG.info("Options: {}", optionsFor(s, who));
                choose(p, who.id(), optionsFor(s, who).indexOf(Favours.ASK));
                later(20, () -> Talk.choose(p, 0));
                later(50, () -> {
                    BlockPos at = Favours.target(s, p.getServerWorld());
                    Kokuhaku.LOG.info("Objective: {}, arrow to {}", Favours.objective(), waypoint(p, s));
                    useBlock(p, at);
                    Kokuhaku.LOG.info("Objective: {}", Favours.objective());
                    talk(p, him);
                    Kokuhaku.LOG.info("Favour finished, yen {}, objective {}", s.yen, Favours.objective());
                    later(10, () -> Talk.choose(p, 0));
                });
                later(90, () -> {
                    Shop.open(p, s);
                    later(10, () -> Talk.choose(p, 0));
                    later(30, () -> {
                        Shop.open(p, s);
                        later(10, () -> Talk.choose(p, 1));
                    });
                    later(50, () -> Kokuhaku.LOG.info("Shop done: chains {} sprays {} yen {}", s.chains, s.sprays, s.yen));
                    later(60, () -> Grab.start(p, s));
                    later(100, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                });
            }
            case "nice" -> {
                StudentEntity her = students(p.server).stream().filter(e -> Roster.get(e.getSkin()).female()).findFirst().orElseThrow();
                Student who = Roster.get(her.getSkin());
                her.setAiDisabled(true);
                p.teleport(p.getServerWorld(), her.getX() + 2, her.getY(), her.getZ(), 90f, 0f);
                Kokuhaku.LOG.info("Options for {}: {}", who.firstName(), optionsFor(s, who));
                later(20, () -> {
                    int before = s.fondness[who.id()];
                    her.setAiDisabled(false);
                    choose(p, who.id(), optionsFor(s, who).indexOf(Romance.NICE));
                    later(40, () -> Talk.choose(p, System.getenv("KOKUHAKU_NICE") == null ? 0 : Integer.parseInt(System.getenv("KOKUHAKU_NICE"))));
                    later(70, () -> {
                        Kokuhaku.LOG.info("Fondness {} to {}, flustered {}", before, s.fondness[who.id()], her.isFlustered());
                        ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
                    });
                    later(100, () -> choose(p, who.id(), optionsFor(s, who).indexOf(Romance.NICE)));
                });
            }
            case "evict" -> {
                if ("date".equals(System.getenv("KOKUHAKU_WITH"))) {
                    s.dating = Roster.active(1).stream().filter(i -> Roster.get(i).female() && !s.mystery().isYandere(i)).findFirst().orElseThrow();
                } else {
                    gone.add(p.getUuid());
                    stay(p);
                }
                later(WAKE_TICKS + 100, () -> {
                    List<Town.Seat> row = town.seats.get("01");
                    Vec3d chair = row.get(4).chair(), next = row.get(5).chair();
                    BlockPos pos = BlockPos.ofFloored(chair), other = BlockPos.ofFloored(next);
                    StudentEntity sitter = students(p.server).stream().filter(e -> e.getSkin() != s.livingWith && e.getSkin() != s.dating).findFirst().orElseThrow();
                    sitter.placeAt("", next);
                    sitter.sitOn(other, 180f);
                    Vec3d far = row.get(3).chair();
                    StudentEntity second = students(p.server).stream().filter(e -> e != sitter && e.getSkin() != s.livingWith && e.getSkin() != s.dating).findFirst().orElseThrow();
                    second.placeAt("", far);
                    second.sitOn(BlockPos.ofFloored(far), 180f);
                    Kokuhaku.LOG.info("Chair beside you: {} sitting {}", sitter.getSkin(), sitter.isSittingDown());
                    p.teleport(p.getServerWorld(), chair.x, chair.y + 1, chair.z + 1, 180f, 0f);
                    later(30, () -> {
                        Seats.sit(p, pos, p.getServerWorld().getBlockState(pos));
                        StudentEntity partner = Living.partner(p, s);
                        if (!Living.active(s)) {
                            partner.placeAt("", p.getPos().add(2, 1, 2));
                            lessonPending.add(p.getUuid());
                            Living.classTime(p, s, partner);
                        }
                    });
                    later(90, () -> {
                        ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
                        StudentEntity partner = Living.partner(p, s);
                        Kokuhaku.LOG.info("After: she's sitting {} at {}, they're sitting {} at {}, you riding {}", partner.isSittingDown(), partner.getBlockPos().toShortString(), sitter.isSittingDown(), sitter.getBlockPos().toShortString(), p.hasVehicle());
                        if (Living.active(s)) {
                            lessonPending.add(p.getUuid());
                            Living.classTime(p, s, partner);
                        }
                    });
                    later(220, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                });
            }
            case "seat" -> {
                gone.add(p.getUuid());
                stay(p);
                later(WAKE_TICKS + 100, () -> {
                    Vec3d chair = town.seats.get("01").get(4).chair();
                    BlockPos pos = BlockPos.ofFloored(chair);
                    p.teleport(p.getServerWorld(), chair.x, chair.y + 1, chair.z + 1, 180f, 0f);
                    later(40, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                    later(60, () -> {
                        Seats.sit(p, pos, p.getServerWorld().getBlockState(pos));
                        Kokuhaku.LOG.info("Sat at {}, eye {}", p.getPos(), p.getEyeY());
                    });
                    later(120, () -> {
                        ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
                        for (StudentEntity e : students(p.server)) if (e.getSkin() == s.livingWith) Kokuhaku.LOG.info("Beside you: {} at {} sitting {}", e.getSkin(), e.getPos(), e.isSittingDown());
                    });
                    later(180, () -> {
                        p.stopRiding();
                        later(20, () -> {
                            for (StudentEntity e : students(p.server)) if (e.getSkin() == s.livingWith) Kokuhaku.LOG.info("You stood: sitting {}", e.isSittingDown());
                        });
                    });
                });
            }
            case "date" -> {
                int girl = Roster.active(1).stream().filter(i -> Roster.get(i).female() && !s.mystery().isYandere(i)).findFirst().orElseThrow();
                s.fondness[girl] = 3;
                s.minute = Schedule.AFTER_SCHOOL + 30;
                for (StudentEntity e : students(p.server)) {
                    if (e.getSkin() != girl) continue;
                    p.teleport(p.getServerWorld(), e.getX() + 1.3, e.getY(), e.getZ(), 90f, 0f);
                    Student who = Roster.get(girl);
                    Kokuhaku.LOG.info("Option offered: {}", Romance.option(s, who));
                    choose(p, girl, optionsFor(s, who).indexOf(Romance.option(s, who)));
                    later(60, () -> {
                        Kokuhaku.LOG.info("Dating {}, option now {}", s.dating, Romance.option(s, who));
                        choose(p, girl, optionsFor(s, who).indexOf(Romance.option(s, who)));
                    });
                    later(120, () -> Talk.choose(p, 1));
                    later(180, () -> Kokuhaku.LOG.info("After the date: fondness {}, minute {}, dateOn {}", s.fondness[girl], s.minute, s.dateOn));
                    break;
                }
            }
            case "scan" -> {
                var world = p.getServerWorld();
                try (java.io.PrintWriter out = new java.io.PrintWriter(System.getenv("KOKUHAKU_SCAN"))) {
                    for (int z = -140; z <= 30; z += 1) {
                        StringBuilder row = new StringBuilder();
                        for (int x = -640; x <= -470; x += 1) {
                            char c = '.';
                            for (int y = 40; y >= 18; y--) {
                                var b = world.getBlockState(new BlockPos(x, y, z));
                                if (b.isAir()) continue;
                                String n = net.minecraft.registry.Registries.BLOCK.getId(b.getBlock()).getPath();
                                c = b.isOf(net.minecraft.block.Blocks.WATER) ? '~' : n.contains("grass") || n.contains("dirt") ? ',' : n.contains("brick") ? 'b' : n.contains("concrete") || n.contains("quartz") ? 'c' : n.contains("plank") || n.contains("oak") || n.contains("spruce") ? 'w' : n.contains("stone") ? 's' : n.contains("glass") ? 'g' : n.contains("iron") ? 'i' : n.contains("wool") || n.contains("carpet") ? 'o' : '#';
                                break;
                            }
                            row.append(c);
                        }
                        out.println(row);
                    }
                    var counts = new java.util.TreeMap<String, Integer>();
                    for (int y = 18; y <= 30; y++) for (int z = -60; z <= -10; z++) for (int x = -570; x <= -520; x++) counts.merge(y + " " + world.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString(), 1, Integer::sum);
                    counts.forEach((k, v) -> { if (v > 15) out.println(k + " = " + v); });
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
                var world2 = p.getServerWorld();
                try (java.io.PrintWriter out = new java.io.PrintWriter(System.getenv("KOKUHAKU_SCAN") + ".lab")) {
                    for (int x = -640; x <= -500; x++) for (int z = -140; z <= 30; z++) for (int y = 20; y <= 45; y++) {
                        var b = world2.getBlockState(new BlockPos(x, y, z));
                        if (b.isOf(net.minecraft.block.Blocks.BREWING_STAND) || b.isOf(net.minecraft.block.Blocks.CAULDRON) || b.isOf(net.minecraft.block.Blocks.ENCHANTING_TABLE) || b.isOf(net.minecraft.block.Blocks.LOOM) || b.isOf(net.minecraft.block.Blocks.SMOKER) || b.isOf(net.minecraft.block.Blocks.FURNACE) || b.isOf(net.minecraft.block.Blocks.CARTOGRAPHY_TABLE) || b.isOf(net.minecraft.block.Blocks.NOTE_BLOCK) || b.isOf(net.minecraft.block.Blocks.JUKEBOX)) out.println(b.getBlock().getTranslationKey().replace("block.minecraft.", "") + " " + x + " " + y + " " + z);
                    }
                    for (String key : town.rooms.keySet()) { var r = town.rooms.get(key); out.println("room " + key + " x " + r.x1() + ".." + r.x2() + " z " + r.z1() + ".." + r.z2() + " y " + r.y()); }
                    var r = town.rooms.get("01");
                    int mid = (r.x1() + r.x2()) / 2;
                    for (int y = r.y(); y <= r.y() + 4; y++) { StringBuilder row = new StringBuilder("y" + y + ": "); for (int z = r.z1() - 2; z <= r.z1() + 5; z++) row.append(net.minecraft.registry.Registries.BLOCK.getId(world2.getBlockState(new BlockPos(mid, y, z)).getBlock()).getPath()).append(' '); out.println(row); }
                    StringBuilder wide = new StringBuilder(); for (int x = r.x1(); x <= r.x2(); x++) wide.append(net.minecraft.registry.Registries.BLOCK.getId(world2.getBlockState(new BlockPos(x, r.y() + 2, r.z1() - 1)).getBlock()).getPath()).append(' ').append(net.minecraft.registry.Registries.BLOCK.getId(world2.getBlockState(new BlockPos(x, r.y() + 2, r.z1())).getBlock()).getPath()).append(" | "); out.println(wide);
                } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                try (java.io.PrintWriter out = new java.io.PrintWriter(System.getenv("KOKUHAKU_SCAN") + ".pool")) {
                    for (int z = 28; z <= 72; z++) {
                        StringBuilder row = new StringBuilder();
                        for (int x = -568; x <= -512; x++) {
                            char c = ' ';
                            for (int y = 26; y >= 19; y--) {
                                var b = world2.getBlockState(new BlockPos(x, y, z));
                                if (b.isAir()) continue;
                                String n = net.minecraft.registry.Registries.BLOCK.getId(b.getBlock()).getPath();
                                c = n.equals("water") ? '~' : n.contains("leaves") ? 'L' : n.contains("bars") ? '|' : n.contains("pane") || n.contains("glass") ? 'g' : (char) ('0' + (y - 19));
                                break;
                            }
                            row.append(c);
                        }
                        out.println(row + " " + z);
                    }
                    for (int n = 0; n < town.graph.size(); n++) { BlockPos q = town.graph.pos(n); if (q.getX() > -570 && q.getX() < -510 && q.getZ() > 28 && q.getZ() < 74) out.println("node " + q.toShortString()); }
                } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                java.util.Set<BlockPos> seen = new java.util.HashSet<>();
                try (java.io.PrintWriter out = new java.io.PrintWriter(System.getenv("KOKUHAKU_SCAN") + ".water")) {
                    for (int x = Town.MIN_X; x <= Town.MAX_X; x++) for (int z = Town.MIN_Z; z <= Town.MAX_Z; z++) for (int y = 18; y <= 40; y++) {
                        BlockPos at = new BlockPos(x, y, z);
                        if (seen.contains(at) || !world2.getBlockState(at).isOf(net.minecraft.block.Blocks.WATER)) continue;
                        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>(List.of(at));
                        seen.add(at);
                        int n = 0, x1 = x, x2 = x, z1 = z, z2 = z, y1 = y, y2 = y;
                        while (!q.isEmpty() && n < 20000) {
                            BlockPos c = q.poll();
                            n++;
                            x1 = Math.min(x1, c.getX()); x2 = Math.max(x2, c.getX()); z1 = Math.min(z1, c.getZ()); z2 = Math.max(z2, c.getZ()); y1 = Math.min(y1, c.getY()); y2 = Math.max(y2, c.getY());
                            for (var d : net.minecraft.util.math.Direction.values()) {
                                BlockPos m = c.offset(d);
                                if (m.getY() >= 18 && m.getY() <= 40 && m.getX() >= Town.MIN_X && m.getX() <= Town.MAX_X && m.getZ() >= Town.MIN_Z && m.getZ() <= Town.MAX_Z && world2.getBlockState(m).isOf(net.minecraft.block.Blocks.WATER) && seen.add(m)) q.add(m);
                            }
                        }
                        out.println(n + " water blocks x " + x1 + ".." + x2 + " z " + z1 + ".." + z2 + " y " + y1 + ".." + y2);
                    }
                    for (int y = 21; y <= 32; y += 1) {
                        out.println("y=" + y);
                        for (int z = 6; z <= 28; z += 2) {
                            StringBuilder row = new StringBuilder();
                            for (int x = -566; x <= -500; x += 2) {
                                var b = world2.getBlockState(new BlockPos(x, y, z));
                                row.append(b.isAir() ? ' ' : Character.toLowerCase(net.minecraft.registry.Registries.BLOCK.getId(b.getBlock()).getPath().charAt(0)));
                            }
                            out.println(row);
                        }
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
                Kokuhaku.LOG.info("Scan done");
            }
            case "practical" -> {
                s.day = 2;
                s.minute = Schedule.PERIOD_START[1];
                BlockPos at = town.graph.pos(town.place("lab"));
                Kokuhaku.LOG.info("Lab at {}", at.toShortString());
                p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                lessonSettled.add(p.getUuid());
                lessonPending.add(p.getUuid());
                Practical.start(p, s, "Science");
                later(20, () -> Kokuhaku.LOG.info("Practical use: {}", useBlock(p, new BlockPos(-598, 33, -79))));
                for (int delay : new int[]{50, 140, 240}) later(delay, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "kept" -> {
                Kokuhaku.LOG.info("Kept test: on {}, curfew {}", Kept.on(s), curfewOf(s));
                later(900, () -> {
                    StudentEntity her = Living.partner(p, s);
                    if (her != null) p.teleport(p.getServerWorld(), her.getX() + 30, her.getY(), her.getZ(), 0f, 0f);
                    Kokuhaku.LOG.info("Kept test: walked away at minute {}", s.minute);
                });
            }
            case "escape" -> {
                Kept.devReady(s);
                s.day = 12;
                s.minute = 21 * 60;
                BlockPos in = town.graph.pos(homeOf(s, p).inside());
                p.teleport(p.getServerWorld(), in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0f, 0f);
                later(30, () -> {
                    Kept.devReady(s);
                    p.teleport(p.getServerWorld(), in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0f, 0f);
                    Kokuhaku.LOG.info("Escape test: can pour tea {}", Kept.canPourTea(s, p));
                    Talk.say(p, "Test", "tea", Kept.pourTea(s));
                    later(10, () -> Talk.choose(p, 0));
                    later(30, () -> {
                        Talk.choose(p, 0);
                        BlockPos at = Kept.devStation();
                        s.minute = 27 * 60 - 2;
                        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                        Kokuhaku.LOG.info("Escape test: free {}, curfew {}, minute {}", Kept.free(s), curfewOf(s), s.minute);
                    });
                });
            }
            case "hallmap" -> later(40, () -> {
                ServerWorld world = p.getServerWorld();
                BlockPos c = town.graph.pos(town.place("center:Cafeteria"));
                Kokuhaku.LOG.info("HALLMAP center {}", c);
                Map<String, String> legend = new java.util.LinkedHashMap<>();
                String marks = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                for (int dy = 0; dy <= 2; dy++) {
                    Kokuhaku.LOG.info("HALLMAP layer y={}", c.getY() + dy);
                    for (int z = c.getZ() - 22; z <= c.getZ() + 22; z++) {
                        StringBuilder row = new StringBuilder();
                        for (int x = c.getX() - 34; x <= c.getX() + 34; x++) {
                            var state = world.getBlockState(new BlockPos(x, c.getY() + dy, z));
                            if (state.isAir()) {
                                row.append('.');
                                continue;
                            }
                            String name = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
                            if (state.getBlock() instanceof net.minecraft.block.StairsBlock) name += "/" + state.get(net.minecraft.block.StairsBlock.FACING);
                            if (!legend.containsKey(name)) legend.put(name, String.valueOf(marks.charAt(Math.min(legend.size(), marks.length() - 1))));
                            row.append(legend.get(name));
                        }
                        Kokuhaku.LOG.info("HALLMAP {} {}", z, row);
                    }
                }
                Kokuhaku.LOG.info("HALLMAP x from {} legend {}", c.getX() - 34, legend);
            });
            case "skirun" -> later(60, () -> {
                s.day = 6;
                s.minute = 11 * 60;
                sendState(p, s);
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                SkiSlope.devRun(p, s, town);
                for (int i = 1; i <= 8; i++) later(40 + i * 90, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            });
            case "forest" -> later(60, () -> {
                s.day = 4;
                s.scenarioDay = s.day;
                s.minute = Schedule.EVENING + 40;
                sendState(p, s);
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                BlockPos woods = ForestScares.thickest(p.getServerWorld());
                if (woods == null) return;
                p.teleport(p.getServerWorld(), woods.getX() + 0.5, woods.getY(), woods.getZ() + 0.5, 180f, 0f);
                // A slow walk north through the trees, so everything has a chance to happen.
                for (int i = 0; i < 400; i++) {
                    int step = i;
                    later(100 + i * 2, () -> {
                        double z = woods.getZ() + 0.5 - step * 0.2;
                        int top = p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, woods.getX(), (int) Math.floor(z));
                        p.teleport(p.getServerWorld(), woods.getX() + 0.5, top, z, 180f, 4f);
                    });
                }
                for (int i = 1; i <= 8; i++) later(i * 110, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            });
            case "meet" -> later(80, () -> {
                // Everyone in the party face to face on the pavement, to see what they look like to each other.
                for (ServerPlayerEntity q : players(p.server)) {
                    int i = Math.max(0, s.party.indexOf(q.getUuid()));
                    q.teleport(q.getServerWorld(), -643.5 + i * 2.2, 22, -24.5, i == 0 ? -90f : 90f, 5f);
                    for (int n = 1; n <= 4; n++) later(n * 40, () -> ServerPlayNetworking.send(q, Net.SHOT, PacketByteBufs.empty()));
                }
            });
            case "mountainview" -> later(60, () -> {
                s.day = 3;
                s.minute = 11 * 60;
                sendState(p, s);
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                String spots = System.getenv("KOKUHAKU_SPOTS");
                double[][] views = spots != null ? java.util.Arrays.stream(spots.split(";")).map(v -> java.util.Arrays.stream(v.split(",")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new)
                        : new double[][]{{-446, -300, 180, 1}, {-450, -560, 0, 18}, {-544, -1392, 160, 2}, {-1300, -900, 90, 30}};
                for (int i = 0; i < views.length; i++) {
                    double[] v = views[i];
                    int at = i * 140;
                    later(at + 1, () -> p.teleport(p.getServerWorld(), v[0] + 0.5, 300, v[1] + 0.5, (float) v[2], 10f));
                    later(at + 70, () -> {
                        int top = p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) v[0], (int) v[1]);
                        p.teleport(p.getServerWorld(), v[0] + 0.5, top + v[3], v[1] + 0.5, (float) v[2], 8f);
                        Kokuhaku.LOG.info("VIEW {} at {} {} ground {}", v[0], v[1], top, top);
                    });
                    later(at + 130, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                }
            });
            case "classlife" -> later(60, () -> {
                s.day = 3;
                s.minute = Schedule.PERIOD_START[1] - 2;
                for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) {
                    if (Routines.group(Roster.get(g)) == 0 && !s.mystery().isYandere(g)) {
                        s.dead.add(g);
                        break;
                    }
                }
                sendState(p, s);
                String subject = Schedule.subjects(s.seed, Schedule.weekday(s.day)).get(1);
                Town.Room room = town.rooms.get(Schedule.roomKey(Schedule.roomNumber(subject)));
                p.teleport(p.getServerWorld(), room.x1() + 6.5, room.y(), room.z1() + 9.5, 180f, 0f);
                for (int i = 1; i <= 6; i++) later(200 + i * 130, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            });
            case "radioscreen" -> later(80, () -> {
                Radio.use(p, s);
                later(30, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            });
            case "raining" -> later(60, () -> {
                s.day = 3;
                s.minute = 8 * 60 + 8;
                sendState(p, s);
                int x = -642, z = -100, y = p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
                p.teleport(p.getServerWorld(), x + 0.5, y, z + 0.5, 0f, 0f);
                var near = students(p.server).stream().filter(e -> e.getSkin() < Roster.TOTAL).limit(5).toList();
                for (int i = 0; i < near.size(); i++) near.get(i).placeAt("", new Vec3d(x + 0.5 + (i - 2) * 1.4, y, z + 4.5 + (i % 2) * 2));
                for (int i = 1; i <= 3; i++) later(20 + i * 30, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            });
            case "lunchscene" -> later(60, () -> {
                s.day = 3;
                s.minute = Schedule.LUNCH + 2;
                sendState(p, s);
                for (StudentEntity e : students(p.server)) {
                    if (e.getSkin() >= Roster.TOTAL) continue;
                    Routines.Target t = Routines.target(e.getSkin(), s, town);
                    BlockPos at = town.graph.pos(t.node());
                    Vec3d floor = new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    e.placeAt("", floor);
                    e.setRoute(t.key() + "@" + t.node(), List.of(floor), t.chair(), t.yaw());
                }
                later(100, () -> {
                    long sat = students(p.server).stream().filter(StudentEntity::isSittingDown).count();
                    Kokuhaku.LOG.info("LUNCH seated {} of {}", sat, students(p.server).size());
                    p.teleport(p.getServerWorld(), -590.5, 25, -103.5, 0f, 0f);
                });
            });
            case "lunchtime" -> later(60, () -> {
                s.day = 3;
                s.minute = Schedule.LUNCH + 2;
                sendState(p, s);
                BlockPos spot = town.graph.pos(town.place("center:Cafeteria"));
                p.teleport(p.getServerWorld(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
                for (StudentEntity e : students(p.server)) {
                    if (e.getSkin() >= Roster.TOTAL) continue;
                    Routines.Target t = Routines.target(e.getSkin(), s, town);
                    BlockPos at = town.graph.pos(t.node());
                    e.placeAt(t.key() + "@" + t.node(), new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
                }
                for (int i = 1; i <= 9; i++) later(i * 120, () -> {
                    s.minute = Schedule.LUNCH + 5;
                    StudentEntity near = null;
                    double best = Double.MAX_VALUE;
                    for (StudentEntity e : students(p.server)) {
                        if (e.getSkin() >= Roster.TOTAL || e.distanceTo(p) > 40) continue;
                        double d = e.squaredDistanceTo(p) * (e.isChatting() ? 0.1 : 1);
                        if (d < best) {
                            best = d;
                            near = e;
                        }
                    }
                    if (near != null) {
                        Vec3d to = near.getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize();
                        Vec3d stand = near.getPos().subtract(to.multiply(3.4));
                        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
                        p.teleport(p.getServerWorld(), stand.x, near.getY(), stand.z, yaw, 6f);
                    }
                    long talk = students(p.server).stream().filter(StudentEntity::isChatting).count();
                    Kokuhaku.LOG.info("HUMAN lunch chatting {} of {}, watching {}", talk, students(p.server).size(), near == null ? "nobody" : near.getName().getString());
                    later(6, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                });
            });
            case "survey" -> later(20, () -> {
                ServerWorld world = p.getServerWorld();
                Map<String, Integer> desks = new java.util.TreeMap<>();
                for (var room : town.seats.values()) {
                    for (Town.Seat seat : room) {
                        if (seat.chair() == null) continue;
                        BlockPos chair = BlockPos.ofFloored(seat.chair());
                        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++) {
                            BlockPos at = chair.add(dx, dy, dz);
                            var st = world.getBlockState(at);
                            if (st.isAir() || st.getCollisionShape(world, at).isEmpty()) continue;
                            String shape = String.format("%.2f", st.getCollisionShape(world, at).getMax(net.minecraft.util.math.Direction.Axis.Y));
                            desks.merge(net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).toString() + " top " + shape, 1, Integer::sum);
                        }
                    }
                }
                Kokuhaku.LOG.info("SURVEY desks {}", desks);
                StringBuilder out = new StringBuilder();
                for (int z = -520; z <= 520; z += 6) {
                    for (int x = -1030; x <= 500; x += 6) {
                        BlockPos top = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                        var st = world.getBlockState(top.down());
                        out.append(x).append(',').append(z).append(',').append(top.getY() - 1).append(',').append(net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).getPath()).append('\n');
                    }
                }
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("TEMP"), "kk", "survey.csv"), out.toString());
                } catch (java.io.IOException ex) {
                    Kokuhaku.LOG.warn("SURVEY failed", ex);
                }
                Kokuhaku.LOG.info("SURVEY done");
            });
            case "festride" -> later(60, () -> {
                s.day = Festival.DAY;
                s.minute = 9 * 60;
                sendState(p, s);
                Festival.setUp(p.getServerWorld(), town);
                TrainRide.meet(s, town, p.server);
                BlockPos at = town.graph.pos(town.place("station"));
                p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
            });
            case "sights" -> later(60, () -> {
                double[][] views = {
                        {-430, 26, -28, 150, 6}, {-455, 24, -56, 90, 4}, {-505, 23, -56, -90, 2},
                        {-436, 27, -200, 100, 8}, {-448, 26, -232, 90, 2}, {-560, 56, -232, -90, 22},
                        {-712, 26, 40, 180, 4}, {-688, 26, 128, -60, 6}, {-452, 25, 128, 45, 6}};
                for (int i = 0; i < views.length; i++) {
                    double[] v = views[i];
                    later(30 + i * 70, () -> p.teleport(p.getServerWorld(), v[0], v[1], v[2], (float) v[3], (float) v[4]));
                    later(30 + i * 70 + 55, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                }
                later(30 + views.length * 70, () -> {
                    p.teleport(p.getServerWorld(), -452.5, 23, -201.5, 0f, 0f);
                    s.yen = 2000;
                });
                later(30 + views.length * 70 + 40, () -> {
                    Talk.choose(p, 0);
                    Kokuhaku.LOG.info("SIGHTS hired skis, yen {}", s.yen);
                });
                for (int i = 1; i <= 5; i++) later(30 + views.length * 70 + 40 + i * 110, () -> {
                    Kokuhaku.LOG.info("SIGHTS skiing busy {} at {}", busy.contains(p.getUuid()), p.getPos());
                    ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty());
                });
            });
            case "story" -> later(60, () -> {
                Story.record(4, 17);
                Kokuhaku.LOG.info("STORY radio placed {}", homeOf(s, p));
                s.day = 5;
                s.minute = Schedule.AFTER_SCHOOL + 4;
                sendState(p, s);
                later(30, () -> {
                    Story.minute(p, s);
                    Radio.use(p, s);
                });
                Student her = Roster.get(s.freeYandere());
                later(120, () -> {
                    Talk.choose(p, 0);
                    Talk.choose(p, 0);
                    Kokuhaku.LOG.info("STORY notes {}", s.notes);
                    Kokuhaku.LOG.info("STORY days off {} her {}", People.daysOff(s, 3), People.daysOff(s, her.id()));
                    s.day = 24;
                    s.minute = Schedule.EVENING + 60;
                    Blackout.minute(s, living(p.server), town);
                    Kokuhaku.LOG.info("STORY blackout, dark {}", p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS));
                });
                later(220, () -> {
                    Talk.choose(p, 0);
                    s.day = 8;
                    Confront.ask(p, s, her);
                });
                later(300, () -> Talk.choose(p, 0));
                later(380, () -> Talk.choose(p, 0));
                later(460, () -> Kokuhaku.LOG.info("STORY status {} caught {}", s.status, s.caught));
            });
            case "radio" -> later(60, () -> Radio.use(p, s));
            case "taken" -> later(60, () -> {
                found(p);
                taken(p, s);
            });
            case "cornered" -> later(60, () -> {
                s.guessesLeft = 1;
                loseGuess(p, s);
            });
            case "human" -> later(60, () -> {
                var near = students(p.server).stream().filter(e -> e.getSkin() < Roster.TOTAL).limit(8).toList();
                Vec3d facing = Vec3d.fromPolar(0f, p.getYaw());
                Vec3d side = new Vec3d(-facing.z, 0, facing.x);
                for (int i = 0; i < near.size(); i++) {
                    Vec3d at = p.getPos().add(facing.multiply(3.5 + (i / 4) * 2.2)).add(side.multiply((i % 4 - 1.5) * 1.7));
                    near.get(i).placeAt("", at);
                    near.get(i).setYaw(p.getYaw() + 180f);
                }
                near.get(0).setMood(StudentEntity.GRIEVING);
                later(200, () -> Kokuhaku.LOG.info("HUMAN chatting {}", near.stream().filter(StudentEntity::isChatting).count()));
                later(400, () -> Kokuhaku.LOG.info("HUMAN chatting {}", near.stream().filter(StudentEntity::isChatting).count()));
                later(80, () -> {
                    Student who = Roster.get(near.get(1).getSkin());
                    Kokuhaku.LOG.info("HUMAN first {}", People.greet(s, who, false, false, "USUAL"));
                    for (int i = 0; i < 4; i++) Kokuhaku.LOG.info("HUMAN again {}", People.greet(s, who, false, false, "USUAL"));
                    s.minute += 400;
                    Kokuhaku.LOG.info("HUMAN later {}", People.greet(s, who, false, true, "USUAL"));
                    s.minute -= 400;
                    int home = 0;
                    for (int i = 0; i < Roster.TOTAL; i++) if (People.stayingHome(s, i)) home++;
                    Kokuhaku.LOG.info("HUMAN home today {}", home);
                });
            });
            case "unease" -> later(60, () -> {
                s.day = 7;
                s.minute = Schedule.EVENING + 30;
                sendState(p, s);
            });
            case "newstuff" -> later(100, () -> {
                List<? extends StudentEntity> all = students(p.server);
                StudentEntity half = all.get(0), buried = all.get(1);
                half.setPosition(half.getX(), Math.floor(half.getY()) - 0.5, half.getZ());
                buried.setPosition(buried.getX(), Math.floor(buried.getY()) - 3, buried.getZ());
                Kokuhaku.LOG.info("NEW embedded: half at {}, buried at {}", half.getPos(), buried.getPos());
                later(80, () -> {
                    Kokuhaku.LOG.info("NEW half now {} health {} alive {} wall {}", half.getPos(), half.getHealth(), half.isAlive(), half.isInsideWall());
                    Kokuhaku.LOG.info("NEW buried now {} health {} alive {} wall {}", buried.getPos(), buried.getHealth(), buried.isAlive(), buried.isInsideWall());
                });
                later(100, () -> {
                    Mystery m = s.mystery();
                    int g = java.util.stream.IntStream.range(0, Roster.girlsFor(s.party.size())).filter(i -> !m.isYandere(i)).findFirst().orElse(0);
                    for (int d = 2; d <= 4; d++) {
                        s.day = d;
                        Whispers.talkedTo(s, g);
                    }
                    s.day = 5;
                    Kokuhaku.LOG.info("NEW warning: {}", Whispers.morningNote(s));
                    Whispers.talkedTo(s, g);
                    Kokuhaku.LOG.info("NEW went back to {}, marked {}", g, s.livingVictim);
                    s.livingVictim = -1;
                    for (int d : new int[]{8, 15, 22, 29}) {
                        s.day = d;
                        Turning.morning(p, s);
            if (s.day == 1) Radio.place(p, homeOf(s, p));
                        Kokuhaku.LOG.info("NEW day {}: lock {} marked {} notes {}", d, s.lockBrokenOn, s.livingVictim, s.notes.size());
                        s.livingVictim = -1;
                    }
                    s.day = 20;
                    s.dead.add(g);
                    s.accused.add(g == 1 ? 2 : 1);
                    Set<String> heard = new java.util.LinkedHashSet<>();
                    for (int i = 0; i < 60; i++) heard.add(Rumours.line(s));
                    Kokuhaku.LOG.info("NEW rumours: {}", heard);
                    Kokuhaku.LOG.info("NEW her note: {} / {}", Habits.note(s), Habits.kind(s));
                    for (int i = 0; i < 40; i++) {
                        String night = Habits.night(s);
                        if (!night.isEmpty()) Kokuhaku.LOG.info("NEW her night: {}", night);
                    }
                    Kokuhaku.LOG.info("NEW reel: {}", Shadow.reel(s, false).replace("\n", " | "));
                });
            });
            case "bedtime" -> {
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
            }
            case "dating" -> {
                int girl = s.mystery().yanderes.contains(0) ? 1 : 0;
                s.dating = girl;
                s.fondness[girl] = 3;
                Kokuhaku.LOG.info("Dating test: partner in a lesson is {}", Living.partner(p, s));
                for (int i = 0; i < 2; i++) {
                    s.day = 2 + i;
                    for (WalkScene.Step step : WalkTalk.script(s, List.of(girl), 900)) Kokuhaku.LOG.info("Dating walk day {}: {} {}", s.day, step.line(), step.answers().stream().map(WalkScene.Answer::label).toList());
                }
                s.minute = Schedule.LUNCH + 5;
                Romance.talk(p, s, Roster.get(girl));
                later(50, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "door" -> {
                Town.Home home = homeOf(s, p);
                BlockPos door = frontDoor(p.getServerWorld(), home);
                Kokuhaku.LOG.info("Door test: front door {}, doors known {}", door, doorOwners.size());
                if (door != null) {
                    p.teleport(p.getServerWorld(), door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 0f, 0f);
                    p.getInventory().selectedSlot = 1;
                    later(10, () -> {
                        useBlock(p, door);
                        Kokuhaku.LOG.info("Door test: with key, locked {}", s.doorLocked[0]);
                        p.getInventory().selectedSlot = 0;
                        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
                    });
                    later(30, () -> Kokuhaku.LOG.info("Door test: without key, {}", useBlock(p, door)));
                    BlockPos other = doorOwners.keySet().stream().filter(d -> !d.equals(door)).findFirst().orElse(null);
                    later(40, () -> Kokuhaku.LOG.info("Door test: someone else's, {}", other == null ? null : useBlock(p, other)));
                    later(50, () -> {
                        p.getInventory().selectedSlot = 1;
                        useBlock(p, door);
                        Kokuhaku.LOG.info("Door test: unlocked again, locked {}", s.doorLocked[0]);
                    });
                }
            }
            case "chase" -> {
                s.minute = s.difficulty.curfew - 1;
                p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 1200, 0, false, false));
                p.teleport(p.getServerWorld(), -700.5, 22, -216.5, 0f, 0f);
                for (int delay : new int[]{120, 200, 300}) later(delay, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "truename" -> {
                BlockPos at = town.trueName;
                Kokuhaku.LOG.info("True name at {} on {} above {}, standable beside it: {}", at.toShortString(), p.getServerWorld().getBlockState(at).getBlock(), p.getServerWorld().getBlockState(at.down()).getBlock(), town.graph.nearest(Vec3d.ofCenter(at)));
                p.teleport(p.getServerWorld(), at.getX() + 4.5, at.getY() + 1, at.getZ() + 0.5, 90f, 14f);
                later(40, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                later(60, () -> useBlock(p, at));
                later(130, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                later(200, () -> Kokuhaku.LOG.info("Rock read, route from the beach: {} waypoints", town.graph.path(town.place("beach"), town.graph.nearest(Vec3d.ofCenter(at))).size()));
            }
            case "ghost" -> {
                s.yen = 16000;
                List<ServerPlayerEntity> all = players(p.server);
                ServerPlayerEntity friend = all.get(all.size() - 1);
                later(60, () -> {
                    if (friend != p) {
                        found(friend);
                        taken(friend, s);
                    }
                });
                later(500, () -> callBack(p, friend.getGameProfile().getName()));
                later(700, () -> Kokuhaku.LOG.info("Ghost test: gone {}, ghost {}, flying {}, living {}, yen {}", gone.size(), ghosts.size(), friend.getAbilities().flying, living(p.server).size(), s.yen));
                later(760, () -> Kokuhaku.LOG.info("Ghost objective: {}", objective(friend, s)));
                later(800, () -> ServerPlayNetworking.send(friend, Net.SHOT, PacketByteBufs.empty()));
            }
            case "pool", "hoops" -> {
                s.day = "pool".equals(System.getenv("KOKUHAKU_STAGE")) ? 1 : 2;
                s.minute = Schedule.PERIOD_START[1];
                lessonSettled.add(p.getUuid());
                lessonPending.add(p.getUuid());
                Pe.start(p, s);
                for (int delay : new int[]{50, 110, 160, 220, 300}) later(delay, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "ward" -> {
                Ward.admit(p, s, "car");
                for (int delay : new int[]{110, 170, 240, 300, 380, 480}) later(delay, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "lesson" -> {
                s.day = 1;
                s.minute = Schedule.PERIOD_START[0] - 2;
                String subject = Schedule.subjects(s.seed, 0).stream().filter(n -> Schedule.roomNumber(n) != 0).findFirst().orElseThrow();
                int period = Schedule.subjects(s.seed, 0).indexOf(subject);
                s.minute = Schedule.PERIOD_START[period] - 2;
                BlockPos at = town.graph.pos(town.place("center:" + Schedule.roomKey(Schedule.roomNumber(subject))));
                p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
                for (int delay : new int[]{240, 300, 360, 420, 480, 560}) later(delay, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "swim" -> {
                p.teleport(p.getServerWorld(), -745.5, 18.5, -262.5, 0f, 0f);
                for (int i = 1; i <= 16; i++) {
                    int at = i;
                    later(i * 20, () -> Kokuhaku.LOG.info("Swim {}: y {} in water {} air {}", at, (float) p.getY(), p.isTouchingWater(), p.getAir()));
                }
                later(120, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "crowd" -> devCrowd(p, s, 0);
            case "grab" -> {
                s.day = 6;
                s.minute = Schedule.EVENING;
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                p.teleport(p.getServerWorld(), -520.5, 22, 60.5, 180f, 0f);
                later(40, () -> Grab.start(p, s));
            }
            case "boards" -> {
                p.getAbilities().allowFlying = true;
                p.getAbilities().flying = true;
                p.sendAbilitiesUpdate();
                List<BlockPos> boards = new ArrayList<>(town.registers.keySet());
                for (int i = 0; i < boards.size(); i++) {
                    BlockPos b = boards.get(i);
                    Kokuhaku.LOG.info("Board {} at {} on {} above {}", town.registers.get(b), b.toShortString(), p.getServerWorld().getBlockState(b).getBlock(), p.getServerWorld().getBlockState(b.down(2)).getBlock());
                    if (i >= 6) continue;
                    later(10 + i * 40, () -> p.teleport(p.getServerWorld(), b.getX() + 0.5, b.getY(), b.getZ() + 3.5, 180f, 5f));
                    later(45 + i * 40, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                }
            }
            case "train" -> {
                s.day = 2;
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
            }
            case "night" -> {
                s.day = 4;
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
            }
            case "figure" -> {
                s.day = 12;
                s.minute = Schedule.EVENING + 30;
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                p.teleport(p.getServerWorld(), -640.5, 22, -60.5, 180f, 0f);
                later(60, () -> {
                    Vec3d at = Stalker.devWhere();
                    if (at == null) return;
                    p.teleport(p.getServerWorld(), p.getX(), p.getY(), p.getZ(), (float) (Math.toDegrees(Math.atan2(at.z - p.getZ(), at.x - p.getX())) - 90), 0f);
                    later(10, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                });
            }
            case "stalk" -> {
                s.day = 20;
                s.minute = s.difficulty.curfew - 70;
                p.getServerWorld().setTimeOfDay(Schedule.worldTime(s.day, s.minute));
                p.teleport(p.getServerWorld(), -640.5, 22, -60.5, 180f, 0f);
                later(100, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                later(220, () -> {
                    p.setYaw(0f);
                    p.teleport(p.getServerWorld(), p.getX(), p.getY(), p.getZ(), 0f, 0f);
                });
                later(240, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
            }
            case "nextday" -> {
                s.day = 3;
                s.dead.add(Roster.GIRLS - 7);
                s.minute = Schedule.AFTER_SCHOOL + 10;
                goToSleep(p.server, s);
            }
            case "note" -> {
                s.blackmailer = s.freeYandere();
                s.blackmailDue = s.day;
                s.found.add(0);
                Ward.openNote(p, s);
            }
            case "hospital" -> {
                double[][] spots = {{-630, 24, -201, -90, -14}, {-628, 42, -232, -50, 18}, {-560, 46, -168, 135, 18}};
                p.getAbilities().allowFlying = true;
                p.getAbilities().flying = true;
                p.sendAbilitiesUpdate();
                for (int i = 0; i < spots.length; i++) {
                    double[] v = spots[i];
                    later(10 + i * 70, () -> p.teleport(p.getServerWorld(), v[0], v[1], v[2], (float) v[3], (float) v[4]));
                    later(10 + i * 70 + 60, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
                }
            }
            default -> {
            }
        }
    }

    /** Developer testing only: keeps standing next to someone until she's had enough, begging the second one. */
    private static void devCrowd(ServerPlayerEntity p, RunState s, int step) {
        if (s.jealous >= Living.limit(s)) {
            BlockPos inside = town.graph.pos(homeOf(s, p).inside());
            p.teleport(p.getServerWorld(), inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0f, 0f);
            return;
        }
        for (StudentEntity e : students(p.server)) {
            if (e.getSkin() == s.livingWith || !Roster.get(e.getSkin()).female()) continue;
            p.teleport(p.getServerWorld(), e.getX() + 1, e.getY(), e.getZ(), 90f, 0f);
            if (step == 40) talk(p, e);
            break;
        }
        later(20, () -> devCrowd(p, s, step + 1));
    }

    /** Developer testing only: talks to girls until she's had enough, then goes home. */
    private static void devJealous(ServerPlayerEntity p, RunState s) {
        if (s.jealous >= Living.limit(s)) {
            BlockPos inside = town.graph.pos(homeOf(s, p).inside());
            p.teleport(p.getServerWorld(), inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0f, 0f);
            return;
        }
        for (StudentEntity e : students(p.server)) {
            if (Roster.get(e.getSkin()).female() && e.getSkin() != s.livingWith) {
                p.teleport(p.getServerWorld(), e.getX() + 1.5, e.getY(), e.getZ(), 90f, 0f);
                later(20, () -> talk(p, e));
                break;
            }
        }
        later(220, () -> devJealous(p, s));
    }

    /** Developer testing only: flies round key spots and photographs each one. */
    private static void devTour(ServerPlayerEntity p, RunState s) {
        BlockPos home = town.graph.pos(homeOf(s, p).inside());
        List<double[]> spots = new ArrayList<>(List.of(
                new double[]{-604.5, 26, -82.5, 180, 0},
                new double[]{-601.5, 26, -63.5, 180, 5},
                new double[]{-640, 40, -84, 90, 15},
                new double[]{-693, 24, -74, 90, 5},
                new double[]{-700, 24, -116, 180, 5},
                new double[]{home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 10}));
        p.getAbilities().allowFlying = true;
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
        for (int i = 0; i < spots.size(); i++) {
            double[] v = spots.get(i);
            int delay = 20 + i * 100;
            later(delay, () -> p.teleport(p.getServerWorld(), v[0], v[1], v[2], (float) v[3], (float) v[4]));
            later(delay + 90, () -> ServerPlayNetworking.send(p, Net.SHOT, PacketByteBufs.empty()));
        }
        later(20 + spots.size() * 100, () -> {
            p.teleport(p.getServerWorld(), home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0f, 0f);
            goToSleep(p.server, s);
        });
    }
}
