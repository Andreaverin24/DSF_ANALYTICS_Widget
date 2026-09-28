param([Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$keyFile = Join-Path $projectDirectory '.signing\dsf-finance.p12'
$passwordFile = Join-Path $projectDirectory '.signing\password.dpapi.xml'
$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists. No signing backup will be overwritten.' }
$backupSecret = Read-Host 'Choose a password for the portable signing backup (save it in your password manager)' -AsSecureString
$confirmSecret = Read-Host 'Repeat backup password' -AsSecureString
try {
    $env:DSF_BACKUP_PASSWORD = [Net.NetworkCredential]::new('', $backupSecret).Password
    if ($env:DSF_BACKUP_PASSWORD.Length -lt 12 -or $env:DSF_BACKUP_PASSWORD -ne [Net.NetworkCredential]::new('', $confirmSecret).Password) {
        throw 'Passwords must match and contain at least 12 characters.'
    }
    $env:DSF_ANDROID_STORE_PASSWORD = [Net.NetworkCredential]::new('', (Import-Clixml -LiteralPath $passwordFile)).Password
    & $keytool -importkeystore -srckeystore $keyFile -srcstoretype PKCS12 -srcstorepass:env DSF_ANDROID_STORE_PASSWORD -srcalias dsf-finance -destkeystore $Destination -deststoretype PKCS12 -deststorepass:env DSF_BACKUP_PASSWORD -destkeypass:env DSF_BACKUP_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Signing backup export failed.' }
    & $keytool -list -keystore $Destination -storetype PKCS12 -storepass:env DSF_BACKUP_PASSWORD -alias dsf-finance
    if ($LASTEXITCODE -ne 0) { throw 'Signing backup verification failed.' }
} finally {
    $env:DSF_ANDROID_STORE_PASSWORD = $null
    $env:DSF_BACKUP_PASSWORD = $null
    $backupSecret.Dispose()
    $confirmSecret.Dispose()
}
