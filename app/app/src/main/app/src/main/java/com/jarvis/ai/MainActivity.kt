

package com.jarvis.ai

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Button

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
        title.textSize = 32f
        title.setTextColor(Color.CYAN)
        title.gravity = Gravity.CENTER

        val status = TextView(this)
        status.text = "JARVIS is ready"
        status.textSize = 18f
        status.setTextColor(Color.GREEN)
        status.gravity = Gravity.CENTER

        val input = EditText(this)
        input.hint = "Ask JARVIS anything"
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)

        val button = Button(this)
        button.text = "SEND"

        button.setOnClickListener {
            val question = input.text.toString().trim()

            status.text = if (question.isNotEmpty()) {
                "Message received: $question"
            } else {
                "Please enter a message"
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(input)
        layout.addView(button)

        setContentView(layout)
    }
}
