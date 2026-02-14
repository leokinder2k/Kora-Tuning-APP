# Kora Tuning Companion

Android app for chromatic kora players with instrument configuration, scale planning, guided setup, instant overview, and live tuning.

## Specification Coverage

This project implements the features defined in `SPEC.md`:

1. Instrument Configuration
- 21/22 string support
- Open pitch entry with automatic closed pitch calculation (`+1` semitone)
- Per-string open/closed intonation (cents)
- Saved profile persistence

2. Scale Calculation Engine
- Root note + scale selection
- Lever-only and peg-correct outputs
- String-role-aware mapping and conflict handling
- Full per-string output (left/right bass to high)

3. Guided Setup Mode
- Step-by-step per-string tuning flow
- String ID, target pitch, lever state, peg adjustment, progress

4. Instant Overview Mode
- Full layout view
- Diagram and table views
- Lever and peg retune indicators

5. Live Tuner
- Real-time pitch detection workflow
- Closest string/target matching
- Cent deviation feedback
- Lever/peg requirement display

6. Traditional Presets
- Built-in traditional tuning presets
- 21/22 string support
- Load preset into active instrument profile

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Android Gradle Plugin `9.0.1`
- Kotlin Compose plugin `2.3.10`
- Gradle wrapper `9.2.1`
- AndroidX DataStore for local persistence

## Project Structure

- `app/src/main/java/com/leokinder2k/koratuningcompanion/instrumentconfig`
- `app/src/main/java/com/leokinder2k/koratuningcompanion/scaleengine`
- `app/src/main/java/com/leokinder2k/koratuningcompanion/livetuner`
- `app/src/main/java/com/leokinder2k/koratuningcompanion/navigation`
- `docs/` additional release, CI, and validation guides

## Requirements

- JDK 21 (matches CI)
- Android SDK with API 36 platform installed
- Android Studio (latest stable recommended)
- Device/emulator on Android 7.0+ (minSdk 24)

## Build and Run

### Windows (PowerShell)

```powershell
./gradlew.bat :app:assembleDebug --no-daemon
```

Install APK from:

- `app/build/outputs/apk/debug/app-debug.apk`

### macOS/Linux

```bash
./gradlew :app:assembleDebug --no-daemon
```

## Quality Commands

```powershell
./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon
```

Instrumented tests (emulator/device required):

```powershell
./gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

## Release and Publishing

Version can be provided via:

- Gradle properties: `-PVERSION_NAME`, `-PVERSION_CODE`
- Environment: `VERSION_NAME`, `VERSION_CODE`

Release signing env vars:

- `ANDROID_SIGNING_STORE_FILE`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

Build release artifacts:

```powershell
./gradlew.bat :app:clean :app:bundleRelease :app:assembleRelease --no-daemon
```

Google Play internal publishing helper:

- `scripts/publish_internal.ps1`

See:

- `docs/PUBLISH_GOOGLE_PLAY.md`
- `docs/CI_RELEASE_SETUP.md`

## Localization

Resource folders include:

- `values` (default)
- `values-de`, `values-es`, `values-fr`, `values-hu`, `values-it`, `values-wo`, `values-b+mnk`

`MissingTranslation` lint is disabled to allow incremental localization without blocking builds.

## Privacy

Privacy policy:

- `PRIVACY_POLICY.md`

Summary:
- Microphone audio is processed on-device for tuning.
- No personal data collection or ad/analytics SDKs.

## License

This project is licensed under the terms in `LICENSE`.
