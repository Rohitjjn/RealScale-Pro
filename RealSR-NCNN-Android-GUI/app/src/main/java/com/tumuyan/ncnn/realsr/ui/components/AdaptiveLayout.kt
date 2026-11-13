package com.tumuyan.ncnn.realsr.ui.components

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Resolves whether the active window should render a two-pane / rail layout (tablets & unfolded
 * foldables >= 600dp width) using Material3 [WindowWidthSizeClass] when hosted in an [Activity],
 * with a clean [LocalConfiguration] fallback for Robolectric/Compose previews.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberIsExpandedWidth(): Boolean {
    val context = LocalContext.current
    val activity = context as? Activity
    if (activity != null) {
        val sizeClass = calculateWindowSizeClass(activity)
        return sizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    }
    return LocalConfiguration.current.screenWidthDp >= 600
}
