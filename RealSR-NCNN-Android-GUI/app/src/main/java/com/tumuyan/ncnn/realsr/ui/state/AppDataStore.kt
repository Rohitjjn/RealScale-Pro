package com.tumuyan.ncnn.realsr.ui.state

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsStore by preferencesDataStore(name = "app_settings_v1")

/** Persisted app-scoped settings. Engine-owned settings (wifiOnly, keepAwake, haptics, keepMeta) are mirrored here for UI display; engine prefs remain the execution source of truth. */
data class AppSettings(
    val onboarded: Boolean = false,
    val theme: String = "system",           // system | light | dark
    val dynamicColor: Boolean = false,
    val launchMode: String = "last",        // last | upscale | super
    val mode: String = "upscale",           // last selected mode
    val superIntroSeen: Boolean = false,
    val compute: String = "auto",           // auto | gpu | cpu
    val format: String = "auto",            // auto | jpeg | png | webp
    val haptics: Boolean = true,
    val keepAwake: Boolean = true,
    val keepMeta: Boolean = true,
    val wifiOnly: Boolean = false,
    val prefsUpscale: String = "",          // JSON JobPrefs (empty = default)
    val prefsSuper: String = "",
)

class AppDataStore(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAUNCH_MODE = stringPreferencesKey("launch_mode")
        val MODE = stringPreferencesKey("mode")
        val SUPER_INTRO_SEEN = booleanPreferencesKey("super_intro_seen")
        val COMPUTE = stringPreferencesKey("compute")
        val FORMAT = stringPreferencesKey("format")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val KEEP_META = booleanPreferencesKey("keep_meta")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val PREFS_UPSCALE = stringPreferencesKey("prefs_upscale")
        val PREFS_SUPER = stringPreferencesKey("prefs_super")
    }

    private fun map(p: androidx.datastore.preferences.core.Preferences): AppSettings = AppSettings(
        onboarded = p[Keys.ONBOARDED] ?: false,
        theme = p[Keys.THEME] ?: "system",
        dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
        launchMode = p[Keys.LAUNCH_MODE] ?: "last",
        mode = p[Keys.MODE] ?: "upscale",
        superIntroSeen = p[Keys.SUPER_INTRO_SEEN] ?: false,
        compute = p[Keys.COMPUTE] ?: "auto",
        format = p[Keys.FORMAT] ?: "auto",
        haptics = p[Keys.HAPTICS] ?: true,
        keepAwake = p[Keys.KEEP_AWAKE] ?: true,
        keepMeta = p[Keys.KEEP_META] ?: true,
        wifiOnly = p[Keys.WIFI_ONLY] ?: false,
        prefsUpscale = p[Keys.PREFS_UPSCALE].orEmpty(),
        prefsSuper = p[Keys.PREFS_SUPER].orEmpty(),
    )

    val settings: Flow<AppSettings> = context.appSettingsStore.data.map(::map)

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appSettingsStore.edit { p ->
            val n = transform(map(p))
            p[Keys.ONBOARDED] = n.onboarded
            p[Keys.THEME] = n.theme
            p[Keys.DYNAMIC_COLOR] = n.dynamicColor
            p[Keys.LAUNCH_MODE] = n.launchMode
            p[Keys.MODE] = n.mode
            p[Keys.SUPER_INTRO_SEEN] = n.superIntroSeen
            p[Keys.COMPUTE] = n.compute
            p[Keys.FORMAT] = n.format
            p[Keys.HAPTICS] = n.haptics
            p[Keys.KEEP_AWAKE] = n.keepAwake
            p[Keys.KEEP_META] = n.keepMeta
            p[Keys.WIFI_ONLY] = n.wifiOnly
            p[Keys.PREFS_UPSCALE] = n.prefsUpscale
            p[Keys.PREFS_SUPER] = n.prefsSuper
        }
    }
}
