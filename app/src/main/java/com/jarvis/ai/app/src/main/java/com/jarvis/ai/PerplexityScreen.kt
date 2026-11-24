package com.jarvis.ai

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class PxMsg(val fromUser: Boolean, val text: String)
class PxSession(val id: Long, val title: String, val msgs: MutableList<PxMsg> = mutableListOf())

/**
 * Perplexity सारखी स्क्रीन (Compose नको, साधे Android views).
 * onAsk = (प्रश्न, उत्तर-परत-देण्याचे-फंक्शन)
 */
class PerplexityView(
    context: Context,
    private val onAsk: (String, (String) -> Unit) -> Unit
) : LinearLayout(context) {

    private val bg = Color.parseColor("#151619")
    private val card = Color.parseColor("#1E2024")
    private val accent = Color.parseColor("#00E5FF")
    private val prefs = context.getSharedPreferences("jarvis_sessions", 0)
    private val sessions: MutableList<PxSession> = loadSessions().toMutableList()
    private var current: PxSession? = null
    private var incognito = false
    private val main = Handler(Looper.getMainLooper())

    private val titleView = TextView(context)
    private val newBtn = TextView(context)
    private val list = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val input = EditText(context)

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        fitsSystemWindows = true

        // वरची पट्टी
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(12), dp(16), dp(12))
        val gear = TextView(context)
        gear.text = "⚙"
        gear.textSize = 24f
        gear.setTextColor(Color.WHITE)
        gear.setOnClickListener { showSettings() }
        titleView.textSize = 20f
        titleView.setTextColor(accent)
        titleView.setPadding(dp(16), 0, 0, 0)
        newBtn.text = "＋ नवीन"
        newBtn.setTextColor(Color.WHITE)
        newBtn.setOnClickListener { current = null; refresh() }
        header.addView(gear)
        header.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(newBtn)
        addView(header)

        // मधला भाग
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), 0, dp(16), dp(8))
        scroll.addView(list)
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // खालची पट्टी
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = round(card, 28)
        bar.setPadding(dp(16), dp(4), dp(8), dp(4))
        input.hint = "काहीही विचारा…"
        input.setHintTextColor(Color.GRAY)
        input.setTextColor(Color.WHITE)
        input.background = null
        input.maxLines = 4
        val go = TextView(context)
        go.text = "➤"
        go.textSize = 24f
        go.setTextColor(accent)
        go.setPadding(dp(12), dp(8), dp(12), dp(8))
        go.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) { input.setText(""); send(t) }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(go)
        val barLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        barLp.setMargins(dp(12), dp(8), dp(12), dp(12))
        addView(bar, barLp)

        refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun label(text: String, size: Float, color: Int): TextView {
        val t = TextView(context)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    private fun refresh() {
        list.removeAllViews()
        titleView.text = if (incognito) "J.A.R.V.I.S  🕶" else "J.A.R.V.I.S"
        val s = current
        newBtn.visibility = if (s == null) View.GONE else View.VISIBLE
        if (s == null) {
            list.addView(label("Sessions", 18f, Color.WHITE))
            if (sessions.isEmpty()) list.addView(label("अजून काही चॅट नाही.", 15f, Color.GRAY))
            for (x in sessions) {
                val row = label(x.title, 16f, Color.WHITE)
                row.setPadding(0, dp(14), 0, dp(14))
                row.setOnClickListener { current = x; refresh() }
                list.addView(row)
            }
        } else {
            for (m in s.msgs) {
                val b = label(m.text, 16f, Color.WHITE)
                b.setPadding(dp(14), dp(10), dp(14), dp(10))
                if (m.fromUser) b.background = round(card, 16)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.gravity = if (m.fromUser) Gravity.END else Gravity.START
                lp.setMargins(0, dp(6), 0, dp(6))
                list.addView(b, lp)
            }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun send(text: String) {
        var s = current
        if (s == null) {
            s = PxSession(System.currentTimeMillis(), text.take(40))
            current = s
            if (!incognito) sessions.add(0, s)
        }
        s.msgs.add(PxMsg(true, text))

        // आधी फोन कमांड (YouTube वगैरे) तपासा
        val cmd = PhoneCommands.handle(context, text)
        if (cmd != null) {
            s.msgs.add(PxMsg(false, cmd))
            saveIfNeeded()
            refresh()
            return
        }

        val idx = s.msgs.size
        s.msgs.add(PxMsg(false, "…"))
        refresh()
        val target: PxSession = s
        try {
            onAsk(text) { reply ->
                main.post {
                    target.msgs[idx] = PxMsg(false, reply)
                    saveIfNeeded()
                    if (current === target) refresh()
                }
            }
        } catch (e: Exception) {
            target.msgs[idx] = PxMsg(false, "त्रुटी: " + e.message)
            refresh()
        }
    }

    private fun showSettings() {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(16), dp(20), dp(8))
        val sw = Switch(context)
        sw.text = "Incognito Mode (चॅट सेव्ह होणार नाहीत)"
        sw.isChecked = incognito
        val clear = Button(context)
        clear.text = "Clear History"
        clear.setOnClickListener {
            sessions.clear()
            saveIfNeeded()
            current = null
            refresh()
        }
        box.addView(sw)
        box.addView(clear)
        AlertDialog.Builder(context)
            .setTitle("Settings")
            .setView(box)
            .setPositiveButton("बंद") { _, _ ->
                if (incognito != sw.isChecked) { incognito = sw.isChecked; current = null }
                refresh()
            }
            .show()
    }

    private fun saveIfNeeded() {
        if (incognito) return
        val arr = JSONArray()
        for (s in sessions) {
            val m = JSONArray()
            for (x in s.msgs) m.put(JSONObject().put("u", x.fromUser).put("t", x.text))
            arr.put(JSONObject().put("id", s.id).put("title", s.title).put("msgs", m))
        }
        prefs.edit().putString("data", arr.toString()).apply()
    }

    private fun loadSessions(): List<PxSession> {
        return try {
            val arr = JSONArray(prefs.getString("data", "[]"))
            val out = mutableListOf<PxSession>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val m = o.getJSONArray("msgs")
                val s = PxSession(o.getLong("id"), o.getString("title"))
                for (j in 0 until m.length()) {
                    val x = m.getJSONObject(j)
                    s.msgs.add(PxMsg(x.getBoolean("u"), x.getString("t")))
                }
                out.add(s)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
