package com.jarvis.ai

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

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
}
