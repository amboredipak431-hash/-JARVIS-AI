
package com.jarvis.ai

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.view.ViewGroup

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 30, 24, 30)
            setBackgroundColor(Color.rgb(5, 12, 28))
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 34f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "PERSONAL AI ASSISTANT"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "JARVIS is ready"
            textSize = 17f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(0, 35, 0, 35)
        }

        val input = EditText(this).apply {
            hint = "Ask JARVIS anything..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
        }

        val send = Button(this).apply {
            text = "SEND"
            setOnClickListener {
                val question = input.text.toString().trim()
                if (question.isNotEmpty()) {
                    status.text = "Message received: $question"
                } else {
                    status.text = "Please enter a message"
                }
            }
        }

        val voice = Button(this).apply {
            text = "VOICE ASSISTANT"
            setOnClickListener {
                status.text = "Voice feature coming soon"
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(status)

        layout.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        layout.addView(send)
        layout.addView(voice)

        setContentView(layout)
    }
}
