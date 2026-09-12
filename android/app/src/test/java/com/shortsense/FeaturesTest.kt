package com.shortsense

import com.shortsense.nlp.Features
import com.shortsense.nlp.Lexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    private val lexicon: Lexicon = Lexicon.parse(RepoFiles.keywordsAsset.bufferedReader())

    @Test
    fun `normalisation keeps letters, marks and digits of every script`() {
        assertEquals("newton s laws in 60 seconds physics jee",
            Features.normalize("Newton's laws in 60 seconds #physics #JEE"))
        // Devanagari matras survive, which is what makes Hindi titles readable to the model
        assertEquals("गुरुत्वाकर्षण बल क्या है",
            Features.normalize("गुरुत्वाकर्षण बल क्या है? 🔥🔥"))
        assertEquals("", Features.normalize("😂😂😂"))
        assertEquals("", Features.normalize(""))
    }

    @Test
    fun `stemming is the documented crude suffix stripper`() {
        assertEquals("solv", Features.stem("solved"))
        assertEquals("solv", Features.stem("solving"))
        assertEquals("study", Features.stem("studies"))
        assertEquals("note", Features.stem("notes"))
        assertEquals("explain", Features.stem("explained"))
        assertEquals("trend", Features.stem("trending"))
        assertEquals("physic", Features.stem("physics"))
    }

    @Test
    fun `feature keys never contain the fixture separators`() {
        val awkward = listOf(
            "Newton's laws, 60 seconds\ttab" to "Physics Wallah",
            "line\nbreak" to "chan,nel",
            "गुरुत्वाकर्षण बल क्या है? 🔥" to "फिजिक्स वाला"
        )
        for ((title, channel) in awkward) {
            for (f in Features.features(title, channel, lexicon)) {
                assertFalse("comma in $f", f.contains(','))
                assertFalse("tab in $f", f.contains('\t'))
                assertFalse("newline in $f", f.contains('\n'))
            }
        }
    }

    @Test
    fun `empty input yields the guard features`() {
        val f = Features.features("", "", lexicon)
        assertTrue(f.contains("__empty"))
        assertTrue(f.contains("__nochan"))
        assertFalse(f.contains("__nonascii"))
    }

    @Test
    fun `keyword and phrase features fire on obvious teaching language`() {
        val f = Features.features("Krebs cycle explained step by step for NEET", "NEETprep", lexicon)
        assertTrue(f.any { it.startsWith("k:i:") })
        assertTrue(f.any { it == "ph:i:step_by_step" })
        assertTrue(f.any { it.startsWith("ch:i:") })
    }

    @Test
    fun `entertainment language is tagged as such`() {
        val f = Features.features("wait for it 🔥 sigma edit", "Sigma Motivation Hindi", lexicon)
        assertTrue(f.any { it.startsWith("k:e:") })
        assertTrue(f.any { it.startsWith("ch:e:") })
    }
}
