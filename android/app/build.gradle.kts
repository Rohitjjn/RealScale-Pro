import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// ---------------------------------------------------------------------------
// Release signing
// ---------------------------------------------------------------------------
// A `key.properties` file in android/ (git-ignored) switches the release build
// to a real upload key:
//
//   storeFile=../upload-keystore.jks   (relative to android/app/)
//   storePassword=…
//   keyAlias=…
//   keyPassword=…
//
// Without it the release APK is signed with the debug certificate: still a
// full AOT/R8-free release build that installs and runs on a phone, just not
// something to upload to Google Play. CI writes this file from the
// KEY_PROPERTIES secret when it is configured.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
val hasUploadKeystore = keystorePropertiesFile.exists()
if (hasUploadKeystore) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}
val hasReleaseSigning = hasUploadKeystore &&
    !keystoreProperties.getProperty("storeFile").isNullOrBlank()

// ---------------------------------------------------------------------------
// Native engine (ncnn via CMake + dart:ffi)
// ---------------------------------------------------------------------------
// `bash native/fetch_ncnn.sh android` downloads the official Tencent ncnn
// Android prebuilts into <repo>/third_party/ncnn-android/<abi>/ . The path is
// derived here instead of being hard-coded so a missing download can never
// produce a half-configured build.
//
// Every ABI in `rsAbis` is compiled from the same `native/` sources. ABIs that
// have a prebuilt ncnn get the real inference engine; ABIs without one still
// build and honestly report the engine as unavailable at runtime
// (RS_BUILD_NCNN falls back to the interface-only stub in native/CMakeLists.txt)
// instead of failing the whole APK.
val rsAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val rsNcnnAndroidDir =
    rootProject.projectDir.resolve("../third_party/ncnn-android").canonicalFile
val rsAbisWithNcnn = rsAbis.filter {
    rsNcnnAndroidDir.resolve("$it/lib/libncnn.a").isFile
}
val rsNativeCmake = file("../../native/CMakeLists.txt")

// Mirrors the Flutter Gradle Plugin: an explicit abiFilters list and
// `--split-per-abi` are mutually exclusive (the plugin sets up splits.abi and
// clears the filters itself). So the filter is only declared for the universal
// APK path; the split path takes its ABI list from Flutter's target platforms.
val rsSplitPerAbi =
    project.findProperty("split-per-abi")?.toString()?.toBoolean() ?: false

// When every ABI we ship has a prebuilt ncnn, the engine must really link it:
// CMake is told to fail instead of silently falling back to the interface-only
// stub. A partially fetched tree keeps the graceful path so one missing ABI
// cannot block the whole APK.
val rsRequireNcnn = rsAbisWithNcnn.size == rsAbis.size

android {
    namespace = "dev.realscale.realscale_pro"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "dev.realscale.realscale_pro"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Ship every ABI Flutter supports (arm64 phones, older armv7 phones,
        // x86_64 emulators). Skipped when `--split-per-abi` is used — the
        // Flutter plugin owns the ABI configuration in that mode.
        if (!rsSplitPerAbi) {
            ndk {
                abiFilters += rsAbis
            }
        }

        externalNativeBuild {
            cmake {
                // c++17 for the engine sources; exceptions/RTTI are required by
                // the engine's recovery paths (ncnn archives are linked as raw
                // .a files precisely so their -fno-exceptions flags do not leak).
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DRS_NCNN_ANDROID_DIR=$rsNcnnAndroidDir",
                    "-DANDROID_STL=c++_shared",
                    "-DRS_REQUIRE_NCNN=${if (rsRequireNcnn) "ON" else "OFF"}",
                )
                if (rsAbisWithNcnn.isEmpty()) {
                    logger.lifecycle(
                        "RealScale-Pro: no ncnn prebuilt found under " +
                            "$rsNcnnAndroidDir — the APK will ship the " +
                            "interface-only engine (no AI inference). " +
                            "Run: bash native/fetch_ncnn.sh android"
                    )
                } else {
                    logger.lifecycle(
                        "RealScale-Pro: ncnn prebuilt found for " +
                            rsAbisWithNcnn.joinToString(", ")
                    )
                }
            }
        }
    }

    // Builds librealscale_native.so from the shared native/ module and bundles
    // it into the APK for dart:ffi to load.
    externalNativeBuild {
        cmake {
            path = rsNativeCmake
            // The NDK version pinned by Flutter ships this CMake; version
            // mismatches are the single most common Android build failure.
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // R8 stays off on purpose: the Dart side is already AOT-compiled and
            // the native engine is untouched by R8, so minification would only
            // shrink a little plugin glue while adding build time and memory on
            // low-resource machines.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
