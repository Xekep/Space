param(
    [Parameter(Mandatory=$true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$VersionName,
    [Parameter(Mandatory=$true)][ValidateRange(1,2100000000)][int]$VersionCode,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-fA-F:]{64,95}$')][string]$AppSigningCertificateSha256,
    [string]$BuildTools = "$env:ANDROID_HOME\build-tools\35.0.1"
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
foreach ($key in @('SPACE_KEYSTORE_PATH','SPACE_KEYSTORE_PASSWORD','SPACE_KEY_ALIAS','SPACE_KEY_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key))) { throw "Missing $key. Configure locally; never paste credentials into chat." }
}
if (!(Test-Path -LiteralPath $env:SPACE_KEYSTORE_PATH -PathType Leaf)) { throw 'Signing keystore is missing' }
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease "-PversionName=$VersionName" "-PversionCode=$VersionCode" --no-parallel --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw 'Release checks failed' }
    $packageDir=Join-Path $projectRoot "dist\stores\$VersionName-$VersionCode"
    if (Test-Path -LiteralPath $packageDir) { throw "Output already exists: $packageDir. Review it before rerunning." }
    New-Item -ItemType Directory -Path $packageDir | Out-Null
    $apk=Join-Path $packageDir 'Space-RuStore.apk'
    Copy-Item -LiteralPath 'src/app/build/outputs/apk/release/app-release.apk' -Destination $apk
    Copy-Item -LiteralPath 'src/app/build/outputs/bundle/release/app-release.aab' -Destination (Join-Path $packageDir 'Space-GooglePlay.aab')
    & python scripts/check-store-package.py $apk --build-tools $BuildTools --expected-cert $AppSigningCertificateSha256 --report (Join-Path $packageDir 'package-check.json')
    if ($LASTEXITCODE -ne 0) { throw 'APK identity/alignment check failed. Do not upload these artifacts.' }
    $aab=Join-Path $packageDir 'Space-GooglePlay.aab'
    # Optional distinct Play upload key. RuStore always retains the app-signing key above.
    $uploadKeys=@('SPACE_PLAY_UPLOAD_KEYSTORE_PATH','SPACE_PLAY_UPLOAD_KEYSTORE_PASSWORD','SPACE_PLAY_UPLOAD_KEY_ALIAS','SPACE_PLAY_UPLOAD_KEY_PASSWORD')
    $uploadValues=@($uploadKeys | ForEach-Object { [Environment]::GetEnvironmentVariable($_) })
    $provided=@($uploadValues | Where-Object { ![string]::IsNullOrWhiteSpace($_) }).Count
    if ($provided -ne 0 -and $provided -ne 4) { throw 'Set all four SPACE_PLAY_UPLOAD_* variables or none' }
    if ($provided -eq 4) {
        $appKeys=@('SPACE_KEYSTORE_PATH','SPACE_KEYSTORE_PASSWORD','SPACE_KEY_ALIAS','SPACE_KEY_PASSWORD')
        $originalValues=@($appKeys | ForEach-Object { [Environment]::GetEnvironmentVariable($_) })
        try {
            for ($i=0;$i -lt 4;$i++) { [Environment]::SetEnvironmentVariable($appKeys[$i],$uploadValues[$i],'Process') }
            & .\gradlew.bat :app:bundleRelease "-PversionName=$VersionName" "-PversionCode=$VersionCode" --no-parallel --max-workers=2
            if ($LASTEXITCODE -ne 0) { throw 'Play upload-key bundle failed' }
            Copy-Item -LiteralPath 'src/app/build/outputs/bundle/release/app-release.aab' -Destination $aab -Force
        } finally {
            for ($i=0;$i -lt 4;$i++) { [Environment]::SetEnvironmentVariable($appKeys[$i],$originalValues[$i],'Process') }
        }
    }
    $verification=& "$env:JAVA_HOME\bin\jarsigner.exe" -J-Duser.language=en -verify $aab 2>&1
    if ($LASTEXITCODE -ne 0 -or ($verification -join "`n") -notmatch 'jar verified') { throw 'AAB signature verification failed' }
    $bundleCertificate=& "$env:JAVA_HOME\bin\keytool.exe" -J-Duser.language=en -printcert -jarfile $aab 2>&1
    if ($LASTEXITCODE -ne 0 -or ($bundleCertificate -join "`n") -match 'CN=Android Debug') { throw 'AAB certificate is missing or a debug identity' }
    $bundleCertificate | Set-Content -LiteralPath (Join-Path $packageDir 'bundle-certificate.txt')
    Copy-Item -LiteralPath 'docs/store-kit' -Destination (Join-Path $packageDir 'store-kit') -Recurse
    Get-ChildItem -LiteralPath $packageDir -File | Where-Object { $_.Extension -in '.apk','.aab' } | ForEach-Object {
        $hash=Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        "$($hash.Hash.ToLower())  $($_.Name)"
    } | Set-Content -LiteralPath (Join-Path $packageDir 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "Prepared local packages: $packageDir. No store upload performed. Confirm the RuStore APK uses the actual Play app-signing identity."
} finally { Pop-Location }
