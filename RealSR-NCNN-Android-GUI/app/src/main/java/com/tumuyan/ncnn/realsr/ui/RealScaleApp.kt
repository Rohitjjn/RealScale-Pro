package com.tumuyan.ncnn.realsr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.ui.screens.ModelHubScreen
import com.tumuyan.ncnn.realsr.ui.screens.ProcessingScreen
import com.tumuyan.ncnn.realsr.ui.screens.ResultScreen
import com.tumuyan.ncnn.realsr.ui.screens.SettingsScreen
import com.tumuyan.ncnn.realsr.ui.screens.StudioScreen
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleTheme
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens

private object Routes {
    const val STUDIO = "studio"
    const val PROCESSING = "processing"
    const val RESULT = "result"
    const val MODEL_HUB = "model_hub"
    const val SETTINGS = "settings"
}

@Composable
fun RealScaleApp(
    viewModel: RealScaleViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Show toast/banner notifications cleanly
    LaunchedEffect(state.selection.bannerMessage) {
        val msg = state.selection.bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
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

    RealScaleTheme(themeMode = state.selection.themeMode) {
        val colors = RealScaleThemeTokens.colors
        Scaffold(
            containerColor = colors.canvas,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.STUDIO,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.STUDIO) {
                    StudioScreen(
                        state = state,
                        onPhotosPicked = viewModel::onPhotosSelected,
                        onClearPhotos = viewModel::clearSelectedPhotos,
                        onPresetSelected = viewModel::selectPreset,
                        onScaleSelected = viewModel::selectScale,
                        onModelOverrideSelected = viewModel::selectModelOverride,
                        onStartUpscale = {
                            if (viewModel.startPrimaryUpscale()) {
                                navController.navigate(Routes.PROCESSING) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onQuickDownloadModel = viewModel::downloadOrResumeModel,
                        onOpenModelHub = {
                            navController.navigate(Routes.MODEL_HUB) { launchSingleTop = true }
                        },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                        onOpenRecentCreation = { entity ->
                            viewModel.openRecentCreation(entity)
                            navController.navigate(Routes.RESULT) { launchSingleTop = true }
                        },
                        onLaunchFadePlayed = viewModel::markLaunchFadePlayed
                    )
                }

                composable(Routes.PROCESSING) {
                    ProcessingScreen(
                        engineState = state.engineState,
                        onCancel = viewModel::cancelProcessing,
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
                        onBackToStudio = {
                            viewModel.acknowledgeEngineState()
                            navController.popBackStack(Routes.STUDIO, inclusive = false)
                        }
                    )
                }

                composable(Routes.MODEL_HUB) {
                    ModelHubScreen(
                        models = state.catalogModels,
                        totalInstalledBytes = state.totalInstalledBytes,
                        onDownloadOrResume = viewModel::downloadOrResumeModel,
                        onPause = viewModel::pauseModelDownload,
                        onCancel = viewModel::cancelModelDownload,
                        onDelete = viewModel::deleteInstalledModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        themeMode = state.selection.themeMode,
                        preferences = state.enginePreferences,
                        consoleLog = state.consoleLog,
                        discoveredCommandsProvider = viewModel::discoverAdvancedCommands,
                        onThemeModeChanged = viewModel::setThemeMode,
                        onBackendChanged = viewModel::setExecutionBackend,
                        onTileSizeChanged = viewModel::setTileSize,
                        onOutputFormatChanged = viewModel::setOutputFormat,
                        onCustomOutputFolderChanged = viewModel::setCustomOutputFolder,
                        onThreadCountChanged = viewModel::setThreadCount,
                        onHideProgramOutputChanged = viewModel::setHideProgramOutput,
                        onRunFolderBatch = { treeUri ->
                            viewModel.runFolderBatchUpscale(treeUri)
                            navController.navigate(Routes.PROCESSING) { launchSingleTop = true }
                        },
                        onRunRawCommand = viewModel::runRawAdvancedCommand,
                        onResetEngineCache = viewModel::resetEngineCache,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
