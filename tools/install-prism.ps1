# Adds Kokuhaku to Prism Launcher as its own instance. Your other instances and accounts aren't touched.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$data = Join-Path $env:APPDATA 'PrismLauncher'
$instance = Join-Path $data 'instances\Kokuhaku'
$u = New-Object Text.UTF8Encoding($false)

if (-not (Test-Path $data)) {
    Write-Host 'Could not find Prism Launcher. Install it, open it once, then run this again.'
    exit 1
}

Write-Host 'Copying the game...'
$game = Join-Path $instance '.minecraft'
foreach ($folder in 'mods', 'config', 'shaderpacks') {
    $target = Join-Path $game $folder
    if ($folder -eq 'mods' -and (Test-Path $target)) { Get-ChildItem $target -Filter '*.jar' | Remove-Item -Force -Confirm:$false }
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item (Join-Path $here "game\$folder\*") $target -Force
}

$cfg = @'
[General]
ConfigVersion=1.2
InstanceType=OneSix
iconKey=kokuhaku
name=Kokuhaku
OverrideConsole=true
ShowConsole=false
ShowConsoleOnError=true
AutoCloseConsole=true
OverrideMemory=true
MinMemAlloc=1024
MaxMemAlloc=2400
OverrideJavaArgs=true
JvmArgs=-XX:CICompilerCount=2 -XX:ReservedCodeCacheSize=200M
'@
[IO.File]::WriteAllText((Join-Path $instance 'instance.cfg'), $cfg.Replace("`r`n", "`n") + "`n", $u)

$pack = '{"components":[{"important":true,"uid":"net.minecraft","version":"1.20.1"},{"uid":"net.fabricmc.fabric-loader","version":"0.20.1"}],"formatVersion":1}'
[IO.File]::WriteAllText((Join-Path $instance 'mmc-pack.json'), $pack, $u)

$icons = Join-Path $data 'icons'
New-Item -ItemType Directory -Force $icons | Out-Null
Copy-Item (Join-Path $here 'kokuhaku.png') $icons -Force

Write-Host ''
Write-Host 'Done. Kokuhaku is in Prism. Open Kokuhaku.exe to play.'
