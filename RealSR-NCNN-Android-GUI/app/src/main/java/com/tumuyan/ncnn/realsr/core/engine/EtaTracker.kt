package com.tumuyan.ncnn.realsr.core.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rolling per-model ETA statistics from REAL finished jobs (never catalog constants).
 * Keeps the last [WINDOW] samples of (outputMP, seconds) per model and reports a
 * throughput-weighted seconds-per-output-MP. Estimates stay null below [MIN_SAMPLES].
 */
private val Context.etaDataStore by preferencesDataStore(name = "eta_stats_v1")

@Serializable
private data class EtaSample(val outMP: Double, val seconds: Double)

@Serializable
private data class EtaStore(val models: Map<String, List<EtaSample>> = emptyMap())

class EtaTracker(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("eta_json")

    /** Record one finished job. Safe to call with any output size / duration. */
    suspend fun record(modelId: String, outputMP: Double, durationMs: Long) {
        if (modelId.isBlank() || outputMP <= 0.0 || durationMs <= 0L) return
        context.etaDataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString(EtaStore.serializer(), prefs[key] ?: "") }
                .getOrDefault(EtaStore())
            val samples = (current.models[modelId].orEmpty() + EtaSample(outputMP, durationMs / 1000.0))
                .takeLast(WINDOW)
            prefs[key] = json.encodeToString(EtaStore.serializer(), EtaStore(current.models + (modelId to samples)))
        }
    }

    /** Seconds per output megapixel, or null when fewer than [MIN_SAMPLES] samples exist. */
    suspend fun secPerMp(modelId: String): Double? {
        val store = readStore()
        val samples = store.models[modelId].orEmpty()
        if (samples.size < MIN_SAMPLES) return null
        val totalMp = samples.sumOf { it.outMP }
        if (totalMp <= 0.0) return null
        return samples.sumOf { it.seconds } / totalMp
    }

    /** One-shot map for plan building. */
    suspend fun snapshot(): Map<String, Double> = readStore().models
        .mapNotNull { (id, samples) ->
            if (samples.size < MIN_SAMPLES) return@mapNotNull null
            val totalMp = samples.sumOf { it.outMP }
            if (totalMp <= 0.0) null else id to samples.sumOf { it.seconds } / totalMp
        }
        .toMap()

    private suspend fun readStore(): EtaStore {
        val raw = context.etaDataStore.data.first()[key] ?: return EtaStore()
        return runCatching { json.decodeFromString(EtaStore.serializer(), raw) }.getOrDefault(EtaStore())
    }

    companion object {
        const val WINDOW = 8
        const val MIN_SAMPLES = 2
    }
}
