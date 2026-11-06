# Builds dist\For friends\Kokuhaku.exe: one file with the whole game inside, which installs itself into Prism.
# Packs the mods already in dist\Kokuhaku\game\mods, so run MakeDist first to pack a newer version.
# The Complementary shaderpack is left out: its licence doesn't allow passing it on.
$ErrorActionPreference = 'Stop'
$stage = Join-Path $env:TEMP 'kk\friend-pack'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force -Confirm:$false }
$instance = Join-Path $stage 'instances\Kokuhaku'
New-Item -ItemType Directory -Force "$instance\.minecraft\mods", "$stage\icons" | Out-Null
Copy-Item 'dist\Kokuhaku\game\mods\*' "$instance\.minecraft\mods\"
Copy-Item 'tools\launcher\kokuhaku.png' "$stage\icons\"
# Create and its addons would take this past 50 MB, so the launcher fetches them instead.
$lines = foreach ($m in Get-ChildItem "$instance\.minecraft\mods" -Filter '*.jar' | Where-Object { $_.Name -match '^(create-fabric|Steam_Rails|dragonlib|createrailwaysnavigator)' }) {
    $sha = (Get-FileHash $m.FullName -Algorithm SHA1).Hash.ToLowerInvariant()
    $file = (Invoke-RestMethod "https://api.modrinth.com/v2/version_file/${sha}?algorithm=sha1").files | Where-Object { $_.hashes.sha1 -eq $sha } | Select-Object -First 1
    if (-not $file) { throw "$($m.Name) isn't on Modrinth" }
    Remove-Item $m.FullName -Confirm:$false
    "$($m.Name) $($file.url.Replace(' ', '%20')) $sha"
}
[IO.File]::WriteAllText("$instance\downloads.txt", ($lines -join "`n"), (New-Object Text.UTF8Encoding($false)))

$u = New-Object Text.UTF8Encoding($false)
$installer = [IO.File]::ReadAllText((Resolve-Path 'tools\install-prism.ps1').Path)
$cfg = [regex]::Match($installer, "(?s)\`$cfg = @'\r?\n(.*?)\r?\n'@").Groups[1].Value.Replace("`r`n", "`n")
# The name people see: symbols nobody can read. Built from codes so this script stays plain ASCII.
$trueName = -join ([char[]](0x2E38,0xA66E,0x16C9,0x26E7,0x16A6,0x2E38))
# Prism gets out of the way once the game's up, and goes when it's closed: the launcher is all anyone sees.
$misc = "OverrideMiscellaneous=true`nCloseAfterLaunch=true`nQuitAfterGameStop=true"
[IO.File]::WriteAllText("$instance\instance.cfg", $cfg.Replace("name=Kokuhaku", "name=$trueName") + "`n" + $misc + "`n", $u)
# Where the mountains come from, if they've been put somewhere: "URL SHA1".
if (Test-Path 'tools\launcher\map.txt') { Copy-Item 'tools\launcher\map.txt' "$instance\map.txt" }
$pack = [regex]::Match($installer, "\`$pack = '(.*?)'").Groups[1].Value
[IO.File]::WriteAllText("$instance\mmc-pack.json", $pack, $u)
$jar = Get-Item 'dist\Kokuhaku\game\mods\kokuhaku-0.1.0.jar'
# The mods and the settings count too, so a change to either reinstalls even when Kokuhaku's own jar hasn't changed.
$everything = ((Get-ChildItem 'dist\Kokuhaku\game\mods' -Filter '*.jar' | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Length)" }) -join ';') + $cfg
$sha = [Security.Cryptography.SHA1]::Create()
$extra = ([BitConverter]::ToString($sha.ComputeHash($u.GetBytes($everything))) -replace '-', '').Substring(0, 12).ToLowerInvariant()
[IO.File]::WriteAllText("$stage\version.txt", "$($jar.Length)-$($jar.LastWriteTimeUtc.Ticks)-$extra", $u)

$zip = Join-Path $env:TEMP 'kk\game.zip'
if (Test-Path $zip) { Remove-Item $zip -Confirm:$false }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($zip, 'Create')
foreach ($f in Get-ChildItem $stage -Recurse -File -Force) {
    $name = $f.FullName.Substring($stage.Length + 1).Replace('\', '/')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $f.FullName, $name, 'Optimal')
}
$archive.Dispose()

$out = 'dist\For friends'
New-Item -ItemType Directory -Force $out | Out-Null
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
& $csc /nologo /target:winexe "/out:$out\$trueName.exe" /win32icon:tools\launcher\kokuhaku.ico "/resource:$zip,game.zip" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Core.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:System.Web.Extensions.dll tools\KokuhakuLauncher.cs
if ($LASTEXITCODE -ne 0) { throw 'csc failed' }
if (Test-Path "$out\Kokuhaku.exe") { Remove-Item "$out\Kokuhaku.exe" -Confirm:$false }
"Built the game in $out, $([Math]::Round((Get-Item -LiteralPath "$out\$trueName.exe").Length / 1MB)) MB"

