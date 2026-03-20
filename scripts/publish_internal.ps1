$ErrorActionPreference = 'Stop'

$serviceAccountJson = Join-Path $PSScriptRoot "..\\.local-signing\\play-service-account.json"
if (-not (Test-Path $serviceAccountJson)) {
  throw "Missing Play service account JSON at $serviceAccountJson"
}

$signingEnv = Join-Path $PSScriptRoot "..\\.local-signing\\release-signing.env"
if (-not (Test-Path $signingEnv)) {
  throw "Missing release signing env at $signingEnv"
}

. (Join-Path $PSScriptRoot "release_versioning.ps1")
$releaseVersionStatePath = Join-Path $PSScriptRoot "..\\release-version.properties"
$selectedVersion = Resolve-ReleaseVersionSelection -VersionStatePath $releaseVersionStatePath
$env:VERSION_NAME = $selectedVersion.VersionName
$env:VERSION_CODE = [string]$selectedVersion.VersionCode

Write-Output "Using VERSION_NAME=$($env:VERSION_NAME) VERSION_CODE=$($env:VERSION_CODE)"

# Load release signing env vars (storeFile/password/alias/keyPassword)
Get-Content $signingEnv |
  Where-Object { $_ -match '^[A-Z0-9_]+=.*$' } |
  ForEach-Object {
    $parts = $_ -split '=',2
    Set-Item -Path "Env:$($parts[0])" -Value $parts[1]
  }

Push-Location (Join-Path $PSScriptRoot "..")
try {
  ./gradlew.bat :app:publishReleaseBundle --no-daemon
  Save-ReleaseVersionProperties `
    -VersionStatePath $releaseVersionStatePath `
    -VersionName $env:VERSION_NAME `
    -VersionCode ([int]$env:VERSION_CODE)
}
finally {
  Pop-Location
}
