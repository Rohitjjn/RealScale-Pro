# RealScale Pro — R8 / ProGuard rules for release Android builds.

# Flutter engine & plugins
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# background_downloader (WorkManager + Kotlin serialization)
-keep class com.bbflight.background_downloader.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Optional Play Core / split-install references pulled transitively by Flutter
-dontwarn com.google.android.play.core.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
