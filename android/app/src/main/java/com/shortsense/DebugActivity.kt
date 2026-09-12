package com.shortsense

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.shortsense.service.ShortsWatcherService

/**
 * Detection log and classifier test bench.
 *
 * Screen-reading is device-specific, so this is the screen that turns "it doesn't work"
 * into something fixable: it shows what the service read off the Short, what the model
 * made of it, and lets you run any title through the same classifier the service uses.
 */
class DebugActivity : Activity() {

    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.textSize = 12f
                tv.setTextColor(Color.parseColor("#FF2B2B33"))
                return view
            }
        }
        findViewById<ListView>(R.id.logList).adapter = adapter
        DebugLog.listener = { runOnUiThread { reload() } }

        findViewById<Button>(R.id.runTest).setOnClickListener { runClassifierTest() }
        findViewById<Button>(R.id.previewBlock).setOnClickListener { previewBlock() }
        findViewById<Button>(R.id.capture).setOnClickListener {
            val service = ShortsWatcherService.instance
            if (service == null) {
                DebugLog.add("warn", "service is not connected — enable it in accessibility settings")
            } else {
                service.captureNow()
            }
            reload()
        }
        findViewById<Button>(R.id.clearLog).setOnClickListener {
            DebugLog.clear()
            DecisionLog.clear()
            reload()
        }
        findViewById<Button>(R.id.exportLog).setOnClickListener { exportLog() }
        findViewById<Button>(R.id.copyLog).setOnClickListener { copyLog() }
        reload()
    }

    override fun onDestroy() {
        DebugLog.listener = null
        super.onDestroy()
    }

    private fun reload() {
        adapter.clear()
        adapter.addAll(DebugLog.snapshot().map { it.render() })
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.decisionSummary).text = getString(
            R.string.decision_summary,
            DecisionLog.size(),
            DecisionLog.count(DecisionLog.Kind.BLOCK),
            DecisionLog.count(DecisionLog.Kind.KEEP) + DecisionLog.count(DecisionLog.Kind.UNKNOWN),
            DecisionLog.count(DecisionLog.Kind.USER_KEEP) +
                DecisionLog.count(DecisionLog.Kind.USER_ALLOW) +
                DecisionLog.count(DecisionLog.Kind.USER_BLOCK)
        )
    }

    /** Writes the report, then opens the share sheet; nothing is uploaded by this app. */
    private fun exportLog() {
        val summary = findViewById<TextView>(R.id.decisionSummary)
        try {
            val report = LogExporter.build(this)
            summary.text = getString(R.string.export_done, report.decisions, report.blocked, report.kept)
            DebugLog.add("info", "exported ${report.file.name}")
            LogExporter.share(this, report)
        } catch (t: Throwable) {
            summary.text = getString(R.string.export_failed, t.message ?: t.toString())
        }
        reload()
    }

    private fun copyLog() {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("ShortsSense log", LogExporter.report(this, Settings(this)))
        )
        findViewById<TextView>(R.id.decisionSummary).text = "Copied to the clipboard."
    }

    private fun runClassifierTest() {
        val title = findViewById<EditText>(R.id.testTitle).text.toString().trim()
        val channel = findViewById<EditText>(R.id.testChannel).text.toString().trim()
        val result = findViewById<TextView>(R.id.testResult)
        val service = ShortsWatcherService.instance
        if (service == null) {
            result.text = "The accessibility service is not running, so the model is not loaded yet. " +
                "Enable it in accessibility settings and reopen this screen."
            return
        }
        val verdict = service.classifyForTest(title, channel)
        if (verdict == null) {
            result.text = "Model not loaded."
            return
        }
        val features = com.shortsense.nlp.Features.features(
            title, channel, com.shortsense.nlp.Lexicon.load(this)
        )
        val words = verdict.explained.joinToString("\n  ") {
            "${it.first}  (${"%.2f".format(it.second)})"
        }
        result.text = buildString {
            append(if (verdict.keep) "KEEP\n" else "BLOCK\n")
            append("reason: ${verdict.reason}\n")
            append("margin ${"%.2f".format(verdict.margin)} vs threshold ${"%.2f".format(verdict.threshold)}\n")
            append("confidence it is useful: ${(verdict.confidence * 100).toInt()}%\n")
            append("${features.size} features extracted\n")
            if (words.isNotEmpty()) append("what mattered:\n  $words")
        }
        DebugLog.add("test", "title='$title' channel='$channel' -> ${if (verdict.keep) "keep" else "block"} " +
            "(${"%.2f".format(verdict.margin)})")
        reload()
    }

    private fun previewBlock() {
        val service = ShortsWatcherService.instance
        if (service == null) {
            findViewById<TextView>(R.id.testResult).text =
                "Enable the accessibility service first — the block screen is drawn by it."
            return
        }
        val title = findViewById<EditText>(R.id.testTitle).text.toString().trim()
            .ifBlank { "sigma rule no 1 🔥🔥 (preview)" }
        service.showPreviewBlock(title, findViewById<EditText>(R.id.testChannel).text.toString().trim())
    }
}
