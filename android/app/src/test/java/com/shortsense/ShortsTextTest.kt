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
        assertTrue(kinds("Boom Shaka · KR\$NA & Dhanda Nyoliwala").isEmpty())
        assertTrue(ShortsText.isSoundRow("Boom Shaka · KR\$NA & Dhanda Nyoliwala"))
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

    // ---- rules added after a real device export (2026-09-12) --------------------------

    @Test
    fun `a badge glued to a title is cut out, the title is kept`() {
        assertEquals(
            "Speed Almost Beat TWO Olympic Runners 🥰🔥",
            ShortsText.stripGluedUi("Speed Almost Beat TWO Olympic Runners 🥰🔥 New content available")
        )
        assertEquals(
            "Newton's laws in 60 seconds",
            ShortsText.stripGluedUi("Newton's laws in 60 seconds Pull down to lock 2x speed")
        )
        assertEquals("", ShortsText.stripGluedUi("New content available"))
        // the same badge at the front, which is how "New content available Beauty of binomial" reads
        assertEquals("Beauty of binomial", ShortsText.stripGluedUi("New content available Beauty of binomial"))
        assertEquals("its the final drdonut", ShortsText.stripGluedUi("New content available its the final drdonut"))
        // stripping is anchored to the ends, so a title that merely starts with the words
        // loses them and what is left is too short to judge (the classifier keeps it)
        assertEquals("soon", ShortsText.stripGluedUi("New content available soon"))
    }

    @Test
    fun `youtube chrome from the export is not a title`() {
        val chrome = listOf(
            "New content available",
            "Pull down to lock 2x speed New content available",
            "Comments. 55 Sort comments",
            "My Ad Centre",
            "Report submitted. You'll get a confirmation email in a few minutes",
            "It violates a specific law or my legal rights National and regional laws, or I'm a copyright holder with intellectual property concerns (opens in new tab)",
            "and ranked by factors like advertiser bid and ad quality. Google doesn't verify reviews",
            "and get fast answers with Google Gemini. Streamline your workflow",
            "Curious about changes ahead? Explore the possibilities waiting in your future.",
            "Download HD videos and enjoy smooth playback directly from your device.",
            "Stream your M3U playlists & live TV easily with a fast IPTV player",
        )
        for (c in chrome) {
            assertTrue("'$c' should be chrome", ShortsText.isChrome(ShortsText.stripGluedUi(c)))
            assertTrue(
                "'$c' should not become a title",
                ShortsText.classify(c).none { it.kind == ShortsText.Kind.TITLE }
            )
        }
    }

    @Test
    fun `comments and sheets are never read as titles`() {
        // the ids these live under, taken from the trees the app walks
        val drop = listOf(
            "com.google.android.youtube:id/comment_thread",
            "com.google.android.youtube:id/comments_header",
            "com.google.android.youtube:id/bottom_sheet_container",
            "com.google.android.youtube:id/reel_comment_button",
            "com.google.android.youtube:id/ad_badge",
            "com.google.android.youtube:id/shopping_overlay",
            "engagement_panel",
        )
        for (id in drop) assertTrue("$id should be dropped", ShortsText.isNonContentId(id))

        // the player overlay and its metadata are exactly what must survive
        val keep = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_metadata",
            "com.google.android.youtube:id/shorts_video_header",
            "com.google.android.youtube:id/reel_player_page_container",
            "",
            null,
        )
        for (id in keep) assertFalse("$id should be usable", ShortsText.isNonContentId(id))
    }

    @Test
    fun `a comment string alone looks like a title - which is why the container check exists`() {
        // These are verbatim from the export. Nothing in the text itself marks them as
        // comments: "they dont even specialize in running so you gotta compare him to a track
        // runner" reads like a perfectly ordinary title. That is exactly why the fix is the
        // container check above and not a keyword list - the app must not look at the text
        // that lives under comment_thread / bottom_sheet at all.
        val commentThread = "com.google.android.youtube:id/comment_thread"
        assertTrue(ShortsText.isNonContentId(commentThread))
        val text = "they dont even specialize in running so you gotta compare him to a track runner"
        assertFalse(
            "the text alone is not chrome; only its container identifies it",
            ShortsText.isChrome(text)
        )
    }
}
