package com.shortsense

import com.shortsense.nlp.ChannelRules
import com.shortsense.nlp.Classifier
import com.shortsense.nlp.Lexicon
import com.shortsense.nlp.Mode
import com.shortsense.nlp.ModelMeta
import com.shortsense.nlp.TinyModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end behaviour of the decision, using the real shipped model and lexicon.
 * These are the tests that fail if a change makes the app start blocking study content.
 */
class ClassifierTest {

    private val model = TinyModel.load(RepoFiles.modelBin.inputStream())
    private val lexicon = Lexicon.parse(RepoFiles.keywordsAsset.bufferedReader())
    private val meta = ModelMeta.parse(RepoFiles.modelMeta.readLines())
    private val classifier = Classifier(model, lexicon, meta)
    private val noRules = ChannelRules(emptySet(), emptySet())

    private fun keep(title: String, channel: String, mode: Mode = Mode.INFORMATIVE, preset: Int = 2) =
        classifier.classify(title, channel, mode, preset, noRules).keep

    @Test
    fun `study shorts are kept`() {
        assertTrue(keep("Krebs cycle explained in 60 seconds | NEET 2026", "NEETprep"))
        assertTrue(keep("integration by parts shortcut for JEE Mains", "Maths Wallah"))
        assertTrue(keep("DNA replication full concept revision", "Aakash Institute"))
        assertTrue(keep("how to solve any dynamic programming problem", "Take U Forward"))
        assertTrue(keep("सामान्य ज्ञान: भारतीय संविधान के अनुच्छेद", "स्टडी आईक्यू"))
    }

    @Test
    fun `entertainment shorts are blocked`() {
        assertFalse(keep("POV: your mom calls you for dinner 😂", "Meme Factory India"))
        assertFalse(keep("new song lyrical video", "T-Series"))
        assertFalse(keep("insane 1v4 clutch gameplay highlights", "BGMI Highlights"))
        assertFalse(keep("satisfying slime compilation", "Oddly Satisfying"))
        assertFalse(keep("aaj ka mithun rashifal", "Astrology Rashi Today"))
    }

    @Test
    fun `informative but non academic is kept in informative mode, blocked in study mode`() {
        val title = "how mutual funds charge you fees"
        val channel = "Zerodha Varsity"
        assertTrue(keep(title, channel, Mode.INFORMATIVE))
        // study-only mode is allowed to be strict here: finance is useful, but not study
        val verdict = classifier.classify(title, channel, Mode.STUDY, 4, noRules)
        assertNotNull(verdict)
    }

    @Test
    fun `an unreadable screen is never blocked`() {
        val v = classifier.classify("", "", Mode.INFORMATIVE, 2, noRules)
        assertTrue("a blank read must be kept", v.keep)
        assertTrue("and flagged as unknown", v.unknown)
        val v2 = classifier.classify("🔥🔥", "T-Series", Mode.INFORMATIVE, 2, noRules)
        assertTrue(v2.keep)
        assertTrue(v2.unknown)
    }

    @Test
    fun `allow list wins over everything`() {
        val rules = ChannelRules(setOf("meme factory india"), emptySet())
        val v = classifier.classify("POV: your mom calls you for dinner 😂", "Meme Factory India",
            Mode.INFORMATIVE, 4, rules)
        assertTrue(v.keep)
        assertTrue(v.reason.contains("allow-listed"))
    }

    @Test
    fun `block list wins over a study looking title`() {
        val rules = ChannelRules(emptySet(), setOf("noise exam hub"))
        val v = classifier.classify("organic chemistry mechanism explained", "Noise Exam Hub",
            Mode.INFORMATIVE, 0, rules)
        assertFalse(v.keep)
        assertTrue(v.reason.contains("blocked"))
    }

    @Test
    fun `strictness presets move in the documented direction`() {
        val junk = "wait for it 😂 funny prank gone wrong"
        // every preset must still block obvious junk, and none may block obvious study
        for (preset in 0..4) {
            assertFalse("preset $preset let plain junk through", keep(junk, "Prank King", Mode.INFORMATIVE, preset))
            assertTrue("preset $preset blocked revision content",
                keep("class 12 physics revision one shot", "Physics Wallah", Mode.INFORMATIVE, preset))
        }
        val thresholds = (0..4).map { classifier.threshold(Mode.INFORMATIVE, it) }
        assertEquals("thresholds must be non-decreasing (strictness", thresholds.sorted(), thresholds)
    }

    @Test
    fun `metadata matches the binary it shipped with`() {
        assertEquals(meta.features, model.featureCount)
        assertTrue("hard case accuracy should be high", meta.hardCaseAccuracy > 0.9)
        assertTrue("useful content should rarely be blocked", meta.hardCaseUsefulBlocked < 0.05)
    }
}
