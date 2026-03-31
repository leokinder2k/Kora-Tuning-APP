param(
    [string]$PackageName = "com.leokinder2k.koratuningcompanion",
    [int]$VersionCode,
    [string]$SymbolsZipPath = ".\app\build\outputs\native-debug-symbols\release\native-debug-symbols.zip",
    [string]$ServiceAccountJsonPath = ".\.local-signing\play-service-account.json"
)

$ErrorActionPreference = "Stop"

if ($VersionCode -le 0) {
    throw "Pass -VersionCode with the Play bundle version code."
}

if (-not (Test-Path $SymbolsZipPath)) {
    throw "Missing native symbols zip at $SymbolsZipPath"
}

if (-not (Test-Path $ServiceAccountJsonPath)) {
    throw "Missing Play service account JSON at $ServiceAccountJsonPath"
}

$serviceAccount = Get-Content $ServiceAccountJsonPath -Raw | ConvertFrom-Json

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

$accessToken = New-ServiceAccountAccessToken -Account $serviceAccount
$headers = @{
    Authorization = "Bearer $accessToken"
}

$insertEditUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits"
$edit = Invoke-RestMethod -Method Post -Uri $insertEditUri -Headers $headers -ContentType "application/json" -Body "{}"
$editId = $edit.id

try {
    $uploadUri = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PackageName/edits/$editId/apks/$VersionCode/deobfuscationFiles/nativeCode?uploadType=media"
    Invoke-RestMethod `
        -Method Post `
        -Uri $uploadUri `
        -Headers $headers `
        -ContentType "application/octet-stream" `
        -InFile $SymbolsZipPath | Out-Null

    $commitUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$editId:commit"
    Invoke-RestMethod -Method Post -Uri $commitUri -Headers $headers -ContentType "application/json" -Body "{}" | Out-Null

    Write-Output "Uploaded native symbols for $PackageName versionCode=$VersionCode"
} catch {
    try {
        $deleteUri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$editId"
        Invoke-RestMethod -Method Delete -Uri $deleteUri -Headers $headers | Out-Null
    } catch {
        # Ignore cleanup failures; preserve the original upload error.
    }
    throw
}
