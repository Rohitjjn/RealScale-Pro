package com.tumuyan.ncnn.realsr.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences

/**
 * "Slow down when hot or low on battery" (Settings → General).
 *
 * Two cheap, permission-free signals decide it:
 *  - the platform thermal status (`PowerManager.currentThermalStatus`, Android 10+), and
 *  - the sticky battery broadcast (15 % or less while unplugged).
 *
 * The engine uses [throttle] to run the same model with a smaller tile instead of refusing the job,
 * and the Processing screen reads [reason] to say why the tiles got slower.
 */
object ThermalGuard {

    private const val LOW_BATTERY_PERCENT = 15

    /** The thermal level that means "the phone is already warm", not just "normal". */
    private fun isHot(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    }

    private fun batteryState(context: Context): Pair<Int, Boolean> {
        val intent: Intent? = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0) (level * 100) / scale else 100
        return percent to charging
    }

    /** Human-readable reason, or null when the phone is fine. */
    fun reason(context: Context): String? {
        if (isHot(context)) return "Phone is warm"
        val (percent, charging) = batteryState(context)
        if (!charging && percent in 0..LOW_BATTERY_PERCENT) return "Battery at $percent%"
        return null
    }

    fun shouldThrottle(context: Context): Boolean = reason(context) != null

    /**
     * Same job, gentler footprint: halve the tile (never below 64 px) and drop to a single thread
     * budget the phone can actually sustain while it is hot or nearly empty.
     */
    fun throttle(preferences: EnginePreferences): EnginePreferences {
        val tile = when {
            preferences.tileSize <= 0 -> 256
            preferences.tileSize > 256 -> 256
            preferences.tileSize > 64 -> (preferences.tileSize / 2).coerceAtLeast(64)
            else -> preferences.tileSize
        }
        val threads = preferences.threadCount.toIntOrNull()?.let { (it / 2).coerceAtLeast(1).toString() }
        return preferences.copy(tileSize = tile, threadCount = threads.orEmpty())
    }
}
