 package com.jarvis.ai

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.util.Xml
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

class JarvisApplication : Application() {

    private var pxIncognito = false
    private var pxSkip = false
    private var pxCurrentId: Long? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity is MainActivity) {
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    val layout = content?.getChildAt(0) as? LinearLayout
                    if (layout != null) {
                        applyCleanUi(activity, layout)
                        try {
                            hookPhoneCommands(activity, layout)
                        } catch (e: Exception) {
                        }
                        try {
                            hideRings(layout)
                        } catch (e: Exception) {
                        }
                        try {
                            applyPerplexityLook(activity, layout)
                        } catch (e: Exception) {
                        }
                    }
                }
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun collect(v: View, out: MutableList<View>) {
        out.add(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                collect(v.getChildAt(i), out)
            }
        }
    }

    // निळ्या रिंग (HUD) लपवतो. काढून टाकायचे असल्यास हेच फंक्शन वापरले आहे.
    private fun hideRings(root: View) {
        val all = ArrayList<View>()
        collect(root, all)
        for (v in all) {
            if (v.javaClass.simpleName == "HudView") {
                v.alpha = 0f
            }
        }
    }

    // Send बटण दाबले की आधी फोन कमांड (YouTube) तपासतो. कमांड नसेल तर जुने काम चालू राहते.
    private fun hookPhoneCommands(activity: Activity, root: View) {
        val all = ArrayList<View>()
        collect(root, all)
        val send = all.firstOrNull { it is Button && it.contentDescription?.toString() == "Send" } as? Button ?: return
        if (send.tag == "cmd-hooked") return
        val input = all.firstOrNull {
            it is EditText && (it.hint?.toString() ?: "").startsWith("JARVIS")
        } as? EditText ?: return
        val scroll = all.firstOrNull { it is ScrollView && it.childCount == 1 && it.getChildAt(0) is TextView } as? ScrollView
        val chat = scroll?.getChildAt(0) as? TextView
        send.tag = "cmd-hooked"
        send.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (isNewsRequest(text)) {
                        input.setText("")
                        chat?.append("\n\nतुम्ही: " + text + "\nJARVIS: बातम्या आणत आहे…")
                        fetchNews(activity, chat, text)
                        v.isPressed = false
                        return@setOnTouchListener true
                    }
                    val reply = PhoneCommands.handle(activity, text)
                    if (reply != null) {
                        input.setText("")
                        chat?.append("\n\nतुम्ही: " + text + "\nJARVIS: " + reply)
                        v.isPressed = false
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    // ---------- Perplexity सारखे: Sessions + Incognito ----------

    private fun applyPerplexityLook(activity: Activity, layout: LinearLayout) {
        val header = layout.getChildAt(0) as? LinearLayout ?: return
        if (header.tag == "px-hooked") return
        if (header.childCount < 2) return
        header.tag = "px-hooked"
        val d = activity.resources.displayMetrics.density
        val prefs = activity.getSharedPreferences("jarvis_px", Context.MODE_PRIVATE)

        fun iconButton(label: String): TextView {
            val t = TextView(activity)
            t.text = label
            t.textSize = 20f
            t.gravity = Gravity.CENTER
            t.setTextColor(android.graphics.Color.WHITE)
            return t
        }

        val size = (44 * d + 0.5f).toInt()
        val sessionsBtn = iconButton("🕘")
        val incBtn = iconButton("🕶")
        incBtn.alpha = 0.45f
        sessionsBtn.setOnClickListener { showSessions(activity, prefs) }
        incBtn.setOnClickListener {
            pxIncognito = !pxIncognito
            if (pxIncognito) {
                pxSkip = true
                pxCurrentId = null
            }
            incBtn.alpha = if (pxIncognito) 1f else 0.45f
            Toast.makeText(
                activity,
                if (pxIncognito) "Incognito चालू: चॅट सेव्ह होणार नाहीत" else "Incognito बंद",
                Toast.LENGTH_SHORT
            ).show()
        }
        val at = header.childCount - 1
        header.addView(sessionsBtn, at, LinearLayout.LayoutParams(size, size))
        header.addView(incBtn, at + 1, LinearLayout.LayoutParams(size, size))

        val all = ArrayList<View>()
        collect(layout, all)
        for (v in all) {
            if (v is HorizontalScrollView && v.childCount > 0) {
                v.getChildAt(0).visibility = View.GONE
            }
        }
        val scroll = all.firstOrNull { it is ScrollView && it.childCount == 1 && it.getChildAt(0) is TextView } as? ScrollView
        val chat = scroll?.getChildAt(0) as? TextView
        chat?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    recordChat(prefs, s?.toString() ?: "")
                } catch (e: Exception) {
                }
            }
        })
    }

    private fun readList(prefs: SharedPreferences): JSONArray {
        return try {
            JSONArray(prefs.getString("list", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun recordChat(prefs: SharedPreferences, text: String) {
        if (pxSkip) {
            if (text.isBlank()) pxSkip = false
            return
        }
        if (pxIncognito) return
        if (text.isBlank()) {
            pxCurrentId = null
            return
        }
        val arr = readList(prefs)
        var id = pxCurrentId
        if (id == null) {
            id = System.currentTimeMillis()
            pxCurrentId = id
        }
        var firstLine = ""
        for (ln in text.lines()) {
            if (ln.isNotBlank()) {
                firstLine = ln.trim()
                break
            }
        }
        val title = if (firstLine.isEmpty()) "चॅट" else firstLine.take(40)
        val body = text.take(20000)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("id") == id) {
                o.put("title", title)
                o.put("text", body)
                prefs.edit().putString("list", arr.toString()).apply()
                return
            }
        }
        val out = JSONArray()
        out.put(JSONObject().put("id", id).put("title", title).put("text", body))
        for (i in 0 until minOf(arr.length(), 29)) {
            out.put(arr.getJSONObject(i))
        }
        prefs.edit().putString("list", out.toString()).apply()
    }

    private fun showSessions(activity: Activity, prefs: SharedPreferences) {
        val arr = readList(prefs)
        if (arr.length() == 0) {
            Toast.makeText(activity, "अजून काही चॅट सेव्ह नाही", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = Array(arr.length()) { arr.getJSONObject(it).getString("title") }
        AlertDialog.Builder(activity)
            .setTitle("Sessions")
            .setItems(titles) { _, i ->
                val o = arr.getJSONObject(i)
                AlertDialog.Builder(activity)
                    .setTitle(o.getString("title"))
                    .setMessage(o.getString("text"))
                    .setPositiveButton("बंद", null)
                    .show()
            }
            .setNeutralButton("Clear History") { _, _ ->
                prefs.edit().remove("list").apply()
                pxCurrentId = null
            }
            .setPositiveButton("बंद", null)
            .show()
    }

    // ---------- आजच्या बातम्या (Google News, मराठी) ----------

    private fun isNewsRequest(text: String): Boolean {
        val t = text.lowercase()
        if (t.length > 60) return false
        return t.contains("बातम्या") || t.contains("बातमी") || t.contains("news")
    }

    private fun fetchNews(activity: Activity, chat: TextView?, request: String) {
        Thread {
            var result: String? = null
            try {
                val key = activity.getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("key", "") ?: ""
                if (key.isNotEmpty()) {
                    result = detailedNews(key, request)
                }
            } catch (e: Exception) {
            }
            if (result == null) {
                result = headlines()
            }
            val out: String = result
            activity.runOnUiThread {
                chat?.append("\n" + out)
            }
        }.start()
    }

    // Gemini + Google Search: आजच्या खऱ्या बातम्या, सविस्तर, मराठीत
    private fun detailedNews(key: String, request: String): String? {
        val today = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
        val prompt = "आजची तारीख: " + today + ". वापरकर्त्याची विनंती: \"" + request + "\". " +
            "Google Search वापरून आजच्या सर्वात ताज्या आणि महत्त्वाच्या 5 बातम्या शोधा " +
            "(विनंतीत विषय असेल तर त्या विषयाच्या, नाहीतर भारत आणि महाराष्ट्राच्या). " +
            "प्रत्येक बातमीला शीर्षक द्या आणि 3-4 वाक्यांत सविस्तर माहिती द्या. " +
            "सोप्या मराठीत देवनागरी लिपीत लिहा. शेवटी स्रोताचे नाव द्या."
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .toString()
        val models = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash")
        for (m in models) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent?key=" + key)
                val c = url.openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 10000
                c.readTimeout = 45000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (c.responseCode != 200) continue
                val resp = c.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text", ""))
                }
                val t = sb.toString().trim()
                if (t.isNotEmpty()) return t
            } catch (e: Exception) {
            }
        }
        return null
    }

    // Gemini चालले नाही तर फक्त हेडलाइन (Google News RSS)
    private fun headlines(): String {
        return try {
            val c = URL("https://news.google.com/rss?hl=mr&gl=IN&ceid=IN:mr").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            val parser = Xml.newPullParser()
            parser.setInput(c.inputStream, null)
            val titles = ArrayList<String>()
            var inItem = false
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT && titles.size < 5) {
                if (ev == XmlPullParser.START_TAG) {
                    if (parser.name == "item") {
                        inItem = true
                    } else if (inItem && parser.name == "title") {
                        titles.add(parser.nextText().trim())
                    }
                } else if (ev == XmlPullParser.END_TAG && parser.name == "item") {
                    inItem = false
                }
                ev = parser.next()
            }
            if (titles.isEmpty()) {
                "आत्ता बातम्या मिळाल्या नाहीत. थोड्या वेळाने पुन्हा सांगा."
            } else {
                val sb = StringBuilder("आजच्या ताज्या बातम्या (फक्त हेडलाइन):")
                for ((i, t) in titles.withIndex()) {
                    sb.append("\n").append(i + 1).append(". ").append(t)
                }
                sb.toString()
            }
        } catch (e: Exception) {
            "बातम्या आणता आल्या नाहीत. इंटरनेट तपासा."
        }
    }
}
