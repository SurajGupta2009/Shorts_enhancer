package com.shortsense.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.shortsense.DebugLog
import com.shortsense.Settings
import com.shortsense.nlp.Classifier
import com.shortsense.nlp.Lexicon
import com.shortsense.nlp.Mode
import com.shortsense.nlp.TinyModel
import com.shortsense.nlp.Verdict

/**
 * The service that watches YouTube Shorts and covers the ones the classifier rejects.
 *
 * Shape of the loop:
 *
 *   accessibility event  ->  debounce 200 ms (YouTube fires a burst per Short)
 *                        ->  read the window, retrying up to 3x if the title has not
 *                            rendered yet (the failure mode documented by everyone who
 *                            has built one of these)
 *                        ->  classify locally (<1 ms)
 *                        ->  keep: get out of the way
 *                            block: show the block screen and count it
 *
 * Guards that exist because the alternative is an app that fights the user:
 *   * the same Short is never blocked twice inside 4 seconds,
 *   * at most 25 block screens a minute, after which the service pauses and says so,
 *   * anything unreadable is kept, never blocked,
 *   * as soon as the foreground app is not YouTube, the block screen disappears.
 */
class ShortsWatcherService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ShortsWatcherService? = null
            private set

        /**
         * Videos are not the only thing this can see, so the package filter runs before
         * anything else. ReVanced builds keep the same UI but a different package name.
         */
        val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube"
        )

        private const val DEBOUNCE_MS = 200L
        private const val RETRY_MS = 250L
        private const val MAX_RETRIES = 3
        private const val SAME_SHORT_COOLDOWN_MS = 4000L
        private const val MAX_BLOCKS_PER_MINUTE = 25
    }

    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: BlockOverlay? = null
    private var classifier: Classifier? = null
    private var loadError: String? = null

    private var pendingEvaluation: Runnable? = null
    private var lastBlockKey: String = ""
    private var lastBlockAt: Long = 0L
    private val blockTimes = ArrayDeque<Long>()
    private var pausedUntil: Long = 0L
    private var wasInShorts = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        settings = Settings(this)
        overlay = BlockOverlay(this)
        loadModel()
        DebugLog.add("info", "service connected (enabled=${settings.enabled}, mode=${settings.mode})")
    }

    private fun loadModel() {
        try {
            val model = TinyModel.load(this)
            val lex = Lexicon.load(this)
            val meta = com.shortsense.nlp.ModelMeta.load(this)
            classifier = Classifier(model, lex, meta)
            DebugLog.add(
                "info",
                "model loaded: ${model.featureCount} features, trained ${meta.trainedAt}, " +
                    "hard-case accuracy ${(meta.hardCaseAccuracy * 100).toInt()}%"
            )
        } catch (t: Throwable) {
            loadError = t.message ?: t.toString()
            DebugLog.add("error", "could not load the model: $loadError")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg !in YOUTUBE_PACKAGES) {
            // The user is somewhere else: never leave anything on screen.
            if (wasInShorts || overlay?.isShowing() == true) {
                dismissOverlay()
                wasInShorts = false
                lastBlockKey = ""
            }
            return
        }
        if (!settings.enabled || classifier == null) {
            dismissOverlay()
            return
        }
        if (System.currentTimeMillis() < pausedUntil) return

        if (ShortsSurface.isShortsWindowClass(event.className)) {
            wasInShorts = true
        }
        scheduleEvaluation(DEBOUNCE_MS)
    }

    private fun scheduleEvaluation(delayMs: Long) {
        pendingEvaluation?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { evaluate(attempt = 0) }
        pendingEvaluation = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun evaluate(attempt: Int) {
        val classifier = this.classifier ?: return
        val root = rootInActiveWindow
        if (root == null) {
            if (attempt < MAX_RETRIES) retry(attempt) else DebugLog.add("warn", "window became unreadable")
            return
        }

        val metrics = resources.displayMetrics
        val surface = ShortsSurface.read(root, readLexicon(), metrics.widthPixels, metrics.heightPixels)

        if (!surface.isShorts) {
            // Either the user is elsewhere in YouTube, or YouTube is still painting the
            // player. If we were just in Shorts, give it another go before giving up.
            if (wasInShorts && attempt < MAX_RETRIES && surface.playerIdSeen == null) {
                retry(attempt)
                return
            }
            if (wasInShorts) {
                wasInShorts = false
                if (settings.debugLogging) DebugLog.add("detect", "left Shorts — ${surface.trace}")
            }
            dismissOverlay()
            return
        }

        wasInShorts = true

        if (surface.title.isBlank() && attempt < MAX_RETRIES) {
            // Title node not rendered yet: this is the documented failure mode, retry.
            retry(attempt)
            return
        }

        val verdict = classifier.classify(
            surface.title,
            surface.channel,
            settings.mode,
            settings.presetIndex,
            settings.rules()
        )

        val where = "title='${surface.title.take(70)}' channel='${surface.channel.take(30)}' " +
            "margin=${"%.1f".format(verdict.margin)} theta=${"%.1f".format(verdict.threshold)} " +
            "p=${"%.2f".format(verdict.confidence)}"

        if (verdict.keep) {
            dismissOverlay()
            if (!verdict.unknown) settings.countAllowed()
            if (settings.debugLogging) {
                DebugLog.add(if (verdict.unknown) "unknown" else "keep", "$where :: ${verdict.reason}")
            }
            return
        }

        // ---- it is a block: apply the guards, then show the screen
        val key = (surface.title + "|" + surface.channel).lowercase()
        val now = System.currentTimeMillis()
        if (key == lastBlockKey && now - lastBlockAt < SAME_SHORT_COOLDOWN_MS) return

        blockTimes.addLast(now)
        while (blockTimes.isNotEmpty() && now - blockTimes.first() > 60_000) blockTimes.removeFirst()
        if (blockTimes.size > MAX_BLOCKS_PER_MINUTE) {
            pausedUntil = now + 60_000
            blockTimes.clear()
            DebugLog.add("warn", "more than $MAX_BLOCKS_PER_MINUTE blocks in a minute — pausing for a minute")
            dismissOverlay()
            return
        }

        lastBlockKey = key
        lastBlockAt = now
        settings.countBlocked()
        if (settings.debugLogging) DebugLog.add("block", "$where :: ${verdict.reason}")
        showBlockScreen(surface.title, surface.channel, verdict)
    }

    private fun retry(attempt: Int) {
        val runnable = Runnable { evaluate(attempt + 1) }
        pendingEvaluation = runnable
        handler.postDelayed(runnable, RETRY_MS)
    }

    private fun showBlockScreen(title: String, channel: String, verdict: Verdict) {
        overlay?.show(
            title = title,
            channel = channel,
            reason = verdict.reason,
            explained = verdict.explained,
            countdownSeconds = settings.countdownSeconds,
            skipsInstead = settings.skipOnTimeout,
            preview = false,
            callbacks = object : BlockOverlay.Callbacks {
                override fun onKeep() {
                    DebugLog.add("user", "kept it: '${title.take(50)}'")
                    dismissOverlay()
                }

                override fun onAlwaysAllow() {
                    if (settings.learnChannels && channel.isNotBlank()) {
                        settings.addChannel("allow", channel)
                        DebugLog.add("user", "always allowing '$channel'")
                    }
                    dismissOverlay()
                }

                override fun onLeaveNow() {
                    if (settings.skipOnTimeout) skipToNextShort() else leaveShorts()
                }
            }
        )
    }

    /** Swipe up inside the feed: lands on the next Short, which is then judged on its own. */
    private fun skipToNextShort() {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels / 2f, metrics.heightPixels * 0.78f)
            lineTo(metrics.widthPixels / 2f, metrics.heightPixels * 0.22f)
        }
        dismissOverlay()
        // the overlay has to be gone before the gesture is injected, or the swipe lands on it
        handler.postDelayed({
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 220))
                .build()
            val ok = dispatchGesture(gesture, null, null)
            DebugLog.add(if (ok) "action" else "warn", "swipe to next Short (${if (ok) "ok" else "rejected"})")
            scheduleEvaluation(320L)
        }, 140)
    }

    private fun leaveShorts() {
        dismissOverlay()
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            DebugLog.add("action", "left Shorts (back)")
            wasInShorts = false
        }, 120)
    }

    private fun readLexicon(): Lexicon = lexicon ?: Lexicon.load(this).also { lexicon = it }
    private var lexicon: Lexicon? = null

    fun dismissOverlay() {
        overlay?.dismiss()
    }

    /** Used by the debug screen: capture and log what the app currently sees. */
    fun captureNow(): String {
        val root = rootInActiveWindow ?: return "no readable window"
        val metrics = resources.displayMetrics
        val surface = ShortsSurface.read(root, readLexicon(), metrics.widthPixels, metrics.heightPixels)
        val line = "shorts=${surface.isShorts} title='${surface.title}' channel='${surface.channel}'"
        DebugLog.add("capture", "$line :: ${surface.trace}")
        return line
    }

    fun classifyForTest(title: String, channel: String): Verdict? =
        classifier?.classify(title, channel, settings.mode, settings.presetIndex, settings.rules())

    fun showPreviewBlock(title: String, channel: String) {
        val verdict = classifier?.classify(title, channel, settings.mode, settings.presetIndex, settings.rules())
        overlay?.show(
            title = title,
            channel = channel,
            reason = verdict?.reason ?: "preview",
            explained = verdict?.explained ?: emptyList(),
            countdownSeconds = settings.countdownSeconds,
            skipsInstead = settings.skipOnTimeout,
            preview = true,
            callbacks = object : BlockOverlay.Callbacks {
                override fun onKeep() = dismissOverlay()
                override fun onAlwaysAllow() = dismissOverlay()
                override fun onLeaveNow() = dismissOverlay()
            }
        )
    }

    override fun onInterrupt() {
        dismissOverlay()
        DebugLog.add("warn", "service interrupted by the system")
    }

    override fun onDestroy() {
        dismissOverlay()
        pendingEvaluation?.let { handler.removeCallbacks(it) }
        instance = null
        DebugLog.add("warn", "service destroyed")
        super.onDestroy()
    }
}
