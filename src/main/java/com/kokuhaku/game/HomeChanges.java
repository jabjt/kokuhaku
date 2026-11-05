package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * You come home and something isn't how you left it. Never much: a chair turned
 * to face the bed, the radio moved along the desk, a door you shut standing
 * open, a flower. Nothing's ever explained, and you start checking.
 */
final class HomeChanges {
    private static final int FROM_DAY = 3, ONE_IN = 3, REACH = 8, HEIGHT = 2, SAYS_SO_TIMES = 2, TELL_AFTER = 70;
    private static final Random RANDOM = new Random();
    private static final Set<UUID> out = new HashSet<>();
    private static int lastDay = 0, told = 0;

    private HomeChanges() {
    }

    static void clear() {
        out.clear();
        lastDay = 0;
        told = 0;
    }

    static void minute(ServerPlayerEntity p, RunState s) {
        boolean home = RunManager.isHomeNow(p, s);
        if (!home) {
            out.add(p.getUuid());
            return;
        }
        if (!out.remove(p.getUuid()) || s.day < FROM_DAY || lastDay == s.day || Kept.on(s) || Living.active(s) || RANDOM.nextInt(ONE_IN) != 0) return;
        Town.Home room = Radio.homeOf(p);
        if (room == null) return;
        lastDay = s.day;
        ServerWorld world = p.getServerWorld();
        List<Runnable> could = new ArrayList<>();
        for (BlockPos at : BlockPos.iterateOutwards(room.bedHead(), REACH, HEIGHT, REACH)) {
            if (!room.containsBlock(at)) continue;
            BlockState state = world.getBlockState(at);
            BlockPos here = at.toImmutable();
            if (state.getBlock() instanceof StairsBlock && could.stream().noneMatch(r -> r instanceof Chair)) could.add(new Chair(world, here, room.bedHead()));
            if (state.getBlock() instanceof DoorBlock door && state.get(Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER && !door.isOpen(state)) could.add(() -> door.setOpen(null, world, state, here, true));
        }
        BlockPos desk = Radio.at(p);
        if (desk != null) {
            for (Direction side : Direction.Type.HORIZONTAL) {
                BlockPos beside = desk.offset(side);
                if (world.isAir(beside) && !world.isAir(beside.down())) {
                    could.add(() -> LunchHall.loose(world, Vec3d.ofBottomCenter(beside).add(0, 0.03, 0), "minecraft:white_tulip", 0.4f));
                    could.add(() -> Radio.shift(p, beside));
                    break;
                }
            }
        }
        if (could.isEmpty()) return;
        could.get(RANDOM.nextInt(could.size())).run();
        Kokuhaku.LOG.info("Something's been moved at home on day {}", s.day);
        Shadow.saw(s, "She let herself in while you were out. She always puts things back. Nearly.");
        if (told++ < SAYS_SO_TIMES) RunManager.later(TELL_AFTER, () -> RunManager.tell(p, "", "Something in here isn't how you left it."));
    }

    /** Round to face the bed. */
    private record Chair(ServerWorld world, BlockPos at, BlockPos bed) implements Runnable {
        @Override
        public void run() {
            Direction to = Direction.getFacing(bed.getX() - at.getX(), 0, bed.getZ() - at.getZ());
            world.setBlockState(at, world.getBlockState(at).with(StairsBlock.FACING, to.getOpposite()));
        }
    }
}
