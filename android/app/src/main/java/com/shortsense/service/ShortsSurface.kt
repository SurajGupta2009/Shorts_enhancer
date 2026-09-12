package com.shortsense.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.shortsense.nlp.Lexicon

/**
 * Is this the Shorts player, and if so, what is on it?
 *
 * Two lessons from the projects that came before this one shape everything here:
 *
 * 1. Resource IDs are necessary but not sufficient. `reel_watch_fragment_root` and
 *    `reel_recycler` are the canonical Shorts markers, but YouTube renders asynchronously
 *    and they intermittently fail to appear on an event; the fix is to re-read the window
 *    a few times rather than trust one read.
 * 2. Only player-only IDs may count. `shorts_pivot_tab_label`, `reel_shelf_item` and
 *    `shorts_shelf_header_endpoint` exist on the home screen, and matching those would
 *    block all of YouTube - the exact thing this app must never do.
 *
 * The title is not read from one blessed node either: YouTube moves it between versions.
 * Every text node is scored and the best one wins, and whatever the app read is shown on
 * the block screen and written to the detection log, so a wrong read is visible rather
 * than mysterious. Taking a node's text at face value is not enough - the player merges
 * the title, the channel row and the sound row into single nodes - so the text is handed
 * to [ShortsText], which splits those blobs and throws away YouTube's own chrome.
 */
object ShortsSurface {

    /** IDs that only occur inside the Shorts player. */
    private val PLAYER_IDS = listOf(
        "reel_watch_fragment_root",   // primary marker, several independent sources
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_player",
        "reel_watch_fragment_container",
        "reel_progress_bar",
        "reel_time_bar",
        "shorts_container",
        "shorts_player_root",
        "shorts_player_sheet",
        "shorts_video_header",
        "shorts_vertical_feed_container",
    )

    /** Home-feed / navigation IDs that mean "not the player". */
    private val SHELF_IDS = listOf(
        "shorts_pivot_tab_label", "reel_shelf_item", "shorts_shelf_header_endpoint",
        "reel_shelf", "shorts_shelf", "shorts_pivot_button"
    )

    private val CLASS_HINTS = listOf("shorts", "reelwatch")

    private const val MAX_NODES = 4000
    private const val MAX_DEPTH = 40

    data class Result(
        val isShorts: Boolean,
        val title: String,
        val channel: String,
        val trace: String,
        val playerIdSeen: String?
    )

    fun isShortsWindowClass(className: CharSequence?): Boolean {
        val cls = className?.toString()?.lowercase() ?: return false
        return CLASS_HINTS.any { cls.contains(it) }
    }

    /** Walk the tree looking for player-only IDs. Home-shelf IDs veto. */
    fun detectShortsByIds(root: AccessibilityNodeInfo): String? {
        var found: String? = null
        var sawShelf = false
        walk(root) { node ->
            val id = node.viewIdResourceName ?: return@walk
            val short = id.substringAfterLast('/')
            if (SHELF_IDS.any { short.contains(it) }) sawShelf = true
            if (found == null && PLAYER_IDS.any { short.contains(it) }) found = short
        }
        return if (sawShelf && found == null) null else found
    }

    /**
     * Read the Short on screen. Retries are handled by the caller; this is one attempt.
     */
    fun read(root: AccessibilityNodeInfo, lex: Lexicon, width: Int, height: Int): Result {
        val playerId = detectShortsByIds(root)

        val titleCandidates = ArrayList<Candidate>()
        val channelCandidates = ArrayList<Candidate>()
        val seen = HashSet<String>()

        walk(root) { node ->
            val raw = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?: return@walk
            val id = node.viewIdResourceName?.substringAfterLast('/') ?: ""
            val cls = node.className?.toString() ?: ""

            for (piece in ShortsText.classify(raw)) {
                when (piece.kind) {
                    ShortsText.Kind.CHROME, ShortsText.Kind.SOUND -> return@walk

                    ShortsText.Kind.CHANNEL -> {
                        val text = piece.text.trim()
                        if (text.length < 2) return@walk
                        if (!seen.add("chan:" + text.lowercase())) return@walk
                        channelCandidates.add(Candidate(text, channelScore(text, id), id))
                    }

                    ShortsText.Kind.TITLE -> {
                        val text = piece.text.trim()
                        if (text.length < 3) return@walk
                        if (isButtonOrChrome(cls, id)) return@walk
                        if (!seen.add(text.lowercase())) return@walk
                        val score = score(text, id, node, width, height, lex) ?: return@walk
                        titleCandidates.add(Candidate(text, score, id))
                    }
                }
            }
        }

        titleCandidates.sortByDescending { it.score }
        channelCandidates.sortByDescending { it.score }

        val title = buildTitle(titleCandidates)
        val channel = channelCandidates.firstOrNull()?.text?.trim()?.removePrefix("@") ?: ""

        val trace = buildString {
            append("player=").append(playerId ?: "none")
            append(" titles=[")
            titleCandidates.take(3).forEach {
                append("'").append(it.text.take(42)).append("'(").append(it.id).append(", ")
                append(String.format("%.1f", it.score)).append(") ")
            }
            append("] channels=[")
            channelCandidates.take(2).forEach {
                append("'").append(it.text.take(24)).append("'(")
                append(String.format("%.1f", it.score)).append(") ")
            }
            append("]")
        }

        return Result(playerId != null, title, channel, trace, playerId)
    }

    /** Titles are sometimes split across two nodes (main line + hashtags line). */
    private fun buildTitle(sorted: List<Candidate>): String {
        val best = sorted.firstOrNull() ?: return ""
        if (best.text.length >= 60) return best.text.take(180)
        val second = sorted.drop(1).firstOrNull { it.score >= best.score * 0.7 } ?: return best.text
        val joined = best.text + " " + second.text
        return if (joined.length <= 190) joined else best.text
    }

    private class Candidate(val text: String, val score: Double, val id: String)

    private fun isButtonOrChrome(className: String, id: String): Boolean {
        val cls = className.lowercase()
        if (cls.contains("button") && !id.contains("title") && !id.contains("header")) return true
        if (cls.contains("edittext")) return true
        return false
    }

    /**
     * Which channel candidate to believe. A handle pulled out of a "Go to channel @x" row is
     * the strongest signal there is; a long blob with spaces in it is usually a soundtrack
     * or a merged header, so it is pushed down rather than thrown away.
     */
    private fun channelScore(text: String, id: String): Double {
        var s = 1.0
        if (id.contains("channel") || id.contains("owner") || id.contains("handle") ||
            id.contains("avatar") || id.contains("creator")
        ) s += 2.0
        if (id.contains("sound") || id.contains("music") || id.contains("audio")) s -= 2.0
        if (text.length <= 40) s += 0.5
        if (!text.contains(' ')) s += 0.5
        if (text.length > 40) s -= 1.5
        if (ShortsText.isSubscribeRow(text)) s += 1.0
        return s
    }

    /**
     * Returns a score for "this looks like the title of the Short", or null to discard.
     * Bigger is better; the arithmetic is arbitrary but deliberately conservative:
     * discarding every candidate is a valid (safe) outcome, because the app then keeps
     * the Short instead of blocking something it cannot read.
     */
    private fun score(
        text: String,
        id: String,
        node: AccessibilityNodeInfo,
        width: Int,
        height: Int,
        lex: Lexicon
    ): Double? {
        if (lex.isUiNoise(text)) return null
        if (ShortsText.isChrome(text)) return null
        val letters = text.count { it.isLetter() }
        if (letters < 4) return null
        if (text.length > 400) return null

        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return null
        val inBottomBand = r.centerY() > height * 0.33 && r.centerY() < height * 1.02
        val leftAligned = r.left < width * 0.7

        var score = 0.0
        if (id.contains("title") || id.contains("reel_metadata") || id.contains("video_title")) score += 3.0
        if (id.contains("shorts_video_header") || id.contains("reel_player_overlay") ||
            id.contains("reel_player")
        ) score += 2.0
        if (id.contains("sound") || id.contains("music") || id.contains("audio")) score -= 2.0
        if (inBottomBand) score += 1.5
        if (leftAligned) score += 0.5
        score += (minOf(text.length, 140)) / 60.0
        if (text.contains(' ')) score += 0.5
        if (text.endsWith("Subscribe") || text.endsWith("subscribed")) score -= 2.0
        if (text.length < 8) score -= 1.0
        return score
    }

    private inline fun walk(root: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        var visited = 0
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(Pair(root, 0))
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (depth > MAX_DEPTH || visited++ > MAX_NODES) continue
            action(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                stack.addLast(Pair(child, depth + 1))
            }
        }
    }
}
