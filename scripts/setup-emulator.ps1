param(
    [string]$SdkPath,
    [string]$AvdHome,
    [string]$AvdName = 'Space_API_35'
)

. (Join-Path $PSScriptRoot 'android-env.ps1')
$sdk = Get-SpaceAndroidSdk $SdkPath
if ($AvdHome) {
    New-Item -ItemType Directory -Force -Path $AvdHome | Out-Null
    $env:ANDROID_AVD_HOME = (Resolve-Path -LiteralPath $AvdHome).Path
    [Environment]::SetEnvironmentVariable('ANDROID_AVD_HOME', $env:ANDROID_AVD_HOME, 'User')
}
$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
$avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
$androidCli = Join-Path $sdk 'cmdline-tools\latest\bin\android.exe'
$image = 'system-images;android-35;default;x86_64'
if (-not (Test-Path -LiteralPath $sdkManager)) { throw "Command-line Tools missing: $sdkManager" }

if (Test-Path -LiteralPath $androidCli) {
    Invoke-SpaceNative $androidCli @('--no-metrics', "--sdk=$sdk", 'sdk', 'install', 'platform-tools', 'platforms;android-35', 'build-tools;35.0.0', 'emulator', $image)
} else {
    1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdk" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK license acceptance failed.' }
    Invoke-SpaceNative $sdkManager @("--sdk_root=$sdk", 'platform-tools', 'platforms;android-35', 'build-tools;35.0.0', 'emulator', $image)
}

$existing = @(& (Join-Path $sdk 'emulator\emulator.exe') -list-avds)
if ($LASTEXITCODE -ne 0) { throw 'Cannot list Android virtual devices.' }
if ($existing -notcontains $AvdName) {
    'no' | & $avdManager create avd --name $AvdName --package $image --device 'pixel_5'
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed.' }
}

$repo = Split-Path $PSScriptRoot -Parent
[IO.File]::WriteAllText((Join-Path $repo 'local.properties'), ('sdk.dir=' + $sdk.Replace('\', '/') + "`n"), [Text.UTF8Encoding]::new($false))
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdk, 'User')
Write-Host "AVD ready: $AvdName. Run .\scripts\debug-android.ps1"
