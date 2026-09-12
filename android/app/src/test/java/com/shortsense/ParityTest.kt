package com.shortsense

import com.shortsense.nlp.Features
import com.shortsense.nlp.Lexicon
import com.shortsense.nlp.TinyModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.math.abs

/**
 * The most important test in the project.
 *
 * The model is trained in Python and runs in Kotlin. If the two feature extractors ever
 * disagree by one character, every probability on the phone is silently wrong - and
 * nothing about the app would look broken. So `model/train.py` writes a fixture of
 * (title, channel, exact feature list, exact probabilities) and this test replays it.
 *
 * Run it after ANY change to Features.kt, keywords.py or features.py:
 *   python3 model/train.py     # regenerates the fixture
 *   cd android && ./gradlew testDebugUnitTest
 */
class ParityTest {

    private val model: TinyModel = TinyModel.load(RepoFiles.modelBin.inputStream())
    private val lexicon: Lexicon = Lexicon.parse(RepoFiles.keywordsAsset.bufferedReader())

    private data class Case(
        val title: String,
        val channel: String,
        val features: List<String>,
        val probs: DoubleArray
    )

    private fun loadCases(): List<Case> {
        val out = ArrayList<Case>()
        RepoFiles.parityFixture.forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val parts = line.split('\t')
            if (parts.size < 4) return@forEachLine
            val title = String(Base64.getDecoder().decode(parts[0]), Charsets.UTF_8)
            val channel = String(Base64.getDecoder().decode(parts[1]), Charsets.UTF_8)
            val features = if (parts[2].isEmpty()) emptyList() else parts[2].split(',')
            val probs = parts[3].split(',').map { it.toInt() / 1_000_000.0 }.toDoubleArray()
            out.add(Case(title, channel, features, probs))
        }
        return out
    }

    @Test
    fun `fixture is present and non trivial`() {
        val cases = loadCases()
        assertTrue("expected the parity fixture to contain cases", cases.size > 100)
        assertTrue("fixture should cover non-ASCII titles",
            cases.any { it.title.any { c -> c.code > 127 } })
        assertTrue("fixture should cover empty titles", cases.any { it.title.isEmpty() })
    }

    @Test
    fun `features match python exactly`() {
        var checked = 0
        for (case in loadCases()) {
            val mine = Features.features(case.title, case.channel, lexicon)
            assertEquals(
                "feature mismatch for '${case.title}' / '${case.channel}'",
                case.features, mine
            )
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `probabilities match python within one part in a million`() {
        var checked = 0
        var worst = 0.0
        for (case in loadCases()) {
            val features = Features.features(case.title, case.channel, lexicon)
            val logits = model.logits(features)
            val probs = model.probabilities(logits, temperature = 1.0)
            for (i in 0..2) {
                val delta = abs(probs[i] - case.probs[i])
                worst = maxOf(worst, delta)
            }
            checked++
        }
        assertTrue("checked $checked cases", checked > 100)
        assertTrue(
            "worst probability difference was $worst (expected < 3e-6); " +
                "the Kotlin and Python models have drifted apart",
            worst < 3e-6
        )
    }

    @Test
    fun `margins agree with the python definition`() {
        // margin = log P(keep) - log P(junk); recomputed here from the fixture probabilities
        for (case in loadCases().filter { it.title.isNotBlank() }) {
            val expected = Math.log(case.probs[0] + case.probs[1] + 1e-12) - Math.log(case.probs[2] + 1e-12)
            val logits = model.logits(Features.features(case.title, case.channel, lexicon))
            val actual = model.marginInformative(logits)
            assertEquals("margin for '${case.title}'", expected, actual, 1e-3)
        }
    }
}
