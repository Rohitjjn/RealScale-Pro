package com.tumuyan.ncnn.realsr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsTheme
import com.tumuyan.ncnn.realsr.design.RsToastHost
import com.tumuyan.ncnn.realsr.ui.screens.AboutScreen
import com.tumuyan.ncnn.realsr.ui.screens.ConfigScreen
import com.tumuyan.ncnn.realsr.ui.screens.GalleryScreen
import com.tumuyan.ncnn.realsr.ui.screens.HelpScreen
import com.tumuyan.ncnn.realsr.ui.screens.HomeScreen
import com.tumuyan.ncnn.realsr.ui.screens.ModelsScreen
import com.tumuyan.ncnn.realsr.ui.screens.Onboarding
import com.tumuyan.ncnn.realsr.ui.screens.ProcessingScreen
import com.tumuyan.ncnn.realsr.ui.screens.ResultScreen
import com.tumuyan.ncnn.realsr.ui.screens.SettingsScreen
import com.tumuyan.ncnn.realsr.ui.screens.UpscalesScreen
import com.tumuyan.ncnn.realsr.ui.screens.ViewerScreen
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.AppUiState
import com.tumuyan.ncnn.realsr.ui.state.Phase
import com.tumuyan.ncnn.realsr.ui.state.Route
import com.tumuyan.ncnn.realsr.ui.state.ThemePref

/**
 * Compose root of the redesigned app. One activity, one store, no nav library: the route stack in
 * [AppUiState.routes] is the whole navigator, so push/pop animation and predictive back behave the
 * same on every Android version.
 */
@Composable
fun RealScaleDesignApp(store: AppStore) {
    val state by store.state.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.settings.theme) {
        ThemePref.System -> systemDark
        ThemePref.Light -> false
        ThemePref.Dark -> true
    }

    RsTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(LocalRsColors.current.canvas)) {
            if (state.phase == Phase.App) {
                Journey(store, state)
            } else {
                Onboarding(store, state.phase)
            }
            RsToastHost(state.toast?.text)
        }
    }
}

@Composable
private fun Journey(store: AppStore, state: AppUiState) {
    val reduce = com.tumuyan.ncnn.realsr.design.rememberReduceMotion()
    val route = state.route

    BackHandler(enabled = state.canGoBack) {
        store.pop()
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState.depth() >= initialState.depth()
            if (reduce) {
                fadeIn(tween(1)) togetherWith fadeOut(tween(1))
            } else if (forward) {
                (slideInHorizontally(tween(300, easing = com.tumuyan.ncnn.realsr.design.RsMotion.Ease)) { it / 5 }
                    + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(300, easing = com.tumuyan.ncnn.realsr.design.RsMotion.Ease)) { -it / 8 } +
                        fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300, easing = com.tumuyan.ncnn.realsr.design.RsMotion.Ease)) { -it / 8 }
                    + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(300, easing = com.tumuyan.ncnn.realsr.design.RsMotion.Ease)) { it / 5 } +
                        fadeOut(tween(200)))
            }
        },
        label = "route",
        modifier = Modifier.fillMaxSize(),
    ) { target ->
        when (target) {
            Route.Home -> HomeScreen(store)
            is Route.Viewer -> ViewerScreen(store, target.photoId, target.single)
            is Route.Config -> ConfigScreen(store, target.photoId)
            Route.Processing -> ProcessingScreen(store)
            is Route.Result -> ResultScreen(store, target.resultId)
            Route.Upscales -> UpscalesScreen(store)
            Route.Gallery -> GalleryScreen(store)
            Route.Models -> ModelsScreen(store)
            Route.Settings -> SettingsScreen(store)
            Route.Help -> HelpScreen(store)
            Route.About -> AboutScreen(store)
        }
    }
}

/** Rough stacking order, used only to pick push versus pop animation. */
private fun Route.depth(): Int = when (this) {
    Route.Home -> 0
    Route.Upscales, Route.Gallery -> 1
    Route.Models, Route.Settings, Route.Help, Route.About -> 2
    is Route.Viewer -> 3
    is Route.Config -> 4
    Route.Processing -> 5
    is Route.Result -> 6
}
