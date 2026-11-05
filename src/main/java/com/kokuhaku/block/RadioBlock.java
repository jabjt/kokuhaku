package com.kokuhaku.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** A small old radio, sitting on the floor by the bed. */
public class RadioBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 5, 13, 7, 11);

    public RadioBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
