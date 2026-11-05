package com.kokuhaku.world.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Things you walk round, not over. The same rule for you and for everyone else in town. */
public final class Furniture {
    private static final List<String> ANYWHERE = List.of("leaves", "jukebox", "crafting_table", "bookshelf", "_log", "_wool",
            "barrel", "furnace", "smoker", "note_block", "lectern", "loom", "cartography_table", "fletching_table", "smithing_table",
            "enchanting_table", "stonecutter", "grindstone", "brewing_stand", "cauldron", "hopper", "dispenser", "dropper", "flower_pot",
            "sea_pickle", "cake", "bell", "beehive", "bee_nest", "_wood", "hay_block", "pumpkin", "melon", "lantern", "_skull",
            "campfire", "conduit", "composter", "iron_bars", "_pane", "chain", "stained_glass", "soul_sand", "turtle_egg",
            "chest", "anvil", "piston", "observer", "end_portal_frame", "daylight_detector", "_bed");
    /** Only furniture indoors: these same blocks are doorsteps and stairs elsewhere on the map. */
    private static final List<String> INDOORS = List.of("brown_terracotta", "purpur_stairs", "end_stone_brick_stairs", "quartz_stairs");
    private static final Map<Block, Boolean> KNOWN = new ConcurrentHashMap<>(), KNOWN_INDOORS = new ConcurrentHashMap<>();

    private Furniture() {
    }

    private static boolean named(Map<Block, Boolean> known, List<String> names, Block block) {
        return known.computeIfAbsent(block, b -> {
            String name = Registries.BLOCK.getId(b).getPath();
            return names.stream().anyMatch(name::contains);
        });
    }

    public static boolean at(World world, BlockPos pos) {
        return is(world, pos, world.getBlockState(pos));
    }

    public static boolean is(World world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        boolean anywhere = named(KNOWN, ANYWHERE, block), indoors = !anywhere && named(KNOWN_INDOORS, INDOORS, block);
        if (!anywhere && !indoors) return false;
        if (state.getCollisionShape(world, pos).isEmpty()) return false;
        if (anywhere) return true;
        if (world.isSkyVisible(pos.up())) return false;
        if (!(block instanceof StairsBlock)) return true;
        // A flight of stairs carries on up or down. A chair doesn't, and nothing leads to it from above.
        for (Direction way : Direction.Type.HORIZONTAL) {
            if (world.getBlockState(pos.up().offset(way)).getBlock() instanceof StairsBlock) return false;
            if (world.getBlockState(pos.down().offset(way)).getBlock() instanceof StairsBlock) return false;
        }
        return true;
    }
}
