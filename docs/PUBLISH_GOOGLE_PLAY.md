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
2. Build + publish (**requires PowerShell 7 / `pwsh`** — not Windows PowerShell 5.1):

```powershell
pwsh -ExecutionPolicy Bypass -File scripts\publish_internal_with_symbols.ps1
```

> **Note:** The upload script uses `RSA.ImportFromPem()` which requires .NET 5+.
> Always use `pwsh` (PowerShell 7+), never `powershell.exe` (5.1 / .NET Framework).

Notes:
- Versioning now defaults from `release-version.properties`
- `scripts/publish_internal_with_symbols.ps1` auto-increments the next release:
  - `1.0.12` -> `1.0.13`
  - `30000012` -> `30000013`
- Manual override is still possible with env vars or Gradle properties:
  - `VERSION_CODE` (int), `VERSION_NAME` (string)
- First upload for a new app can be slow because Play Console checks are stricter.

## CI Publishing (optional)

If you want GitHub Actions to publish to Play on tag pushes, add a repo secret with the service account JSON
and update `.github/workflows/release.yml` accordingly.
