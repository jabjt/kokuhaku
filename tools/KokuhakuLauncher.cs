using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Runtime.InteropServices;
using System.Reflection;
using System.Security.Cryptography;
using System.Threading;
using System.Windows.Forms;

/// <summary>
/// Kokuhaku.exe: a dark window with a Launch button and the versions down the side.
/// Prism Launcher does the sign-in and runs the game, but it's kept out of sight:
/// everything it would show you, this shows you instead. The game itself is packed
/// inside this file; the first time it runs on a PC, or when it's a newer version,
/// it adds the Kokuhaku instance to Prism, and fetches what's too big to pack.
/// </summary>
public class KokuhakuLauncher : Form
{
    private const string Instance = "Kokuhaku";
    /// The name people see. Prism's folder keeps the plain one, so updates still find it.
    private const string TrueName = "⸸ꙮᛉ⛧ᚦ⸸";
    private const int GiveUpMs = 240000;
    /// Prism stays hidden this long; if there's still no game by then, it's probably asking something only it can.
    private const int HidePrismMs = 60000;
    /// The game with shaders peaks at about 5.7 GB, and it needs some room above that.
    private const long ShadersNeedMb = 7000;
    // Fetched from Modrinth on each PC rather than packed in here.
    private const string ShaderFile = "ComplementaryUnbound_r5.9.3.zip";
    private const string ShaderUrl = "https://cdn.modrinth.com/data/R6NEzAwj/versions/B1kyfoUZ/ComplementaryUnbound_r5.9.3.zip";
    private const string ShaderSha1 = "2ee08300e1d6f039e63eae8484dddf57b3aaaf67";
    private const string DownloadList = "downloads.txt", MapList = "map.txt", MapFolder = "kokuhaku-map";

    private static readonly Color Back = Color.FromArgb(14, 12, 15), Side = Color.FromArgb(9, 8, 10), Bar = Color.FromArgb(18, 16, 19);
    private static readonly Color Grey = Color.FromArgb(138, 138, 146), Dim = Color.FromArgb(78, 76, 84), Pale = Color.FromArgb(222, 214, 218);
    private static readonly Color Red = Color.FromArgb(176, 24, 42), RedLit = Color.FromArgb(206, 34, 54);
    private const int TitleH = 36, SideW = 210;

    /// This launcher's own version: a newer release on GitHub replaces it.
    private const string LauncherVersion = "1.4.2";
    private const string Releases = "https://api.github.com/repos/jackieboyjt/kokuhaku/releases";
    private const int MostShown = 7;

    private sealed class Version
    {
        public string Name, Detail, Notes = "";
        public bool Ready;
    }

    private Version[] Versions =
    {
        new Version { Name = "Kokuhaku " + LauncherVersion, Detail = "1.20.1  ·  this one", Ready = true, Notes = "Looking for updates..." },
        new Version { Name = "Next update", Detail = "1.20.1  ·  WIP", Ready = false },
    };
    private int notesScroll = 0;

    private readonly string prism;
    private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 50 };
    private readonly Stopwatch clock = new Stopwatch();
    private readonly Font huge = new Font("Georgia", 34f), body = new Font("Segoe UI", 10f), small = new Font("Segoe UI", 8.5f), bold = new Font("Segoe UI Semibold", 10.5f), button = new Font("Segoe UI Semibold", 13f);
    private int selected = 0, hover = -1;
    private Rectangle launchBox, minBox, closeBox;
    private bool overLaunch, overMin, overClose, busy, launched;
    private volatile string status = "";
    private volatile float progress = -1f;

    [STAThread]
    public static void Main()
    {
        Application.EnableVisualStyles();
        // What's left of the last version, once it's let go of.
        try { File.Delete(Application.ExecutablePath + ".old"); } catch (Exception) { }
        string prism = FindPrism();
        if (prism == null)
        {
            Tell("This needs Prism Launcher.\n\nInstall it from prismlauncher.org, then open this again.");
            return;
        }
        Application.Run(new KokuhakuLauncher(prism));
    }

    private static void Tell(string message)
    {
        MessageBox.Show(message, TrueName, MessageBoxButtons.OK, MessageBoxIcon.Information);
    }

    private static string Data()
    {
        return Path.Combine(Environment.GetEnvironmentVariable("APPDATA"), "PrismLauncher");
    }

    private static string Game()
    {
        return Path.Combine(Data(), "instances", Instance, ".minecraft");
    }

    /// <summary>Unpacks the game into Prism, if this PC doesn't have this version yet.</summary>
    private void Install()
    {
        using (Stream packed = Assembly.GetExecutingAssembly().GetManifestResourceStream("game.zip"))
        {
            if (packed == null) return;
            using (ZipArchive zip = new ZipArchive(packed, ZipArchiveMode.Read))
            {
                string instance = Path.Combine(Data(), "instances", Instance);
                string stamp = Path.Combine(instance, ".kokuhaku-version");
                string version;
                using (StreamReader reader = new StreamReader(zip.GetEntry("version.txt").Open())) version = reader.ReadToEnd().Trim();
                if (File.Exists(stamp) && File.ReadAllText(stamp).Trim() == version) return;
                Say("Unpacking the game", 0f);

                // Whatever's listed for download stays, so an update doesn't fetch it all again.
                ZipArchiveEntry list = zip.GetEntry(DownloadList);
                string[] keep = new string[0];
                if (list != null)
                {
                    using (StreamReader reader = new StreamReader(list.Open())) keep = reader.ReadToEnd().Split('\n').Select(l => l.Split(' ')[0].Trim()).ToArray();
                }
                string mods = Path.Combine(instance, ".minecraft", "mods");
                if (Directory.Exists(mods))
                {
                    foreach (string jar in Directory.GetFiles(mods, "*.jar"))
                    {
                        if (!keep.Contains(Path.GetFileName(jar))) File.Delete(jar);
                    }
                }
                // Fabric's cache in .fabric is left alone: it notices changed mods by itself, and where
                // Prism's folder has been moved and linked back, Fabric can't always make it again.
                int done = 0, all = zip.Entries.Count;
                foreach (ZipArchiveEntry entry in zip.Entries)
                {
                    Say("Unpacking the game", ++done / (float)all);
                    if (entry.FullName.EndsWith("/") || entry.FullName == "version.txt") continue;
                    string target = Path.Combine(Data(), entry.FullName.Replace('/', Path.DirectorySeparatorChar));
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    entry.ExtractToFile(target, true);
                }
                File.WriteAllText(stamp, version);
            }
        }
    }

    /// <summary>A file, with the bar going across as it comes in.</summary>
    private void Fetch(string url, string target, string what)
    {
        ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12;
        HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
        request.UserAgent = "Kokuhaku launcher";
        using (WebResponse response = request.GetResponse())
        using (Stream from = response.GetResponseStream())
        using (Stream to = File.Create(target))
        {
            long length = response.ContentLength, got = 0;
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = from.Read(buffer, 0, buffer.Length)) > 0)
            {
                to.Write(buffer, 0, read);
                got += read;
                Say(what + (length > 0 ? "  " + got / (1024 * 1024) + " of " + length / (1024 * 1024) + " MB" : ""), length > 0 ? got / (float)length : -1f);
            }
        }
    }

    /// <summary>
    /// Create and its addons are too big to pack in here, so each PC fetches them from Modrinth.
    /// Unlike the shaders the game can't start without these, so a failure here stops the launch.
    /// </summary>
    private void EnsureMods()
    {
        string instance = Path.Combine(Data(), "instances", Instance);
        string list = Path.Combine(instance, DownloadList);
        if (!File.Exists(list)) return;
        string mods = Path.Combine(instance, ".minecraft", "mods");
        Directory.CreateDirectory(mods);
        foreach (string line in File.ReadAllLines(list))
        {
            string[] parts = line.Trim().Split(' ');
            if (parts.Length != 3) continue;
            string target = Path.Combine(mods, parts[0]);
            if (File.Exists(target) && Sha1(target) == parts[2]) continue;
            string part = target + ".part";
            Fetch(parts[1], part, "Getting " + parts[0].Split('-')[0]);
            if (Sha1(part) != parts[2])
            {
                File.Delete(part);
                throw new IOException(parts[0] + " didn't download properly.");
            }
            File.Delete(target);
            File.Move(part, target);
        }
    }

    /// <summary>The mountains are too big to pack in here too. The game still runs without them, so a failure here doesn't stop it.</summary>
    private void EnsureMap()
    {
        string list = Path.Combine(Data(), "instances", Instance, MapList);
        if (!File.Exists(list)) return;
        string[] parts = File.ReadAllText(list).Trim().Split(' ');
        if (parts.Length != 2 || !parts[0].StartsWith("http")) return;
        string folder = Path.Combine(Game(), MapFolder), stamp = Path.Combine(folder, ".version");
        if (File.Exists(stamp) && File.ReadAllText(stamp).Trim() == parts[1]) return;
        try
        {
            string part = Path.Combine(Game(), "kokuhaku-map.zip.part");
            Fetch(parts[0], part, "Getting the mountains");
            if (Sha1(part) != parts[1])
            {
                File.Delete(part);
                return;
            }
            Say("Unpacking the mountains", -1f);
            if (Directory.Exists(folder)) Directory.Delete(folder, true);
            ZipFile.ExtractToDirectory(part, folder);
            File.Delete(part);
            File.WriteAllText(stamp, parts[1]);
        }
        catch (Exception)
        {
            // Offline, or the link's gone: the town is all there is this time.
        }
    }

    /// <summary>Gets the shaderpack and turns it on. If that fails the game just runs without it.</summary>
    private void EnsureShader()
    {
        try
        {
            string packs = Path.Combine(Game(), "shaderpacks");
            string pack = Path.Combine(packs, ShaderFile);
            Directory.CreateDirectory(packs);
            if (!File.Exists(pack))
            {
                string part = pack + ".part";
                Fetch(ShaderUrl, part, "Getting the shaders");
                if (Sha1(part) != ShaderSha1)
                {
                    File.Delete(part);
                    return;
                }
                File.Move(part, pack);
            }
            string config = Path.Combine(Game(), "config");
            string iris = Path.Combine(config, "iris.properties");
            Directory.CreateDirectory(config);
            string[] kept = File.Exists(iris)
                ? File.ReadAllLines(iris).Where(l => !l.StartsWith("shaderPack=") && !l.StartsWith("enableShaders=") && !l.StartsWith("maxShadowRenderDistance=")).ToArray()
                : new string[0];
            // Shaders are what tips a PC with little to spare into crashing, so they only go on when there's room.
            string enabled = FreeMemoryMb() >= ShadersNeedMb ? "true" : "false";
            File.WriteAllLines(iris, kept.Concat(new[] { "enableShaders=" + enabled, "shaderPack=" + ShaderFile, "maxShadowRenderDistance=8" }));
        }
        catch (Exception)
        {
            // No internet, or Modrinth is down. Not worth stopping the game over.
        }
    }

    // Java 21 starts the game quicker than the 17 Prism picks for 1.20.1. Fetched once, kept beside Prism's own.
    private const string JavaUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip";
    private const string JavaSha256 = "2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628";
    private const string JavaFolder = "kokuhaku-java-21.0.13";
    /// Heap: more makes loading quicker, but only where the PC can spare it.
    private const int RoomyHeapMb = 4096, TightHeapMb = 2400;

    /// <summary>
    /// Java 21 and a heap that fits this PC, written into the instance. If Java 21 can't be had,
    /// Prism's own Java runs it, as before.
    /// </summary>
    private void EnsureJava()
    {
        string java = null;
        try
        {
            string folder = Path.Combine(Data(), "java", JavaFolder);
            java = Directory.Exists(folder) ? Directory.GetFiles(folder, "javaw.exe", SearchOption.AllDirectories).FirstOrDefault() : null;
            if (java == null && Environment.Is64BitOperatingSystem && Environment.GetEnvironmentVariable("PROCESSOR_ARCHITECTURE") != "ARM64")
            {
                string part = folder + ".zip.part";
                Directory.CreateDirectory(Path.GetDirectoryName(part));
                Fetch(JavaUrl, part, "Getting Java");
                if (Sha256(part) != JavaSha256)
                {
                    File.Delete(part);
                }
                else
                {
                    Say("Unpacking Java", -1f);
                    string temp = folder + ".new";
                    if (Directory.Exists(temp)) Directory.Delete(temp, true);
                    ZipFile.ExtractToDirectory(part, temp);
                    File.Delete(part);
                    if (Directory.Exists(folder)) Directory.Delete(folder, true);
                    Directory.Move(temp, folder);
                    java = Directory.GetFiles(folder, "javaw.exe", SearchOption.AllDirectories).FirstOrDefault();
                }
            }
        }
        catch (Exception)
        {
            java = null;
        }
        try
        {
            var set = new Dictionary<string, string>();
            set["MaxMemAlloc"] = (FreeMemoryMb() >= ShadersNeedMb ? RoomyHeapMb : TightHeapMb).ToString();
            if (java != null)
            {
                set["OverrideJavaLocation"] = "true";
                set["JavaPath"] = java.Replace('\\', '/');
                // 1.20.1 only asks for 17, but runs on 21.
                set["IgnoreJavaCompatibility"] = "true";
            }
            else
            {
                set["OverrideJavaLocation"] = "false";
            }
            Configure(set);
        }
        catch (Exception)
        {
            // Prism's defaults still start it.
        }
    }

    /// <summary>Sets keys in the instance's instance.cfg, keeping everything else.</summary>
    private static void Configure(Dictionary<string, string> set)
    {
        string cfg = Path.Combine(Data(), "instances", Instance, "instance.cfg");
        if (!File.Exists(cfg)) return;
        var lines = File.ReadAllLines(cfg).Where(l => !set.Keys.Any(k => l.StartsWith(k + "="))).ToList();
        lines.AddRange(set.Select(kv => kv.Key + "=" + kv.Value));
        File.WriteAllText(cfg, string.Join("\n", lines) + "\n", new System.Text.UTF8Encoding(false));
    }

    private static string Sha256(string file)
    {
        using (var sha = System.Security.Cryptography.SHA256.Create())
        using (Stream s = File.OpenRead(file))
        {
            return BitConverter.ToString(sha.ComputeHash(s)).Replace("-", "").ToLowerInvariant();
        }
    }

    [StructLayout(LayoutKind.Sequential)]
    private class MemoryStatus
    {
        public uint Length = (uint)Marshal.SizeOf(typeof(MemoryStatus));
        public uint Load;
        public ulong TotalPhysical, AvailablePhysical, TotalPageFile, AvailablePageFile, TotalVirtual, AvailableVirtual, AvailableExtended;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GlobalMemoryStatusEx([In, Out] MemoryStatus status);

    private delegate bool EnumWindowsProc(IntPtr window, IntPtr data);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc each, IntPtr data);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr window);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr window, int how);

    [DllImport("user32.dll")]
    private static extern bool ReleaseCapture();

    [DllImport("user32.dll")]
    private static extern IntPtr SendMessage(IntPtr window, int message, IntPtr w, IntPtr l);

    /// <summary>What Windows can still promise, which is what runs out here, not the RAM. If it won't say, assume there's plenty.</summary>
    private static long FreeMemoryMb()
    {
        MemoryStatus status = new MemoryStatus();
        return GlobalMemoryStatusEx(status) ? (long)(status.AvailablePageFile / (1024 * 1024)) : long.MaxValue;
    }

    private static string Sha1(string file)
    {
        using (SHA1 sha = SHA1.Create())
        using (Stream stream = File.OpenRead(file))
        {
            return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
        }
    }

    private static string FindPrism()
    {
        string[] places =
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "PrismLauncher"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "PrismLauncher"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "PrismLauncher"),
        };
        foreach (string place in places)
        {
            string exe = Path.Combine(place, "prismlauncher.exe");
            if (File.Exists(exe)) return exe;
        }
        return null;
    }

    private KokuhakuLauncher(string prism)
    {
        this.prism = prism;
        Text = TrueName;
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(960, 620);
        BackColor = Back;
        DoubleBuffered = true;
        using (GraphicsPath path = Rounded(ClientRectangle, 14)) Region = new Region(path);
        minBox = new Rectangle(ClientSize.Width - 92, 0, 46, TitleH);
        closeBox = new Rectangle(ClientSize.Width - 46, 0, 46, TitleH);
        launchBox = new Rectangle(SideW + 48, ClientSize.Height - 140, 220, 54);

        timer.Tick += (sender, args) => Tick();
        timer.Start();
        new Thread(CheckForUpdates) { IsBackground = true }.Start();
        // Developer testing only: presses Launch by itself.
        if (Environment.GetEnvironmentVariable("KOKUHAKU_AUTOLAUNCH") == "1") Shown += (s, a) => Launch();
        string shot = Environment.GetEnvironmentVariable("KOKUHAKU_LAUNCHERSHOT");
        if (!string.IsNullOrEmpty(shot))
        {
            // A few seconds in, so GitHub has answered.
            System.Windows.Forms.Timer later = new System.Windows.Forms.Timer { Interval = 5000 };
            later.Tick += (s, a) =>
            {
                later.Stop();
                using (Bitmap bitmap = new Bitmap(Width, Height))
                {
                    DrawToBitmap(bitmap, new Rectangle(0, 0, Width, Height));
                    bitmap.Save(shot);
                }
                Close();
            };
            Shown += (s, a) => later.Start();
        }
    }

    // ------------------------------------------------------------------ versions and updates, from GitHub

    private sealed class Release
    {
        public System.Version Number;
        public string Tag, Notes, Date, ExeUrl, ExeDigest;
        public long ExeSize;
    }

    private static System.Version Number(string tag)
    {
        System.Version v;
        string t = (tag ?? "").TrimStart('v', 'V');
        if (t.Split('.').Length == 2) t += ".0";
        return System.Version.TryParse(t, out v) ? v : null;
    }

    private static string Get(Dictionary<string, object> d, string key)
    {
        object v;
        return d.TryGetValue(key, out v) && v != null ? v.ToString() : "";
    }

    /// <summary>Markdown, as plain lines: headings in capitals, bullets as dots.</summary>
    private static string Plain(string body)
    {
        List<string> lines = new List<string>();
        foreach (string raw in (body ?? "").Replace("\r", "").Split('\n'))
        {
            // Hidden notes, how to download it (you have), and asides: not for in here.
            if (raw.TrimStart().StartsWith("<!--") || raw.TrimStart().StartsWith("_")) continue;
            if (raw.StartsWith("## Download")) break;
            string line = raw.Replace("**", "").Replace("`", "").TrimEnd();
            if (line.StartsWith("#")) line = line.TrimStart('#', ' ').ToUpperInvariant();
            else if (line.StartsWith("- ") || line.StartsWith("* ")) line = "\u2022  " + line.Substring(2);
            else if (line.StartsWith("  - ")) line = "      \u2013  " + line.Substring(4);
            lines.Add(line);
        }
        return string.Join("\n", lines).Trim();
    }

    private static string When(string published)
    {
        DateTime d;
        return DateTime.TryParse(published, System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.AdjustToUniversal, out d) ? d.ToString("d MMM yyyy", System.Globalization.CultureInfo.InvariantCulture) : "";
    }

    /// <summary>Every release on GitHub down the side, each with what changed; and if there's a newer one, this becomes it.</summary>
    private void CheckForUpdates()
    {
        List<Release> found = new List<Release>();
        try
        {
            ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12;
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(Releases);
            request.UserAgent = "Kokuhaku launcher";
            request.Accept = "application/vnd.github+json";
            request.Timeout = 10000;
            string json;
            using (WebResponse response = request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream())) json = reader.ReadToEnd();
            object[] all = new System.Web.Script.Serialization.JavaScriptSerializer { MaxJsonLength = int.MaxValue }.DeserializeObject(json) as object[];
            foreach (object o in all ?? new object[0])
            {
                Dictionary<string, object> r = o as Dictionary<string, object>;
                if (r == null || Get(r, "draft") == "True") continue;
                System.Version number = Number(Get(r, "tag_name"));
                if (number == null) continue;
                // When it really came out, if the notes say: older versions were put up later than they were made.
                System.Text.RegularExpressions.Match dated = System.Text.RegularExpressions.Regex.Match(Get(r, "body"), @"<!--\s*date:\s*(\d{4}-\d{2}-\d{2})\s*-->");
                Release release = new Release { Number = number, Tag = Get(r, "tag_name"), Notes = Plain(Get(r, "body")), Date = When(dated.Success ? dated.Groups[1].Value + "T12:00:00Z" : Get(r, "published_at")) };
                object assets;
                if (r.TryGetValue("assets", out assets) && assets is object[])
                {
                    foreach (object a in (object[])assets)
                    {
                        Dictionary<string, object> asset = a as Dictionary<string, object>;
                        if (asset == null || !Get(asset, "name").EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) continue;
                        release.ExeUrl = Get(asset, "browser_download_url");
                        release.ExeDigest = Get(asset, "digest");
                        long size;
                        long.TryParse(Get(asset, "size"), out size);
                        release.ExeSize = size;
                    }
                }
                found.Add(release);
            }
        }
        catch (Exception)
        {
            // Offline, or GitHub is down: this version, as it is.
            BeginInvoke((Action)(() => { Versions[0].Notes = "Couldn't check for updates. You're playing this version."; }));
            return;
        }
        found.Sort((a, b) => b.Number.CompareTo(a.Number));
        System.Version mine = Number(LauncherVersion);
        List<Version> shown = new List<Version>();
        int me = 0;
        foreach (Release r in found.Take(MostShown))
        {
            int c = r.Number.CompareTo(mine);
            if (c == 0) me = shown.Count;
            shown.Add(new Version
            {
                Name = "Kokuhaku " + r.Number.ToString(3),
                Detail = r.Date + (c == 0 ? "  \u00b7  this one" : c > 0 ? "  \u00b7  new" : ""),
                Notes = r.Notes.Length > 0 ? r.Notes : "No notes for this one.",
                Ready = true,
            });
        }
        if (shown.Count == 0)
        {
            BeginInvoke((Action)(() => { Versions[0].Notes = "This is the newest version."; }));
            return;
        }
        shown.Add(new Version { Name = "Next update", Detail = "1.20.1  \u00b7  WIP", Ready = false });
        BeginInvoke((Action)(() =>
        {
            Versions = shown.ToArray();
            selected = me;
            notesScroll = 0;
        }));
        Release newest = found[0];
        if (newest.Number.CompareTo(mine) > 0 && !string.IsNullOrEmpty(newest.ExeUrl) && Environment.GetEnvironmentVariable("KOKUHAKU_NOUPDATE") != "1") Update(newest);
    }

    /// <summary>Downloads the new launcher over this one and starts it. If anything goes wrong, this one carries on.</summary>
    private void Update(Release to)
    {
        busy = true;
        string me = Application.ExecutablePath, next = me + ".new", old = me + ".old";
        string what = "Updating to " + to.Number.ToString(3);
        try
        {
            Fetch(to.ExeUrl, next, what);
            byte[] head = new byte[2];
            using (Stream s = File.OpenRead(next)) s.Read(head, 0, 2);
            if (head[0] != (byte)'M' || head[1] != (byte)'Z' || to.ExeSize > 0 && new FileInfo(next).Length != to.ExeSize) throw new IOException("The download was broken.");
            if (to.ExeDigest.StartsWith("sha256:"))
            {
                using (SHA256 sha = SHA256.Create())
                using (Stream s = File.OpenRead(next))
                {
                    string got = BitConverter.ToString(sha.ComputeHash(s)).Replace("-", "").ToLowerInvariant();
                    if (got != to.ExeDigest.Substring(7).ToLowerInvariant()) throw new IOException("The download didn't match.");
                }
            }
            Say(what, -1f);
            try { File.Delete(old); } catch (Exception) { }
            // A running program can be renamed, just not overwritten.
            File.Move(me, old);
            try
            {
                File.Move(next, me);
            }
            catch (Exception)
            {
                File.Move(old, me);
                throw;
            }
            Process.Start(new ProcessStartInfo(me) { UseShellExecute = false, WorkingDirectory = Path.GetDirectoryName(me) });
            BeginInvoke((Action)Close);
        }
        catch (Exception)
        {
            try { File.Delete(next); } catch (Exception) { }
            BeginInvoke((Action)(() =>
            {
                busy = false;
                Say("", -1f);
                Versions[0].Notes = "Couldn't update just now. This version still plays.\n\n" + Versions[0].Notes;
            }));
        }
    }

    private Rectangle NotesBox()
    {
        int left = SideW + 48;
        return new Rectangle(left, TitleH + 200, ClientSize.Width - left - 48, ClientSize.Height - TitleH - 200 - 160);
    }

    protected override void OnMouseWheel(MouseEventArgs e)
    {
        if (NotesBox().Contains(e.Location)) notesScroll = Math.Max(0, notesScroll - e.Delta / 4);
        base.OnMouseWheel(e);
    }

    private void Say(string what, float howFar)
    {
        status = what;
        progress = howFar;
    }

    private void Launch()
    {
        if (busy) return;
        busy = true;
        Say("Getting ready", -1f);
        new Thread(() =>
        {
            try
            {
                Install();
                EnsureMods();
            }
            catch (Exception e)
            {
                BeginInvoke((Action)(() =>
                {
                    Tell("It couldn't install itself.\n\nClose Prism Launcher, check you're online and try again.\n\n" + e.Message);
                    busy = false;
                    Say("", -1f);
                }));
                return;
            }
            EnsureMap();
            EnsureShader();
            EnsureJava();
            Say("Starting", -1f);
            bool free = Environment.GetEnvironmentVariable("KOKUHAKU_NOLAUNCH") == "1" || ClosePrism();
            BeginInvoke((Action)(() =>
            {
                if (!free)
                {
                    Tell("Prism Launcher is busy.\n\nClose it, then open this again.");
                    busy = false;
                    Say("", -1f);
                    return;
                }
                if (Environment.GetEnvironmentVariable("KOKUHAKU_NOLAUNCH") == "1")
                {
                    Close();
                    return;
                }
                Process.Start(new ProcessStartInfo(prism, "--launch \"" + Instance + "\"") { UseShellExecute = false, WorkingDirectory = Path.GetDirectoryName(prism) });
                launched = true;
                clock.Restart();
                Say("Starting Minecraft", -1f);
            }));
        }) { IsBackground = true }.Start();
    }

    private void Tick()
    {
        if (launched)
        {
            if (clock.ElapsedMilliseconds < HidePrismMs) HidePrism();
            if (GameIsUp() || clock.ElapsedMilliseconds > GiveUpMs) Close();
        }
        Invalidate();
    }

    /// <summary>Everything Prism opens while it gets the game going, out of sight.</summary>
    private static void HidePrism()
    {
        HashSet<uint> ids = new HashSet<uint>(Process.GetProcessesByName("prismlauncher").Select(p => (uint)p.Id));
        if (ids.Count == 0) return;
        EnumWindows((window, data) =>
        {
            uint owner;
            GetWindowThreadProcessId(window, out owner);
            if (ids.Contains(owner) && IsWindowVisible(window)) ShowWindow(window, 0);
            return true;
        }, IntPtr.Zero);
    }

    /// <summary>
    /// Prism ignores a launch request while it's already open, so an idle Prism has to close
    /// first. False if it won't, or if it's running a game we shouldn't interrupt.
    /// </summary>
    private static bool ClosePrism()
    {
        Process[] open = Process.GetProcessesByName("prismlauncher");
        if (open.Length == 0) return true;
        if (GameIsUp()) return false;
        foreach (Process p in open)
        {
            p.CloseMainWindow();
            if (!p.WaitForExit(6000)) return false;
        }
        return true;
    }

    /// <summary>The game's own window has opened.</summary>
    private static bool GameIsUp()
    {
        foreach (string name in new[] { "javaw", "java" })
        {
            // Its title is scrambled, so any Java window will do: nothing else here opens one.
            if (Process.GetProcessesByName(name).Any(p => p.MainWindowHandle != IntPtr.Zero)) return true;
        }
        return false;
    }

    private Rectangle VersionBox(int i)
    {
        return new Rectangle(10, TitleH + 46 + i * 58, SideW - 20, 50);
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        overMin = minBox.Contains(e.Location);
        overClose = closeBox.Contains(e.Location);
        overLaunch = launchBox.Contains(e.Location);
        hover = -1;
        for (int i = 0; i < Versions.Length; i++) if (VersionBox(i).Contains(e.Location)) hover = i;
        Cursor = overLaunch && !busy || hover >= 0 && Versions[hover].Ready ? Cursors.Hand : Cursors.Default;
        base.OnMouseMove(e);
    }

    protected override void OnMouseDown(MouseEventArgs e)
    {
        if (e.Button != MouseButtons.Left) return;
        if (closeBox.Contains(e.Location)) Close();
        else if (minBox.Contains(e.Location)) WindowState = FormWindowState.Minimized;
        else if (launchBox.Contains(e.Location)) Launch();
        else if (hover >= 0 && Versions[hover].Ready && !busy)
        {
            selected = hover;
            notesScroll = 0;
        }
        else if (e.Y < TitleH)
        {
            // Dragged by its top edge, like any window.
            ReleaseCapture();
            SendMessage(Handle, 0xA1, (IntPtr)2, IntPtr.Zero);
        }
    }

    private static GraphicsPath Rounded(Rectangle r, int radius)
    {
        int d = radius * 2;
        GraphicsPath path = new GraphicsPath();
        path.AddArc(r.X, r.Y, d, d, 180, 90);
        path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        Graphics g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        int w = ClientSize.Width, h = ClientSize.Height;
        using (Brush side = new SolidBrush(Side)) g.FillRectangle(side, 0, TitleH, SideW, h - TitleH);
        using (Brush bar = new SolidBrush(Bar)) g.FillRectangle(bar, 0, 0, w, TitleH);
        using (Brush grey = new SolidBrush(Grey)) g.DrawString(TrueName, small, grey, 14, 10);

        // Minimise and close, plain grey until you're over them.
        if (overMin) using (Brush lit = new SolidBrush(Color.FromArgb(34, 32, 36))) g.FillRectangle(lit, minBox);
        if (overClose) using (Brush lit = new SolidBrush(Red)) g.FillRectangle(lit, closeBox);
        using (Pen pen = new Pen(overClose ? Color.White : Grey, 1.4f))
        {
            int cx = closeBox.X + closeBox.Width / 2, cy = TitleH / 2;
            g.DrawLine(pen, cx - 5, cy - 5, cx + 5, cy + 5);
            g.DrawLine(pen, cx + 5, cy - 5, cx - 5, cy + 5);
        }
        using (Pen pen = new Pen(overMin ? Pale : Grey, 1.4f))
        {
            int cx = minBox.X + minBox.Width / 2, cy = TitleH / 2;
            g.DrawLine(pen, cx - 6, cy, cx + 6, cy);
        }

        // The versions.
        using (Brush dim = new SolidBrush(Dim)) g.DrawString("VERSIONS", small, dim, 18, TitleH + 18);
        for (int i = 0; i < Versions.Length; i++)
        {
            Rectangle box = VersionBox(i);
            if (i == selected || i == hover && Versions[i].Ready)
            {
                using (GraphicsPath path = Rounded(box, 6))
                using (Brush fill = new SolidBrush(i == selected ? Color.FromArgb(30, 26, 31) : Color.FromArgb(22, 20, 24))) g.FillPath(fill, path);
            }
            if (i == selected) using (Brush red = new SolidBrush(Red)) g.FillRectangle(red, box.X, box.Y + 12, 3, box.Height - 24);
            using (Brush name = new SolidBrush(Versions[i].Ready ? Pale : Dim)) g.DrawString(Versions[i].Name, bold, name, box.X + 14, box.Y + 7);
            using (Brush detail = new SolidBrush(Versions[i].Ready ? Grey : Dim)) g.DrawString(Versions[i].Detail, small, detail, box.X + 14, box.Y + 28);
        }

        // The game.
        int left = SideW + 48;
        using (Brush pale = new SolidBrush(Pale)) g.DrawString(TrueName, huge, pale, left - 8, TitleH + 54);
        using (Brush grey = new SolidBrush(Grey))
        {
            g.DrawString("someone left a note in your locker", body, grey, left, TitleH + 122);
            g.DrawString("Two weeks. Find out who she is.", small, grey, left, TitleH + 146);
        }

        // What changed in whichever version is picked.
        Version picked = Versions[Math.Min(selected, Versions.Length - 1)];
        Rectangle notes = NotesBox();
        using (Brush dim = new SolidBrush(Dim)) g.DrawString("WHAT'S NEW  \u00b7  " + picked.Name.ToUpperInvariant(), small, dim, notes.X, notes.Y - 22);
        using (Pen line = new Pen(Color.FromArgb(34, 32, 36))) g.DrawLine(line, notes.X, notes.Y - 4, notes.Right, notes.Y - 4);
        SizeF needed = g.MeasureString(picked.Notes, small, notes.Width);
        notesScroll = Math.Min(notesScroll, Math.Max(0, (int)needed.Height - notes.Height));
        g.SetClip(notes);
        using (Brush grey = new SolidBrush(Grey)) g.DrawString(picked.Notes, small, grey, new RectangleF(notes.X, notes.Y - notesScroll, notes.Width, needed.Height + 4));
        g.ResetClip();

        Color fillColour = busy ? Color.FromArgb(70, 22, 30) : overLaunch ? RedLit : Red;
        using (GraphicsPath path = Rounded(launchBox, 8))
        using (Brush fill = new SolidBrush(fillColour)) g.FillPath(fill, path);
        using (Brush white = new SolidBrush(busy ? Grey : Color.White))
        using (StringFormat centre = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
        {
            g.DrawString(busy ? "Starting..." : "Launch", button, white, launchBox, centre);
        }

        // What it's doing, and how far along.
        if (busy)
        {
            Rectangle track = new Rectangle(left, h - 70, w - left - 48, 4);
            using (Brush back = new SolidBrush(Color.FromArgb(34, 32, 36))) g.FillRectangle(back, track);
            using (Brush red = new SolidBrush(Red))
            {
                float p = progress;
                if (p >= 0f) g.FillRectangle(red, track.X, track.Y, (int)(track.Width * Math.Min(1f, p)), track.Height);
                else
                {
                    // Not knowing how long: a short bar going across and round again.
                    int span = track.Width / 4, at = (int)(Environment.TickCount / 3 % (track.Width + span)) - span;
                    int from = Math.Max(track.X, track.X + at), to = Math.Min(track.Right, track.X + at + span);
                    if (to > from) g.FillRectangle(red, from, track.Y, to - from, track.Height);
                }
            }
            using (Brush grey = new SolidBrush(Grey)) g.DrawString(status, small, grey, left, h - 58);
        }
    }
}
