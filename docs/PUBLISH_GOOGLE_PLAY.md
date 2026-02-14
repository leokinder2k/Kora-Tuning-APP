# Publish To Google Play

This repo is set up to publish **AAB**s to Google Play **Internal testing** via the Gradle Play Publisher plugin.

## What You Must Do In Play Console (one-time)

You must do these steps yourself (requires your Google account):

1. Create the app in Play Console
   - Package name must be: `com.leokinder2k.koratuningcompanion`
2. Enable Play App Signing
   - Upload key certificate: `.local-signing/upload_cert.pem`
3. Create a Service Account + API access
   - Play Console: `Setup -> API access`
   - Create/link a Google Cloud project
   - Create a Service Account
   - Download a JSON key file
   - Grant that service account access to your app (at least release management)

## Local Publishing (internal track)

1. Place your service account key at:
   - `.local-signing/play-service-account.json`
2. Build + publish:

```powershell
$ErrorActionPreference = 'Stop'

# Load signing env vars for release signing
Get-Content '.local-signing/release-signing.env' |
  Where-Object { $_ -match '^[A-Z0-9_]+=.*$' } |
  ForEach-Object {
    $parts = $_ -split '=',2
    Set-Item -Path "Env:$($parts[0])" -Value $parts[1]
  }

./gradlew.bat :app:publishReleaseBundle --no-daemon
```

Notes:
- Versioning is controlled by env vars or Gradle properties:
  - `VERSION_CODE` (int), `VERSION_NAME` (string)
- First upload for a new app can be slow because Play Console checks are stricter.

## CI Publishing (optional)

If you want GitHub Actions to publish to Play on tag pushes, add a repo secret with the service account JSON
and update `.github/workflows/release.yml` accordingly.

