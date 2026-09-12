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
    private val presetBudget = listOf(1, 2, 5, 10, 20)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        findViewById<Button>(R.id.openSettings).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.openDebug).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
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

        // strictness
        val strictness = findViewById<SeekBar>(R.id.strictness)
        strictness.max = presetNames.size - 1
        strictness.progress = settings.presetIndex
        strictness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                settings.presetIndex = value
                refresh()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

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
            text = getString(if (settings.enabled && running) R.string.status_on else R.string.status_off)
            setTextColor(getColor(if (settings.enabled && running) R.color.accent else R.color.danger))
        }
        findViewById<TextView>(R.id.statusHint).text = getString(
            if (settings.enabled && running) R.string.status_hint_on else R.string.status_hint_off
        )

        findViewById<TextView>(R.id.strictnessLabel).text =
            "${presetNames[settings.presetIndex]} — blocks about ${presetBudget[settings.presetIndex]}% of useful Shorts"
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

    private fun openAccessibilitySettings() {
        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
    }
}
