package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.min

// ---------- बंद असलेले apps आणि शब्द ----------

private val G_LONG_NAMES = listOf(
    "phonepe", "phone pe", "फोनपे", "फोन पे", "gpay", "g pay", "google pay", "गुगल पे", "जीपे", "जी पे",
    "paytm", "पेटीएम", "bhim", "भीम", "yono", "योनो", "digilocker", "digi locker", "डिजीलॉकर", "डिजिलॉकर",
    "डिजी लॉकर", "aadhaar", "aadhar", "आधार", "authenticator", "ऑथेंटिकेटर", "एसबीआय",
    "token scanner", "टोकन स्कॅनर", "oken", "ओकेन"
)
private val G_SHORT_WORDS = setOf(
    "sbi", "upi", "otp", "scanner", "bank", "pay", "payment",
    "बँक", "ओटीपी", "युपीआय", "यूपीआय", "स्कॅनर", "पेमेंट"
)
private val G_SMS_WORDS = setOf("messages", "message", "sms", "messaging", "मेसेजेस", "मेसेज", "मॅसेज", "एसएमएस")
private val G_MESSENGERS = listOf("whatsapp", "व्हॉट्सअ", "व्हाट्सअ", "instagram", "insta", "इन्स्टा", "इंस्टा", "telegram", "टेलिग्राम")
private val G_OPEN = listOf("open", "launch", "उघड", "ओपन", "लाँच", "खोल", "kholo")
private val G_OPEN_MORE = listOf("start", "चालू", "सुरू", "login", "log in", "लॉगिन")
private val G_OTHER = listOf(
    "send", "pay", "transfer", "read", "show", "scan", "check", "balance",
    "पाठव", "वाच", "दाखव", "स्कॅन", "चेक", "बॅलन्स", "ट्रान्सफर", "पे कर"
)
internal val G_QUESTION = listOf(
    "what", "how", "why", "who", "when", "where", "explain",
    "काय", "कसे", "कशी", "कसं", "का ", "कोण", "कधी", "कुठे", "समजाव", "माहिती", "म्हणजे"
)
private val G_SYSTEM = listOf(
    "settings", "सेटिंग", "wifi", "wi-fi", "वायफाय", "bluetooth", "ब्लूटूथ", "airplane", "एअरप्लेन",
    "volume", "आवाज", "brightness", "ब्राइटनेस", "camera", "कॅमेरा", "calculator", "कॅल्क्युलेटर",
    "dark mode", "battery saver", "torch", "टॉर्च", "flashlight"
)
private val G_APP_NOISE = setOf("app", "अॅप", "ॲप", "अप्प", "application", "ऍप", "अँप")

private val G_APP_BLOCK = listOf(
    "phonepe", "paytm", "bhim", "npci", "gpay", "paisa", "tez", "yono", "sbi", "digilocker", "aadhaar",
    "aadhar", "uidai", "authenticator", "token", "oken", "hdfc", "icici", "kotak", "axis", "pnb",
    "mobikwik", "freecharge", "amazonpay", "wallet"
)

// ---------- कॉलसाठी शब्द ----------

private val G_CALL = setOf("call", "calling", "कॉल", "काल", "कोल", "dial", "डायल", "ring", "रिंग")
private val G_PHONE = setOf("phone", "फोन")
private val G_DO = setOf("कर", "kar", "लाव", "लावा", "लगाव", "lagao", "लगा", "करा", "करून")
internal val G_STOP = setOf(
    "दे", "द्या", "ला", "ना", "नको", "please", "plz", "pls", "to", "the", "a", "on", "mobile", "मोबाईल",
    "number", "नंबर", "sir", "सर", "jarvis", "जार्विस", "जारविस", "hey", "hi", "ok", "okay", "can", "you",
    "बोल", "kro", "karo"
)
private val G_EMERGENCY = setOf("100", "101", "102", "108", "112", "181", "1091", "1098", "1930")

// ---------- WhatsApp साठी शब्द ----------

private val G_WA = listOf("whatsapp", "व्हॉट्सअ", "व्हाट्सअ", "व्हॉट्स", "व्हाट्स", "वॉट्सअ", "वाट्सअ")
private val G_MSGW = listOf("msg", "message", "मेसेज", "मॅसेज", "संदेश", "पाठव", "send")
private val G_WA_NOISE = setOf(
    "open", "उघडून", "उघड", "उघडा", "ओपन", "kholo", "खोलून", "खोल",
    "msg", "message", "मेसेज", "मॅसेज", "संदेश", "पाठव", "पाठवा", "send", "text", "whatsapp",
    "कर", "करा", "kar", "करून", "दे", "द्या", "लिही", "लिहा", "सांग",
    "ला", "ना", "वर", "var", "on", "to", "the", "a", "please", "plz", "jarvis", "जार्विस", "जारविस", "सर", "sir",
    "hey", "ok", "okay", "ko", "ke", "आणि", "and", "then", "मग", "मला", "my", "for"
)

internal fun gNorm(s: String): String {
    val sb = StringBuilder()
    for (c in s.lowercase(Locale.ROOT)) {
        if (c in '\u0966'..'\u096F') {
            sb.append(('0'.code + (c.code - 0x0966)).toChar())
        } else {
            sb.append(c)
        }
    }
    return sb.toString().trim()
}

internal fun gWords(t: String): List<String> {
    return t.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
}

internal fun gHasAny(t: String, list: List<String>): Boolean {
    for (w in list) {
        if (t.contains(w)) return true
    }
    return false
}

private fun gBlocked(t: String): Boolean {
    val words = gWords(t)
    var sms = false
    for (w in words) {
        if (G_SMS_WORDS.contains(w)) sms = true
    }
    if (sms && words.size <= 5 && gHasAny(t, G_OPEN) && !gHasAny(t, G_MESSENGERS)) return true

    var named = false
    for (n in G_LONG_NAMES) {
        if (t.contains(n)) {
            named = true
            break
        }
    }
    if (!named) {
        for (w in words) {
            if (G_SHORT_WORDS.contains(w)) {
                named = true
                break
            }
        }
    }
    if (!named) return false
    if (gHasAny(t, G_OPEN)) return true
    val question = gHasAny(t, G_QUESTION)
    if (!question && (gHasAny(t, G_OPEN_MORE) || gHasAny(t, G_OTHER))) return true
    return false
}

private fun gAppBlocked(label: String, pkg: String): Boolean {
    val l = label.lowercase(Locale.ROOT)
    val p = pkg.lowercase(Locale.ROOT)
    for (w in G_APP_BLOCK) {
        if (l.contains(w) || p.contains(w)) return true
    }
    if (l == "message" || l.contains("messages") || l.contains("संदेश") || l.contains("मेसेज")) return true
    if (p.contains("messaging") || p.contains(".mms") || p.contains("com.android.mms")) return true
    return false
}

// null = कॉल नाही, "" = कॉल पण नाव नाही, बाकी = नाव
private fun gParseCall(t: String): String? {
    val words = gWords(t)
    if (words.size > 9) return null
    if (Regex("[0-9]{6,}").containsMatchIn(t.replace(" ", "").replace("-", ""))) return null
    if (gHasAny(t, G_QUESTION)) return null
    var isCall = false
    var hasDo = false
    var hasPhone = false
    for (w in words) {
        if (G_CALL.contains(w)) isCall = true
        if (G_PHONE.contains(w)) hasPhone = true
        if (G_DO.contains(w)) hasDo = true
    }
    if (!isCall && !(hasPhone && hasDo)) return null
    val keep = ArrayList<String>()
    for (w0 in words) {
        var w = w0
        if (G_CALL.contains(w) || G_PHONE.contains(w) || G_DO.contains(w) || G_STOP.contains(w)) continue
        if (w.length > 3 && w.endsWith("ला")) {
            w = w.substring(0, w.length - 2)
        }
        if (w.isNotEmpty()) keep.add(w)
    }
    return keep.joinToString(" ")
}

private fun gNeedsConfirm(t: String): Boolean {
    if (gHasAny(t, listOf("alarm", "अलार्म", "timer", "टायमर"))) return false
    val msgApp = gHasAny(t, G_MESSENGERS)
    val msgWord = gHasAny(t, listOf("msg", "message", "मेसेज", "मॅसेज", "संदेश", "पाठव", "send"))
    if (msgApp && msgWord) return false
    if (Regex("[0-9]{6,}").containsMatchIn(t.replace(" ", "").replace("-", "")) &&
        gHasAny(t, listOf("call", "कॉल", "dial", "डायल"))
    ) {
        return false
    }
    if (gHasAny(t, G_QUESTION)) return false
    if (gHasAny(t, listOf("torch", "टॉर्च", "flashlight", "फ्लॅशलाईट", "फ्लॅशलाइट"))) return true
    if (gHasAny(t, G_OPEN)) return true
    return false
}

// ---------- नाव ओळखणं (मराठी आणि English एकत्र) ----------

private val G_DEVA: HashMap<Char, String> = HashMap<Char, String>().apply {
    put('क', "k"); put('ख', "kh"); put('ग', "g"); put('घ', "gh"); put('ङ', "n")
    put('च', "ch"); put('छ', "chh"); put('ज', "j"); put('झ', "jh"); put('ञ', "n")
    put('ट', "t"); put('ठ', "th"); put('ड', "d"); put('ढ', "dh"); put('ण', "n")
    put('त', "t"); put('थ', "th"); put('द', "d"); put('ध', "dh"); put('न', "n")
    put('प', "p"); put('फ', "ph"); put('ब', "b"); put('भ', "bh"); put('म', "m")
    put('य', "y"); put('र', "r"); put('ल', "l"); put('व', "v"); put('श', "sh")
    put('ष', "sh"); put('स', "s"); put('ह', "h"); put('ळ', "l"); put('ं', "n")
    put('ँ', "n"); put('ऋ', "r")
}

private fun gAddCons(c: Char, sb: StringBuilder) {
    val x = when (c) {
        'w' -> 'v'
        'f' -> 'p'
        'z' -> 'j'
        'q' -> 'k'
        else -> c
    }
    if ("aeiouyh".indexOf(x) >= 0) return
    if (sb.isNotEmpty() && sb[sb.length - 1] == x) return
    sb.append(x)
}

internal fun gSkeleton(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase(Locale.ROOT)) {
        val m = G_DEVA[ch]
        if (m != null) {
            for (x in m) gAddCons(x, sb)
        } else if (ch in 'a'..'z') {
            gAddCons(ch, sb)
        }
    }
    return sb.toString()
}

internal fun gLev(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + (if (a[i - 1] == b[j - 1]) 0 else 1))
            prev = tmp
        }
    }
    return dp[b.length]
}

private class ContactHit(val name: String, val number: String)
private class ContactRec(val name: String, val number: String, val nl: String, val sk: String)

private fun gLoadContacts(act: Activity): List<ContactRec> {
    val out = ArrayList<ContactRec>()
    var c: Cursor? = null
    try {
        c = act.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        if (c != null) {
            var n = 0
            while (c.moveToNext() && n < 6000) {
                n++
                val name = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val nl = name.lowercase(Locale.ROOT)
                out.add(ContactRec(name, num, nl, gSkeleton(nl)))
            }
        }
    } catch (e: Exception) {
    } finally {
        try {
            c?.close()
        } catch (e: Exception) {
        }
    }
    return out
}

private fun gMatch(list: List<ContactRec>, query: String): List<ContactHit> {
    val qLow = query.lowercase(Locale.ROOT).trim()
    if (qLow.isEmpty()) return emptyList()
    val qSk = gSkeleton(qLow)
    var best = 99
    val hits = ArrayList<ContactHit>()
    for (r in list) {
        val score = when {
            r.nl == qLow -> 0
            r.sk.isNotEmpty() && r.sk == qSk -> 1
            qLow.length >= 3 && r.nl.contains(qLow) -> 1
            qSk.length >= 3 && r.sk.contains(qSk) -> 2
            qSk.length >= 4 && r.sk.isNotEmpty() && gLev(r.sk, qSk) <= 1 -> 3
            else -> 99
        }
        if (score < best) {
            best = score
            hits.clear()
        }
        if (score == best && score < 99) {
            val digits = r.number.filter { it.isDigit() }.takeLast(10)
            var dup = false
            for (h in hits) {
                if (h.number.filter { it.isDigit() }.takeLast(10) == digits) {
                    dup = true
                    break
                }
            }
            if (!dup) hits.add(ContactHit(r.name, r.number))
        }
    }
    return hits
}

// ---------- फोनमधले apps शोधणं ----------

private class AppHit(val label: String, val pkg: String)

private fun gFindApps(act: Activity, query: String): List<AppHit> {
    val qLow = query.lowercase(Locale.ROOT).trim()
    if (qLow.isEmpty()) return emptyList()
    val qSk = gSkeleton(qLow)
    var best = 99
    val hits = ArrayList<AppHit>()
    try {
        val li = Intent(Intent.ACTION_MAIN)
        li.addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = act.packageManager
        for (ri in pm.queryIntentActivities(li, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == act.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val nl = label.lowercase(Locale.ROOT)
            val sk = gSkeleton(nl)
            val score = when {
                nl == qLow -> 0
                sk.isNotEmpty() && sk == qSk -> 1
                qLow.length >= 3 && nl.contains(qLow) -> 1
                qSk.length >= 3 && sk.contains(qSk) -> 2
                qSk.length >= 5 && sk.isNotEmpty() && gLev(sk, qSk) <= 1 -> 3
                else -> 99
            }
            if (score < best) {
                best = score
                hits.clear()
            }
            if (score == best && score < 99) {
                var dup = false
                for (h in hits) {
                    if (h.pkg == pkg) dup = true
                }
                if (!dup) hits.add(AppHit(label, pkg))
            }
        }
    } catch (e: Exception) {
    }
    return hits
}

// ---------- जुन्या JARVIS शी जोड ----------

private class GuardBridge(private val act: Activity) {

    private fun method(name: String, vararg types: Class<*>): java.lang.reflect.Method? {
        return try {
            val m = act.javaClass.getDeclaredMethod(name, *types)
            m.isAccessible = true
            m
        } catch (e: Exception) {
            null
        }
    }

    private fun field(name: String): java.lang.reflect.Field? {
        return try {
            val f = act.javaClass.getDeclaredField(name)
            f.isAccessible = true
            f
        } catch (e: Exception) {
            null
        }
    }

    private val mHandle = method("handle", String::class.java)
    private val mAddMsg = method("addMsg", String::class.java, String::class.java)
    private val mShow = method("show", String::class.java, java.lang.Boolean.TYPE)
    private val fPending = field("pendingImage")
    private val fStatus = field("status")
    private val fMarathi = field("marathi")

    fun ready(): Boolean = mHandle != null

    fun handle(text: String): Boolean {
        return try {
            mHandle?.invoke(act, text)
            mHandle != null
        } catch (e: Exception) {
            false
        }
    }

    fun addMsg(who: String, text: String) {
        try {
            mAddMsg?.invoke(act, who, text)
        } catch (e: Exception) {
        }
    }

    fun setStatus(text: String) {
        try {
            (fStatus?.get(act) as? TextView)?.text = text
        } catch (e: Exception) {
        }
    }

    fun say(text: String) {
        try {
            if (mShow != null) {
                mShow.invoke(act, text, true)
            } else {
                setStatus(text)
            }
        } catch (e: Exception) {
            setStatus(text)
        }
    }

    fun hasPendingImage(): Boolean {
        return try {
            fPending?.get(act) != null
        } catch (e: Exception) {
            false
        }
    }

    fun isMarathi(): Boolean {
        return try {
            fMarathi?.getBoolean(act) ?: true
        } catch (e: Exception) {
            true
        }
    }

    fun t(mr: String, en: String): String = if (isMarathi()) mr else en
}

private fun gEnsure(act: Activity, perm: String, onOk: () -> Unit, onFail: () -> Unit) {
    if (act.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
        onOk()
        return
    }
    try {
        act.requestPermissions(arrayOf(perm), 4400)
    } catch (e: Exception) {
    }
    val h = Handler(Looper.getMainLooper())
    val start = System.currentTimeMillis()
    val r = object : Runnable {
        override fun run() {
            if (act.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
                onOk()
            } else if (System.currentTimeMillis() - start > 30000L) {
                onFail()
            } else {
                h.postDelayed(this, 400L)
            }
        }
    }
    h.postDelayed(r, 800L)
}

private fun gStart(act: Activity, i: Intent) {
    try {
        act.startActivity(i)
    } catch (e: Exception) {
    }
}

private fun gPickContact(act: Activity, b: GuardBridge, hits: List<ContactHit>, onPick: (ContactHit) -> Unit) {
    if (hits.size == 1) {
        onPick(hits[0])
        return
    }
    val shown = hits.take(6)
    val labels = ArrayList<String>()
    for (h in shown) labels.add(h.name + "  " + h.number)
    AlertDialog.Builder(act)
        .setTitle(b.t("कोणाला?", "Which contact?"))
        .setItems(labels.toTypedArray()) { _, which -> onPick(shown[which]) }
        .setNegativeButton("NO", null)
        .show()
}

// ---------- कॉल ----------

private fun gDoCall(act: Activity, b: GuardBridge, h: ContactHit) {
    val keep = h.number.filter { it.isDigit() || it == '+' }
    val plain = keep.filter { it.isDigit() }
    val uri = Uri.parse("tel:" + Uri.encode(keep))
    if (G_EMERGENCY.contains(plain)) {
        b.say(b.t("हा आणीबाणी नंबर आहे. Dialer उघडला, हिरवं बटण तू दाब.", "Emergency number. Dialer opened, you press the green button."))
        gStart(act, Intent(Intent.ACTION_DIAL, uri))
        return
    }
    gEnsure(act, Manifest.permission.CALL_PHONE, {
        b.say(b.t("ठीक आहे सर, " + h.name + " ला कॉल लावतो.", "Okay Sir, calling " + h.name + "."))
        try {
            act.startActivity(Intent(Intent.ACTION_CALL, uri))
        } catch (e: Exception) {
            gStart(act, Intent(Intent.ACTION_DIAL, uri))
        }
    }, {
        b.say(b.t("कॉलची परवानगी मिळाली नाही. Dialer उघडला.", "No call permission. Dialer opened."))
        gStart(act, Intent(Intent.ACTION_DIAL, uri))
    })
}

private fun gConfirmCall(act: Activity, b: GuardBridge, h: ContactHit) {
    val msg = b.t(h.name + " (" + h.number + ") ला कॉल करू का?", "Call " + h.name + " (" + h.number + ")?")
    AlertDialog.Builder(act)
        .setTitle("Allow?")
        .setMessage(msg)
        .setPositiveButton("YES") { _, _ -> gDoCall(act, b, h) }
        .setNegativeButton("NO") { _, _ -> b.say(b.t("ठीक आहे सर, कॉल रद्द केला.", "Okay Sir, call cancelled.")) }
        .show()
}

private fun gStartCall(act: Activity, b: GuardBridge, name: String) {
    gEnsure(act, Manifest.permission.READ_CONTACTS, {
        val hits = gMatch(gLoadContacts(act), name)
        if (hits.isEmpty()) {
            b.say(b.t("सर, \"" + name + "\" नावाचा कॉन्टॅक्ट सापडला नाही.", "Sir, I could not find a contact named \"" + name + "\"."))
        } else {
            gPickContact(act, b, hits) { h -> gConfirmCall(act, b, h) }
        }
    }, {
        b.say(b.t("सर, कॉन्टॅक्ट्सची परवानगी मिळाली नाही.", "Sir, contacts permission was not given."))
    })
}

// ---------- WhatsApp संदेश (नावाने) ----------

private class WaParsed(val tokens: List<String>, val explicit: String?)

private fun gParseWa(q: String, t: String): WaParsed? {
    if (!gHasAny(t, G_WA)) return null
    if (!gHasAny(t, G_MSGW)) return null
    if (Regex("[0-9]{8,}").containsMatchIn(t.replace(" ", "").replace("-", ""))) return null
    if (gHasAny(t, G_QUESTION)) return null
    val orig = q.split(Regex("[\\s,.!?]+")).filter { it.isNotEmpty() }
    var sep = -1
    for (i in orig.indices) {
        val lw = gNorm(orig[i])
        if (lw == "की" || lw == "म्हणून" || lw == "saying" || lw == "that" || lw == "ki") {
            sep = i
            break
        }
    }
    val limit = if (sep >= 0) sep else orig.size
    val tokens = ArrayList<String>()
    for (i in 0 until limit) {
        val lw = gNorm(orig[i])
        if (G_WA_NOISE.contains(lw) || gHasAny(lw, G_WA)) continue
        tokens.add(orig[i])
    }
    val explicit = if (sep >= 0) orig.subList(sep + 1, orig.size).joinToString(" ") else null
    return WaParsed(tokens, explicit)
}

private fun gOpenWa(act: Activity, b: GuardBridge, h: ContactHit, body: String) {
    var digits = h.number.filter { it.isDigit() }
    if (digits.length == 11 && digits.startsWith("0")) digits = digits.substring(1)
    if (digits.length == 10) digits = "91" + digits
    val link = "https://wa.me/" + digits + "?text=" + Uri.encode(body)
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            i.setPackage(pkg)
            act.startActivity(i)
            b.say(b.t("संदेश तयार आहे सर. Send बटण तू दाब.", "Message is ready, Sir. You press Send."))
            return
        } catch (e: Exception) {
        }
    }
    b.say(b.t("WhatsApp सापडलं नाही.", "WhatsApp was not found."))
}

private fun gConfirmWa(act: Activity, b: GuardBridge, h: ContactHit, body: String) {
    val msg = b.t(
        "WhatsApp वर " + h.name + " ला हा संदेश तयार करू का?\n\n\"" + body + "\"\n\nSend बटण तू स्वतः दाबायचं.",
        "Prepare this WhatsApp message to " + h.name + "?\n\n\"" + body + "\"\n\nYou press Send yourself."
    )
    AlertDialog.Builder(act)
        .setTitle("Allow?")
        .setMessage(msg)
        .setPositiveButton("YES") { _, _ -> gOpenWa(act, b, h, body) }
        .setNegativeButton("NO") { _, _ -> b.say(b.t("ठीक आहे सर, रद्द केलं.", "Okay Sir, cancelled.")) }
        .show()
}

private fun gStartWa(act: Activity, b: GuardBridge, p: WaParsed) {
    gEnsure(act, Manifest.permission.READ_CONTACTS, {
        val list = gLoadContacts(act)
        val mt = ArrayList<String>()
        for (tok in p.tokens) {
            val lw = gNorm(tok)
            mt.add(if (lw.length > 3 && lw.endsWith("ला")) lw.substring(0, lw.length - 2) else lw)
        }
        var hits: List<ContactHit> = emptyList()
        var body = ""
        val explicit = p.explicit
        if (explicit != null) {
            hits = gMatch(list, mt.joinToString(" "))
            body = explicit
        } else {
            for (s in p.tokens.size downTo 1) {
                val h = gMatch(list, mt.subList(0, s).joinToString(" "))
                if (h.isNotEmpty()) {
                    hits = h
                    body = p.tokens.subList(s, p.tokens.size).joinToString(" ")
                    break
                }
            }
        }
        if (body.isBlank()) body = "Hi"
        if (hits.isEmpty()) {
            b.say(b.t("सर, कॉन्टॅक्ट सापडला नाही.", "Sir, I could not find that contact."))
        } else {
            val finalBody = body
            gPickContact(act, b, hits) { h -> gConfirmWa(act, b, h, finalBody) }
        }
    }, {
        b.say(b.t("सर, कॉन्टॅक्ट्सची परवानगी मिळाली नाही.", "Sir, contacts permission was not given."))
    })
}

// ---------- कोणताही app नावाने उघडणं ----------

private fun gConfirmOpen(act: Activity, b: GuardBridge, h: AppHit) {
    AlertDialog.Builder(act)
        .setTitle("Allow?")
        .setMessage(b.t(h.label + " उघडू का?", "Open " + h.label + "?"))
        .setPositiveButton("YES") { _, _ ->
            val i = act.packageManager.getLaunchIntentForPackage(h.pkg)
            if (i != null) {
                b.say(b.t("ठीक आहे सर, " + h.label + " उघडतो.", "Okay Sir, opening " + h.label + "."))
                gStart(act, i)
            } else {
                b.say(b.t("तो app उघडता आला नाही.", "Could not open that app."))
            }
        }
        .setNegativeButton("NO") { _, _ -> b.say(b.t("ठीक आहे सर, रद्द केलं.", "Okay Sir, cancelled.")) }
        .show()
}

private fun gOpenApp(act: Activity, b: GuardBridge, input: EditText, q: String, t: String): Boolean {
    if (!gHasAny(t, G_OPEN)) return false
    if (gHasAny(t, G_SYSTEM) || gHasAny(t, G_QUESTION)) return false
    val keep = ArrayList<String>()
    for (w in gWords(t)) {
        if (gHasAny(w, G_OPEN) || G_STOP.contains(w) || G_APP_NOISE.contains(w) || G_DO.contains(w) || G_PHONE.contains(w)) continue
        keep.add(w)
    }
    if (keep.isEmpty()) return false
    val hits = gFindApps(act, keep.joinToString(" "))
    if (hits.isEmpty()) return false
    input.setText("")
    b.addMsg("You", q)
    val ok = hits.filter { !gAppBlocked(it.label, it.pkg) }
    if (ok.isEmpty()) {
        b.say(
            b.t(
                "सर, या app ला मी हात लावणार नाही. पेमेंट, बँक, ओळखपत्र आणि SMS apps माझ्यासाठी बंद आहेत.",
                "Sir, I will not touch that app. Payment, banking, ID and SMS apps are off limits for me."
            )
        )
        return true
    }
    if (ok.size == 1) {
        gConfirmOpen(act, b, ok[0])
    } else {
        val shown = ok.take(6)
        val labels = ArrayList<String>()
        for (h in shown) labels.add(h.label + "  (" + h.pkg.substringAfterLast('.') + ")")
        AlertDialog.Builder(act)
            .setTitle(b.t("कोणता?", "Which one?"))
            .setItems(labels.toTypedArray()) { _, which -> gConfirmOpen(act, b, shown[which]) }
            .setNegativeButton("NO", null)
            .show()
    }
    return true
}

// ---------- SEND बटणावर पहारा ----------

private fun gSend(act: Activity, b: GuardBridge, input: EditText) {
    val q = input.text.toString().trim()
    try {
        if (b.hasPendingImage()) {
            b.handle(q)
            return
        }
        if (q.isEmpty()) {
            b.setStatus("Please enter a message")
            return
        }
        val sp = act.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        if (!sp.getBoolean("on", true)) {
            b.handle(q)
            return
        }
        val t = gNorm(q)

        if (gBlocked(t)) {
            input.setText("")
            b.addMsg("You", q)
            b.say(
                b.t(
                    "सर, या app ला मी हात लावणार नाही. पेमेंट, बँक, ओळखपत्र आणि SMS apps माझ्यासाठी बंद आहेत.",
                    "Sir, I will not touch that app. Payment, banking, ID and SMS apps are off limits for me."
                )
            )
            return
        }

        val wa = gParseWa(q, t)
        if (wa != null) {
            input.setText("")
            b.addMsg("You", q)
            if (wa.tokens.isEmpty()) {
                b.say(b.t("सर, कोणाला मेसेज करू?", "Sir, whom should I message?"))
            } else {
                gStartWa(act, b, wa)
            }
            return
        }

        val mini = jarvisParseMini(t)
        if (mini != null) {
            input.setText("")
            b.addMsg("You", q)
            jarvisRunMini(act, mini, { s -> b.say(s) }, b.isMarathi())
            return
        }

        val callName = gParseCall(t)
        if (callName != null) {
            input.setText("")
            b.addMsg("You", q)
            if (callName.isEmpty()) {
                b.say(b.t("सर, कोणाला कॉल करू?", "Sir, whom should I call?"))
            } else {
                gStartCall(act, b, callName)
            }
            return
        }

        if (gOpenApp(act, b, input, q, t)) return

        if (gNeedsConfirm(t)) {
            AlertDialog.Builder(act)
                .setTitle("Allow?")
                .setMessage(b.t("सर, मी हे करू का?", "Sir, shall I do this?") + "\n\n" + q)
                .setPositiveButton("YES") { _, _ -> b.handle(q) }
                .setNegativeButton("NO") { _, _ ->
                    input.setText("")
                    b.addMsg("You", q)
                    b.say(b.t("ठीक आहे सर, रद्द केलं.", "Okay Sir, cancelled."))
                }
                .show()
            return
        }

        b.handle(q)
    } catch (e: Exception) {
        if (!b.handle(q)) {
            b.setStatus("Error: " + e.message)
        }
    }
}

private val guardBridges = WeakHashMap<Activity, GuardBridge>()
private val guardPatched = WeakHashMap<View, Boolean>()

private fun gCollect(v: View, out: MutableList<View>) {
    out.add(v)
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            gCollect(v.getChildAt(i), out)
        }
    }
}

private fun gPatch(act: Activity) {
    try {
        val content = act.findViewById<ViewGroup>(android.R.id.content) ?: return
        val all = ArrayList<View>()
        gCollect(content, all)
        var input: EditText? = null
        var send: Button? = null
        for (v in all) {
            if (v is EditText && input == null) {
                val h = v.hint?.toString() ?: ""
                if (h.contains("विचारा") || h.contains("Ask JARVIS")) input = v
            }
            if (v is Button && send == null) {
                val desc = v.contentDescription?.toString() ?: ""
                val t = v.text?.toString() ?: ""
                if (desc == "Send" || t == "SEND") send = v
            }
        }
        val inp = input ?: return
        val btn = send ?: return
        if (guardPatched[btn] == true) return
        val bridge = guardBridges.getOrPut(act) { GuardBridge(act) }
        if (!bridge.ready()) return
        guardPatched[btn] = true
        btn.setOnClickListener { gSend(act, bridge, inp) }
    } catch (e: Exception) {
    }
}

private object PhoneGuardHook : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        gPatch(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        gPatch(activity)
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ gPatch(activity) }, 800L)
        h.postDelayed({ gPatch(activity) }, 2500L)
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        guardBridges.remove(activity)
    }
}

// app सुरू होताना हा आपोआप चालतो
class PhoneGuardInit : ContentProvider() {
    override fun onCreate(): Boolean {
        val c = context
        val app = c?.applicationContext as? Application
        app?.registerActivityLifecycleCallbacks(PhoneGuardHook)
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
