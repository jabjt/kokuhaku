package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The school goes on around you. In a lesson nobody just sits there: they
 * whisper, pass notes, put a hand up, and get told off. And it remembers: a
 * classmate's desk stays empty with a flower on it, and flowers gather by the
 * doors. Nobody ever mentions it.
 */
final class SchoolLife {
    private static final int SEE_WHO_EVERY = 20, FLOWERS_EVERY = 40, WHISPER_TICKS = 70, NEXT_FROM = 90, NEXT_MORE = 140, HAND_TICKS = 55, CAUGHT_ONE_IN = 3;
    private static final int MOST_FLOWERS = 10, FLOWER_REACH = 4;
    private static final float NECK = 14f;
    private static final double NEXT_TO = 2.7, SEEN_FROM = 56;
    private static final String[] FLOWERS = {"minecraft:white_tulip", "minecraft:oxeye_daisy", "minecraft:azure_bluet"};
    private static final String[] TOLD_OFF = {"Eyes on the board, %s.", "%s. Is there something you'd like to tell the class?", "I can hear you, %s.", "%s, I won't ask again."};
    private static final Random RANDOM = new Random();

    private static int ticks = 0, nextEvent = 0, whisperUntil = 0, laidFor = -1;
    private static Town.Room room = null;
    private static final List<StudentEntity> pupils = new ArrayList<>();
    private static StudentEntity teacher = null, whisperer = null, other = null;
    private static final List<Entity> desks = new ArrayList<>(), gate = new ArrayList<>();

    private SchoolLife() {
    }

    static void clear() {
        ticks = nextEvent = whisperUntil = 0;
        laidFor = -1;
        room = null;
        pupils.clear();
        teacher = whisperer = other = null;
        desks.clear();
        gate.clear();
    }

    static void tick(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        ticks++;
        if (ticks % FLOWERS_EVERY == 0) atTheDoors(server, s, living, town);
        ServerPlayerEntity sat = living.stream().filter(RunManager::inLesson).findFirst().orElse(null);
        Town.Room now = null;
        if (sat != null) for (Town.Room r : town.rooms.values()) if (r.contains(sat.getPos())) now = r;
        if (now != room) {
            lessonOver();
            room = now;
            if (now != null) lessonBegins(server, s, town);
        }
        if (room == null || sat == null) return;
        if (ticks % SEE_WHO_EVERY == 0) register(server);
        lesson(sat);
    }

    private static void register(MinecraftServer server) {
        pupils.clear();
        teacher = null;
        for (StudentEntity e : RunManager.studentsOf(server)) if (e.isSittingDown() && room.contains(e.getPos())) pupils.add(e);
        for (StudentEntity e : server.getOverworld().getEntitiesByType(com.kokuhaku.Kokuhaku.STUDENT, Teachers::isOne)) if (room.contains(e.getPos())) teacher = e;
    }

    private static void lessonBegins(MinecraftServer server, RunState s, Town town) {
        register(server);
        nextEvent = ticks + NEXT_FROM;
        com.kokuhaku.Kokuhaku.LOG.info("Lesson: {} sat down, teacher {}", pupils.size(), teacher == null ? "absent" : teacher.getName().getString());
        // Whoever's gone: their desk's still there.
        Set<Integer> groups = new HashSet<>();
        for (StudentEntity e : pupils) if (e.getSkin() < Roster.TOTAL) groups.add(Routines.group(Roster.get(e.getSkin())));
        String key = town.rooms.entrySet().stream().filter(entry -> entry.getValue() == room).map(Map.Entry::getKey).findFirst().orElse(null);
        if (key == null) return;
        int number = Integer.parseInt(key);
        for (int dead : s.dead) {
            int group = Routines.group(Roster.get(dead));
            if (!groups.contains(group)) continue;
            Vec3d chair = Routines.seat(Roster.get(dead), group, number, town).chair();
            if (chair != null) desks.add(LunchHall.loose(server.getOverworld(), chair.add(0, 1.02, -1), FLOWERS[dead % FLOWERS.length], 0.34f));
        }
    }

    private static void lessonOver() {
        pupils.forEach(e -> e.setWalkTalking(false));
        pupils.clear();
        if (teacher != null) teacher.setWalkTalking(false);
        teacher = whisperer = other = null;
        desks.forEach(e -> {
            if (e != null) e.discard();
        });
        desks.clear();
    }

    private static StudentEntity beside(StudentEntity e) {
        for (StudentEntity near : pupils) if (near != e && near.squaredDistanceTo(e) < NEXT_TO * NEXT_TO) return near;
        return null;
    }

    private static void lesson(ServerPlayerEntity sat) {
        if (teacher != null) teacher.setWalkTalking(whisperer == null || ticks % 100 < 60);
        if (whisperer != null) {
            whisperer.getLookControl().lookAt(other, NECK, NECK);
            other.getLookControl().lookAt(whisperer, NECK, NECK);
            if (ticks < whisperUntil) return;
            whisperer.setWalkTalking(false);
            if (teacher != null && RANDOM.nextInt(CAUGHT_ONE_IN) == 0) {
                teacher.getLookControl().lookAt(whisperer, NECK, NECK);
                String line = TOLD_OFF[RANDOM.nextInt(TOLD_OFF.length)];
                if (whisperer.getSkin() < Roster.TOTAL) RunManager.tell(sat, "", "\"" + String.format(line, Roster.get(whisperer.getSkin()).firstName()) + "\"");
            }
            whisperer = other = null;
        }
        if (ticks < nextEvent || pupils.size() < 2) return;
        nextEvent = ticks + NEXT_FROM + RANDOM.nextInt(NEXT_MORE);
        StudentEntity e = pupils.get(RANDOM.nextInt(pupils.size()));
        switch (RANDOM.nextInt(4)) {
            case 0, 1 -> {
                StudentEntity near = beside(e);
                if (near == null) return;
                whisperer = e;
                other = near;
                whisperUntil = ticks + WHISPER_TICKS;
                e.setWalkTalking(true);
            }
            case 2 -> {
                // A note, under the desk: a rustle, and a look.
                StudentEntity near = beside(e);
                if (near == null) return;
                e.getLookControl().lookAt(near, NECK, NECK);
                near.getLookControl().lookAt(e, NECK, NECK);
                e.getWorld().playSound(null, e.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.3f, 1.3f);
            }
            default -> {
                e.wave(HAND_TICKS);
                if (teacher != null) teacher.getLookControl().lookAt(e, NECK, NECK);
            }
        }
    }

    /** One more bunch by the doors, each time. Nobody saw who left them. */
    private static void atTheDoors(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        int want = Math.min(MOST_FLOWERS, s.dead.size());
        if (want == laidFor) return;
        BlockPos doors = town.graph.pos(town.foyer);
        if (living.stream().noneMatch(p -> p.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(doors)) < SEEN_FROM * SEEN_FROM)) return;
        laidFor = want;
        gate.forEach(e -> {
            if (e != null) e.discard();
        });
        gate.clear();
        ServerWorld world = server.getOverworld();
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos at : BlockPos.iterateOutwards(doors, FLOWER_REACH, 1, FLOWER_REACH)) if (world.isAir(at) && RunManager.standable(world, at) && at.getManhattanDistance(doors) >= 2) floor.add(at.toImmutable());
        java.util.Collections.shuffle(floor, new Random(s.seed));
        for (int i = 0; i < want && i < floor.size(); i++) gate.add(LunchHall.loose(world, Vec3d.ofBottomCenter(floor.get(i)).add(0, 0.03, 0), FLOWERS[i % FLOWERS.length], 0.42f));
    }
}
