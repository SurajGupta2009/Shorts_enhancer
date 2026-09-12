package com.shortsense

import com.shortsense.service.ShortsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading rules, tested against strings copied verbatim out of the app's own detection
 * log from a real phone. Every one of these was read wrong, and every wrong read is a wrong
 * decision: a soundtrack used as the channel name, YouTube's "Shorts are paused" notice
 * classified as a video title, and the channel row glued onto the end of titles.
 */
class ShortsTextTest {

    private fun kinds(raw: String) = ShortsText.classify(raw).map { it.kind to it.text }

    @Test
    fun `the channel row is pulled out of the title it was glued to`() {
        val pieces = ShortsText.classify("Making Ohnepixel Gamble To His Death Go to channel @Jettism")
        assertEquals(2, pieces.size)
        assertEquals(ShortsText.Kind.TITLE, pieces[0].kind)
        assertEquals("Making Ohnepixel Gamble To His Death", pieces[0].text)
        assertEquals(ShortsText.Kind.CHANNEL, pieces[1].kind)
        assertEquals("Jettism", pieces[1].text)
    }

    @Test
    fun `a bare handle is a channel`() {
        assertEquals(listOf(ShortsText.Kind.CHANNEL to "Jettism"), kinds("@Jettism"))
    }

    @Test
    fun `youTubes own notices are never a title`() {
        assertTrue(kinds("Scrolling on Shorts is paused. You can update your limit in settings.").isEmpty())
        assertTrue(kinds("Drag handle").isEmpty())
        assertTrue(kinds("Subscribe").isEmpty())
        assertTrue(kinds("Go to channel").isEmpty())
        assertTrue(kinds("Shorts").isEmpty())
        assertTrue(kinds("Up next").isEmpty())
        assertTrue(ShortsText.isChrome("Scrolling on Shorts is paused. You can update your limit in settings."))
    }

    @Test
    fun `a soundtrack is not a channel`() {
        assertTrue(kinds("Boom Shaka · KR$NA & Dhanda Nyoliwala").isEmpty())
        assertTrue(ShortsText.isSoundRow("Boom Shaka · KR$NA & Dhanda Nyoliwala"))
        assertTrue(kinds("original sound - Arijit Singh").isEmpty())
        // the one legitimate bullet row on YouTube: the channel, not a soundtrack
        assertFalse(ShortsText.isSoundRow("Channel Name · Subscribe"))
        assertEquals(listOf(ShortsText.Kind.CHANNEL to "Channel Name"), kinds("Channel Name · Subscribe"))
    }

    @Test
    fun `a soundtrack glued in front of the title is stripped`() {
        assertEquals(
            "How I Made my own Smart Glasses",
            ShortsText.stripSoundPrefix("Mix – Memory reboot (Ultra slowed & reverb) How I Made my own Smart Glasses")
        )
        assertEquals(
            "the physics of a spinning ball",
            ShortsText.stripSoundPrefix("(sped up) the physics of a spinning ball")
        )
        // nothing to strip: unchanged
        assertEquals("Krebs cycle explained in 60 seconds", ShortsText.stripSoundPrefix("Krebs cycle explained in 60 seconds"))
    }

    @Test
    fun `ordinary titles are left alone`() {
        val titles = listOf(
            "SUJEET SIR IN 80s 😂 |ARJUNA NEET |Physics wallah #pw #neet #biology",
            "Advanced Concept #maths #mathsshorts #jee #jeemains #upsc #knowledge",
            "You Have No Girlfriend 💯 ( Watch This ) | Relationship Advice | Neeraj",
            "Vishy Anand vs Magnus Carlsen | Are you not entertained? AI Learns to play chess",
            "Trapping The Sweatiest PVPers in Minecraft",
        )
        for (t in titles) {
            assertEquals("'$t' should be one title", listOf(ShortsText.Kind.TITLE to t), kinds(t))
        }
    }

    @Test
    fun `a blob of several things is split into its parts`() {
        val pieces = ShortsText.classify("Krebs cycle explained, NEETprep, 1.2M likes, 3 days ago")
        assertTrue(pieces.isNotEmpty())
        assertEquals(ShortsText.Kind.TITLE, pieces[0].kind)
        assertEquals("Krebs cycle explained", pieces[0].text)
    }
}
