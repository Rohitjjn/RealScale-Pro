package com.tumuyan.ncnn.realsr.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.ui.components.RealScaleToast
import com.tumuyan.ncnn.realsr.ui.screens.AboutScreen
import com.tumuyan.ncnn.realsr.ui.screens.ConfigScreen
import com.tumuyan.ncnn.realsr.ui.screens.GalleryScreen
import com.tumuyan.ncnn.realsr.ui.screens.AlbumScreen
import com.tumuyan.ncnn.realsr.ui.screens.HelpScreen
import com.tumuyan.ncnn.realsr.ui.screens.HomeScreen
import com.tumuyan.ncnn.realsr.ui.screens.ModelsScreen
import com.tumuyan.ncnn.realsr.ui.screens.OnboardingFlow
import com.tumuyan.ncnn.realsr.ui.screens.ProcessingScreen
import com.tumuyan.ncnn.realsr.ui.screens.ResultScreen
import com.tumuyan.ncnn.realsr.ui.screens.SettingsScreen
import com.tumuyan.ncnn.realsr.ui.screens.UpscalesScreen
import com.tumuyan.ncnn.realsr.ui.screens.ViewerScreen
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.theme.AppTheme
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.MotionDuration
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleTheme
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import com.tumuyan.ncnn.realsr.ui.theme.SpringEasing
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Route constants. */
object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer"
    const val CONFIG = "config"
    const val PROCESSING = "processing"
    const val RESULT = "result"
    const val UPSCALES = "upscales"
    const val GALLERY = "gallery"
    const val ALBUM = "album"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val ABOUT = "about"
}

/**
 * App root: theme → onboarding gate → navigation shell with JobStrip + toast.
 * Engine plumbing is owned by [AppViewModel]; screens are stateless renderers of state.
 */
@Composable
fun RealScaleApp(viewModel: AppViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val themeMode = when (settings.theme) {
        "light" -> AppTheme.LIGHT
        "dark" -> AppTheme.DARK
        else -> AppTheme.SYSTEM
    }
    RealScaleTheme(themeMode = themeMode, dynamicColor = settings.dynamicColor) {
        if (settings.onboarded) {
            AppShell(viewModel)
        } else {
            OnboardingFlow(viewModel)
        }
    }
}

@Composable
private fun AppShell(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val c = LocalRealScaleColors.current
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val engineState by viewModel.engineState.collectAsState()

    // Toasts: keep the latest for MotionDuration.LONG
    LaunchedEffect(Unit) {
        viewModel.toast.collectLatest { msg ->
            toastMessage = msg
            kotlinx.coroutines.delay(3000)
            toastMessage = null
        }
    }

    // External share/open SEND intents: jump straight to config for the shared photo(s).
    val shareUris by viewModel.shareUris.collectAsState()
    LaunchedEffect(shareUris) {
        val uris = shareUris
        if (uris.isEmpty()) return@LaunchedEffect
        val first = viewModel.probeSessionPhoto(uris.first(), com.tumuyan.ncnn.realsr.ui.state.SessionPhoto.Source.IMPORTED, fallbackName = null)
        if (first != null) {
            viewModel.setPendingInput(first)
            navController.navigate(Routes.CONFIG)
        } else {
            viewModel.showToast("This file can't be opened. Try another image.")
        }
        viewModel.clearShareUris()
    }

    val onBack: () -> Unit = { if (!navController.popBackStack()) Unit }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(MotionDuration.PushIn, easing = SpringEasing)) + slideInHorizontally(tween(MotionDuration.PushIn, easing = SpringEasing)) { full -> full } },
            exitTransition = { fadeOut(tween(MotionDuration.PopIn)) + slideOutHorizontally(tween(MotionDuration.PopIn)) { full -> -full / 4 } },
            popEnterTransition = { fadeIn(tween(MotionDuration.PopIn, easing = SpringEasing)) + slideInHorizontally(tween(MotionDuration.PopIn, easing = SpringEasing)) { full -> -full / 4 } },
            popExitTransition = { fadeOut(tween(MotionDuration.PopIn)) + slideOutHorizontally(tween(MotionDuration.PushIn)) { full -> full } },
        ) {
            composable(Routes.HOME) { HomeScreen(viewModel, navController) }
            composable(Routes.VIEWER) { ViewerScreen(viewModel, navController) }
            composable(Routes.CONFIG) { ConfigScreen(viewModel, navController) }
            composable(Routes.PROCESSING) { ProcessingScreen(viewModel, navController) }
            composable(Routes.RESULT) { ResultScreen(viewModel, navController) }
            composable(Routes.UPSCALES) { UpscalesScreen(viewModel, navController) }
            composable(Routes.GALLERY) { GalleryScreen(viewModel, navController) }
            composable(Routes.ALBUM) { AlbumScreen(viewModel, navController) }
            composable(Routes.MODELS) { ModelsScreen(viewModel, navController) }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel, navController) }
            composable(Routes.HELP) { HelpScreen(viewModel, navController) }
            composable(Routes.ABOUT) { AboutScreen(viewModel, navController) }
        }

        // JobStrip — one tap away from a running/finished job everywhere except Processing itself.
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val showStrip = engineState !is EngineState.Idle && engineState !is EngineState.Cancelled && currentRoute != Routes.PROCESSING
        if (showStrip) {
            JobStrip(
                state = engineState,
                onOpen = {
                    if (engineState is EngineState.Completed) {
                        navController.navigate(Routes.RESULT)
                    } else {
                        navController.navigate(Routes.PROCESSING)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        RealScaleToast(message = toastMessage, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun JobStrip(state: EngineState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRealScaleColors.current
    val done = state is EngineState.Completed
    val pct = when (state) {
        is EngineState.Running -> (state.progressFraction * 100).roundToInt().coerceIn(0, 100)
        is EngineState.Preparing -> 0
        else -> 0
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.dark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        // 2dp progress hairline
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.darkElevated)) {
            Box(Modifier.fillMaxWidth(if (done) 1f else pct / 100f).height(2.dp).background(c.primary))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column {
                    Text(
                        text = if (done) "Upscale finished" else "Upscaling · $pct%",
                        style = RealScaleType.Body14Medium,
                        color = c.onDark,
                        maxLines = 1,
                    )
                    Text(
                        text = if (done) "Saved to Pictures/RealScale-Pro. Tap to view." else "Tap to see progress",
                        style = RealScaleType.Caption12,
                        color = c.onDarkSoft,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_lucide_chevron_right),
                contentDescription = null,
                tint = c.onDarkSoft,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
