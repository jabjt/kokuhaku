package com.kokuhaku.world;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The bedroom behind the main menu. Built from code into the world every time
 * the game loads, high above the town so nothing outside leaks in.
 *
 * Interior: x -4..4, y 100..103, z -6..4. The player is invisible and placed
 * so the third-person camera (4 blocks behind them) sits about 3 blocks in
 * front of her, framing her on the left and the candle-lit desk on the right.
 */
public final class MenuRoom {
    private static final int FLOOR = 99, CEILING = 104;
    private static final int MIN_X = -5, MAX_X = 5, MIN_Z = -7, MAX_Z = 5;

    // Camera ends up at about (2.0, 101.6, 0.4) looking at (2.8, -2.5): between her and the candle.
    public static final double PLAYER_X = 3.06, PLAYER_Y = 100, PLAYER_Z = -3.46;
    public static final float PLAYER_YAW = 195.4f;
    public static final double GIRL_X = 2.0, GIRL_Y = 100, GIRL_Z = -2.5;
    /** The chair you wake up in, and the hall you get there by. */
    public static final double SEAT_X = 2.5, SEAT_Z = 0.5;
    public static final net.minecraft.util.math.BlockPos CHAIR_POS = new net.minecraft.util.math.BlockPos(2, 100, 0);
    private static final int HALL_FAR = -46, HALL_LIT_FROM = -11, LANTERN_EVERY = 7, HALL_TOP = 103;
    public static final net.minecraft.util.math.Vec3d HALL_START = new net.minecraft.util.math.Vec3d(-32.5, 100.06, 1.5), HALL_END = new net.minecraft.util.math.Vec3d(-12.5, 100.06, 1.5);

    private MenuRoom() {
    }

    /** How many times she heaves on the way down the hall. */
    public static final int HEAVES = 12;
    private static final double SURGE = 0.9;

    /** How far along you've got by this point of the drag: nearly nothing between heaves, then a lurch. */
    public static double pulled(double p) {
        double around = 2 * Math.PI * HEAVES;
        return Math.min(1.0, p + SURGE / around * (1 - Math.cos(around * p)));
    }

    public static BlockState chair() {
        return Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH);
    }

    public static void build(ServerWorld world) {
        BlockState wall = Blocks.WHITE_TERRACOTTA.getDefaultState();
        BlockState beam = Blocks.DARK_OAK_LOG.getDefaultState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState ceiling = Blocks.SPRUCE_PLANKS.getDefaultState();

        // Shell.
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                for (int y = FLOOR; y <= CEILING; y++) {
                    boolean edge = x == MIN_X || x == MAX_X || z == MIN_Z || z == MAX_Z;
                    BlockState state;
                    if (y == FLOOR) state = floor;
                    else if (y == CEILING) state = ceiling;
                    else if (edge) state = isBeam(x, z) || y == CEILING - 1 ? beam : wall;
                    else state = Blocks.AIR.getDefaultState();
                    set(world, x, y, z, state);
                }
            }
        }

        // Window on the back wall, behind her and to the left: the moon is the only cold light.
        for (int x = -3; x <= -1; x++) {
            for (int y = 101; y <= 102; y++) {
                set(world, x, y, MIN_Z, Blocks.GLASS.getDefaultState());
            }
        }

        // Bed in the back-left corner, head against the back wall.
        BlockState bed = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH);
        set(world, -4, 100, -6, bed.with(BedBlock.PART, BedPart.HEAD));
        set(world, -4, 100, -5, bed.with(BedBlock.PART, BedPart.FOOT));
        set(world, -3, 100, -6, bed.with(BedBlock.PART, BedPart.HEAD));
        set(world, -3, 100, -5, bed.with(BedBlock.PART, BedPart.FOOT));

        // Desk on the right wall with two candles: the only warm light.
        BlockState desk = Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        set(world, 4, 100, -3, desk);
        set(world, 4, 100, -2, desk);
        set(world, 4, 101, -3, Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 2).with(CandleBlock.LIT, true));
        set(world, 3, 100, -2, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
        set(world, 4, 100, 0, Blocks.BOOKSHELF.getDefaultState());
        set(world, 4, 101, 0, Blocks.BOOKSHELF.getDefaultState());
        set(world, 4, 100, 1, Blocks.BOOKSHELF.getDefaultState());

        // Rug where she stands.
        for (int x = 0; x <= 2; x++) {
            for (int z = -4; z <= -1; z++) {
                set(world, x, 100, z, (x == 1 && z > -4 && z < -1 ? Blocks.RED_CARPET : Blocks.BLACK_CARPET).getDefaultState());
            }
        }

        // Door on the left wall, left open onto a pitch-black hallway.
        for (int x = HALL_FAR; x <= MIN_X; x++) {
            for (int z = 0; z <= 3; z++) {
                for (int y = FLOOR; y <= HALL_TOP; y++) {
                    if (x == MIN_X) continue; // room wall handled below
                    boolean near = x > HALL_LIT_FROM;
                    boolean side = z == 0 || z == 3, end = x == HALL_FAR;
                    BlockState state = Blocks.AIR.getDefaultState();
                    if (y == FLOOR) state = near ? Blocks.BLACK_CONCRETE.getDefaultState() : floor;
                    else if (y == HALL_TOP) state = near ? Blocks.BLACK_CONCRETE.getDefaultState() : ceiling;
                    else if (side || end) state = near ? Blocks.BLACK_CONCRETE.getDefaultState() : x % LANTERN_EVERY == 0 || y == HALL_TOP - 1 ? beam : wall;
                    set(world, x, y, z, state);
                }
                if (x < HALL_LIT_FROM && z > 0 && z < 3) set(world, x, 100, z, Blocks.RED_CARPET.getDefaultState());
            }
            if (x < HALL_LIT_FROM && x % LANTERN_EVERY == 0) set(world, x, HALL_TOP - 1, 1, Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true));
        }
        // The chair, right where you'll be.
        set(world, CHAIR_POS.getX(), CHAIR_POS.getY(), CHAIR_POS.getZ(), chair());
        BlockState door = Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.EAST).with(DoorBlock.OPEN, true);
        set(world, MIN_X, 100, 2, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(world, MIN_X, 101, 2, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        world.setTimeOfDay(18000); // midnight
        world.setSpawnPos(BlockPos.ofFloored(PLAYER_X, PLAYER_Y, PLAYER_Z), PLAYER_YAW);

        StudentEntity girl = Kokuhaku.STUDENT.create(world);
        if (girl != null) {
            girl.refreshPositionAndAngles(GIRL_X, GIRL_Y, GIRL_Z, 0f, 0f);
            girl.setHeadYaw(0f);
            girl.setBodyYaw(0f);
            girl.setAiDisabled(true);
            girl.setInvulnerable(true);
            girl.setSilent(true);
            world.spawnEntity(girl);
        }
    }

    public static void placePlayer(ServerPlayerEntity player) {
        player.networkHandler.requestTeleport(PLAYER_X, PLAYER_Y, PLAYER_Z, PLAYER_YAW, 0f);
        player.setInvulnerable(true);
        hide(player);
    }

    /** Only the camera is "you" here: the body isn't drawn. */
    public static void hide(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false));
    }

    private static boolean isBeam(int x, int z) {
        boolean corner = (x == MIN_X || x == MAX_X) && (z == MIN_Z || z == MAX_Z);
        boolean sidePost = (x == MIN_X || x == MAX_X) && (z == -4 || z == -1 || z == 3);
        boolean backPost = (z == MIN_Z || z == MAX_Z) && (x == -4 || x == 0 || x == 3);
        return corner || sidePost || backPost;
    }

    private static void set(ServerWorld world, int x, int y, int z, BlockState state) {
        world.setBlockState(new BlockPos(x, y, z), state, 2);
    }
}
