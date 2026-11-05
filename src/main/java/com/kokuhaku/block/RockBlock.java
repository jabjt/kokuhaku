package com.kokuhaku.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** A low rock with a smaller one against its side. It hangs half a block out of its own space, so all of it can be clicked. */
public class RockBlock extends Block {
    private static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 16, 16), Block.createCuboidShape(16, 0, 2, 24, 8, 14));

    public RockBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}