package com.shortsense

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small in-memory ring buffer of what the service saw and decided.
 *
 * This exists because screen-reading heuristics are only as good as the device they run
 * on: YouTube's layout, your phone's ROM and its font scale all change what the
 * accessibility tree looks like. When detection misbehaves, this log is the difference
 * between "it doesn't work" and a bug report someone can act on. Nothing is written to
 * disk and it is cleared when the app is killed.
 */
object DebugLog {

    private const val CAPACITY = 250

    data class Entry(val timeMs: Long, val kind: String, val message: String) {
        fun render(): String {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timeMs))
            return "$ts  ${kind.uppercase(Locale.US)}  $message"
        }
    }

    private val entries = ArrayDeque<Entry>()

    @Volatile
    var listener: (() -> Unit)? = null

    fun add(kind: String, message: String) {
        synchronized(entries) {
            entries.addFirst(Entry(System.currentTimeMillis(), kind, message))
            while (entries.size > CAPACITY) entries.removeLast()
        }
        listener?.invoke()
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }
}
