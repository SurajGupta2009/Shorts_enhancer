package com.shortsense.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.shortsense.DebugLog
import com.shortsense.DecisionLog
import com.shortsense.R
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
 *   * the same Short is never blocked twice inside 4 seconds (see [BlockPolicy]),
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
        private const val MAX_BLOCKS_PER_MINUTE = 25

        /** "Learn from my taps": keeps on the same channel before it is allow-listed. */
        private const val LEARN_AFTER_KEEPS = 3

        /** How long a confirmation stays on the block screen before it acts. */
        private const val CONFIRM_MS = 1100L
    }

    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: BlockOverlay? = null
    private var classifier: Classifier? = null
    private var loadError: String? = null

    private var pendingEvaluation: Runnable? = null
    private val policy = BlockPolicy()
    private var showingKey: String = ""
    private var suppressUntil: Long = 0L
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

        // Which events may this service act on? See EventRoute: reacting to our own block
        // screen's countdown was the flicker.
        val route = EventRoute.decide(
            eventPackage = event.packageName?.toString(),
            eventType = event.eventType,
            ownPackage = packageName,
            youtubePackages = YOUTUBE_PACKAGES,
            wasInShorts = wasInShorts,
            overlayShowing = overlay?.isShowing() == true
        )
        if (route == EventRoute.Route.IGNORE) return
        if (route == EventRoute.Route.DISMISS_OVERLAY) {
            dismissOverlay()
            wasInShorts = false
            policy.onLeftShorts()
            return
        }

        if (!settings.enabled || classifier == null) {
            dismissOverlay()
            return
        }
        val now = System.currentTimeMillis()
        if (now < pausedUntil || now < suppressUntil) return

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

    /**
     * The window belonging to YouTube, not whatever happens to be on top of it.
     *
     * The block screen covers the whole display, so `rootInActiveWindow` can return our own
     * overlay - and then the app reads its own words ("Not for you right now") as if they
     * were a Short title, decides to keep, dismisses the screen, sees the Short again, shows
     * the screen again. That loop was the other half of the flickering.
     */
    private fun youtubeRoot(): AccessibilityNodeInfo? {
        return try {
            val mine = ArrayList<AccessibilityNodeInfo>(2)
            for (w in windows) {
                val r = w.root ?: continue
                val p = r.packageName?.toString()
                if (p != null && p in YOUTUBE_PACKAGES) mine.add(r)
            }
            if (mine.isEmpty()) {
                rootInActiveWindow
            } else {
                mine.firstOrNull { ShortsSurface.detectShortsByIds(it) != null } ?: mine.first()
            }
        } catch (t: Throwable) {
            rootInActiveWindow
        }
    }

    private fun evaluate(attempt: Int) {
        val classifier = this.classifier ?: return
        val root = youtubeRoot()
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
            // A screen the app could not read must not tear down a block screen that is
            // standing: the same Short would be blocked again a moment later, which looks
            // like flicker. Only a Short that was read and judged keepable clears it.
            if (!verdict.unknown || overlay?.isShowing() != true) dismissOverlay()
            if (!verdict.unknown) settings.countAllowed()
            DecisionLog.add(
                if (verdict.unknown) DecisionLog.Kind.UNKNOWN else DecisionLog.Kind.KEEP,
                surface.title, surface.channel, verdict.margin, verdict.threshold
            )
            if (settings.debugLogging) {
                DebugLog.add(if (verdict.unknown) "unknown" else "keep", "$where :: ${verdict.reason}")
            }
            return
        }

        // ---- it is a block: apply the guards, then show the screen
        val key = (surface.title + "|" + surface.channel).lowercase()
        val now = System.currentTimeMillis()

        // already up for this exact Short: never re-add it because YouTube painted again
        if (overlay?.isShowing() == true && key == showingKey) return

        val action = policy.onBlock(key, now)
        if (action == BlockPolicy.Action.SKIP) return
        val exitFailed = action == BlockPolicy.Action.SHOW_STABLE

        blockTimes.addLast(now)
        while (blockTimes.isNotEmpty() && now - blockTimes.first() > 60_000) blockTimes.removeFirst()
        if (blockTimes.size > MAX_BLOCKS_PER_MINUTE) {
            pausedUntil = now + 60_000
            blockTimes.clear()
            DebugLog.add("warn", "more than $MAX_BLOCKS_PER_MINUTE blocks in a minute — pausing for a minute")
            dismissOverlay()
            return
        }

        settings.countBlocked()
        DecisionLog.add(
            DecisionLog.Kind.BLOCK, surface.title, surface.channel, verdict.margin, verdict.threshold
        )
        if (settings.debugLogging) {
            val extra = if (exitFailed) " (no countdown: the last exit did not take)" else ""
            DebugLog.add("block", "$where :: ${verdict.reason}$extra")
        }
        // a Short the exit could not remove gets a screen that stays put
        val countdown = if (exitFailed) 0 else settings.countdownSeconds
        showBlockScreen(surface.title, surface.channel, verdict, countdown, exitFailed)
    }

    private fun retry(attempt: Int) {
        val runnable = Runnable { evaluate(attempt + 1) }
        pendingEvaluation = runnable
        handler.postDelayed(runnable, RETRY_MS)
    }

    private fun showBlockScreen(
        title: String,
        channel: String,
        verdict: Verdict,
        countdownSeconds: Int,
        exitFailed: Boolean
    ) {
        val key = (title + "|" + channel).lowercase()
        showingKey = key
        overlay?.show(
            title = title,
            channel = channel,
            reason = verdict.reason,
            explained = verdict.explained,
            countdownSeconds = countdownSeconds,
            skipsInstead = settings.skipOnTimeout,
            preview = false,
            note = if (exitFailed) getString(R.string.blocked_exit_failed) else null,
            callbacks = object : BlockOverlay.Callbacks {
                override fun onKeep() {
                    policy.onUserKept(key, System.currentTimeMillis())
                    DecisionLog.add(DecisionLog.Kind.USER_KEEP, title, channel, verdict.margin, verdict.threshold)
                    DebugLog.add("user", "kept it: '${title.take(50)}'")
                    // "Learn from my taps": enough keeps on the same channel and it is allowed.
                    if (settings.learnChannels && channel.isNotBlank()) {
                        val times = settings.bumpKeep(channel)
                        if (times >= LEARN_AFTER_KEEPS) {
                            settings.addChannel("allow", channel)
                            overlay?.say(getString(R.string.learned_channel, channel, times))
                            DebugLog.add("user", "learned to allow '$channel' after $times keeps")
                            handler.postDelayed({ dismissOverlay() }, CONFIRM_MS)
                            return
                        }
                        DebugLog.add("user", "kept '$channel' ($times/$LEARN_AFTER_KEEPS towards auto-allow)")
                    }
                    dismissOverlay()
                }

                override fun onAlwaysAllow() {
                    policy.onUserKept(key, System.currentTimeMillis())
                    if (channel.isBlank()) {
                        // no silent no-op: say what happened and keep the Short
                        overlay?.say(getString(R.string.no_channel_read))
                        DebugLog.add("warn", "allow tapped but no channel was read")
                        handler.postDelayed({ dismissOverlay() }, CONFIRM_MS)
                        return
                    }
                    settings.addChannel("allow", channel)
                    DecisionLog.add(DecisionLog.Kind.USER_ALLOW, title, channel, verdict.margin, verdict.threshold)
                    DebugLog.add("user", "always allowing '$channel'")
                    overlay?.say(getString(R.string.allow_added, channel))
                    handler.postDelayed({ dismissOverlay() }, CONFIRM_MS)
                }

                override fun onNeverShow() {
                    policy.onUserKept(key, System.currentTimeMillis())
                    if (channel.isBlank()) {
                        overlay?.say(getString(R.string.no_channel_read))
                        handler.postDelayed({ dismissOverlay() }, CONFIRM_MS)
                        return
                    }
                    settings.addChannel("block", channel)
                    DecisionLog.add(DecisionLog.Kind.USER_BLOCK, title, channel, verdict.margin, verdict.threshold)
                    DebugLog.add("user", "never showing '$channel'")
                    overlay?.say(getString(R.string.block_added, channel))
                    // they do not want this one: move on once the confirmation has been read
                    handler.postDelayed({
                        if (settings.skipOnTimeout) skipToNextShort() else leaveShorts()
                    }, CONFIRM_MS)
                }

                override fun onLeaveNow() {
                    policy.onExitAttempt(key, System.currentTimeMillis())
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
        // let the feed settle before judging again, or the Short we just left is re-read
        suppressUntil = System.currentTimeMillis() + 1500
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
        suppressUntil = System.currentTimeMillis() + 1500
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
        showingKey = ""
    }

    /** Used by the debug screen: capture and log what the app currently sees. */
    fun captureNow(): String {
        val root = youtubeRoot() ?: return "no readable window"
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
