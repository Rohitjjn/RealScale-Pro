plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

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

        // Native engine ABI set. This build ships arm64-v8a — the primary
        // physical-device target (verified in this environment). To widen
        // the APK, also fetch the matching ncnn prebuilts:
        //   NCNN_ANDROID_ARCHS="arm64-v8a armeabi-v7a x86_64" tools/fetch_ncnn_android.sh
        // and add the ABIs below.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // Per-ABI prebuilt ncnn (Vulkan build; CPU path verified on
                // host, GPU is opt-in with CPU fallback). Fetched by
                // tools/fetch_ncnn_android.sh — see docs/BUILD.md.
                arguments += listOf(
                    "-DRS_NCNN_ANDROID_DIR=${project.rootDir}/../third_party/ncnn-android"
                )
            }
        }
    }

    // Builds librealscale_native.so from the shared native/ module and
    // bundles it into the APK for dart:ffi to load. ncnn is linked from
    // prebuilt static libraries fetched by tools/fetch_ncnn_android.sh
    // (pinned commit; see docs/native-engine-design.md).
    externalNativeBuild {
        cmake {
            path = file("../../native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            // Release signing is NOT configured here on purpose: no signing
            // credentials exist in this repository and none were invented.
            // The release APK is signed with the debug key so it can be
            // installed for testing. For a store-ready build follow
            // docs/INSTALLATION.md § Signing (keytool + apksigner).
            signingConfig = signingConfigs.getByName("debug")
            // R8 minification intentionally left off: the Dart side is
            // already AOT-compiled and the native engine is not touched by
            // R8; enabling it only shrinks a small amount of plugin glue
            // while adding build memory/time on low-resource machines.
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
