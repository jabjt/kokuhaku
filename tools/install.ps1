# Installs Kokuhaku as a profile in the official Minecraft launcher, in its own folder.
# It adds to your launcher; it doesn't touch your other profiles, mods, worlds or account.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$minecraft = Join-Path $env:APPDATA '.minecraft'
$home_ = Join-Path $env:APPDATA '.kokuhaku'
$profiles = Join-Path $minecraft 'launcher_profiles.json'
$loader = '0.20.1'
$game = '1.20.1'
$id = "fabric-loader-$loader-$game"

if (-not (Test-Path $profiles)) {
    Write-Host 'Could not find the Minecraft launcher. Open it once, then run this again.'
    exit 1
}
if (Get-Process -Name 'Minecraft*' -ErrorAction SilentlyContinue) {
    Write-Host 'Please close the Minecraft launcher first, then run this again.'
    exit 1
}

Write-Host 'Copying the game...'
New-Item -ItemType Directory -Force $home_ | Out-Null
foreach ($folder in 'mods', 'config', 'shaderpacks') {
    $target = Join-Path $home_ $folder
    if (Test-Path $target) { Get-ChildItem $target -Filter '*.jar' | Remove-Item -Force -Confirm:$false }
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item (Join-Path $here "game\$folder\*") $target -Force
}

Write-Host 'Setting up Fabric...'
$versionDir = Join-Path $minecraft "versions\$id"
New-Item -ItemType Directory -Force $versionDir | Out-Null
$json = (Invoke-WebRequest -UseBasicParsing "https://meta.fabricmc.net/v2/versions/loader/$game/$loader/profile/json").Content
[IO.File]::WriteAllText((Join-Path $versionDir "$id.json"), $json, (New-Object Text.UTF8Encoding($false)))

Write-Host 'Adding the Kokuhaku profile...'
Copy-Item $profiles "$profiles.kokuhaku-backup" -Force
$data = Get-Content $profiles -Raw -Encoding UTF8 | ConvertFrom-Json
$entry = [pscustomobject]@{
    name          = 'Kokuhaku'
    type          = 'custom'
    created       = (Get-Date).ToString('o')
    lastUsed      = (Get-Date).ToString('o')
    icon          = 'Furnace'
    lastVersionId = $id
    gameDir       = $home_
    javaArgs      = '-Xmx4G -XX:+UseG1GC'
}
if ($data.profiles.PSObject.Properties.Name -contains 'kokuhaku') {
    $data.profiles.kokuhaku = $entry
} else {
    $data.profiles | Add-Member -NotePropertyName 'kokuhaku' -NotePropertyValue $entry
}
$text = $data | ConvertTo-Json -Depth 32
[IO.File]::WriteAllText($profiles, $text, (New-Object Text.UTF8Encoding($false)))

Write-Host ''
Write-Host 'Done. Open the Minecraft launcher, pick the Kokuhaku profile and press Play.'
