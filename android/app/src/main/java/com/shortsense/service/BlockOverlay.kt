package com.shortsense.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.shortsense.R
import kotlin.math.roundToInt

/**
 * The block screen: a full-screen, touch-consuming panel drawn as
 * TYPE_ACCESSIBILITY_OVERLAY (so it needs no "display over other apps" permission).
 *
 * It always shows three things, because a blocker that will not explain itself is
 * indistinguishable from a bug:
 *   * what the app read off the screen (title and channel),
 *   * what it decided and why, including the words that tipped the decision,
 *   * a countdown to the default action, with an obvious way to overrule it.
 *
 * Built in code rather than inflated from XML so it cannot be broken by a theme an
 * accessibility service does not inherit.
 */
class BlockOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var timer: CountDownTimer? = null
    private var countdownText: TextView? = null

    fun isShowing(): Boolean = view != null

    interface Callbacks {
        fun onKeep()
        fun onAlwaysAllow()
        fun onLeaveNow()
    }

    fun show(
        title: String,
        channel: String,
        reason: String,
        explained: List<Pair<String, Double>>,
        countdownSeconds: Int,
        skipsInstead: Boolean,
        preview: Boolean,
        callbacks: Callbacks
    ) {
        dismiss()
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.getColor(R.color.overlay_scrim))
            gravity = Gravity.CENTER
            isClickable = true          // swallow taps aimed at the video underneath
            isFocusable = true
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#FF15151C"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(20), dp(22), dp(20), dp(18))
        }

        card.addView(label("Not for you right now", 20f, Color.WHITE, bold = true))
        if (preview) {
            card.addView(label("PREVIEW — this is what a blocked Short looks like", 11f, Color.parseColor("#FF9AD5C8")))
        }

        val shown = when {
            title.isBlank() -> context.getString(R.string.blocked_unknown)
            channel.isBlank() -> "\u201C$title\u201D"
            else -> "\u201C$title\u201D\n$channel"
        }
        card.addView(label(shown, 15f, Color.parseColor("#FFEDEDF2")).apply {
            setPadding(0, dp(10), 0, 0)
        })

        card.addView(label(reason, 13f, Color.parseColor("#FFB9B9C7")).apply {
            setPadding(0, dp(10), 0, 0)
        })

        val why = explained
            .filter { it.second > 0.02 || it.second < -0.02 }
            .joinToString(", ") { "\u201C${prettyFeature(it.first)}\u201D" }
        if (why.isNotEmpty()) {
            card.addView(label(if (it(explained) > 0) "read as useful because of $why" else "read as junk because of $why",
                12f, Color.parseColor("#FF8E8E9E")).apply { setPadding(0, dp(6), 0, 0) })
        }

        val counter = label("", 13f, Color.parseColor("#FF9AD5C8")).apply {
            setPadding(0, dp(12), 0, dp(8))
        }
        countdownText = counter
        card.addView(counter)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(button(context.getString(R.string.blocked_keep), fill = false) { callbacks.onKeep() })
        row.addView(button(context.getString(R.string.blocked_allow), fill = false) { callbacks.onAlwaysAllow() })
        card.addView(row)
        card.addView(button(context.getString(R.string.blocked_exit), fill = true) { callbacks.onLeaveNow() })

        root.addView(card, LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT))
        view = root

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        windowManager.addView(root, params)

        if (countdownSeconds > 0 && !preview) {
            val label = if (skipsInstead) R.string.blocked_countdown_skip_fmt else R.string.blocked_countdown_fmt
            timer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 250) {
                override fun onTick(msLeft: Long) {
                    val s = ((msLeft + 999) / 1000).toInt()
                    countdownText?.text = context.getString(label, s)
                }

                override fun onFinish() {
                    callbacks.onLeaveNow()
                }
            }.start()
        } else {
            counter.visibility = View.GONE
        }
    }

    /** "k:e:vlog" -> "vlog"; "ph:i:how_to" -> "how to"; keeps the screen readable. */
    private fun prettyFeature(raw: String): String {
        val parts = raw.split(':')
        val tail = parts.last()
        return tail.replace('_', ' ').take(28)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun button(text: String, fill: Boolean, onClick: () -> Unit): Button =
        Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(6, 0, 6, 0)
            layoutParams = lp
            if (fill) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.setMargins(0, 10, 0, 0)
            }
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(if (fill) context.getColor(R.color.accent) else Color.parseColor("#FF26262F"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }

    fun dismiss() {
        timer?.cancel()
        timer = null
        countdownText = null
        view?.let {
            runCatching { windowManager.removeView(it) }
            view = null
        }
    }
}
