param(
    [string]$PackageName = "com.leokinder2k.koratuningcompanion",
    [string]$Track = "internal",
    [string]$ReleaseStatus = "draft",
    [string]$ServiceAccountJsonPath = ".\.local-signing\play-service-account.json",
    [string]$ReleaseSigningEnvPath = ".\.local-signing\release-signing.env",
    [string]$ReleaseVersionStatePath = ".\release-version.properties",
    [string]$BundlePath = ".\android\build\outputs\bundle\release\app-release.aab",
    [string]$NativeSymbolsZipPath = ".\android\build\outputs\native-debug-symbols\release\native-debug-symbols.zip"
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "release_versioning.ps1")
$selectedVersion = Resolve-ReleaseVersionSelection -VersionStatePath $ReleaseVersionStatePath
$env:VERSION_NAME = $selectedVersion.VersionName
$env:VERSION_CODE = [string]$selectedVersion.VersionCode

Write-Output "Using VERSION_NAME=$($env:VERSION_NAME) VERSION_CODE=$($env:VERSION_CODE)"

if (-not (Test-Path $ServiceAccountJsonPath)) {
    throw "Missing Play service account JSON at $ServiceAccountJsonPath"
}
if (-not (Test-Path $ReleaseSigningEnvPath)) {
    throw "Missing release signing env at $ReleaseSigningEnvPath"
}

Get-Content $ReleaseSigningEnvPath |
    Where-Object { $_ -match '^[A-Z0-9_]+=.*$' } |
    ForEach-Object {
        $parts = $_ -split '=', 2
        Set-Item -Path "Env:$($parts[0])" -Value $parts[1]
    }

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    [Convert]::ToBase64String($Bytes).TrimEnd("=") -replace "\+", "-" -replace "/", "_"
}

function New-ServiceAccountAccessToken {
    param($Account)

    $now = [DateTimeOffset]::UtcNow
    $headerJson = '{"alg":"RS256","typ":"JWT"}'
    $claimSet = @{
        iss   = $Account.client_email
        scope = "https://www.googleapis.com/auth/androidpublisher"
        aud   = $Account.token_uri
        exp   = $now.ToUnixTimeSeconds() + 3600
        iat   = $now.ToUnixTimeSeconds()
    } | ConvertTo-Json -Compress

    $header = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
    $claims = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($claimSet))
    $unsignedToken = "$header.$claims"

    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $rsa.ImportFromPem($Account.private_key)
        $signatureBytes = $rsa.SignData(
            [System.Text.Encoding]::UTF8.GetBytes($unsignedToken),
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
        )
    } finally {
        $rsa.Dispose()
    }

    $assertion = "$unsignedToken.$(ConvertTo-Base64Url $signatureBytes)"
    $tokenResponse = Invoke-RestMethod `
        -Method Post `
        -Uri $Account.token_uri `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
            assertion  = $assertion
        }

    return $tokenResponse.access_token
}

$bundleDir = Split-Path $BundlePath -Parent
if (-not (Test-Path $bundleDir)) {
    New-Item -ItemType Directory -Path $bundleDir | Out-Null
}

$nativeSymbolsDir = Split-Path $NativeSymbolsZipPath -Parent
if (-not (Test-Path $nativeSymbolsDir)) {
    New-Item -ItemType Directory -Path $nativeSymbolsDir | Out-Null
}

$mergedNativeLibsPath = ".\android\build\intermediates\merged_native_libs\release\mergeReleaseNativeLibs\out\lib\*"

./gradlew.bat :app:bundleRelease --no-daemon

if (Test-Path $NativeSymbolsZipPath) {
    Remove-Item $NativeSymbolsZipPath -Force
}
Compress-Archive -Path $mergedNativeLibsPath -DestinationPath $NativeSymbolsZipPath

if (-not (Test-Path $BundlePath)) {
    throw "Missing release bundle at $BundlePath"
}

$serviceAccount = Get-Content $ServiceAccountJsonPath -Raw | ConvertFrom-Json
$accessToken = New-ServiceAccountAccessToken -Account $serviceAccount
$headers = @{
    Authorization = "Bearer $accessToken"
}

$insertEditUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits"
$edit = Invoke-RestMethod -Method Post -Uri $insertEditUri -Headers $headers -ContentType "application/json" -Body "{}"
$editId = $edit.id

try {
    $bundleUploadUri = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PackageName/edits/$editId/bundles?uploadType=media"
    $bundleResponse = Invoke-RestMethod `
        -Method Post `
        -Uri $bundleUploadUri `
        -Headers $headers `
        -ContentType "application/octet-stream" `
        -InFile $BundlePath

    $uploadedVersionCode = [string]$bundleResponse.versionCode
    if ([string]::IsNullOrWhiteSpace($uploadedVersionCode)) {
        $uploadedVersionCode = [string]$env:VERSION_CODE
    }

    if (Test-Path $NativeSymbolsZipPath) {
        $symbolsUploadUri = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PackageName/edits/$editId/apks/$uploadedVersionCode/deobfuscationFiles/nativeCode?uploadType=media"
        Invoke-RestMethod `
            -Method Post `
            -Uri $symbolsUploadUri `
            -Headers $headers `
            -ContentType "application/octet-stream" `
            -InFile $NativeSymbolsZipPath | Out-Null
    }

    $trackBody = @{
        track = $Track
        releases = @(
            @{
                name = $env:VERSION_NAME
                versionCodes = @($uploadedVersionCode)
                status = $ReleaseStatus
            }
        )
    } | ConvertTo-Json -Depth 6 -Compress

    $trackUpdateUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$editId/tracks/$Track"
    Invoke-RestMethod `
        -Method Put `
        -Uri $trackUpdateUri `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $trackBody | Out-Null

    $commitUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/${editId}:commit"
    Invoke-RestMethod -Method Post -Uri $commitUri -Headers $headers -ContentType "application/json" -Body "{}" | Out-Null

    Save-ReleaseVersionProperties `
        -VersionStatePath $ReleaseVersionStatePath `
        -VersionName $env:VERSION_NAME `
        -VersionCode ([int]$uploadedVersionCode)

    Write-Output "Uploaded bundle and native symbols for $PackageName versionCode=$uploadedVersionCode versionName=$($env:VERSION_NAME)"
} catch {
    try {
        $deleteUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$editId"
        Invoke-RestMethod -Method Delete -Uri $deleteUri -Headers $headers | Out-Null
    } catch {
        # Ignore cleanup failure.
    }
    throw
}
