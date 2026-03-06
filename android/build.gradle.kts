import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.gradle.play.publisher)
}

val releaseVersionProperties = Properties().apply {
    val releaseVersionFile = rootProject.file("release-version.properties")
    if (releaseVersionFile.exists()) {
        releaseVersionFile.inputStream().use(::load)
    }
}

fun releaseVersionProperty(name: String): String? =
    releaseVersionProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val configuredVersionCode = providers.gradleProperty("VERSION_CODE")
    .orElse(providers.environmentVariable("VERSION_CODE"))
    .orNull
    ?.toIntOrNull()
    ?: releaseVersionProperty("VERSION_CODE")?.toIntOrNull()
    ?: 30000012

val configuredVersionName = providers.gradleProperty("VERSION_NAME")
    .orElse(providers.environmentVariable("VERSION_NAME"))
    .orNull
    ?: releaseVersionProperty("VERSION_NAME")
    ?: "1.0.12"

val releaseStoreFilePath = providers.gradleProperty("ANDROID_SIGNING_STORE_FILE")
    .orElse(providers.environmentVariable("ANDROID_SIGNING_STORE_FILE"))
    .orNull
val releaseStorePassword = providers.gradleProperty("ANDROID_SIGNING_STORE_PASSWORD")
    .orElse(providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("ANDROID_SIGNING_KEY_ALIAS")
    .orElse(providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("ANDROID_SIGNING_KEY_PASSWORD")
    .orElse(providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD"))
    .orNull
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.leokinder2k.koratuningcompanion"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.leokinder2k.koratuningcompanion"
        minSdk = 24
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            ndk {
                debugSymbolLevel = "symbol_table"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        disable += "MissingTranslation"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

play {
    // Put the Play service account JSON here (DO NOT commit it).
    serviceAccountCredentials.set(rootProject.file(".local-signing/play-service-account.json"))

    // Safe default: internal testing track.
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)

    // Prefer AAB upload.
    defaultToAppBundles.set(true)
}
