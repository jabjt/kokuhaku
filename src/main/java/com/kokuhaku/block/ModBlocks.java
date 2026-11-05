package com.kokuhaku.block;

import com.kokuhaku.Kokuhaku;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Custom blocks: numbered shoe lockers, vending machines, job boards, and a body for the crime scenes. */
public final class ModBlocks {
    public static final Block LOCKER = register("locker", new LockerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block VENDING_MACHINE = register("vending_machine", new ThinCollisionBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque().luminance(s -> 7)));
    public static final Block JOB_BOARD = register("job_board", new ThinPanelBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));
    public static final Block EVIDENCE = register("evidence", new EvidenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque().noCollision()));

    public static final Block RADIO = register("radio", new RadioBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));

    /** One and a half blocks wide and one tall: the rock on the beach. */
    public static final Block ROCK = register("rock", new RockBlock(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque()));

    private ModBlocks() {
    }

    /** Forces the class to load so the blocks register during mod init. */
    public static void init() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, new Identifier(Kokuhaku.MOD_ID, name), block);
    }
}
