package com.jarvis.ai

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private val MP = ViewGroup.LayoutParams.MATCH_PARENT
private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

private fun shape(fill: Int, radius: Float, strokeColor: Int, strokeWidth: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = radius
    g.setColor(fill)
    if (strokeWidth > 0) {
        g.setStroke(strokeWidth, strokeColor)
    }
    return g
}

private fun ripple(content: GradientDrawable): RippleDrawable {
    return RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), content, null)
}

private fun flatten(b: Button) {
    b.setAllCaps(false)
    b.minWidth = 0
    b.minHeight = 0
    b.minimumWidth = 0
    b.minimumHeight = 0
    b.stateListAnimator = null
}

private fun styleChip(b: Button, d: Float) {
    flatten(b)
    b.textSize = 12f
    b.setTextColor(Color.parseColor("#C9D6E4"))
    val h = (10 * d).toInt()
    val v = (8 * d).toInt()
    b.setPadding(h, v, h, v)
    b.background = ripple(
        shape(Color.parseColor("#141C26"), 100 * d, Color.parseColor("#223042"), (1 * d).toInt())
    )
}

private fun styleIcon(b: Button, label: String, size: Float) {
    flatten(b)
    b.text = label
    b.textSize = size
    b.setTextColor(Color.parseColor("#C9D6E4"))
    b.setPadding(0, 0, 0, 0)
    b.gravity = Gravity.CENTER
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(Color.TRANSPARENT)
    b.background = ripple(g)
}

private fun styleRound(b: Button, label: String, fill: Int, textColor: Int) {
    flatten(b)
    b.text = label
    b.textSize = 18f
    b.setTextColor(textColor)
    b.setPadding(0, 0, 0, 0)
    b.gravity = Gravity.CENTER
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(fill)
    b.background = ripple(g)
}

fun applyCleanUi(act: Activity, layout: LinearLayout) {
    try {
        buildCleanUi(act, layout)
    } catch (e: Exception) {
    }
}

private fun buildCleanUi(act: Activity, layout: LinearLayout) {
    if (layout.childCount != 9) return
    val title = layout.getChildAt(0) as? TextView ?: return
    val rowA = layout.getChildAt(1) as? LinearLayout ?: return
    val rowB = layout.getChildAt(2) as? LinearLayout ?: return
    val rowC = layout.getChildAt(3) as? LinearLayout ?: return
    val keyRow = layout.getChildAt(4) as? LinearLayout ?: return
    val scroll = layout.getChildAt(5) as? ScrollView ?: return
    val status = layout.getChildAt(6) as? TextView ?: return
    val inputRow = layout.getChildAt(7) as? LinearLayout ?: return
    val actionRow = layout.getChildAt(8) as? LinearLayout ?: return
    if (keyRow.childCount != 2 || inputRow.childCount != 3 || actionRow.childCount != 3) return
    val keyInput = keyRow.getChildAt(0) as? EditText ?: return
    val saveKey = keyRow.getChildAt(1) as? Button ?: return
    val input = inputRow.getChildAt(0) as? EditText ?: return
    val cam = inputRow.getChildAt(1) as? Button ?: return
    val gal = inputRow.getChildAt(2) as? Button ?: return
    val send = actionRow.getChildAt(0) as? Button ?: return
    val mic = actionRow.getChildAt(1) as? Button ?: return
    val stop = actionRow.getChildAt(2) as? Button ?: return
    val chatView = scroll.getChildAt(0) as? TextView ?: return

    val d = act.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * d + 0.5f).toInt()
    fun margin(v: View, l: Int, t: Int, r: Int, b: Int) {
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.setMargins(dp(l), dp(t), dp(r), dp(b))
        v.layoutParams = lp
    }

    val cyan = Color.parseColor("#00E5FF")
    val soft = Color.parseColor("#C9D6E4")
    val card = Color.parseColor("#121A24")
    val line = Color.parseColor("#223042")
    val hasKey = (act.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        .getString("key", "") ?: "").isNotEmpty()

    layout.removeAllViews()
    inputRow.removeAllViews()
    actionRow.removeAllViews()
    layout.setBackgroundColor(Color.parseColor("#0B0F14"))
    layout.setPadding(dp(16), dp(24), dp(16), dp(12))

    // Settings panel (hidden unless the key is missing)
    for (row in listOf(rowA, rowB, rowC)) {
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i)
            if (v is Button) {
                styleChip(v, d)
                margin(v, 3, 3, 3, 3)
            }
        }
    }
    keyInput.textSize = 14f
    keyInput.setPadding(dp(12), dp(10), dp(12), dp(10))
    keyInput.background = shape(Color.parseColor("#141C26"), 12 * d, line, (1 * d).toInt())
    margin(keyInput, 3, 3, 3, 3)
    styleChip(saveKey, d)
    margin(saveKey, 3, 3, 3, 3)

    val panel = LinearLayout(act)
    panel.orientation = LinearLayout.VERTICAL
    panel.background = shape(Color.parseColor("#10161F"), 16 * d, Color.parseColor("#1E2A38"), (1 * d).toInt())
    panel.setPadding(dp(8), dp(8), dp(8), dp(8))
    panel.addView(rowA, LinearLayout.LayoutParams(MP, WC))
    panel.addView(rowB, LinearLayout.LayoutParams(MP, WC))
    panel.addView(rowC, LinearLayout.LayoutParams(MP, WC))
    panel.addView(keyRow, LinearLayout.LayoutParams(MP, WC))
    panel.visibility = if (hasKey) View.GONE else View.VISIBLE

    // Header
    title.textSize = 20f
    title.letterSpacing = 0.12f
    title.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    styleChip(stop, d)
    stop.text = "■ Stop"
    val gear = Button(act)
    styleIcon(gear, "⚙", 20f)
    gear.setOnClickListener {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
    val header = LinearLayout(act)
    header.orientation = LinearLayout.HORIZONTAL
    header.gravity = Gravity.CENTER_VERTICAL
    header.addView(title, LinearLayout.LayoutParams(0, WC, 1f))
    header.addView(stop, LinearLayout.LayoutParams(WC, WC))
    header.addView(gear, LinearLayout.LayoutParams(dp(44), dp(44)))

    // Chat area with a friendly empty screen
    scroll.setBackgroundColor(Color.TRANSPARENT)
    chatView.textSize = 16f
    chatView.setLineSpacing(0f, 1.2f)
    val frame = FrameLayout(act)
    frame.addView(scroll, FrameLayout.LayoutParams(MP, MP))

    val empty = LinearLayout(act)
    empty.orientation = LinearLayout.VERTICAL
    empty.gravity = Gravity.CENTER_HORIZONTAL
    val logoId = act.resources.getIdentifier("ic_jarvis_foreground", "drawable", act.packageName)
    if (logoId != 0) {
        val logo = ImageView(act)
        logo.setImageResource(logoId)
        empty.addView(logo, LinearLayout.LayoutParams(dp(120), dp(120)))
    }
    val hello = TextView(act)
    hello.text = "आज मी कशी मदत करू?"
    hello.textSize = 18f
    hello.setTextColor(soft)
    hello.gravity = Gravity.CENTER
    hello.setPadding(0, dp(4), 0, dp(12))
    empty.addView(hello, LinearLayout.LayoutParams(MP, WC))

    fun chip(label: String, cmd: String): Button {
        val b = Button(act)
        b.text = label
        styleChip(b, d)
        b.textSize = 13f
        b.setOnClickListener {
            input.setText(cmd)
            send.performClick()
        }
        return b
    }
    fun chipRow(a: Button, b: Button): LinearLayout {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER
        val lp1 = LinearLayout.LayoutParams(0, WC, 1f)
        lp1.setMargins(dp(4), dp(4), dp(4), dp(4))
        val lp2 = LinearLayout.LayoutParams(0, WC, 1f)
        lp2.setMargins(dp(4), dp(4), dp(4), dp(4))
        r.addView(a, lp1)
        r.addView(b, lp2)
        return r
    }
    empty.addView(
        chipRow(chip("📰 आजच्या बातम्या", "आजच्या बातम्या सांग"), chip("📚 आजचा study plan", "आजचा study plan दे")),
        LinearLayout.LayoutParams(MP, WC)
    )
    empty.addView(
        chipRow(chip("🕒 किती वाजले", "आता किती वाजले"), chip("🔦 Torch चालू", "torch चालू कर")),
        LinearLayout.LayoutParams(MP, WC)
    )
    frame.addView(empty, FrameLayout.LayoutParams(MP, WC, Gravity.CENTER))

    empty.visibility = if (chatView.text.isNullOrEmpty()) View.VISIBLE else View.GONE
    chatView.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            empty.visibility = if (s == null || s.isEmpty()) View.VISIBLE else View.GONE
        }
    })

    // Status line
    status.textSize = 12f
    status.setTextColor(Color.parseColor("#E6B450"))
    status.setPadding(dp(4), dp(4), dp(4), dp(6))

    // Bottom bar
    styleIcon(cam, "📷", 18f)
    styleIcon(gal, "🖼", 18f)
    input.background = null
    input.textSize = 16f
    input.maxLines = 4
    input.setPadding(dp(6), dp(10), dp(6), dp(10))
    val pill = LinearLayout(act)
    pill.orientation = LinearLayout.HORIZONTAL
    pill.gravity = Gravity.CENTER_VERTICAL
    pill.background = shape(card, 28 * d, line, (1 * d).toInt())
    pill.setPadding(dp(6), dp(2), dp(10), dp(2))
    pill.addView(cam, LinearLayout.LayoutParams(dp(40), dp(40)))
    pill.addView(gal, LinearLayout.LayoutParams(dp(40), dp(40)))
    pill.addView(input, LinearLayout.LayoutParams(0, WC, 1f))

    styleRound(mic, "🎤", cyan, Color.BLACK)
    styleRound(send, "➤", Color.parseColor("#1B2A3A"), cyan)
    val bar = LinearLayout(act)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.addView(pill, LinearLayout.LayoutParams(0, WC, 1f))
    val micLp = LinearLayout.LayoutParams(dp(46), dp(46))
    micLp.setMargins(dp(8), 0, 0, 0)
    bar.addView(mic, micLp)
    val sendLp = LinearLayout.LayoutParams(dp(46), dp(46))
    sendLp.setMargins(dp(6), 0, 0, 0)
    bar.addView(send, sendLp)

    // Put everything on the screen
    layout.addView(header, LinearLayout.LayoutParams(MP, WC))
    val panelLp = LinearLayout.LayoutParams(MP, WC)
    panelLp.setMargins(0, dp(8), 0, 0)
    layout.addView(panel, panelLp)
    layout.addView(frame, LinearLayout.LayoutParams(MP, 0, 1f))
    layout.addView(status, LinearLayout.LayoutParams(MP, WC))
    layout.addView(bar, LinearLayout.LayoutParams(MP, WC))
}
