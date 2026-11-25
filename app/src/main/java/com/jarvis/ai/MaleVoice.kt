package com.jarvis.ai

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

// 1.0 = मूळ आवाज. कमी आकडा = जास्त जाड आवाज.
private const val VOICE_PITCH = 0.72f
private const val VOICE_RATE = 0.95f

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

private class MaleTts(ctx: Context, listener: TextToSpeech.OnInitListener) : TextToSpeech(ctx, listener) {

    fun prepare() {
        setPitch(VOICE_PITCH)
        setSpeechRate(VOICE_RATE)
    }

    override fun setLanguage(loc: Locale?): Int {
        val r = super.setLanguage(loc)
        pickMaleVoice(loc)
        prepare()
        return r
    }

    // मजकूर मराठी असेल तर मराठी आवाज, English असेल तर English आवाज
    override fun speak(text: CharSequence?, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
        val t = text?.toString() ?: ""
        var dev = 0
        var lat = 0
        for (ch in t) {
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
        return super.speak(text, queueMode, params, utteranceId)
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
}

private val pending: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

private fun installMaleVoice(act: Activity) {
    try {
        val f = act.javaClass.getDeclaredField("tts")
        f.isAccessible = true
        if (f.get(act) is MaleTts) return
        if (pending.contains(act)) return
        pending.add(act)
        var created: MaleTts? = null
        created = MaleTts(act.applicationContext, TextToSpeech.OnInitListener { _ ->
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

private object MaleVoiceHook : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        installMaleVoice(activity)
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
            app?.registerActivityLifecycleCallbacks(MaleVoiceHook)
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
