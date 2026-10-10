param([Parameter(Mandatory=$true)][string]$Serial,[ValidateSet('before','after')][string]$Stage='after',[string]$Adb="$env:ANDROID_HOME\platform-tools\adb.exe")
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$reportDir=Join-Path $projectRoot ("dist\phone-check\"+(Get-Date -Format 'yyyyMMdd-HHmmss')+"-$Stage")
New-Item -ItemType Directory -Path $reportDir | Out-Null
# Read-only capture. No reset, install, restart, deletion or device identifiers in exported report.
& $Adb -s $Serial shell getprop ro.product.model | Set-Content (Join-Path $reportDir 'model.txt')
& $Adb -s $Serial shell getprop ro.build.version.release | Set-Content (Join-Path $reportDir 'android.txt')
& $Adb -s $Serial shell dumpsys gfxinfo com.xekep.space framestats | Set-Content (Join-Path $reportDir 'frames.txt')
& $Adb -s $Serial shell dumpsys meminfo com.xekep.space | Set-Content (Join-Path $reportDir 'memory.txt')
& $Adb -s $Serial shell dumpsys battery | Set-Content (Join-Path $reportDir 'battery.txt')
& $Adb -s $Serial shell dumpsys thermalservice | Set-Content (Join-Path $reportDir 'thermal.txt')
$gamePid=(@(& $Adb -s $Serial shell pidof com.xekep.space) -join ' ').Trim()
if ($gamePid -match '^\d+$') { & $Adb -s $Serial logcat -d "--pid=$gamePid" | Set-Content (Join-Path $reportDir 'game-log.txt') }
Write-Output $reportDir
