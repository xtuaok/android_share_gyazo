import com.android.build.api.artifact.SingleArtifact

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
        versionCode = 32
        versionName = "4.0.1"
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

// Copy APK / AAB to <workspace>/output/ as ShareGyazo-<versionName>-<versionCode>[-<variant>].{apk,aab}
// after assemble* / bundle* finishes.
val artifactOutputDir = rootProject.layout.projectDirectory.dir("output")

androidComponents {
    onVariants { variant ->
        val taskSuffix = variant.name.replaceFirstChar(Char::titlecase)
        val output = variant.outputs.single()
        val versionName = output.versionName
        val versionCode = output.versionCode
        val variantSuffix = if (variant.buildType == "release") "" else "-${variant.name}"
        fun fileName(ext: String) =
            "ShareGyazo-${versionName.get()}-${versionCode.get()}$variantSuffix.$ext"

        val copyApk = tasks.register<Copy>("copy${taskSuffix}ApkToOutput") {
            from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
            into(artifactOutputDir)
            rename { fileName("apk") }
        }
        val copyBundle = tasks.register<Copy>("copy${taskSuffix}BundleToOutput") {
            from(variant.artifacts.get(SingleArtifact.BUNDLE))
            into(artifactOutputDir)
            rename { fileName("aab") }
        }
        tasks.matching { it.name == "assemble$taskSuffix" }.configureEach { finalizedBy(copyApk) }
        tasks.matching { it.name == "bundle$taskSuffix" }.configureEach { finalizedBy(copyBundle) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
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
