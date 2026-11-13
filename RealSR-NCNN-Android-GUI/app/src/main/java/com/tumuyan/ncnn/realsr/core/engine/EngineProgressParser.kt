package com.tumuyan.ncnn.realsr.core.engine

import kotlin.math.roundToLong

data class ParsedCliProgress(
    val fraction: Float,
    val nativeEtaMillis: Long? = null
)

/**
 * Parses progress output across all bundled native upscaling tools:
 * - realsr-ncnn, waifu2x-ncnn, mnnsr-ncnn: "%5.2f%%\t[%5.2fs /%5.2f ETA]"
 * - Anime4k: "%7.2f%%     elpsed: %8.2fs    remaining: %8.2fs\r"
 * - realcugan-ncnn: "%.2f%%\n"
 * Tools without progress lines (resize-ncnn Instant, srmd-ncnn, magick) return null so UI
 * shows a calm indeterminate bar + elapsed seconds instead of a frozen "0%".
 */
object EngineProgressParser {

    // Matches leading percentage token at start of line (after optional CR/whitespace)
    private val LEADING_PCT_REGEX = Regex("""^\s*(\d{1,3}(?:\.\d+)?)\s*%""")
    // Matches "[ 1.23s / 4.56 ETA]" from realsr/waifu2x/mnnsr
    private val BRACKET_ETA_REGEX = Regex("""/\s*(\d+(?:\.\d+)?)\s*ETA\]""", RegexOption.IGNORE_CASE)
    // Matches "remaining:   4.56s" from Anime4k
    private val REMAINING_SEC_REGEX = Regex("""remaining:\s*(\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)

    fun parseLine(rawLine: String): ParsedCliProgress? {
        // Handle carriage-return overwritten progress updates by taking the latest non-blank segment
        val cleaned = rawLine
            .split('\r')
            .lastOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        val pctMatch = LEADING_PCT_REGEX.find(cleaned) ?: return null
        val pctValue = pctMatch.groupValues[1].toFloatOrNull() ?: return null
        if (pctValue.isNaN() || pctValue < 0f || pctValue > 100.5f) return null

        val fraction = (pctValue / 100f).coerceIn(0f, 1f)
        val etaSec = BRACKET_ETA_REGEX.find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: REMAINING_SEC_REGEX.find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        val etaMs = etaSec
            ?.takeIf { it.isFinite() && it >= 0.05 }
            ?.let { (it * 1000.0).roundToLong() }

        return ParsedCliProgress(
            fraction = fraction,
            nativeEtaMillis = etaMs
        )
    }
}
