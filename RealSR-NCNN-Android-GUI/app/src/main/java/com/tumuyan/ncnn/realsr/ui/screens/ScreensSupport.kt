package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.ScrollState

/** True once the scroll container moved past the design's threshold (drives the top-bar border). */
fun isScrolled(state: ScrollState, thresholdPx: Int = 4): Boolean = state.value > thresholdPx

/** Bytes → "63.5 MB" / "1.2 GB" style label. */
fun fmtBytesMB(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return when {
        mb >= 1000 -> String.format(java.util.Locale.US, "%.1f GB", mb / 1000)
        mb >= 100 -> "${mb.toInt()} MB"
        else -> String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}

/** Milliseconds → "42 s" / "2 min 10 s" / "1.2 h". */
fun fmtDuration(ms: Long): String {
    val sec = ms / 1000.0
    return when {
        sec < 60 -> "${sec.toInt()} s"
        sec < 3600 -> "${(sec / 60).toInt()} min ${((sec % 60).toInt())} s"
        else -> String.format(java.util.Locale.US, "%.1f h", sec / 3600)
    }
}

/** Long millisecond epoch → "12 Nov · 6:42 PM" for the details/result screens. */
fun fmtDateTime(epochMs: Long): String =
    java.text.SimpleDateFormat("d MMM · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(epochMs))

/** Design tips, rotated on the Processing screen. */
val PROCESSING_TIPS: List<String> = listOf(
    "4× works best on photos under 1000 px.",
    "Your original photo is never changed.",
    "Anime and illustrations get their own model automatically.",
    "Results are saved to Pictures/RealScale-Pro.",
    "Bigger scales rebuild more detail.",
    "Everything runs on this device. No upload, no account.",
)

/** FAQ entries for the Help screen — same content as the design. */
val FAQ: List<Pair<String, String>> = listOf(
    "Why is upscaling slow on my phone?" to "Every pixel is rebuilt on your device instead of a server. Bigger photos, higher scales and heavier models take longer. Upscale Mode with the Default level is the fastest path.",
    "What is a tile?" to "A big image is cut into small squares, each square is enhanced, then they are joined again. Smaller tiles use less memory, which is how the app avoids crashes on phones with little RAM.",
    "Why does 5× look a little different?" to "Models work at 2×, 3× or 4×. For 5×, the app runs 4× and then resizes the result up by 125% with light sharpening, so there is one extra resize step.",
    "Do my photos leave my device?" to "No. There is no account and no cloud processing. The network is used only to download models.",
    "Which mode should I use?" to "Upscale Mode is right for everyday photos on any device. Super Resolution Mode keeps the original file untouched and uses heavier models, so it suits powerful devices and detailed work.",
)

/** Open-source licenses for the About screen. */
val LICENSES: List<Triple<String, String, String>> = listOf(
    Triple("ncnn", "BSD-3-Clause", "On-device neural network inference"),
    Triple("realesrgan-ncnn-vulkan", "BSD-3-Clause", "Photo and anime upscaling CLIs"),
    Triple("RealSR", "Apache-2.0", "Photo super-resolution model (RealeSR v3)"),
    Triple("waifu2x", "MIT", "Anime and line-art upscaling"),
    Triple("Real-CUGAN", "MIT", "Anime upscaling models"),
    Triple("MNN", "Apache-2.0", "MNN super-resolution runtime"),
    Triple("Jetpack Compose", "Apache-2.0", "Declarative UI toolkit"),
    Triple("AndroidX / Room / WorkManager", "Apache-2.0", "Persistence and background work"),
    Triple("Coil", "Apache-2.0", "Image loading and caching"),
    Triple("kotlinx.coroutines / serialization", "Apache-2.0", "Concurrency and (de)serialization"),
)
