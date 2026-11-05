package com.kokuhaku.tools;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer tool, never shipped: reads another world's region files.
 *   overview SRC OUT.png       a picture from above, a pixel for every four blocks
 *   convert SRC DEST MAP.txt   copies chunks across, moved and with blocks swapped, as MAP.txt says
 */
public final class MapTool {
    private static final int MIN_Y = -64, STEP = 4;

    private MapTool() {
    }

    public static void main(String[] args) throws IOException {
        switch (args[0]) {
            case "overview" -> overview(Path.of(args[1]), Path.of(args[2]));
            case "convert" -> convert(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            case "merge" -> Merge.main(args);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static NbtCompound read(RegionFile file, ChunkPos pos) throws IOException {
        try (DataInputStream in = file.getChunkInputStream(pos)) {
            return in == null ? null : NbtIo.read(in);
        }
    }

    private static int height(long[] map, int x, int z) {
        int bits = 9, perLong = 64 / bits, i = z * 16 + x;
        long word = map[i / perLong];
        return (int) (word >>> (i % perLong * bits) & ((1L << bits) - 1)) + MIN_Y - 1;
    }

    /** The block at a spot in a chunk, by name, or null if that part of the chunk isn't stored. */
    private static String block(NbtCompound chunk, int x, int y, int z) {
        NbtList sections = chunk.getList("sections", NbtElement.COMPOUND_TYPE);
        for (int s = 0; s < sections.size(); s++) {
            NbtCompound section = sections.getCompound(s);
            if (section.getByte("Y") != Math.floorDiv(y, 16)) continue;
            NbtCompound states = section.getCompound("block_states");
            NbtList palette = states.getList("palette", NbtElement.COMPOUND_TYPE);
            if (palette.isEmpty()) return null;
            if (palette.size() == 1) return palette.getCompound(0).getString("Name");
            long[] data = states.getLongArray("data");
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1)), perLong = 64 / bits;
            int i = (y & 15) * 256 + z * 16 + x;
            int index = (int) (data[i / perLong] >>> (i % perLong * bits) & ((1L << bits) - 1));
            return palette.getCompound(index).getString("Name");
        }
        return null;
    }

    private static int colour(String top, int y) {
        int base;
        if (top == null) base = 0x202020;
        else if (top.contains("water") || top.contains("ice")) base = 0x3060C0;
        else if (top.contains("snow")) base = 0xF0F0F8;
        else if (top.contains("leaves") || top.contains("log")) base = 0x2A5A22;
        else if (top.contains("grass") || top.contains("fern") || top.contains("flower")) base = 0x5A9A3A;
        else if (top.contains("sand")) base = 0xD8C880;
        else if (top.contains("stone") || top.contains("andesite") || top.contains("gravel") || top.contains("tuff") || top.contains("calcite")) base = 0x8A8A8A;
        else if (top.contains("dirt") || top.contains("podzol") || top.contains("mud")) base = 0x7A5A3A;
        else base = 0xC040C0;
        float shade = Math.max(0.35f, Math.min(1.25f, 0.45f + (y - 40) / 220f));
        int r = Math.min(255, (int) ((base >> 16 & 255) * shade)), g = Math.min(255, (int) ((base >> 8 & 255) * shade)), b = Math.min(255, (int) ((base & 255) * shade));
        return r << 16 | g << 8 | b;
    }

    private static void overview(Path src, Path out) throws IOException {
        int minRx = Integer.MAX_VALUE, maxRx = Integer.MIN_VALUE, minRz = Integer.MAX_VALUE, maxRz = Integer.MIN_VALUE;
        try (var files = Files.list(src)) {
            for (Path p : files.toList()) {
                String[] parts = p.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
                minRx = Math.min(minRx, rx);
                maxRx = Math.max(maxRx, rx);
                minRz = Math.min(minRz, rz);
                maxRz = Math.max(maxRz, rz);
            }
        }
        int width = (maxRx - minRx + 1) * 512 / STEP, depth = (maxRz - minRz + 1) * 512 / STEP;
        BufferedImage image = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        Map<String, Integer> blocks = new TreeMap<>();
        Map<Integer, Integer> heights = new TreeMap<>();
        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                Path file = src.resolve("r." + rx + "." + rz + ".mca");
                if (!Files.exists(file)) continue;
                try (RegionFile region = new RegionFile(file, src, false)) {
                    for (int cx = 0; cx < 32; cx++) {
                        for (int cz = 0; cz < 32; cz++) {
                            ChunkPos pos = new ChunkPos(rx * 32 + cx, rz * 32 + cz);
                            NbtCompound chunk = read(region, pos);
                            if (chunk == null) continue;
                            long[] map = chunk.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
                            NbtList sections = chunk.getList("sections", NbtElement.COMPOUND_TYPE);
                            for (int s = 0; s < sections.size(); s++) {
                                NbtList palette = sections.getCompound(s).getCompound("block_states").getList("palette", NbtElement.COMPOUND_TYPE);
                                for (int i = 0; i < palette.size(); i++) blocks.merge(palette.getCompound(i).getString("Name"), 1, Integer::sum);
                            }
                            for (int x = 0; x < 16; x += STEP) {
                                for (int z = 0; z < 16; z += STEP) {
                                    int y = map.length == 0 ? 0 : height(map, x, z);
                                    heights.merge(y / 10 * 10, 1, Integer::sum);
                                    int px = ((rx - minRx) * 512 + cx * 16 + x) / STEP, pz = ((rz - minRz) * 512 + cz * 16 + z) / STEP;
                                    image.setRGB(px, pz, colour(block(chunk, x, y, z), y));
                                }
                            }
                        }
                    }
                }
                System.out.println("read r." + rx + "." + rz);
            }
        }
        ImageIO.write(image, "png", out.toFile());
        System.out.println("REGIONS x " + minRx + ".." + maxRx + " z " + minRz + ".." + maxRz + ", image " + width + "x" + depth + ", 1px = " + STEP + " blocks");
        System.out.println("HEIGHTS " + heights);
        System.out.println("BLOCKS " + blocks);
    }

    /**
     * MAP.txt lines:
     *   area SRC_CHUNK_X1 SRC_CHUNK_Z1 SRC_CHUNK_X2 SRC_CHUNK_Z2   what to take
     *   move CHUNKS_X CHUNKS_Z SECTIONS_Y                          where it goes
     *   swap FROM TO                                               blocks the resource pack has made into something else
     */
    private static void convert(Path src, Path dest, Path spec) throws IOException {
        int x1 = 0, z1 = 0, x2 = 0, z2 = 0, mx = 0, mz = 0, my = 0;
        Map<String, String> swap = new HashMap<>();
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
                    mx = Integer.parseInt(w[1]);
                    mz = Integer.parseInt(w[2]);
                    my = Integer.parseInt(w[3]);
                }
                case "swap" -> swap.put(w[1], w[2]);
                default -> {
                }
            }
        }
        Files.createDirectories(dest);
        Map<String, RegionFile> open = new HashMap<>();
        int copied = 0;
        try {
            for (int cx = x1; cx <= x2; cx++) {
                for (int cz = z1; cz <= z2; cz++) {
                    Path from = src.resolve("r." + Math.floorDiv(cx, 32) + "." + Math.floorDiv(cz, 32) + ".mca");
                    if (!Files.exists(from)) continue;
                    RegionFile in = open.computeIfAbsent("in" + from, k -> regionFile(from, src));
                    NbtCompound chunk = read(in, new ChunkPos(cx, cz));
                    if (chunk == null) continue;
                    ChunkPos to = new ChunkPos(cx + mx, cz + mz);
                    move(chunk, to, my, swap);
                    Path target = dest.resolve("r." + to.getRegionX() + "." + to.getRegionZ() + ".mca");
                    RegionFile out = open.computeIfAbsent("out" + target, k -> regionFile(target, dest));
                    try (DataOutputStream stream = out.getChunkOutputStream(to)) {
                        NbtIo.write(chunk, stream);
                    }
                    copied++;
                }
            }
        } finally {
            for (RegionFile f : open.values()) f.close();
        }
        System.out.println("CONVERTED " + copied + " chunks");
    }

    private static RegionFile regionFile(Path file, Path dir) {
        try {
            return new RegionFile(file, dir, false);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Somewhere else, lower down, made of the blocks this game can show. It'll be lit and measured again when it loads. */
    private static void move(NbtCompound chunk, ChunkPos to, int down, Map<String, String> swap) {
        chunk.putInt("xPos", to.x);
        chunk.putInt("zPos", to.z);
        NbtList sections = chunk.getList("sections", NbtElement.COMPOUND_TYPE), kept = new NbtList();
        int lowest = MIN_Y / 16;
        for (int s = 0; s < sections.size(); s++) {
            NbtCompound section = sections.getCompound(s);
            int y = section.getByte("Y") + down;
            if (y < lowest) continue;
            section.putByte("Y", (byte) y);
            section.remove("BlockLight");
            section.remove("SkyLight");
            NbtList palette = section.getCompound("block_states").getList("palette", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < palette.size(); i++) {
                NbtCompound entry = palette.getCompound(i);
                String name = swap.get(entry.getString("Name"));
                if (name == null) continue;
                entry.put("Name", NbtString.of(name));
                entry.remove("Properties");
            }
            kept.add(section);
        }
        chunk.put("sections", kept);
        chunk.remove("Heightmaps");
        chunk.putBoolean("isLightOn", false);
        chunk.remove("block_entities");
        chunk.remove("block_ticks");
        chunk.remove("fluid_ticks");
        chunk.remove("structures");
        chunk.putString("Status", "minecraft:full");
    }
}
