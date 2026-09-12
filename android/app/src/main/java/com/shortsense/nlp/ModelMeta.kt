package com.shortsense.nlp

import android.content.Context

/**
 * Thresholds and validation numbers that ship with the model.
 *
 * Read from `assets/model.meta`, a flat `key=value` file written by `model/train.py`
 * alongside `model.bin` and `model.json`. Deliberately not JSON: the file has to be
 * readable in a unit test with no Android framework and no extra dependency.
 */
class ModelMeta(
    val infoMargin: Double,
    val studyMargin: Double,
    val temperature: Double,
    val informativePresets: List<Double>,
    val studyPresets: List<Double>,
    /** Measured on the hand-written cases, one entry per rung of the strictness ladder. */
    val presetUsefulBlocked: List<Double>,
    val presetJunkKept: List<Double>,
    val features: Int,
    val hardCaseAccuracy: Double,
    val hardCaseUsefulBlocked: Double,
    val hardCaseJunkKept: Double,
    val trainedAt: String
) {

    companion object {
        const val ASSET_NAME = "model.meta"

        fun load(context: Context): ModelMeta =
            context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readLines()) }

        fun parse(lines: List<String>): ModelMeta {
            val map = HashMap<String, String>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val i = line.indexOf('=')
                if (i > 0) map[line.substring(0, i).trim()] = line.substring(i + 1).trim()
            }
            fun num(key: String, fallback: Double): Double =
                map[key]?.toDoubleOrNull() ?: fallback

            fun presets(key: String): List<Double> =
                map[key]?.split(',')?.mapNotNull { it.substringAfter(':').toDoubleOrNull() }
                    ?: emptyList()

            /** Plain comma-separated decimals, no "rung:" prefix. */
            fun rates(key: String): List<Double> =
                map[key]?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: emptyList()

            return ModelMeta(
                infoMargin = num("info_margin", 0.0),
                studyMargin = num("study_margin", 0.0),
                temperature = num("temperature", 1.0),
                informativePresets = presets("presets_informative").ifEmpty { listOf(0.0) },
                studyPresets = presets("presets_study").ifEmpty { listOf(0.0) },
                presetUsefulBlocked = rates("preset_info_useful_blocked"),
                presetJunkKept = rates("preset_info_junk_kept"),
                features = num("features", 0.0).toInt(),
                hardCaseAccuracy = num("hard_accuracy", 0.0),
                hardCaseUsefulBlocked = num("hard_useful_blocked", 0.0),
                hardCaseJunkKept = num("hard_junk_kept", 0.0),
                trainedAt = map["trained_at"] ?: "unknown"
            )
        }
    }
}
