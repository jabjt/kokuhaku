package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Who sits where at lunch, and what's in front of them. Friends fill a table
 * together; your table has a chair left for you, with five girls round it, and
 * anyone there's no chair for eats out in the courtyard.
 */
final class LunchHall {
    static final int YOUR_TABLE = 1, COOK_SKIN = Roster.TOTAL + 21;
    private static final int YOUR_CHAIR_X = -598, AT_YOUR_TABLE = 5;
    private static final String TAG = "kokuhaku_food";
    /** What people have in front of them. */
    private static final String[] MAINS = {"minecraft:mushroom_stew", "minecraft:cooked_porkchop", "minecraft:rabbit_stew", "minecraft:bread", "minecraft:cooked_chicken", "minecraft:beetroot_soup", "minecraft:baked_potato"};
    private static final String[] SIDES = {"minecraft:apple", "minecraft:cookie", "minecraft:sweet_berries", "minecraft:carrot", "minecraft:dried_kelp"};
    private static final double COOK_X = -582.5, COOK_Y = 25, COOK_Z = -114.5;
    private static final float COOK_FACES = 90f, NECK = 14f;

    private static int plannedDay = -1;
    private static final Map<Integer, Town.LunchSeat> seatOf = new HashMap<>();
    private static final List<Integer> girls = new ArrayList<>();
    private static Town.LunchSeat yours = null;
    private static final List<Entity> food = new ArrayList<>();
    private static StudentEntity cook = null;
    private static final List<Town.LunchSeat> flowers = new ArrayList<>();
    private static final int TURN_EVERY = 90, SEE_WHO_EVERY = 20;
    private static final Map<Integer, List<StudentEntity>> tables = new HashMap<>();
    private static final Map<Integer, StudentEntity> talking = new HashMap<>();
    private static int ticks = 0;

    private LunchHall() {
    }

    /** Every table but yours gets on with its own conversation: one talks, the rest turn to them, and then it's someone else. */
    static void talkAmongThemselves(MinecraftServer server, RunState s, ServerPlayerEntity watched) {
        ticks++;
        if (ticks % SEE_WHO_EVERY == 0) {
            tables.clear();
            for (StudentEntity e : RunManager.studentsOf(server)) {
                Town.LunchSeat seat = seatOf.get(e.getSkin());
                if (seat == null || !e.isSittingDown() || !e.goingToLunch()) continue;
                if (watched != null && s.mystery().isYandere(e.getSkin()) && seat.table() != YOUR_TABLE) continue;
                tables.computeIfAbsent(seat.table(), t -> new ArrayList<>()).add(e);
            }
        }
        for (var entry : tables.entrySet()) {
            int table = entry.getKey();
            List<StudentEntity> here = entry.getValue();
            // The more people have gone, the less there is to say.
            if (here.size() < 2 || watched != null && table == YOUR_TABLE || table % 3 < s.dead.size() / 3) continue;
            StudentEntity speaker = talking.get(table);
            if (speaker == null || !here.contains(speaker) || (ticks + table * 37) % TURN_EVERY == 0) {
                if (speaker != null) speaker.setWalkTalking(false);
                speaker = here.get(here.get(0).getRandom().nextInt(here.size()));
                speaker.setWalkTalking(true);
                talking.put(table, speaker);
            }
            for (StudentEntity e : here) e.getLookControl().lookAt(e == speaker ? here.get(here.get(0) == e ? 1 : 0) : speaker, NECK, NECK);
        }
        // She isn't at your table. That doesn't mean she isn't with you.
        if (watched != null) {
            for (StudentEntity e : RunManager.studentsOf(server)) {
                Town.LunchSeat seat = seatOf.get(e.getSkin());
                if (seat != null && seat.table() != YOUR_TABLE && s.mystery().isYandere(e.getSkin()) && e.isSittingDown()) e.getLookControl().lookAt(watched, NECK, NECK);
            }
        }
    }

    /** The bell. Nobody finishes a sentence. */
    static void stopTalking() {
        talking.values().forEach(e -> e.setWalkTalking(false));
        talking.clear();
        tables.clear();
    }

    static void clear() {
        plannedDay = -1;
        seatOf.clear();
        girls.clear();
        yours = null;
        food.clear();
        cook = null;
        flowers.clear();
        tables.clear();
        talking.clear();
    }

    private static boolean here(RunState s, int id) {
        return !s.dead.contains(id) && !People.stayingHome(s, id);
    }

    private static void plan(RunState s, Town town) {
        if (plannedDay == s.day) return;
        plannedDay = s.day;
        seatOf.clear();
        girls.clear();
        flowers.clear();
        Random r = new Random(s.seed ^ s.day * 48611L);
        List<Town.LunchSeat> free = new ArrayList<>(town.lunchSeats);
        yours = free.stream().filter(seat -> seat.table() == YOUR_TABLE && seat.yaw() != 0f && seat.chair().getX() == YOUR_CHAIR_X).findFirst().orElse(free.get(0));
        free.remove(yours);
        int party = s.party.size();
        List<Integer> everyone = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(party); g++) if (here(s, g)) everyone.add(g);
        Collections.shuffle(everyone, r);
        // She saves you a seat.
        if (everyone.remove((Integer) s.dating)) everyone.add(0, s.dating);
        girls.addAll(everyone.subList(0, Math.min(AT_YOUR_TABLE, everyone.size())));
        List<Town.LunchSeat> beside = free.stream().filter(seat -> seat.table() == YOUR_TABLE).toList();
        for (int i = 0; i < girls.size() && i < beside.size(); i++) seatOf.put(girls.get(i), beside.get(i));
        free.removeAll(beside);
        // Whoever's gone: their friends don't take the chair.
        for (int i = 0; i < s.dead.size() && !free.isEmpty(); i++) flowers.add(free.remove(free.size() - 1 - r.nextInt(Math.min(free.size(), 12))));
        // Everyone else, friends together.
        List<Integer> rest = new ArrayList<>(everyone.subList(girls.size(), everyone.size()));
        for (int b = 0; b < Roster.boysFor(party); b++) if (here(s, Roster.GIRLS + b)) rest.add(Roster.GIRLS + b);
        rest.sort(Comparator.<Integer>comparingInt(id -> Routines.group(Roster.get(id))).thenComparingInt(id -> id));
        for (int i = 0; i < rest.size() && i < free.size(); i++) seatOf.put(rest.get(i), free.get(i));
    }

    /** Their chair today, or null if there's no room and they're eating outside. */
    static Town.LunchSeat seatFor(int id, RunState s, Town town) {
        plan(s, town);
        return seatOf.get(id);
    }

    static Town.LunchSeat yours(RunState s, Town town) {
        plan(s, town);
        return yours;
    }

    static List<Integer> yourTable(RunState s, Town town) {
        plan(s, town);
        return girls;
    }

    static StudentEntity cook() {
        return cook;
    }

    static void spawnCook(ServerWorld world) {
        StudentEntity e = Kokuhaku.STUDENT.create(world);
        if (e == null) return;
        e.refreshPositionAndAngles(COOK_X, COOK_Y, COOK_Z, COOK_FACES, 0f);
        e.setBodyYaw(COOK_FACES);
        e.setHeadYaw(COOK_FACES);
        e.setSkin(COOK_SKIN);
        e.setCustomName(Text.literal(LunchScene.COOK));
        e.setAiDisabled(true);
        world.spawnEntity(e);
        cook = e;
    }

    private static Entity display(ServerWorld world, Vec3d at, EntityType<?> type, String nbt) {
        Entity e = type.create(world);
        if (e == null) return null;
        try {
            e.readNbt(StringNbtReader.parse(nbt));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            Kokuhaku.LOG.warn("Bad food: {}", nbt);
            return null;
        }
        e.setPosition(at);
        e.addCommandTag(TAG);
        world.spawnEntity(e);
        food.add(e);
        return e;
    }

    /** A tray, flat on the table. */
    private static void tray(ServerWorld world, Vec3d at) {
        display(world, at, EntityType.BLOCK_DISPLAY, "{block_state:{Name:\"minecraft:light_gray_carpet\"},transformation:{translation:[-0.22f,0f,-0.16f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[0.44f,1f,0.32f]}}");
    }

    /** Something to eat, lying flat. */
    static Entity dish(ServerWorld world, Vec3d at, String item, float size) {
        return display(world, at, EntityType.ITEM_DISPLAY, "{item:{id:\"" + item + "\",Count:1b},item_display:\"fixed\",transformation:{translation:[0f,0.08f,0f],left_rotation:[0.7071f,0f,0f,0.7071f],right_rotation:[0f,0f,0f,1f],scale:[" + size + "f," + size + "f," + size + "f]}}");
    }

    /** Your tray: a main, whatever you asked for. */
    static Entity serveYou(ServerWorld world, RunState s, Town town, String item) {
        Vec3d at = yours(s, town).tray();
        tray(world, at);
        return dish(world, at, item, 0.3f);
    }

    /** The trays go out when there's someone to see them. */
    static void lay(ServerWorld world, RunState s, Town town) {
        plan(s, town);
        Random r = new Random(s.seed ^ s.day * 91L);
        for (var entry : seatOf.entrySet()) {
            Vec3d at = entry.getValue().tray();
            tray(world, at);
            dish(world, at.add(-0.07, 0, 0), MAINS[r.nextInt(MAINS.length)], 0.26f);
            dish(world, at.add(0.13, 0, 0), SIDES[r.nextInt(SIDES.length)], 0.14f);
        }
        for (Town.LunchSeat empty : flowers) dish(world, empty.tray(), "minecraft:white_tulip", 0.34f);
        Kokuhaku.LOG.info("Lunch is out: {} places laid, {} left empty", seatOf.size(), flowers.size());
    }

    static void clearTables() {
        food.forEach(Entity::discard);
        food.clear();
    }

    static boolean laid() {
        return !food.isEmpty();
    }

    /** A flower or the like that stays when lunch is cleared. Whoever put it there takes it away. */
    static Entity loose(ServerWorld world, Vec3d at, String item, float size) {
        Entity e = dish(world, at, item, size);
        food.remove(e);
        return e;
    }

    static BlockPos chairOf(Town.LunchSeat seat) {
        return seat.chair();
    }
}
