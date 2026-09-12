package com.shortsense

import com.shortsense.service.EventRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that ended the flicker. Written as a test because the bug it prevents is
 * invisible in code review and very visible on a phone: the block screen appearing and
 * disappearing several times a second.
 */
class EventRouteTest {

    private val youtube = setOf("com.google.android.youtube", "app.revanced.android.youtube")
    private val own = "com.shortsense"
    private val windowStateChanged = EventRoute.WINDOW_STATE_CHANGED
    private val contentChanged = 2048  // AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    private fun route(pkg: String?, type: Int = contentChanged, inShorts: Boolean = true, showing: Boolean = true) =
        EventRoute.decide(pkg, type, own, youtube, inShorts, showing)

    @Test
    fun `the app never reacts to its own block screen`() {
        // this is what made the screen flicker: the countdown rewrites its text 4x a second
        assertEquals(EventRoute.Route.IGNORE, route(own))
        assertEquals(EventRoute.Route.IGNORE, route(own, windowStateChanged))
    }

    @Test
    fun `youTube events are evaluated`() {
        assertEquals(EventRoute.Route.EVALUATE, route("com.google.android.youtube"))
        assertEquals(EventRoute.Route.EVALUATE, route("app.revanced.android.youtube"))
    }

    @Test
    fun `another app coming to the front takes the block screen down`() {
        assertEquals(EventRoute.Route.DISMISS_OVERLAY, route("com.whatsapp", windowStateChanged))
    }

    @Test
    fun `a keyboard or a notification in front of youtube does not`() {
        assertEquals(EventRoute.Route.IGNORE, route("com.google.android.inputmethod.latin"))
        assertEquals(EventRoute.Route.IGNORE, route("com.android.systemui"))
    }

    @Test
    fun `we only dismiss when there is something to dismiss`() {
        assertEquals(
            EventRoute.Route.IGNORE,
            route("com.whatsapp", windowStateChanged, inShorts = false, showing = false)
        )
    }

    @Test
    fun `a missing package name is ignored`() {
        assertEquals(EventRoute.Route.IGNORE, route(null))
    }
}
