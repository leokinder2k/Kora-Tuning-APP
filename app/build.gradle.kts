plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val configuredVersionCode = providers.gradleProperty("VERSION_CODE")
    .orElse(providers.environmentVariable("VERSION_CODE"))
    .orNull
    ?.toIntOrNull()
    ?: 1

val configuredVersionName = providers.gradleProperty("VERSION_NAME")
    .orElse(providers.environmentVariable("VERSION_NAME"))
    .orNull
    ?: "1.0"

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
    namespace = "com.example.koratuningsystem"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.koratuningsystem"
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
            isMinifyEnabled = false
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
