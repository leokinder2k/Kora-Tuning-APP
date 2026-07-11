# Android Release Readiness

Reviewed: 2026-07-11

This is the Android-specific go/no-go note for the next Google Play internal test submission.

## Ready In Repo

- Package ID: `com.leokinder2k.koratuningcompanion`
- Min SDK: 24
- Target SDK: 36
- Release build has minification and resource shrinking enabled.
- Cleartext traffic is disabled.
- Android backup is disabled; backup/data-extraction rules exclude app-local files, databases, and shared preferences.
- `RECORD_AUDIO` is the only dangerous permission found in the Android manifest.
- Microphone feature is optional, so Play should not block install on devices without a microphone.
- File sharing uses a non-exported `FileProvider` limited to cache exports.
- In-app About includes Privacy Policy and Support links.
- Store/legal/manual QA checklists exist under `docs/`.

## Manual Before Play Submission

- Complete Google Play Data safety using the final release artifact.
- Enter the public Privacy Policy URL in Play Console.
- Enter the Support URL or support email in Play Console.
- Capture final screenshots from a release/internal build.
- Run the Play internal test install and review the Play pre-launch report.
- Confirm the final version code/name shown in Play Console match the intended release.

## Validation Commands

Run from repo root:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle_user_home"
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Build a Play-uploadable signed AAB without publishing:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle_user_home"
Get-Content .\.local-signing\release-signing.env |
  Where-Object { $_ -match '^[A-Z0-9_]+=.*$' } |
  ForEach-Object {
    $parts = $_ -split '=', 2
    Set-Item -Path "Env:$($parts[0])" -Value $parts[1]
  }
.\gradlew.bat :app:bundleRelease --no-daemon
jarsigner -verify -verbose -certs "android\build\outputs\bundle\release\app-release.aab"
```

Expected signing result: `jar verified`. A self-signed upload certificate warning is expected for an Android upload key; Play App Signing verifies the configured upload certificate.

Device smoke test:

```powershell
adb devices
adb install -r "android/build/outputs/apk/debug/app-debug.apk"
adb shell monkey -p com.leokinder2k.koratuningcompanion 1
adb logcat -d -s AndroidRuntime
```
