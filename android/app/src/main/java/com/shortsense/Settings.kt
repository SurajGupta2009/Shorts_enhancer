package com.shortsense

import android.content.Context
import android.content.SharedPreferences
import com.shortsense.nlp.ChannelRules
import com.shortsense.nlp.Mode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the user can change, plus the two counters on the home screen.
 * Plain SharedPreferences: no database, no background sync, nothing to leak.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("shortsense", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var mode: Mode
        get() = if (prefs.getString("mode", "informative") == "study") Mode.STUDY else Mode.INFORMATIVE
        set(v) = prefs.edit().putString("mode", if (v == Mode.STUDY) "study" else "informative").apply()

    /** Index into the model's preset table: 0 = fewest blocks, 4 = strictest. */
    var presetIndex: Int
        get() = prefs.getInt("preset", 2)
        set(v) = prefs.edit().putInt("preset", v.coerceIn(0, 4)).apply()

    /** 0 means "no countdown": the block screen waits for the user. */
    var countdownSeconds: Int
        get() = prefs.getInt("countdown", 3)
        set(v) = prefs.edit().putInt("countdown", v.coerceIn(0, 10)).apply()

    /** true  = countdown swipes to the next Short
     *  false = countdown leaves Shorts altogether */
    var skipOnTimeout: Boolean
        get() = prefs.getBoolean("skip_on_timeout", true)
        set(v) = prefs.edit().putBoolean("skip_on_timeout", v).apply()

    var learnChannels: Boolean
        get() = prefs.getBoolean("learn_channels", true)
        set(v) = prefs.edit().putBoolean("learn_channels", v).apply()

    var debugLogging: Boolean
        get() = prefs.getBoolean("debug_logging", true)
        set(v) = prefs.edit().putBoolean("debug_logging", v).apply()

    fun rules(): ChannelRules = ChannelRules(allowed = channelSet("allow"), blocked = channelSet("block"))

    fun channelSet(kind: String): Set<String> =
        prefs.getStringSet("channels_$kind", emptySet())?.toMutableSet() ?: mutableSetOf()

    fun addChannel(kind: String, raw: String) {
        val name = raw.trim().lowercase().removePrefix("@")
        if (name.isEmpty()) return
        val set = channelSet(kind).toMutableSet()
        set.add(name)
        prefs.edit().putStringSet("channels_$kind", set).apply()
    }

    fun removeChannel(kind: String, raw: String) {
        val set = channelSet(kind).toMutableSet()
        set.remove(raw.trim().lowercase().removePrefix("@"))
        prefs.edit().putStringSet("channels_$kind", set).apply()
    }

    // ---- counters -------------------------------------------------------------

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    var blockedToday: Int
        get() = prefs.getInt("blocked_" + today(), 0)
        set(v) = prefs.edit().putInt("blocked_" + today(), v).apply()

    var allowedToday: Int
        get() = prefs.getInt("allowed_" + today(), 0)
        set(v) = prefs.edit().putInt("allowed_" + today(), v).apply()

    fun countBlocked() {
        blockedToday += 1
    }

    fun countAllowed() {
        allowedToday += 1
    }

    fun resetCounters() {
        prefs.edit().putInt("blocked_" + today(), 0).putInt("allowed_" + today(), 0).apply()
    }
}
