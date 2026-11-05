package com.kokuhaku.entity;

import com.kokuhaku.game.Roster;
import com.kokuhaku.game.RunManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** One of the 40 students (and the menu girl). Skin index = student id. */
public class StudentEntity extends PathAwareEntity {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SITTING = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> PEEK = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> FLUSTERED = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CHATTING = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SPEAKING = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> WAVING = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> MOOD = DataTracker.registerData(StudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** How they're carrying themselves. */
    public static final int AT_EASE = 0, AFRAID = 1, GRIEVING = 2;

    // Route state (server only; not saved).
    private List<Vec3d> route = List.of();
    private int routeIndex = 0;
    private String routeKey = "";
    private float arriveYaw = Float.NaN;
    private Vec3d chair = null;
    private int talkingTicks = 0;
    private ServerPlayerEntity catching = null;
    private boolean keptClose = false;
    /** Nobody will stand near you. Everybody knows what happens. */
    private static boolean shunning = false;
    private static int exempt = -1;
    private boolean staring = false;
    private int flusterTicks = 0;
    private static final double STARE_RANGE = 20;
    private static final float SHUN_DISTANCE = 4.5f;
    /** How often anyone checks they aren't in something, and how long before they do something about it. */
    private static final int CHECK_EVERY = 5, STUCK_FOR = 30, FURTHEST_ASIDE = 6;
    /** Path blocks, carpets and slabs: a body can end up a fraction inside one, and then nothing can walk it out. */
    private static final double LIFT_STEP = 0.0625, MOST_LIFT = 1.25, BODY_MARGIN = 0.02;
    private int embeddedFor = 0;
    /** How quickly anyone's head or body comes round to someone. */
    private static final float NECK_TURN = 11f, BODY_TURN = 7f;
    private static final double CHAT_GAP = 1.9, CHAT_WALK = 0.75, STARE_REACH = 5, STARED_AT = 0.97;
    private static final int SPEAK_MIN = 50, SPEAK_MORE = 90, STARE_CHECK = 10, STARE_TICKS = 50, STARE_REST = 300, LOOK_BACK = 60;
    /** How close is too close, how hard they step away, and when they see someone coming. */
    private static final double PERSONAL_SPACE = 1.05, STEP_AWAY = 0.09, SEES_COMING = 3.4, HEAD_ON = 0.75;
    private static final float PASS_ANGLE = 38f;
    private static final int SPACE_CHECK = 2, COMING_CHECK = 5;
    private float passing = 0f;
    private static final double WALKS_AWAY = 28;
    private static final int WALKS_AWAY_TICKS = 700;
    private int walkingAwayUntil = 0;
    private StudentEntity chatPartner = null;
    private int chatLeft = 0, speakFor = 0, stared = 0, waveTicks = 0, lookBack = 0;
    private PlayerEntity listeningTo = null, lookingBackAt = null;
    private boolean walkTalking = false;

    public StudentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        if (getNavigation() instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanEnterOpenDoors(true);
        }
        // Mobs refuse to step onto rails by default, and the walk to school crosses the railway.
        setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.UNPASSABLE_RAIL, 0f);
        // Up a kerb the way anyone goes up a kerb: in their stride, not with a hop.
        setStepHeight(1f);
        moveControl = new HumanMoveControl(this);
    }

    @Override
    protected net.minecraft.entity.ai.pathing.EntityNavigation createNavigation(World world) {
        return new RoadsideNavigation(this, world);
    }

    /** Her head stays turned to you, wherever she's going. If you're watching for it, it's the one thing that gives her away. */
    public void setStaring(boolean stare) {
        staring = stare;
    }

    public boolean isStaring() {
        return staring;
    }

    /** Where they've gone once the day's over, as opposed to anywhere else. */
    public boolean isHeadingToHomeOrClub() {
        return routeKey.startsWith("home") || routeKey.startsWith("club");
    }

    /** Everyone but her steps out of your way. */
    public static void setShunning(boolean on, int she) {
        shunning = on;
        exempt = she;
    }

    private boolean held = false;

    /** Stopped on the spot for now, route and all. */
    public void setHeld(boolean stop) {
        held = stop;
    }

    public boolean isHeld() {
        return held;
    }

    /** She goes where you go, so she doesn't wander off by herself. */
    public void setKeptClose(boolean close) {
        keptClose = close;
    }

    public boolean isKeptClose() {
        return keptClose;
    }

    public boolean isChatting() {
        return dataTracker.get(CHATTING);
    }

    public boolean isSpeaking() {
        return dataTracker.get(SPEAKING);
    }


    public boolean isWaving() {
        return dataTracker.get(WAVING);
    }

    public int getMood() {
        return dataTracker.get(MOOD);
    }

    public void setMood(int mood) {
        dataTracker.set(MOOD, mood);
    }


    public void wave(int ticks) {
        waveTicks = ticks;
        dataTracker.set(WAVING, true);
    }

    /** Talking on the walk to school, with their hands. */
    public void setWalkTalking(boolean talking) {
        walkTalking = talking;
        if (chatPartner == null) dataTracker.set(SPEAKING, talking);
    }

    /** On their way to a seat at lunch, which they walk right up to: there are no desks to squeeze between. */
    public boolean goingToLunch() {
        return routeKey.startsWith("lunch@");
    }

    /** Everyone has their own walk, and from the third week nobody dawdles. */
    public double pace() {
        int skin = getSkin();
        double own = 0.95 + (skin * 37 % 11) * 0.01;
        if (skin < Roster.TOTAL) {
            own *= switch (Roster.get(skin).personality()) {
                case SPORTY -> 1.1;
                case CHEERFUL -> 1.04;
                case SHY -> 0.93;
                case POLITE -> 0.96;
                default -> 1.0;
            };
        }
        return getMood() == AFRAID ? own * 1.06 : getMood() == GRIEVING ? own * 0.9 : own;
    }

    public void startChat(StudentEntity with, int ticks) {
        chatPartner = with;
        with.chatPartner = this;
        chatLeft = with.chatLeft = ticks;
        speakFor = SPEAK_MIN;
        with.speakFor = 0;
        dataTracker.set(SPEAKING, true);
        with.dataTracker.set(SPEAKING, false);
        dataTracker.set(CHATTING, true);
        with.dataTracker.set(CHATTING, true);
    }

    private void endChat() {
        StudentEntity other = chatPartner;
        chatPartner = null;
        dataTracker.set(SPEAKING, false);
        if (other != null && other.chatPartner == this) other.endChat();
    }

    /** Turns to them a little at a time: head first, the rest of them after. */
    private void turnTowards(net.minecraft.entity.Entity who) {
        getLookControl().lookAt(who, NECK_TURN, NECK_TURN);
        float wanted = (float) (Math.toDegrees(Math.atan2(who.getZ() - getZ(), who.getX() - getX())) - 90);
        float next = getYaw() + net.minecraft.util.math.MathHelper.clamp(net.minecraft.util.math.MathHelper.wrapDegrees(wanted - getYaw()), -BODY_TURN, BODY_TURN);
        setYaw(next);
        setBodyYaw(next);
    }

    private void tickChat() {
        StudentEntity other = chatPartner;
        if (--chatLeft <= 0 || other.isRemoved() || hasRoute() || other.hasRoute() || talkingTicks > 0 || isAiDisabled() || squaredDistanceTo(other) > 100) {
            endChat();
            return;
        }
        if (squaredDistanceTo(other) > CHAT_GAP * CHAT_GAP) {
            if (age % 10 == 0) getNavigation().startMovingTo(other, CHAT_WALK);
            return;
        }
        getNavigation().stop();
        turnTowards(other);
        // They take it in turns, more or less.
        if (--speakFor <= 0) {
            boolean me = !isSpeaking();
            dataTracker.set(SPEAKING, me);
            other.dataTracker.set(SPEAKING, !me);
            speakFor = other.speakFor = SPEAK_MIN + getRandom().nextInt(SPEAK_MORE);
        }
    }

    /** Stare at someone long enough and they notice. What they do about it depends who they are. */
    private void watchForStares() {
        PlayerEntity near = getWorld().getClosestPlayer(this, STARE_REACH);
        if (near == null || isTalking() || isSittingDown() || !isGirlOrBoy()) {
            stared = Math.min(stared, 0);
            return;
        }
        Vec3d to = getEyePos().subtract(near.getEyePos()).normalize();
        boolean atMe = near.getRotationVec(1f).dotProduct(to) > STARED_AT && canSee(near);
        stared = atMe ? stared + STARE_CHECK : Math.min(stared, 0);
        if (stared < STARE_TICKS) {
            if (stared < 0) stared += STARE_CHECK;
            return;
        }
        stared = -STARE_REST;
        switch (Roster.get(getSkin()).personality()) {
            case SHY -> fluster(LOOK_BACK);
            case CHEERFUL -> wave(LOOK_BACK / 2);
            default -> {
                lookBack = LOOK_BACK;
                lookingBackAt = near;
            }
        }
    }

    private boolean isGirlOrBoy() {
        return getSkin() < Roster.TOTAL;
    }

    private boolean inTheirSpace(net.minecraft.entity.Entity other) {
        if (other == this || other.hasVehicle() || other == chatPartner) return false;
        if (other instanceof StudentEntity s) return !s.isSittingDown();
        return other instanceof PlayerEntity p && !p.isSpectator();
    }

    /** Nobody stands in anyone. Two people who end up too close drift apart, the way people do. */
    private void keepTheirDistance() {
        if (noClip || isAiDisabled() || hasVehicle() || isSittingDown() || getPeek() != 0 || talkingTicks > 0) return;
        double awayX = 0, awayZ = 0;
        for (var other : getWorld().getOtherEntities(this, getBoundingBox().expand(PERSONAL_SPACE, 0.5, PERSONAL_SPACE), this::inTheirSpace)) {
            double dx = getX() - other.getX(), dz = getZ() - other.getZ(), gap = Math.sqrt(dx * dx + dz * dz);
            if (gap >= PERSONAL_SPACE) continue;
            if (gap < 0.01) {
                // Exactly on top of each other: pick a way, and not the same way.
                dx = Math.sin(getId());
                dz = Math.cos(getId());
                gap = 1;
            }
            double push = (PERSONAL_SPACE - gap) / PERSONAL_SPACE;
            awayX += dx / gap * push;
            awayZ += dz / gap * push;
        }
        if (awayX == 0 && awayZ == 0) return;
        double nextX = getX() + awayX, nextZ = getZ() + awayZ;
        if (com.kokuhaku.world.town.Town.isRoad(getWorld(), (int) Math.floor(nextX), (int) Math.floor(nextZ))) return;
        addVelocity(awayX * STEP_AWAY, 0, awayZ * STEP_AWAY);
    }

    /** Someone walking straight at them: they drift to the left, like everyone does here. */
    private void watchWhereTheyreGoing() {
        passing = 0f;
        if (noClip || isAiDisabled() || !getNavigation().isFollowingPath()) return;
        Vec3d facing = Vec3d.fromPolar(0f, getYaw());
        for (var other : getWorld().getOtherEntities(this, getBoundingBox().expand(SEES_COMING, 0.5, SEES_COMING), this::inTheirSpace)) {
            Vec3d to = other.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (to.length() < SEES_COMING && to.normalize().dotProduct(facing) > HEAD_ON) {
                passing = PASS_ANGLE;
                return;
            }
        }
    }

    /** Degrees off their path, for getting past someone. */
    public float passing() {
        return passing;
    }

    private void tickCompany() {
        if (listeningTo != null) {
            if (talkingTicks <= 0 || listeningTo.isRemoved()) listeningTo = null;
            else turnTowards(listeningTo);
        }
        if (chatPartner != null) tickChat();
        if (lookBack > 0 && --lookBack > 0 && lookingBackAt != null) getLookControl().lookAt(lookingBackAt, NECK_TURN, NECK_TURN);
        if (waveTicks > 0 && --waveTicks == 0) dataTracker.set(WAVING, false);

        boolean talking = listeningTo != null || walkTalking;
        dataTracker.set(CHATTING, talking || chatPartner != null);
        if (chatPartner == null) dataTracker.set(SPEAKING, talking);
        if (age % STARE_CHECK == 0) watchForStares();
        if (age % SPACE_CHECK == 0) keepTheirDistance();
        if (age % COMING_CHECK == 0) watchWhereTheyreGoing();
    }

    public static DefaultAttributeContainer.Builder createStudentAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(SKIN, 0);
        dataTracker.startTracking(SITTING, false);
        dataTracker.startTracking(PEEK, 0);
        dataTracker.startTracking(FLUSTERED, false);
        dataTracker.startTracking(CHATTING, false);
        dataTracker.startTracking(SPEAKING, false);

        dataTracker.startTracking(WAVING, false);
        dataTracker.startTracking(MOOD, AT_EASE);
    }

    /** Head down, looking anywhere but at you, for a moment. */
    public void fluster(int ticks) {
        flusterTicks = ticks;
        dataTracker.set(FLUSTERED, true);
    }

    public boolean isFlustered() {
        return dataTracker.get(FLUSTERED);
    }

    /** At their desk, in their chair. */
    public boolean isSittingDown() {
        return dataTracker.get(SITTING);
    }

    /** Running off, after peeking. */
    public static final int RUNNING = 2;
    /** Just a black shape, standing there. */
    public static final int SILHOUETTE = 3;
    /** You, on the floor; her, with your ankle; you, in the chair. */
    public static final int DRAGGED = 4, HAULING = 5, TIED = 6;

    /** Leaning out from a corner: 1 or -1 for the side, 0 if not. */
    public int getPeek() {
        return dataTracker.get(PEEK);
    }

    public void setPeek(int side) {
        dataTracker.set(PEEK, side);
    }

    public int getSkin() {
        return dataTracker.get(SKIN);
    }

    public void setSkin(int skin) {
        dataTracker.set(SKIN, skin);
    }

    public boolean isGirl() {
        return getSkin() < Roster.GIRLS;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Skin", getSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setSkin(nbt.getInt("Skin"));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new LongDoorInteractGoal(this, true));
        goalSelector.add(1, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, PlayerEntity.class, SHUN_DISTANCE, 1.1, 1.25) {
            @Override
            public boolean canStart() {
                return shunning && getSkin() != exempt && !isSittingDown() && !isAiDisabled() && !hasVehicle() && super.canStart();
            }
        });
        goalSelector.add(2, new RouteGoal(this));
        // Each other, not you: nobody follows you round the room with their head like a villager.
        goalSelector.add(4, new LookAtEntityGoal(this, StudentEntity.class, 5f, 0.4f) {
            @Override
            public boolean canStart() {
                return !isChatting() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !isChatting() && super.shouldContinue();
            }
        });
        goalSelector.add(5, new WanderAroundGoal(this, 0.5, 300) {
            @Override
            public boolean canStart() {
                // Indoors only: outside, nobody just wanders off the pavement.
                return !hasRoute() && !isSittingDown() && !isCatchingUp() && !keptClose && !isChatting() && !getWorld().isSkyVisible(getBlockPos()) && super.canStart();
            }
        });
        goalSelector.add(6, new LookAroundGoal(this) {
            @Override
            public boolean canStart() {
                return !isChatting() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !isChatting() && super.shouldContinue();
            }
        });
    }

    // ---------------------------------------------------------------- route

    public boolean isHeadingTo(String key) {
        return key.equals(routeKey);
    }

    /** Replaces the route. A chair (or NaN yaw for none) is where they settle on arrival. */
    public void setRoute(String key, List<Vec3d> points, Vec3d chairAt, float yawOnArrival) {
        routeKey = key;
        route = points;
        routeIndex = 0;
        laidUntil = 0;
        chair = chairAt;
        arriveYaw = yawOnArrival;
        dataTracker.set(SITTING, false);
        if (!getWorld().isClient) com.kokuhaku.game.Paths.ask(this);
    }

    /** How far along the route it's been laid out on the ground. Past that it's only waypoints. */
    private int laidUntil = 0;

    public List<Vec3d> routeLeft() {
        return List.copyOf(route.subList(Math.min(routeIndex, route.size()), route.size()));
    }

    public void laidOut(List<Vec3d> points, int laid) {
        route = points;
        routeIndex = 0;
        laidUntil = laid;
    }

    public boolean onLaidRoute() {
        return routeIndex < laidUntil;
    }

    /** Sits straight down on a chair that's right there. */
    public void sitOn(BlockPos seat, float yaw) {
        getNavigation().stop();
        refreshPositionAndAngles(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5, yaw, 0f);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        dataTracker.set(SITTING, true);
    }

    /** Sat down right where they are. */
    public void sit() {
        dataTracker.set(SITTING, true);
    }

    public void standUp() {
        dataTracker.set(SITTING, false);
    }

    /** Nobody wants to be near this. They get up and go, and don't look back. */
    public void walkAwayFrom(Vec3d spot) {
        if (noClip || isAiDisabled() || hasVehicle()) return;
        Vec3d way = getPos().subtract(spot).multiply(1, 0, 1);
        if (way.lengthSquared() < 0.01) way = new Vec3d(Math.sin(getId()), 0, Math.cos(getId()));
        Vec3d far = getPos().add(way.normalize().multiply(WALKS_AWAY));
        talkingTicks = 0;
        listeningTo = null;
        if (chatPartner != null) endChat();
        chair = null;
        walkingAwayUntil = age + WALKS_AWAY_TICKS;
        setRoute("leaving", List.of(far), null, Float.NaN);
    }

    public boolean isWalkingAway() {
        return age < walkingAwayUntil;
    }

    /** Up and out of the chair, to the aisle. */
    public void sentAway(Vec3d aisle) {
        chair = null;
        setRoute("moved", List.of(aisle), null, Float.NaN);
    }

    /** Puts them straight at their destination (used overnight). */
    public void placeAt(String key, Vec3d pos) {
        routeKey = key;
        route = List.of();
        routeIndex = 0;
        catching = null;
        dataTracker.set(SITTING, false);
        getNavigation().stop();
        refreshPositionAndAngles(pos.x, pos.y, pos.z, getYaw(), 0f);
    }

    /** Where they were going is no good from here. The next timetable check works out a fresh way. */
    public void replan() {
        routeKey = "";
        route = List.of();
        routeIndex = 0;
        laidUntil = 0;
    }

    private static Vec3d keepOut = null;

    /** Everybody finds a reason to stay away from here, for now. */
    public static void setKeepOut(Vec3d at) {
        keepOut = at;
    }

    public static Vec3d keepOut() {
        return keepOut;
    }

    /** Heading for a chair, which is reached by squeezing between desks. */
    public boolean headedForChair() {
        return chair != null;
    }

    public boolean hasRoute() {
        return routeIndex < route.size();
    }

    public boolean onLastWaypoint() {
        return routeIndex == route.size() - 1;
    }

    public int waypointsLeft() {
        return Math.max(0, route.size() - routeIndex);
    }

    public Vec3d waypoint() {
        return hasRoute() ? route.get(routeIndex) : null;
    }

    /** The one they've just come from, if any. */
    public Vec3d waypointBefore() {
        return routeIndex > 0 && routeIndex <= route.size() ? route.get(routeIndex - 1) : null;
    }

    public Vec3d waypointAhead(int steps) {
        return route.get(Math.min(route.size() - 1, routeIndex + steps));
    }

    public void advanceRoute() {
        routeIndex++;
        if (!hasRoute()) {
            getNavigation().stop();
            if (chair != null) {
                refreshPositionAndAngles(chair.x, chair.y + 0.5, chair.z, arriveYaw, 0f);
                setBodyYaw(arriveYaw);
                setHeadYaw(arriveYaw);
                dataTracker.set(SITTING, true);
                // The legs of it, on the floor.
                getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_WOOD_HIT, net.minecraft.sound.SoundCategory.NEUTRAL, 0.25f, 0.7f);
            }
        }
    }

    // ------------------------------------------------------------- friends

    /** Run over to the player ("Wait up!"), then say something. */
    public void catchUpWith(ServerPlayerEntity player) {
        catching = player;
    }

    public boolean isCatchingUp() {
        return catching != null;
    }

    public void tickCatchUp(double speed) {
        if (catching.isRemoved() || squaredDistanceTo(catching) > 60 * 60) {
            catching = null;
        } else if (squaredDistanceTo(catching) < 3.5 * 3.5) {
            getNavigation().stop();
            talkingTicks = 20 * 10;
            RunManager.friendArrived(catching, this);
            catching = null;
        } else {
            getNavigation().startMovingTo(catching, speed);
        }
    }

    public boolean isTalking() {
        return talkingTicks > 0;
    }

    /** Sitting is sitting: nothing moves them, and nothing turns them. */
    private Vec3d seatedAt = null;
    private float seatedYaw = 0f;

    @Override
    protected float turnHead(float bodyRotation, float headRotation) {
        if (!isSittingDown()) return super.turnHead(bodyRotation, headRotation);
        setBodyYaw(getYaw());
        return headRotation;
    }

    @Override
    public void travel(Vec3d movement) {
        if (isSittingDown()) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        super.travel(movement);
    }

    @Override
    public void pushAwayFrom(net.minecraft.entity.Entity entity) {
        if (!isSittingDown()) super.pushAwayFrom(entity);
    }

    private void stayInTheChair() {
        if (!isSittingDown()) {
            seatedAt = null;
            return;
        }
        if (seatedAt == null) {
            seatedAt = getPos();
            seatedYaw = getYaw();
        }
        setVelocity(Vec3d.ZERO);
        if (!getPos().equals(seatedAt)) setPosition(seatedAt);
        setBodyYaw(seatedYaw);
        setYaw(seatedYaw);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) stayInTheChair();
        if (talkingTicks > 0) talkingTicks--;
        if (flusterTicks > 0 && --flusterTicks == 0) dataTracker.set(FLUSTERED, false);
        if (!getWorld().isClient) tickCompany();
        if (!getWorld().isClient && age % CHECK_EVERY == 0) checkFree();
        if (staring && !getWorld().isClient) {
            PlayerEntity near = getWorld().getClosestPlayer(this, STARE_RANGE);
            if (near != null) getLookControl().lookAt(near, NECK_TURN, NECK_TURN);
        }
    }

    /** Whether a block is where their body is. */
    private boolean embedded(double up) {
        return !getWorld().isSpaceEmpty(this, getBoundingBox().contract(BODY_MARGIN).offset(0, up, 0));
    }

    private void checkFree() {
        if (noClip || hasVehicle() || isSittingDown() || isAiDisabled()) {
            embeddedFor = 0;
            return;
        }
        embeddedFor = embedded(0) ? embeddedFor + CHECK_EVERY : 0;
        if (embeddedFor >= STUCK_FOR) settle();
        // However they got up there, nobody stays stood on a table.
        if (isOnGround() && com.kokuhaku.world.town.Furniture.at(getWorld(), getBlockPos().down())) stepAside(getPos());
    }

    /** Up out of whatever they've ended up in, or to the nearest clear ground if that won't do it. */
    public void settle() {
        embeddedFor = 0;
        if (!embedded(0)) return;
        for (double up = LIFT_STEP; up <= MOST_LIFT; up += LIFT_STEP) {
            if (embedded(up)) continue;
            getNavigation().stop();
            setPosition(getX(), getY() + up, getZ());
            replan();
            return;
        }
        stepAside(getPos());
    }

    /** They can't get anywhere from here: onto the nearest clear ground, on the side they were going. */
    public void stepAside(Vec3d towards) {
        if (!(getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
        BlockPos at = getBlockPos(), best = null;
        double closest = Double.MAX_VALUE;
        for (int ring = 1; ring <= FURTHEST_ASIDE && best == null; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    for (int dy = -1; dy <= 3; dy++) {
                        BlockPos spot = at.add(dx, dy, dz);
                        if (!RunManager.standable(world, spot) || com.kokuhaku.world.town.Town.isRoad(world, spot.getX(), spot.getZ())) continue;
                        double gap = spot.getSquaredDistance(towards);
                        if (gap < closest) {
                            closest = gap;
                            best = spot;
                        }
                    }
                }
            }
        }
        if (best == null) return;
        getNavigation().stop();
        refreshPositionAndAngles(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, getYaw(), 0f);
        replan();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (player instanceof ServerPlayerEntity sp && RunManager.isRun(sp.server)) {
            talkingTicks = 20 * 15;
            listeningTo = sp;
            getNavigation().stop();
            RunManager.talk(sp, this);
        }
        return ActionResult.success(getWorld().isClient);
    }
}
