param([switch]$InitializeSigning)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectDirectory '.signing'
$keyFile = Join-Path $signingDirectory 'dsf-finance.p12'
$passwordFile = Join-Path $signingDirectory 'password.dpapi.xml'
$javaDirectory = 'C:\Program Files\Android\Android Studio\jbr'
$keytool = Join-Path $javaDirectory 'bin\keytool.exe'
if (!(Test-Path -LiteralPath $keytool)) { throw 'Android Studio JDK not found.' }

$hasKey = Test-Path -LiteralPath $keyFile
$hasPassword = Test-Path -LiteralPath $passwordFile
if ($hasKey -ne $hasPassword) { throw 'Incomplete signing material. Restore it; never replace an existing key.' }
if (!$hasKey) {
    if (!$InitializeSigning) { throw 'Signing material not found. Use -InitializeSigning only for the first release.' }
    New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
    # Restrict private signing files to this Windows account and SYSTEM.
    $accountSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $signingAcl = [System.Security.AccessControl.DirectorySecurity]::new()
    $signingAcl.SetOwner($accountSid)
    $signingAcl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @($accountSid, [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $rule = [System.Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $signingAcl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $signingDirectory -AclObject $signingAcl
    $randomBytes = New-Object byte[] 48
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
    $signingPassword = [Convert]::ToBase64String($randomBytes)
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    ConvertTo-SecureString $signingPassword -AsPlainText -Force | Export-Clixml -LiteralPath $passwordFile
} else {
    $signingSecret = Import-Clixml -LiteralPath $passwordFile
    $signingPassword = [System.Net.NetworkCredential]::new('', $signingSecret).Password
}

$priorJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $javaDirectory
    $env:DSF_ANDROID_STORE_FILE = $keyFile
    $env:DSF_ANDROID_STORE_PASSWORD = $signingPassword
    if (!$hasKey) {
        & $keytool -genkeypair -alias dsf-finance -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -storetype PKCS12 -keystore $keyFile -storepass:env DSF_ANDROID_STORE_PASSWORD -keypass:env DSF_ANDROID_STORE_PASSWORD -dname 'CN=DSF Finance Android, OU=Mobile, O=DSF Finance' -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed. Inspect partial signing material before retrying.' }
    }
    Push-Location $projectDirectory
    try {
        & .\gradlew.bat --no-daemon :app:assembleRelease :app:lintRelease :app:testDebugUnitTest
        if ($LASTEXITCODE -ne 0) { throw 'Release checks failed.' }
    } finally { Pop-Location }
} finally {
    $env:DSF_ANDROID_STORE_PASSWORD = $null
    $env:DSF_ANDROID_STORE_FILE = $null
    $signingPassword = $null
    $signingSecret = $null
    $env:JAVA_HOME = $priorJava
}
