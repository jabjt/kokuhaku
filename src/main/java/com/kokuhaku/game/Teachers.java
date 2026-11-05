package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.world.town.Town;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The staff. They stand at the front of whichever room their subject is in,
 * walk the halls between lessons, eat in the cafeteria and go back to the staff
 * room after school. They'll tell you how much trouble you're in and let you
 * work a little of it off. They know more about Haruka than they'll say.
 */
public final class Teachers {
    public record Teacher(String name, String subject) {
    }

    public static final List<Teacher> STAFF = List.of(new Teacher("Ms. Kuroda", "Japanese"), new Teacher("Mr. Shibata", "Mathematics"), new Teacher("Ms. Bennett", "English"),
            new Teacher("Mr. Hirota", "History"), new Teacher("Dr. Asano", "Science"), new Teacher("Ms. Nagai", "Art"), new Teacher("Mr. Tachibana", "Music"),
            new Teacher("Mrs. Oshima", "Home Economics"), new Teacher("Coach Iwata", "PE"));
    /** Their skins come after the students' and the townsfolks'. */
    private static final int FIRST_SKIN = Roster.TOTAL + 12;
    private static final int MORNING = 7 * 60 + 40, GOES_BACK = 18 * 60 + 30, MAKE_UP_MINUTES = 60, DAYS_BETWEEN_MAKE_UPS = 2, GROUPS = Schedule.GROUPS;
    private static final String STAFF_ROOM = "center:11";
    private static final String HOW = "How am I doing?", MAKE_UP = "Can I make up for it?", ABOUT = "Can you tell me about one of the girls?", HARUKA = "Who was Haruka?";
    private static final String[] HARUKA_LINES = {
            "Where did you hear that name? ...She was a student here. Three years ago. She was a nice girl. That's all I'm going to say.",
            "You're still asking. Look. There was an accident, and then there wasn't anyone to ask about it. The hospital kept her file on the top floor. I never told you that.",
            "Every teacher here got a note that year. Nobody will admit it. I burned mine. Go home, and lock your door, and stop asking me."};
    private static final Random RANDOM = new Random();

    private static int madeUpOn = -DAYS_BETWEEN_MAKE_UPS, askedOn = 0, harukaAsked = 0;
    private static final java.util.Map<Integer, Integer> fronts = new java.util.HashMap<>();

    private Teachers() {
    }

    static void clear() {
        madeUpOn = -DAYS_BETWEEN_MAKE_UPS;
        askedOn = 0;
        harukaAsked = 0;
        fronts.clear();
    }

    public static boolean isOne(StudentEntity e) {
        return e.getSkin() >= FIRST_SKIN && e.getSkin() < FIRST_SKIN + STAFF.size();
    }

    private static Teacher of(StudentEntity e) {
        return STAFF.get(Math.floorMod(e.getSkin() - FIRST_SKIN, STAFF.size()));
    }

    /** Whoever marks your absence. */
    static String nameFor(String subject) {
        return STAFF.stream().filter(t -> t.subject().equals(subject)).map(Teacher::name).findFirst().orElse(STAFF.get(0).name());
    }

    static void spawn(ServerWorld world, Town town) {
        BlockPos at = town.graph.pos(town.place(STAFF_ROOM));
        for (int i = 0; i < STAFF.size(); i++) {
            StudentEntity e = Kokuhaku.STUDENT.create(world);
            if (e == null) continue;
            e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
            e.setSkin(FIRST_SKIN + i);
            e.setCustomName(Text.literal(STAFF.get(i).name()));
            world.spawnEntity(e);
        }
    }

    private static List<? extends StudentEntity> all(MinecraftServer server) {
        return server.getOverworld().getEntitiesByType(Kokuhaku.STUDENT, Teachers::isOne);
    }

    /** The node at the front of a classroom, facing the rows. */
    private static int front(Town town, int room) {
        return fronts.computeIfAbsent(room, n -> findFront(town, n));
    }

    private static int findFront(Town town, int room) {
        Town.Room r = town.rooms.get(Schedule.roomKey(room));
        Vec3d board = new Vec3d((r.x1() + r.x2()) / 2.0, r.y(), r.z1() + 3);
        int best = town.place("center:" + Schedule.roomKey(room));
        double closest = Double.MAX_VALUE;
        // On the classroom floor: the nearest waypoint to the board can be up on the furniture.
        for (int n = 0; n < town.graph.size(); n++) {
            BlockPos at = town.graph.pos(n);
            if (at.getY() != r.y() || !r.contains(Vec3d.ofBottomCenter(at))) continue;
            double distance = board.squaredDistanceTo(Vec3d.ofBottomCenter(at));
            if (distance < closest) {
                closest = distance;
                best = n;
            }
        }
        return best;
    }

    /** Which room, if any, has this subject right now. */
    private static int roomNow(Teacher t, RunState s, int period) {
        for (int group = 0; group < GROUPS; group++) {
            List<String> subjects = Schedule.subjects(s.seed + group, Schedule.weekday(s.day));
            String subject = subjects.get(period);
            if (subject.equals(t.subject()) || subject.equals("Class Meeting") && t == STAFF.get(0)) return Schedule.roomFor(group, subject);
        }
        return -1;
    }

    /** Where they should be: a key that changes when the answer does, and the node. */
    private static Object[] target(Teacher t, RunState s, Town town, Random daily) {
        int m = s.minute;
        if (Festival.on(s)) return new Object[]{"festival", town.graph.wander(town.place("field"), 6, daily)};
        if (!Schedule.isSchoolDay(s.day) || m < MORNING || m >= GOES_BACK) return new Object[]{"staff", town.place(STAFF_ROOM)};
        if (m >= Schedule.AFTER_SCHOOL) return new Object[]{"after", t.subject().equals("PE") ? town.place("gym") : town.place(STAFF_ROOM)};
        if (m >= Schedule.LUNCH && m < Schedule.PERIOD_START[4]) return new Object[]{"lunch", town.graph.wander(town.place("center:Cafeteria"), 3, daily)};
        for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
            int start = Schedule.PERIOD_START[p];
            if (m < start - 4 || m >= start + Schedule.PERIOD_LENGTH) continue;
            int room = roomNow(t, s, p);
            if (room >= 0 && Schedule.practical(t.subject(), s.day)) return new Object[]{"lesson", town.place(Schedule.practicalPlace(t.subject()))};
            if (room == 0) return new Object[]{"lesson", town.place(Schedule.swimming(s.day) ? "pool" : "gym")};
            if (room > 0) return new Object[]{"lesson" + room, front(town, room)};
        }
        // Between lessons, or nothing to teach: the halls, keeping an eye on things.
        return new Object[]{"halls", town.graph.wander(town.place("hall:" + Schedule.roomKey(1 + daily.nextInt(Schedule.ROOMS))), 3, daily)};
    }


    private static Random daily(RunState s, StudentEntity e) {
        return new Random(s.seed ^ s.day * 6101L ^ e.getSkin() * 8123L);
    }

    /** Sends everyone on their way, once a game minute. */
    static void update(MinecraftServer server, RunState s, Town town) {
        for (StudentEntity e : all(server)) {
            if (e.isTalking()) continue;
            Object[] target = target(of(e), s, town, daily(s, e));
            int node = (int) target[1];
            String key = target[0] + "@" + node;
            if (e.isHeadingTo(key)) continue;
            e.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ((String) target[0]).startsWith("lesson") ? new net.minecraft.item.ItemStack(com.kokuhaku.item.ModItems.CHALK) : net.minecraft.item.ItemStack.EMPTY);
            e.setRoute(key, WalkRoute.routeFor(e, town, node), null, 0f);
        }
    }

    /** A lesson passes in a blink for you, so they're already at the board. */
    static void settle(MinecraftServer server, RunState s, Town town) {
        for (StudentEntity e : all(server)) {
            if (e.getWorld().getClosestPlayer(e, Routines.WATCHED) != null) continue;
            Object[] target = target(of(e), s, town, daily(s, e));
            int node = (int) target[1];
            BlockPos p = town.graph.pos(node);
            Vec3d at = new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            e.placeAt("", at);
            e.setRoute(target[0] + "@" + node, List.of(at), null, 0f);
        }
    }

    private static String greeting(RunState s) {
        if (s.dead.size() >= 3) return "Not now. There's another assembly. Have you got something to say?";
        if (!s.dead.isEmpty()) return "You should be careful, walking home. All of you. What is it?";
        return "Yes? Make it quick, I've got marking.";
    }

    private static String standing(RunState s) {
        int left = RunManager.strikesLeft(s);
        if (s.strikes == 0) return "Not a mark against you. Keep it that way.";
        return s.strikes + " marks against you. " + (left <= 3 ? "One more bad week and the head will have you out of this school." : left <= 7 ? "You're starting to worry me." : "Nothing that can't be fixed.");
    }

    static void talk(ServerPlayerEntity p, RunState s, StudentEntity e) {
        e.getNavigation().stop();
        e.getLookControl().lookAt(p, 30f, 30f);
        String name = of(e).name();
        List<Talk.Option> options = new ArrayList<>();
        options.add(Talk.option(HOW, q -> Talk.say(q, name, standing(s), Talk.leave("Thank you."))));
        if (s.strikes > 0 && s.day - madeUpOn >= DAYS_BETWEEN_MAKE_UPS && s.minute >= Schedule.AFTER_SCHOOL && s.minute < s.difficulty.curfew - 2 * MAKE_UP_MINUTES) {
            options.add(Talk.option(MAKE_UP, q -> makeUp(q, s, name)));
        }
        if (askedOn != s.day) options.add(Talk.option(ABOUT, q -> aboutSomeone(q, s, name)));
        if (!s.pages.isEmpty() || s.scenarios.contains(6)) options.add(Talk.option(HARUKA, q -> {
            Talk.say(q, name, HARUKA_LINES[Math.min(harukaAsked, HARUKA_LINES.length - 1)], Talk.leave("..."));
            harukaAsked++;
        }));
        options.add(Talk.leave("Never mind."));
        Talk.say(p, name, greeting(s), e.getId(), options.toArray(new Talk.Option[0]));
    }

    /** An hour's filing, and one mark comes off. */
    private static void makeUp(ServerPlayerEntity p, RunState s, String name) {
        madeUpOn = s.day;
        s.strikes--;
        RunManager.skipMinutes(p.server, s, MAKE_UP_MINUTES);
        Kokuhaku.LOG.info("{} let you work off a strike, {} left", name, s.strikes);
        RunManager.tell(p, "An hour later", name + " crosses one mark out. \"Don't make me regret it.\"");
    }

    private static void aboutSomeone(ServerPlayerEntity p, RunState s, String name) {
        askedOn = s.day;
        List<Integer> unknown = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.known.contains(g) && !s.dead.contains(g)) unknown.add(g);
        if (unknown.isEmpty()) {
            Talk.say(p, name, "You seem to know more about them than I do. Is that wise?", Talk.leave("Probably not."));
            return;
        }
        Student girl = Roster.get(unknown.get(RANDOM.nextInt(unknown.size())));
        RunManager.learned(p, girl);
        Talk.say(p, name, "You could ask " + girl.firstName() + " yourself. But alright: here's what's on her file.", Talk.leave("Thank you."));
    }
}
