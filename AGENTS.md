# KoraTuningSystem — Codex Agent Guide

## Project

Android app (Kotlin + Jetpack Compose) — a tuning companion for the kora (21-string West African harp).

- **Package:** `com.leokinder2k.koratuningcompanion`
- **Min SDK:** 24 | **Target SDK:** 36
- **Build system:** Gradle (Kotlin DSL), Gradle Play Publisher plugin

## Package Structure

```
app/src/main/java/com/leokinder2k/koratuningcompanion/
  MainActivity.kt
  instrumentconfig/ui/   — InstrumentConfigurationScreen.kt (bridge + tuning strip)
  livetuner/ui/          — LiveTunerScreen.kt (live pitch detection)
  livetuner/audio/       — MetronomeClickPlayer.kt
  navigation/            — KoraAuthorityApp.kt (nav host)
  scaleengine/ui/        — InstantOverviewScreen.kt (kora body canvas renderer)
  settings/              — settings dialogs
  ui/theme/              — KoraStatusColors.kt
```

## Build Commands

Always set a project-local Gradle home to avoid polluting the system cache:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle_user_home"
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Install and smoke-test on connected device:

```powershell
adb devices
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
adb shell monkey -p com.leokinder2k.koratuningcompanion 1
adb logcat -d -s AndroidRuntime
```

## Key Files

| File | Purpose |
|------|---------|
| `release-version.properties` | Current VERSION_NAME / VERSION_CODE (auto-updated on publish) |
| `app/build.gradle.kts` | Build config, signing, Play publisher config |
| `scripts/publish_internal_with_symbols.ps1` | Release build + upload to Play internal track |
| `docs/CODEX_CLAUDE_WORKFLOW.md` | Full collaboration protocol with Claude |
| `docs/CLAUDE_ACTIVE_TASK_PACKET.md` | Current Claude-owned visual task (bridge + tuner) |
| `Images/bridge-reference/` | Drop bridge photos here for visual reference tasks |

## Codex Role

- Implement code changes from Claude's task packets
- Run build, lint, tests, and device install to validate
- Report: files changed, commands run, pass/fail, blockers
- Do **not** invent visual designs — Claude owns the visual spec for the tuner strip and kora bridge

## Publishing (Internal Track)

Requires PowerShell 7 (`pwsh`) — not `powershell.exe` (5.1):

```powershell
pwsh -ExecutionPolicy Bypass -File scripts\publish_internal_with_symbols.ps1
```

This auto-increments the version code and uploads a draft to Play Console.

## Lint Suppression

`MissingTranslation` is suppressed globally — translations are managed manually across:
`values/`, `values-fr/`, `values-de/`, `values-es/`, `values-it/`, `values-hu/`,
`values-wo/`, `values-b+mnk/`
