package com.jarvis.ai

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(40, 40, 40, 40)
        layout.setBackgroundColor(Color.rgb(5, 12, 28))

        val title = TextView(this)
        title.text = "J.A.R.V.I.S"
        title.textSize = 34f
        title.setTextColor(Color.CYAN)
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "PERSONAL AI ASSISTANT"
        subtitle.textSize = 14f
        subtitle.setTextColor(Color.WHITE)
        subtitle.gravity = Gravity.CENTER

        val status = TextView(this)
        status.text = "JARVIS is ready"
        status.textSize = 18f
        status.setTextColor(Color.GREEN)
        status.gravity = Gravity.CENTER
        status.setPadding(0, 35, 0, 35)

        val input = EditText(this)
        input.hint = "Ask JARVIS anything..."
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        input.setSingleLine(true)

        val send = Button(this)
        send.text = "SEND"
        send.setOnClickListener {
            val question = input.text.toString().trim()
            status.text = if (question.isNotEmpty()) {
                "Message received: " + question
            } else {
                "Please enter a message"
            }
        }

        val voice = Button(this)
        voice.text = "VOICE ASSISTANT"
        voice.setOnClickListener {
            status.text = "Voice feature coming soon"
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(status)
        layout.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        layout.addView(send)
        layout.addView(voice)

        setContentView(layout)
    }
}
