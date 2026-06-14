# KoraTuningSystem - Codex Agent Guide

## Project

Kotlin Multiplatform tuning companion for the kora (21-string West African harp).

- **Package:** `com.leokinder2k.koratuningcompanion`
- **Android:** Kotlin + Jetpack Compose, Min SDK 24, Target SDK 36
- **iOS:** Kotlin/Native shared framework + SwiftUI host generated with XcodeGen
- **Build system:** Gradle (Kotlin DSL), Kotlin Multiplatform, Gradle Play Publisher, XcodeGen

## Cross-App Changes

- When the user asks for a change to the Android app, also inspect the sibling `Kora-Tuning-iOS-Swift/` repository and make the corresponding iOS change where appropriate.
- If the Android change has no appropriate iOS counterpart, mention that explicitly in the final response.

## Package Structure

```
android/src/main/java/com/leokinder2k/koratuningcompanion/
  MainActivity.kt

shared/src/commonMain/kotlin/com/leokinder2k/koratuningcompanion/
  navigation/             - KoraAuthorityApp.kt (shared root app)
  instrumentconfig/ui/    - bridge + tuning strip
  livetuner/ui/           - live pitch detection
  notation/ui/            - notation import/export flow
  scaleengine/ui/         - kora body canvas renderer
  settings/               - settings dialogs
  ui/theme/               - shared Material theme

shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/
  MainViewController.kt   - shared Compose entrypoint for SwiftUI host
  notation/ui/            - iOS notation bridge and file sharing

apple/
  project.yml             - XcodeGen source config
  iosApp/                 - SwiftUI host app
  kora_engine/            - bundled notation bridge resources
```

## Build Commands

Always set a project-local Gradle home to avoid polluting the system cache:

```bash
export GRADLE_USER_HOME="$PWD/.gradle_user_home"
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Build the shared iOS framework and generate the Xcode project:

```bash
export GRADLE_USER_HOME="$PWD/.gradle_user_home"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --no-daemon
cd apple
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build/ios-derived CODE_SIGNING_ALLOWED=NO build
```

Install and smoke-test on a connected Android device:

```bash
adb devices
adb install -r "android/build/outputs/apk/debug/app-debug.apk"
adb shell monkey -p com.leokinder2k.koratuningcompanion 1
adb logcat -d -s AndroidRuntime
```

## Key Files

| File | Purpose |
|------|---------|
| `release-version.properties` | Current VERSION_NAME / VERSION_CODE (auto-updated on publish) |
| `android/build.gradle.kts` | Android build config |
| `shared/build.gradle.kts` | Shared Kotlin Multiplatform build config |
| `apple/project.yml` | iOS XcodeGen project config |
| `scripts/publish_internal_with_symbols.ps1` | Release build + upload to Play internal track |
| `docs/CODEX_CLAUDE_WORKFLOW.md` | Full collaboration protocol with Claude |
| `docs/CLAUDE_ACTIVE_TASK_PACKET.md` | Current Claude-owned visual task (bridge + tuner) |
| `Images/bridge-reference/` | Drop bridge photos here for visual reference tasks |

## Git Workflow

- Commit and push each coherent piece of completed work as you go, without waiting for an explicit prompt.
- Before committing, run the relevant verification commands for the project you changed.
- Do not stage untracked editor, IDE, cache, virtualenv, dependency, build, or machine-local files unless the user explicitly asks for them.
- Treat `.env*`, `.gradle_user_home/`, `.gradle-user/`, `.gradle/`, `build/`, `**/build/`, Xcode DerivedData, and local simulator output as local-only.
- If there are unrelated user changes in the worktree, leave them untouched and commit only files that belong to the completed task.
- Never print or commit API keys, signing keys, provisioning profiles, keystores, or Play Console credentials.

## Codex Role

- Implement code changes from Claude's task packets
- Run the relevant build, lint, tests, simulator/device install, and smoke tests to validate
- Report: files changed, commands run, pass/fail, blockers
- Do **not** invent visual designs - Claude owns the visual spec for the tuner strip and kora bridge

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
