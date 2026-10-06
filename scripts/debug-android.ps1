param(
    [string]$SdkPath,
    [string]$AvdHome,
    [string]$AvdName = 'Space_API_35',
    [switch]$Headless,
    [switch]$NoAcceleration,
    [int]$TimeoutSeconds = 600,
    [ValidateRange(5554, 5682)][int]$Port = 5554
)

. (Join-Path $PSScriptRoot 'android-env.ps1')
$sdk = Get-SpaceAndroidSdk $SdkPath
if ($AvdHome) { $env:ANDROID_AVD_HOME = (Resolve-Path -LiteralPath $AvdHome).Path }
$repo = Split-Path $PSScriptRoot -Parent
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$serial = "emulator-$Port"
$emulatorProcess = $null
if ($Port % 2 -ne 0) { throw 'Emulator console port must be even.' }
if (-not (Test-Path -LiteralPath $emulator)) { throw 'Run scripts/setup-emulator.ps1 first.' }
$avds = @(& $emulator -list-avds)
if ($LASTEXITCODE -ne 0 -or $avds -notcontains $AvdName) { throw "AVD not found: $AvdName. Run scripts/setup-emulator.ps1 first." }
Invoke-SpaceNative $adb @('start-server')
$devices = @(& $adb devices)
if ($LASTEXITCODE -ne 0) { throw 'Cannot list adb devices.' }
if (-not ($devices -match "^$serial\s+")) {
    if (-not $NoAcceleration) {
        $acceleration = & $emulator -accel-check 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'Hardware acceleration unavailable; using software emulation. Boot may take several minutes.'
            $NoAcceleration = $true
        }
    }
    $arguments = @('-avd', $AvdName, '-port', "$Port", '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader', '-memory', '2048', '-cores', '4')
    if ($Headless) { $arguments += '-no-window' }
    if ($NoAcceleration) { $arguments += @('-accel', 'off') }
    $log = Join-Path $env:TEMP "space-emulator-$Port"
    $emulatorProcess = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput "$log.out.log" -RedirectStandardError "$log.err.log" -PassThru
}

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    if ($null -ne $emulatorProcess) {
        if ($emulatorProcess.HasExited) { throw "Emulator stopped before Android booted. See $log.*.log" }
    }
    $ErrorActionPreference = 'Continue'
    try {
        $boot = & $adb -s $serial shell getprop sys.boot_completed 2>$null
        $bootExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($bootExitCode -eq 0 -and ($boot -join '').Trim() -eq '1') { break }
    if ([DateTime]::UtcNow -gt $deadline) { throw "Emulator boot timed out. See $env:TEMP\space-emulator-$Port.*.log" }
    Start-Sleep -Seconds 2
} while ($true)
$actualAvd = @(& $adb -s $serial emu avd name)
if ($LASTEXITCODE -ne 0 -or $actualAvd -notcontains $AvdName) { throw "Port $Port belongs to another AVD. Use -Port with another even port." }

Push-Location $repo
try {
    Invoke-SpaceNative (Join-Path $repo 'gradlew.bat') @('--console=plain', ':app:assembleDebug')
    Invoke-SpaceNative $adb @('-s', $serial, 'install', '-r', (Join-Path $repo 'src\app\build\outputs\apk\debug\app-debug.apk'))
    Invoke-SpaceNative $adb @('-s', $serial, 'shell', 'am', 'start', '-W', '-n', 'com.xekep.space/.MainActivity')
} finally {
    Pop-Location
}
Write-Host "Space is running on $serial. Logs: & '$adb' -s $serial logcat"
