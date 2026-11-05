package com.kokuhaku.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.Direction;

/** A numbered shoe locker. Each number has its own texture on the front. */
public class LockerBlock extends HorizontalFacingBlock {
    public static final IntProperty NUMBER = IntProperty.of("number", 1, 42);

    public LockerBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(NUMBER, 1));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, NUMBER);
    }
}
