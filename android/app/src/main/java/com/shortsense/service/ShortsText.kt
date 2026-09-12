package com.shortsense.service

import java.util.Locale

/**
 * Reading YouTube's Shorts UI as text.
 *
 * This is where the reading bugs lived, so it is deliberately free of Android types and
 * unit tested directly against strings copied out of the app's own detection log:
 *
 *   "Making Ohnepixel Gamble To His Death Go to channel @Jettism"
 *        the title and the channel row arrive merged in one node
 *   "Scrolling on Shorts is paused. You can update your limit in settings."
 *        YouTube's own chrome, which was being classified as a title
 *   "Boom Shaka · KR$NA & Dhanda Nyoliwala"
 *        the sound row, which was being classified as a channel
 *   "Mix – Memory reboot (Ultra slowed & reverb) How I Made my own Smart Gl"
 *        sound attribution glued in front of the real title
 *
 * Every rule here exists because one of those actually happened on a phone.
 */
object ShortsText {

    enum class Kind { TITLE, CHANNEL, SOUND, CHROME }

    data class Piece(val text: String, val kind: Kind)

    /** Strings that are UI, never content. Matched after normalisation. */
    private val CHROME_EXACT = setOf(
        "shorts", "short", "subscribe", "subscribed", "like", "likes", "dislike",
        "share", "comments", "comment", "remix", "save", "send", "report", "hide",
        "not interested", "show less", "read more", "more", "less", "cancel", "ok",
        "done", "settings", "help", "send feedback", "search", "home", "trending",
        "subscriptions", "library", "history", "playlists", "downloads", "watch later",
        "liked videos", "your videos", "back", "close", "next", "previous", "play",
        "pause", "mute", "unmute", "autoplay", "up next", "drag handle", "sponsored",
        "promoted", "shop now", "watch on youtube", "open app", "install", "get the app",
        "sign in", "try shorts", "double tap to like", "add to playlist",
        "save to playlist", "copy link", "remix this video", "use this sound",
        "view replies", "reply", "translate", "captions", "turn on captions",
        "quality", "playback speed", "shorts feed", "voice search", "more options",
        "menu", "sound", "original sound", "likes and views", "view all", "see more",
        "learn more", "watch full video", "open in app", "join", "members only",
        "live", "premiere", "shorts remixing this video", "related shorts",
        // from a real device log: YouTube paints these into the same node as the title, and
        // a Short whose title was only one of them got judged on it (a Physics Wallah
        // toppers Short was blocked because its title read as "New content available")
        "new content available", "content available", "pull down to lock 2x speed",
        "pull down to lock 2x", "sort comments", "add a comment", "my ad centre",
        "ad centre", "opens in new tab", "opens a new tab",
    )

    /** Substrings that mark a blob as UI rather than content. */
    private val CHROME_PHRASES = listOf(
        "go to channel",
        "scrolling on shorts is paused",
        "you can update your limit in settings",
        "screen time limit",
        "tap to unmute",
        "double tap",
        "swipe up",
        "swipe down",
        "watching in",
        "add to queue",
        "don't recommend",
        "not interested in this",
        "stop showing",
        "your limit",
        "remind me later",
        "watch on youtube",
        "open the youtube app",
        "installed app",
        "accessibility",
        "sort comments",
        "add a comment",
        "comments.",
        "more replies",
        "report submitted",
        "we will use this information",
        "you shouldn't see this ad",
        "you shouldn\u2019t see this ad",
        "ranked by factors like",
        "as detailed and accurate as possible",
        "opens in new tab",
        "opens a new tab",
        "my ad centre",
        "ad centre",
        "download hd videos",
        "smooth playback directly from your device",
        "explore the possibilities",
        "curious about changes ahead",
        "streamline your workflow",
        "fast answers with google",
        "giving away",
        "gamified learning platform",
        "video chat rooms",
        "1v1 video chat",
        "verified girls",
        "hair generator",
        "ideal hairstyle",
        "iptv player",
        "m3u playlists",
        "free with ads",
        "watch ads to",
    )

    /**
     * Badges YouTube glues into the same node as the title.
     *
     * "Speed Almost Beat TWO Olympic Runners 🥶🔥 New content available" is one node: the
     * title plus a notification badge. Rejecting the whole node would lose the title, and
     * keeping it means the model reads the badge as part of the title - so the badge is cut
     * off, and the title is kept.
     */
    private val GLUED_UI = listOf(
        "new content available",
        "pull down to lock 2x speed",
        "pull down to lock 2x",
        "pull down to unlock",
        "content available",
    )

    /**
     * Cuts the badge off whichever end it is glued to.
     *
     * Anchored on purpose: a badge always sits at the start ("New content available Beauty
     * of binomial") or the end ("… New content available"), so a plain replace would also
     * rewrite a title that merely mentions the words. Leftovers too short to judge are then
     * kept by the classifier's own "fewer than two words" rule.
     */
    fun stripGluedUi(raw: String): String {
        var t = raw.trim()
        var changed = true
        while (changed && t.isNotEmpty()) {
            changed = false
            val low = t.lowercase(Locale.US)
            for (ui in GLUED_UI) {
                if (low.startsWith(ui)) {
                    t = t.substring(ui.length)
                    changed = true
                    break
                }
                if (low.endsWith(ui)) {
                    t = t.substring(0, t.length - ui.length)
                    changed = true
                    break
                }
            }
            t = t.replace(WHITESPACE, " ").trim().trim(',', '|', '·', '•', '-', '—').trim()
        }
        return t
    }

    /**
     * View IDs whose subtrees never contain the video's own title.
     *
     * Comments ("they dont even specialize in running so you gotta compare him to a track
     * runner"), the report sheet ("It violates a specific law or my legal rights") and ad
     * units all rendered text that scored well as titles, so the app judged the Short behind
     * a comment and kept a feedback form. The title lives in the player overlay, so anything
     * under these containers is dropped before it is scored.
     */
    private val NON_CONTENT_IDS = listOf(
        "comment", "reply", "engagement", "bottom_sheet", "sheet_container",
        "ad_badge", "ad_attribution", "sponsored", "promo_", "shopping_",
    )

    /** True for a view ID (and so a subtree) that holds comments, sheets or ad units. */
    fun isNonContentId(rawId: String?): Boolean {
        val id = rawId?.substringAfterLast('/')?.lowercase() ?: return false
        if (id.isEmpty()) return false
        return NON_CONTENT_IDS.any { id.contains(it) }
    }

    /** "…  Go to channel @handle" — the channel row merged into whatever precedes it. */
    private val GO_TO_CHANNEL = Regex(
        """^(.*?)\bgo to channel\b[^@\w]*@?([\w.\-]{2,40})""",
        RegexOption.IGNORE_CASE
    )

    private val HANDLE_ONLY = Regex("""^@([\w.\-]{2,40})$""")

    private val SOUND_WORDS = Regex(
        """slowed|reverb|remix|sped up|speed up|lo-?fi|bass boosted|8d|instrumental|acoustic|nightcore|mashup|karaoke""",
        RegexOption.IGNORE_CASE
    )

    /** "Mix – Memory reboot (Ultra slowed & reverb) " in front of the real title. */
    private val SOUND_PREFIX = Regex(
        """^.{0,90}?\((?=[^()]*?(?:$SOUND_WORDS)[^()]*?\))[^()]*?\)\s*""",
        RegexOption.IGNORE_CASE
    )

    private val WHITESPACE = Regex("""\s+""")

    fun normalise(raw: String): String =
        raw.lowercase(Locale.US).replace(WHITESPACE, " ").trim().trim('•', '·', '-', '—').trim()

    fun isChrome(raw: String): Boolean {
        val t = normalise(raw)
        if (t.isEmpty()) return true
        if (t in CHROME_EXACT) return true
        for (p in CHROME_PHRASES) if (t.contains(p)) return true
        return false
    }

    /**
     * "Boom Shaka · KR$NA & Dhanda Nyoliwala" is a soundtrack, not a channel: the Shorts
     * sound row uses "Artist · Track" (or a bullet). "ChannelName · Subscribe" is the one
     * legitimate use of the bullet, so it is allowed through as a channel.
     */
    fun isSoundRow(raw: String): Boolean {
        val t = normalise(raw)
        if (t.startsWith("original sound")) return true
        if (t.contains(" · ") || t.contains(" • ")) {
            return !(t.endsWith("subscribe") || t.endsWith("subscribed"))
        }
        return false
    }

    fun isSubscribeRow(raw: String): Boolean {
        val t = normalise(raw)
        return t.endsWith("subscribe") || t.endsWith("subscribed")
    }

    /** @return the handle in "Go to channel @handle" or "@handle", otherwise null. */
    fun channelHandle(raw: String): String? {
        GO_TO_CHANNEL.find(raw)?.let { m ->
            val handle = m.groupValues.getOrNull(2)?.trim().orEmpty()
            if (handle.length >= 2) return handle
        }
        HANDLE_ONLY.find(raw.trim())?.let { m -> return m.groupValues[1] }
        return null
    }

    /** Drops a leading soundtrack attribution, keeping the title behind it. */
    fun stripSoundPrefix(raw: String): String {
        val t = raw.trim()
        if (!SOUND_WORDS.containsMatchIn(t)) return t
        val m = SOUND_PREFIX.find(t) ?: return t
        val rest = t.substring(m.range.last + 1).trim()
        val words = rest.count { it == ' ' } + 1
        return if (rest.length >= 15 && words >= 3) rest else t
    }

    /** One accessibility node's text can be several things at once; pull it apart. */
    fun classify(raw: String): List<Piece> {
        val out = ArrayList<Piece>(6)
        for (chunk in split(raw)) {
            // Badges are cut before the row is interpreted, so a blob that was nothing but
            // a badge falls into the length check below. The chrome check must stay where it
            // was, AFTER "Go to channel" is handled: that phrase is itself in the chrome list,
            // and testing it first threw away the title and the channel together.
            val t = stripGluedUi(chunk.trim())
            if (t.length < 3) continue

            val merged = GO_TO_CHANNEL.find(t)
            if (merged != null) {
                val head = merged.groupValues[1].trim()
                if (head.length >= 3 && !isChrome(head)) {
                    out.add(Piece(stripSoundPrefix(head), Kind.TITLE))
                }
                val handle = merged.groupValues[2].trim()
                if (handle.length >= 2) out.add(Piece(handle, Kind.CHANNEL))
                continue
            }

            val handle = HANDLE_ONLY.find(t)?.groupValues?.get(1)
            if (handle != null) {
                out.add(Piece(handle, Kind.CHANNEL))
                continue
            }

            // "Channel Name · Subscribe" is the channel row, not something to classify
            if (isSubscribeRow(t)) {
                val lower = t.lowercase(Locale.US)
                val name = if (lower.endsWith("subscribed")) t.dropLast(10) else t.dropLast(9)
                val cleaned = name.trim().trim('·', '•', '-', '—').trim()
                if (cleaned.length >= 2) out.add(Piece(cleaned, Kind.CHANNEL))
                continue
            }
            if (isSoundRow(t)) continue
            if (isChrome(t)) continue
            out.add(Piece(stripSoundPrefix(t), Kind.TITLE))
        }
        return out
    }

    /** A long blob is often "TITLE, channel, likes, …"; split it so parts are scorable. */
    fun split(raw: String): List<String> {
        val out = ArrayList<String>(4)
        for (line in raw.split('\n')) {
            val l = line.trim()
            if (l.isEmpty()) continue
            if (l.length >= 30) {
                val parts = l.split(", ")
                if (parts.size >= 3) {
                    out.addAll(parts)
                    continue
                }
            }
            out.add(l)
        }
        return out
    }
}
