$ErrorActionPreference = 'Stop'

$serviceAccountJson = Join-Path $PSScriptRoot "..\\.local-signing\\play-service-account.json"
if (-not (Test-Path $serviceAccountJson)) {
  throw "Missing Play service account JSON at $serviceAccountJson"
}

$signingEnv = Join-Path $PSScriptRoot "..\\.local-signing\\release-signing.env"
if (-not (Test-Path $signingEnv)) {
  throw "Missing release signing env at $signingEnv"
}

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
}
finally {
  Pop-Location
}

