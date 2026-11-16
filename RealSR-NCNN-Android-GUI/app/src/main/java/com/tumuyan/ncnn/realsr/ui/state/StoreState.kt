package com.tumuyan.ncnn.realsr.ui.state

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Keeps one screen in sync with [AppStore.state].
 *
 * Reading `store.state.value` inside a Composable looks correct but is invisible to Compose: a
 * `StateFlow.value` read is not a snapshot read, so nothing recomposes when the value changes. The
 * screens used to read the flow directly and only refreshed because the root re-collected the state
 * and an unstable `AppStore` parameter defeated skipping — i.e. every download tick re-composed the
 * whole tree, which is what made mode switches, toggles and the theme switch hang for seconds.
 *
 * With this helper each screen observes the flow itself and Compose can skip everything else.
 */
@Composable
fun AppStore.rememberState(): AppUiState = state.collectAsStateWithLifecycle().value
