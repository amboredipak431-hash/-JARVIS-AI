package com.jarvis.ai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout

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
}
