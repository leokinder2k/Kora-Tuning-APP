# CI and Release Setup

This project includes:

- CI workflow: `.github/workflows/ci.yml`
- Release workflow: `.github/workflows/release.yml`

## CI workflow

`ci.yml` runs:

1. `:app:compileDebugKotlin`
2. `:app:testDebugUnitTest`
3. `:app:lintDebug`
4. `:app:connectedDebugAndroidTest` on an emulator

## Release workflow

`release.yml` builds signed release APK/AAB and uploads artifacts.

Triggers:

- Manual dispatch (`workflow_dispatch`) with `version_name` and `version_code`
- Git tag push `v*` (uses tag name and run number fallback)

## Required GitHub repository secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

## Create `ANDROID_KEYSTORE_BASE64`

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\release.keystore"))
```

Bash:

```bash
base64 -w 0 path/to/release.keystore
```

Copy the output into the `ANDROID_KEYSTORE_BASE64` secret.

## Versioning inputs

Gradle now accepts version values from:

- `-PVERSION_NAME`, `-PVERSION_CODE` or
- environment variables `VERSION_NAME`, `VERSION_CODE`.

Release signing is enabled when all signing env vars are present.
