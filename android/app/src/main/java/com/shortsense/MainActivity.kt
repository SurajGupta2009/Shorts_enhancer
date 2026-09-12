package com.shortsense

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.shortsense.nlp.Mode
import com.shortsense.service.ShortsWatcherService

/**
 * Settings. Every control here maps to something the service actually does; there is no
 * screen of options that quietly does nothing.
 */
class MainActivity : Activity() {

    private lateinit var settings: Settings

    private val presetNames = listOf(
        "Fewest interruptions", "Gentle", "Balanced", "Strict", "Maximum filtering"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        findViewById<Button>(R.id.openSettings).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.openDebug).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
        findViewById<Button>(R.id.exportFromMain).setOnClickListener { exportLog() }
        findViewById<Button>(R.id.resetStats).setOnClickListener {
            settings.resetCounters()
            refresh()
        }

        // mode
        val (radioInfo, radioStudy) = findViewById<RadioButton>(R.id.modeInformative) to
            findViewById<RadioButton>(R.id.modeStudy)
        radioInfo.isChecked = settings.mode == Mode.INFORMATIVE
        radioStudy.isChecked = settings.mode == Mode.STUDY
        findViewById<android.widget.RadioGroup>(R.id.modeGroup).setOnCheckedChangeListener { _, id ->
            settings.mode = if (id == R.id.modeStudy) Mode.STUDY else Mode.INFORMATIVE
            refresh()
        }

        // strictness: five named buttons, each quoting what it actually measured on the
        // hand-written cases. A slider here was a trap - one stray nudge during a scroll
        // moved the user from Balanced to Strict without them noticing.
        renderPresets()

        // action
        val skip = findViewById<Switch>(R.id.switchSkip)
        skip.isChecked = settings.skipOnTimeout
        skip.setOnCheckedChangeListener { _, checked ->
            settings.skipOnTimeout = checked
            refresh()
        }
        val countdown = findViewById<SeekBar>(R.id.countdown)
        countdown.max = 10
        countdown.progress = settings.countdownSeconds
        countdown.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                settings.countdownSeconds = value
                refresh()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        // channels
        val learn = findViewById<Switch>(R.id.switchLearn)
        learn.isChecked = settings.learnChannels
        learn.setOnCheckedChangeListener { _, checked -> settings.learnChannels = checked }

        findViewById<Button>(R.id.addAllowChannel).setOnClickListener {
            val field = findViewById<EditText>(R.id.newAllowChannel)
            val value = field.text.toString()
            if (value.isBlank()) {
                Toast.makeText(this, "Type a channel name first", Toast.LENGTH_SHORT).show()
            } else {
                settings.addChannel("allow", value)
                field.setText("")
                refresh()
            }
        }

        val debug = findViewById<Switch>(R.id.switchDebug)
        debug.isChecked = settings.debugLogging
        debug.setOnCheckedChangeListener { _, checked ->
            settings.debugLogging = checked
            DebugLog.add("info", "detection log ${if (checked) "on" else "off"}")
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val running = ShortsWatcherService.instance != null
        findViewById<TextView>(R.id.statusTitle).apply {
            text = if (settings.enabled && running) "● ON" else "● OFF"
            setTextColor(getColor(if (settings.enabled && running) R.color.accent_bright else R.color.danger_bright))
        }
        findViewById<TextView>(R.id.statusHint).text = getString(
            if (settings.enabled && running) R.string.status_hint_on else R.string.status_hint_off
        )

        val meta = runCatching { com.shortsense.nlp.ModelMeta.load(this) }.getOrNull()
        val rung = settings.presetIndex.coerceIn(0, presetNames.size - 1)
        val threshold = meta?.informativePresets?.getOrNull(rung)
        val blockedNow = meta?.presetUsefulBlocked?.getOrNull(rung)
        val junkNow = meta?.presetJunkKept?.getOrNull(rung)
        findViewById<TextView>(R.id.strictnessLabel).text = buildString {
            append("On: ").append(presetNames[rung])
            if (threshold != null) {
                append(" (threshold ").append(String.format(java.util.Locale.US, "%+.1f", threshold)).append(")")
            }
            if (blockedNow != null && junkNow != null) {
                append(" — blocks ${pct(blockedNow)} of useful Shorts, lets ${pct(junkNow)} of junk through")
            }
        }
        findViewById<TextView>(R.id.strictnessExplain).text = explainPreset(settings.presetIndex)

        findViewById<TextView>(R.id.countdownLabel).text = if (settings.countdownSeconds == 0) {
            "Countdown: off — the block screen waits for you"
        } else {
            "Countdown: ${settings.countdownSeconds}s, then " +
                if (settings.skipOnTimeout) "skip to the next Short" else "leave Shorts"
        }

        findViewById<TextView>(R.id.statsLine).text =
            getString(R.string.stats_fmt, settings.blockedToday, settings.allowedToday)

        renderChannelList()
        renderModelInfo()
    }

    private fun renderPresets() {
        val box = findViewById<LinearLayout>(R.id.presetBox)
        box.removeAllViews()
        val meta = runCatching { com.shortsense.nlp.ModelMeta.load(this) }.getOrNull()
        val group = android.widget.RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in presetNames.indices) {
            val blocked = meta?.presetUsefulBlocked?.getOrNull(i)
            val junk = meta?.presetJunkKept?.getOrNull(i)
            val threshold = meta?.informativePresets?.getOrNull(i)
            val measured = if (blocked != null && junk != null) {
                " — blocks ${pct(blocked)} of useful Shorts, lets ${pct(junk)} of junk through"
            } else {
                ""
            }
            val label = if (threshold != null) {
                "${presetNames[i]} (threshold ${String.format(java.util.Locale.US, "%+.1f", threshold)})$measured"
            } else {
                presetNames[i] + measured
            }
            group.addView(RadioButton(this).apply {
                id = 1000 + i
                text = label
                textSize = 13f
                setPadding(4, 16, 4, 16)
                isChecked = i == settings.presetIndex
            })
        }
        group.setOnCheckedChangeListener { _, id ->
            val index = id - 1000
            if (index in presetNames.indices && index != settings.presetIndex) {
                settings.presetIndex = index
                refresh()
            }
        }
        box.addView(group)
    }

    /** 0.008 -> "0.8%", 0.0 -> "0%": a small rate must not read as exactly zero. */
    private fun pct(v: Double): String {
        val p = v * 100.0
        return when {
            p <= 0.0 -> "0%"
            p < 1.0 -> String.format(java.util.Locale.US, "%.1f%%", p)
            else -> String.format(java.util.Locale.US, "%.0f%%", p)
        }
    }

    private fun explainPreset(index: Int): String = when (index) {
        0 -> "Only the most obvious junk gets blocked."
        1 -> "Blocks clearly irrelevant Shorts, lets borderline ones through."
        2 -> "The default: catches most junk while keeping nearly everything useful."
        3 -> "Blocks more, at the cost of the occasional useful Short."
        else -> "For when you want Shorts almost gone. Expect false blocks."
    }

    private fun renderChannelList() {
        val box = findViewById<LinearLayout>(R.id.allowListBox)
        box.removeAllViews()
        val channels = settings.channelSet("allow").sorted()
        findViewById<TextView>(R.id.allowEmpty).text = if (channels.isEmpty()) {
            "No channels allow-listed yet. Every channel is judged by its titles; add the ones you always want."
        } else {
            "Tap a channel to remove it."
        }
        for (name in channels) {
            val row = TextView(this).apply {
                text = "@$name"
                textSize = 15f
                setTextColor(getColor(R.color.text))
                setPadding(8, 18, 8, 18)
                gravity = Gravity.START
                setOnClickListener {
                    settings.removeChannel("allow", name)
                    refresh()
                }
            }
            box.addView(row)
        }
    }

    /** The About card quotes the model's own validation numbers instead of adjectives. */
    private fun renderModelInfo() {
        val about = findViewById<TextView>(R.id.aboutBody)
        if (about.text.contains("Model:")) return
        val meta = runCatching { com.shortsense.nlp.ModelMeta.load(this) }.getOrNull()
        if (meta == null) return
        about.append(
            "\n\nModel: ${meta.features} features, trained ${meta.trainedAt}. " +
                "On a hand-written set of borderline titles it kept " +
                "${((1 - meta.hardCaseUsefulBlocked) * 100).toInt()}% of the useful ones and " +
                "blocked ${((1 - meta.hardCaseJunkKept) * 100).toInt()}% of the junk " +
                "(${(meta.hardCaseAccuracy * 100).toInt()}% of decisions correct overall)."
        )
    }

    /** Same export as the debug screen: useful when something went wrong and you want it out. */
    private fun exportLog() {
        try {
            val report = LogExporter.build(this)
            Toast.makeText(
                this,
                getString(R.string.export_done, report.decisions, report.blocked, report.kept),
                Toast.LENGTH_LONG
            ).show()
            LogExporter.share(this, report)
        } catch (t: Throwable) {
            Toast.makeText(this, getString(R.string.export_failed, t.message ?: "?"), Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
    }
}
