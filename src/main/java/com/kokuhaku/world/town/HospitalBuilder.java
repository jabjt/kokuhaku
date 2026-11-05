package com.kokuhaku.world.town;

import com.kokuhaku.block.ModBlocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Chinetsu General, in the map's own materials: white plaster over andesite,
 * dark prismarine bands, glass panes, sea lanterns and birch floors. Three
 * floors of wards off one long corridor, a lobby at the front, and a cut
 * through the bank to the crossing on the main road.
 */
final class HospitalBuilder {
    /** The front wall, the back wall, the north wall, the south wall, and the middle of the corridor. */
    static final int WEST = -616, EAST = -570, NORTH = -224, SOUTH = -178, MIDDLE = -201;
    /** Floor blocks of each storey; people stand one above. */
    static final int[] FLOORS = {21, 27, 33};
    static final int ROOF = 39;
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int[] WARD_WALLS = {-600, -592, -584, -576};

    private static final BlockState AIR = Blocks.AIR.getDefaultState(),
            PLASTER = Blocks.WHITE_WOOL.getDefaultState(),
            TRIM = Blocks.DARK_PRISMARINE.getDefaultState(),
            PLINTH = Blocks.POLISHED_ANDESITE.getDefaultState(),
            LIGHT = Blocks.SEA_LANTERN.getDefaultState(),
            BIRCH = Blocks.BIRCH_PLANKS.getDefaultState(),
            TILE = Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState(),
            PAVING = Blocks.BRICKS.getDefaultState(),
            GLASS = Blocks.GLASS_PANE.getDefaultState(),
            CAP = Blocks.END_STONE_BRICKS.getDefaultState(),
            COUNTER = Blocks.QUARTZ_BLOCK.getDefaultState();

    private final ServerWorld world;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    HospitalBuilder(ServerWorld world) {
        this.world = world;
    }

    void build() {
        approach();
        shell();
        for (int floor = 0; floor < FLOORS.length; floor++) storey(floor);
        lobby();
        stairs();
        records();
        roof();
    }

    private void set(int x, int y, int z, BlockState state) {
        world.setBlockState(cursor.set(x, y, z), state, FLAGS);
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, state);
    }

    private static BlockState pane(boolean eastWest) {
        return GLASS.with(PaneBlock.EAST, eastWest).with(PaneBlock.WEST, eastWest).with(PaneBlock.NORTH, !eastWest).with(PaneBlock.SOUTH, !eastWest);
    }

    private static BlockState stair(Block block, Direction facing) {
        return block.getDefaultState().with(StairsBlock.FACING, facing);
    }

    private void door(int x, int y, int z, Direction facing) {
        BlockState lower = Blocks.BIRCH_DOOR.getDefaultState().with(DoorBlock.FACING, facing);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private void bed(BlockPos head, Direction facing) {
        BlockState state = Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING, facing);
        world.setBlockState(head, state.with(BedBlock.PART, BedPart.HEAD), FLAGS);
        world.setBlockState(head.offset(facing.getOpposite()), state.with(BedBlock.PART, BedPart.FOOT), FLAGS);
    }

    private void sign(int x, int y, int z, Direction facing, String... lines) {
        BlockPos pos = new BlockPos(x, y, z);
        world.setBlockState(pos, Blocks.BIRCH_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing), FLAGS);
        if (world.getBlockEntity(pos) instanceof SignBlockEntity board) {
            SignText text = new SignText();
            for (int i = 0; i < lines.length && i < 4; i++) text = text.withMessage(i, Text.literal(lines[i]));
            board.setText(text, true);
            board.setWaxed(true);
            board.markDirty();
        }
    }

    /** A gap in the wall and the hedge opposite the crossing, a cut through the bank, and paving up to the door. */
    private void approach() {
        int north = MIDDLE - 3, south = MIDDLE + 3;
        box(-637, 22, north, -618, 45, south, AIR);
        box(-637, 21, north, -618, 21, south, PAVING);
        box(-637, 20, north, -618, 20, south, Blocks.DIRT.getDefaultState());
        for (int z : new int[]{north - 1, south + 1}) {
            box(-637, 21, z, -625, 25, z, Blocks.STONE_BRICKS.getDefaultState());
            box(-637, 26, z, -625, 26, z, Blocks.STONE_BRICK_SLAB.getDefaultState());
            box(-637, 21, z, -637, 27, z, TRIM);
            set(-637, 28, z, LIGHT);
        }
        sign(-638, 24, north - 1, Direction.WEST, "Chinetsu", "General", "Hospital");
        // The forecourt, with room to pull up by the doors.
        box(-624, 21, MIDDLE - 12, -617, 21, MIDDLE + 12, PAVING);
        box(-624, 22, MIDDLE - 12, -617, 30, MIDDLE - 4, AIR);
        box(-624, 22, MIDDLE + 4, -617, 30, MIDDLE + 12, AIR);
        for (int z : new int[]{MIDDLE - 11, MIDDLE + 11}) {
            box(-623, 22, z - 1, -618, 22, z + 1, Blocks.POLISHED_ANDESITE_SLAB.getDefaultState());
            box(-622, 22, z, -619, 22, z, Blocks.GRASS_BLOCK.getDefaultState());
            for (int x = -622; x <= -619; x++) set(x, 23, z, x % 2 == 0 ? Blocks.AZURE_BLUET.getDefaultState() : Blocks.OXEYE_DAISY.getDefaultState());
        }
        for (int z : new int[]{MIDDLE - 7, MIDDLE + 7}) {
            set(-621, 22, z, Blocks.IRON_BARS.getDefaultState());
            box(-621, 23, z, -621, 25, z, Blocks.IRON_BARS.getDefaultState());
            set(-621, 26, z, LIGHT);
            set(-621, 27, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        }
    }

    private void shell() {
        // Footings, floors and the bands between storeys.
        box(WEST, 21, NORTH, EAST, 21, SOUTH, PLINTH);
        for (int y : new int[]{FLOORS[1], FLOORS[2], ROOF}) box(WEST, y, NORTH, EAST, y, SOUTH, TRIM);
        box(WEST + 1, 22, NORTH + 1, EAST - 1, ROOF - 1, SOUTH - 1, AIR);
        for (int y = 22; y < ROOF; y++) {
            if (y == FLOORS[1] || y == FLOORS[2]) continue;
            for (int x = WEST; x <= EAST; x++) {
                set(x, y, NORTH, y == 22 ? PLINTH : PLASTER);
                set(x, y, SOUTH, y == 22 ? PLINTH : PLASTER);
            }
            for (int z = NORTH; z <= SOUTH; z++) {
                set(WEST, y, z, y == 22 ? PLINTH : PLASTER);
                set(EAST, y, z, y == 22 ? PLINTH : PLASTER);
            }
        }
        // Windows in long runs between the pilasters, as on the school.
        for (int floor = 0; floor < FLOORS.length; floor++) {
            int low = FLOORS[floor] + 2, high = FLOORS[floor] + 4;
            for (int x = WEST + 2; x < EAST - 1; x++) {
                if ((x - WEST) % 8 == 0) continue;
                box(x, low, NORTH, x, high, NORTH, pane(true));
                box(x, low, SOUTH, x, high, SOUTH, pane(true));
            }
            for (int z = NORTH + 2; z < SOUTH - 1; z++) {
                if ((z - NORTH) % 8 == 0) continue;
                box(EAST, low, z, EAST, high, z, pane(false));
                if (Math.abs(z - MIDDLE) > 9 || floor > 0 && Math.abs(z - MIDDLE) > 3) box(WEST, low, z, WEST, high, z, pane(false));
            }
        }
        for (int x = WEST; x <= EAST; x += 8) {
            box(x, 22, NORTH - 1, x, ROOF, NORTH - 1, TRIM);
            box(x, 22, SOUTH + 1, x, ROOF, SOUTH + 1, TRIM);
        }
        for (int z = NORTH; z <= SOUTH; z += 8) {
            box(WEST - 1, 22, z, WEST - 1, ROOF, z, TRIM);
            box(EAST + 1, 22, z, EAST + 1, ROOF, z, TRIM);
        }
    }

    private void storey(int floor) {
        int base = FLOORS[floor], y = base + 1, top = base + 5;
        box(WEST + 1, base, NORTH + 1, EAST - 1, base, SOUTH - 1, BIRCH);
        box(WEST + 1, base, MIDDLE - 2, EAST - 1, base, MIDDLE + 2, TILE);
        // Lights down the corridor.
        for (int x = WEST + 4; x < EAST; x += 5) box(x, top, MIDDLE - 1, x, top, MIDDLE + 1, LIGHT);
        // Corridor walls.
        box(-600, y, MIDDLE - 3, -577, top, MIDDLE - 3, PLASTER);
        box(-600, y, MIDDLE + 3, -577, top, MIDDLE + 3, PLASTER);
        for (int x : WARD_WALLS) {
            box(x, y, NORTH + 1, x, top, MIDDLE - 3, PLASTER);
            box(x, y, MIDDLE + 3, x, top, SOUTH - 1, PLASTER);
        }
        // A hand rail along each side.
        BlockState rail = Blocks.BIRCH_TRAPDOOR.getDefaultState().with(TrapdoorBlock.OPEN, true);
        for (int x = -599; x <= -578; x++) {
            if ((x + 596) % 8 == 0) continue;
            set(x, y + 1, MIDDLE - 2, rail.with(TrapdoorBlock.FACING, Direction.SOUTH));
            set(x, y + 1, MIDDLE + 2, rail.with(TrapdoorBlock.FACING, Direction.NORTH));
        }
        int number = (floor + 1) * 100 + 1;
        for (int x = -596; x <= -580; x += 8) {
            ward(x, y, base, top, true, number + 1);
            ward(x, y, base, top, false, number);
            number += 2;
        }
    }

    /** One ward: door off the corridor, two beds under the window, a curtain between them. */
    private void ward(int x, int y, int base, int top, boolean north, int number) {
        Direction out = north ? Direction.NORTH : Direction.SOUTH;
        int wall = north ? MIDDLE - 3 : MIDDLE + 3, step = north ? -1 : 1, far = north ? NORTH + 1 : SOUTH - 1;
        door(x, y, wall, out.getOpposite());
        box(x, y + 2, wall, x, top, wall, PLASTER);
        sign(x + 1, y + 1, wall - step, out.getOpposite(), "", "Ward " + number);
        box(x - 2, top, wall + step * 8, x + 2, top, wall + step * 8, LIGHT);
        for (int side : new int[]{-2, 2}) {
            int bedX = x + side;
            bed(new BlockPos(bedX, y, far), out);
            set(bedX, y, far - step * 2, Blocks.WHITE_CARPET.getDefaultState());
            // The drip stand and the monitor over the head of the bed.
            set(bedX + (side > 0 ? 1 : -1), y, far, Blocks.IRON_BARS.getDefaultState());
            set(bedX + (side > 0 ? 1 : -1), y + 1, far, Blocks.IRON_BARS.getDefaultState());
            set(bedX + (side > 0 ? 1 : -1), y + 2, far, Blocks.LANTERN.getDefaultState());
            set(bedX, y + 1, far, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
            set(bedX, y + 2, far, Blocks.BLACK_STAINED_GLASS_PANE.getDefaultState());
        }
        // The curtain, and a locker by each bed.
        box(x, y, far, x, y + 3, far - step * 4, Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState().with(PaneBlock.NORTH, true).with(PaneBlock.SOUTH, true));
        set(x - 1, y, far - step * 3, Blocks.BIRCH_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP));
        set(x + 1, y, far - step * 3, Blocks.BIRCH_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP));
        set(x + 1, y + 1, far - step * 3, Blocks.POTTED_OXEYE_DAISY.getDefaultState());
        // A half wall splits the room into two bays, with two more beds in the front one.
        int bay = far - step * 7;
        for (int side : new int[]{-1, 1}) {
            box(x + side, y, bay, x + side * 3, y + 1, bay, PLASTER);
            box(x + side, y + 2, bay, x + side * 3, y + 2, bay, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
            int bedX = x + side * 2;
            bed(new BlockPos(bedX, y, bay - step), out);
            set(bedX, y + 1, bay, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
            set(x + side * 3, y, bay - step, Blocks.BIRCH_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP));
            set(x + side * 3, y + 1, bay - step, side < 0 ? Blocks.POTTED_FERN.getDefaultState() : Blocks.LANTERN.getDefaultState());
        }
        box(x - 2, top, bay - step * 2, x + 2, top, bay - step * 2, LIGHT);
        // Seats for visitors, and the sink by the door.
        set(x - 2, y, wall + step * 2, stair(Blocks.BIRCH_STAIRS, Direction.EAST));
        set(x + 2, y, wall + step * 1, Blocks.CAULDRON.getDefaultState());
    }

    private void lobby() {
        int y = 22, top = 26;
        // The front: a canopy on two pillars and a wall of glass.
        box(WEST, 23, MIDDLE - 9, WEST, 26, MIDDLE + 9, pane(false));
        box(WEST, 22, MIDDLE - 9, WEST, 22, MIDDLE + 9, PLINTH);
        box(WEST, 22, MIDDLE - 3, WEST, 25, MIDDLE + 3, AIR);
        box(WEST, 26, MIDDLE - 3, WEST, 26, MIDDLE + 3, TRIM);
        box(WEST - 8, 27, MIDDLE - 5, WEST - 1, 27, MIDDLE + 5, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        box(WEST - 8, 26, MIDDLE - 5, WEST - 1, 26, MIDDLE - 5, TRIM);
        box(WEST - 8, 26, MIDDLE + 5, WEST - 1, 26, MIDDLE + 5, TRIM);
        box(WEST - 8, 26, MIDDLE - 5, WEST - 8, 26, MIDDLE + 5, TRIM);
        for (int z : new int[]{MIDDLE - 5, MIDDLE + 5}) box(WEST - 8, 22, z, WEST - 8, 25, z, PLINTH);
        for (int x = WEST - 7; x < WEST; x += 3) set(x, 26, MIDDLE, LIGHT);
        // A red cross high on the front.
        for (int d = -2; d <= 2; d++) {
            set(WEST - 1, 33, MIDDLE + d, Blocks.RED_CONCRETE.getDefaultState());
            set(WEST - 1, 33 + d, MIDDLE, Blocks.RED_CONCRETE.getDefaultState());
        }
        box(WEST - 1, 29, MIDDLE - 4, WEST - 1, 37, MIDDLE - 4, TRIM);
        box(WEST - 1, 29, MIDDLE + 4, WEST - 1, 37, MIDDLE + 4, TRIM);

        // Lobby floor, lights, and the reception desk.
        box(WEST + 1, 21, NORTH + 1, -601, 21, SOUTH - 1, TILE);
        for (int z = NORTH + 3; z < SOUTH; z += 5) for (int x = WEST + 3; x < -601; x += 5) set(x, top, z, LIGHT);
        box(-606, y, MIDDLE - 8, -606, y, MIDDLE - 4, COUNTER);
        box(-606, y, MIDDLE + 4, -606, y, MIDDLE + 8, COUNTER);
        box(-606, y + 1, MIDDLE - 8, -606, y + 1, MIDDLE - 4, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
        box(-606, y + 1, MIDDLE + 4, -606, y + 1, MIDDLE + 8, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
        box(-605, y, MIDDLE - 8, -602, y, MIDDLE - 8, COUNTER);
        box(-605, y, MIDDLE + 8, -602, y, MIDDLE + 8, COUNTER);
        // Rows of seats facing the desk, and plants in the corners.
        for (int z = MIDDLE - 18; z <= MIDDLE - 12; z += 3) box(WEST + 3, y, z, WEST + 10, y, z, stair(Blocks.BIRCH_STAIRS, Direction.SOUTH));
        for (int z = MIDDLE + 12; z <= MIDDLE + 18; z += 3) box(WEST + 3, y, z, WEST + 10, y, z, stair(Blocks.BIRCH_STAIRS, Direction.NORTH));
        for (int[] c : new int[][]{{WEST + 1, NORTH + 1}, {WEST + 1, SOUTH - 1}, {-602, NORTH + 1}, {-602, SOUTH - 1}}) {
            set(c[0], y, c[1], Blocks.POTTED_FERN.getDefaultState());
        }
        set(WEST + 2, y, MIDDLE - 9, ModBlocks.VENDING_MACHINE.getDefaultState());
        sign(-607, 25, MIDDLE - 6, Direction.WEST, "Reception");
        sign(-607, 25, MIDDLE + 6, Direction.WEST, "Wards", "101 - 106", "Stairs at end");
        // The way through to the corridor stays open; the rest of that wall has notice boards.
        box(-600, 22, NORTH + 1, -600, 26, MIDDLE - 3, PLASTER);
        box(-600, 22, MIDDLE + 3, -600, 26, SOUTH - 1, PLASTER);
        box(-600, 22, MIDDLE - 2, -600, 26, MIDDLE + 2, AIR);
    }

    /** One straight flight per storey at the end of the corridor, with a lane to walk back along. */
    private void stairs() {
        int low = NORTH + 13, high = low + 5;
        for (int f = 0; f < FLOORS.length - 1; f++) {
            int y = FLOORS[f] + 1;
            box(-575, FLOORS[f + 1], low, -574, FLOORS[f + 1], high, AIR);
            for (int i = 0; i < 6; i++) {
                box(-575, y + i, high - i, -574, y + i, high - i, stair(Blocks.BIRCH_STAIRS, Direction.NORTH));
                if (i > 0) box(-575, y, high - i, -574, y + i - 1, high - i, PLASTER);
            }
            box(-575, y + 6, low - 1, -574, y + 6, low - 1, BIRCH);
        }
        for (int floor : FLOORS) {
            box(-573, floor + 1, MIDDLE - 3, -572, floor + 3, MIDDLE - 3, AIR);
            box(-573, floor + 1, low - 2, -573, floor + 2, high, AIR);
        }
    }

    /** Top floor, far end: where the old files go. */
    private void records() {
        int y = FLOORS[2] + 1, top = FLOORS[2] + 4;
        box(-583, y, NORTH + 1, -577, top, MIDDLE - 4, AIR);
        for (int x = -582; x <= -578; x += 2) box(x, y, NORTH + 2, x, top, MIDDLE - 7, Blocks.BOOKSHELF.getDefaultState());
        box(-581, y, MIDDLE - 5, -579, y, MIDDLE - 5, Blocks.BARREL.getDefaultState());
        door(-580, y, MIDDLE - 3, Direction.SOUTH);
        box(-580, y + 2, MIDDLE - 3, -580, y + 4, MIDDLE - 3, PLASTER);
        sign(-579, y + 1, MIDDLE - 2, Direction.SOUTH, "", "Records");
        set(-583, y + 4, MIDDLE - 10, LIGHT);
    }

    private void roof() {
        box(WEST, ROOF + 1, NORTH, EAST, ROOF + 1, NORTH, CAP);
        box(WEST, ROOF + 1, SOUTH, EAST, ROOF + 1, SOUTH, CAP);
        box(WEST, ROOF + 1, NORTH, WEST, ROOF + 1, SOUTH, CAP);
        box(EAST, ROOF + 1, NORTH, EAST, ROOF + 1, SOUTH, CAP);
        box(-594, ROOF + 1, MIDDLE - 7, -582, ROOF + 4, MIDDLE + 7, PLASTER);
        box(-595, ROOF + 5, MIDDLE - 8, -581, ROOF + 5, MIDDLE + 8, TRIM);
        for (int d = -2; d <= 2; d++) {
            set(-594 - 1, ROOF + 7, MIDDLE + d, Blocks.RED_CONCRETE.getDefaultState());
            set(-594 - 1, ROOF + 7 + d, MIDDLE, Blocks.RED_CONCRETE.getDefaultState());
        }
    }

    /** Where a page goes in the records room. */
    static BlockPos recordsShelf() {
        return new BlockPos(-579, FLOORS[2] + 1, MIDDLE - 6);
    }
}
