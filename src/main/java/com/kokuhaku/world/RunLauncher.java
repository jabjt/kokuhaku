package com.kokuhaku.world;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.level.LevelInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/** Creates the world the game runs in, menu room and town both. Every launch starts from a fresh save. */
public final class RunLauncher {
    public static final String SAVE_DIR = "kokuhaku_run";
    /** The Chinetsu Town map's region files. They're from 1.14, which the game upgrades itself. */
    private static final String MOUNTAINS = "kokuhaku-map";
    private static final String[] MAP_REGIONS = {"r.-1.-1", "r.-1.0", "r.-2.-1", "r.-2.0", "r.0.-1", "r.0.0"};

    private RunLauncher() {
    }

    public static void startNewRun(MinecraftClient client) {
        // A new folder every launch: Windows can take a moment to let go of an old one.
        Path saves = client.getLevelStorage().getSavesDirectory();
        clearOldSaves(saves);
        String dir = SAVE_DIR + "_" + System.currentTimeMillis();
        copyMap(saves.resolve(dir).resolve("region"));
        start(client, dir);
    }

    private static void start(MinecraftClient client, String dir) {
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, null);
        rules.get(GameRules.SPAWN_RADIUS).set(0, null);

        LevelInfo info = new LevelInfo("Kokuhaku", GameMode.ADVENTURE, false,
                net.minecraft.world.Difficulty.PEACEFUL, false, rules, DataConfiguration.SAFE_MODE);
        GeneratorOptions options = new GeneratorOptions(new Random().nextLong(), false, false);

        client.createIntegratedServerLoader().createAndStart(dir, info, options, registries -> {
            DimensionOptionsRegistryHolder flat = registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder();
            // Beyond the map, the ground continues at the map's level (grass at y=20).
            FlatChunkGeneratorConfig base = ((FlatChunkGenerator) flat.getChunkGenerator()).getConfig();
            FlatChunkGeneratorConfig ground = base.with(List.of(new FlatChunkGeneratorLayer(1, Blocks.BEDROCK),
                    new FlatChunkGeneratorLayer(83, Blocks.DIRT), new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK)),
                    base.getStructureOverrides(), base.getBiome());
            return flat.with(registries, new FlatChunkGenerator(ground));
        });
    }

    private static void copyMap(Path regionDir) {
        try {
            Files.createDirectories(regionDir);
            for (String name : MAP_REGIONS) {
                try (InputStream in = RunLauncher.class.getResourceAsStream("/data/kokuhaku/map/" + name + ".mca")) {
                    if (in != null) Files.copy(in, regionDir.resolve(name + ".mca"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // The mountains, if the launcher's fetched them: those files take the town's edge with them, so they go over the top.
            Path mountains = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(MOUNTAINS);
            if (Files.isDirectory(mountains)) {
                try (Stream<Path> files = Files.list(mountains)) {
                    for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".mca")).toList()) Files.copy(f, regionDir.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy the map", e);
        }
    }

    /** Old runs are never reloaded. Anything Windows won't let go of yet can wait for next time. */
    private static void clearOldSaves(Path saves) {
        if (!Files.exists(saves)) return;
        try (Stream<Path> dirs = Files.list(saves)) {
            for (Path dir : dirs.filter(p -> p.getFileName().toString().startsWith(SAVE_DIR)).toList()) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // Only tidying up.
        }
    }
}
