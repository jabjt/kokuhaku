package com.kokuhaku.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** The vending machine: a model two blocks tall, with a matching hitbox so all of it can be clicked. */
public class ThinCollisionBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 32, 15);

    public ThinCollisionBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
