package com.jarvis.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.ExifInterface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
    private var lengthMode = 2
    private var lastAnswer = ""
    private var pendingImage: String? = null
    private var cameraUri: Uri? = null
    private var chatId = ""
    private var chatTitle = ""
    private val msgs = ArrayList<Pair<String, String>>()
    private val history = ArrayList<Pair<String, String>>()
    private val speechCode = 101
    private val galleryCode = 102
    private val cameraCode = 103
    private val fence = "\u0060".repeat(3)

    private val appAliases = listOf(
        Triple(listOf("whatsapp", "व्हॉट्सअ", "व्हाट्सअ", "व्हॉट्स अ", "व्हाट्स अ"), listOf("com.whatsapp", "com.whatsapp.w4b"), "https://web.whatsapp.com"),
        Triple(listOf("instagram", "insta", "इन्स्टा", "इंस्टा"), listOf("com.instagram.android"), "https://www.instagram.com"),
        Triple(listOf("youtube", "यूट्यूब", "युट्युब", "यु ट्यूब"), listOf("com.google.android.youtube"), "https://www.youtube.com"),
        Triple(listOf("facebook", "फेसबुक"), listOf("com.facebook.katana"), "https://www.facebook.com"),
        Triple(listOf("telegram", "टेलिग्राम"), listOf("org.telegram.messenger"), "https://web.telegram.org"),
        Triple(listOf("snapchat", "स्नॅपचॅट"), listOf("com.snapchat.android"), "https://www.snapchat.com"),
        Triple(listOf("spotify", "स्पॉटिफाय"), listOf("com.spotify.music"), "https://open.spotify.com"),
        Triple(listOf("gmail", "जीमेल"), listOf("com.google.android.gm"), "https://mail.google.com"),
        Triple(listOf("google maps", "गुगल मॅप", "मॅप्स"), listOf("com.google.android.apps.maps"), "https://maps.google.com"),
        Triple(listOf("chrome", "क्रोम"), listOf("com.android.chrome"), "https://www.google.com"),
        Triple(listOf("perplexity", "पर्प्लेक्सिटी"), listOf("ai.perplexity.app.android"), "https://www.perplexity.ai"),
        Triple(listOf("chatgpt", "चॅटजीपीटी"), listOf("com.openai.chatgpt"), "https://chatgpt.com"),
        Triple(listOf("claude", "क्लॉड"), listOf("com.anthropic.claude"), "https://claude.ai")
    )

    private val blockedApp = listOf(
        "pay", "upi", "bank", "bhim", "paisa", "npci", "wallet", "yono",
        "mobikwik", "freecharge", "phonepe", "paytm", "hdfc", "icici", "kotak", "sbi"
    )

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

    // ---------- Chats (saved on the phone) ----------

    private fun chatIds(): MutableList<String> {
        val s = prefs.getString("chat_ids", "") ?: ""
        return if (s.isEmpty()) ArrayList() else ArrayList(s.split(","))
    }

    private fun saveChat() {
        if (msgs.isEmpty()) return
        try {
            val o = JSONObject()
            o.put("title", chatTitle)
            o.put("ts", chatId.toLong())
            val ma = JSONArray()
            for (m in msgs) {
                ma.put(JSONArray().put(m.first).put(m.second))
            }
            o.put("msgs", ma)
            val ha = JSONArray()
            for (h in history) {
                ha.put(JSONArray().put(h.first).put(h.second))
            }
            o.put("hist", ha)
            val ids = chatIds()
            if (!ids.contains(chatId)) ids.add(chatId)
            while (ids.size > 40) {
                val old = ids.removeAt(0)
                prefs.edit().remove("c_" + old).apply()
            }
            prefs.edit()
                .putString("c_" + chatId, o.toString())
                .putString("chat_ids", ids.joinToString(","))
                .apply()
        } catch (e: Exception) {
        }
    }

    private fun migrateOld() {
        try {
            if (prefs.getBoolean("migrated", false)) return
            val old = (prefs.getString("chat", "") ?: "").trim()
            if (old.isNotEmpty()) {
                val id = (System.currentTimeMillis() - 1000).toString()
                val o = JSONObject()
                o.put("title", "जुन्या गप्पा")
                o.put("ts", id.toLong())
                o.put("msgs", JSONArray().put(JSONArray().put("JARVIS").put(old)))
                o.put("hist", JSONArray())
                val ids = chatIds()
                ids.add(id)
                prefs.edit()
                    .putString("c_" + id, o.toString())
                    .putString("chat_ids", ids.joinToString(","))
                    .apply()
            }
            prefs.edit().putBoolean("migrated", true).remove("chat").remove("history").apply()
        } catch (e: Exception) {
        }
    }

    private fun addMsg(who: String, text: String) {
        msgs.add(Pair(who, text))
        if (who == "You" && chatTitle.isEmpty()) {
            chatTitle = text.replace("\n", " ").take(40)
        }
        saveChat()
        render()
    }

    private fun render() {
        val sb = SpannableStringBuilder()
        val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        for (m in msgs) {
            val isUser = m.first == "You"
            var s0 = sb.length
            sb.append(if (isUser) "You" else "JARVIS").append("\n")
            sb.setSpan(StyleSpan(Typeface.BOLD), s0, sb.length, flag)
            sb.setSpan(ForegroundColorSpan(Color.GRAY), s0, sb.length, flag)
            var inCode = false
            for (line in m.second.split("\n")) {
                val tl = line.trim()
                if (tl.startsWith(fence)) {
                    inCode = !inCode
                    continue
                }
                s0 = sb.length
                if (inCode) {
                    sb.append(line).append("\n")
                    sb.setSpan(TypefaceSpan("monospace"), s0, sb.length, flag)
                    sb.setSpan(BackgroundColorSpan(Color.rgb(34, 34, 34)), s0, sb.length, flag)
                    sb.setSpan(ForegroundColorSpan(Color.rgb(190, 255, 190)), s0, sb.length, flag)
                    continue
                }
                var txt = line
                var bold = false
                if (!isUser) {
                    if (tl.startsWith("#")) {
                        txt = tl.trimStart('#').trim()
                        bold = true
                    } else if (tl.length > 4 && tl.startsWith("**") && tl.endsWith("**")) {
                        bold = true
                    } else if (tl.startsWith("* ") || tl.startsWith("- ")) {
                        txt = "• " + tl.substring(2)
                    }
                    txt = txt.replace("**", "").replace("*", "")
                }
                sb.append(txt).append("\n")
                sb.setSpan(
                    ForegroundColorSpan(if (isUser) Color.LTGRAY else Color.WHITE),
                    s0, sb.length, flag
                )
                if (bold) {
                    sb.setSpan(StyleSpan(Typeface.BOLD), s0, sb.length, flag)
                }
            }
            sb.append("\n")
        }
        chatView.text = sb
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun newChat() {
        saveChat()
        chatId = System.currentTimeMillis().toString()
        chatTitle = ""
        msgs.clear()
        history.clear()
        pendingImage = null
        render()
        status.text = say("नवीन chat सुरू.", "New chat started.")
    }

    private fun openChat(id: String) {
        try {
            saveChat()
            val s = prefs.getString("c_" + id, "") ?: ""
            if (s.isEmpty()) return
            val o = JSONObject(s)
            chatId = id
            chatTitle = o.optString("title", "")
            msgs.clear()
            history.clear()
            val ma = o.getJSONArray("msgs")
            for (k in 0 until ma.length()) {
                val p = ma.getJSONArray(k)
                msgs.add(Pair(p.getString(0), p.getString(1)))
            }
            val ha = o.optJSONArray("hist")
            if (ha != null) {
                for (k in 0 until ha.length()) {
                    val p = ha.getJSONArray(k)
                    history.add(Pair(p.getString(0), p.getString(1)))
                }
            }
            pendingImage = null
            render()
            status.text = ""
        } catch (e: Exception) {
            status.text = say("Chat उघडता आला नाही.", "Could not open chat.")
        }
    }

    private fun deleteChat(id: String) {
        val ids = chatIds()
        ids.remove(id)
        prefs.edit().remove("c_" + id).putString("chat_ids", ids.joinToString(",")).apply()
        if (id == chatId) {
            chatId = System.currentTimeMillis().toString()
            chatTitle = ""
            msgs.clear()
            history.clear()
            render()
        }
        status.text = say("Chat पुसला.", "Chat deleted.")
    }

    private fun deleteAllChats() {
        for (id in chatIds()) {
            prefs.edit().remove("c_" + id).apply()
        }
        prefs.edit().remove("chat_ids").apply()
        chatId = System.currentTimeMillis().toString()
        chatTitle = ""
        msgs.clear()
        history.clear()
        render()
        status.text = say("सगळा history पुसला.", "All history deleted.")
    }

    private fun showHistory() {
        val ids = chatIds()
        val labels = ArrayList<String>()
        val shownIds = ArrayList<String>()
        for (k in ids.indices.reversed()) {
            val id = ids[k]
            val s = prefs.getString("c_" + id, "") ?: ""
            if (s.isEmpty()) continue
            try {
                val o = JSONObject(s)
                val d = SimpleDateFormat("d MMM, hh:mm a", Locale.ENGLISH).format(Date(id.toLong()))
                labels.add(o.optString("title", "Chat") + "\n" + d)
                shownIds.add(id)
            } catch (e: Exception) {
            }
        }
        if (labels.isEmpty()) {
            status.text = say("History रिकामी आहे.", "History is empty.")
            return
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(say("History (पुसायला दाबून धर)", "History (long press to delete)"))
            .setItems(labels.toTypedArray()) { _, which -> openChat(shownIds[which]) }
            .setNegativeButton("CLOSE", null)
            .setNeutralButton("DELETE ALL") { _, _ ->
                confirm(say("सगळा history पुसू का?", "Delete all history?")) { deleteAllChats() }
            }
            .create()
        dlg.show()
        dlg.listView.setOnItemLongClickListener { _, _, pos, _ ->
            val id = shownIds[pos]
            dlg.dismiss()
            confirm(say("हा chat पुसू का?", "Delete this chat?")) { deleteChat(id) }
            true
        }
    }

    private fun copyLast() {
        if (lastAnswer.isEmpty()) {
            status.text = say("Copy करायला काही नाही.", "Nothing to copy.")
            return
        }
        var textToCopy = lastAnswer
        val blocks = Regex(fence + "[a-zA-Z0-9+#]*\\n([\\s\\S]*?)" + fence).findAll(lastAnswer).toList()
        if (blocks.isNotEmpty()) {
            textToCopy = blocks.last().groupValues[1].trimEnd()
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JARVIS", textToCopy))
        status.text = say("Copy केलं.", "Copied.")
    }

    // ---------- Screen ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        marathi = prefs.getBoolean("marathi", true)
        lengthMode = prefs.getInt("length2", 2)
        migrateOld()
        chatId = System.currentTimeMillis().toString()

        tts = TextToSpeech(this) { s ->
            ttsReady = (s == TextToSpeech.SUCCESS)
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 50, 30, 30)
        layout.setBackgroundColor(Color.BLACK)

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

        val newChatButton = Button(this)
        newChatButton.text = "NEW CHAT"
        val historyButton = Button(this)
        historyButton.text = "HISTORY"
        val copyButton = Button(this)
        copyButton.text = "COPY"

        val keyInput = EditText(this)
        keyInput.hint = "Paste Gemini key (only once)"
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val saveKey = Button(this)
        saveKey.text = "SAVE KEY"
        saveKey.textSize = 12f

        val keyRow = LinearLayout(this)
        keyRow.orientation = LinearLayout.HORIZONTAL
        keyRow.addView(keyInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        keyRow.addView(saveKey, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        chatView = TextView(this)
        chatView.textSize = 17f
        chatView.setTextColor(Color.WHITE)
        chatView.setPadding(10, 20, 10, 20)
        chatView.setTextIsSelectable(true)

        scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.BLACK)
        scrollView.addView(chatView)

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

        val camButton = Button(this)
        camButton.text = "📷"
        camButton.textSize = 18f
        val galButton = Button(this)
        galButton.text = "🖼"
        galButton.textSize = 18f

        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(camButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        inputRow.addView(galButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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

        newChatButton.setOnClickListener { newChat() }
        historyButton.setOnClickListener { showHistory() }
        copyButton.setOnClickListener { copyLast() }
        camButton.setOnClickListener { openCamera() }
        galButton.setOnClickListener { openGallery() }

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
            if (q.isEmpty() && pendingImage == null) {
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
        layout.addView(makeRow(newChatButton, historyButton, copyButton))
        layout.addView(keyRow)
        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        layout.addView(status)
        layout.addView(inputRow)
        layout.addView(makeRow(send, mic, stop))

        setContentView(layout)
        render()
    }

    private fun setLang(m: Boolean) {
        marathi = m
        prefs.edit().putBoolean("marathi", marathi).apply()
        langButton.text = langLabel()
        status.text = say("भाषा: मराठी", "Language: English")
    }

    private fun setLength(n: Int) {
        lengthMode = n
        prefs.edit().putInt("length2", n).apply()
        lengthButton.text = lengthLabel()
        status.text = lengthLabel()
    }

    // ---------- Voice ----------

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
        if (requestCode == galleryCode && resultCode == Activity.RESULT_OK) {
            val u = data?.data
            if (u != null) {
                loadImage(u)
            }
        }
        if (requestCode == cameraCode) {
            val u = cameraUri
            if (u != null) {
                if (resultCode == Activity.RESULT_OK) {
                    loadImage(u)
                } else {
                    try {
                        contentResolver.delete(u, null, null)
                    } catch (e: Exception) {
                    }
                }
            }
            cameraUri = null
        }
    }

    private fun speakable(text: String): String {
        val out = StringBuilder()
        var inCode = false
        for (line in text.split("\n")) {
            val tl = line.trim()
            if (tl.startsWith(fence)) {
                if (!inCode) {
                    out.append(say("कोड स्क्रीनवर आहे.", "The code is on the screen.")).append("\n")
                }
                inCode = !inCode
                continue
            }
            if (inCode) continue
            if (tl.startsWith("Sources:") || tl.startsWith("स्रोत:")) continue
            out.append(line.replace("**", "").replace("*", "").replace("#", "")).append("\n")
        }
        return out.toString()
    }

    private fun speak(text: String) {
        val t = tts
        if (t == null || !ttsReady || !isOn()) return
        val wanted = if (marathi) Locale("mr", "IN") else Locale("en", "IN")
        val r = t.setLanguage(wanted)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("hi", "IN"))
        }
        t.speak(text.take(3000), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun show(text: String, speakIt: Boolean) {
        if (speakIt) {
            lastAnswer = text
            status.text = ""
            addMsg("JARVIS", text)
            speak(speakable(text))
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

    // ---------- Photos ----------

    private fun openGallery() {
        if (!isOn()) {
            status.text = "JARVIS is OFF. Tap MASTER SWITCH to turn it ON."
            return
        }
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            startActivityForResult(Intent.createChooser(i, "Choose photo"), galleryCode)
        } catch (e: Exception) {
            status.text = "Gallery error: " + e.message
        }
    }

    private fun openCamera() {
        if (!isOn()) {
            status.text = "JARVIS is OFF. Tap MASTER SWITCH to turn it ON."
            return
        }
        try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "jarvis_" + System.currentTimeMillis() + ".jpg")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                status.text = say("Camera सुरू होत नाही.", "Could not start the camera.")
                return
            }
            cameraUri = uri
            val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(i, cameraCode)
        } catch (e: Exception) {
            status.text = "Camera error: " + e.message
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (decoded == null) {
                status.text = say("फोटो वाचता आला नाही.", "Could not read the photo.")
                return
            }
            var bmp: Bitmap = decoded
            var rotation = 0f
            try {
                contentResolver.openInputStream(uri)?.use {
                    val ex = ExifInterface(it)
                    val o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    rotation = when (o) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            } catch (e: Exception) {
            }
            if (rotation != 0f) {
                val m = Matrix()
                m.postRotate(rotation)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            pendingImage = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            status.text = say(
                "📷 फोटो तयार आहे. प्रश्न लिही किंवा बोल, मग SEND दाब. प्रश्न नसेल तर थेट SEND दाब.",
                "📷 Photo ready. Type or speak your question, then tap SEND. Or tap SEND directly."
            )
        } catch (e: Exception) {
            status.text = "Photo error: " + e.message
        }
    }

    // ---------- Phone actions ----------

    private fun launch(i: Intent, okMsg: String) {
        try {
            startActivity(i)
            show(okMsg, false)
        } catch (e: Exception) {
            show("Could not do this on your phone: " + e.message, false)
        }
    }

    private fun isBlockedApp(label: String, pkg: String): Boolean {
        val l = label.lowercase(Locale.ROOT)
        val p = pkg.lowercase(Locale.ROOT)
        for (w in blockedApp) {
            if (l.contains(w) || p.contains(w)) return true
        }
        return false
    }

    private fun openKnown(pkgs: List<String>, fallbackUrl: String) {
        for (p in pkgs) {
            val li = packageManager.getLaunchIntentForPackage(p)
            if (li != null) {
                launch(li, say("उघडतो...", "Opening..."))
                return
            }
            val m = Intent(Intent.ACTION_MAIN)
            m.addCategory(Intent.CATEGORY_LAUNCHER)
            m.setPackage(p)
            m.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(m)
                show(say("उघडतो...", "Opening..."), false)
                return
            } catch (e: Exception) {
            }
        }
        launch(
            Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)),
            say("App सापडला नाही. Browser मध्ये उघडतो...", "App not found. Opening in browser...")
        )
    }

    private fun startWithPackages(base: Intent, pkgs: List<String>, okMsg: String) {
        for (p in pkgs) {
            val i = Intent(base)
            i.setPackage(p)
            try {
                startActivity(i)
                show(okMsg, false)
                return
            } catch (e: Exception) {
            }
        }
        show(say("तो app सापडला नाही.", "That app was not found."), false)
    }

    private fun openAppByName(t: String): Boolean {
        for (a in appAliases) {
            if (has(t, *a.first.toTypedArray())) {
                openKnown(a.second, a.third)
                return true
            }
        }
        try {
            val li = Intent(Intent.ACTION_MAIN)
            li.addCategory(Intent.CATEGORY_LAUNCHER)
            val list = packageManager.queryIntentActivities(li, 0)
            for (ri in list) {
                val label = ri.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
                val pkg = ri.activityInfo.packageName
                if (label.length >= 4 && t.contains(label)) {
                    if (isBlockedApp(label, pkg)) {
                        show(
                            say(
                                "पेमेंट आणि बँक apps ला JARVIS हात लावत नाही.",
                                "JARVIS never touches payment and bank apps."
                            ),
                            true
                        )
                        return true
                    }
                    val i = packageManager.getLaunchIntentForPackage(pkg)
                    if (i != null) {
                        launch(i, say("उघडतो...", "Opening..."))
                        return true
                    }
                }
            }
        } catch (e: Exception) {
        }
        return false
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

    // ---------- Questions to Gemini ----------

    private fun needsSearch(t: String): Boolean {
        if (has(
                t, "news", "बातम", "weather", "हवामान", "तापमान", "current affairs",
                "चालू घडामोडी", "score", "स्कोअर", "सामना", "latest", "ताज्या",
                "updates", "अपडेट", "निकाल", "result"
            )
        ) {
            return true
        }
        if (studyMode || t.length < 15) return false
        return has(
            t, "?", "काय", "कसा", "कशी", "कसे", "कसं", "कोण", "कधी", "कुठे", "कुठल", "किती",
            "what", "how", "why", "who", "when", "where", "which", "explain", "समजाव", "माहिती", "information"
        )
    }

    private fun isCoding(t: String): Boolean {
        return has(
            t, "code", "कोड", "program", "प्रोग्राम", "python", "पायथॉन", "javascript", "html", "css",
            "java", "kotlin", "c++", "sql", "script", "function", "फंक्शन", "bug", "debug",
            "algorithm", "coding", "कोडिंग", "react", "android studio", "website बनव", "वेबसाइट बनव"
        )
    }

    private fun systemText(study: Boolean, coding: Boolean): String {
        val lang = if (marathi) "Marathi" else "English"
        val today = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val notes = (prefs.getString("notes", "") ?: "").takeLast(3000)
        var s = "You are JARVIS, a calm, polite and friendly personal assistant. " +
            "Today is " + today + ". Reply in simple " + lang + " with short sentences and very simple words. " +
            "Answer only what the user asked. Do not bring up studies, nursing, exams or textbooks unless the user does. " +
            "Do not add medical warnings or disclaimers yourself. " +
            "For a simple greeting, just greet back in one short sentence. " +
            "You cannot control the phone yourself. " +
            "Background: the user is a first-year GNM nursing student in India. Use this only when the question is about studies."
        s += when (lengthMode) {
            0 -> " Keep the answer under 60 words and do not use headings."
            1 -> " Keep the answer around 150 words. Use a few short bullet points when helpful."
            else -> " Give a detailed, well organised answer like a research assistant. " +
                "First give the direct answer in 1 or 2 sentences. Then add sections with short headings " +
                "(lines starting with ## ) and bullet points (lines starting with - ) covering the key facts, " +
                "causes, examples and steps. End with a one-line summary. " +
                "Usually 250 to 450 words. For a greeting or a very small question keep it short."
        }
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
        if (coding) {
            s += " The user is asking about programming. You are an expert software engineer. " +
                "Write complete, correct, working code. Never leave parts out and never use placeholders. " +
                "Put all code inside markdown fenced code blocks with the language name. " +
                "Write code comments in English. After the code, explain how it works in simple " + lang +
                " in a few short points. The word limit does not apply to the code."
        }
        return s
    }

    private fun askGemini(question: String, search: Boolean, study: Boolean, image: String?, coding: Boolean) {
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
        val sys = systemText(study, coding)
        val models = if (coding) {
            listOf("gemini-flash-latest", "gemini-flash-lite-latest")
        } else {
            listOf("gemini-flash-lite-latest", "gemini-flash-latest")
        }
        val tries = ArrayList<Pair<String, Boolean>>()
        for (m in models) {
            tries.add(Pair(m, search))
        }
        if (search) {
            tries.add(Pair(models[0], false))
        }
        val past = ArrayList<Pair<String, String>>(history)
        val sentChatId = chatId

        Thread {
            var answer = ""
            var err = ""
            val sources = ArrayList<String>()
            for (tr in tries) {
                try {
                    sources.clear()
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
                    val userParts = JSONArray()
                    userParts.put(JSONObject().put("text", question))
                    if (image != null) {
                        userParts.put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject().put("mimeType", "image/jpeg").put("data", image)
                            )
                        )
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", userParts))

                    val body = JSONObject()
                    body.put("contents", contents)
                    body.put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    )
                    body.put("generationConfig", JSONObject().put("maxOutputTokens", 8192))
                    if (tr.second) {
                        body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                    }

                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + tr.first + ":generateContent")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-goog-api-key", key)
                    conn.connectTimeout = 20000
                    conn.readTimeout = 120000
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
                        val cand = cands.getJSONObject(0)
                        val content = cand.optJSONObject("content")
                        val rparts = content?.optJSONArray("parts")
                        val sb = StringBuilder()
                        if (rparts != null) {
                            for (k in 0 until rparts.length()) {
                                sb.append(rparts.getJSONObject(k).optString("text", ""))
                            }
                        }
                        val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
                        if (chunks != null) {
                            for (k in 0 until chunks.length()) {
                                val title = chunks.getJSONObject(k).optJSONObject("web")?.optString("title", "") ?: ""
                                if (title.isNotEmpty() && !sources.contains(title)) {
                                    sources.add(title)
                                }
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
            val finalAnswer = answer.trim()
            runOnUiThread {
                if (sentChatId != chatId) {
                    status.text = say("उत्तर आलं, पण तू chat बदलला.", "The answer came, but you changed the chat.")
                    return@runOnUiThread
                }
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
                    history.add(Pair(question.take(600), cleaned.take(6000)))
                    while (history.size > 6) {
                        history.removeAt(0)
                    }
                    var shown = cleaned
                    if (disclaimer) {
                        shown += "\n\n" + say(
                            "डोस आणि उपचार textbook किंवा शिक्षकांकडून तपासा.",
                            "Check doses and treatment with your textbook or teacher."
                        )
                    }
                    if (sources.isNotEmpty()) {
                        shown += "\n\n" + say("स्रोत: ", "Sources: ") + sources.take(5).joinToString(", ")
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

    // ---------- Commands ----------

    private fun handle(raw: String) {
        if (!isOn()) {
            show("JARVIS is OFF. Tap MASTER SWITCH to turn it ON.", false)
            return
        }

        val img = pendingImage
        if (img != null) {
            pendingImage = null
            input.setText("")
            val q = raw.trim()
            addMsg("You", "📷 " + (if (q.isEmpty()) say("फोटो", "Photo") else q))
            val ask = if (q.isEmpty()) {
                "Explain this photo in detail. If there is any text in it, read it and explain it."
            } else {
                q
            }
            askGemini(ask, false, studyMode, img, false)
            return
        }

        val t = normDigits(raw).lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return

        addMsg("You", raw.trim())
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
                speak(speakable(lastAnswer))
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
                val waPkgs = listOf("com.whatsapp", "com.whatsapp.w4b")
                if (igOnly) {
                    if (finalMsg.isNotEmpty()) {
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, finalMsg)
                        startWithPackages(i, listOf("com.instagram.android"), okMsg)
                    } else {
                        openKnown(listOf("com.instagram.android"), "https://www.instagram.com")
                    }
                } else if (finalDigits.isNotEmpty()) {
                    val link = "https://wa.me/" + finalDigits +
                        (if (finalMsg.isNotEmpty()) "?text=" + Uri.encode(finalMsg) else "")
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    startWithPackages(i, waPkgs, okMsg)
                } else if (finalMsg.isNotEmpty()) {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, finalMsg)
                    startWithPackages(i, waPkgs, okMsg)
                } else {
                    openKnown(waPkgs, "https://web.whatsapp.com")
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
            if (t.length <= 50 && openAppByName(t)) {
                return
            }
        }

        // Delete all my data
        if (has(t, "माझी माहिती", "delete my data", "delete all data") &&
            has(t, "delete", "डिलीट", "पुसून", "काढून")
        ) {
            confirm(
                say(
                    "तुझे सगळे notes, chats आणि key फोनमधून पुसू का?",
                    "Delete all your notes, chats and the key from this phone?"
                )
            ) {
                prefs.edit().clear().apply()
                history.clear()
                msgs.clear()
                lastAnswer = ""
                pendingImage = null
                chatId = System.currentTimeMillis().toString()
                chatTitle = ""
                render()
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
                    true,
                    null,
                    false
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
            askGemini("Give 5 MCQs, each with 4 options, and write the correct answers at the end." + tail, false, true, null, false)
            return
        }
        if (has(t, "viva", "व्हायवा")) {
            askGemini("Give 5 viva questions with short model answers." + tail, false, true, null, false)
            return
        }
        if (has(t, "care plan", "केअर प्लॅन")) {
            askGemini(
                "Make a simple nursing care plan with these parts: assessment, nursing diagnosis, goal, interventions, evaluation." + tail,
                false,
                true,
                null,
                false
            )
            return
        }
        if (has(t, "drug", "ड्रग", "औषध") && has(t, "समजाव", "explain", "सांग", "बद्दल", "information", "माहिती")) {
            askGemini(
                "Explain this drug simply: uses, common side effects, nursing responsibilities. Do not give exact doses." + tail,
                false,
                true,
                null,
                false
            )
            return
        }
        if (has(t, "revision", "उजळणी")) {
            askGemini("Give 10 short revision points." + tail, false, true, null, false)
            return
        }
        if (has(t, "study plan", "अभ्यास योजना", "अभ्यासाचा प्लॅन", "अभ्यासाची योजना")) {
            askGemini(
                "Make a simple study plan for today for a first-year GNM nursing student. Ask which subjects are weak if you do not know.",
                false,
                true,
                null,
                false
            )
            return
        }

        // Everything else goes to Gemini
        val coding = isCoding(t)
        val search = !coding && needsSearch(t)
        askGemini(raw, search, studyMode && !search && !coding, null, coding)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
