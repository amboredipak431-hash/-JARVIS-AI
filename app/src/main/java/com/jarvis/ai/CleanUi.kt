package com.jarvis.ai

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.TransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

// Shows "MODE: NORMAL" as "Mode  ·  Normal" (only on screen, the real text is not changed)
private class PrettyText : TransformationMethod {
    private fun nice(x: String): String {
        val t = x.trim().lowercase()
        return if (t.isEmpty()) t else t.substring(0, 1).uppercase() + t.substring(1)
    }

    override fun getTransformation(source: CharSequence?, view: View?): CharSequence {
        val s = source?.toString() ?: ""
        val i = s.indexOf(": ")
        return if (i < 0) nice(s) else nice(s.substring(0, i)) + "  ·  " + nice(s.substring(i + 2))
    }

    override fun onFocusChanged(
        view: View?,
        sourceText: CharSequence?,
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
    }
}

private fun styleRow(b: Button, d: Float) {
    flatten(b)
    b.transformationMethod = PrettyText()
    b.textSize = 15f
    b.setTextColor(Color.parseColor("#C9D6E4"))
    b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    val h = (16 * d).toInt()
    val v = (14 * d).toInt()
    b.setPadding(h, v, h, v)
    val g = GradientDrawable()
    g.setColor(Color.TRANSPARENT)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22FFFFFF")), g, null)
}

// ---------- Clean line icons ----------

private class IconDrawable(private val type: Int, private val color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        canvas.scale(s, s)
        p.color = color
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 1.8f
        path.reset()
        when (type) {
            0 -> {
                p.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(3f, 7f, 21f, 20f), 3f, 3f, p)
                path.moveTo(8f, 7f)
                path.lineTo(9.5f, 4.5f)
                path.lineTo(14.5f, 4.5f)
                path.lineTo(16f, 7f)
                canvas.drawPath(path, p)
                canvas.drawCircle(12f, 13.5f, 3.6f, p)
            }
            1 -> {
                p.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(3f, 4f, 21f, 20f), 3f, 3f, p)
                path.moveTo(4f, 17.5f)
                path.lineTo(9.5f, 12f)
                path.lineTo(13.5f, 16f)
                path.lineTo(16.5f, 13f)
                path.lineTo(20f, 16.5f)
                canvas.drawPath(path, p)
                p.style = Paint.Style.FILL
                canvas.drawCircle(8.5f, 8.5f, 1.8f, p)
            }
            2 -> {
                p.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, p)
                canvas.drawArc(RectF(6f, 7f, 18f, 18f), 0f, 180f, false, p)
                canvas.drawLine(12f, 18f, 12f, 21f, p)
                canvas.drawLine(9f, 21f, 15f, 21f, p)
            }
            else -> {
                p.style = Paint.Style.FILL
                path.moveTo(4f, 4f)
                path.lineTo(21f, 12f)
                path.lineTo(4f, 20f)
                path.lineTo(7f, 12f)
                path.close()
                canvas.drawPath(path, p)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        p.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        p.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun styleIconBtn(b: Button, type: Int, sizeDp: Int, d: Float, fill: Int, iconColor: Int) {
    flatten(b)
    b.text = ""
    b.setPadding(0, 0, 0, 0)
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(fill)
    val layers = LayerDrawable(arrayOf<Drawable>(g, IconDrawable(type, iconColor)))
    val inset = (sizeDp * 0.26f * d).toInt()
    layers.setLayerInset(1, inset, inset, inset, inset)
    val mask = GradientDrawable()
    mask.shape = GradientDrawable.OVAL
    mask.setColor(Color.WHITE)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), layers, mask)
}

// ---------- The moving JARVIS rings ----------

private class HudView(context: Context) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val cyan = Color.parseColor("#00E5FF")
    private val blue = Color.parseColor("#1E78FF")
    private val light = Color.parseColor("#7DF9FF")
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashes = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val speeds = floatArrayOf(14f, -22f, -32f, 40f, 24f, -60f, 50f)
    private val angles = FloatArray(7)
    private val outerSpans = floatArrayOf(8f, 60f, 128f, 36f, 212f, 74f)
    private val midSpans = floatArrayOf(30f, 82f, 190f, 56f)
    private val innerSpans = floatArrayOf(15f, 72f, 135f, 72f, 255f, 72f)
    private var mult = 1f
    private var targetMult = 1f
    private var pulse = 0f
    private var targetPulse = 0f
    private var clock = 0f
    private var last = 0L
    private var running = false
    private var since = 0f
    private var frame = 0
    var stateProvider: (() -> Int)? = null

    init {
        thin.style = Paint.Style.STROKE
        thin.strokeWidth = 1.2f * d
        thin.color = blue
        thick.style = Paint.Style.STROKE
        thick.strokeWidth = 5f * d
        thick.color = cyan
        glow.style = Paint.Style.STROKE
        glow.strokeWidth = 11f * d
        glow.color = cyan
        glow.alpha = 55
        dots.style = Paint.Style.STROKE
        dots.strokeWidth = 2.2f * d
        dots.color = cyan
        dots.pathEffect = DashPathEffect(floatArrayOf(2f * d, 7f * d), 0f)
        dashes.style = Paint.Style.STROKE
        dashes.strokeWidth = 1.4f * d
        dashes.color = light
        dashes.pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
        fill.style = Paint.Style.FILL
        label.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        label.textAlign = Paint.Align.CENTER
        label.color = light
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (visibility != View.VISIBLE) {
                last = 0L
                this@HudView.postDelayed(this, 300)
                return
            }
            val now = System.nanoTime()
            val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
            last = now
            step(dt)
            frame++
            if (targetMult > 1f || frame % 2 == 0) {
                invalidate()
            }
            this@HudView.postOnAnimation(this)
        }
    }

    private fun step(dt: Float) {
        clock += dt
        mult += (targetMult - mult) * min(1f, dt * 4f)
        pulse += (targetPulse - pulse) * min(1f, dt * 5f)
        for (i in angles.indices) {
            angles[i] = (angles[i] + speeds[i] * mult * dt) % 360f
        }
        since += dt
        if (since >= 0.25f) {
            since = 0f
            val s = try {
                stateProvider?.invoke() ?: 0
            } catch (e: Exception) {
                0
            }
            targetMult = if (s == 2) 3.2f else if (s == 1) 2.2f else 1f
            targetPulse = if (s == 2) 1f else if (s == 1) 0.4f else 0f
        }
    }

    private fun start() {
        if (!running) {
            running = true
            last = 0L
            postOnAnimation(tick)
        }
    }

    private fun stop() {
        running = false
        removeCallbacks(tick)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) start() else stop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val rad = min(width, height) / 2f - 6f * d
        if (rad < 24f * d) return
        val k = min(1f, rad / (120f * d))
        thick.strokeWidth = 5f * d * k + 1f * d
        glow.strokeWidth = thick.strokeWidth * 2.2f
        thin.strokeWidth = max(0.8f * d, 1.2f * d * k)
        dots.strokeWidth = max(1.4f * d, 2.2f * d * k)
        val breathe = 1f + pulse * 0.02f * sin(clock * 9f)

        fun circle(f: Float, p: Paint) {
            canvas.drawCircle(cx, cy, rad * f * breathe, p)
        }

        fun arcs(f: Float, start: Float, spans: FloatArray) {
            val r = rad * f * breathe
            box.set(cx - r, cy - r, cx + r, cy + r)
            var i = 0
            while (i + 1 < spans.size) {
                canvas.drawArc(box, start + spans[i], spans[i + 1], false, glow)
                canvas.drawArc(box, start + spans[i], spans[i + 1], false, thick)
                i += 2
            }
        }

        circle(1.0f, thin)
        arcs(0.94f, angles[0], outerSpans)
        circle(0.86f, thin)

        canvas.save()
        canvas.rotate(angles[1], cx, cy)
        circle(0.79f, dots)
        canvas.restore()

        arcs(0.70f, angles[2], midSpans)
        circle(0.62f, thin)

        canvas.save()
        canvas.rotate(angles[3], cx, cy)
        circle(0.55f, dashes)
        canvas.restore()

        val sq = max(2f * d, 3.4f * d * k)
        val dr = rad * 0.47f * breathe
        canvas.save()
        canvas.rotate(angles[4], cx, cy)
        for (i in 0 until 7) {
            canvas.save()
            canvas.rotate(i * (360f / 7f), cx, cy)
            canvas.drawRect(cx - sq, cy - dr - sq, cx + sq, cy - dr + sq, thin)
            canvas.restore()
        }
        canvas.restore()

        arcs(0.40f, angles[5], innerSpans)

        canvas.save()
        canvas.rotate(angles[6], cx, cy)
        circle(0.33f, dots)
        canvas.restore()
        circle(0.28f, thin)

        val coreR = rad * 0.26f * (1f + pulse * 0.1f * sin(clock * 10f))
        fill.color = cyan
        fill.alpha = (28f + 40f * pulse).toInt()
        canvas.drawCircle(cx, cy, coreR, fill)

        if (rad > 70f * d) {
            label.textSize = rad * 0.15f
            canvas.drawText("JARVIS", cx, cy + label.textSize * 0.35f, label)
        } else {
            fill.color = light
            fill.alpha = 255
            canvas.drawCircle(cx, cy, rad * 0.08f, fill)
        }
    }
}

// ---------- The clean screen ----------

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
    if (rowA.childCount != 2 || rowB.childCount != 2 || rowC.childCount != 3) return
    if (keyRow.childCount != 2 || inputRow.childCount != 3 || actionRow.childCount != 3) return
    val master = rowA.getChildAt(0) as? Button ?: return
    val mode = rowA.getChildAt(1) as? Button ?: return
    val lang = rowB.getChildAt(0) as? Button ?: return
    val length = rowB.getChildAt(1) as? Button ?: return
    val newChat = rowC.getChildAt(0) as? Button ?: return
    val history = rowC.getChildAt(1) as? Button ?: return
    val copy = rowC.getChildAt(2) as? Button ?: return
    val keyInput = keyRow.getChildAt(0) as? EditText ?: return
    val saveKey = keyRow.getChildAt(1) as? Button ?: return
    val input = inputRow.getChildAt(0) as? EditText ?: return
    val cam = inputRow.getChildAt(1) as? Button ?: return
    val gal = inputRow.getChildAt(2) as? Button ?: return
    val send = actionRow.getChildAt(0) as? Button ?: return
    val mic = actionRow.getChildAt(1) as? Button ?: return
    val stop = actionRow.getChildAt(2) as? Button ?: return
    val chatView = scroll.getChildAt(0) as? TextView ?: return

    val prefs = act.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    val d = act.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * d + 0.5f).toInt()

    val cyan = Color.parseColor("#00E5FF")
    val soft = Color.parseColor("#C9D6E4")
    val card = Color.parseColor("#121A24")
    val line = Color.parseColor("#223042")
    val hasKey = (prefs.getString("key", "") ?: "").isNotEmpty()

    layout.removeAllViews()
    inputRow.removeAllViews()
    actionRow.removeAllViews()
    rowA.removeAllViews()
    rowB.removeAllViews()
    rowC.removeAllViews()
    keyRow.removeAllViews()
    layout.setBackgroundColor(Color.parseColor("#0B0F14"))
    layout.setPadding(dp(16), dp(24), dp(16), dp(12))

    // ----- the moving rings -----
    val hud = HudView(act)
    val smallH = dp(110)

    val ttsField = try {
        val f = act.javaClass.getDeclaredField("tts")
        f.isAccessible = true
        f
    } catch (e: Exception) {
        null
    }

    // ----- header -----
    title.textSize = 20f
    title.letterSpacing = 0.12f
    title.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    styleChip(stop, d)
    stop.text = "■ Stop"
    if (ttsField != null) {
        stop.visibility = View.GONE
    }
    val gear = Button(act)
    styleIcon(gear, "⚙", 20f)
    val header = LinearLayout(act)
    header.orientation = LinearLayout.HORIZONTAL
    header.gravity = Gravity.CENTER_VERTICAL
    header.addView(title, LinearLayout.LayoutParams(0, WC, 1f))
    header.addView(stop, LinearLayout.LayoutParams(WC, WC))
    header.addView(gear, LinearLayout.LayoutParams(dp(44), dp(44)))

    hud.stateProvider = {
        var st = 0
        if (status.text.toString().startsWith("JARVIS is thinking")) {
            st = 1
        }
        try {
            val t = ttsField?.get(act) as? TextToSpeech
            if (t != null && t.isSpeaking) {
                st = 2
            }
        } catch (e: Exception) {
        }
        if (ttsField != null) {
            stop.visibility = if (st == 2) View.VISIBLE else View.GONE
        }
        st
    }

    // ----- settings page (opens only when the gear is tapped) -----
    fun lp(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(MP, WC)
        p.setMargins(dp(3), dp(top), dp(3), 0)
        return p
    }

    fun field(hint: String, saved: String, lines: Int): EditText {
        val e = EditText(act)
        e.hint = hint
        e.setText(saved)
        e.textSize = 14f
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(Color.parseColor("#6B7C8F"))
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        e.background = shape(Color.parseColor("#141C26"), 12 * d, line, (1 * d).toInt())
        if (lines > 1) {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            e.minLines = lines
            e.gravity = Gravity.TOP
        } else {
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.setSingleLine(true)
        }
        return e
    }

    fun newCard(): LinearLayout {
        val c = LinearLayout(act)
        c.orientation = LinearLayout.VERTICAL
        c.background = shape(Color.parseColor("#10161F"), 16 * d, Color.parseColor("#1E2A38"), (1 * d).toInt())
        return c
    }

    fun section(text: String): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Color.parseColor("#8FA3B8"))
        t.letterSpacing = 0.08f
        t.setPadding(dp(6), dp(18), 0, dp(6))
        return t
    }

    fun addRows(c: LinearLayout, rows: List<View>) {
        for ((i, r) in rows.withIndex()) {
            c.addView(r, LinearLayout.LayoutParams(MP, WC))
            if (i < rows.size - 1) {
                val dv = View(act)
                dv.setBackgroundColor(line)
                val p = LinearLayout.LayoutParams(MP, dp(1))
                p.setMargins(dp(16), 0, 0, 0)
                c.addView(dv, p)
            }
        }
    }

    for (b in listOf(master, mode, lang, length, newChat, history, copy)) {
        styleRow(b, d)
    }

    val settingsTitle = TextView(act)
    settingsTitle.text = "सेटिंग्स"
    settingsTitle.textSize = 20f
    settingsTitle.setTextColor(Color.WHITE)
    settingsTitle.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    settingsTitle.setPadding(dp(6), dp(4), 0, dp(2))

    val assistantCard = newCard()
    addRows(assistantCard, listOf<View>(master, mode, lang, length))
    val chatsCard = newCard()
    addRows(chatsCard, listOf<View>(newChat, history, copy))

    keyInput.textSize = 14f
    keyInput.hint = "Gemini API key येथे paste करा"
    keyInput.setPadding(dp(12), dp(10), dp(12), dp(10))
    keyInput.background = shape(Color.parseColor("#141C26"), 12 * d, line, (1 * d).toInt())
    styleChip(saveKey, d)
    saveKey.text = "Key सेव करा"
    val keyCaption = TextView(act)
    keyCaption.textSize = 12f
    keyCaption.setTextColor(Color.parseColor("#8FA3B8"))
    fun refreshKeyCaption() {
        val ok = (prefs.getString("key", "") ?: "").isNotEmpty()
        keyCaption.text = if (ok) {
            "✓ Key सेव आहे. नवीन टाकली तर जुनी बदलेल. ती फक्त तुमच्या फोनमध्ये राहते."
        } else {
            "Key अजून टाकलेली नाही. ती फक्त तुमच्या फोनमध्ये राहते."
        }
    }
    refreshKeyCaption()
    val keyCard = newCard()
    keyCard.setPadding(dp(12), dp(12), dp(12), dp(12))
    keyCard.addView(keyInput, LinearLayout.LayoutParams(MP, WC))
    keyCard.addView(saveKey, lp(8))
    keyCard.addView(keyCaption, lp(8))

    val infoNote = TextView(act)
    infoNote.text = "JARVIS तुम्हाला नावाने बोलवेल आणि तुमच्या कोर्सप्रमाणे समजावेल. " +
        "ही माहिती प्रश्न विचारताना Google च्या Gemini कडे जाते. " +
        "Password, OTP आणि बँकेची माहिती टाकू नका."
    infoNote.textSize = 12f
    infoNote.setTextColor(Color.parseColor("#8FA3B8"))
    val nameEt = field("तुमचं नाव", prefs.getString("pf_name", "") ?: "", 1)
    val courseEt = field("कोर्स आणि वर्ष (उदा. GNM, पहिलं वर्ष)", prefs.getString("pf_course", "") ?: "", 1)
    val cityEt = field("शहर किंवा गाव", prefs.getString("pf_city", "") ?: "", 1)
    val aboutEt = field("तुमच्याबद्दल आणखी काही (उदा. JARVIS ने कसं बोलावं)", prefs.getString("pf_about", "") ?: "", 3)
    val saveInfo = Button(act)
    styleChip(saveInfo, d)
    saveInfo.text = "माहिती सेव करा"
    saveInfo.setOnClickListener {
        val nm = nameEt.text.toString().trim()
        val co = courseEt.text.toString().trim()
        val ci = cityEt.text.toString().trim()
        val ab = aboutEt.text.toString().trim().take(300)
        val parts = ArrayList<String>()
        if (nm.isNotEmpty()) parts.add("the user's name is " + nm + " (call the user by this name)")
        if (co.isNotEmpty()) parts.add("course and year: " + co)
        if (ci.isNotEmpty()) parts.add("lives in " + ci)
        if (ab.isNotEmpty()) parts.add("about the user: " + ab.replace("\n", " "))
        val sb = StringBuilder()
        if (parts.isNotEmpty()) {
            sb.append("PROFILE: ").append(parts.joinToString("; ")).append("\n")
        }
        for (ln in (prefs.getString("notes", "") ?: "").split("\n")) {
            if (ln.isNotBlank() && !ln.startsWith("PROFILE:")) {
                sb.append(ln).append("\n")
            }
        }
        prefs.edit()
            .putString("pf_name", nm)
            .putString("pf_course", co)
            .putString("pf_city", ci)
            .putString("pf_about", ab)
            .putString("notes", sb.toString())
            .apply()
        Toast.makeText(act, "माहिती सेव केली", Toast.LENGTH_SHORT).show()
    }
    val infoCard = newCard()
    infoCard.setPadding(dp(12), dp(12), dp(12), dp(12))
    infoCard.addView(infoNote, LinearLayout.LayoutParams(MP, WC))
    infoCard.addView(nameEt, lp(10))
    infoCard.addView(courseEt, lp(8))
    infoCard.addView(cityEt, lp(8))
    infoCard.addView(aboutEt, lp(8))
    infoCard.addView(saveInfo, lp(10))

    val doneBtn = Button(act)
    styleChip(doneBtn, d)
    doneBtn.text = "बंद करा"

    val panel = LinearLayout(act)
    panel.orientation = LinearLayout.VERTICAL
    panel.setPadding(dp(4), dp(8), dp(4), dp(28))
    panel.addView(settingsTitle, LinearLayout.LayoutParams(MP, WC))
    panel.addView(section("असिस्टंट"), LinearLayout.LayoutParams(MP, WC))
    panel.addView(assistantCard, LinearLayout.LayoutParams(MP, WC))
    panel.addView(section("गप्पा"), LinearLayout.LayoutParams(MP, WC))
    panel.addView(chatsCard, LinearLayout.LayoutParams(MP, WC))
    panel.addView(section("API KEY"), LinearLayout.LayoutParams(MP, WC))
    panel.addView(keyCard, LinearLayout.LayoutParams(MP, WC))
    panel.addView(section("तुमची माहिती"), LinearLayout.LayoutParams(MP, WC))
    panel.addView(infoCard, LinearLayout.LayoutParams(MP, WC))
    panel.addView(doneBtn, lp(20))

    val settingsScroll = ScrollView(act)
    settingsScroll.setBackgroundColor(Color.parseColor("#0B0F14"))
    settingsScroll.addView(panel, ViewGroup.LayoutParams(MP, WC))
    settingsScroll.visibility = View.GONE

    fun setSettings(show: Boolean) {
        settingsScroll.visibility = if (show) View.VISIBLE else View.GONE
        hud.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            refreshKeyCaption()
        }
    }

    gear.setOnClickListener { setSettings(settingsScroll.visibility != View.VISIBLE) }
    doneBtn.setOnClickListener { setSettings(false) }

    // New chat, History and Copy close the settings after a tap
    for (b in listOf(newChat, history, copy)) {
        b.setOnTouchListener { view, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                view.post { setSettings(false) }
            }
            false
        }
    }

    // ----- chat area: rings in the middle, suggestions at the bottom -----
    scroll.setBackgroundColor(Color.TRANSPARENT)
    chatView.textSize = 16f
    chatView.setLineSpacing(0f, 1.25f)
    chatView.setPadding(dp(4), dp(12), dp(4), dp(12))

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

    val chipBox = LinearLayout(act)
    chipBox.orientation = LinearLayout.HORIZONTAL
    chipBox.setPadding(0, dp(4), 0, dp(8))
    val chipTexts = listOf(
        Pair("आजच्या बातम्या", "आजच्या बातम्या सांग"),
        Pair("आजचा study plan", "आजचा study plan दे"),
        Pair("किती वाजले", "आता किती वाजले"),
        Pair("Torch चालू", "torch चालू कर")
    )
    for (c in chipTexts) {
        val cp = LinearLayout.LayoutParams(WC, WC)
        cp.setMargins(dp(4), 0, dp(4), 0)
        chipBox.addView(chip(c.first, c.second), cp)
    }
    val chips = HorizontalScrollView(act)
    chips.isHorizontalScrollBarEnabled = false
    chips.addView(chipBox, FrameLayout.LayoutParams(WC, WC))

    val frame = FrameLayout(act)
    frame.addView(hud, FrameLayout.LayoutParams(MP, MP, Gravity.CENTER))
    frame.addView(scroll, FrameLayout.LayoutParams(MP, MP))
    frame.addView(chips, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))
    frame.addView(settingsScroll, FrameLayout.LayoutParams(MP, MP))

    // big centred rings on the empty screen, small rings on top once the chat starts
    var hudMode = -1
    fun chatState(has: Boolean) {
        val m = if (has) 1 else 0
        if (m == hudMode) return
        hudMode = m
        chips.visibility = if (has) View.GONE else View.VISIBLE
        hud.layoutParams = if (has) {
            FrameLayout.LayoutParams(MP, smallH, Gravity.TOP)
        } else {
            FrameLayout.LayoutParams(MP, MP, Gravity.CENTER)
        }
        val sp = scroll.layoutParams as? FrameLayout.LayoutParams
        if (sp != null) {
            sp.topMargin = if (has) smallH + dp(4) else 0
            scroll.layoutParams = sp
        }
    }
    chatState(chatView.text.isNotEmpty())
    chatView.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            chatState(s != null && s.isNotEmpty())
        }
    })

    // ----- small status line: hidden when there is nothing to say -----
    status.textSize = 12f
    status.setTextColor(Color.parseColor("#E6B450"))
    status.setPadding(dp(4), dp(4), dp(4), dp(6))
    fun syncStatus(t: CharSequence?) {
        val s = t?.toString() ?: ""
        if (s.isEmpty() || s == "JARVIS is ready") {
            status.visibility = View.GONE
            return
        }
        status.visibility = View.VISIBLE
        if (!s.startsWith("JARVIS is thinking") && !s.startsWith("Error") && !s.startsWith("⚙")) {
            status.postDelayed({
                if (status.text.toString() == s) {
                    status.visibility = View.GONE
                }
            }, 4000)
        }
    }
    if (!hasKey) {
        status.text = "⚙ दाबून Gemini API key टाका"
    }
    syncStatus(status.text)
    status.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            syncStatus(s)
        }
    })

    // ----- bottom bar -----
    styleIconBtn(cam, 0, 40, d, Color.TRANSPARENT, soft)
    styleIconBtn(gal, 1, 40, d, Color.TRANSPARENT, soft)
    cam.contentDescription = "Camera"
    gal.contentDescription = "Gallery"
    input.hint = "JARVIS ला काहीही विचारा..."
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

    styleIconBtn(mic, 2, 46, d, cyan, Color.BLACK)
    styleIconBtn(send, 3, 46, d, Color.parseColor("#1B2A3A"), cyan)
    mic.contentDescription = "Speak"
    send.contentDescription = "Send"
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

    // ----- put everything on the screen -----
    layout.addView(header, LinearLayout.LayoutParams(MP, WC))
    layout.addView(frame, LinearLayout.LayoutParams(MP, 0, 1f))
    layout.addView(status, LinearLayout.LayoutParams(MP, WC))
    layout.addView(bar, LinearLayout.LayoutParams(MP, WC))
}
