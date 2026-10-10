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
    param([string]$Executable, [string[]]$Arguments, [Parameter(ValueFromPipeline=$true)][string]$InputText)
    $previousNativePreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        if ([IO.Path]::GetExtension($Executable) -in '.bat','.cmd') {
            # Legacy Windows PowerShell strips unnecessary quotes from native arguments.
            # Keep semicolon SDK package IDs inside explicit cmd quotes.
            $allArguments=@($Executable)+$Arguments
            foreach ($argument in $allArguments) {
                if ($argument -match '["%\r\n]') { throw 'Unsupported character in SDK command argument' }
            }
            $quoted=$allArguments | ForEach-Object { '"'+$_+'"' }
            $command='"'+($quoted -join ' ')+'"'
            if ($PSBoundParameters.ContainsKey('InputText')) { $InputText | & $env:ComSpec /d /s /c $command }
            else { & $env:ComSpec /d /s /c $command }
        } else { & $Executable @Arguments }
        $nativeExitCode=$LASTEXITCODE
    } finally { $ErrorActionPreference=$previousNativePreference }
    if ($nativeExitCode -ne 0) { throw "$Executable exited with code $nativeExitCode" }
}
