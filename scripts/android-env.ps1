Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-SpaceAndroidSdk {
    param([string]$SdkPath)
    if (-not $SdkPath) {
        $candidates = @($env:ANDROID_HOME, [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User'), (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
        $SdkPath = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
    }
    if (-not $SdkPath) { throw 'Android SDK not found. Install Command-line Tools and set ANDROID_HOME.' }
    if (-not (Test-Path -LiteralPath $SdkPath)) {
        throw "Android SDK not found: $SdkPath. Install Android SDK Command-line Tools first."
    }
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkPath).Path
    if (-not $env:ANDROID_AVD_HOME) {
        $configuredAvdHome = [Environment]::GetEnvironmentVariable('ANDROID_AVD_HOME', 'User')
        if ($configuredAvdHome) { $env:ANDROID_AVD_HOME = $configuredAvdHome }
    }
    return $env:ANDROID_HOME
}

function Invoke-SpaceNative {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with code $LASTEXITCODE" }
}
