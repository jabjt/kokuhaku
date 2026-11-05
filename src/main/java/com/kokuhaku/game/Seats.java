package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Right-click an anvil or a school chair to sit down, sneak to get up. You sit
 * on an invisible marker, which goes away when you do.
 */
public final class Seats {
    /** A rider sits this far below whatever they ride, and their hips are this far above their feet. */
    private static final double RIDER_DROP = 0.35, HIPS = 0.75;
    /** How far along a row to look for the chair next to yours: every other block has one. */
    private static final int ROW_REACH = 4;
    private static final List<ArmorStandEntity> taken = new ArrayList<>();
    /** Which chair each person is on. */
    private static final Map<UUID, BlockPos> chairs = new HashMap<>();

    private Seats() {
    }

    public static boolean isSeat(BlockState state) {
        return state.getBlock() instanceof AnvilBlock || state.isOf(Blocks.PURPUR_STAIRS);
    }

    public static void sit(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (player.hasVehicle()) return;
        ServerWorld world = player.getServerWorld();
        // Getting up puts you back where you stood to sit down: always this side of the wall, and on the floor.
        net.minecraft.util.math.Vec3d stoodAt = player.getPos();
        ArmorStandEntity seat = new ArmorStandEntity(world, stoodAt.x, stoodAt.y, stoodAt.z) {
            @Override
            public net.minecraft.util.math.Vec3d updatePassengerForDismount(net.minecraft.entity.LivingEntity passenger) {
                Box room = passenger.getDimensions(net.minecraft.entity.EntityPose.STANDING).getBoxAt(stoodAt);
                return getWorld().isSpaceEmpty(passenger, room) ? stoodAt : super.updatePassengerForDismount(passenger);
            }
        };
        NbtCompound marker = new NbtCompound();
        marker.putBoolean("Marker", true);
        marker.putBoolean("Invisible", true);
        marker.putBoolean("NoGravity", true);
        seat.readCustomDataFromNbt(marker);
        boolean stairs = state.getBlock() instanceof StairsBlock;
        float yaw = stairs ? state.get(StairsBlock.FACING).getOpposite().asRotation() : player.getYaw();
        // Your hips go on the seat, so your head comes down with them.
        seat.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + (stairs ? 0.5 : 1.0) - HIPS + RIDER_DROP, pos.getZ() + 0.5, yaw, 0f);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        world.spawnEntity(seat);
        player.startRiding(seat, true);
        taken.add(seat);
        chairs.put(player.getUuid(), pos.toImmutable());
    }

    /** The chair she's taken. Nobody's timetable sends them to it. */
    private static BlockPos claimed = null;

    public static void claim(BlockPos chair) {
        claimed = chair == null ? null : chair.toImmutable();
    }

    public static boolean isClaimed(BlockPos chair) {
        return chair.equals(claimed);
    }

    /** Whoever's sitting on a chair, if anyone is. */
    public static StudentEntity occupant(ServerWorld world, BlockPos chair) {
        return world.getEntitiesByClass(StudentEntity.class, new Box(chair).expand(0.1, 1, 0.1), StudentEntity::isSittingDown).stream().findFirst().orElse(null);
    }

    /** The closest chair in your row, either side of you, whoever's in it. Another player's isn't up for grabs. */
    public static BlockPos besideYou(ServerPlayerEntity player) {
        BlockPos chair = chairs.get(player.getUuid());
        ServerWorld world = player.getServerWorld();
        if (chair == null || !player.hasVehicle()) return null;
        BlockState state = world.getBlockState(chair);
        Direction along = state.getBlock() instanceof StairsBlock ? state.get(StairsBlock.FACING).rotateYClockwise() : player.getHorizontalFacing().rotateYClockwise();
        for (int reach = 1; reach <= ROW_REACH; reach++) {
            for (int side : new int[]{reach, -reach}) {
                BlockPos at = chair.offset(along, side);
                if (isSeat(world.getBlockState(at)) && !chairs.containsValue(at)) return at;
            }
        }
        return null;
    }


    /** Which way someone faces on this chair. */
    public static float facing(BlockState state, float otherwise) {
        return state.getBlock() instanceof StairsBlock ? state.get(StairsBlock.FACING).getOpposite().asRotation() : otherwise;
    }

    public static void tick() {
        chairs.keySet().removeIf(id -> taken.stream().noneMatch(seat -> seat.getPassengerList().stream().anyMatch(rider -> rider.getUuid().equals(id))));
        taken.removeIf(seat -> {
            if (seat.hasPassengers()) return false;
            seat.discard();
            return true;
        });
    }

    public static void clear() {
        taken.clear();
        chairs.clear();
        claimed = null;
    }
}
