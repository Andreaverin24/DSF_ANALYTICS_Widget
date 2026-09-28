param()
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$privateDirectory = Join-Path $projectDirectory '.signing'
$keyFile = Join-Path $privateDirectory 'dsf-finance.p12'
$sourceApk = Join-Path $projectDirectory 'app/build/outputs/apk/release/app-release.apk'
$suffix = [Guid]::NewGuid().ToString('N')
$recoveredKey = Join-Path $privateDirectory "recovery-$suffix.p12"
$testApk = Join-Path $privateDirectory "recovery-$suffix.apk"
$javaDirectory = 'C:\Program Files\Android\Android Studio\jbr'
$keytool = Join-Path $javaDirectory 'bin/keytool.exe'
$apksigner = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.0.0/apksigner.bat'
$priorJava = $env:JAVA_HOME
try {
    if (!(Test-Path -LiteralPath $sourceApk)) { throw 'Build the signed release first.' }
    $env:JAVA_HOME = $javaDirectory
    $secret = Import-Clixml -LiteralPath (Join-Path $privateDirectory 'password.dpapi.xml')
    $env:DSF_ANDROID_STORE_PASSWORD = [Net.NetworkCredential]::new('', $secret).Password
    $bytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:DSF_RECOVERY_PASSWORD = [Convert]::ToBase64String($bytes)
    [Array]::Clear($bytes, 0, $bytes.Length)
    & $keytool -importkeystore -srckeystore $keyFile -srcstoretype PKCS12 -srcstorepass:env DSF_ANDROID_STORE_PASSWORD -srcalias dsf-finance -destkeystore $recoveredKey -deststoretype PKCS12 -deststorepass:env DSF_RECOVERY_PASSWORD -destkeypass:env DSF_RECOVERY_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Recovery import failed.' }
    & $apksigner sign --ks $recoveredKey --ks-key-alias dsf-finance --ks-pass env:DSF_RECOVERY_PASSWORD --out $testApk $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'Signing with recovered key failed.' }
    $original = & $apksigner verify --print-certs $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'Original APK verification failed.' }
    $restored = & $apksigner verify --print-certs $testApk
    if ($LASTEXITCODE -ne 0) { throw 'Recovered APK verification failed.' }
    $originalFingerprint = ($original | Select-String '^Signer #1 certificate SHA-256 digest:').Line
    $restoredFingerprint = ($restored | Select-String '^Signer #1 certificate SHA-256 digest:').Line
    if (!$originalFingerprint -or $originalFingerprint -ne $restoredFingerprint) { throw 'Recovered signing identity differs.' }
    Write-Output 'PASS: recovered PKCS12 signs an APK with the same verified certificate.'
    Write-Output $originalFingerprint
} finally {
    $env:DSF_ANDROID_STORE_PASSWORD = $null
    $env:DSF_RECOVERY_PASSWORD = $null
    $env:JAVA_HOME = $priorJava
    if ($secret) { $secret.Dispose() }
    # Only these generated files in the fixed private directory are disposable.
    foreach ($temporaryFile in @($recoveredKey, $testApk, "$testApk.idsig")) {
        if (Test-Path -LiteralPath $temporaryFile) { Remove-Item -LiteralPath $temporaryFile -Force }
    }
}
