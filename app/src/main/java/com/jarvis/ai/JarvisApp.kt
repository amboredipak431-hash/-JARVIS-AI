package com.jarvis.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var chatView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var langButton: Button
    private lateinit var modeButton: Button
    private lateinit var masterButton: Button
    private lateinit var lengthButton: Button
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var marathi = true
    private var studyMode = false
    private var lengthMode = 1
    private var lastAnswer = ""
    private val chatLog = StringBuilder()
    private val history = ArrayList<Pair<String, String>>()
    private val speechCode = 101

    private fun isOn(): Boolean {
        return prefs.getBoolean("on", true)
    }

    private fun say(mr: String, en: String): String {
        return if (marathi) mr else en
    }

    private fun has(t: String, vararg words: String): Boolean {
        for (w in words) {
            if (t.contains(w)) return true
        }
        return false
    }

    private fun langLabel(): String {
        return if (marathi) "LANGUAGE: MARATHI" else "LANGUAGE: ENGLISH"
    }

    private fun modeLabel(): String {
        return if (studyMode) "MODE: STUDY" else "MODE: NORMAL"
    }

    private fun masterLabel(): String {
        return if (isOn()) "MASTER SWITCH: ON" else "MASTER SWITCH: OFF"
    }

    private fun lengthLabel(): String {
        return when (lengthMode) {
            0 -> "ANSWER: SHORT"
            2 -> "ANSWER: DETAILED"
            else -> "ANSWER: NORMAL"
        }
    }

    private fun makeRow(vararg buttons: Button): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (b in buttons) {
            b.textSize = 12f
            r.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return r
    }

    private fun saveHistory() {
        val arr = JSONArray()
        for (p in history) {
            arr.put(JSONArray().put(p.first).put(p.second))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        try {
            val s = prefs.getString("history", "") ?: ""
            if (s.isEmpty()) return
            val arr = JSONArray(s)
            for (k in 0 until arr.length()) {
                val p = arr.getJSONArray(k)
                history.add(Pair(p.getString(0), p.getString(1)))
            }
        } catch (e: Exception) {
            history.clear()
        }
    }

    private fun addChat(who: String, text: String) {
        chatLog.append(who).append(": ").append(text).append("\n\n")
        if (chatLog.length > 8000) {
            chatLog.delete(0, chatLog.length - 8000)
            val cut = chatLog.indexOf("\n\n")
            if (cut >= 0 && cut + 2 < chatLog.length) {
                chatLog.delete(0, cut + 2)
            }
        }
        prefs.edit().putString("chat", chatLog.toString()).apply()
        chatView.text = chatLog.toString()
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        marathi = prefs.getBoolean("marathi", true)
        lengthMode = prefs.getInt("length", 1)
        loadHistory()
        chatLog.append(prefs.getString("chat", "") ?: "")

        tts = TextToSpeech(this) { s ->
            ttsReady = (s == TextToSpeech.SUCCESS)
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 50, 30, 30)
        layout.setBackgroundColor(Color.rgb(5, 12, 28))

        val title = TextView(this)
        title.text = "J.A.R.V.I.S"
        title.textSize = 30f
        title.setTextColor(Color.CYAN)
        title.gravity = Gravity.CENTER

        masterButton = Button(this)
        masterButton.text = masterLabel()
        modeButton = Button(this)
        modeButton.text = modeLabel()
        langButton = Button(this)
        langButton.text = langLabel()
        lengthButton = Button(this)
        lengthButton.text = lengthLabel()

        val keyInput = EditText(this)
        keyInput.hint = "Paste Gemini key (only once)"
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val saveKey = Button(this)
        saveKey.text = "SAVE KEY"

        chatView = TextView(this)
        chatView.textSize = 17f
        chatView.setTextColor(Color.GREEN)
        chatView.setPadding(0, 20, 0, 20)
        chatView.text = chatLog.toString()

        scrollView = ScrollView(this)
        scrollView.addView(chatView)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        status = TextView(this)
        status.textSize = 14f
        status.setTextColor(Color.YELLOW)
        status.setPadding(0, 10, 0, 10)
        status.text = if (prefs.getString("key", "").isNullOrEmpty()) {
            "First save your Gemini key"
        } else {
            "JARVIS is ready"
        }

        input = EditText(this)
        input.hint = "Ask JARVIS anything..."
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)

        val send = Button(this)
        send.text = "SEND"
        val mic = Button(this)
        mic.text = "SPEAK"
        val stop = Button(this)
        stop.text = "STOP"

        masterButton.setOnClickListener {
            val now = !isOn()
            prefs.edit().putBoolean("on", now).apply()
            if (!now) tts?.stop()
            masterButton.text = masterLabel()
            status.text = if (now) "JARVIS is ON" else "JARVIS is OFF. Nothing will work until you turn it ON."
        }

        modeButton.setOnClickListener {
            studyMode = !studyMode
            modeButton.text = modeLabel()
            status.text = if (studyMode) {
                say("STUDY mode चालू. विषयाचं नाव सांग.", "STUDY mode ON. Tell me a topic.")
            } else {
                say("NORMAL mode चालू.", "NORMAL mode ON.")
            }
        }

        langButton.setOnClickListener { setLang(!marathi) }

        lengthButton.setOnClickListener {
            setLength((lengthMode + 1) % 3)
        }

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
            val q = input.text.toString().trim()
            if (q.isEmpty()) {
                status.text = "Please enter a message"
            } else {
                handle(q)
            }
        }

        mic.setOnClickListener { startVoice() }
        stop.setOnClickListener { tts?.stop() }

        layout.addView(title)
        layout.addView(makeRow(masterButton, modeButton))
        layout.addView(makeRow(langButton, lengthButton))
        layout.addView(keyInput)
        layout.addView(saveKey)
        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        layout.addView(status)
        layout.addView(input)
        layout.addView(makeRow(send, mic, stop))

        setContentView(layout)
    }

    private fun setLang(m: Boolean) {
        marathi = m
        prefs.edit().putBoolean("marathi", marathi).apply()
        langButton.text = langLabel()
        status.text = say("भाषा: मराठी", "Language: English")
    }

    private fun setLength(n: Int) {
        lengthMode = n
        prefs.edit().putInt("length", n).apply()
        lengthButton.text = lengthLabel()
        status.text = lengthLabel()
    }

    private fun startVoice() {
        if (!isOn()) {
            status.text = "JARVIS is OFF. Tap MASTER SWITCH to turn it ON."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (marathi) "mr-IN" else "en-IN")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        try {
            startActivityForResult(intent, speechCode)
        } catch (e: Exception) {
            status.text = "Voice typing is not available on this phone"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechCode && resultCode == Activity.RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = if (results != null && results.isNotEmpty()) results[0] else ""
            if (spoken.isNotEmpty()) {
                handle(spoken)
            }
        }
    }

    private fun speak(text: String) {
        val t = tts
        if (t == null || !ttsReady || !isOn()) return
        val wanted = if (marathi) Locale("mr", "IN") else Locale("en", "IN")
        val r = t.setLanguage(wanted)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("hi", "IN"))
        }
        t.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun show(text: String, speakIt: Boolean) {
        if (speakIt) {
            lastAnswer = text
            status.text = ""
            addChat("JARVIS", text)
            speak(text)
        } else {
            status.text = text
        }
    }

    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Allow?")
            .setMessage(message)
            .setPositiveButton("YES") { _, _ -> action() }
            .setNegativeButton("NO") { _, _ -> show(say("रद्द केलं.", "Cancelled."), false) }
            .show()
    }

    private fun launch(i: Intent, okMsg: String) {
        try {
            startActivity(i)
            show(okMsg, false)
        } catch (e: Exception) {
            show("Could not do this on your phone: " + e.message, false)
        }
    }

    private fun openPackage(pkg: String, fallbackUrl: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            launch(i, say("उघडतो...", "Opening..."))
        } else {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)), say("Browser मध्ये उघडतो...", "Opening in browser..."))
        }
    }

    private fun openSettings(action: String) {
        launch(Intent(action), say("Settings उघडली. बदल तू स्वतः कर.", "Settings opened. Please change it yourself."))
    }

    private fun setTorch(on: Boolean) {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (cid in cm.cameraIdList) {
                val flash = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (flash == true) {
                    id = cid
                    break
                }
            }
            if (id == null) {
                show("No torch found on this phone", false)
                return
            }
            cm.setTorchMode(id, on)
            show(if (on) "Torch ON" else "Torch OFF", false)
        } catch (e: Exception) {
            show("Torch error: " + e.message, false)
        }
    }

    private fun setAlarm(h: Int, min: Int) {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, h)
        i.putExtra(AlarmClock.EXTRA_MINUTES, min)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS")
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        launch(i, say("Alarm लावला.", "Alarm set."))
    }

    private fun setTimer(seconds: Int) {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
        i.putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS")
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        launch(i, say("Timer सुरू केला.", "Timer started."))
    }

    private fun normDigits(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c in '\u0966'..'\u096F') {
                sb.append(('0'.code + (c.code - 0x0966)).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun parseTime(t: String): IntArray? {
        var h = 0
        var min = 0
        val m = Regex("([0-9]{1,2})\\s*[:.]\\s*([0-9]{2})").find(t)
        if (m != null) {
            h = m.groupValues[1].toInt()
            min = m.groupValues[2].toInt()
        } else {
            val n = Regex("([0-9]{1,2})").find(t) ?: return null
            h = n.groupValues[1].toInt()
        }
        val pm = has(t, "संध्याकाळ", "सायंकाळ", "रात्र", "दुपार", "evening", "night", "afternoon") ||
            Regex("\\bp\\.?m\\b").containsMatchIn(t)
        val am = has(t, "सकाळ", "morning") || Regex("\\ba\\.?m\\b").containsMatchIn(t)
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        if (has(t, "रात्र", "night") && h == 12) h = 0
        if (h > 23 || min > 59) return null
        return intArrayOf(h, min)
    }

    private fun needsSearch(t: String): Boolean {
        return has(
            t, "news", "बातम", "weather", "हवामान", "तापमान", "current affairs",
            "चालू घडामोडी", "score", "स्कोअर", "सामना", "latest", "ताज्या",
            "updates", "अपडेट", "निकाल", "result"
        )
    }

    private fun systemText(study: Boolean): String {
        val lang = if (marathi) "Marathi" else "English"
        val base = if (lengthMode == 0) 60 else if (lengthMode == 2) 250 else 100
        val limit = if (study) base * 2 else base
        val today = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val notes = (prefs.getString("notes", "") ?: "").takeLast(3000)
        var s = "You are JARVIS, a calm, polite and friendly personal assistant. " +
            "Today is " + today + ". Reply in simple " + lang + " with short sentences and very simple words. " +
            "Keep the answer under " + limit + " words. Do not use markdown, stars or hashtags. " +
            "Answer only what the user asked. Do not bring up studies, nursing, exams or textbooks unless the user does. " +
            "Do not add medical warnings or disclaimers yourself. " +
            "For a simple greeting, just greet back in one short sentence. " +
            "You cannot control the phone yourself. " +
            "Background: the user is a first-year GNM nursing student in India. Use this only when the question is about studies."
        if (notes.isNotEmpty()) {
            s += " Things the user asked you to remember (use them when relevant): " + notes.replace("\n", "; ")
        }
        s += " If the user tells you something personal and important to remember " +
            "(name, exam date, deadline, goal, family or friend details), " +
            "add one extra last line exactly like this: SAVE: <short fact>. " +
            "Never save passwords, OTPs, bank or payment details. Never mention the SAVE line."
        if (study) {
            s += " The user is in STUDY mode, so studies are the topic now. You are a kind nursing tutor. " +
                "When the student names a topic: first explain it simply, " +
                "then give 5 important points, then ask 5 questions. " +
                "If the student is answering your questions, check the answers kindly and give the correct answers briefly."
        }
        return s
    }

    private fun askGemini(question: String, search: Boolean, study: Boolean) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) {
            status.text = "First save your Gemini key"
            return
        }
        status.text = "JARVIS is thinking..."
        val disclaimer = has(
            question.lowercase(Locale.ROOT),
            "dose", "डोस", "drug", "औषध", "ड्रग", "treatment", "उपचार", "care plan", "केअर प्लॅन"
        )
        val sys = systemText(study)
        val models = listOf("gemini-flash-lite-latest", "gemini-flash-latest")
        val tries = ArrayList<Pair<String, Boolean>>()
        for (m in models) {
            tries.add(Pair(m, search))
        }
        if (search) {
            tries.add(Pair(models[0], false))
        }
        val past = ArrayList<Pair<String, String>>(history)

        Thread {
            var answer = ""
            var err = ""
            for (tr in tries) {
                try {
                    val contents = JSONArray()
                    for (p in past) {
                        contents.put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", p.first)))
                        )
                        contents.put(
                            JSONObject().put("role", "model")
                                .put("parts", JSONArray().put(JSONObject().put("text", p.second)))
                        )
                    }
                    contents.put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", question)))
                    )
                    val body = JSONObject()
                    body.put("contents", contents)
                    body.put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    )
                    if (tr.second) {
                        body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                    }

                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + tr.first + ":generateContent")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-goog-api-key", key)
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (text.isEmpty()) {
                        err = "No response. Check internet."
                        continue
                    }
                    val json = JSONObject(text)
                    val cands = json.optJSONArray("candidates")
                    if (cands != null && cands.length() > 0) {
                        val content = cands.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val sb = StringBuilder()
                        if (parts != null) {
                            for (k in 0 until parts.length()) {
                                sb.append(parts.getJSONObject(k).optString("text", ""))
                            }
                        }
                        if (sb.isNotEmpty()) {
                            answer = sb.toString()
                            break
                        }
                        err = "Empty answer"
                    } else if (json.has("error")) {
                        err = json.getJSONObject("error").optString("message")
                    } else {
                        err = "No answer"
                    }
                } catch (e: Exception) {
                    err = e.message ?: "unknown error"
                }
            }
            val finalAnswer = answer.replace("*", "").replace("#", "").trim()
            runOnUiThread {
                if (finalAnswer.isEmpty()) {
                    status.text = "Error: " + err
                } else {
                    val keep = ArrayList<String>()
                    val facts = ArrayList<String>()
                    for (ln in finalAnswer.split("\n")) {
                        val tl = ln.trim()
                        if (tl.startsWith("SAVE:", ignoreCase = true)) {
                            val f = tl.substring(5).trim()
                            if (f.isNotEmpty()) facts.add(f)
                        } else {
                            keep.add(ln)
                        }
                    }
                    var cleaned = keep.joinToString("\n").trim()
                    if (cleaned.isEmpty()) {
                        cleaned = say("ठीक आहे.", "Okay.")
                    }
                    history.add(Pair(question.take(400), cleaned))
                    while (history.size > 6) {
                        history.removeAt(0)
                    }
                    saveHistory()
                    var shown = cleaned
                    if (disclaimer) {
                        shown += "\n\n" + say(
                            "डोस आणि उपचार textbook किंवा शिक्षकांकडून तपासा.",
                            "Check doses and treatment with your textbook or teacher."
                        )
                    }
                    if (!search) {
                        for (f in facts.take(3)) {
                            val fact = f.take(200)
                            saveNote(fact)
                            shown += "\n\n" + say("नोट सेव केली: ", "Note saved: ") + fact
                        }
                    }
                    show(shown, true)
                }
            }
        }.start()
    }

    private fun saveNote(text: String) {
        val date = SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date())
        val old = prefs.getString("notes", "") ?: ""
        prefs.edit().putString("notes", old + date + ": " + text + "\n").apply()
    }

    private fun handle(raw: String) {
        if (!isOn()) {
            show("JARVIS is OFF. Tap MASTER SWITCH to turn it ON.", false)
            return
        }
        val t = normDigits(raw).lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return

        addChat("You", raw.trim())
        input.setText("")

        // Master off by voice
        if (has(t, "सगळं बंद", "सगळे बंद", "सर्व बंद", "stop everything", "privacy mode", "प्रायव्हसी मोड")) {
            prefs.edit().putBoolean("on", false).apply()
            tts?.stop()
            masterButton.text = masterLabel()
            show("JARVIS is OFF. Tap MASTER SWITCH to turn it ON.", false)
            return
        }
        if (t.length <= 20 && has(t, "थांब", "stop", "शांत")) {
            tts?.stop()
            status.text = say("थांबलो.", "Stopped.")
            return
        }
        if (t.length <= 25 && has(t, "पुन्हा सांग", "परत सांग", "repeat")) {
            if (lastAnswer.isEmpty()) {
                show(say("पुन्हा सांगायला काही नाही.", "Nothing to repeat."), false)
            } else {
                speak(lastAnswer)
            }
            return
        }
        if (t.length <= 30 && has(t, "थोडक्यात", "briefly", "in short", "short answer")) {
            setLength(0)
            return
        }
        if (t.length <= 30 && has(t, "विस्ताराने", "in detail", "detailed")) {
            setLength(2)
            return
        }
        if (t.length <= 25 && has(t, "मराठीत सांग", "मराठीत बोल", "in marathi", "speak marathi")) {
            setLang(true)
            return
        }
        if (t.length <= 30 && has(
                t, "इंग्लिश मध्ये", "इंग्रजीत सांग", "इंग्रजीत बोल", "in english", "speak english"
            )
        ) {
            setLang(false)
            return
        }

        // Payment / bank / OTP apps: never
        val payWords = has(
            t, "phonepe", "phone pe", "फोनपे", "gpay", "google pay", "गुगल पे", "paytm", "पेटीएम",
            "bhim", "upi", "युपीआय", "यूपीआय", "bank", "बँक", "otp", "ओटीपी", "password", "पासवर्ड"
        )
        val doWords = has(
            t, "open", "उघड", "ओपन", "पाठव", "send", "transfer", "ट्रान्सफर", "पे कर", "pay now"
        )
        if (payWords && doWords) {
            show(
                say(
                    "पेमेंट, बँक, OTP आणि password ला JARVIS हात लावत नाही.",
                    "JARVIS never touches payment, bank, OTP or password apps."
                ),
                true
            )
            return
        }

        // Message on WhatsApp / Instagram: JARVIS only prepares it, you press Send
        val isWa = has(t, "whatsapp", "व्हॉट्सअ", "व्हाट्सअ", "व्हॉट्स अ", "व्हाट्स अ")
        val isIg = has(t, "instagram", "insta", "इन्स्टा", "इंस्टा")
        val msgWord = has(t, "msg", "message", "मेसेज", "मॅसेज", "मेसज", "संदेश", "पाठव", "send")
        if ((isWa || isIg) && msgWord) {
            val rt = raw.trim()
            var msg = ""
            var best = -1
            var bestLen = 0
            for (mk in listOf(" की ", " म्हणून ", " saying ", " that ", ":")) {
                var ix = t.indexOf(mk)
                if (mk == ":" && ix > 0 && t[ix - 1].isDigit()) {
                    ix = -1
                }
                if (ix >= 0 && (best < 0 || ix < best)) {
                    best = ix
                    bestLen = mk.length
                }
            }
            if (best >= 0 && best + bestLen <= rt.length) {
                msg = rt.substring(best + bestLen).trim()
            }
            val nm = Regex("\\+?[0-9][0-9 \\-]{7,}[0-9]").find(t)
            var digits = ""
            if (nm != null) {
                digits = nm.value.replace(Regex("[^0-9]"), "")
                if (digits.length == 10) digits = "91" + digits
            }
            val finalMsg = msg
            val finalDigits = digits
            val igOnly = isIg && !isWa
            val appName = if (igOnly) "Instagram" else "WhatsApp"
            val ask = say(
                appName + " उघडून संदेश तयार ठेवू का?",
                "Open " + appName + " with the message ready?"
            ) + (if (finalMsg.isNotEmpty()) "\n\n" + finalMsg else "") +
                "\n\n" + say("Send तू स्वतः दाबायचं.", "You press Send yourself.")
            confirm(ask) {
                val okMsg = say("उघडतो. Send तू दाब.", "Opening. You press Send.")
                if (igOnly) {
                    if (finalMsg.isNotEmpty()) {
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, finalMsg)
                        i.setPackage("com.instagram.android")
                        launch(i, okMsg)
                    } else {
                        openPackage("com.instagram.android", "https://www.instagram.com")
                    }
                } else if (finalDigits.isNotEmpty()) {
                    val link = "https://wa.me/" + finalDigits +
                        (if (finalMsg.isNotEmpty()) "?text=" + Uri.encode(finalMsg) else "")
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    i.setPackage("com.whatsapp")
                    launch(i, okMsg)
                } else if (finalMsg.isNotEmpty()) {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, finalMsg)
                    i.setPackage("com.whatsapp")
                    launch(i, okMsg)
                } else {
                    openPackage("com.whatsapp", "https://web.whatsapp.com")
                }
            }
            return
        }

        // Torch
        if (has(t, "torch", "टॉर्च", "flashlight", "flash light", "फ्लॅशलाईट", "फ्लॅशलाइट")) {
            val off = has(t, "बंद", "off", "नको")
            setTorch(!off)
            return
        }

        // Battery
        if (has(t, "battery", "बॅटरी") && has(t, "किती", "status", "level", "सांग", "%", "how much", "percent")) {
            val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            if (pct >= 0) {
                show(say("Battery " + pct + " टक्के आहे.", "Battery is " + pct + " percent."), true)
            } else {
                show(say("Battery कळत नाही.", "Could not read battery."), false)
            }
            return
        }

        // Time and date
        if (has(
                t, "किती वाजले", "वाजले किती", "what time", "current time", "time now",
                "आजची तारीख", "आज तारीख", "today's date", "what is the date", "date today",
                "आज कोणता दिवस", "what day"
            )
        ) {
            val now = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
            show(now, true)
            return
        }

        // Alarm
        if (has(t, "alarm", "अलार्म")) {
            val tm = parseTime(t)
            if (tm != null) {
                val label = String.format(Locale.ENGLISH, "%02d:%02d", tm[0], tm[1])
                confirm(say("Alarm " + label + " ला लावू का?", "Set alarm for " + label + "?")) {
                    setAlarm(tm[0], tm[1])
                }
                return
            } else if (has(t, "लाव", "set", "ठेव")) {
                show(say("वेळ सांग. उदा: सकाळी 6:30 वाजता alarm लाव", "Tell me the time. Example: alarm at 6:30 am"), true)
                return
            }
        }

        // Timer
        if (has(t, "timer", "टायमर")) {
            val n = Regex("([0-9]+)").find(t)
            if (n != null) {
                val num = n.groupValues[1].toInt()
                val secs = if (has(t, "तास", "hour")) num * 3600
                else if (has(t, "सेकंद", "second")) num
                else num * 60
                confirm(say("Timer " + num + " साठी सुरू करू का?", "Start timer for " + num + "?")) {
                    setTimer(secs)
                }
                return
            }
        }

        // Call (dialer only)
        val numMatch = Regex("\\+?[0-9][0-9 \\-]{5,}[0-9]").find(t)
        if (t.length <= 60 && has(t, "call", "कॉल", "फोन कर", "dial", "डायल") &&
            (numMatch != null || has(t, "कर", "लाव", "make", "place", "dial", "डायल"))
        ) {
            if (numMatch != null) {
                val number = numMatch.value.replace(" ", "").replace("-", "")
                confirm(say("Dialer मध्ये हा नंबर उघडू का? " + number, "Open this number in the dialer? " + number)) {
                    launch(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))),
                        say("Dialer उघडला. हिरवं बटण तू दाब.", "Dialer opened. You press the green button.")
                    )
                }
            } else {
                confirm(say("नंबर सांगितला नाही. Dialer उघडू का?", "No number given. Open the dialer?")) {
                    launch(Intent(Intent.ACTION_DIAL), say("Dialer उघडला.", "Dialer opened."))
                }
            }
            return
        }

        // Open apps and settings
        if (has(t, "उघड", "open", "ओपन", "चालू", "सुरू", "बंद")) {
            if (has(t, "youtube", "यूट्यूब", "युट्युब", "यु ट्यूब")) {
                openPackage("com.google.android.youtube", "https://www.youtube.com")
                return
            }
            if (has(t, "whatsapp", "व्हॉट्सअ", "व्हाट्सअ", "व्हॉट्स अ")) {
                openPackage("com.whatsapp", "https://web.whatsapp.com")
                return
            }
            if (has(t, "chrome", "क्रोम")) {
                openPackage("com.android.chrome", "https://www.google.com")
                return
            }
            if (has(t, "camera", "कॅमेरा", "कैमरा")) {
                launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), say("Camera उघडतो...", "Opening camera..."))
                return
            }
            if (has(t, "gallery", "गॅलरी", "गैलरी")) {
                launch(
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
                    say("Gallery उघडतो...", "Opening gallery...")
                )
                return
            }
            if (has(t, "calculator", "कॅल्क्युलेटर", "कॅलक्युलेटर")) {
                launch(
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR),
                    say("Calculator उघडतो...", "Opening calculator...")
                )
                return
            }
            if (has(t, "wifi", "wi-fi", "वायफाय", "वाय-फाय")) {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
                return
            }
            if (has(t, "bluetooth", "ब्लूटूथ")) {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                return
            }
            if (has(t, "airplane", "एअरप्लेन", "विमान")) {
                openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                return
            }
            if (has(t, "battery saver", "बॅटरी सेव्हर")) {
                openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                return
            }
            if (has(t, "volume", "sound", "आवाज")) {
                openSettings(Settings.ACTION_SOUND_SETTINGS)
                return
            }
            if (has(t, "brightness", "display", "ब्राइटनेस", "dark mode")) {
                openSettings(Settings.ACTION_DISPLAY_SETTINGS)
                return
            }
            if (has(t, "settings", "सेटिंग")) {
                openSettings(Settings.ACTION_SETTINGS)
                return
            }
        }

        // Delete all my data
        if (has(t, "माझी माहिती", "delete my data", "delete all data") &&
            has(t, "delete", "डिलीट", "पुसून", "काढून")
        ) {
            confirm(
                say(
                    "तुझ्या सगळ्या notes, गप्पा आणि key फोनमधून पुसू का?",
                    "Delete all your notes, chats and the key from this phone?"
                )
            ) {
                prefs.edit().clear().apply()
                history.clear()
                lastAnswer = ""
                chatLog.setLength(0)
                chatView.text = ""
                show(say("सगळी माहिती पुसली. Key पुन्हा टाक.", "All data deleted. Please save the key again."), false)
            }
            return
        }

        // Notes: quiz from my notes
        if (has(t, "notes मधून", "नोट्स मधून", "from my notes")) {
            val notes = prefs.getString("notes", "") ?: ""
            if (notes.isEmpty()) {
                show(say("Notes अजून नाहीत.", "No notes yet."), true)
            } else {
                askGemini(
                    "Ask me 5 questions, one by one in a list, from these notes of mine. Notes:\n" + notes,
                    false,
                    true
                )
            }
            return
        }

        // Notes: delete
        if (has(t, "delete", "डिलीट", "पुसून", "clear") && has(t, "notes", "नोट")) {
            confirm(say("सगळ्या notes पुसू का?", "Delete all notes?")) {
                prefs.edit().remove("notes").apply()
                show(say("Notes पुसल्या.", "Notes deleted."), false)
            }
            return
        }

        // Notes: list
        if (has(t, "माझ्या नोट", "माझे नोट", "my notes", "my note", "माझ्या notes")) {
            val notes = prefs.getString("notes", "") ?: ""
            if (notes.isEmpty()) {
                show(say("Notes अजून नाहीत.", "No notes yet."), true)
            } else {
                show(notes, true)
            }
            return
        }

        // Notes: save
        val saveNoteCmd = has(t, "नोट कर", "note down", "note that", "save note", "नोट सेव") ||
            t.startsWith("remember ") || t.startsWith("लक्षात ठेव") ||
            t.endsWith("लक्षात ठेव") || t.endsWith("लक्षात ठेवा")
        if (saveNoteCmd) {
            val note = raw.replace(
                Regex("लक्षात ठेवा|लक्षात ठेव|remember( that)?|नोट कर|note down|note that|save note|नोट सेव", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            if (note.isEmpty()) {
                show(say("काय लक्षात ठेवू?", "What should I remember?"), true)
            } else {
                saveNote(note)
                show(say("नोट सेव केली: ", "Note saved: ") + note, true)
            }
            return
        }

        // Nursing study tools
        val tail = " Student request: " + raw + ". If no topic is named, use the last topic from our chat."
        if (has(t, "mcq", "एमसीक्यू")) {
            askGemini("Give 5 MCQs, each with 4 options, and write the correct answers at the end." + tail, false, true)
            return
        }
        if (has(t, "viva", "व्हायवा")) {
            askGemini("Give 5 viva questions with short model answers." + tail, false, true)
            return
        }
        if (has(t, "care plan", "केअर प्लॅन")) {
            askGemini(
                "Make a simple nursing care plan with these parts: assessment, nursing diagnosis, goal, interventions, evaluation." + tail,
                false,
                true
            )
            return
        }
        if (has(t, "drug", "ड्रग", "औषध") && has(t, "समजाव", "explain", "सांग", "बद्दल", "information", "माहिती")) {
            askGemini(
                "Explain this drug simply: uses, common side effects, nursing responsibilities. Do not give exact doses." + tail,
                false,
                true
            )
            return
        }
        if (has(t, "revision", "उजळणी")) {
            askGemini("Give 10 short revision points." + tail, false, true)
            return
        }
        if (has(t, "study plan", "अभ्यास योजना", "अभ्यासाचा प्लॅन", "अभ्यासाची योजना")) {
            askGemini(
                "Make a simple study plan for today for a first-year GNM nursing student. Ask which subjects are weak if you do not know.",
                false,
                true
            )
            return
        }

        // Everything else goes to Gemini
        val search = needsSearch(t)
        askGemini(raw, search, studyMode && !search)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
