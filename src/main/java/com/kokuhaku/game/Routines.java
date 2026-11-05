package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.Traits.Club;
import com.kokuhaku.game.Traits.Homeroom;
import com.kokuhaku.world.town.Town;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where each student should be at a given time. Weekdays: home, walk to
 * school, homeroom, lessons (special rooms for science, art, music, home ec
 * and PE), breaks in the hall, lunch, club, home. Weekends: a morning at
 * home, an afternoon out somewhere, home by evening.
 */
public final class Routines {
    /** A waypoint to reach; chair = where to sit on arrival, or null. */
    public record Target(String key, int node, Vec3d chair, float yaw) {
    }

    private static final int LEAVE_HOME = 7 * 60 + 55;
    private static final int GO_HOME = 17 * 60 + 40;
    private static final int WEEKEND_OUT = 10 * 60;
    private static final int WEEKEND_BACK = 17 * 60;
    private static final int LATE_ONE_IN = 12, RUNNING_LATE = 30;
    private static final String[] HANGOUTS = {"station", "beach", "park", "shops", "field"}, UNDER_A_ROOF = {"station", "shops"};
    /** The chairs face the board to the north. */
    private static final float FACE_BOARD = 180f;
    static final double WATCHED = 40;

    private Routines() {
    }

    public static Target target(int id, RunState s, Town town) {
        Student st = Roster.get(id);
        Random daily = new Random(s.seed ^ (s.day * 7919L) ^ (id * 104729L));
        int m = s.minute;
        // Everyone keeps their own hours, so they don't all spill out of their doors at once.
        int leaveHome = LEAVE_HOME - 20 + daily.nextInt(35);
        int goHome = GO_HOME - 40 + daily.nextInt(70);
        // Friends go where their friends go.
        Random friends = new Random(s.seed ^ (s.day * 6151L) ^ (group(st) * 49157L));
        // Somebody's always running late.
        if (daily.nextInt(LATE_ONE_IN) == 0) leaveHome += RUNNING_LATE;

        if (Festival.on(s)) {
            // All of them, all day, in their own time.
            int arrive = Festival.OPENS + daily.nextInt(120), leave = Festival.CLOSES + daily.nextInt(40);
            if (m < arrive || m >= leave) return home(id, s, town);
            return scattered("festival", "field", town, daily, 9);
        }
        if (!Schedule.isSchoolDay(s.day)) {
            int out = WEEKEND_OUT + daily.nextInt(150), back = WEEKEND_BACK + daily.nextInt(120);
            if (m < out || m >= back) return home(id, s, town);
            return scattered("hangout", Rain.now(s) ? UNDER_A_ROOF[friends.nextInt(UNDER_A_ROOF.length)] : HANGOUTS[friends.nextInt(HANGOUTS.length)], town, daily, 6);
        }
        if (m < leaveHome || m >= goHome || People.stayingHome(s, id)) return home(id, s, town);
        if (m >= Schedule.AFTER_SCHOOL) return Rain.now(s) && s.mystery().clubs[id] == Club.TENNIS ? scattered("club", "gym", town, daily, 3) : club(s.mystery().clubs[id], town, daily);
        if (m >= Schedule.LUNCH && m < Schedule.PERIOD_START[4]) {
            Town.LunchSeat place = LunchHall.seatFor(id, s, town);
            if (place != null) return new Target("lunch", place.node(), Vec3d.ofBottomCenter(place.chair()), place.yaw());
            String[] spots = Rain.now(s) ? new String[]{"foyer"} : new String[]{"courtyard_w", "courtyard_e"};
            return scattered("yard", spots[friends.nextInt(spots.length)], town, daily, 3);
        }
        int group = group(st);
        List<String> subjects = Schedule.subjects(s.seed + group, Schedule.weekday(s.day));
        for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
            int start = Schedule.PERIOD_START[p];
            if (m >= start && m < start + Schedule.PERIOD_LENGTH) return lesson(subjects.get(p), st, group, town, daily, s.day);
        }
        if (m < Schedule.PERIOD_START[0]) return seat(st, group, Schedule.homeroomFor(group), town);
        // A break between lessons: out in the hall by wherever you're due next.
        for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
            if (Schedule.PERIOD_START[p] > m) {
                int next = Schedule.roomFor(group, subjects.get(p));
                return scattered("break", "hall:" + Schedule.roomKey(next == 0 ? Schedule.homeroomFor(group) : next), town, daily, 4);
            }
        }
        return scattered("break", "hall:" + Schedule.roomKey(Schedule.homeroomFor(group)), town, daily, 4);
    }

    private static Target home(int id, RunState s, Town town) {
        return new Target("home", town.homes.get(s.homes[id]).inside(), null, Float.NaN);
    }

    /** Which of the six groups a student is in: three to a homeroom. */
    public static int group(Student st) {
        int index = 0;
        for (Student other : Roster.STUDENTS) {
            if (other.id() == st.id()) break;
            if (other.homeroom() == st.homeroom()) index++;
        }
        return (st.homeroom() == Homeroom.A ? 0 : 3) + index % 3;
    }

    private static Target lesson(String subject, Student st, int group, Town town, Random daily, int day) {
        int room = Schedule.roomFor(group, subject);
        if (Schedule.practical(subject, day)) return scattered("lesson:" + subject, Schedule.practicalPlace(subject), town, daily, 2);
        return room == 0 ? scattered("lesson:" + subject, Schedule.swimming(day) ? "pool" : "gym", town, daily, 3) : seat(st, group, room, town);
    }

    private static Target club(Club club, Town town, Random daily) {
        return scattered("club", clubPlace(club), town, daily, 3);
    }

    /** Where a club meets. */
    public static String clubPlace(Club club) {
        return switch (club) {
            case ART -> "center:06";
            case COOKING -> "center:08";
            case DRAMA -> "gym_stage";
            case KENDO -> "gym_kendo";
            case LITERATURE -> "center:Library";
            case MUSIC -> "center:07";
            case PHOTOGRAPHY -> "center:Darkroom";
            case TENNIS -> "tennis";
        };
    }

    /** Each group fills its own stretch of seats, in id order, so nobody ends up on someone's lap. */
    static Target seat(Student st, int group, int room, Town town) {
        int index = group * 8;
        for (Student other : Roster.STUDENTS) {
            if (other.id() == st.id()) break;
            if (group(other) == group) index++;
        }
        List<Town.Seat> seats = town.seats.get(Schedule.roomKey(room));
        Town.Seat seat = seats.get(index % seats.size());
        return new Target("seat", seat.node(), seat.chair(), FACE_BOARD);
    }

    /** Somewhere walkable near a place, so a crowd spreads out over real floor. */
    private static Target scattered(String why, String place, Town town, Random daily, int steps) {
        return new Target(why + ":" + place, town.graph.wander(town.place(place), steps, daily), null, Float.NaN);
    }

    /** Their seat's been taken, so they stand at the back and don't argue. */
    private static Target notClaimed(Target t) {
        if (t.chair() == null || !Seats.isClaimed(BlockPos.ofFloored(t.chair()))) return t;
        return new Target(t.key() + ":moved", t.node(), null, Float.NaN);
    }

    /** Sends a student on their way if their destination changed. */
    public static void update(StudentEntity e, RunState s, Town town) {
        if (e.isWalkingAway()) return;
        Target t = notClaimed(target(e.getSkin(), s, town));
        String key = t.key() + "@" + t.node();
        if (e.isHeadingTo(key)) return;
        e.setRoute(key, WalkRoute.routeFor(e, town, t.node()), t.chair(), t.yaw());
    }

    /** Puts a student straight where the timetable has them: a lesson passes in a blink for you. */
    public static void settle(StudentEntity e, RunState s, Town town) {
        // Nobody appears in front of you. Anyone you could see walks in like anyone else.
        if (e.getWorld().getClosestPlayer(e, WATCHED) != null) {
            update(e, s, town);
            return;
        }
        Target t = notClaimed(target(e.getSkin(), s, town));
        BlockPos p = town.graph.pos(t.node());
        Vec3d at = new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        e.placeAt("", at);
        e.setRoute(t.key() + "@" + t.node(), List.of(at), t.chair(), t.yaw());
    }

    public static Vec3d homePos(int id, RunState s, Town town) {
        BlockPos p = town.graph.pos(town.homes.get(s.homes[id]).inside());
        return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }
}
