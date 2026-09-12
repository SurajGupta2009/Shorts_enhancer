package com.shortsense.service

/**
 * Which accessibility events the service is allowed to act on.
 *
 * This exists because getting it wrong makes the app unusable in a way that is hard to
 * diagnose from a screenshot: the block screen is itself an accessibility window, and its
 * countdown rewrites its text four times a second. Treating those events as "somebody else
 * came to the foreground" dismissed the screen, which let the Short be found again, which
 * showed the screen again - a flash loop, one cycle per countdown tick.
 *
 * Pure logic with no Android types, so the rule is unit tested rather than trusted.
 */
object EventRoute {

    enum class Route {
        /** Nothing to do. */
        IGNORE,

        /** Another app is in front: take the block screen down. */
        DISMISS_OVERLAY,

        /** YouTube is in front: read the screen and decide. */
        EVALUATE
    }

    /**
     * AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, as a literal on purpose: reading an
     * Android constant from a unit test would drag the framework in for no reason.
     */
    const val WINDOW_STATE_CHANGED = 32

    fun decide(
        eventPackage: String?,
        eventType: Int,
        ownPackage: String,
        youtubePackages: Set<String>,
        wasInShorts: Boolean,
        overlayShowing: Boolean
    ): Route {
        if (eventPackage == null) return Route.IGNORE
        // our own block screen: its text changes are our own doing, never a signal
        if (eventPackage == ownPackage) return Route.IGNORE
        if (eventPackage in youtubePackages) return Route.EVALUATE
        // A stranger: only a real window change means the user left YouTube. A notification
        // shade, a keyboard or a toast must not tear the block screen down.
        val foregroundSwitch = eventType == WINDOW_STATE_CHANGED
        return if (foregroundSwitch && (wasInShorts || overlayShowing)) Route.DISMISS_OVERLAY else Route.IGNORE
    }
}
