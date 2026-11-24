package com.jarvis.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class PxMsg(val fromUser: Boolean, val text: String)
class PxSession(val id: Long, val title: String, val msgs: SnapshotStateList<PxMsg>)

private val PxBg = Color(0xFF151619)
private val PxCard = Color(0xFF1E2024)
private val PxAccent = Color(0xFF00E5FF)

private fun saveSessions(p: android.content.SharedPreferences, list: List<PxSession>) {
    val arr = JSONArray()
    list.forEach { s ->
        val m = JSONArray()
        s.msgs.forEach { m.put(JSONObject().put("u", it.fromUser).put("t", it.text)) }
        arr.put(JSONObject().put("id", s.id).put("title", s.title).put("msgs", m))
    }
    p.edit().putString("data", arr.toString()).apply()
}

private fun loadSessions(p: android.content.SharedPreferences): List<PxSession> = try {
    val arr = JSONArray(p.getString("data", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val m = o.getJSONArray("msgs")
        val msgs = mutableStateListOf<PxMsg>()
        for (j in 0 until m.length()) {
            val x = m.getJSONObject(j)
            msgs.add(PxMsg(x.getBoolean("u"), x.getString("t")))
        }
        PxSession(o.getLong("id"), o.getString("title"), msgs)
    }
} catch (e: Exception) { emptyList() }

/**
 * onAsk = तुमच्या आधीच्या Gemini चे फंक्शन (प्रश्न घेतो, उत्तर परत देतो).
 */
@Composable
fun PerplexityScreen(onAsk: suspend (String) -> String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("jarvis_sessions", 0) }
    val sessions = remember { mutableStateListOf<PxSession>().also { it.addAll(loadSessions(prefs)) } }
    var current by remember { mutableStateOf<PxSession?>(null) }
    var incognito by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    fun send(text: String) {
        val s = current ?: PxSession(System.currentTimeMillis(), text.take(40), mutableStateListOf()).also {
            current = it
            if (!incognito) sessions.add(0, it)
        }
        s.msgs.add(PxMsg(true, text))
        scope.launch {
            val reply = PhoneCommands.handle(ctx, text)
                ?: runCatching { onAsk(text) }.getOrElse { "त्रुटी: ${it.message}" }
            s.msgs.add(PxMsg(false, reply))
            if (!incognito) saveSessions(prefs, sessions)
        }
    }

    Column(Modifier.fillMaxSize().background(PxBg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // वरची पट्टी
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showSettings = true }) { Text("⚙", fontSize = 22.sp, color = Color.White) }
            Text(
                if (incognito) "J.A.R.V.I.S  🕶" else "J.A.R.V.I.S",
                color = PxAccent, fontSize = 20.sp, modifier = Modifier.weight(1f)
            )
            if (current != null) TextButton(onClick = { current = null }) { Text("＋ नवीन", color = Color.White) }
        }

        // मधला भाग
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            val s = current
            if (s == null) {
                Column {
                    Text("Sessions", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
                    if (sessions.isEmpty()) Text("अजून काही चॅट नाही.", color = Color.Gray)
                    LazyColumn {
                        items(sessions, key = { it.id }) { x ->
                            Text(
                                x.title, color = Color.White, fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth().clickable { current = x }.padding(vertical = 14.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(reverseLayout = false) {
                    items(s.msgs) { m ->
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                m.text, color = Color.White, fontSize = 16.sp,
                                modifier = Modifier.background(if (m.fromUser) PxCard else PxBg, RoundedCornerShape(14.dp)).padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // खालची "Ask anything..." पट्टी
        Row(
            Modifier.fillMaxWidth().padding(12.dp).background(PxCard, RoundedCornerShape(24.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("काहीही विचारा…", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )
            TextButton(onClick = {
                val t = input.trim()
                if (t.isNotEmpty()) { input = ""; send(t) }
            }) { Text("➤", color = PxAccent, fontSize = 22.sp) }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("बंद") } },
            title = { Text("Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Incognito Mode")
                            Text("चॅट सेव्ह होणार नाहीत", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = incognito, onCheckedChange = { incognito = it; current = null })
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        sessions.clear(); saveSessions(prefs, sessions); current = null; showSettings = false
                    }) { Text("Clear History", color = Color.Red) }
                }
            }
        )
    }
}
