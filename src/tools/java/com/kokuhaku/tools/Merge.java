package com.kokuhaku.tools;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Developer tool, never shipped. Puts another world's terrain into the town's:
 * moved across and down, with the blocks the resource pack has repurposed swapped
 * for ones it hasn't, and foothills built out from every edge down to the town's
 * flat ground, so nothing ends at a cliff. The town itself is never written over.
 *
 *   merge SRC DEST SPEC   DEST already holds copies of any town region files it may touch.
 *
 * SPEC lines:  area X1 Z1 X2 Z2 (source chunks)   move DX DZ (chunks)   down N (sections)
 *              ramp BLOCKS   ground Y   terrain_above Y   swap FROM TO
 */
public final class Merge {
    private static final int MIN_Y = -64, SECTIONS = 24, DATA_VERSION = 3465, SMOOTHING = 8, SMOOTHING_PASSES = 3;

    private record Section(List<String> palette, int[] blocks) {
    }

    private Merge() {
    }

    public static void main(String[] args) throws IOException {
        new Run(Path.of(args[1]), Path.of(args[2]), Path.of(args[3])).run();
    }

    // ------------------------------------------------------------------ sections, read and written back

    private static Section decode(NbtCompound states) {
        NbtList paletteNbt = states.getList("palette", NbtElement.COMPOUND_TYPE);
        List<String> palette = new ArrayList<>();
        for (int i = 0; i < paletteNbt.size(); i++) palette.add(paletteNbt.getCompound(i).getString("Name"));
        // Some sections come with nothing written in them at all: that's air.
        if (palette.isEmpty()) palette.add("minecraft:air");
        int[] blocks = new int[4096];
        if (palette.size() > 1) {
            long[] data = states.getLongArray("data");
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1)), perLong = 64 / bits;
            for (int i = 0; i < 4096; i++) blocks[i] = (int) (data[i / perLong] >>> (i % perLong * bits) & ((1L << bits) - 1));
        }
        return new Section(palette, blocks);
    }

    /** Palette entries keep their properties when they're unchanged: only swapped or new ones lose them. */
    private static NbtCompound encode(Section s, NbtList oldPalette) {
        NbtList palette = new NbtList();
        for (int i = 0; i < s.palette().size(); i++) {
            String name = s.palette().get(i);
            NbtCompound entry = oldPalette != null && i < oldPalette.size() && oldPalette.getCompound(i).getString("Name").equals(name) ? oldPalette.getCompound(i).copy() : new NbtCompound();
            entry.putString("Name", name);
            if (name.endsWith("_leaves") && !entry.contains("Properties")) {
                // Planted here: kept, so they don't fall away like leaves off a felled tree.
                NbtCompound properties = new NbtCompound();
                properties.putString("distance", "1");
                properties.putString("persistent", "true");
                properties.putString("waterlogged", "false");
                entry.put("Properties", properties);
            }
            palette.add(entry);
        }
        NbtCompound states = new NbtCompound();
        states.put("palette", palette);
        if (s.palette().size() > 1) {
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(s.palette().size() - 1)), perLong = 64 / bits;
            long[] data = new long[(4096 + perLong - 1) / perLong];
            for (int i = 0; i < 4096; i++) data[i / perLong] |= (long) s.blocks()[i] << (i % perLong * bits);
            states.putLongArray("data", data);
        }
        return states;
    }

    private static int index(Section s, String name) {
        int at = s.palette().indexOf(name);
        if (at >= 0) return at;
        s.palette().add(name);
        return s.palette().size() - 1;
    }

    private static final int SNOWLINE = 168;
    private static final java.util.Set<String> BARE_ROCK = java.util.Set.of("minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:gravel", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:podzol", "minecraft:calcite", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:packed_mud");

    private static final class Run {
        final Path src, dest;
        int x1, z1, x2, z2, dx, dz, down, ramp = 96, ground = 20, terrainAbove = 30;
        final Map<String, String> swap = new HashMap<>();
        /** Surface height of every column in the target, once moved; MIN_Y where the source has no real terrain. */
        int[][] surface;
        boolean[][] realMask;
        int baseX, baseZ, width, depth;
        final Map<String, RegionFile> open = new HashMap<>();
        int copied, built, protectedChunks;

        Run(Path src, Path dest, Path spec) throws IOException {
            this.src = src;
            this.dest = dest;
            for (String line : Files.readAllLines(spec)) {
                String[] w = line.trim().split("\\s+");
                if (w.length < 2 || w[0].startsWith("#")) continue;
                switch (w[0]) {
                    case "area" -> {
                        x1 = Integer.parseInt(w[1]);
                        z1 = Integer.parseInt(w[2]);
                        x2 = Integer.parseInt(w[3]);
                        z2 = Integer.parseInt(w[4]);
                    }
                    case "move" -> {
                        dx = Integer.parseInt(w[1]);
                        dz = Integer.parseInt(w[2]);
                    }
                    case "down" -> down = Integer.parseInt(w[1]);
                    case "ramp" -> ramp = Integer.parseInt(w[1]);
                    case "ground" -> ground = Integer.parseInt(w[1]);
                    case "terrain_above" -> terrainAbove = Integer.parseInt(w[1]);
                    case "swap" -> swap.put(w[1], w[2]);
                    default -> {
                    }
                }
            }
        }

        RegionFile region(Path dir, ChunkPos pos) {
            Path file = dir.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            return open.computeIfAbsent(file.toString(), k -> {
                try {
                    return new RegionFile(file, dir, false);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        NbtCompound read(Path dir, ChunkPos pos) throws IOException {
            if (dir == src && !Files.exists(dir.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca"))) return null;
            try (DataInputStream in = region(dir, pos).getChunkInputStream(pos)) {
                return in == null ? null : NbtIo.read(in);
            }
        }

        void write(ChunkPos pos, NbtCompound chunk) throws IOException {
            try (DataOutputStream out = region(dest, pos).getChunkOutputStream(pos)) {
                NbtIo.write(chunk, out);
            }
        }

        int sourceHeight(NbtCompound chunk, int x, int z) {
            long[] map = chunk.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
            if (map.length == 0) return MIN_Y;
            int i = z * 16 + x, perLong = 7;
            return (int) (map[i / perLong] >>> (i % perLong * 9) & 511) + MIN_Y - 1;
        }

        void run() throws IOException {
            try {
                measure();
                spread();
                for (int cx = x1 - ramp / 16; cx <= x2 + ramp / 16; cx++) {
                    for (int cz = z1 - ramp / 16; cz <= z2 + ramp / 16; cz++) place(cx, cz);
                }
            } finally {
                for (RegionFile f : open.values()) f.close();
            }
            System.out.println("MERGED " + copied + " chunks copied, " + built + " built, " + protectedChunks + " of the town's left alone");
            System.out.println("PLACED x " + baseX + ".." + (baseX + width) + " z " + baseZ + ".." + (baseZ + depth));
            // The top of the ski run: the highest real ground within reach of the station.
            int peakX = 0, peakZ = 0, peak = Integer.MIN_VALUE;
            for (int wx = -700; wx <= -200; wx++) {
                for (int wz = -900; wz <= -590; wz++) {
                    if (real(wx, wz) && heightAt(wx, wz) > peak) {
                        peak = heightAt(wx, wz);
                        peakX = wx;
                        peakZ = wz;
                    }
                }
            }
            System.out.println("PEAK " + peakX + " " + peak + " " + peakZ);
            picture();
        }

        /** From above, a pixel for every four blocks: the real terrain in its colours, the foothills in brown. */
        void picture() throws IOException {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width / 4, depth / 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width / 4; x++) {
                for (int z = 0; z < depth / 4; z++) {
                    int h = surface[x * 4][z * 4];
                    int v = Math.max(20, Math.min(255, 40 + h));
                    image.setRGB(x, z, realMask[x * 4][z * 4] ? (v / 3) << 16 | v << 8 | v / 3 : v << 16 | (v * 2 / 3) << 8 | v / 3);
                }
            }
            javax.imageio.ImageIO.write(image, "png", dest.resolve("placed.png").toFile());
        }

        /** Every column's surface, moved down, and where the real terrain is. */
        void measure() throws IOException {
            int margin = ramp;
            baseX = (x1 + dx) * 16 - margin;
            baseZ = (z1 + dz) * 16 - margin;
            width = (x2 - x1 + 1) * 16 + margin * 2;
            depth = (z2 - z1 + 1) * 16 + margin * 2;
            surface = new int[width][depth];
            realMask = new boolean[width][depth];
            for (int[] col : surface) java.util.Arrays.fill(col, MIN_Y);
            for (int cx = x1; cx <= x2; cx++) {
                for (int cz = z1; cz <= z2; cz++) {
                    NbtCompound chunk = read(src, new ChunkPos(cx, cz));
                    if (chunk == null) continue;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int y = sourceHeight(chunk, x, z);
                            if (y < terrainAbove) continue;
                            surface[(cx - x1) * 16 + x + margin][(cz - z1) * 16 + z + margin] = y - down * 16;
                            realMask[(cx - x1) * 16 + x + margin][(cz - z1) * 16 + z + margin] = true;
                        }
                    }
                }
            }
        }

        /**
         * Out from the edge of the real terrain, the ground falls away to the town's level:
         * slowly at first, then easing out, the way a hill does. Whatever's nearest decides how high it starts.
         */
        void spread() {
            int[][] distance = new int[width][depth], from = new int[width][depth];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (realMask[x][z]) {
                        queue.add(new int[]{x, z});
                        from[x][z] = surface[x][z];
                    } else {
                        distance[x][z] = Integer.MAX_VALUE;
                    }
                }
            }
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                for (int[] s : steps) {
                    int x = at[0] + s[0], z = at[1] + s[1];
                    if (x < 0 || z < 0 || x >= width || z >= depth || distance[x][z] <= distance[at[0]][at[1]] + 1) continue;
                    distance[x][z] = distance[at[0]][at[1]] + 1;
                    from[x][z] = from[at[0]][at[1]];
                    queue.add(new int[]{x, z});
                }
            }
            // Whoever's nearest leaves streaks where it changes: smoothed out, a few times over, it's a slope.
            for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
                int[][] next = new int[width][depth];
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < depth; z++) {
                        if (realMask[x][z]) {
                            next[x][z] = from[x][z];
                            continue;
                        }
                        long sum = 0;
                        int n = 0;
                        for (int ox = -SMOOTHING; ox <= SMOOTHING; ox += 2) {
                            for (int oz = -SMOOTHING; oz <= SMOOTHING; oz += 2) {
                                int sx = x + ox, sz = z + oz;
                                if (sx < 0 || sz < 0 || sx >= width || sz >= depth) continue;
                                sum += from[sx][sz];
                                n++;
                            }
                        }
                        next[x][z] = (int) (sum / n);
                    }
                }
                from = next;
            }
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (realMask[x][z]) continue;
                    // A little roughness, so it isn't a perfect cone.
                    // Steep near the top, a long gentle tail at the bottom: the way the foot of a mountain goes.
                    double t = Math.min(1.0, distance[x][z] / (double) ramp), eased = 1 - (1 - t) * (1 - t);
                    double wobble = Math.sin((baseX + x) * 0.13) * Math.cos((baseZ + z) * 0.11) * 2.5 * (1 - t);
                    surface[x][z] = (int) Math.round(from[x][z] + (ground - from[x][z]) * eased + wobble);
                }
            }
        }

        int heightAt(int worldX, int worldZ) {
            int x = worldX - baseX, z = worldZ - baseZ;
            if (x < 0 || z < 0 || x >= width || z >= depth) return ground;
            return surface[x][z];
        }

        boolean real(int worldX, int worldZ) {
            int x = worldX - baseX, z = worldZ - baseZ;
            return x >= 0 && z >= 0 && x < width && z < depth && realMask[x][z];
        }

        /** Built in the town already: anything solid above its ground. */
        boolean townsAlready(NbtCompound chunk) {
            NbtList sections = chunk.getList("sections", NbtElement.COMPOUND_TYPE);
            for (int s = 0; s < sections.size(); s++) {
                NbtCompound section = sections.getCompound(s);
                int y = section.getByte("Y");
                if (y < Math.floorDiv(ground + 2, 16)) continue;
                Section decoded = decode(section.getCompound("block_states"));
                for (int i = 0; i < 4096; i++) {
                    int by = y * 16 + (i >> 8);
                    if (by > ground + 1 && !decoded.palette().get(decoded.blocks()[i]).equals("minecraft:air")) return true;
                }
            }
            return false;
        }

        void place(int sourceX, int sourceZ) throws IOException {
            ChunkPos to = new ChunkPos(sourceX + dx, sourceZ + dz);
            if (Files.exists(dest.resolve("r." + to.getRegionX() + "." + to.getRegionZ() + ".mca"))) {
                NbtCompound there = read(dest, to);
                if (there != null && townsAlready(there)) {
                    protectedChunks++;
                    return;
                }
            }
            boolean inside = sourceX >= x1 && sourceX <= x2 && sourceZ >= z1 && sourceZ <= z2;
            NbtCompound chunk = inside ? read(src, new ChunkPos(sourceX, sourceZ)) : null;
            boolean anyReal = false, anyBuilt = false;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    boolean isReal = chunk != null && real(to.getStartX() + x, to.getStartZ() + z);
                    anyReal |= isReal;
                    anyBuilt |= !isReal && heightAt(to.getStartX() + x, to.getStartZ() + z) > ground;
                }
            }
            if (!anyReal && !anyBuilt) return;
            if (chunk == null || !anyReal) {
                chunk = blank(to);
                built++;
            } else {
                moveDown(chunk);
                copied++;
            }
            chunk.putInt("xPos", to.x);
            chunk.putInt("zPos", to.z);
            sculpt(chunk, to);
            write(to, chunk);
        }

        NbtCompound blank(ChunkPos pos) {
            NbtCompound chunk = new NbtCompound();
            chunk.putInt("DataVersion", DATA_VERSION);
            chunk.putInt("yPos", MIN_Y / 16);
            chunk.putString("Status", "minecraft:full");
            chunk.putLong("LastUpdate", 0);
            chunk.putLong("InhabitedTime", 0);
            NbtList sections = new NbtList();
            for (int y = MIN_Y / 16; y < MIN_Y / 16 + SECTIONS; y++) {
                NbtCompound section = new NbtCompound();
                section.putByte("Y", (byte) y);
                Section air = new Section(new ArrayList<>(List.of("minecraft:air")), new int[4096]);
                section.put("block_states", encode(air, null));
                NbtCompound biomes = new NbtCompound();
                NbtList biomePalette = new NbtList();
                biomePalette.add(NbtString.of("minecraft:plains"));
                biomes.put("palette", biomePalette);
                section.put("biomes", biomes);
                sections.add(section);
            }
            chunk.put("sections", sections);
            chunk.put("block_entities", new NbtList());
            chunk.putBoolean("isLightOn", false);
            return chunk;
        }

        /** Lower, with the blocks swapped, and anything that would need re-measuring or re-lighting left for the game to do. */
        void moveDown(NbtCompound chunk) {
            NbtList sections = chunk.getList("sections", NbtElement.COMPOUND_TYPE), kept = new NbtList();
            for (int s = 0; s < sections.size(); s++) {
                NbtCompound section = sections.getCompound(s);
                int y = section.getByte("Y") - down;
                if (y < MIN_Y / 16 || y >= MIN_Y / 16 + SECTIONS) continue;
                section.putByte("Y", (byte) y);
                section.remove("BlockLight");
                section.remove("SkyLight");
                kept.add(section);
            }
            // Everything that was above the top now has nowhere to be; the floor of the world is solid.
            chunk.put("sections", kept);
            chunk.remove("Heightmaps");
            chunk.putBoolean("isLightOn", false);
            chunk.put("block_entities", new NbtList());
            chunk.remove("block_ticks");
            chunk.remove("fluid_ticks");
            chunk.remove("structures");
            chunk.remove("PostProcessing");
            chunk.putString("Status", "minecraft:full");
        }

        /** Spruces up the foothills, thinning out the higher it gets, so they run on into the forest. Kept inside the chunk. */
        Map<Integer, String> trees(ChunkPos to) {
            Map<Integer, String> out = new HashMap<>();
            for (int x = 2; x <= 13; x++) {
                for (int z = 2; z <= 13; z++) {
                    int wx = to.getStartX() + x, wz = to.getStartZ() + z;
                    if (real(wx, wz)) continue;
                    int top = heightAt(wx, wz);
                    if (top < ground + 3 || top > 135) continue;
                    if (Math.abs(heightAt(wx + 1, wz) - heightAt(wx - 1, wz)) > 3 || Math.abs(heightAt(wx, wz + 1) - heightAt(wx, wz - 1)) > 3) continue;
                    long h = (wx * 0x9E3779B97F4A7C15L) ^ (wz * 0xC2B2AE3D27D4EB4FL);
                    h ^= h >>> 29;
                    h *= 0xBF58476D1CE4E5B9L;
                    h ^= h >>> 32;
                    int odds = top > 110 ? 400 : top > 80 ? 110 : 55;
                    if (Math.floorMod(h, odds) != 0) continue;
                    int tall = 6 + (int) Math.floorMod(h >>> 8, 5L), crown = top + tall + 1;
                    for (int y = top + 3; y <= crown; y++) {
                        int d = crown - y, r = d == 0 ? 0 : d <= 2 ? 1 : d % 2 == 1 ? 2 : 1;
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                                int key = (y - MIN_Y) * 256 + (z + dz) * 16 + x + dx;
                                out.putIfAbsent(key, "minecraft:spruce_leaves");
                            }
                        }
                    }
                    for (int y = top + 1; y <= top + tall; y++) out.put((y - MIN_Y) * 256 + z * 16 + x, "minecraft:spruce_log");
                }
            }
            return out;
        }

        /** Swaps blocks, fills the foothills, and floors the world. */
        void sculpt(NbtCompound chunk, ChunkPos to) {
            NbtList sections = chunk.getList("sections", NbtElement.COMPOUND_TYPE);
            Map<Integer, NbtCompound> byY = new HashMap<>();
            for (int s = 0; s < sections.size(); s++) byY.put((int) sections.getCompound(s).getByte("Y"), sections.getCompound(s));
            for (int y = MIN_Y / 16; y < MIN_Y / 16 + SECTIONS; y++) {
                if (byY.containsKey(y)) continue;
                NbtCompound section = new NbtCompound();
                section.putByte("Y", (byte) y);
                section.put("block_states", encode(new Section(new ArrayList<>(List.of("minecraft:air")), new int[4096]), null));
                NbtCompound biomes = new NbtCompound();
                NbtList biomePalette = new NbtList();
                biomePalette.add(NbtString.of("minecraft:plains"));
                biomes.put("palette", biomePalette);
                section.put("biomes", biomes);
                sections.add(section);
                byY.put(y, section);
            }
            Map<Integer, String> trees = trees(to);
            for (var entry : byY.entrySet()) {
                int sy = entry.getKey();
                NbtCompound section = entry.getValue();
                NbtList oldPalette = section.getCompound("block_states").getList("palette", NbtElement.COMPOUND_TYPE);
                Section s = decode(section.getCompound("block_states"));
                for (int i = 0; i < s.palette().size(); i++) {
                    String swapped = swap.get(s.palette().get(i));
                    if (swapped != null) s.palette().set(i, swapped);
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = to.getStartX() + x, wz = to.getStartZ() + z;
                        boolean isReal = real(wx, wz);
                        int top = heightAt(wx, wz);
                        for (int ly = 0; ly < 16; ly++) {
                            int y = sy * 16 + ly, i = ly * 256 + z * 16 + x;
                            String now = s.palette().get(s.blocks()[i]);
                            if (y == MIN_Y) {
                                s.blocks()[i] = index(s, "minecraft:bedrock");
                            } else if (!isReal) {
                                // Foothills: earth, grass on top, snow where it's high enough to settle.
                                String tree = y > top ? trees.get((y - MIN_Y) * 256 + z * 16 + x) : null;
                                String want = tree != null ? tree : y > top ? "minecraft:air" : y == top ? (top > 150 ? "minecraft:snow_block" : top > 70 ? "minecraft:coarse_dirt" : "minecraft:grass_block") : y > top - 4 ? "minecraft:dirt" : "minecraft:stone";
                                s.blocks()[i] = index(s, want);
                            } else if (y >= top - 1 && y <= top && y >= SNOWLINE + (int) (Math.sin(wx * 0.07) * Math.cos(wz * 0.09) * 9) && BARE_ROCK.contains(now)) {
                                // Snow lies on the high slopes: it's a ski mountain.
                                s.blocks()[i] = index(s, "minecraft:snow_block");
                            } else if (now.equals("minecraft:air") && y <= ground - 30) {
                                // What was cut off underneath is solid ground, not a hole down to nothing.
                                s.blocks()[i] = index(s, "minecraft:stone");
                            }
                        }
                    }
                }
                section.put("block_states", encode(s, oldPalette));
            }
        }
    }
}
