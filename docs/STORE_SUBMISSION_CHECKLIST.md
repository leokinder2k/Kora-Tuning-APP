# Store Submission Checklist

Reviewed: 2026-06-10

This is a practical release checklist for Android, iOS, and desktop store/distribution work. It is not legal advice. Complete it against the exact artifact being submitted.

## Release Identity

- App name: Kora Tuning Companion / Kora Tuning, confirm final public naming.
- Android package: `com.leokinder2k.koratuningcompanion`
- Android min SDK: 24
- Android target SDK: 36
- Current release version file: `release-version.properties`
- Android release artifact: `android/build/outputs/bundle/release/app-release.aab`
- Android internal publishing script: `scripts/publish_internal_with_symbols.ps1`
- iOS bundle identifier: `com.leokinder2k.koratuningcompanion.ios`
- Desktop package name: `KoraTuningCompanion`

## Google Play

### Store Listing

- [ ] Confirm final app title and short description.
- [ ] Write full description that accurately describes a kora tuning and notation companion.
- [ ] Select category, tags, and content descriptors that match music/education use.
- [ ] Upload final app icon.
- [ ] Upload final phone screenshots from a release build.
- [ ] Upload tablet screenshots if targeting tablets.
- [ ] Upload feature graphic if the Play Console requires or recommends it for the listing.
- [ ] Add publisher-controlled support email or support URL.
- [ ] Add public privacy policy URL.
- [ ] Confirm all store claims are accurate and not overstated.

### App Content

- [ ] Privacy policy: required.
- [ ] Data safety: complete before open/production testing.
- [ ] App access: no login or restricted account access found.
- [ ] Ads: declare no ads if no ad SDKs are added.
- [ ] Content rating: complete questionnaire from final app behavior.
- [ ] Target audience and content: confirm whether the app is for general users or any child/family audience.
- [ ] News apps, health apps, financial features, government apps: mark not applicable unless product positioning changes.
- [ ] Sensitive permissions: explain microphone access as live pitch detection, on-device processing, not recording.

### Data Safety Draft

Verify this against the release artifact before submission:

- Data collected off device by app code: No, based on current review.
- Data shared by app code: No, based on current review.
- Microphone audio: on-device processing for tuner functionality; not transmitted off device by app code.
- Local tuning/profile data: stored on device only for app functionality.
- Imported files: user-selected and processed locally.
- Generated exports: user-initiated sharing only; receiving apps control their own copies.
- Analytics/crash logs/diagnostics: none collected by app code unless a future SDK is added.
- Account deletion: not applicable because there are no accounts or server-side user records.
- Data deletion: local data can be deleted by clearing app storage, deleting presets where supported, or uninstalling.

### Android Release Build

Run from repo root:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle_user_home"
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon
.\gradlew.bat :app:bundleRelease --no-daemon
```

Internal test upload:

```powershell
pwsh -ExecutionPolicy Bypass -File scripts\publish_internal_with_symbols.ps1
```

Manual verification after upload:

- [ ] Install from Play internal testing, not only `adb install`.
- [ ] Confirm version name/code shown in Play Console match `release-version.properties`.
- [ ] Confirm native symbols upload completed or was intentionally skipped.
- [ ] Confirm Play pre-launch report has no release-blocking crashes.
- [ ] Confirm no privacy, policy, or SDK warnings are unresolved.

## Apple App Store

Status: Needs manual review on macOS/Xcode/App Store Connect.

- [ ] Confirm iOS bundle identifier: `com.leokinder2k.koratuningcompanion.ios`.
- [ ] Confirm `NSMicrophoneUsageDescription` is present and accurate.
- [ ] Align iOS version/build numbers with the intended release.
- [ ] Build the shared framework and iOS app on macOS with Xcode.
- [ ] Archive and validate in Xcode Organizer.
- [ ] Complete App Privacy details in App Store Connect.
- [ ] Provide public privacy policy URL.
- [ ] Provide support URL.
- [ ] Complete age rating.
- [ ] Upload iPhone screenshots.
- [ ] Upload iPad screenshots if iPad is supported.
- [ ] Confirm no App Tracking Transparency prompt is needed because no tracking SDK is present.
- [ ] Confirm microphone use is only for live tuning and not recording/uploading.

Note: iOS was not fully verified in the latest Windows-based pass because Kotlin/Native framework validation requires a clean Kotlin/Native environment and final Xcode/App Store Connect validation.

## Desktop Distribution

Status: Needs manual review before commercial desktop distribution.

- [ ] Confirm whether desktop release is public, private beta, or deferred.
- [ ] Set desktop `packageVersion` to match the commercial release version.
- [ ] Confirm macOS bundle ID: `com.leokinder2k.koratuningcompanion.desktop`.
- [ ] Verify desktop microphone permission/entitlements on macOS if live tuning is enabled.
- [ ] Build desktop package for the target OS.
- [ ] Code-sign desktop artifacts where required.
- [ ] Notarize macOS artifacts if distributing outside the Mac App Store.
- [ ] Provide privacy policy and support links on the download page.
- [ ] Run install, launch, uninstall, and update smoke tests on each supported desktop OS.

## Official Policy References

- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Apple App Privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Apple App Review Guidelines, Privacy: https://developer.apple.com/app-store/review/guidelines/#privacy
