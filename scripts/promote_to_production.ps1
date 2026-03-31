$ErrorActionPreference = 'Stop'

$signingEnv = Join-Path $PSScriptRoot "..\\.local-signing\\release-signing.env"
Get-Content $signingEnv |
  Where-Object { $_ -match '^[A-Z0-9_]+=.*$' } |
  ForEach-Object {
    $parts = $_ -split '=',2
    Set-Item -Path "Env:$($parts[0])" -Value $parts[1]
  }

$env:VERSION_NAME = '1.0.17'
$env:VERSION_CODE = '30000017'

Push-Location (Join-Path $PSScriptRoot "..")
try {
  ./gradlew.bat :app:promoteArtifact --from-track internal --promote-track production --no-daemon
} finally {
  Pop-Location
}
