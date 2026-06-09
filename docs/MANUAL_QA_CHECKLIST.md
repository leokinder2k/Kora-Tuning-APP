# Manual QA Checklist

Reviewed: 2026-06-10

Use this checklist against a signed release or Play/App Store internal build whenever possible. Record device model, OS version, app version name/code, build source, tester, date, and pass/fail notes.

## Test Matrix

- Android phone: small, mid-size, and large screen if available.
- Android tablet or foldable if supported by the store listing.
- iPhone on physical device before App Store submission.
- iPad if the iOS listing supports iPad.
- Desktop macOS package if desktop is released.
- Windows/Linux desktop only if the project adds supported installers for those platforms.

## Install And Launch

- [ ] Fresh install launches without crash.
- [ ] Upgrade from the previous public/internal build preserves expected presets and settings.
- [ ] Clearing app storage resets local settings and custom presets.
- [ ] Uninstall removes local app data.
- [ ] App works offline after installation.
- [ ] App resumes correctly after home, recent apps, lock/unlock, and process recreation.
- [ ] No startup crash appears in `adb logcat -d -s AndroidRuntime`.

## Privacy And Permissions

- [ ] App asks for microphone only when tuner/microphone tuning is used.
- [ ] Denying microphone permission leaves the app usable outside live tuning.
- [ ] Revoking microphone permission in system settings is handled gracefully.
- [ ] Microphone permission copy explains live pitch detection.
- [ ] Privacy Policy link opens the final public policy URL.
- [ ] No login, email, location, contacts, calendar, SMS, camera, or payment prompts appear.
- [ ] No unexpected network traffic is observed during normal offline use.
- [ ] Export/share actions only happen after explicit user taps.

## Global Audio And Mute

- [ ] Global mute stops all app-generated sound.
- [ ] Muted state applies to string preview sounds.
- [ ] Muted state applies to tuner reference tones if present.
- [ ] Muted state applies to metronome/click sounds if present.
- [ ] Muted state applies to notation preview WAV playback if present.
- [ ] Unmuting restores sound without restarting the app.
- [ ] Rapid mute/unmute taps do not overlap or leave stuck audio.
- [ ] Audio stops when leaving screens that generate sound.

## Instrument Configuration

- [ ] User can select kora/string count options.
- [ ] User can select tuning mode and root note.
- [ ] Tuning options include starting from the 2nd string on the right.
- [ ] Tuning options include starting from the 3rd string on the right.
- [ ] Saving instrument configuration persists after app restart.
- [ ] Reset/default behavior is understandable and does not destroy custom presets unexpectedly.
- [ ] Custom preset names with long text, symbols, and non-English characters do not break layout.
- [ ] Empty preset names are rejected or handled clearly.
- [ ] Rapid save/delete preset actions do not duplicate or corrupt presets.

## Live Tuner

- [ ] Tuner starts after microphone permission is granted.
- [ ] Tuner shows stable pitch/cents response for a known reference tone.
- [ ] Tuner handles silence without noisy UI failures.
- [ ] Tuner handles loud input without crashing.
- [ ] Tuner handles orientation changes and screen sleep/resume.
- [ ] Tuner does not keep microphone/audio resources active after stop/navigation.
- [ ] Tuner works with phone speaker output muted and device volume changes.

## Scale And Overview Screens

- [ ] Overview diagram renders correctly on first load.
- [ ] String labels, notes, and bridge/body visuals do not overlap on phone screens.
- [ ] Tapping strings or controls updates the expected note/scale state.
- [ ] Scale recommendations update when tuning/profile changes.
- [ ] Empty or unusual tuning profiles do not crash the screen.
- [ ] Landscape orientation remains usable.
- [ ] Large font settings remain usable.

## Notation Import, Preview, Export

- [ ] Valid MusicXML import completes.
- [ ] Valid MXL import completes.
- [ ] Valid MIDI import completes.
- [ ] Supported PDF import completes or shows a clear unsupported/failed state.
- [ ] Invalid/corrupt files show a user-friendly error and do not crash.
- [ ] Very large files fail safely or complete without freezing the app.
- [ ] File names with spaces, punctuation, long names, and non-English characters work.
- [ ] PDF export opens the Android share sheet.
- [ ] MIDI export opens the Android share sheet.
- [ ] WAV preview generation/playback works when not muted.
- [ ] Exports are shared as content URIs and are readable by selected share targets.
- [ ] Cancelling the share sheet leaves the app usable.
- [ ] Repeated imports/exports do not leak memory or degrade performance noticeably.

## Accessibility

- [ ] TalkBack/VoiceOver can navigate primary screens in a logical order.
- [ ] Buttons and icon controls have meaningful labels.
- [ ] Focus indicators are visible.
- [ ] Touch targets are large enough for core controls.
- [ ] Text remains readable at large font sizes.
- [ ] App does not rely only on colour to communicate critical tuner status.
- [ ] Dark and light themes maintain readable contrast.
- [ ] Reduced motion settings do not leave essential information unavailable.
- [ ] Keyboard/tab navigation works for desktop and Android keyboard users where applicable.

## Responsive Layout

- [ ] No horizontal clipping on a narrow Android phone.
- [ ] No important controls are hidden behind navigation bars or cutouts.
- [ ] Landscape mode is usable on phone.
- [ ] Tablet layout is not stretched or sparse in a way that hides core workflows.
- [ ] Dialogs fit on small screens and with large font settings.
- [ ] On-screen text does not overlap adjacent controls.

## Abuse And Break Testing

- [ ] Rapid taps on navigation tabs do not crash or create duplicate work.
- [ ] Rapid import/export taps do not double-submit in a harmful way.
- [ ] Back button from every screen returns to a sensible previous state.
- [ ] Rotation during import/export does not crash.
- [ ] Airplane mode does not affect core offline features.
- [ ] Corrupted local settings are handled by safe defaults.
- [ ] Revoking permissions while app is running does not crash.
- [ ] Low storage during export shows a clear failure state.
- [ ] Low battery/background restrictions do not leave audio stuck.

## Performance

- [ ] Cold start feels acceptable on a mid-range Android phone.
- [ ] No screen visibly freezes during normal tuning/configuration use.
- [ ] Notation import/export shows progress or remains responsive.
- [ ] Repeated screen navigation does not steadily increase memory use.
- [ ] Live tuner does not cause excessive battery drain during a short tuning session.
- [ ] Release build has minification/resource shrinking enabled.

## Release Commands

Run from repo root before Android release testing:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle_user_home"
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon
.\gradlew.bat :app:bundleRelease --no-daemon
```

Install debug build for device smoke testing:

```powershell
adb devices
adb install -r "android/build/outputs/apk/debug/app-debug.apk"
adb shell monkey -p com.leokinder2k.koratuningcompanion 1
adb logcat -d -s AndroidRuntime
```

Publish Android internal test:

```powershell
pwsh -ExecutionPolicy Bypass -File scripts\publish_internal_with_symbols.ps1
```

## Go/No-Go Gate

- Ready: all install, launch, privacy, permission, mute, tuner, configuration, export, accessibility, and store smoke checks pass on the target release platforms.
- Needs work: non-critical defects have clear owner, risk, workaround, and post-launch plan.
- Release-blocking: crash, data exposure, incorrect privacy declaration, unusable core tuning flow, broken microphone permission flow, broken store artifact, or inaccessible launch path.
- Needs legal review: privacy policy, terms, refunds, licences, cultural/source permissions, and regional compliance are not signed off.
