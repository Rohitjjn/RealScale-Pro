package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings

/** The design's spring-like easing: cubic-bezier(0.22, 1, 0.36, 1). */
val SpringEasing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Design durations (ms) from the keyframes in index.css. */
object MotionDuration {
    const val PushIn = 300
    const val PopIn = 300
    const val Fade = 260
    const val FadeOut = 200
    const val RiseIn = 450
    const val SheetIn = 360
    const val SheetOut = 220
    const val Pop = 500
    const val TilePulse = 900
    const val Shimmer = 1500
    const val Sweep = 3200
}

/** True when the system asks to minimize motion ("Transition animation scale" = 0 or Animator duration scale = 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f || animator == 0f
}

/**
 * Returns `reduce` when system reduce-motion is on; call sites use it to zero out animations/durations,
 * mirroring the design's `@media (prefers-reduced-motion: reduce)` rule.
 */
@Composable
fun motionDuration(normalMs: Int): Int = if (rememberReduceMotion()) 1 else normalMs

fun <T> springTween(durationMillis: Int) = tween<T>(durationMillis = durationMillis, easing = SpringEasing)
