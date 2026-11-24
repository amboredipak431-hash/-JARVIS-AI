package com.jarvis.ai

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

object PhoneCommands {

    private val ytWords = listOf("youtube", "यूट्यूब", "युट्यूब")
    private val noise = listOf(
        "open", "and", "play", "song", "by", "on", "in", "lav", "laav", "chalu", "kar",
        "ughad", "var", "la", "ओपन", "उघड", "लाव", "लावा", "चालू", "कर", "गाणे", "गाणं", "वर", "वाजव"
    ) + ytWords

    // कमांड असेल तर उत्तर (मजकूर) देतो, नसेल तर null (मग Gemini कडे जाईल)
    fun handle(context: Context, text: String): String? {
        val t = text.trim()
        val lower = t.lowercase()
        if (ytWords.none { lower.contains(it) }) return null

        val query = lower.split(" ", ",", ".")
            .filter { it.isNotBlank() && it !in noise }
            .joinToString(" ")
            .trim()

        if (query.isEmpty()) {
            return if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                    .setPackage("com.google.android.youtube")))
                "YouTube उघडत आहे." else "YouTube उघडता आले नाही."
        }

        // 1) थेट गाणे वाजवण्याचा प्रयत्न
        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
        }
        if (launch(context, play)) return "YouTube वर \"$query\" लावत आहे."

        // 2) नाही जमले तर YouTube मध्ये सर्च
        val search = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
        }
        if (launch(context, search)) return "YouTube मध्ये \"$query\" शोधत आहे. गाण्यावर एक टॅप करा."

        // 3) YouTube app नसेल तर browser
        val web = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
        return if (launch(context, web)) "ब्राउझरमध्ये \"$query\" शोधत आहे."
        else "YouTube उघडता आले नाही."
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
