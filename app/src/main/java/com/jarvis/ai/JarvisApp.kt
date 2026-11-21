package com.jarvis.ai

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 60, 40, 40)
        layout.setBackgroundColor(Color.rgb(5, 12, 28))

        val title = TextView(this)
        title.text = "J.A.R.V.I.S"
        title.textSize = 32f
        title.setTextColor(Color.CYAN)
        title.gravity = Gravity.CENTER

        val keyInput = EditText(this)
        keyInput.hint = "Paste Gemini key (only once)"
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val saveKey = Button(this)
        saveKey.text = "SAVE KEY"

        val status = TextView(this)
        status.textSize = 17f
        status.setTextColor(Color.GREEN)
        status.setPadding(0, 30, 0, 30)
        status.text = if (prefs.getString("key", "").isNullOrEmpty()) {
            "First save your Gemini key"
        } else {
            "JARVIS is ready"
        }

        val scroll = ScrollView(this)
        scroll.addView(status)

        val input = EditText(this)
        input.hint = "Ask JARVIS anything..."
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)

        val send = Button(this)
        send.text = "SEND"

        saveKey.setOnClickListener {
            val k = keyInput.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs.edit().putString("key", k).apply()
                keyInput.setText("")
                status.text = "Key saved. JARVIS is ready"
            } else {
                status.text = "Please paste your key"
            }
        }

        send.setOnClickListener {
            val question = input.text.toString().trim()
            val key = prefs.getString("key", "") ?: ""
            if (key.isEmpty()) {
                status.text = "First save your Gemini key"
            } else if (question.isEmpty()) {
                status.text = "Please enter a message"
            } else {
                status.text = "JARVIS is thinking..."
                Thread {
                    var answer = "No answer"
                    try {
                        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("x-goog-api-key", key)
                        conn.connectTimeout = 20000
                        conn.readTimeout = 60000
                        conn.doOutput = true

                        val part = JSONObject().put("text", question)
                        val content = JSONObject().put("parts", JSONArray().put(part))
                        val body = JSONObject().put("contents", JSONArray().put(content))
                        conn.outputStream.use { it.write(body.toString().toByteArray()) }

                        val code = conn.responseCode
                        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val text = stream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)

                        if (json.has("candidates")) {
                            answer = json.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                        } else if (json.has("error")) {
                            answer = "Error: " + json.getJSONObject("error").optString("message")
                        }
                    } catch (e: Exception) {
                        answer = "Error: " + e.message
                    }
                    runOnUiThread { status.text = answer }
                }.start()
            }
        }

        layout.addView(title)
        layout.addView(keyInput)
        layout.addView(saveKey)
        layout.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        layout.addView(input)
        layout.addView(send)

        setContentView(layout)
    }
}
