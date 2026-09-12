package com.shortsense

import com.shortsense.service.BlockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The anti-flicker rules.
 *
 * On a phone these failures looked like this: the block screen appeared and vanished a few
 * times a second, and the detection log filled with the same decision repeated in pairs.
 * The screen is itself an accessibility window, so its own countdown events were read as
 * "the user left YouTube", which dismissed it, which let the Short be found again.
 */
class BlockPolicyTest {

    private val policy = BlockPolicy()
    private val short = "title|channel"

    @Test
    fun `the same short is not blocked twice in a row`() {
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 1_000))
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock(short, 1_500))
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock(short, 3_000))
        // after the cooldown it may be shown again (the user may have re-opened it)
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 5_500))
    }

    @Test
    fun `a different short is blocked immediately`() {
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock("a|chan", 1_000))
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock("b|chan", 1_100))
    }

    @Test
    fun `if the exit did not work the screen stops counting down`() {
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 1_000))
        policy.onExitAttempt(short, 4_000)
        // the countdown ran out, we pressed back, and the same Short is still here
        assertEquals(BlockPolicy.Action.SHOW_STABLE, policy.onBlock(short, 6_000))
        // and it keeps waiting for the user rather than re-opening itself
        assertEquals(BlockPolicy.Action.SHOW_STABLE, policy.onBlock(short, 20_000))
    }

    @Test
    fun `keeping a short is respected for a minute`() {
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 1_000))
        policy.onUserKept(short, 1_500)
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock(short, 5_000))
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock(short, 40_000))
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 90_000))
    }

    @Test
    fun `leaving shorts forgets the exit that failed`() {
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 1_000))
        policy.onExitAttempt(short, 1_200)
        assertEquals(BlockPolicy.Action.SHOW_STABLE, policy.onBlock(short, 6_000))
        policy.onLeftShorts()
        // back in Shorts with the same Short on screen: a fresh start, countdown restored
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 6_500))
    }

    @Test
    fun `a short the user chose to keep stays kept when they come back to it`() {
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(short, 1_000))
        policy.onUserKept(short, 1_100)
        policy.onLeftShorts()
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock(short, 2_000))
    }

    @Test
    fun `an empty key is never shown`() {
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock("", 1_000))
    }

    // ---- the "warning appears for a second, disappears, repeats" report ---------------

    @Test
    fun `a standing block screen is never taken down by a read that found no Shorts`() {
        // The overlay stops YouTube painting the player behind it, so the very next read
        // says "no Shorts here". Releasing the screen on that read is what made the warning
        // flash and come back: the Short was still playing, and got blocked again.
        for (reads in 1..5) {
            assertEquals(
                "read #$reads while blocking",
                BlockPolicy.ScreenHold.HOLD,
                policy.onLostShorts(overlayShowing = true, consecutiveReads = reads)
            )
        }
    }

    @Test
    fun `with no screen up it takes two agreeing reads to leave Shorts`() {
        assertEquals(BlockPolicy.ScreenHold.HOLD, policy.onLostShorts(false, 1))
        assertEquals(BlockPolicy.ScreenHold.RELEASE, policy.onLostShorts(false, 2))
        assertEquals(BlockPolicy.ScreenHold.RELEASE, policy.onLostShorts(false, 9))
    }

    @Test
    fun `one Short keeps one identity however YouTube re-renders the row`() {
        val a = policy.keyOf("Top 10 Memes of the week 😂", "@Meme Factory India")
        val b = policy.keyOf("Top 10 Memes of the week 😂", "Meme Factory India · Subscribe")
        val c = policy.keyOf("  Top 10 memes of the WEEK!  ", "Meme Factory India")
        assertEquals(a, b)
        assertEquals(a, c)
        // ...but two genuinely different Shorts stay different
        assertNotEquals(a, policy.keyOf("Top 10 Memes of the week 😂", "Prank King"))
        assertNotEquals(a, policy.keyOf("Top 10 fails of the week 😂", "Meme Factory India"))
    }

    @Test
    fun `identity keeps Hindi titles distinct`() {
        // folding non-Latin letters away would have made every Devanagari title collide
        val a = policy.keyOf("यूपीएससी की तैयारी", "Study IQ")
        val b = policy.keyOf("नीट की तैयारी", "Study IQ")
        assertNotEquals(a, b)
        assertTrue(a.isNotEmpty())
    }

    @Test
    fun `a re-read with the glued channel row still counts as the same Short`() {
        // this is the pair that used to defeat the cooldown and re-block a Short forever
        val first = policy.keyOf("Trapping The Sweatiest PVPers in Minecraft", "Jettism")
        assertEquals(BlockPolicy.Action.SHOW, policy.onBlock(first, 1_000))
        val reread = policy.keyOf("Trapping The Sweatiest PVPers in Minecraft", "Jettism · Subscribe")
        assertEquals(BlockPolicy.Action.SKIP, policy.onBlock(reread, 2_000))
    }
}
