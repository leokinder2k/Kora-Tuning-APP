# Codex + Claude Workflow

Use this workflow when you want Claude and Codex to collaborate on the same task in this repo.

## Role Split

- `Claude`: planning, architecture decisions, acceptance criteria, review feedback.
- `Codex`: code edits, terminal execution, build/test/install, and concrete verification output.

## Visual Ownership

- `Claude` owns the visual specification for:
  - the tuner slide/tuning strip UX,
  - the kora bridge image and bridge-facing perspective.
- `Codex` implements Claude's visual packet in code, then runs build, debug, device install, and verification.
- For these two areas, do not let both tools invent visuals independently. Claude defines the target look first.

## Standard Loop

1. `Claude` writes a focused task packet:
   - goal,
   - constraints,
   - files likely involved,
   - acceptance criteria.
2. `Codex` implements directly in the repo and runs validation commands.
3. `Codex` reports:
   - files changed,
   - exact commands run,
   - pass/fail results,
   - blockers (if any).
4. `Claude` reviews the diff and returns either:
   - approval, or
   - precise change requests.
5. Repeat until acceptance criteria are met.

## Task Packet Template (Claude -> Codex)

```text
Task:
Context:
Constraints:
Target files:
Acceptance criteria:
Validation commands:
```

## Completion Template (Codex -> Claude/User)

```text
Changed files:
Commands run:
Results:
Remaining risks:
```

## Android Runbook (This Project)

Use these commands for predictable local results in this repo:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-user"
$env:ANDROID_USER_HOME = "$PWD\.android-home"
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

```powershell
adb devices
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
adb shell monkey -p com.leokinder2k.koratuningcompanion 1
adb logcat -d -s AndroidRuntime
```

## Asset Handoff

Use this when visual reference images are needed for UI/art updates.

- Upload reference files to `Images/bridge-reference/`
- `Claude`: review the photos/sketches and define the visual deltas to apply
- `Codex`: implement the shared rendering change in code and validate on device
- For the kora bridge specifically, update the shared renderer in:
  - `app/src/main/java/com/leokinder2k/koratuningcompanion/scaleengine/ui/InstantOverviewScreen.kt`

## Current Assignment

- `Claude` is the active owner for:
  - tuner slide appearance and tuning-state visual behavior,
  - bridge image shape, scale, angle, and forward-facing tip.
- `Codex` is the active owner for:
  - applying Claude's spec in Compose/canvas code,
  - debugging regressions,
  - building and installing to connected devices.

## Practical Rules

- Keep one active owner per step (either Claude or Codex), not both at once.
- Avoid overlapping terminal actions; run `adb` commands sequentially.
- Always verify on-device behavior after UI changes.
- Prefer small, reviewable patches and explicit acceptance checks.
