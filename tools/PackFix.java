import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Brings the 1.14 texture pack up to what 1.20.1 will load, in place:
 * legal file names (lower case, no spaces), model paths with their block/
 * prefix, walls with low/tall sides, moved cauldron states, renamed blocks,
 * and the OptiFine connected textures that Continuity reads.
 *
 * java tools/PackFix.java <source pack> <dest pack> <minecraft-client.jar>
 */
public class PackFix {
    private static final Pattern VALUE = Pattern.compile("\"([a-zA-Z_]+|[^\"]*)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern WALL_SIDE = Pattern.compile("\"(north|east|south|west)\"\\s*:\\s*\"(true|false)\"");
    private static final Set<String> PATH_KEYS = Set.of("tiles", "matchtiles", "connecttiles", "source");

    private static Path assets;
    private static final Set<String> vanillaModels = new HashSet<>();

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]), dest = Path.of(args[1]);
        assets = dest.resolve("assets/minecraft");
        try (ZipFile zip = new ZipFile(args[2])) {
            zip.stream().map(e -> e.getName()).filter(n -> n.startsWith("assets/minecraft/models/") && n.endsWith(".json"))
                    .forEach(n -> vanillaModels.add(n.substring("assets/minecraft/models/".length(), n.length() - 5)));
        }

        copyTree(source.resolve("assets/minecraft/optifine/ctm"), assets.resolve("optifine/ctm"));
        int renamed = renameAll();
        int models = 0;
        for (Path f : walk(assets.resolve("models"), ".json")) models += fixJson(f, "parent", false);
        for (Path f : walk(assets.resolve("blockstates"), ".json")) {
            models += fixJson(f, "model", true);
            if (f.getFileName().toString().endsWith("_wall.json")) fixWall(f);
        }
        int tiles = 0;
        for (Path f : walk(assets.resolve("optifine"), ".properties")) tiles += fixProperties(f);
        cauldrons();
        renames();
        System.out.println("renamed " + renamed + " files, fixed " + models + " model refs, " + tiles + " ctm files");
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static List<Path> walk(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).toList();
        }
    }

    private static String legal(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._:#-]", "_");
    }

    /** Files first, then directories deepest-first, via a temp name so case-only renames work on Windows. */
    private static int renameAll() throws IOException {
        List<Path> all;
        try (Stream<Path> s = Files.walk(assets)) {
            all = new ArrayList<>(s.toList());
        }
        all.sort((a, b) -> b.getNameCount() - a.getNameCount());
        int n = 0;
        for (Path p : all) {
            if (p.equals(assets)) continue;
            String name = p.getFileName().toString(), fixed = legal(name);
            if (name.equals(fixed)) continue;
            Path temp = p.resolveSibling(fixed + ".tmp_rename");
            Files.move(p, temp);
            Path target = p.resolveSibling(fixed);
            if (Files.exists(target)) Files.delete(temp); // the legal name wins
            else Files.move(temp, target);
            n++;
        }
        return n;
    }

    private static String withPrefix(String ref) {
        boolean spaced = ref.startsWith("minecraft:");
        String path = spaced ? ref.substring("minecraft:".length()) : ref;
        if (path.contains(":") || path.startsWith("builtin/") || path.startsWith("block/") || path.startsWith("item/")) return ref;
        if (exists(path)) return ref;
        return exists("block/" + path) ? (spaced ? "minecraft:" : "") + "block/" + path : ref;
    }

    private static boolean exists(String model) {
        return vanillaModels.contains(model) || Files.exists(assets.resolve("models/" + model + ".json"));
    }

    private static int fixJson(Path file, String prefixKey, boolean always) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        int[] count = {0};
        String fixed = VALUE.matcher(text).replaceAll(m -> {
            String key = m.group(1), value = m.group(2);
            if (value.startsWith("#") || key.equals("credit") || key.equals("__comment")) return Matcher.quoteReplacement(m.group());
            String v = value;
            if (v.contains("/") || v.contains(":")) v = legal(v);
            if (key.equals(prefixKey)) v = withPrefix(legal(v));
            if (!v.equals(value)) count[0]++;
            return Matcher.quoteReplacement("\"" + key + "\": \"" + v + "\"");
        });
        if (count[0] > 0) Files.writeString(file, fixed, StandardCharsets.UTF_8);
        return count[0];
    }

    private static void fixWall(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String fixed = WALL_SIDE.matcher(text).replaceAll(m -> "\"" + m.group(1) + "\": \"" + (m.group(2).equals("true") ? "low|tall" : "none") + "\"");
        Files.writeString(file, fixed, StandardCharsets.UTF_8);
    }

    private static int fixProperties(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        boolean changed = false;
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            int eq = line.indexOf('=');
            if (eq > 0 && PATH_KEYS.contains(line.substring(0, eq).trim().toLowerCase(Locale.ROOT))) {
                String value = line.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
                String fixed = line.substring(0, eq + 1) + value;
                changed |= !fixed.equals(line);
                line = fixed;
            }
            lines.add(line);
        }
        if (changed) Files.write(file, lines, StandardCharsets.ISO_8859_1);
        return changed ? 1 : 0;
    }

    /** Since 1.17 a cauldron with water is its own block with levels 1-3; an empty one has no state. */
    private static void cauldrons() throws IOException {
        Files.deleteIfExists(assets.resolve("blockstates/jigsaw.json"));
        Path states = assets.resolve("blockstates");
        Files.writeString(states.resolve("cauldron.json"), "{\"variants\":{\"\":{\"model\":\"block/cauldron\"}}}");
        Files.writeString(states.resolve("water_cauldron.json"), "{\"variants\":{"
                + "\"level=1\":{\"model\":\"block/cauldron_level1\"},"
                + "\"level=2\":{\"model\":\"block/cauldron_level2\"},"
                + "\"level=3\":{\"model\":\"block/cauldron_level3\"}}}");
    }

    private static void renames() throws IOException {
        copyIfPresent("blockstates/grass_path.json", "blockstates/dirt_path.json");
        copyIfPresent("textures/block/grass_path_top.png", "textures/block/dirt_path_top.png");
        copyIfPresent("textures/block/grass_path_side.png", "textures/block/dirt_path_side.png");
    }

    private static void copyIfPresent(String from, String to) throws IOException {
        Path f = assets.resolve(from);
        if (Files.exists(f)) Files.copy(f, assets.resolve(to), StandardCopyOption.REPLACE_EXISTING);
    }
}
