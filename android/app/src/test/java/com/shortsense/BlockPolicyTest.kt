package com.shortsense

import com.shortsense.service.BlockPolicy
import org.junit.Assert.assertEquals
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
}
