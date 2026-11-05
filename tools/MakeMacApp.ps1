# Builds dist\For friends\Kokuhaku-Mac.tar.gz: the Mac app, with the same game inside as the Windows exe.
# Run MakeFriendExe first: this packs the game.zip that it leaves behind.
# A .tar.gz, not a .zip: a Mac keeps the mark that lets the app's start script run, and a zip made here wouldn't carry it.
$ErrorActionPreference = 'Stop'
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin'
$game = Join-Path $env:TEMP 'kk\game.zip'
if (-not (Test-Path $game)) { throw 'Run MakeFriendExe first' }
$build = Join-Path $env:TEMP 'kk\mac-build'
if (Test-Path $build) { Remove-Item $build -Recurse -Force -Confirm:$false }
New-Item -ItemType Directory -Force "$build\classes" | Out-Null

# The launcher window, for any Java from 17 up: that's what Prism fetches for this version of Minecraft.
& "$jdk\javac.exe" --release 17 -encoding UTF-8 -d "$build\classes" tools\mac\KokuhakuMac.java
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
# The name, drawn here: Windows finds those symbols in some font or other, and Java on a Mac may not.
Add-Type -AssemblyName System.Drawing
$name = -join ([char[]](0x2E38,0xA66E,0x16C9,0x26E7,0x16A6,0x2E38))
foreach ($n in @(@('title.png', 88, 222, 214, 218), @('name.png', 24, 138, 138, 146))) {
    $font = New-Object Drawing.Font('Georgia', [float]$n[1], [Drawing.GraphicsUnit]::Pixel)
    $probe = New-Object Drawing.Bitmap 1, 1; $pg = [Drawing.Graphics]::FromImage($probe)
    $size = $pg.MeasureString($name, $font); $pg.Dispose(); $probe.Dispose()
    $bmp = New-Object Drawing.Bitmap ([int][Math]::Ceiling($size.Width)), ([int][Math]::Ceiling($size.Height))
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAlias
    $brush = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, $n[2], $n[3], $n[4]))
    $g.DrawString($name, $font, $brush, 0, 0)
    $g.Dispose()
    $bmp.Save("$build\classes\$($n[0])", [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}
Set-Content "$build\manifest.txt" -Encoding ascii -Value "Main-Class: KokuhakuMac`r`n"
& "$jdk\jar.exe" cfm "$build\launcher.jar" "$build\manifest.txt" -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

# The icon: an .icns holding the same picture at two sizes, as PNGs.
Add-Type -AssemblyName System.Drawing
function Png($size) {
    $source = [Drawing.Image]::FromFile((Resolve-Path 'tools\launcher\kokuhaku.png').Path)
    $bmp = New-Object Drawing.Bitmap $size, $size
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.DrawImage($source, 0, 0, $size, $size)
    $g.Dispose(); $source.Dispose()
    $ms = New-Object IO.MemoryStream
    $bmp.Save($ms, [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    return ,$ms.ToArray()
}
function BigEndian([int]$v) { return [byte[]]@((($v -shr 24) -band 255), (($v -shr 16) -band 255), (($v -shr 8) -band 255), ($v -band 255)) }
$icns = New-Object IO.MemoryStream
$chunks = @(@('ic07', (Png 128)), @('ic08', (Png 256)), @('ic09', (Png 512)))
$total = 8; foreach ($c in $chunks) { $total += 8 + $c[1].Length }
$icns.Write([Text.Encoding]::ASCII.GetBytes('icns'), 0, 4); $icns.Write((BigEndian $total), 0, 4)
foreach ($c in $chunks) { $icns.Write([Text.Encoding]::ASCII.GetBytes($c[0]), 0, 4); $icns.Write((BigEndian (8 + $c[1].Length)), 0, 4); $icns.Write($c[1], 0, $c[1].Length) }

# The name people see: symbols nobody can read. Built from codes so this script stays plain ASCII.
$trueName = -join ([char[]](0x2E38,0xA66E,0x16C9,0x26E7,0x16A6,0x2E38))
$plist = @"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key><string>kokuhaku</string>
    <key>CFBundleIdentifier</key><string>com.kokuhaku.launcher</string>
    <key>CFBundleName</key><string>$trueName</string>
    <key>CFBundleDisplayName</key><string>$trueName</string>
    <key>CFBundleIconFile</key><string>icon</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSMinimumSystemVersion</key><string>10.15</string>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
"@
$u = New-Object Text.UTF8Encoding($false)
$script = [IO.File]::ReadAllText((Resolve-Path 'tools\mac\kokuhaku.sh').Path).Replace("`r`n", "`n")

# Every file, with where it goes and whether it runs.
$app = "$trueName.app/Contents"
$files = @(
    @{ path = "$app/Info.plist"; bytes = $u.GetBytes($plist.Replace("`r`n", "`n")); mode = 420 },
    @{ path = "$app/MacOS/kokuhaku"; bytes = $u.GetBytes($script); mode = 493 },
    @{ path = "$app/Resources/launcher.jar"; bytes = [IO.File]::ReadAllBytes("$build\launcher.jar"); mode = 420 },
    @{ path = "$app/Resources/game.zip"; bytes = [IO.File]::ReadAllBytes($game); mode = 420 },
    @{ path = "$app/Resources/icon.icns"; bytes = $icns.ToArray(); mode = 420 }
)
$dirs = @("$trueName.app/", "$app/", "$app/MacOS/", "$app/Resources/")

# A tar, written by hand so each file's permissions are exactly what the Mac needs.
function Header([string]$name, [long]$size, [int]$mode, [char]$type) {
    $h = New-Object byte[] 512
    $nameBytes = $u.GetBytes($name)
    if ($nameBytes.Length -gt 100) { throw "Name too long for tar: $name" }
    [Array]::Copy($nameBytes, $h, $nameBytes.Length)
    function Put($text, $at) { $b = [Text.Encoding]::ASCII.GetBytes($text); [Array]::Copy($b, 0, $h, $at, $b.Length) }
    Put ([Convert]::ToString($mode, 8).PadLeft(7, '0')) 100
    Put '0000000' 108; Put '0000000' 116
    Put ([Convert]::ToString($size, 8).PadLeft(11, '0')) 124
    Put ([Convert]::ToString([long]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()), 8).PadLeft(11, '0')) 136
    Put '        ' 148
    $h[156] = [byte][char]$type
    Put 'ustar' 257; Put '00' 263
    $sum = 0; foreach ($b in $h) { $sum += $b }
    Put ([Convert]::ToString($sum, 8).PadLeft(6, '0')) 148
    $h[154] = 0; $h[155] = 32
    return ,$h
}
$out = 'dist\For friends'
New-Item -ItemType Directory -Force $out | Out-Null
$target = Join-Path (Resolve-Path $out).Path 'Kokuhaku-Mac.tar.gz'
$file = [IO.File]::Create($target)
$gzip = New-Object IO.Compression.GZipStream($file, [IO.Compression.CompressionLevel]::Optimal)
foreach ($d in $dirs) { $h = Header $d 0 493 '5'; $gzip.Write($h, 0, 512) }
foreach ($f in $files) {
    $h = Header $f.path $f.bytes.Length $f.mode '0'
    $gzip.Write($h, 0, 512)
    $gzip.Write($f.bytes, 0, $f.bytes.Length)
    $pad = (512 - $f.bytes.Length % 512) % 512
    if ($pad -gt 0) { $gzip.Write((New-Object byte[] $pad), 0, $pad) }
}
$end = New-Object byte[] 1024
$gzip.Write($end, 0, 1024)
$gzip.Dispose(); $file.Dispose()
"Built the Mac app in $out, $([Math]::Round((Get-Item $target).Length / 1MB)) MB"
