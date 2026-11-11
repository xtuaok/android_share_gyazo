plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: set these in ~/.gradle/gradle.properties (never in this repository).
//   sharegyazo.keystore=/path/to/keystore
//   sharegyazo.keystorePassword=...
//   sharegyazo.keyAlias=xtuaok-apk
//   sharegyazo.keyPassword=...
val releaseKeystore = providers.gradleProperty("sharegyazo.keystore").orNull

android {
    namespace = "xtuaok.sharegyazo"
    compileSdk = 37

    defaultConfig {
        applicationId = "xtuaok.sharegyazo"
        minSdk = 28
        targetSdk = 37
        versionCode = 30
        versionName = "3.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.gradleProperty("sharegyazo.keystorePassword").get()
                keyAlias = providers.gradleProperty("sharegyazo.keyAlias").getOrElse("xtuaok-apk")
                keyPassword = providers.gradleProperty("sharegyazo.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            // Unsigned when the keystore is not configured (e.g. CI).
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
