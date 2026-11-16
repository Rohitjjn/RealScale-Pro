package com.tumuyan.ncnn.realsr.design

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/**
 * The prototype's motion system (`src/index.css`).
 *
 * One spring curve is allowed: `cubic-bezier(0.22, 1, 0.36, 1)`. Every entrance, sheet and
 * selection uses it; the only other curves are plain `ease` fades. `prefers-reduced-motion` maps to
 * the system animator scale being 0 (or Accessibility → Remove animations).
 */
object RsMotion {
    val Spring = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val Ease = FastOutSlowInEasing
    val Linear = LinearEasing

    const val pushMs = 300
    const val fadeMs = 260
    const val riseMs = 450
    const val sheetInMs = 360
    const val sheetOutMs = 220
    const val popMs = 500
    const val tileMs = 900
    const val sweepMs = 3200

    fun <T> spring(durationMs: Int = pushMs): AnimationSpec<T> = tween(durationMs, easing = Spring)

    fun <T> fade(durationMs: Int = fadeMs): AnimationSpec<T> = tween(durationMs, easing = Ease)

    /** Slide distance of `push-in` / `pop-in` (28 CSS px = 28 dp). */
    const val slidePx = 28
    const val risePx = 12
}

private const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"

/** True when the user asked the system to remove animations. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(context.contentResolver, ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

val LocalReduceMotion = compositionLocalOf { false }

/** 0 ms when reduced motion is on, so entrance animations finish instantly instead of playing. */
fun <T> reduceAwareSpec(reduce: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (reduce) tween(1, easing = LinearEasing) else spec

/** Interpolates the `translateX` of the push/pop screen transitions. */
fun slideX(offsetFraction: Float, distance: Int = RsMotion.slidePx): IntOffset =
    IntOffset((offsetFraction * distance).toInt(), 0)
