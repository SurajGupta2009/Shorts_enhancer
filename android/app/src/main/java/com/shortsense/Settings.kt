package com.shortsense

import android.content.Context
import android.content.SharedPreferences
import com.shortsense.nlp.ChannelRules
import com.shortsense.nlp.Channels
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

    /**
     * Adds a channel to the allow or block list. The name is normalised the same way the
     * classifier normalises what it reads off the screen (see [Channels]); without that,
     * "Add" appeared to do nothing because the two strings never matched.
     */
    fun addChannel(kind: String, raw: String) {
        val name = Channels.normalize(raw)
        if (name.isEmpty()) return
        val edit = prefs.edit()
        val set = channelSet(kind).toMutableSet()
        set.add(name)
        edit.putStringSet("channels_$kind", set)
        // a channel cannot be in both lists - the later choice wins and the old one is dropped
        val other = if (kind == "allow") "block" else "allow"
        val otherSet = channelSet(other).toMutableSet()
        if (otherSet.removeAll { Channels.normalize(it) == name }) {
            edit.putStringSet("channels_$other", otherSet)
        }
        edit.apply()
    }

    /**
     * Counts "keep" taps per channel, so the app can learn which channels the user keeps
     * choosing instead of guessing from a single tap. Keyed by the normalised name, the
     * same normalisation the lists use, so a learn event can never invent a duplicate
     * spelling of a channel that is already allowed.
     */
    fun bumpKeep(raw: String): Int {
        val name = Channels.normalize(raw)
        if (name.isEmpty()) return 0
        val next = keepCount(name) + 1
        prefs.edit().putInt("keep_$name", next).apply()
        return next
    }

    fun keepCount(raw: String): Int {
        val name = Channels.normalize(raw)
        if (name.isEmpty()) return 0
        return prefs.getInt("keep_$name", 0)
    }

    fun clearKeepCounts() {
        val edit = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith("keep_")) edit.remove(key)
        }
        edit.apply()
    }

    fun removeChannel(kind: String, raw: String) {
        val name = Channels.normalize(raw)
        if (name.isEmpty()) return
        val set = channelSet(kind).toMutableSet()
        set.removeAll { Channels.normalize(it) == name }
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
