package com.kokuhaku.client;

import com.kokuhaku.world.town.Furniture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


/**
 * There's no jumping, and this map's kerbs and doorsteps are a full block high,
 * so you have to be able to step up them or you'd never leave your own house.
 * What you can't do is climb onto furniture, bushes and the like: walking
 * towards one of those, the step drops back to a normal half block.
 * The map was checked with this list: every home still reaches the school.
 */
public final class StepRule {
    /** Low enough that a chair's seat or a desk's edge is a wall, not a step. */
    private static final float KERB = 1f, NORMAL = 0.4f;
    private static final double REACH = 0.6;
    private StepRule() {
    }

    private static boolean furnitureAt(ClientPlayerEntity player, Vec3d at) {
        BlockPos pos = BlockPos.ofFloored(at.x, player.getY() + 0.01, at.z);
        return Furniture.at(player.getWorld(), pos);
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.input == null) return;
        float forward = player.input.movementForward, sideways = player.input.movementSideways;
        if (forward == 0f && sideways == 0f) return;
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double x = sideways * MathHelper.cos(yaw) - forward * MathHelper.sin(yaw);
        double z = forward * MathHelper.cos(yaw) + sideways * MathHelper.sin(yaw);
        double length = Math.sqrt(x * x + z * z);
        x = x / length * REACH;
        z = z / length * REACH;
        Vec3d at = player.getPos();
        boolean blocked = furnitureAt(player, at.add(x, 0, z)) || furnitureAt(player, at.add(x, 0, 0)) || furnitureAt(player, at.add(0, 0, z));
        player.setStepHeight(blocked ? NORMAL : KERB);
    }
}
