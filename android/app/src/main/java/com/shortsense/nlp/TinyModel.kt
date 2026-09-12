package com.shortsense.nlp

import android.content.Context
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * The on-device classifier: a quantised linear softmax stored as a flat table.
 *
 * Format of `assets/model.bin` (little endian, written by `model/train.py`):
 *
 * ```
 * magic          "SSM1"
 * version        uint16
 * classes        uint16            (always 3: 0 study, 1 info, 2 ent)
 * bias           int32 x 3         (already multiplied by scale)
 * scale          int32
 * feature_count  uint32
 * repeat feature_count times:
 *     key_len    uint8
 *     key        UTF-8 bytes
 *     weights    int16 x 3         (already multiplied by scale)
 * ```
 *
 * Inference is one hash lookup per feature plus three integer adds: no framework, no
 * threads, no allocation beyond the feature list. On a mid-range phone this is tens of
 * microseconds, i.e. invisible next to the ~200 ms it takes YouTube to render a Short.
 */
class TinyModel private constructor(
    private val bias: IntArray,
    private val scale: Int,
    private val index: HashMap<String, Int>,
    private val weights: ShortArray,
    val featureCount: Int
) {

    /** Raw integer logits (multiplied by [scale]). */
    fun logits(features: List<String>): IntArray {
        val out = intArrayOf(bias[0], bias[1], bias[2])
        for (f in features) {
            val i = index[f] ?: continue
            val base = i * 3
            out[0] += weights[base].toInt()
            out[1] += weights[base + 1].toInt()
            out[2] += weights[base + 2].toInt()
        }
        return out
    }

    fun logitsFloat(logits: IntArray): DoubleArray =
        doubleArrayOf(logits[0] / scale.toDouble(), logits[1] / scale.toDouble(), logits[2] / scale.toDouble())

    /** Softmax with a temperature, used only for the number shown in the UI. */
    fun probabilities(logits: IntArray, temperature: Double): DoubleArray {
        val l = logitsFloat(logits)
        val t = if (temperature <= 0.0) 1.0 else temperature
        val m = max(l[0], max(l[1], l[2]))
        val ex = doubleArrayOf(exp((l[0] - m) / t), exp((l[1] - m) / t), exp((l[2] - m) / t))
        val s = ex[0] + ex[1] + ex[2]
        return doubleArrayOf(ex[0] / s, ex[1] / s, ex[2] / s)
    }

    /** log P(study or info) - log P(ent): how strongly the model wants to keep this Short. */
    fun marginInformative(logits: IntArray): Double {
        val l = logitsFloat(logits)
        return logSumExp(l[0], l[1]) - l[2]
    }

    /** log P(study) - log P(info or ent): the Study-only mode decision variable. */
    fun marginStudy(logits: IntArray): Double {
        val l = logitsFloat(logits)
        return l[0] - logSumExp(l[1], l[2])
    }

    /** Which features pushed the decision, for the "why" line on the block screen. */
    fun explain(features: List<String>, studyOnly: Boolean, limit: Int = 4): List<Pair<String, Double>> {
        val scored = ArrayList<Pair<String, Double>>()
        for (f in features) {
            val i = index[f] ?: continue
            val base = i * 3
            val d = if (studyOnly) {
                weights[base].toDouble() - logSumExp(
                    weights[base + 1].toDouble(), weights[base + 2].toDouble()
                )
            } else {
                logSumExp(weights[base].toDouble(), weights[base + 1].toDouble()) - weights[base + 2]
            }
            if (d != 0.0) scored.add(Pair(f, d / scale))
        }
        scored.sortByDescending { kotlin.math.abs(it.second) }
        return scored.take(limit)
    }

    private fun logSumExp(a: Double, b: Double): Double {
        val m = max(a, b)
        return m + ln(exp(a - m) + exp(b - m))
    }

    companion object {
        const val ASSET_NAME = "model.bin"
        private const val MAGIC = "SSM1"

        fun load(context: Context): TinyModel =
            context.assets.open(ASSET_NAME).use { load(it) }

        fun load(stream: InputStream): TinyModel {
            // The file is little endian (written by Python's struct.pack("<...")).
            // DataInputStream would read it big endian and produce nonsense, so read the
            // bytes and use an explicitly ordered buffer instead.
            val bytes = stream.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buf.get(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "not a ShortsSense model file" }
            val version = buf.short.toInt()
            require(version == 1) { "unsupported model version $version" }
            val classes = buf.short.toInt()
            require(classes == 3) { "unsupported class count $classes" }
            val bias = intArrayOf(buf.int, buf.int, buf.int)
            val scale = buf.int
            val count = buf.int
            require(count > 0 && count < 5_000_000) { "implausible feature count $count" }
            val index = HashMap<String, Int>(count * 2)
            val weights = ShortArray(count * 3)
            for (i in 0 until count) {
                val keyLen = buf.get().toInt() and 0xFF
                val key = String(bytes, buf.position(), keyLen, Charsets.UTF_8)
                buf.position(buf.position() + keyLen)
                index[key] = i
                val base = i * 3
                weights[base] = buf.short
                weights[base + 1] = buf.short
                weights[base + 2] = buf.short
            }
            return TinyModel(bias, scale, index, weights, count)
        }
    }
}
