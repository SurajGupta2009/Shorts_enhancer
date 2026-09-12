package com.shortsense.service

/**
 * The rules that stop the block screen from turning into a strobe light.
 *
 * Two failures are baked into this class, both seen on a phone:
 *
 *  * the block screen is itself an accessibility window and its countdown rewrites its text
 *    four times a second. Those events used to be read as "the foreground app is no longer
 *    YouTube", which dismissed the screen, which let the Short be detected again, which
 *    showed the screen again - a flash loop, one cycle per countdown tick. (The service now
 *    ignores its own package entirely; this class is the second line of defence.)
 *
 *  * when "Take me out" does not actually leave Shorts (BACK does nothing on some builds),
 *    the same Short was blocked again after every countdown, forever. Now the same Short
 *    seen again after an exit attempt gets [Action.SHOW_STABLE]: the screen stays put with
 *    no countdown ticking, because a screen that keeps closing itself is worse than none.
 *
 * Pure logic, no Android types, so it is unit tested directly.
 */
class BlockPolicy(
    private val sameShortCooldownMs: Long = 4_000,
    private val userKeptMs: Long = 60_000,
    private val exitRetryWindowMs: Long = 30_000
) {

    enum class Action {
        /** Already handled a moment ago: show nothing, log nothing. */
        SKIP,

        /** Show the block screen with the usual countdown. */
        SHOW,

        /** Show it without a countdown: the previous exit did not work. */
        SHOW_STABLE
    }

    private var lastKey = ""
    private var lastAt = 0L
    private var exitKey = ""
    private var exitAt = 0L
    private var keptKey = ""
    private var keptAt = 0L

    fun onBlock(key: String, now: Long): Action {
        if (key.isEmpty()) return Action.SKIP
        // the same Short re-read a moment later is not a new Short
        if (key == lastKey && now - lastAt < sameShortCooldownMs) return Action.SKIP
        // the user said "keep watching anyway": respect that for a while
        if (key == keptKey && now - keptAt < userKeptMs) return Action.SKIP
        // we tried to leave this Short and it is still here: stop the countdown
        if (key == exitKey && now - exitAt < exitRetryWindowMs) return Action.SHOW_STABLE
        lastKey = key
        lastAt = now
        return Action.SHOW
    }

    /** The user tapped "Take me out" (or the countdown ran out) for this Short. */
    fun onExitAttempt(key: String, now: Long) {
        exitKey = key
        exitAt = now
    }

    /** The user tapped "Keep watching anyway" for this Short. */
    fun onUserKept(key: String, now: Long) {
        keptKey = key
        keptAt = now
    }

    /** The user is no longer in Shorts: forget everything about the last one. */
    fun onLeftShorts() {
        lastKey = ""
        lastAt = 0L
        exitKey = ""
        exitAt = 0L
    }
}
