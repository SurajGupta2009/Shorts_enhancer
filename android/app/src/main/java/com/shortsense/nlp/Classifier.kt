package com.shortsense.nlp

enum class Mode { INFORMATIVE, STUDY }

data class ChannelRules(val allowed: Set<String>, val blocked: Set<String>)

/**
 * The decision, with enough explanation to render the block screen honestly.
 *
 * `keep == false` means "cover it"; `unknown == true` means the app could not read
 * anything useful and chose to keep, which is the safe direction: a false block
 * interrupts something the user wanted, a false keep just wastes a swipe.
 */
data class Verdict(
    val keep: Boolean,
    val unknown: Boolean,
    val margin: Double,
    val threshold: Double,
    val confidence: Double,
    val explained: List<Pair<String, Double>>,
    val reason: String
)

class Classifier(
    private val model: TinyModel,
    private val lex: Lexicon,
    private val meta: ModelMeta
) {

    fun threshold(mode: Mode, presetIndex: Int): Double {
        val presets = if (mode == Mode.STUDY) meta.studyPresets else meta.informativePresets
        if (presets.isEmpty()) return if (mode == Mode.STUDY) meta.studyMargin else meta.infoMargin
        val i = presetIndex.coerceIn(0, presets.size - 1)
        return presets[i]
    }

    fun classify(
        title: String,
        channel: String?,
        mode: Mode,
        presetIndex: Int,
        rules: ChannelRules
    ): Verdict {
        val chan = channel?.trim().orEmpty()
        val features = Features.features(title, chan, lex)
        val logits = model.logits(features)
        val theta = threshold(mode, presetIndex)
        val margin = if (mode == Mode.STUDY) model.marginStudy(logits) else model.marginInformative(logits)
        val explained = model.explain(features, mode == Mode.STUDY)

        // 1. anything the user explicitly allow-listed wins outright
        if (Channels.matches(rules.allowed, chan)) {
            return Verdict(true, false, margin, theta, 1.0, explained, "you allow-listed $chan")
        }
        // 2. so does an explicit block
        if (Channels.matches(rules.blocked, chan)) {
            return Verdict(false, false, margin, theta, 0.0, explained, "you blocked $chan")
        }

        val p = model.probabilities(logits, meta.temperature)
        val confidence = if (mode == Mode.STUDY) p[0] else p[0] + p[1]

        // 3. no readable title: keep, and say so rather than guessing
        val tokens = Features.normalize(title).split(' ').count { it.length >= 2 }
        if (title.isBlank() || tokens < 2) {
            return Verdict(true, true, margin, theta, confidence, explained,
                "could not read a title here, so it was let through")
        }

        val keep = margin >= theta
        val subject = if (mode == Mode.STUDY) "study material" else "worth knowing"
        val reason = if (keep) {
            "title reads like $subject"
        } else {
            "title reads like entertainment, not $subject"
        }
        return Verdict(keep, false, margin, theta, confidence, explained, reason)
    }
}
