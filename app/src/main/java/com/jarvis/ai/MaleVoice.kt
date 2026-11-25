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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.max

// ----- येथे बदल करता येतात -----
private const val CLOUD_VOICE = "Charon"
private const val LOCAL_PITCH = 0.72f
private const val LOCAL_RATE = 0.95f
private const val MAX_SPOKEN = 800
private val CLOUD_MODELS = listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts")

private const val PERSONA =
    "PERSONA: You are JARVIS, Tony Stark's calm, precise, slightly witty AI butler. " +
        "Address the user as 'सर' (Sir). Lead with the answer in the first sentence, no long introductions. " +
        "Think ahead like an advanced system: when the user plans or asks to do something, " +
        "briefly say what will happen and the main risk, for example 'सर, असं केल्यास ... होईल.' " +
        "Then offer one best next step. " +
        "Follow the answer-length rule given above and never be longer than needed. " +
        "If the user sends a photo of themselves and asks for body measurements (waist, hip, chest, height), " +
        "give rough estimates in centimetres as ranges (about plus or minus 5 cm), say what you assumed, " +
        "use the user's height from their profile if known, otherwise ask for it, " +
        "and say that a tape measure is more accurate. " +
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
    @Volatile var fallback = false
    @Volatile var gotAny = false
    @Volatile var track: AudioTrack? = null
}

private class GeminiTts(private val ctx: Context, listener: TextToSpeech.OnInitListener) :
    TextToSpeech(ctx, listener) {

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

    override fun speak(text: CharSequence?, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
        val t = (text?.toString() ?: "").trim()
        if (t.isEmpty()) return TextToSpeech.SUCCESS
        stopCloud()
        val key = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("key", "") ?: ""
        if (key.isEmpty() || System.currentTimeMillis() < blockedUntil) {
            return super.speak(t, queueMode, params, utteranceId)
        }
        super.stop()
        startCloud(t, key)
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

    private fun localSpeak(text: String) {
        try {
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
        return if (i > 200) cut.substring(0, i + 1) else cut
    }

    private fun splitChunks(text: String): List<String> {
        val sentences = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in text) {
            cur.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '।' || ch == '\n') {
                val s = cur.toString().trim()
                if (s.isNotEmpty()) sentences.add(s)
                cur.setLength(0)
            }
        }
        val rest = cur.toString().trim()
        if (rest.isNotEmpty()) sentences.add(rest)

        val out = ArrayList<String>()
        val sb = StringBuilder()
        var limit = 110
        for (p in sentences) {
            if (sb.isNotEmpty() && sb.length + p.length > limit) {
                out.add(sb.toString().trim())
                sb.setLength(0)
                limit = 280
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(p)
        }
        if (sb.isNotEmpty()) out.add(sb.toString().trim())
        return out
    }

    private fun startCloud(text: String, key: String) {
        val s = VoiceSession()
        current = s
        val spoken = clip(text)
        val chunks = splitChunks(spoken)
        val queue = LinkedBlockingQueue<ByteArray>()
        val end = ByteArray(0)

        Thread {
            try {
                for (c in chunks) {
                    if (s.cancelled) break
                    val pcm = fetchPcm(c, key)
                    if (pcm == null) {
                        if (!s.gotAny) {
                            s.fallback = true
                            blockedUntil = System.currentTimeMillis() + 45000L
                        }
                        break
                    }
                    s.gotAny = true
                    queue.put(pcm)
                }
            } catch (e: Exception) {
            }
            try {
                queue.put(end)
            } catch (e: Exception) {
            }
        }.start()

        Thread { play(s, queue, spoken) }.start()
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

    private fun play(s: VoiceSession, queue: LinkedBlockingQueue<ByteArray>, spoken: String) {
        var tr: AudioTrack? = null
        var frames = 0L
        try {
            while (!s.cancelled) {
                val pcm = queue.take()
                if (pcm.isEmpty()) break
                val cur = tr ?: makeTrack().also {
                    tr = it
                    s.track = it
                    it.play()
                }
                var off = 0
                while (off < pcm.size && !s.cancelled) {
                    val n = cur.write(pcm, off, pcm.size - off)
                    if (n <= 0) break
                    off += n
                }
                frames += (pcm.size / 2).toLong()
            }
            val done = tr
            if (done != null && !s.cancelled) {
                val deadline = System.currentTimeMillis() + frames * 1000L / 24000L + 3000L
                while (!s.cancelled && done.playbackHeadPosition < frames &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(60)
                }
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
            val fb = s.fallback && !s.cancelled
            s.finished = true
            if (fb) {
                ui.post { localSpeak(spoken) }
            }
        }
    }

    private fun fetchPcm(text: String, key: String): ByteArray? {
        val order = ArrayList<String>()
        val g = goodModel
        if (g != null) order.add(g)
        for (m in CLOUD_MODELS) {
            if (!order.contains(m)) order.add(m)
        }
        for (m in order) {
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
                conn.readTimeout = 25000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) continue

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
                        goodModel = m
                        return bytes
                    }
                }
            } catch (e: Exception) {
            }
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
        created = GeminiTts(act.applicationContext, TextToSpeech.OnInitListener { _ ->
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
