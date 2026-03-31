# Claude Active Task Packet

Owner: `Codex`

## Task

Add iOS (Apple App Store) support to the KMP project.

## Context

The project already has a working KMP structure:
- `shared/` — commonMain + androidMain + desktopMain
- Platform expect/actual pattern in place for all platform-sensitive APIs
- Compose Multiplatform used throughout

iOS requires:
1. Adding iOS targets to `shared/build.gradle.kts`
2. Creating `iosMain` actual implementations for every `expect`
3. Creating an `iosApp/` Xcode project that hosts the shared Compose UI

**IMPORTANT — macOS required:** Kotlin/Native iOS compilation and Xcode require macOS.
Run all iOS build/test steps on a Mac or in GitHub Actions with `macos-latest`.
On Windows you can still write all the source files — they just cannot be compiled until macOS is available.

## Constraints

- Do NOT invent new platform behavior. Follow the exact `expect` signatures in `commonMain`.
- Use `AVAudioEngine` for all iOS audio (microphone capture, tone player, metronome, plucked string).
- Use `AVAudioSession` for mic permission.
- Use `NSFileManager` / `NSDocumentDirectory` for DataStore paths.
- `supportsDynamicColor` must be `false` on iOS (Material You dynamic color is Android-only).
- Minimum iOS deployment target: **16.0**.
- Bundle ID: `com.leokinder2k.koratuningcompanion.ios`

## Target Files to Create / Modify

### 1. `shared/build.gradle.kts` — add iOS targets

Replace the `kotlin { }` block with:

```kotlin
kotlin {
    androidTarget()
    jvm("desktop")

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation("androidx.appcompat:appcompat:1.7.0")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain.get())
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}
```

Also add this `framework` block inside the `kotlin { }` block (after the targets, before sourceSets):

```kotlin
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
```

### 2. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/platform/DataStoreFactory.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private fun dataStoreDir(): String {
    val urls = NSFileManager.defaultManager.URLsForDirectory(
        NSDocumentDirectory, NSUserDomainMask
    )
    return (urls.first() as NSURL).path!!
}

private val appSettingsStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${dataStoreDir()}/app_settings.preferences_pb".toPath() }
    )
}

private val instrumentConfigStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${dataStoreDir()}/instrument_config.preferences_pb".toPath() }
    )
}

actual fun createAppSettingsDataStore(): DataStore<Preferences> = appSettingsStore
actual fun createInstrumentConfigDataStore(): DataStore<Preferences> = instrumentConfigStore
```

### 3. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/platform/LocalePlatform.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual fun changeLocale(tag: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual fun getCurrentLocaleTag(): String =
    NSLocale.currentLocale.localeIdentifier.replace("_", "-")
```

### 4. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/platform/MicPermission.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted

actual fun isMicPermissionGranted(): Boolean =
    AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

@Composable
actual fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    return remember {
        {
            AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                onResult(granted)
            }
        }
    }
}
```

### 5. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/platform/UrlOpener.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
}
```

### 6. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/platform/DynamicColor.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.platform

actual val supportsDynamicColor: Boolean = false
```

### 7. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/platform/KoraDiagramBackground.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun KoraDiagramBackground(contentDescription: String, modifier: Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}
```

### 8. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/livetuner/audio/AudioCapture.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.livetuner.audio

actual fun createAudioFrameSource(): AudioFrameSource = IosAudioFrameSource()
```

### 9. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/livetuner/audio/IosAudioFrameSource.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.livetuner.audio

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement

class IosAudioFrameSource : AudioFrameSource {
    override fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray> = callbackFlow {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setMode(AVAudioSessionModeMeasurement, error = null)
        session.setActive(true, error = null)

        val engine = AVAudioEngine()
        val inputNode = engine.inputNode
        val format = AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = 1u)

        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = frameSize.toUInt(),
            format = format
        ) { buffer, _ ->
            val channelData = buffer?.floatChannelData?.get(0) ?: return@installTapOnBus
            val count = buffer.frameLength.toInt()
            val shorts = ShortArray(count) { i ->
                (channelData[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            trySend(shorts)
        }

        engine.prepare()
        engine.startAndReturnError(null)

        awaitClose {
            inputNode.removeTapOnBus(0u)
            engine.stop()
            session.setActive(false, error = null)
        }
    }
}
```

### 10. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/livetuner/audio/MetronomeClickPlayer.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.livetuner.audio

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

actual class MetronomeClickPlayer actual constructor(private val sampleRateHz: Int) {
    private val engine = AVAudioEngine()
    private val mixer = engine.mainMixerNode
    private val format = AVAudioFormat(standardFormatWithSampleRate = sampleRateHz.toDouble(), channels = 1u)!!
    private val players = mutableListOf<AVAudioPlayerNode>()

    init {
        engine.prepare()
        engine.startAndReturnError(null)
    }

    actual fun play(sound: MetronomeSoundOption, accent: Boolean, volumeScale: Float) {
        val samples = createClickSamples(sound, accent, volumeScale.coerceIn(0f, 1.8f).toDouble())
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt()) ?: return
        buffer.setFrameLength(samples.size.toUInt())
        val channelData = buffer.floatChannelData?.get(0) ?: return
        samples.forEachIndexed { i, v -> channelData[i] = v }

        val player = AVAudioPlayerNode()
        players.add(player)
        engine.attachNode(player)
        engine.connect(player, to = mixer, format = format)
        player.scheduleBuffer(buffer, completionHandler = {
            engine.detachNode(player)
            players.remove(player)
        })
        player.play()
    }

    actual fun stopAll() {
        players.forEach { it.stop() }
        players.clear()
    }

    actual fun release() {
        stopAll()
        engine.stop()
    }

    private fun createClickSamples(sound: MetronomeSoundOption, accent: Boolean, volumeScale: Double): FloatArray {
        val durationMs = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 58
            MetronomeSoundOption.WOOD_BLOCK -> 46
            MetronomeSoundOption.WOOD_CLICK -> 34
        }
        val baseFreq = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 880.0
            MetronomeSoundOption.WOOD_BLOCK -> 1450.0
            MetronomeSoundOption.WOOD_CLICK -> 2250.0
        }
        val harmonicGain = when (sound) {
            MetronomeSoundOption.WOOD_SOFT -> 0.16
            MetronomeSoundOption.WOOD_BLOCK -> 0.24
            MetronomeSoundOption.WOOD_CLICK -> 0.32
        }
        val amplitude = (if (accent) 0.48 else 0.34) * volumeScale
        val count = ((sampleRateHz * durationMs) / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRateHz * 0.0018).toInt().coerceAtLeast(1)
        return FloatArray(count) { i ->
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decay = exp(-8.0 * i.toDouble() / count)
            val f = sin(2.0 * PI * baseFreq * i / sampleRateHz)
            val h = sin(2.0 * PI * baseFreq * 2.38 * i / sampleRateHz)
            ((f + harmonicGain * h) * attack * decay * amplitude).coerceIn(-1.0, 1.0).toFloat()
        }
    }
}
```

### 11. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/livetuner/audio/ReferenceTonePlayer.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.livetuner.audio

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

actual class ReferenceTonePlayer actual constructor(
    private val sampleRateHz: Int,
    private val amplitude: Double
) {
    private val engine = AVAudioEngine()
    private val mixer = engine.mainMixerNode
    private val format = AVAudioFormat(standardFormatWithSampleRate = sampleRateHz.toDouble(), channels = 1u)!!
    private var player: AVAudioPlayerNode? = null
    private var currentFreq: Double? = null

    init {
        engine.prepare()
        engine.startAndReturnError(null)
    }

    actual fun play(frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return
        if (currentFreq?.let { abs(it - frequencyHz) < 0.05 } == true) return
        stopInternal()

        val samples = FloatArray(sampleRateHz) { i ->
            (sin(2.0 * PI * frequencyHz * i / sampleRateHz) * amplitude).toFloat()
        }
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt()) ?: return
        buffer.setFrameLength(samples.size.toUInt())
        val channelData = buffer.floatChannelData?.get(0) ?: return
        samples.forEachIndexed { i, v -> channelData[i] = v }

        val p = AVAudioPlayerNode()
        player = p
        currentFreq = frequencyHz
        engine.attachNode(p)
        engine.connect(p, to = mixer, format = format)
        p.scheduleBuffer(buffer, atTime = null, options = 1uL /* AVAudioPlayerNodeBufferLoops */, completionHandler = null)
        p.play()
    }

    actual fun stop() { stopInternal() }

    actual fun isPlaying(): Boolean = player != null

    actual fun release() { stopInternal(); engine.stop() }

    private fun stopInternal() {
        player?.stop()
        player?.let { engine.detachNode(it) }
        player = null
        currentFreq = null
    }
}
```

### 12. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/livetuner/audio/PluckedStringPlayer.kt`

```kotlin
package com.leokinder2k.koratuningcompanion.livetuner.audio

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

actual class PluckedStringPlayer actual constructor(
    private val sampleRateHz: Int,
    private val baseAmplitude: Double,
    private val pluckDurationMs: Int
) {
    private val engine = AVAudioEngine()
    private val mixer = engine.mainMixerNode
    private val format = AVAudioFormat(standardFormatWithSampleRate = sampleRateHz.toDouble(), channels = 1u)!!
    private val activePlayers = mutableMapOf<Int, AVAudioPlayerNode>()
    private val extraVolumeFactor = 250.0
    private var amplitudeScale = 1.0

    init {
        engine.prepare()
        engine.startAndReturnError(null)
    }

    actual fun setVolumeDb(db: Double) {
        val clamped = db.coerceIn(0.0, 100.0)
        amplitudeScale = 10.0.pow((clamped - 70.0) / 20.0).coerceAtLeast(0.15)
    }

    actual fun play(stringNumber: Int, frequencyHz: Double) {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return
        stop(stringNumber)

        val count = ((sampleRateHz * pluckDurationMs) / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRateHz * 0.004).toInt().coerceAtLeast(1)
        val amp = baseAmplitude * amplitudeScale * extraVolumeFactor

        val samples = FloatArray(count) { i ->
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decay = exp(-6.5 * i.toDouble() / count)
            val f = sin(2.0 * PI * frequencyHz * i / sampleRateHz)
            val o = sin(2.0 * PI * frequencyHz * 2.0 * i / sampleRateHz)
            ((f + 0.35 * o) * 0.74 * amp * attack * decay).coerceIn(-1.0, 1.0).toFloat()
        }

        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = count.toUInt()) ?: return
        buffer.setFrameLength(count.toUInt())
        val channelData = buffer.floatChannelData?.get(0) ?: return
        samples.forEachIndexed { i, v -> channelData[i] = v }

        val player = AVAudioPlayerNode()
        activePlayers[stringNumber] = player
        engine.attachNode(player)
        engine.connect(player, to = mixer, format = format)
        player.scheduleBuffer(buffer, completionHandler = {
            engine.detachNode(player)
            activePlayers.remove(stringNumber)
        })
        player.play()
    }

    actual fun stop(stringNumber: Int) {
        activePlayers.remove(stringNumber)?.let {
            it.stop()
            engine.detachNode(it)
        }
    }

    actual fun stopAll() {
        activePlayers.values.forEach { it.stop(); engine.detachNode(it) }
        activePlayers.clear()
    }

    actual fun release() {
        stopAll()
        engine.stop()
    }
}
```

### 13. Create `iosApp/iosApp/iOSApp.swift`

```swift
import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### 14. Create `iosApp/iosApp/ContentView.swift`

```swift
import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

### 15. Create `shared/src/iosMain/kotlin/com/leokinder2k/koratuningcompanion/MainViewController.kt`

This is the iOS entry point that bridges Swift to Compose:

```kotlin
package com.leokinder2k.koratuningcompanion

import androidx.compose.ui.window.ComposeUIViewController
import com.leokinder2k.koratuningcompanion.navigation.KoraAuthorityApp

fun MainViewController() = ComposeUIViewController { KoraAuthorityApp() }
```

### 16. Create `iosApp/iosApp/Info.plist`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleDisplayName</key>
    <string>Kora Tuning</string>
    <key>CFBundleExecutable</key>
    <string>$(EXECUTABLE_NAME)</string>
    <key>CFBundleIdentifier</key>
    <string>com.leokinder2k.koratuningcompanion.ios</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$(PRODUCT_NAME)</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
    <key>NSMicrophoneUsageDescription</key>
    <string>Kora Tuning needs your microphone to detect string pitch in real time.</string>
    <key>UIApplicationSceneManifest</key>
    <dict>
        <key>UIApplicationSupportsMultipleScenes</key>
        <false/>
    </dict>
    <key>UILaunchScreen</key>
    <dict/>
    <key>UIRequiredDeviceCapabilities</key>
    <array>
        <string>armv7</string>
    </array>
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
</dict>
</plist>
```

### 17. Create `iosApp/iosApp.xcodeproj/project.pbxproj`

Use Xcode on macOS to generate this. Run on Mac:
```bash
# Install KMP Wizard or use Fleet / Android Studio to generate the Xcode project
# Then copy iosApp/ into the repo
```
The recommended way is:
- Open the repo in Android Studio on macOS
- Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` to confirm the framework builds
- Then create the Xcode project manually in Xcode targeting iOS 16.0+, add the `shared.framework` as a dependency

## Validation Commands (run on macOS)

```bash
# 1. Build the shared framework for iOS simulator
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Build the shared framework for device
./gradlew :shared:linkReleaseFrameworkIosArm64

# 3. Open Xcode project and build/run on simulator (after Xcode project is created)
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build
```

## Acceptance Criteria

- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` succeeds with zero errors
- [ ] `./gradlew :shared:linkReleaseFrameworkIosArm64` succeeds with zero errors
- [ ] Xcode build succeeds on simulator (iPhone 16, iOS 17+)
- [ ] App launches on simulator — all three screens visible (Instrument Config, Live Tuner, Scale Overview)
- [ ] Microphone permission dialog appears on first launch
- [ ] Metronome plays audible click on iOS simulator
- [ ] Reference tone plays on iOS simulator
- [ ] DataStore persists settings across app restarts (verified by relaunching)
- [ ] Android build still passes: `.\gradlew.bat :app:assembleDebug`
- [ ] Desktop build still passes: `.\gradlew.bat :desktopApp:jar`

## Completion Template

```
Changed files:
Commands run:
Results:
Remaining risks:
```
