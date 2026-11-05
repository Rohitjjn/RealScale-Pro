import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android Gradle plugin.
    id("dev.flutter.flutter-gradle-plugin")
}

// Release signing: drop a `key.properties` next to this file (storeFile,
// storePassword, keyAlias, keyPassword). Without it the build falls back to
// the auto-generated certificate so `flutter build apk --release` always works.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}
val hasReleaseKeystore =
    keystorePropertiesFile.exists() &&
        !keystoreProperties.getProperty("storeFile").isNullOrBlank()

// Native ncnn engine: only wired in when the pinned prebuilt ncnn is on disk
// (`bash native/fetch_ncnn.sh android`). Without it the APK still builds and
// ships the honest pure-Dart Lanczos engine.
val nativeCmakeFile =
    rootProject.projectDir.resolve("../native/CMakeLists.txt").canonicalFile
val ncnnAndroidDir =
    rootProject.projectDir.resolve("../native/third_party/ncnn-android")
        .canonicalFile

android {
    namespace = "app.realscale.realscale_pro"
    // permission_handler_android requires API 37.
    compileSdk = 37
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "app.realscale.realscale_pro"
        minSdk = 26
        targetSdk = 37
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        if (ncnnAndroidDir.exists()) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                    arguments += listOf(
                        "-DNCNN_DIR=${ncnnAndroidDir.absolutePath}",
                        "-DANDROID_STL=c++_shared",
                        "-DENABLE_VULKAN=ON",
                    )
                }
            }
        }
    }

    if (ncnnAndroidDir.exists()) {
        externalNativeBuild {
            cmake {
                path = nativeCmakeFile
            }
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
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
            signingConfig =
                if (hasReleaseKeystore) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            // Real release build: AOT + R8 (tree-shaken, obfuscated stack
            // traces aside). proguard-rules.pro keeps the Flutter embedding
            // and the background downloader intact.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
