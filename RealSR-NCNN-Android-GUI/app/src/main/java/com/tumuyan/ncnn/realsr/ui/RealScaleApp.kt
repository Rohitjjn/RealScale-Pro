package com.tumuyan.ncnn.realsr.ui

import android.app.Activity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.ui.screens.ActiveModelDownloadsCard
import com.tumuyan.ncnn.realsr.ui.screens.LibraryScreen
import com.tumuyan.ncnn.realsr.ui.screens.ModelHubScreen
import com.tumuyan.ncnn.realsr.ui.screens.ProcessingScreen
import com.tumuyan.ncnn.realsr.ui.screens.ResultScreen
import com.tumuyan.ncnn.realsr.ui.screens.SettingsScreen
import com.tumuyan.ncnn.realsr.ui.screens.StudioScreen
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleTheme
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing

private object Routes {
    const val STUDIO = "studio"
    const val LIBRARY = "library"
    const val PROCESSING = "processing"
    const val RESULT = "result"
    const val MODEL_HUB = "model_hub"
    const val SETTINGS = "settings"
}

private data class BottomTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RealScaleApp(
    viewModel: RealScaleViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val context = LocalContext.current

    val topLevelRoutes = remember { setOf(Routes.STUDIO, Routes.LIBRARY, Routes.MODEL_HUB) }
    val showBottomBar = currentRoute in topLevelRoutes

    // Show toast/banner notifications cleanly
    LaunchedEffect(state.selection.bannerMessage) {
        val msg = state.selection.bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    // Show Save snackbar with "Open in gallery" action
    LaunchedEffect(state.selection.savedGalleryEvent) {
        val event = state.selection.savedGalleryEvent ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = "Open in gallery"
        )
        viewModel.dismissSavedGalleryEvent()
        if (result == SnackbarResult.ActionPerformed) {
            runCatching {
                MediaExportHelper.openInGallery(context, event.uri, event.mimeType)
            }
        }
    }

    // Deep-link navigation from completion notification, widget, or Quick Sheet "Open"
    LaunchedEffect(state.selection.pendingOpenResultNavigation) {
        if (state.selection.pendingOpenResultNavigation) {
            viewModel.consumePendingResultNavigation()
            navController.navigate(Routes.RESULT) {
                popUpTo(Routes.STUDIO) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // Drive screen transitions from reactive EngineState
    LaunchedEffect(state.engineState) {
        when (state.engineState) {
            is EngineState.Preparing,
            is EngineState.Running -> {
                if (currentRoute != Routes.PROCESSING && currentRoute != Routes.SETTINGS) {
                    navController.navigate(Routes.PROCESSING) {
                        launchSingleTop = true
                    }
                }
            }
            is EngineState.Completed -> {
                navController.navigate(Routes.RESULT) {
                    popUpTo(Routes.STUDIO) { inclusive = false }
                    launchSingleTop = true
                }
            }
            is EngineState.Cancelled -> {
                viewModel.acknowledgeEngineState()
                if (currentRoute == Routes.PROCESSING) {
                    navController.popBackStack(Routes.STUDIO, inclusive = false)
                }
            }
            else -> Unit
        }
    }

    val navigateTopTab: (String) -> Unit = { targetRoute ->
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    RealScaleTheme(themeMode = state.selection.themeMode) {
        val colors = RealScaleThemeTokens.colors
        val activeDownloadStates = remember(state.catalogModels) {
            state.catalogModels.filter {
                it.status == ModelInstallStatus.DOWNLOADING ||
                    it.status == ModelInstallStatus.QUEUED ||
                    it.status == ModelInstallStatus.VERIFYING
            }
        }

        Scaffold(
            containerColor = colors.canvas,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    EditorialBottomBar(
                        currentRoute = currentRoute,
                        activeDownloadsCount = activeDownloadStates.size,
                        onSelectRoute = navigateTopTab
                    )
                }
            }
        ) { innerPadding ->
            SharedTransitionLayout {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (activeDownloadStates.isNotEmpty() && currentRoute != Routes.MODEL_HUB) {
                        ActiveModelDownloadsCard(
                            activeModels = activeDownloadStates,
                            wifiOnly = state.enginePreferences.wifiOnlyDownloads,
                            onOpenHub = { navigateTopTab(Routes.MODEL_HUB) },
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.xs
                            )
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.STUDIO,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(Routes.STUDIO) {
                            StudioScreen(
                                state = state,
                                onPhotosPicked = viewModel::onPhotosSelected,
                                onClearPhotos = viewModel::clearSelectedPhotos,
                                onPresetSelected = viewModel::selectPreset,
                                onScaleSelected = viewModel::selectScale,
                                onModelOverrideSelected = viewModel::selectModelOverride,
                                onStartUpscale = viewModel::requestPrimaryUpscale,
                                onApplyPreflightMitigation = viewModel::applyPreflightMitigation,
                                onConfirmPreflightProceedAnyway = viewModel::confirmPreflightProceedAnyway,
                                onDismissPreflightAdvisory = viewModel::dismissPreflightAdvisory,
                                onQuickDownloadModel = viewModel::downloadOrResumeModel,
                                onOpenModelHub = {
                                    navController.navigate(Routes.MODEL_HUB) { launchSingleTop = true }
                                },
                                onOpenLibrary = { navigateTopTab(Routes.LIBRARY) },
                                onOpenSettings = {
                                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                                },
                                onOpenRecentCreation = { entity ->
                                    viewModel.openRecentCreation(entity)
                                    navController.navigate(Routes.RESULT) { launchSingleTop = true }
                                },
                                onShareRecentCreation = viewModel::shareResult,
                                onRerunRecentCreation = viewModel::rerunRecentCreation,
                                onDeleteRecentCreation = viewModel::deleteRecentCreation,
                                onConsumePendingPhotoPicker = viewModel::consumePendingPhotoPicker,
                                onLaunchFadePlayed = viewModel::markLaunchFadePlayed,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable
                            )
                        }

                        composable(Routes.LIBRARY) {
                            LibraryScreen(
                                creations = state.recentCreations,
                                hapticsEnabled = state.enginePreferences.hapticsEnabled,
                                onOpenCreation = { entity ->
                                    viewModel.openRecentCreation(entity)
                                    navController.navigate(Routes.RESULT) { launchSingleTop = true }
                                },
                                onCompareSelected = { chosen ->
                                    viewModel.openCreationsForComparison(chosen)
                                    navController.navigate(Routes.RESULT) { launchSingleTop = true }
                                },
                                onShareSelected = viewModel::shareCreations,
                                onDeleteSelected = viewModel::deleteCreations,
                                onGoToStudio = { navigateTopTab(Routes.STUDIO) },
                                onOpenSettings = {
                                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                                },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable
                            )
                        }

                        composable(Routes.PROCESSING) {
                            ProcessingScreen(
                                engineState = state.engineState,
                                queue = state.queue,
                                keepScreenOn = state.enginePreferences.keepScreenOnDuringEnhance,
                                onCancel = viewModel::cancelProcessing,
                                onCancelQueuedItem = viewModel::cancelQueuedItem,
                                onApplyRecovery = { action ->
                                    viewModel.applyErrorRecovery(action)
                                },
                                onRunInBackground = {
                                    viewModel.enableBackgroundCompletionNotification()
                                    (context as? Activity)?.moveTaskToBack(true)
                                },
                                onBackToStudio = {
                                    viewModel.acknowledgeEngineState()
                                    navController.popBackStack(Routes.STUDIO, inclusive = false)
                                },
                                onOpenSettings = {
                                    viewModel.acknowledgeEngineState()
                                    navController.navigate(Routes.SETTINGS) {
                                        popUpTo(Routes.STUDIO) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(Routes.RESULT) {
                            ResultScreen(
                                creations = state.selection.resultCreations,
                                selectedIndex = state.selection.selectedResultIndex,
                                onSelectIndex = viewModel::selectResultIndex,
                                onSave = viewModel::saveResultToGallery,
                                onShare = viewModel::shareResult,
                                onTryAnotherPreset = { creation ->
                                    viewModel.tryAnotherPresetFromResult(creation)
                                    navController.popBackStack(Routes.STUDIO, inclusive = false)
                                },
                                onDelete = { creation ->
                                    val wasLast = state.selection.resultCreations.size <= 1
                                    viewModel.deleteRecentCreation(creation)
                                    if (wasLast) {
                                        viewModel.acknowledgeEngineState()
                                        navController.popBackStack()
                                    }
                                },
                                onBackToStudio = {
                                    viewModel.acknowledgeEngineState()
                                    if (!navController.popBackStack()) {
                                        navController.navigate(Routes.STUDIO) { launchSingleTop = true }
                                    }
                                },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable
                            )
                        }

                        composable(Routes.MODEL_HUB) {
                            ModelHubScreen(
                                models = state.catalogModels,
                                totalInstalledBytes = state.totalInstalledBytes,
                                wifiOnlyDownloads = state.enginePreferences.wifiOnlyDownloads,
                                onWifiOnlyChanged = viewModel::setWifiOnlyDownloads,
                                onDownloadOrResume = viewModel::downloadOrResumeModel,
                                onPause = viewModel::pauseModelDownload,
                                onCancel = viewModel::cancelModelDownload,
                                onDelete = viewModel::deleteInstalledModel,
                                onBack = {
                                    if (!navController.popBackStack()) {
                                        navigateTopTab(Routes.STUDIO)
                                    }
                                }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                themeMode = state.selection.themeMode,
                                preferences = state.enginePreferences,
                                consoleLog = state.consoleLog,
                                discoveredCommandsProvider = viewModel::discoverAdvancedCommands,
                                onThemeModeChanged = viewModel::setThemeMode,
                                onDefaultPresetChanged = viewModel::setDefaultPreset,
                                onKeepScreenOnChanged = viewModel::setKeepScreenOnDuringEnhance,
                                onHapticsChanged = viewModel::setHapticsEnabled,
                                onWifiOnlyChanged = viewModel::setWifiOnlyDownloads,
                                onExternalOpenModeChanged = viewModel::setExternalOpenMode,
                                onBackendChanged = viewModel::setExecutionBackend,
                                onTileSizeChanged = viewModel::setTileSize,
                                onOutputFormatChanged = viewModel::setOutputFormat,
                                onCustomOutputFolderChanged = viewModel::setCustomOutputFolder,
                                onThreadCountChanged = viewModel::setThreadCount,
                                onHideProgramOutputChanged = viewModel::setHideProgramOutput,
                                onClearTempCache = viewModel::clearTemporaryCache,
                                onRunFolderBatch = viewModel::runFolderBatchUpscale,
                                onRunRawCommand = viewModel::runRawAdvancedCommand,
                                onResetEngineCache = viewModel::resetEngineCache,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialBottomBar(
    currentRoute: String?,
    activeDownloadsCount: Int,
    onSelectRoute: (String) -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val tabs = remember {
        listOf(
            BottomTabItem(Routes.STUDIO, "Studio", RealScaleIcons.Sparkle),
            BottomTabItem(Routes.LIBRARY, "Library", RealScaleIcons.LibraryGrid),
            BottomTabItem(Routes.MODEL_HUB, "Models", RealScaleIcons.ModelHub)
        )
    }

    Surface(
        color = colors.canvas,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Tab) { onSelectRoute(tab.route) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = if (selected) colors.terracottaTint else colors.canvas,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) colors.terracotta else colors.muted,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                        .size(20.dp)
                                )
                            }
                            if (tab.route == Routes.MODEL_HUB && activeDownloadsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.terracotta)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.ink else colors.muted
                        )
                    }
                }
            }
        }
    }
}
