package com.shortsense

import android.content.Context
import android.os.Build
import com.shortsense.nlp.ModelMeta
import com.shortsense.service.ShortsWatcherService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the exportable log.
 *
 * The file is deliberately self-contained: version, device, settings, model metadata, the
 * decision table and the human-readable log. When detection misbehaves, this one file is
 * everything needed to reproduce it, and the decision table doubles as labelled training
 * data for the model.
 *
 * It is written to the app's own files directory - no storage permission, nothing public -
 * and shared through [ExportProvider], which hands the receiving app a read-only handle on
 * that one file.
 */
object LogExporter {

    const val DIR = "exports"

    data class Report(val file: File, val decisions: Int, val blocked: Int, val kept: Int)

    fun build(context: Context): Report {
        val settings = Settings(context)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        // keep the newest few only: this is a diagnostic, not a data hoard
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }

        val file = File(dir, "shortsense-log-$stamp.txt")
        val decisions = DecisionLog.snapshot()
        file.writeText(report(context, settings, decisions), Charsets.UTF_8)

        return Report(
            file = file,
            decisions = decisions.size,
            blocked = DecisionLog.count(DecisionLog.Kind.BLOCK),
            kept = DecisionLog.count(DecisionLog.Kind.KEEP) + DecisionLog.count(DecisionLog.Kind.UNKNOWN)
        )
    }

    fun report(
        context: Context,
        settings: Settings,
        decisions: List<DecisionLog.Decision> = DecisionLog.snapshot()
    ): String = buildString {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName} ($code)"
        }.getOrDefault("unknown")

        appendLine("ShortsSense export")
        appendLine("generated      : ${
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        }")
        appendLine("app            : $version")
        appendLine("device         : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("screen         : ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} @ ${context.resources.displayMetrics.density}")
        appendLine("service        : connected=${ShortsWatcherService.instance != null}, enabled=${settings.enabled}, mode=${settings.mode}, preset=${settings.presetIndex}")
        val meta = runCatching { ModelMeta.load(context) }.getOrNull()
        if (meta != null) {
            appendLine("model          : ${meta.features} features, trained ${meta.trainedAt}, " +
                "hard-case accuracy ${(meta.hardCaseAccuracy * 100).toInt()}% " +
                "(useful blocked ${(meta.hardCaseUsefulBlocked * 100).toInt()}%, " +
                "junk kept ${(meta.hardCaseJunkKept * 100).toInt()}%)")
        }
        appendLine("counts today   : blocked=${settings.blockedToday}, let through=${settings.allowedToday}")
        appendLine("allow list     : ${settings.channelSet("allow").sorted().joinToString(", ").ifEmpty { "(empty)" }}")
        appendLine("block list     : ${settings.channelSet("block").sorted().joinToString(", ").ifEmpty { "(empty)" }}")
        appendLine("learn channels : ${settings.learnChannels}, countdown=${settings.countdownSeconds}s, skip=${settings.skipOnTimeout}")
        appendLine()

        appendLine("## decisions (newest first): ${decisions.size} rows")
        appendLine("## kind=keep/block is what the model decided; user-keep/user-allow/user-block are your taps.")
        appendLine("## the rows with user_overruled=yes are training data: the model got those wrong.")
        append(DecisionLog.toTsv())
        appendLine()

        appendLine("## detection log (newest first)")
        for (e in DebugLog.snapshot()) appendLine(e.render())
    }

    fun share(context: Context, report: Report) {
        val uri = ExportProvider.uriFor(report.file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "ShortsSense log")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "${report.decisions} decisions, ${report.blocked} blocked, ${report.kept} kept."
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "Share the ShortsSense log")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
