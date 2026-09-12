package com.shortsense

import com.shortsense.nlp.Channels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the "add this channel does nothing" bug.
 *
 * The failure was never in the list itself: the settings screen stored what the user typed,
 * the service compared it to the text read off the screen, and the two never matched. So
 * these tests are about the shape of real screen text ("@Physics Wallah · Subscribe")
 * meeting a typed name ("Physics Wallah"), not about set membership.
 */
class ChannelsTest {

    @Test
    fun normalize_stripsTheDecorationYouTubeAddsToAChannelRow() {
        assertEquals("physics wallah", Channels.normalize("@Physics Wallah"))
        assertEquals("physics wallah", Channels.normalize("  Physics   Wallah  "))
        assertEquals("physics wallah", Channels.normalize("Physics Wallah · Subscribe"))
        assertEquals("physics wallah", Channels.normalize("Physics Wallah - Subscribe"))
        assertEquals("physics wallah", Channels.normalize("PHYSICS WALLAH"))
        assertEquals("", Channels.normalize(null))
        assertEquals("", Channels.normalize("   "))
        assertEquals("", Channels.normalize("@ · Subscribe"))
    }

    @Test
    fun normalize_keepsNamesThatOnlyLookLikeDecorations() {
        // "Follow" is part of these names, not a trailing button
        assertEquals("follow the science", Channels.normalize("Follow The Science"))
        assertEquals("subscribed daily", Channels.normalize("Subscribed Daily"))
        // a row that holds nothing but the button text must normalise to nothing, so a stray
        // tap can never create a rule that matches every channel
        assertEquals("", Channels.normalize("Subscribed"))
        assertEquals("", Channels.normalize("Follow"))
        // the '@' inside a name stays put
        assertEquals("a@b", Channels.normalize("a@b"))
    }

    @Test
    fun matches_acceptsTheScreenTextForATypedName() {
        val stored = setOf("Physics Wallah")
        assertTrue(Channels.matches(stored, "@Physics Wallah"))
        assertTrue(Channels.matches(stored, "Physics Wallah · Subscribe"))
        assertTrue(Channels.matches(stored, "physics wallah"))
        assertTrue(Channels.matches(stored, "  Physics Wallah  "))
    }

    @Test
    fun matches_allowsASuffixOnTheChannelButNotADifferentChannel() {
        val stored = setOf("physics wallah")
        // channel names grow suffixes - Hindi, Shorts, Clips - and the user means the channel
        assertTrue(Channels.matches(stored, "Physics Wallah Hindi"))
        assertTrue(Channels.matches(stored, "Physics Wallah - Shorts"))
        // ...but not a different channel that merely starts with the same letters
        assertFalse(Channels.matches(stored, "Physics Wallahya"))
        assertFalse(Channels.matches(stored, "Physics"))
        assertFalse(Channels.matches(stored, "Not Physics Wallah"))
    }

    @Test
    fun matches_isFalseWhenThereIsNothingToCompare() {
        assertFalse(Channels.matches(setOf("physics wallah"), null))
        assertFalse(Channels.matches(setOf("physics wallah"), "   "))
        assertFalse(Channels.matches(setOf("physics wallah"), "· Subscribe"))
        assertFalse(Channels.matches(emptySet(), "Physics Wallah"))
    }

    @Test
    fun matches_ignoresEntriesThatNormaliseToNothing() {
        // a stray "· Subscribe" entry must not turn into a match-everything rule
        val stored = setOf("· Subscribe", "@", "")
        assertFalse(Channels.matches(stored, "Physics Wallah"))
        assertFalse(Channels.matches(stored, "· Subscribe"))
    }

    @Test
    fun same_comparesTwoNamesForTheLists() {
        assertTrue(Channels.same("Physics Wallah", "@physics wallah · Subscribe"))
        assertFalse(Channels.same("Physics Wallah", "Physics Wallah Hindi"))
        // empty names never compare equal, so a blank entry cannot shadow a real one
        assertFalse(Channels.same("", ""))
        assertFalse(Channels.same(null, "Physics Wallah"))
    }
}
