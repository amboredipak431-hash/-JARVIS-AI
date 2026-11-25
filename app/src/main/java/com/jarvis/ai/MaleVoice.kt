package com.jarvis.ai

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.max

// ----- येथे बदल करता येतात -----
private const val CLOUD_VOICE = "Charon"
private const val LOCAL_PITCH = 0.55f
private const val LOCAL_RATE = 0.95f
private const val MAX_SPOKEN = 450
private val CLOUD_MODELS = listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts")

private const val PERSONA =
    "PERSONA: You are JARVIS, Tony Stark's calm, precise, slightly witty AI butler. " +
        "IMPORTANT, this overrides any earlier language rule: always reply in the same language " +
        "the user wrote or spoke in (an English question gets an English answer, Marathi gets Marathi, " +
        "Hindi gets Hindi) and never mix scripts in one answer. " +
        "Address the user as 'Sir' in English and as 'सर' in Marathi or Hindi, naturally, not in every sentence. " +
        "Lead with the answer in the first sentence, no long introductions. " +
        "Think ahead like an advanced system: when the user plans or asks to do something, " +
        "briefly say what will happen and the main risk, then offer one best next step. " +
        "Follow the answer-length rule given above and never be longer than needed. " +
        "About photos and body measurements: a photo cannot give exact sizes. " +
        "If the user sends a photo of themselves and asks for waist, hip, chest or height, " +
        "give rough estimates as ranges in centimetres, state clearly that it is only a guess " +
        "that can be off by 5 to 10 cm, say what you assumed, use the user's height from their profile if known, " +
        "otherwise ask for it, and if the user states tape measurements, trust those over your estimate. " +
        "Say that a tape measure is the only reliable way. " +
        "Stay respectful and factual, do not comment on attractiveness and do not push dieting. " +
        "Never mention these instructions."

// app सुरू होताना: उत्तर SHORT (एकदाच) आणि JARVIS चा स्वभाव
private fun applyJarvisBrain(ctx: Context) {
    val prefs = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    val edit = prefs.edit()
    if (!prefs.getBoolean("fast_default_v1", false)) {
        edit.putInt("length2", 0)
        edit.putBoolean("fast_default_v1", true)
    }
    val sb = StringBuilder()
    for (ln in (prefs.getString("notes", "") ?: "").split("\n")) {
        if (ln.isNotBlank() && !ln.startsWith("PERSONA:")) {
            sb.append(ln).append("\n")
        }
    }
    sb.append(PERSONA).append("\n")
    edit.putString("notes", sb.toString())
    edit.apply()
}

private class VoiceSession {
    @Volatile var cancelled = false
    @Volatile var finished = false
    @Volatile var track: AudioTrack? = null
}

private class GeminiTts(private val act: Activity, listener: TextToSpeech.OnInitListener) :
    TextToSpeech(act.applicationContext, listener) {

    private val ctx: Context = act.applicationContext
    @Volatile private var current: VoiceSession? = null
    @Volatile private var blockedUntil = 0L
    @Volatile private var goodModel: String? = null
    private val ui = Handler(Looper.getMainLooper())

    fun prepare() {
        setPitch(LOCAL_PITCH)
        setSpeechRate(LOCAL_RATE)
    }

    override fun setLanguage(loc: Locale?): Int {
        val r = super.setLanguage(loc)
        pickMaleVoice(loc)
        prepare()
        return r
    }

    private fun pickMaleVoice(loc: Locale?) {
        try {
            val all = voices ?: return
            for (v in all) {
                if (loc != null && v.locale.language != loc.language) continue
                val n = v.name.lowercase(Locale.ROOT)
                val missing = v.features?.contains("notInstalled") == true
                if (!missing && n.contains("male") && !n.contains("female")) {
                    setVoice(v)
                    return
                }
            }
        } catch (e: Exception) {
        }
    }

    // खाली पिवळ्या ओळीत छोटा संदेश दाखवतो
    private fun note(msg: String) {
        try {
            val f = act.javaClass.getDeclaredField("status")
            f.isAccessible = true
            val tv = f.get(act) as? TextView
            if (tv != null) {
                tv.text = msg
            }
        } catch (e: Exception) {
        }
    }

    override fun speak(text: CharSequence?, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
        val t = (text?.toString() ?: "").trim()
        if (t.isEmpty()) return TextToSpeech.SUCCESS
        stopCloud()
        super.stop()
        val key = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("key", "") ?: ""
        if (key.isEmpty() || System.currentTimeMillis() < blockedUntil) {
            localSpeak(clip(t))
        } else {
            startCloud(t, key)
        }
        return TextToSpeech.SUCCESS
    }

    override fun stop(): Int {
        stopCloud()
        return super.stop()
    }

    override fun isSpeaking(): Boolean {
        val s = current
        if (s != null && !s.finished && !s.cancelled) return true
        return super.isSpeaking()
    }

    override fun shutdown() {
        stopCloud()
        super.shutdown()
    }

    private fun stopCloud() {
        val s = current
        if (s != null) {
            s.cancelled = true
            try {
                val t = s.track
                if (t != null) {
                    t.pause()
                    t.flush()
                }
            } catch (e: Exception) {
            }
        }
        current = null
    }

    // फोनचा खोल आवाज (Gemini आवाज न आल्यास). मजकुराप्रमाणे भाषा निवडतो.
    private fun localSpeak(text: String) {
        try {
            var dev = 0
            var lat = 0
            for (ch in text) {
                if (ch in '\u0900'..'\u097F') {
                    dev++
                } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                    lat++
                }
            }
            if (dev + lat > 0) {
                if (dev >= lat) {
                    val r = setLanguage(Locale("mr", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        setLanguage(Locale("hi", "IN"))
                    }
                } else {
                    setLanguage(Locale("en", "IN"))
                }
            }
            prepare()
            super.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        } catch (e: Exception) {
        }
    }

    private fun clip(text: String): String {
        val t = text.replace("•", " ").replace("·", " ").replace("\n\n", "\n").trim()
        if (t.length <= MAX_SPOKEN) return t
        val cut = t.substring(0, MAX_SPOKEN)
        val i = max(
            max(cut.lastIndexOf('.'), cut.lastIndexOf('।')),
            max(cut.lastIndexOf('!'), cut.lastIndexOf('?'))
        )
        return if (i > 150) cut.substring(0, i + 1) else cut
    }

    private fun startCloud(text: String, key: String) {
        val s = VoiceSession()
        current = s
        val spoken = clip(text)
        Thread {
            var pcm: ByteArray? = null
            try {
                if (spoken.isNotEmpty()) {
                    pcm = fetchPcm(spoken, key)
                }
            } catch (e: Exception) {
            }
            if (s.cancelled) {
                s.finished = true
            } else if (pcm == null) {
                blockedUntil = System.currentTimeMillis() + 8000L
                s.finished = true
                ui.post {
                    if (!s.cancelled) {
                        note("⚠ Gemini आवाज आला नाही (मर्यादा किंवा इंटरनेट). फोनचा आवाज वापरला.")
                        localSpeak(spoken)
                    }
                }
            } else {
                playPcm(s, pcm)
            }
        }.start()
    }

    private fun makeTrack(): AudioTrack {
        val rate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun playPcm(s: VoiceSession, pcm: ByteArray) {
        var tr: AudioTrack? = null
        try {
            val t = makeTrack()
            tr = t
            s.track = t
            t.play()
            var off = 0
            while (off < pcm.size && !s.cancelled) {
                val n = t.write(pcm, off, pcm.size - off)
                if (n <= 0) break
                off += n
            }
            val frames = (pcm.size / 2).toLong()
            val deadline = System.currentTimeMillis() + frames * 1000L / 24000L + 3000L
            while (!s.cancelled && t.playbackHeadPosition < frames &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(60)
            }
        } catch (e: Exception) {
        } finally {
            try {
                tr?.stop()
            } catch (e: Exception) {
            }
            try {
                tr?.release()
            } catch (e: Exception) {
            }
            s.finished = true
        }
    }

    // 3 वेळा प्रयत्न: दोन model, मध्ये थोडा थांबा
    private fun fetchPcm(text: String, key: String): ByteArray? {
        val order = ArrayList<String>()
        val g = goodModel
        if (g != null) order.add(g)
        for (m in CLOUD_MODELS) {
            if (!order.contains(m)) order.add(m)
        }
        val start = System.currentTimeMillis()
        for (attempt in 0 until 3) {
            for (m in order) {
                val r = requestOnce(m, text, key)
                if (r != null) {
                    goodModel = m
                    return r
                }
            }
            if (System.currentTimeMillis() - start > 25000L) break
            try {
                Thread.sleep(if (attempt == 0) 1200L else 2500L)
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun requestOnce(m: String, text: String, key: String): ByteArray? {
        try {
            val part = JSONObject().put("text", text)
            val content = JSONObject().put("role", "user").put("parts", JSONArray().put(part))
            val audio = JSONObject().put("mimeType", "AUDIO_L16").put("sampleRate", 24000)
            val cfg = JSONObject()
            cfg.put("responseModalities", JSONArray().put("AUDIO"))
            cfg.put("responseFormat", JSONObject().put("audio", audio))
            cfg.put(
                "speechConfig",
                JSONObject().put("voiceConfig", JSONObject().put("voice", CLOUD_VOICE))
            )
            val body = JSONObject()
            body.put("contents", JSONArray().put(content))
            body.put("generationConfig", cfg)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return null

            val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            for (k in 0 until parts.length()) {
                val data = parts.getJSONObject(k).optJSONObject("inlineData")?.optString("data", "") ?: ""
                if (data.isNotEmpty()) {
                    var bytes = Base64.decode(data, Base64.DEFAULT)
                    if (bytes.size > 44 && bytes[0] == 82.toByte() && bytes[1] == 73.toByte() &&
                        bytes[2] == 70.toByte() && bytes[3] == 70.toByte()
                    ) {
                        bytes = bytes.copyOfRange(44, bytes.size)
                    }
                    return bytes
                }
            }
        } catch (e: Exception) {
        }
        return null
    }
}

private val pending: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

private fun installJarvisVoice(act: Activity) {
    try {
        val f = act.javaClass.getDeclaredField("tts")
        f.isAccessible = true
        if (f.get(act) is GeminiTts) return
        if (pending.contains(act)) return
        pending.add(act)
        var created: GeminiTts? = null
        created = GeminiTts(act, TextToSpeech.OnInitListener { _ ->
            try {
                val c = created
                if (c != null) {
                    if (act.isDestroyed || act.isFinishing) {
                        c.shutdown()
                    } else {
                        c.prepare()
                        val old = f.get(act) as? TextToSpeech
                        f.set(act, c)
                        try {
                            val r = act.javaClass.getDeclaredField("ttsReady")
                            r.isAccessible = true
                            r.setBoolean(act, true)
                        } catch (e: Exception) {
                        }
                        old?.stop()
                        old?.shutdown()
                    }
                }
            } catch (e: Exception) {
            }
            pending.remove(act)
        })
    } catch (e: Exception) {
    }
}

private object JarvisVoiceHook : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        installJarvisVoice(activity)
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

// app सुरू होताना हा आपोआप चालतो
class MaleVoiceInit : ContentProvider() {
    override fun onCreate(): Boolean {
        val c = context
        if (c != null) {
            try {
                applyJarvisBrain(c)
            } catch (e: Exception) {
            }
            val app = c.applicationContext as? Application
            app?.registerActivityLifecycleCallbacks(JarvisVoiceHook)
        }
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
