package com.shortsense.service

import com.shortsense.nlp.Channels
import java.util.Locale

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
    private val exitRetryWindowMs: Long = 30_000,
    private val lostReadConfirmations: Int = 2
) {

    enum class Action {
        /** Already handled a moment ago: show nothing, log nothing. */
        SKIP,

        /** Show the block screen with the usual countdown. */
        SHOW,

        /** Show it without a countdown: the previous exit did not work. */
        SHOW_STABLE
    }

    /** What to do about a screen the app is already showing. */
    enum class ScreenHold {
        /** Leave it standing. */
        HOLD,

        /** YouTube is genuinely gone: take it down. */
        RELEASE
    }

    /**
     * Is this the same Short as last time?
     *
     * Identity has to survive the way YouTube re-renders: the channel row gains and loses
     * "· Subscribe", a title picks up a stray bullet or extra spaces between reads. Two
     * spellings of one Short used to look like two different Shorts, so the cooldowns below
     * never applied and the same video was blocked again and again.
     */
    fun keyOf(title: String, channel: String): String {
        val t = fold(title)
        val c = Channels.normalize(channel)
        return if (c.isEmpty()) t else "$t|$c"
    }

    /** Lowercase, punctuation and emoji folded to single spaces, so "Top 10! 😂" == "top 10". */
    private fun fold(raw: String): String {
        val sb = StringBuilder(raw.length)
        var lastWasSpace = true
        for (ch in raw.lowercase(Locale.US)) {
            // Both combining-mark classes count as letters: Devanagari keeps its vowel
            // marks in the non-spacing category, but several Indic scripts put them in the
            // spacing one, and dropping either would merge different titles.
            val combines = Character.getType(ch) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(ch) == Character.COMBINING_SPACING_MARK.toInt()
            if (ch.isLetterOrDigit() || combines) {
                // letters, digits and the vowel marks of Indic scripts: all of them are part
                // of the title, and dropping the marks would make two different Hindi or
                // Marathi titles fold into the same identity
                sb.append(ch)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                // any separator - space, "·", "—", emoji - is one space, and never a leading
                // one, so a title that gains a stray bullet keeps the same identity
                sb.append(' ')
                lastWasSpace = true
            }
        }
        return sb.toString().trim()
    }

    /**
     * A read that found no Shorts content.
     *
     * A standing block screen is never taken down by this: the block screen is a window over
     * YouTube, YouTube stops painting the player behind it, and the app then reads "no Shorts
     * here" as if the user had left. It dismissed its own screen, the Short came back, and it
     * blocked it again a few seconds later - the "warning appears for a second, goes away,
     * repeats" report. Only a real foreground switch (see [EventRoute]) releases a screen
     * that is already up, or [lostReadConfirmations] reads in a row that agree.
     */
    fun onLostShorts(overlayShowing: Boolean, consecutiveReads: Int): ScreenHold {
        if (overlayShowing) return ScreenHold.HOLD
        return if (consecutiveReads >= lostReadConfirmations) ScreenHold.RELEASE else ScreenHold.HOLD
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
