# Builds dist\Kokuhaku: everything a player needs, plus an installer for the official Minecraft launcher.
# Run from the project folder, after gradlew build. Needs an internet connection for the mods.
$ErrorActionPreference = 'Stop'
$dist = 'dist\Kokuhaku'
$game = "$dist\game"
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force -Confirm:$false }
New-Item -ItemType Directory -Force "$game\mods", "$game\config", "$game\shaderpacks" | Out-Null

Copy-Item 'build\libs\kokuhaku-0.1.0.jar' "$game\mods\"
$wanted = @(
    @{ project = 'fabric-api'; version = '0.92.12+1.20.1' },
    @{ project = 'sodium'; version = 'mc1.20.1-0.5.8' },
    @{ project = 'iris'; version = '1.7.0+1.20.1' },
    @{ project = 'indium'; version = '1.0.30+mc1.20.4' },
    @{ project = 'continuity'; version = '3.0.0-beta.5+1.20.1' },
    @{ project = 'e4mc'; version = '6.2.3-fabric' },
    @{ project = 'create-fabric'; version = '6.0.8.1+build.1744-mc1.20.1' },
    @{ project = 'create-steam-n-rails'; version = '1.7.3+fabric-mc1.20.1' },
    @{ project = 'dragonlib'; version = '3.0.29+1.20.1-fabric' },
    @{ project = 'create-railways-navigator'; version = '1.20.1-beta-0.10.0-C6' },
    # Faster to load and lighter to run.
    @{ project = 'immediatelyfast'; version = '1.5.5+1.20.4-fabric' },
    @{ project = 'lazydfu'; version = '0.1.3' },
    @{ project = 'ferrite-core'; version = '6.0.1' },
    # Walls muffle sound, and rooms echo.
    @{ project = 'sound-physics-remastered'; version = 'fabric-1.20.1-1.5.1' }
)
# Trep's Cars and GeckoLib aren't on Modrinth under a version this can pin, so they come from libs.
Copy-Item 'libs\*.jar' "$game\mods\"
foreach ($m in $wanted) {
    $versions = Invoke-RestMethod "https://api.modrinth.com/v2/project/$($m.project)/version"
    # Some mods publish Forge and Fabric builds under one version number.
    $v = $versions | Where-Object { $_.version_number -eq $m.version -and $_.loaders -contains 'fabric' } | Select-Object -First 1
    if (-not $v) { throw "No $($m.project) $($m.version) on Modrinth" }
    $file = $v.files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $file) { $file = $v.files[0] }
    Invoke-WebRequest $file.url -OutFile "$game\mods\$($file.filename)"
    "  $($file.filename)"
}

# Complementary's licence doesn't allow redistributing it, so it's only ever copied from this machine:
# leave it out of anything you share, and have players download it themselves.
if (Test-Path 'run\shaderpacks\ComplementaryUnbound_r5.9.3.zip') {
    Copy-Item 'run\shaderpacks\ComplementaryUnbound_r5.9.3.zip' "$game\shaderpacks\"
}
Copy-Item 'run\config\iris.properties' "$game\config\"

Copy-Item 'tools\install.ps1' "$dist\install.ps1"
Copy-Item 'tools\install-prism.ps1' "$dist\install-prism.ps1"
Copy-Item 'tools\launcher\kokuhaku.png', 'tools\launcher\Kokuhaku.exe' "$dist\"
Set-Content "$dist\Install for Prism.bat" -Encoding ascii -Value "@echo off`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0install-prism.ps1`"`r`npause"
Set-Content "$dist\Install Kokuhaku.bat" -Encoding ascii -Value "@echo off`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0install.ps1`"`r`npause"
"Built $dist"
