package com.shortsense.nlp

import java.util.Locale

/**
 * Channel names, normalised in exactly one place.
 *
 * This exists because the allow/block lists silently did not work: the settings screen
 * stored a channel as "physics wallah" while the classifier compared the raw text it read
 * off the screen, so "@Physics Wallah", "Physics Wallah · Subscribe" or "Physics Wallah "
 * never matched what the user had typed. Tapping "Add" looked like it did nothing.
 *
 * A stored name matches a channel when the two agree after normalisation, or when the
 * channel simply continues the stored name ("physics wallah hindi" matches "Physics
 * Wallah"). Continuations are allowed because channel names get suffixes - Hindi, Shorts,
 * Clips, 2.0 - and a user who allow-lists a name means the channel, not the exact string.
 */
object Channels {

    private val WHITESPACE = Regex("""\s+""")

    /** Trailing words YouTube appends to a channel row. */
    private val TAIL = Regex("""[\s·•\-–—]*(subscribe|subscribed|follow|following)\s*$""")

    fun normalize(raw: String?): String {
        var t = (raw ?: "").lowercase(Locale.US).replace(WHITESPACE, " ").trim()
        if (t.isEmpty()) return ""
        t = t.removePrefix("@").trim()
        t = t.trim('·', '•', '-', '–', '—', ' ').trim()
        t = TAIL.replace(t, "").trim()
        return t.trim('·', '•', '-', '–', '—', ' ').trim()
    }

    /** True when [channel] is covered by any name in [stored]. */
    fun matches(stored: Set<String>, channel: String?): Boolean {
        val c = normalize(channel)
        if (c.isEmpty()) return false
        for (s in stored) {
            val n = normalize(s)
            if (n.isEmpty()) continue
            if (n == c) return true
            if (c.length > n.length && c.startsWith(n) && c[n.length] == ' ') return true
        }
        return false
    }

    fun same(a: String?, b: String?): Boolean {
        val x = normalize(a)
        return x.isNotEmpty() && x == normalize(b)
    }
}
