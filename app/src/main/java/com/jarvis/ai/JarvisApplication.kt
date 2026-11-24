package com.jarvis.ai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class JarvisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity is MainActivity) {
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    val layout = content?.getChildAt(0) as? LinearLayout
                    if (layout != null) {
                        applyCleanUi(activity, layout)
                        try {
                            hookPhoneCommands(activity, layout)
                        } catch (e: Exception) {
                        }
                        try {
                            hideRings(layout)
                        } catch (e: Exception) {
                        }
                    }
                }
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun collect(v: View, out: MutableList<View>) {
        out.add(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                collect(v.getChildAt(i), out)
            }
        }
    }

    // निळ्या रिंग (HUD) लपवतो. काढून टाकायचे असल्यास हेच फंक्शन वापरले आहे.
    private fun hideRings(root: View) {
        val all = ArrayList<View>()
        collect(root, all)
        for (v in all) {
            if (v.javaClass.simpleName == "HudView") {
                v.alpha = 0f
            }
        }
    }

    // Send बटण दाबले की आधी फोन कमांड (YouTube) तपासतो. कमांड नसेल तर जुने काम चालू राहते.
    private fun hookPhoneCommands(activity: Activity, root: View) {
        val all = ArrayList<View>()
        collect(root, all)
        val send = all.firstOrNull { it is Button && it.contentDescription?.toString() == "Send" } as? Button ?: return
        if (send.tag == "cmd-hooked") return
        val input = all.firstOrNull {
            it is EditText && (it.hint?.toString() ?: "").startsWith("JARVIS")
        } as? EditText ?: return
        val scroll = all.firstOrNull { it is ScrollView && it.childCount == 1 && it.getChildAt(0) is TextView } as? ScrollView
        val chat = scroll?.getChildAt(0) as? TextView
        send.tag = "cmd-hooked"
        send.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val reply = PhoneCommands.handle(activity, text)
                    if (reply != null) {
                        input.setText("")
                        chat?.append("\n\nतुम्ही: " + text + "\nJARVIS: " + reply)
                        v.isPressed = false
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
}
