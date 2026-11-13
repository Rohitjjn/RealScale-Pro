-keepattributes *Annotation*, SourceFile, LineNumberTable
-keepattributes Signature, InnerClasses, EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Tukaani XZ
-dontwarn org.tukaani.xz.**
-keep class org.tukaani.xz.** { *; }

# Keep engine, service, and worker entrypoints
-keep class com.tumuyan.ncnn.realsr.** { *; }
-keep class com.realscale.pro.** { *; }
