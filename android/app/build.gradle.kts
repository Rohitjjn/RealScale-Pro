plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}
android {
    namespace = "com.realscale.pro"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = JavaVersion.VERSION_17.toString() }
    defaultConfig {
        applicationId = "com.realscale.pro"
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_shared", "-DCMAKE_POLICY_VERSION_MINIMUM=3.5") }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("../../native/realscale/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("debug") } }
}
dependencies { implementation("androidx.core:core-ktx:1.16.0") }

flutter { source = "../.." }
