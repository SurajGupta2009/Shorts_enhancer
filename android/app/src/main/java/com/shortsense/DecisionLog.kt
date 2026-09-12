package com.shortsense

/**
 * Every decision the app made, in a form that can be read by a human and by a script.
 *
 * [DebugLog] is for reading on the phone; this is for exporting. The distinction matters
 * because the exported file is how a wrong decision becomes a fixed one: each row carries
 * the title, the channel, the margin, the threshold and whether the user overruled it, so
 * the rows can be pasted straight into the hand-labelled case set the model is scored on
 * (`model/hard_cases.py`).
 *
 * Kept in memory only. Nothing leaves the phone unless the user exports it.
 */
object DecisionLog {

    enum class Kind(val label: String) {
        KEEP("keep"),
        BLOCK("block"),
        UNKNOWN("unknown"),
        USER_KEEP("user-keep"),
        USER_ALLOW("user-allow"),
        USER_BLOCK("user-block"),
        LEARN("learn")
    }

    data class Decision(
        val timeMs: Long,
        val kind: Kind,
        val title: String,
        val channel: String,
        val margin: Double,
        val threshold: Double
    )

    private const val CAPACITY = 800

    private val entries = ArrayDeque<Decision>()

    /** Collapses an identical decision repeated by YouTube's repaint storm. */
    private var lastSignature = ""
    private var lastSignatureAt = 0L
    private const val DUPLICATE_WINDOW_MS = 1_500L

    fun add(kind: Kind, title: String, channel: String, margin: Double, threshold: Double) {
        val now = System.currentTimeMillis()
        val signature = kind.name + "\u0000" + title.lowercase() + "\u0000" + channel.lowercase()
        val fresh = synchronized(entries) {
            if (signature == lastSignature && now - lastSignatureAt < DUPLICATE_WINDOW_MS) return
            lastSignature = signature
            lastSignatureAt = now
            entries.addFirst(Decision(now, kind, title, channel, margin, threshold))
            while (entries.size > CAPACITY) entries.removeLast()
            true
        }
        if (!fresh) return
    }

    fun snapshot(): List<Decision> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    fun count(kind: Kind): Int = synchronized(entries) { entries.count { it.kind == kind } }

    fun size(): Int = synchronized(entries) { entries.size }

    /** Tab separated, one decision per line, for spreadsheets and for the training set. */
    fun toTsv(): String {
        val out = StringBuilder()
        out.append("# time\tkind\ttitle\tchannel\tmargin\tthreshold\tuser_overruled\n")
        for (d in snapshot()) {
            val overruled = d.kind == Kind.USER_KEEP || d.kind == Kind.USER_ALLOW ||
                d.kind == Kind.USER_BLOCK
            out.append(fmt(d.timeMs)).append('\t')
                .append(d.kind.label).append('\t')
                .append(clean(d.title)).append('\t')
                .append(clean(d.channel)).append('\t')
                .append(String.format("%.3f", d.margin)).append('\t')
                .append(String.format("%.3f", d.threshold)).append('\t')
                .append(if (overruled) "yes" else "no")
                .append('\n')
        }
        return out.toString()
    }

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').trim()

    private fun fmt(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(ms))
}
