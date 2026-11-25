package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val VM_MP = ViewGroup.LayoutParams.MATCH_PARENT
private val VM_WC = ViewGroup.LayoutParams.WRAP_CONTENT
private const val REQ_AUDIO = 4321

private fun vmShape(fill: Int, radius: Float): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = radius
    g.setColor(fill)
    return g
}

// ---------- आयकॉन (माईक, म्यूट माईक, X) ----------

private class VmIcon(private val type: Int, private val color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        canvas.scale(s, s)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 1.9f
        when (type) {
            0, 1 -> {
                canvas.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, p)
                canvas.drawArc(RectF(6f, 7f, 18f, 18f), 0f, 180f, false, p)
                canvas.drawLine(12f, 18f, 12f, 21f, p)
                canvas.drawLine(9f, 21f, 15f, 21f, p)
                if (type == 1) {
                    p.strokeWidth = 2.4f
                    canvas.drawLine(4f, 4f, 20f, 20f, p)
                }
            }
            else -> {
                canvas.drawLine(6f, 6f, 18f, 18f, p)
                canvas.drawLine(18f, 6f, 6f, 18f, p)
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

private fun vmIconButton(b: Button, type: Int, sizeDp: Int, d: Float, fill: Int, iconColor: Int) {
    b.setAllCaps(false)
    b.minWidth = 0
    b.minHeight = 0
    b.minimumWidth = 0
    b.minimumHeight = 0
    b.stateListAnimator = null
    b.text = ""
    b.setPadding(0, 0, 0, 0)
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(fill)
    val layers = LayerDrawable(arrayOf<Drawable>(g, VmIcon(type, iconColor)))
    val inset = (sizeDp * 0.27f * d).toInt()
    layers.setLayerInset(1, inset, inset, inset, inset)
    val mask = GradientDrawable()
    mask.shape = GradientDrawable.OVAL
    mask.setColor(Color.WHITE)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), layers, mask)
}

// ---------- निळ्या ठिपक्यांचा गोल ----------
// mode: 0 शांत, 1 ऐकतोय, 2 विचार करतोय, 3 बोलतोय, 4 म्यूट

private class OrbView(context: Context) : View(context) {
    var mode = 0
    var level = 0f
    private val d = context.resources.displayMetrics.density
    private val n = 520
    private val bx = FloatArray(n)
    private val by = FloatArray(n)
    private val bz = FloatArray(n)
    private val pts = Array(3) { FloatArray(n * 2) }
    private val cnt = IntArray(3)
    private val dotPaint = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sizes = floatArrayOf(2.2f, 3.0f, 4.0f)
    private var clock = 0f
    private var ay = 0f
    private var smooth = 0f
    private var last = 0L
    private var running = false

    init {
        val golden = 2.3999631f
        for (i in 0 until n) {
            val y = 1f - 2f * (i + 0.5f) / n
            val r = sqrt(1f - y * y)
            val th = golden * i
            bx[i] = cos(th) * r
            by[i] = y
            bz[i] = sin(th) * r
        }
        for (p in dotPaint) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
        }
        glow.style = Paint.Style.FILL
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 1f * d
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
            last = now
            clock += dt
            val speed = 0.30f + smooth * 1.8f + (if (mode == 2) 1.3f else 0f)
            ay = (ay + dt * speed) % 6.2831855f
            invalidate()
            this@OrbView.postOnAnimation(this)
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
        val base = min(width, height) * 0.28f
        if (base < 24f * d) return
        val t = clock
        val target = when (mode) {
            1 -> level
            3 -> 0.30f + 0.40f * (0.5f + 0.5f * sin(t * 6.3f) * sin(t * 2.1f + 1f))
            2 -> 0.18f
            else -> 0.04f
        }
        smooth += (target - smooth) * 0.2f
        val amp = smooth
        val scale = 1f + amp * 0.15f + (if (mode == 2) -0.05f else 0f) + 0.015f * sin(t * 2f)
        val cosY = cos(ay)
        val sinY = sin(ay)
        val ax = 0.35f + 0.25f * sin(t * 0.4f)
        val cosX = cos(ax)
        val sinX = sin(ax)
        cnt[0] = 0
        cnt[1] = 0
        cnt[2] = 0
        val wobSpeed = 5f + 6f * amp
        for (i in 0 until n) {
            val wob = 1f + amp * 0.22f * sin(i * 0.61f + t * wobSpeed) + 0.02f * sin(i * 0.17f + t * 1.3f)
            val x0 = bx[i] * wob
            val y0 = by[i] * wob
            val z0 = bz[i] * wob
            val x1 = x0 * cosY + z0 * sinY
            val z1 = -x0 * sinY + z0 * cosY
            val y2 = y0 * cosX - z1 * sinX
            val z2 = y0 * sinX + z1 * cosX
            val f = 1f / (1f - z2 * 0.18f)
            val px = cx + x1 * base * scale * f
            val py = cy + y2 * base * scale * f
            val b = if (z2 < -0.35f) 0 else if (z2 < 0.35f) 1 else 2
            val k = cnt[b]
            val arr = pts[b]
            arr[k * 2] = px
            arr[k * 2 + 1] = py
            cnt[b] = k + 1
        }

        val a = if (mode == 4) 0.35f else 1f
        val bright = mode == 1 || mode == 3
        glow.color = Color.argb(((22f + 70f * amp) * a).toInt(), 30, 120, 255)
        canvas.drawCircle(cx, cy, base * scale * 1.05f, glow)
        ring.color = Color.argb(((60f + 100f * amp) * a).toInt(), 30, 120, 255)
        canvas.drawCircle(cx, cy, base * scale * 1.34f, ring)
        dotPaint[0].color = Color.argb((110f * a).toInt(), 30, 100, 255)
        dotPaint[1].color = Color.argb((190f * a).toInt(), 46, 139, 255)
        dotPaint[2].color = if (bright) {
            Color.argb((255f * a).toInt(), 200, 245, 255)
        } else {
            Color.argb((255f * a).toInt(), 143, 227, 255)
        }
        for (j in 0 until 3) {
            dotPaint[j].strokeWidth = sizes[j] * d * (0.9f + amp * 0.6f)
            canvas.drawPoints(pts[j], 0, cnt[j] * 2, dotPaint[j])
        }
    }
}

// ---------- व्हॉइस मोड ----------

private class VoiceModeController(private val act: Activity) {

    private val ui = Handler(Looper.getMainLooper())
    private val d = act.resources.displayMetrics.density
    private lateinit var root: FrameLayout
    private lateinit var orb: OrbView
    private lateinit var stateLabel: TextView
    private lateinit var caption: TextView
    private lateinit var langPill: TextView
    private lateinit var typeBox: EditText
    private lateinit var muteBtn: Button
    private var recognizer: SpeechRecognizer? = null
    private var isOpen = false
    private var muted = false
    private var listening = false
    private var wasBusy = false
    private var permAsked = false
    private var errors = 0
    private var resumeAt = 0L
    private var heard = ""
    private var shownAnswer = ""
    private var shownStatus = ""

    private fun dp(v: Int): Int = (v * d + 0.5f).toInt()

    private fun fld(name: String): java.lang.reflect.Field? {
        return try {
            val f = act.javaClass.getDeclaredField(name)
            f.isAccessible = true
            f
        } catch (e: Exception) {
            null
        }
    }

    private val fStatus = fld("status")
    private val fTts = fld("tts")
    private val fLast = fld("lastAnswer")
    private val fLang = fld("marathi")

    private fun statusText(): String {
        return try {
            (fStatus?.get(act) as? TextView)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun lastAnswerText(): String {
        return try {
            (fLast?.get(act) as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun readMarathi(): Boolean {
        return try {
            fLang?.getBoolean(act) ?: true
        } catch (e: Exception) {
            true
        }
    }

    private fun isSpeaking(): Boolean {
        return try {
            (fTts?.get(act) as? TextToSpeech)?.isSpeaking == true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopSpeaking() {
        try {
            (fTts?.get(act) as? TextToSpeech)?.stop()
        } catch (e: Exception) {
        }
    }

    // ----- मुख्य स्क्रीनवरचे view शोधणं -----

    private fun collectSkip(v: View, skip: View?, out: MutableList<View>) {
        if (v === skip) return
        out.add(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                collectSkip(v.getChildAt(i), skip, out)
            }
        }
    }

    private fun mainViews(): List<View> {
        val out = ArrayList<View>()
        val content = act.findViewById<ViewGroup>(android.R.id.content)
        if (content != null) {
            collectSkip(content, if (isOpen) root else null, out)
        }
        return out
    }

    private fun findInput(): EditText? {
        for (v in mainViews()) {
            if (v is EditText) {
                val h = v.hint?.toString() ?: ""
                if (h.contains("विचारा") || h.contains("Ask JARVIS")) return v
            }
        }
        return null
    }

    private fun findSend(): Button? {
        for (v in mainViews()) {
            if (v is Button) {
                val desc = v.contentDescription?.toString() ?: ""
                val t = v.text?.toString() ?: ""
                if (desc == "Send" || t == "SEND") return v
            }
        }
        return null
    }

    // ----- स्क्रीन बनवणं -----

    private fun build(): FrameLayout {
        val frame = FrameLayout(act)
        frame.setBackgroundColor(Color.parseColor("#05080D"))
        frame.isClickable = true
        frame.keepScreenOn = true
        frame.isFocusableInTouchMode = true
        frame.setOnKeyListener { _, keyCode, ev ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (ev.action == KeyEvent.ACTION_UP) {
                    close()
                }
                true
            } else {
                false
            }
        }

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(28), dp(16), dp(20))

        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        langPill = TextView(act)
        langPill.textSize = 13f
        langPill.setTextColor(Color.parseColor("#C9D6E4"))
        langPill.gravity = Gravity.CENTER
        langPill.setPadding(dp(12), dp(8), dp(12), dp(8))
        langPill.background = vmShape(Color.parseColor("#1A222C"), 100 * d)
        langPill.setOnClickListener { toggleLanguage() }
        val title = TextView(act)
        title.text = "J.A.R.V.I.S"
        title.textSize = 16f
        title.letterSpacing = 0.14f
        title.setTextColor(Color.CYAN)
        title.gravity = Gravity.CENTER
        val spacer = View(act)
        top.addView(langPill, LinearLayout.LayoutParams(dp(88), VM_WC))
        top.addView(title, LinearLayout.LayoutParams(0, VM_WC, 1f))
        top.addView(spacer, LinearLayout.LayoutParams(dp(88), dp(1)))
        col.addView(top, LinearLayout.LayoutParams(VM_MP, VM_WC))

        orb = OrbView(act)
        orb.setOnClickListener { interrupt() }
        col.addView(orb, LinearLayout.LayoutParams(VM_MP, 0, 1f))

        stateLabel = TextView(act)
        stateLabel.textSize = 14f
        stateLabel.letterSpacing = 0.05f
        stateLabel.setTextColor(Color.parseColor("#4FD8FF"))
        stateLabel.gravity = Gravity.CENTER
        stateLabel.setPadding(0, dp(4), 0, dp(6))
        col.addView(stateLabel, LinearLayout.LayoutParams(VM_MP, VM_WC))

        caption = TextView(act)
        caption.textSize = 16f
        caption.setTextColor(Color.parseColor("#C9D6E4"))
        caption.gravity = Gravity.CENTER
        caption.minLines = 3
        caption.maxLines = 6
        caption.ellipsize = TextUtils.TruncateAt.END
        caption.setPadding(dp(12), 0, dp(12), 0)
        col.addView(caption, LinearLayout.LayoutParams(VM_MP, VM_WC))

        typeBox = EditText(act)
        typeBox.hint = "JARVIS ला बोला किंवा लिहा…"
        typeBox.setHintTextColor(Color.parseColor("#7C8794"))
        typeBox.setTextColor(Color.WHITE)
        typeBox.textSize = 16f
        typeBox.setSingleLine(true)
        typeBox.imeOptions = EditorInfo.IME_ACTION_SEND
        typeBox.background = vmShape(Color.parseColor("#1F2328"), 28 * d)
        typeBox.setPadding(dp(18), dp(12), dp(18), dp(12))
        typeBox.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val t = v.text.toString().trim()
                v.text = ""
                if (t.isNotEmpty()) {
                    stopListening()
                    submit(t)
                }
                true
            } else {
                false
            }
        }
        typeBox.setOnKeyListener { _, keyCode, ev ->
            if (keyCode == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                false
            }
        }

        muteBtn = Button(act)
        muteBtn.contentDescription = "Mute"
        updateMuteIcon()
        muteBtn.setOnClickListener { toggleMute() }
        val closeBtn = Button(act)
        vmIconButton(closeBtn, 2, 48, d, Color.WHITE, Color.BLACK)
        closeBtn.contentDescription = "Close"
        closeBtn.setOnClickListener { close() }

        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(typeBox, LinearLayout.LayoutParams(0, VM_WC, 1f))
        val m1 = LinearLayout.LayoutParams(dp(48), dp(48))
        m1.setMargins(dp(8), 0, 0, 0)
        bar.addView(muteBtn, m1)
        val m2 = LinearLayout.LayoutParams(dp(48), dp(48))
        m2.setMargins(dp(8), 0, 0, 0)
        bar.addView(closeBtn, m2)
        val bp = LinearLayout.LayoutParams(VM_MP, VM_WC)
        bp.setMargins(0, dp(12), 0, 0)
        col.addView(bar, bp)

        frame.addView(col, FrameLayout.LayoutParams(VM_MP, VM_MP))
        return frame
    }

    private fun updateMuteIcon() {
        vmIconButton(muteBtn, if (muted) 1 else 0, 48, d, Color.parseColor("#2A2D33"), Color.WHITE)
    }

    private fun updateLangPill() {
        langPill.text = if (readMarathi()) "मराठी" else "English"
    }

    // ----- उघडणं आणि बंद करणं -----

    fun show() {
        if (isOpen) return
        isOpen = true
        muted = false
        listening = false
        wasBusy = false
        errors = 0
        resumeAt = 0L
        shownAnswer = lastAnswerText()
        shownStatus = statusText()
        root = build()
        val content = act.findViewById<ViewGroup>(android.R.id.content)
        content.addView(root, ViewGroup.LayoutParams(VM_MP, VM_MP))
        root.requestFocus()
        updateLangPill()
        stateLabel.text = "तयार…"
        ui.post(poll)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        ui.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        recognizer = null
        listening = false
        stopSpeaking()
        try {
            (root.parent as? ViewGroup)?.removeView(root)
        } catch (e: Exception) {
        }
    }

    // ----- दर 0.15 सेकंदाला स्थिती तपासणं -----

    private val poll = object : Runnable {
        override fun run() {
            if (!isOpen) return
            try {
                tick()
            } catch (e: Exception) {
            }
            ui.postDelayed(this, 150L)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val st = statusText()
        val thinking = st.startsWith("JARVIS is thinking")
        val speaking = isSpeaking()
        val busy = thinking || speaking

        val ans = lastAnswerText()
        if (ans.isNotEmpty() && ans != shownAnswer) {
            shownAnswer = ans
            caption.text = ans.take(260)
        } else if (!thinking && st.isNotEmpty() && st != "JARVIS is ready" && st != shownStatus) {
            shownStatus = st
            if (!listening) {
                caption.text = st
            }
        }

        if (!busy && wasBusy) {
            resumeAt = now + 600L
        }
        wasBusy = busy

        if (muted) {
            orb.mode = 4
            stateLabel.text = "म्यूट आहे"
            return
        }
        if (speaking) {
            if (listening) stopListening()
            orb.mode = 3
            stateLabel.text = "बोलतोय…"
        } else if (thinking) {
            if (listening) stopListening()
            orb.mode = 2
            stateLabel.text = "विचार करतोय…"
        } else if (listening) {
            orb.mode = 1
            stateLabel.text = "ऐकतोय…"
        } else {
            orb.mode = 0
            stateLabel.text = "…"
            if (now >= resumeAt) {
                startListening()
            }
        }
    }

    // ----- ऐकणं -----

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {
            errors = 0
        }

        override fun onRmsChanged(rmsdB: Float) {
            var lv = (rmsdB + 2f) / 12f
            if (lv < 0f) lv = 0f
            if (lv > 1f) lv = 1f
            orb.level = lv
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            onRecError(error)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            orb.level = 0f
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = if (list != null && list.isNotEmpty()) list[0].trim() else ""
            if (text.isNotEmpty()) {
                errors = 0
                submit(text)
            } else {
                resumeAt = System.currentTimeMillis() + 250L
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (list != null && list.isNotEmpty()) {
                heard = list[0]
                caption.text = "तुम्ही: " + heard
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun askPermission() {
        stateLabel.text = "माईकची परवानगी द्या"
        if (!permAsked) {
            permAsked = true
            try {
                act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            } catch (e: Exception) {
            }
        }
    }

    private fun startListening() {
        if (!isOpen || muted || listening) return
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermission()
            return
        }
        try {
            val r: SpeechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(act).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (readMarathi()) "mr-IN" else "en-IN")
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            heard = ""
            listening = true
            r.startListening(i)
        } catch (e: Exception) {
            listening = false
            errors++
            resumeAt = System.currentTimeMillis() + 1000L
        }
    }

    private fun stopListening() {
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
        }
        listening = false
        orb.level = 0f
    }

    private fun onRecError(code: Int) {
        listening = false
        orb.level = 0f
        val now = System.currentTimeMillis()
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                resumeAt = now + 250L
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                try {
                    recognizer?.destroy()
                } catch (e: Exception) {
                }
                recognizer = null
                resumeAt = now + 700L
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                stateLabel.text = "माईकची परवानगी द्या"
                resumeAt = now + 1500L
            }
            else -> {
                errors++
                if (errors >= 6) {
                    muted = true
                    updateMuteIcon()
                    caption.text = "माईक चालू होत नाही. इंटरनेट आणि Google चं Speech Services तपास. मग माईक बटण दाब."
                } else {
                    resumeAt = now + 900L
                }
            }
        }
    }

    // ----- मुख्य JARVIS ला प्रश्न पाठवणं -----

    private fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        caption.text = "तुम्ही: " + t
        val input = findInput()
        val send = findSend()
        if (input != null && send != null) {
            input.setText(t)
            send.performClick()
            resumeAt = System.currentTimeMillis() + 1500L
        } else {
            caption.text = "JARVIS ची मुख्य स्क्रीन सापडली नाही."
        }
    }

    private fun toggleMute() {
        muted = !muted
        errors = 0
        if (muted) {
            stopListening()
        } else {
            resumeAt = 0L
        }
        updateMuteIcon()
    }

    private fun interrupt() {
        stopSpeaking()
        resumeAt = System.currentTimeMillis() + 300L
    }

    private fun toggleLanguage() {
        for (v in mainViews()) {
            if (v is Button && (v.text?.toString() ?: "").startsWith("LANGUAGE:")) {
                v.performClick()
                break
            }
        }
        ui.postDelayed({
            updateLangPill()
            stopListening()
            resumeAt = System.currentTimeMillis() + 300L
        }, 200L)
    }
}

// ---------- माईक बटण जोडणं ----------

private val vmControllers = WeakHashMap<Activity, VoiceModeController>()
private val vmPatched = WeakHashMap<View, Boolean>()

private fun vmCollect(v: View, out: MutableList<View>) {
    out.add(v)
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            vmCollect(v.getChildAt(i), out)
        }
    }
}

private fun vmPatchMic(act: Activity) {
    try {
        val content = act.findViewById<ViewGroup>(android.R.id.content) ?: return
        val all = ArrayList<View>()
        vmCollect(content, all)
        for (v in all) {
            if (v is Button) {
                val desc = v.contentDescription?.toString() ?: ""
                val t = v.text?.toString() ?: ""
                if ((desc == "Speak" || t == "SPEAK") && vmPatched[v] != true) {
                    vmPatched[v] = true
                    v.setOnClickListener {
                        vmControllers.getOrPut(act) { VoiceModeController(act) }.show()
                    }
                }
            }
        }
    } catch (e: Exception) {
    }
}

private object VoiceModeHook : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        vmPatchMic(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        vmPatchMic(activity)
        Handler(Looper.getMainLooper()).postDelayed({ vmPatchMic(activity) }, 800L)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        vmControllers[activity]?.close()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        vmControllers.remove(activity)?.close()
    }
}

// app सुरू होताना हा आपोआप चालतो
class VoiceModeInit : ContentProvider() {
    override fun onCreate(): Boolean {
        val c = context
        val app = c?.applicationContext as? Application
        app?.registerActivityLifecycleCallbacks(VoiceModeHook)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
