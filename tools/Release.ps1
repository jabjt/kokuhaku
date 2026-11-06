# Builds a Kokuhaku release, and publishes it once you've tried it.
#
#   tools\Release.ps1 1.4.2            builds everything into D:\Kokuhaku\release-1.4.2
#   tools\Release.ps1 1.4.2 -Publish   pushes main and puts those same files up on GitHub
#
# Before building it checks that everything is committed, that both launchers say this
# version, and that CHANGELOG.md has an entry for it: the release notes come from there.
# -Publish only ever sends files built from the commit you're on, so what goes out is
# what you tested; if you've committed since, it builds again first.
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [switch]$Publish,
    [string]$Repo = 'jackieboyjt/kokuhaku'
)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$git = 'C:\Program Files\Git\cmd\git.exe'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
$env:GIT_TERMINAL_PROMPT = '0'
$out = Join-Path (Resolve-Path '..').Path "release-$Version"
$utf8 = New-Object Text.UTF8Encoding($false)
$files = 'Kokuhaku-Windows.exe', 'Kokuhaku-Mac.tar.gz', 'launcher.jar', 'game.zip'

function Stop-With($why) { Write-Host "Stopped: $why" -ForegroundColor Red; exit 1 }

# ---- Is it ready? ----
if ($Version -notmatch '^\d+\.\d+\.\d+$') { Stop-With "$Version isn't a version like 1.4.2" }
if (& $git status --porcelain) { Stop-With 'there are uncommitted changes. Commit them first, so the release matches a commit.' }
if ((& $git branch --show-current) -ne 'main') { Stop-With 'you are not on main' }
foreach ($f in 'tools\KokuhakuLauncher.cs', 'tools\mac\KokuhakuMac.java') {
    if (-not (Select-String $f -Pattern "VERSION = `"$([regex]::Escape($Version))`"|Version = `"$([regex]::Escape($Version))`"" -Quiet)) { Stop-With "$f doesn't say $Version. Change its launcher version first." }
}
$changelog = [IO.File]::ReadAllText((Resolve-Path 'CHANGELOG.md').Path, $utf8).Replace("`r`n", "`n")
$m = [regex]::Match($changelog, "(?ms)^## $([regex]::Escape($Version)) [^\n]*\n(.*?)(?=^## |\z)")
if (-not $m.Success) { Stop-With "CHANGELOG.md has no '## $Version' entry" }
& $git fetch -q origin
if (& $git ls-remote --tags origin "v$Version") { Stop-With "v$Version is already on GitHub" }
& $git merge-base --is-ancestor origin/main main
if ($LASTEXITCODE -ne 0) { Stop-With "GitHub's main has commits you don't have. Pull them first." }
$commit = (& $git rev-parse HEAD).Trim()

# ---- Build, unless these files are already from this commit ----
$stamp = Join-Path $out 'commit.txt'
$built = (Test-Path $stamp) -and ((Get-Content $stamp -Raw).Trim() -eq $commit) -and -not ($files | Where-Object { -not (Test-Path (Join-Path $out $_)) })
if (-not $built) {
    Write-Host "Building $Version from $($commit.Substring(0, 7))..."
    & .\gradlew.bat build -q --console=plain
    if ($LASTEXITCODE -ne 0) { Stop-With 'the mod failed to build' }
    & .\tools\MakeDist.ps1
    & .\tools\MakeFriendExe.ps1
    & .\tools\MakeMacApp.ps1
    New-Item -ItemType Directory -Force $out | Out-Null
    $exe = -join ([char[]](0x2E38, 0xA66E, 0x16C9, 0x26E7, 0x16A6, 0x2E38))
    Copy-Item -LiteralPath "dist\For friends\$exe.exe" (Join-Path $out 'Kokuhaku-Windows.exe') -Force
    Copy-Item 'dist\For friends\Kokuhaku-Mac.tar.gz' (Join-Path $out 'Kokuhaku-Mac.tar.gz') -Force
    Copy-Item (Join-Path $env:TEMP 'kk\mac-build\launcher.jar') (Join-Path $out 'launcher.jar') -Force
    Copy-Item (Join-Path $env:TEMP 'kk\game.zip') (Join-Path $out 'game.zip') -Force
    $inside = [Text.Encoding]::Unicode.GetString([IO.File]::ReadAllBytes((Join-Path $out 'Kokuhaku-Windows.exe')))
    if (-not $inside.Contains($Version)) { Stop-With "the Windows launcher that came out doesn't say $Version" }
    [IO.File]::WriteAllText($stamp, $commit, $utf8)
}

# ---- Notes, from the changelog ----
$notes = "<!-- date: $((Get-Date).ToString('yyyy-MM-dd')) -->`n" + $m.Groups[1].Value.Trim() + "`n`n" + @'
## Download
- **Windows:** `Kokuhaku-Windows.exe`
- **Mac:** `Kokuhaku-Mac.tar.gz`. Double-click to unpack, then right-click the app and choose **Open**.

Already playing? You don't need to download anything: the launcher updates itself when you open it.

_`launcher.jar` and `game.zip` are what the Mac launcher fetches when it updates; you don't need them._
'@
[IO.File]::WriteAllText((Join-Path $out 'notes.md'), $notes.Replace("`r`n", "`n"), $utf8)

if (-not $Publish) {
    Write-Host "Built $Version in $out" -ForegroundColor Green
    Write-Host "Try $out\Kokuhaku-Windows.exe, then run: tools\Release.ps1 $Version -Publish"
    exit 0
}

# ---- Out it goes ----
& $git push origin main
if ($LASTEXITCODE -ne 0) { Stop-With 'pushing main failed' }
$paths = $files | ForEach-Object { Join-Path $out $_ }
& $gh release create "v$Version" -R $Repo --target $commit --title "Kokuhaku $Version" --notes-file (Join-Path $out 'notes.md') --latest @paths
if ($LASTEXITCODE -ne 0) { Stop-With 'creating the release failed' }
Write-Host "Kokuhaku $Version is out: https://github.com/$Repo/releases/tag/v$Version" -ForegroundColor Green
