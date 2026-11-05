package com.kokuhaku.entity;

import com.kokuhaku.world.town.Furniture;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Pathfinding for people who look both ways: the road surface is a wall to them, except at the crossings. */
public class RoadsideNavigation extends MobNavigation {
    /** The road surface, and the kerb stairs you stand a little lower on. */
    private static final int KERB_LEVEL = 21, STREET_LEVEL = 22;
    /** The railway runs in a sunken bed between these: people cross on the walkways, and don't climb down. */
    private static final int LINE_WEST = -723, LINE_EAST = -705;

    public RoadsideNavigation(MobEntity mob, World world) {
        super(mob, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = new LandPathNodeMaker() {
            @Override
            public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
                // Anyone who's somehow out in the road is allowed to walk off it.
                boolean inTheRoad = Town.isRoad(world, entity.getBlockX(), entity.getBlockZ());
                if (!inTheRoad && (y == STREET_LEVEL || y == KERB_LEVEL) && Town.isRoad(world, x, z)) return PathNodeType.BLOCKED;
                // The same goes for the railway bed: to someone standing in it, it can't be a wall, or they'd never leave.
                boolean inTheBed = entity.getBlockY() <= KERB_LEVEL && entity.getBlockX() >= LINE_WEST && entity.getBlockX() <= LINE_EAST;
                if (!inTheBed && y <= KERB_LEVEL && x >= LINE_WEST && x <= LINE_EAST) return PathNodeType.BLOCKED;
                BlockPos here = new BlockPos(x, y, z);
                // Bars and glass are walls, whatever shape they are, and nobody walks along the top of one.
                if (world.getBlockState(here).getBlock() instanceof PaneBlock || world.getBlockState(here.down()).getBlock() instanceof PaneBlock) return PathNodeType.BLOCKED;
                PathNodeType type = super.getDefaultNodeType(world, x, y, z);
                if (type != PathNodeType.WALKABLE) return type;
                // Nobody stands on the furniture.
                if (entity != null && !entity.getWorld().isClient && Furniture.at(entity.getWorld(), here.down())) return PathNodeType.BLOCKED;
                // Walkable, for anyone actually going there, but never a short cut.
                BlockState under = world.getBlockState(new BlockPos(x, y - 1, z));
                if (under.isOf(Blocks.GRASS_BLOCK) || under.isOf(Blocks.DIRT) || under.isOf(Blocks.COARSE_DIRT) || under.isOf(Blocks.GRAVEL) || under.isOf(Blocks.SAND)) return PathNodeType.DANGER_OTHER;
                return type;
            }
        };
        nodeMaker.setCanEnterOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, range);
    }
}
