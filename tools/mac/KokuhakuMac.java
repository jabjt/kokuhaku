import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * The Mac launcher: the same window as on Windows, with a Launch button and the
 * versions down the side. Prism Launcher signs in and runs the game, but it's
 * started hidden, so all anyone sees is this and then the game. The game itself
 * comes in the app, as game.zip beside this; the first time, or when it's newer,
 * it goes into Prism as the Kokuhaku instance, and what's too big to pack is fetched.
 */
public final class KokuhakuMac extends JPanel {
    private static final String INSTANCE = "Kokuhaku", TRUE_NAME = "⸸ꙮᛉ⛧ᚦ⸸";
    private static final String DOWNLOAD_LIST = "downloads.txt", MAP_LIST = "map.txt", MAP_FOLDER = "kokuhaku-map";
    private static final String SHADER_FILE = "ComplementaryUnbound_r5.9.3.zip";
    private static final String SHADER_URL = "https://cdn.modrinth.com/data/R6NEzAwj/versions/B1kyfoUZ/ComplementaryUnbound_r5.9.3.zip";
    private static final String SHADER_SHA1 = "2ee08300e1d6f039e63eae8484dddf57b3aaaf67";
    /** Shaders want a good deal of memory: below this, the game runs without them. */
    private static final long SHADERS_NEED_GB = 12;
    private static final long GIVE_UP_MS = 240_000, GAME_SETTLES_MS = 4_000;

    private static final Color BACK = new Color(14, 12, 15), SIDE = new Color(9, 8, 10), BAR = new Color(18, 16, 19);
    private static final Color GREY = new Color(138, 138, 146), DIM = new Color(78, 76, 84), PALE = new Color(222, 214, 218);
    private static final Color RED = new Color(176, 24, 42), RED_LIT = new Color(206, 34, 54);
    private static final int W = 960, H = 620, TITLE_H = 36, SIDE_W = 210, MOST_SHOWN = 7;
    /** This launcher's own version: a newer release on GitHub replaces it. */
    private static final String LAUNCHER_VERSION = "1.4.1";
    private static final String RELEASES = "https://api.github.com/repos/jackieboyjt/kokuhaku/releases";
    /** Name, detail, "ready" or not, and what changed. */
    private volatile String[][] VERSIONS = {{"Kokuhaku " + LAUNCHER_VERSION, "1.20.1  \u00b7  this one", "ready", "Looking for updates..."}, {"Next update", "1.20.1  \u00b7  WIP", "", ""}};
    private int notesScroll = 0;

    private final JFrame frame;
    private final Path pack;
    private final Rectangle minBox = new Rectangle(W - 92, 0, 46, TITLE_H), closeBox = new Rectangle(W - 46, 0, 46, TITLE_H), launchBox = new Rectangle(SIDE_W + 48, H - 140, 220, 54);
    private final Rectangle notesBox = new Rectangle(SIDE_W + 48, TITLE_H + 206, W - SIDE_W - 96, H - TITLE_H - 206 - 160);
    private final Font huge = new Font("Georgia", Font.PLAIN, 44), body = new Font("Helvetica Neue", Font.PLAIN, 14), small = new Font("Helvetica Neue", Font.PLAIN, 12), bold = new Font("Helvetica Neue", Font.BOLD, 14), button = new Font("Helvetica Neue", Font.BOLD, 17);
    private int selected = 0, hover = -1;
    private boolean overLaunch, overMin, overClose;
    private volatile boolean busy;
    private volatile String status = "";
    private volatile float progress = -1f;
    private Point dragFrom;
    /** The name, drawn at twice the size it's shown, so it's sharp on a Retina screen. */
    private final BufferedImage titleImage = image("/title.png"), nameImage = image("/name.png");

    private static BufferedImage image(String name) {
        try (InputStream in = KokuhakuMac.class.getResourceAsStream(name)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", TRUE_NAME);
        Path pack = Path.of(args.length > 0 ? args[0] : "game.zip");
        // An update fetched earlier is newer than what came in the app: that one runs instead.
        if (handOver()) return;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(TRUE_NAME);
            frame.setUndecorated(true);
            KokuhakuMac panel = new KokuhakuMac(frame, pack);
            frame.setContentPane(panel);
            frame.pack();
            frame.setShape(new RoundRectangle2D.Double(0, 0, W, H, 28, 28));
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            String shot = System.getenv("KOKUHAKU_LAUNCHERSHOT");
            if (shot != null && !shot.isEmpty()) {
                BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                panel.setSize(W, H);
                panel.paint(image.getGraphics());
                try {
                    ImageIO.write(image, "png", new File(shot));
                } catch (IOException ignored) {
                }
                System.exit(0);
            }
            frame.setVisible(true);
            if ("1".equals(System.getenv("KOKUHAKU_AUTOLAUNCH"))) panel.launch();
        });
    }

    private KokuhakuMac(JFrame frame, Path pack) {
        this.frame = frame;
        this.pack = pack;
        setPreferredSize(new Dimension(W, H));
        setBackground(BACK);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                overMin = minBox.contains(e.getPoint());
                overClose = closeBox.contains(e.getPoint());
                overLaunch = launchBox.contains(e.getPoint());
                hover = -1;
                for (int i = 0; i < VERSIONS.length; i++) if (versionBox(i).contains(e.getPoint())) hover = i;
                setCursor(Cursor.getPredefinedCursor(overLaunch && !busy || hover >= 0 && ready(hover) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                Point at = e.getPoint();
                if (closeBox.contains(at)) System.exit(0);
                else if (minBox.contains(at)) frame.setState(Frame.ICONIFIED);
                else if (launchBox.contains(at)) launch();
                else if (hover >= 0 && ready(hover) && !busy) {
                    selected = hover;
                    notesScroll = 0;
                }
                else if (at.y < TITLE_H) dragFrom = at;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragFrom = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragFrom == null) return;
                Point on = e.getLocationOnScreen();
                frame.setLocation(on.x - dragFrom.x, on.y - dragFrom.y);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        new Timer(50, e -> repaint()).start();
        addMouseWheelListener(e -> {
            if (notesBox.contains(e.getPoint())) notesScroll = Math.max(0, notesScroll + e.getUnitsToScroll() * 6);
        });
        Thread check = new Thread(this::checkForUpdates);
        check.setDaemon(true);
        check.start();
    }

    // ------------------------------------------------------------------ versions and updates, from GitHub

    /** Where updates are kept: the app itself may not be somewhere this can write. */
    private static Path updates() {
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Kokuhaku", "current");
    }

    private static int[] number(String tag) {
        if (tag == null) return null;
        String t = tag.replaceFirst("^[vV]", "");
        if (!t.matches("\\d+(\\.\\d+){1,2}")) return null;
        String[] p = t.split("\\.");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 0};
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return 0;
    }

    private static String shown(int[] v) {
        return v[0] + "." + v[1] + "." + v[2];
    }

    private static boolean handOver() {
        try {
            Path dir = updates();
            int[] there = number(Files.readString(dir.resolve("version.txt")).trim()), mine = number(LAUNCHER_VERSION);
            if (there == null || compare(there, mine) <= 0 || !Files.isRegularFile(dir.resolve("launcher.jar")) || !Files.isRegularFile(dir.resolve("game.zip"))) return false;
            String java = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(java, "-Dapple.awt.application.name=" + TRUE_NAME, "-jar", dir.resolve("launcher.jar").toString(), dir.resolve("game.zip").toString()).inheritIO().start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Just enough JSON for GitHub's answer: objects, lists, strings, numbers, true, false and null. */
    private static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Object value() {
            space();
            char c = s.charAt(i);
            if (c == '{') {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                i++;
                space();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    space();
                    String k = string();
                    space();
                    i++; // :
                    m.put(k, value());
                    space();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                space();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    space();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            int from = i;
            while (i < s.length() && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) i++;
            String word = s.substring(from, i);
            return switch (word) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null" -> null;
                default -> word;
            };
        }

        private void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    /** Markdown, as plain lines: headings in capitals, bullets as dots. */
    private static String plain(String body) {
        List<String> lines = new ArrayList<>();
        for (String raw : body.replace("\r", "").split("\n", -1)) {
            // Hidden notes, how to download it (you have), and asides: not for in here.
            if (raw.strip().startsWith("<!--") || raw.strip().startsWith("_")) continue;
            if (raw.startsWith("## Download")) break;
            String line = raw.replace("**", "").replace("`", "").stripTrailing();
            if (line.startsWith("#")) line = line.replaceFirst("^#+\\s*", "").toUpperCase();
            else if (line.startsWith("- ") || line.startsWith("* ")) line = "\u2022  " + line.substring(2);
            else if (line.startsWith("  - ")) line = "      \u2013  " + line.substring(4);
            lines.add(line);
        }
        return String.join("\n", lines).strip();
    }

    private static String when(String published) {
        try {
            return java.time.OffsetDateTime.parse(published).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH));
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private void checkForUpdates() {
        List<java.util.Map<String, Object>> found = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(java.time.Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES)).header("User-Agent", "Kokuhaku launcher").header("Accept", "application/vnd.github+json").timeout(java.time.Duration.ofSeconds(15)).build();
            String json = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            for (Object o : (List<Object>) new Json(json).value()) {
                java.util.Map<String, Object> r = (java.util.Map<String, Object>) o;
                if (Boolean.TRUE.equals(r.get("draft")) || number(text(r.get("tag_name"))) == null) continue;
                found.add(r);
            }
        } catch (Exception e) {
            VERSIONS[0][3] = "Couldn't check for updates. You're playing this version.";
            return;
        }
        found.sort((a, b) -> compare(number(text(b.get("tag_name"))), number(text(a.get("tag_name")))));
        int[] mine = number(LAUNCHER_VERSION);
        List<String[]> list = new ArrayList<>();
        int me = 0;
        for (java.util.Map<String, Object> r : found.subList(0, Math.min(MOST_SHOWN, found.size()))) {
            int[] v = number(text(r.get("tag_name")));
            int c = compare(v, mine);
            if (c == 0) me = list.size();
            String notes = plain(text(r.get("body")));
            java.util.regex.Matcher dated = java.util.regex.Pattern.compile("<!--\\s*date:\\s*(\\d{4}-\\d{2}-\\d{2})\\s*-->").matcher(text(r.get("body")));
            String date = dated.find() ? dated.group(1) + "T12:00:00Z" : text(r.get("published_at"));
            list.add(new String[]{"Kokuhaku " + shown(v), when(date) + (c == 0 ? "  \u00b7  this one" : c > 0 ? "  \u00b7  new" : ""), "ready", notes.isEmpty() ? "No notes for this one." : notes});
        }
        if (list.isEmpty()) {
            VERSIONS[0][3] = "This is the newest version.";
            return;
        }
        list.add(new String[]{"Next update", "1.20.1  \u00b7  WIP", "", ""});
        int pick = me;
        SwingUtilities.invokeLater(() -> {
            VERSIONS = list.toArray(new String[0][]);
            selected = pick;
            notesScroll = 0;
        });
        java.util.Map<String, Object> newest = found.get(0);
        if (compare(number(text(newest.get("tag_name"))), mine) > 0 && !"1".equals(System.getenv("KOKUHAKU_NOUPDATE"))) update(newest);
    }

    /** The new launcher and game, fetched beside this one, then started in its place. If anything goes wrong, this one carries on. */
    @SuppressWarnings("unchecked")
    private void update(java.util.Map<String, Object> release) {
        java.util.Map<String, java.util.Map<String, Object>> want = new java.util.HashMap<>();
        for (Object o : (List<Object>) release.getOrDefault("assets", List.of())) {
            java.util.Map<String, Object> a = (java.util.Map<String, Object>) o;
            String name = text(a.get("name"));
            if (name.equals("launcher.jar") || name.equals("game.zip")) want.put(name, a);
        }
        if (want.size() < 2) return;
        String version = shown(number(text(release.get("tag_name"))));
        busy = true;
        Path dir = updates(), staging = dir.resolveSibling("incoming");
        try {
            deleteAll(staging);
            Files.createDirectories(staging);
            for (var entry : want.entrySet()) {
                Path to = staging.resolve(entry.getKey());
                fetch(text(entry.getValue().get("browser_download_url")), to, "Updating to " + version);
                String digest = text(entry.getValue().get("digest"));
                if (digest.startsWith("sha256:")) {
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = Files.newInputStream(to)) {
                        byte[] buf = new byte[1 << 16];
                        int n;
                        while ((n = in.read(buf)) > 0) sha.update(buf, 0, n);
                    }
                    if (!HexFormat.of().formatHex(sha.digest()).equalsIgnoreCase(digest.substring(7))) throw new IOException(entry.getKey() + " didn't match.");
                }
            }
            Files.writeString(staging.resolve("version.txt"), version);
            deleteAll(dir);
            Files.move(staging, dir);
            say("Updating to " + version, -1f);
            if (handOver()) System.exit(0);
            throw new IOException("The new version wouldn't start.");
        } catch (Exception e) {
            try {
                deleteAll(staging);
            } catch (IOException ignored) {
            }
            busy = false;
            say("", -1f);
            VERSIONS[0][3] = "Couldn't update just now. This version still plays.\n\n" + VERSIONS[0][3];
        }
    }

    private static void deleteAll(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    /** Lines that fit the width, for the notes. */
    private static List<String> wrap(String text, java.awt.FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                out.add("");
                continue;
            }
            String indent = para.startsWith("\u2022  ") ? "    " : "";
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(tryLine) > width && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(indent + word);
                } else {
                    line = new StringBuilder(tryLine);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private boolean ready(int i) {
        return !VERSIONS[i][2].isEmpty();
    }

    private static Path data() {
        String forced = System.getenv("KOKUHAKU_PRISM_DATA");
        return forced != null ? Path.of(forced) : Path.of(System.getProperty("user.home"), "Library", "Application Support", "PrismLauncher");
    }

    private static Path instance() {
        return data().resolve("instances").resolve(INSTANCE);
    }

    private static Path game() {
        return instance().resolve(".minecraft");
    }

    /** Where Prism was last found, or picked by hand. */
    private static Path remembered() {
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Kokuhaku", "prism.txt");
    }

    private static boolean isPrism(Path app) {
        return app != null && Files.isDirectory(app) && app.getFileName().toString().toLowerCase().endsWith(".app") && app.getFileName().toString().toLowerCase().contains("prism");
    }

    private static Path findPrism() {
        Path found = lookForPrism();
        if (found != null) {
            try {
                Files.createDirectories(remembered().getParent());
                Files.writeString(remembered(), found.toString());
            } catch (IOException ignored) {
            }
        }
        return found;
    }

    private static Path lookForPrism() {
        String home = System.getProperty("user.home");
        // Picked or found before.
        try {
            Path before = Path.of(Files.readString(remembered()).trim());
            if (isPrism(before)) return before;
        } catch (Exception ignored) {
        }
        // The usual names, in the usual places.
        List<Path> places = List.of(Path.of("/Applications"), Path.of(home, "Applications"), Path.of(home, "Downloads"), Path.of(home, "Desktop"), Path.of("/Applications/Games"));
        for (Path dir : places) {
            for (String name : List.of("Prism Launcher.app", "PrismLauncher.app", "Prism.app")) {
                if (isPrism(dir.resolve(name))) return dir.resolve(name);
            }
        }
        // Spotlight knows every app on the Mac, wherever it's been put.
        for (String query : List.of("kMDItemCFBundleIdentifier == 'org.prismlauncher.PrismLauncher'", "kMDItemContentType == 'com.apple.application-bundle' && kMDItemFSName == '*prism*'cd")) {
            try {
                Process p = new ProcessBuilder("mdfind", query).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor();
                for (String line : out.split("\n")) {
                    Path app = Path.of(line.trim().isEmpty() ? "/nonexistent" : line.trim());
                    if (isPrism(app) && !app.toString().contains("/.Trash/")) return app;
                }
            } catch (Exception ignored) {
            }
        }
        // Already running: whichever app it was started from.
        java.util.Optional<String> running = ProcessHandle.allProcesses().map(p -> p.info().command().orElse("")).filter(c -> c.toLowerCase().contains("prism") && c.contains(".app/")).findFirst();
        if (running.isPresent()) {
            String c = running.get();
            Path app = Path.of(c.substring(0, c.indexOf(".app/") + 4));
            if (isPrism(app)) return app;
        }
        // Any app with Prism in its name, a folder down from the usual places, or still on its disk image.
        List<Path> wider = new ArrayList<>(places);
        try (var volumes = Files.list(Path.of("/Volumes"))) {
            volumes.forEach(wider::add);
        } catch (Exception ignored) {
        }
        for (Path dir : wider) {
            try (var walk = Files.walk(dir, 2)) {
                Path app = walk.filter(KokuhakuMac::isPrism).findFirst().orElse(null);
                if (app != null) return app;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Last of all, ask: wherever it is, they can point at it. */
    private Path pickPrism() {
        final Path[] picked = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                int answer = JOptionPane.showConfirmDialog(frame, "Couldn't find Prism Launcher.\n\nIf it's installed, show me where it is.\nIf not, get it from prismlauncher.org first.", TRUE_NAME, JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if (answer != JOptionPane.OK_OPTION) return;
                FileDialog dialog = new FileDialog(frame, "Where's Prism Launcher?", FileDialog.LOAD);
                System.setProperty("apple.awt.fileDialogForDirectories", "false");
                dialog.setDirectory("/Applications");
                dialog.setVisible(true);
                if (dialog.getFile() == null) return;
                Path app = Path.of(dialog.getDirectory(), dialog.getFile());
                // Picked something inside it: up to the .app.
                while (app != null && !app.getFileName().toString().toLowerCase().endsWith(".app")) app = app.getParent();
                if (app != null && Files.isDirectory(app)) picked[0] = app;
            });
        } catch (Exception ignored) {
        }
        if (picked[0] != null) {
            try {
                Files.createDirectories(remembered().getParent());
                Files.writeString(remembered(), picked[0].toString());
            } catch (IOException ignored) {
            }
        }
        return picked[0];
    }

    private void say(String what, float howFar) {
        status = what;
        progress = howFar;
    }

    private void tell(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, message, TRUE_NAME, JOptionPane.INFORMATION_MESSAGE));
    }

    private void launch() {
        if (busy) return;
        busy = true;
        say("Getting ready", -1f);
        new Thread(() -> {
            Path prism = findPrism();
            if (prism == null && System.getenv("KOKUHAKU_PRISM_DATA") == null) prism = pickPrism();
            if (prism == null && System.getenv("KOKUHAKU_PRISM_DATA") == null) {
                say("", -1f);
                busy = false;
                return;
            }
            try {
                install();
                ensureMods();
            } catch (Exception e) {
                tell("It couldn't install itself.\n\nQuit Prism Launcher, check you're online and try again.\n\n" + e.getMessage());
                busy = false;
                say("", -1f);
                return;
            }
            ensureMap();
            ensureShader();
            ensureJava();
            if ("1".equals(System.getenv("KOKUHAKU_NOLAUNCH")) || prism == null) System.exit(0);
            say("Starting", -1f);
            if (!quitPrism()) {
                tell("Prism Launcher is busy.\n\nQuit it, then open this again.");
                busy = false;
                return;
            }
            try {
                // -j: opened hidden, so nothing of Prism ever comes up. -g: and not in front of this.
                new ProcessBuilder("open", "-j", "-g", "-a", prism.toString(), "--args", "--launch", INSTANCE).start();
            } catch (IOException e) {
                tell("Prism Launcher wouldn't start.\n\n" + e.getMessage());
                busy = false;
                return;
            }
            say("Starting Minecraft", -1f);
            long started = System.currentTimeMillis(), seen = 0;
            while (System.currentTimeMillis() - started < GIVE_UP_MS) {
                if (gameRunning()) {
                    if (seen == 0) seen = System.currentTimeMillis();
                    if (System.currentTimeMillis() - seen > GAME_SETTLES_MS) System.exit(0);
                }
                sleep(500);
            }
            System.exit(0);
        }).start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /** The game's own Java is running: Prism starts it through its own entry point, then Fabric. */
    private static boolean gameRunning() {
        return ProcessHandle.allProcesses().anyMatch(p -> p.info().commandLine().map(c -> c.contains("KnotClient") || c.contains("org.prismlauncher.EntryPoint")).orElse(false));
    }

    /** Prism ignores a launch request while it's already open, so an idle Prism has to quit first. */
    private static boolean quitPrism() {
        boolean open = ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(c -> c.endsWith("/prismlauncher")).orElse(false));
        if (!open) return true;
        if (gameRunning()) return false;
        try {
            new ProcessBuilder("osascript", "-e", "quit app \"Prism Launcher\"").start().waitFor();
        } catch (IOException | InterruptedException e) {
            return false;
        }
        for (int i = 0; i < 24; i++) {
            if (ProcessHandle.allProcesses().noneMatch(p -> p.info().command().map(c -> c.endsWith("/prismlauncher")).orElse(false))) return true;
            sleep(250);
        }
        return false;
    }

    /** Unpacks the game into Prism, if this Mac doesn't have this version yet. */
    private void install() throws IOException {
        if (!Files.exists(pack)) return;
        try (ZipFile zip = new ZipFile(pack.toFile(), StandardCharsets.UTF_8)) {
            Path stamp = instance().resolve(".kokuhaku-version");
            String version = new String(zip.getInputStream(zip.getEntry("version.txt")).readAllBytes(), StandardCharsets.UTF_8).trim();
            if (Files.exists(stamp) && Files.readString(stamp).trim().equals(version)) return;
            say("Unpacking the game", 0f);
            // Whatever's listed for download stays, so an update doesn't fetch it all again.
            List<String> keep = new ArrayList<>();
            ZipEntry list = zip.getEntry(DOWNLOAD_LIST);
            if (list == null) list = zip.getEntry("instances/" + INSTANCE + "/" + DOWNLOAD_LIST);
            if (list != null) for (String line : new String(zip.getInputStream(list).readAllBytes(), StandardCharsets.UTF_8).split("\n")) keep.add(line.split(" ")[0].trim());
            Path mods = game().resolve("mods");
            if (Files.isDirectory(mods)) {
                try (var jars = Files.list(mods)) {
                    for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) if (!keep.contains(jar.getFileName().toString())) Files.delete(jar);
                }
            }
            // Fabric's cache in .fabric is left alone: it notices changed mods by itself.
            List<? extends ZipEntry> entries = zip.stream().toList();
            int done = 0;
            for (ZipEntry entry : entries) {
                say("Unpacking the game", ++done / (float) entries.size());
                if (entry.isDirectory() || entry.getName().equals("version.txt")) continue;
                Path target = data().resolve(entry.getName()).normalize();
                if (!target.startsWith(data())) continue;
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(stamp, version);
        }
    }

    private void fetch(String url, Path target, String what) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kokuhaku launcher").build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException(what + ": " + response.statusCode());
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1), got = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                got += read;
                say(what + (length > 0 ? "  " + got / (1024 * 1024) + " of " + length / (1024 * 1024) + " MB" : ""), length > 0 ? got / (float) length : -1f);
            }
        }
    }

    private static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Create and its addons are too big to pack in, so each Mac fetches them from Modrinth. The game can't start without them. */
    private void ensureMods() throws IOException, InterruptedException {
        Path list = instance().resolve(DOWNLOAD_LIST);
        if (!Files.exists(list)) return;
        Path mods = game().resolve("mods");
        Files.createDirectories(mods);
        for (String line : Files.readAllLines(list)) {
            String[] parts = line.trim().split(" ");
            if (parts.length != 3) continue;
            Path target = mods.resolve(parts[0]);
            if (Files.exists(target) && sha1(target).equals(parts[2])) continue;
            Path part = mods.resolve(parts[0] + ".part");
            fetch(parts[1], part, "Getting " + parts[0].split("-")[0]);
            if (!sha1(part).equals(parts[2])) {
                Files.delete(part);
                throw new IOException(parts[0] + " didn't download properly.");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The mountains are fetched too. The game still runs without them, so nothing here stops it. */
    private void ensureMap() {
        try {
            Path list = instance().resolve(MAP_LIST);
            if (!Files.exists(list)) return;
            String[] parts = Files.readString(list).trim().split(" ");
            if (parts.length != 2 || !parts[0].startsWith("http")) return;
            Path folder = game().resolve(MAP_FOLDER), stamp = folder.resolve(".version");
            if (Files.exists(stamp) && Files.readString(stamp).trim().equals(parts[1])) return;
            Path part = game().resolve("kokuhaku-map.zip.part");
            fetch(parts[0], part, "Getting the mountains");
            if (!sha1(part).equals(parts[1])) {
                Files.delete(part);
                return;
            }
            say("Unpacking the mountains", -1f);
            Files.createDirectories(folder);
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(part))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    Path target = folder.resolve(entry.getName()).normalize();
                    if (!target.startsWith(folder)) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.delete(part);
            Files.writeString(stamp, parts[1]);
        } catch (Exception ignored) {
            // Offline, or the link's gone: the town is all there is this time.
        }
    }

    /** Gets the shaderpack, and turns it on if this Mac has the memory for it. If that fails the game runs without. */
    private void ensureShader() {
        try {
            Path packs = game().resolve("shaderpacks"), file = packs.resolve(SHADER_FILE);
            Files.createDirectories(packs);
            if (!Files.exists(file)) {
                Path part = packs.resolve(SHADER_FILE + ".part");
                fetch(SHADER_URL, part, "Getting the shaders");
                if (!sha1(part).equals(SHADER_SHA1)) {
                    Files.delete(part);
                    return;
                }
                Files.move(part, file);
            }
            Path config = game().resolve("config"), iris = config.resolve("iris.properties");
            Files.createDirectories(config);
            List<String> kept = new ArrayList<>();
            if (Files.exists(iris)) for (String l : Files.readAllLines(iris)) if (!l.startsWith("shaderPack=") && !l.startsWith("enableShaders=") && !l.startsWith("maxShadowRenderDistance=")) kept.add(l);
            long gb = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() >> 30;
            kept.addAll(Arrays.asList("enableShaders=" + (gb >= SHADERS_NEED_GB), "shaderPack=" + SHADER_FILE, "maxShadowRenderDistance=8"));
            Files.write(iris, kept);
        } catch (Exception ignored) {
            // No internet, or Modrinth is down. Not worth stopping the game over.
        }
    }

    // Java 21 starts the game quicker than the 17 Prism picks for 1.20.1. Fetched once, kept beside Prism's own.
    private static final String JAVA_VERSION = "21.0.13";
    private static final String JAVA_ARM_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_aarch64_mac_hotspot_21.0.13_9.tar.gz";
    private static final String JAVA_ARM_SHA256 = "225bab0a09491ef06835488cd942f5d2befa9a0410a2484f0c8bbcf7506ec09a";
    private static final String JAVA_INTEL_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_mac_hotspot_21.0.13_9.tar.gz";
    private static final String JAVA_INTEL_SHA256 = "c909674b1c10c261b9a3fb7e9c0efebd303e33f88a4fe84be7135b5e2d711f89";
    /** Heap: more makes loading quicker, but only where the Mac can spare it. */
    private static final int ROOMY_HEAP_MB = 4096, TIGHT_HEAP_MB = 2400;

    /** The java inside a fetched Java folder, if it's there. */
    private static Path javaIn(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return null;
        try (var all = Files.walk(folder)) {
            return all.filter(p -> p.endsWith(Path.of("Contents", "Home", "bin", "java")) && Files.isExecutable(p)).findFirst().orElse(null);
        }
    }

    /** Java 21 and a heap that fits this Mac, written into the instance. If Java 21 can't be had, Prism's own Java runs it, as before. */
    private void ensureJava() {
        Path java = null;
        try {
            boolean arm = System.getProperty("os.arch", "").contains("aarch64");
            Path folder = data().resolve("java").resolve("kokuhaku-java-" + JAVA_VERSION + (arm ? "-arm" : "-intel"));
            java = javaIn(folder);
            if (java == null) {
                Files.createDirectories(folder.getParent());
                Path part = folder.getParent().resolve(folder.getFileName() + ".tar.gz.part");
                fetch(arm ? JAVA_ARM_URL : JAVA_INTEL_URL, part, "Getting Java");
                if (!sha256(part).equals(arm ? JAVA_ARM_SHA256 : JAVA_INTEL_SHA256)) {
                    Files.delete(part);
                } else {
                    say("Unpacking Java", -1f);
                    Files.createDirectories(folder);
                    Process tar = new ProcessBuilder("/usr/bin/tar", "-xzf", part.toString(), "-C", folder.toString()).redirectErrorStream(true).start();
                    tar.getInputStream().readAllBytes();
                    if (tar.waitFor() == 0) java = javaIn(folder);
                    Files.delete(part);
                }
            }
        } catch (Exception ignored) {
            java = null;
        }
        try {
            long gb = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() >> 30;
            Map<String, String> set = new LinkedHashMap<>();
            set.put("MaxMemAlloc", String.valueOf(gb >= SHADERS_NEED_GB ? ROOMY_HEAP_MB : TIGHT_HEAP_MB));
            if (java != null) {
                set.put("OverrideJavaLocation", "true");
                set.put("JavaPath", java.toString());
                // 1.20.1 only asks for 17, but runs on 21.
                set.put("IgnoreJavaCompatibility", "true");
            } else {
                set.put("OverrideJavaLocation", "false");
            }
            Path cfg = instance().resolve("instance.cfg");
            if (!Files.exists(cfg)) return;
            List<String> lines = new ArrayList<>();
            for (String l : Files.readAllLines(cfg, StandardCharsets.UTF_8)) if (set.keySet().stream().noneMatch(k -> l.startsWith(k + "="))) lines.add(l);
            set.forEach((k, v) -> lines.add(k + "=" + v));
            Files.writeString(cfg, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Prism's defaults still start it.
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private Rectangle versionBox(int i) {
        return new Rectangle(10, TITLE_H + 46 + i * 58, SIDE_W - 20, 50);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACK);
        g.fillRect(0, 0, W, H);
        g.setColor(SIDE);
        g.fillRect(0, TITLE_H, SIDE_W, H - TITLE_H);
        g.setColor(BAR);
        g.fillRect(0, 0, W, TITLE_H);
        g.setFont(small);
        g.setColor(GREY);
        if (nameImage != null) g.drawImage(nameImage, 14, 11, nameImage.getWidth() / 2, nameImage.getHeight() / 2, null);

        // Minimise and close, plain grey until you're over them.
        if (overMin) {
            g.setColor(new Color(34, 32, 36));
            g.fill(minBox);
        }
        if (overClose) {
            g.setColor(RED);
            g.fill(closeBox);
        }
        g.setStroke(new BasicStroke(1.4f));
        int cy = TITLE_H / 2, cx = closeBox.x + closeBox.width / 2;
        g.setColor(overClose ? Color.WHITE : GREY);
        g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
        g.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
        cx = minBox.x + minBox.width / 2;
        g.setColor(overMin ? PALE : GREY);
        g.drawLine(cx - 6, cy, cx + 6, cy);

        // The versions.
        g.setColor(DIM);
        g.drawString("VERSIONS", 18, TITLE_H + 30);
        for (int i = 0; i < VERSIONS.length; i++) {
            Rectangle box = versionBox(i);
            if (i == selected || i == hover && ready(i)) {
                g.setColor(i == selected ? new Color(30, 26, 31) : new Color(22, 20, 24));
                g.fillRoundRect(box.x, box.y, box.width, box.height, 12, 12);
            }
            if (i == selected) {
                g.setColor(RED);
                g.fillRect(box.x, box.y + 12, 3, box.height - 24);
            }
            g.setFont(bold);
            g.setColor(ready(i) ? PALE : DIM);
            g.drawString(VERSIONS[i][0], box.x + 14, box.y + 21);
            g.setFont(small);
            g.setColor(ready(i) ? GREY : DIM);
            g.drawString(VERSIONS[i][1], box.x + 14, box.y + 39);
        }

        // The game.
        int left = SIDE_W + 48;
        if (titleImage != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(titleImage, left - 6, TITLE_H + 50, titleImage.getWidth() / 2, titleImage.getHeight() / 2, null);
        }
        g.setFont(body);
        g.setColor(GREY);
        g.drawString("someone left a note in your locker", left, TITLE_H + 138);
        g.setFont(small);
        g.drawString("Two weeks. Find out who she is.", left, TITLE_H + 160);

        // What changed in whichever version is picked.
        String[][] versions = VERSIONS;
        String[] picked = versions[Math.min(selected, versions.length - 1)];
        g.setColor(DIM);
        g.drawString("WHAT'S NEW  \u00b7  " + picked[0].toUpperCase(), notesBox.x, notesBox.y - 12);
        g.setColor(new Color(34, 32, 36));
        g.drawLine(notesBox.x, notesBox.y - 4, notesBox.x + notesBox.width, notesBox.y - 4);
        java.awt.FontMetrics fm = g.getFontMetrics(small);
        List<String> lines = wrap(picked[3], fm, notesBox.width);
        int lineH = fm.getHeight();
        notesScroll = Math.min(notesScroll, Math.max(0, lines.size() * lineH - notesBox.height));
        java.awt.Shape was = g.getClip();
        g.clip(notesBox);
        g.setColor(GREY);
        for (int i = 0; i < lines.size(); i++) g.drawString(lines.get(i), notesBox.x, notesBox.y + fm.getAscent() + i * lineH - notesScroll);
        g.setClip(was);

        g.setColor(busy ? new Color(70, 22, 30) : overLaunch ? RED_LIT : RED);
        g.fillRoundRect(launchBox.x, launchBox.y, launchBox.width, launchBox.height, 16, 16);
        g.setFont(button);
        g.setColor(busy ? GREY : Color.WHITE);
        String label = busy ? "Starting..." : "Launch";
        g.drawString(label, launchBox.x + (launchBox.width - g.getFontMetrics().stringWidth(label)) / 2, launchBox.y + launchBox.height / 2 + 6);

        // What it's doing, and how far along.
        if (busy) {
            int x = left, y = H - 70, w = W - left - 48;
            g.setColor(new Color(34, 32, 36));
            g.fillRect(x, y, w, 4);
            g.setColor(RED);
            float p = progress;
            if (p >= 0f) g.fillRect(x, y, (int) (w * Math.min(1f, p)), 4);
            else {
                int span = w / 4, at = (int) (System.currentTimeMillis() / 3 % (w + span)) - span;
                int from = Math.max(x, x + at), to = Math.min(x + w, x + at + span);
                if (to > from) g.fillRect(from, y, to - from, 4);
            }
            g.setFont(small);
            g.setColor(GREY);
            g.drawString(status, x, H - 46);
        }
    }
}
