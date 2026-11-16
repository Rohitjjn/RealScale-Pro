package com.tumuyan.ncnn.realsr.core.ai

import android.content.Context
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Locale

/**
 * One saved API key.
 *
 * The secret is stored in the app's private SharedPreferences file (`realscale_ai_keys`), which is
 * readable only by this app; it is sent to nobody except the provider the user picked, as the
 * request's auth header. `used`/`usedMonth` implement the per-key monthly image limit.
 */
@Serializable
data class AiKeyRecord(
    val id: String,
    val providerId: String,
    val label: String,
    val key: String,
    /** Only for the custom endpoint. */
    val baseUrl: String? = null,
    /** Only for the custom endpoint: the model id the gateway expects. */
    val modelId: String? = null,
    /** 0 = unlimited. */
    val monthlyLimit: Int = 0,
    val used: Int = 0,
    /** `2026-11`; a new month resets [used]. */
    val usedMonth: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
) {
    val provider: AiProvider get() = AiCatalog.provider(providerId)

    val maskedKey: String
        get() = when {
            key.length <= 8 -> "•".repeat(key.length.coerceAtLeast(4))
            else -> key.take(4) + "…" + key.takeLast(4)
        }

    /** `12 / 50 images` or `12 images · unlimited`. */
    val usageLabel: String
        get() = if (monthlyLimit > 0) "$used / $monthlyLimit images this month" else "$used images · no limit"

    val quotaReached: Boolean get() = monthlyLimit > 0 && used >= monthlyLimit

    fun withCurrentMonth(): AiKeyRecord {
        val month = AiKeyStore.currentMonth()
        return if (usedMonth == month) this else copy(used = 0, usedMonth = month)
    }
}

/** What the AI Upscale screen remembers between jobs. */
@Serializable
data class AiPrefs(
    val level: PlanLevel = PlanLevel.DEFAULT,
    /** `auto` (random available model) or an [AiModel.id]. */
    val modelId: String = "auto",
    /** `null` = pick any enabled key with quota left. */
    val keyId: String? = null,
    val scale: Int = 2,
    /** Empty = [AiCatalog.DEFAULT_PROMPT]. */
    val prompt: String = "",
    val format: String = "png",
    val faceEnhance: Boolean = false,
    /** `faithful` (Stability Conservative) or `balanced`. */
    val strength: String = "balanced",
    /** Downsize the upload above this many megapixels. */
    val maxInputMP: Int = 4,
)

/** Persists [AiKeyRecord]s and [AiPrefs]; no other part of the app knows how they are stored. */
class AiKeyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun keys(): List<AiKeyRecord> {
        val raw = prefs.getString(KEY_KEYS, null) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString(ListSerializer(AiKeyRecord.serializer()), raw) }
            .getOrDefault(emptyList())
        return parsed.map { it.withCurrentMonth() }
    }

    fun saveKeys(keys: List<AiKeyRecord>) {
        val payload = json.encodeToString(ListSerializer(AiKeyRecord.serializer()), keys)
        prefs.edit().putString(KEY_KEYS, payload).apply()
    }

    fun loadPrefs(): AiPrefs {
        val raw = prefs.getString(KEY_PREFS, null) ?: return AiPrefs()
        return runCatching { json.decodeFromString(AiPrefs.serializer(), raw) }.getOrDefault(AiPrefs())
    }

    fun savePrefs(value: AiPrefs) {
        prefs.edit().putString(KEY_PREFS, json.encodeToString(AiPrefs.serializer(), value)).apply()
    }

    companion object {
        private const val PREFS = "realscale_ai_keys"
        private const val KEY_KEYS = "keys"
        private const val KEY_PREFS = "prefs"

        /** `2026-11` in the device's time zone. */
        fun currentMonth(): String {
            val now = Calendar.getInstance()
            return String.format(
                Locale.US,
                "%04d-%02d",
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
            )
        }
    }
}
