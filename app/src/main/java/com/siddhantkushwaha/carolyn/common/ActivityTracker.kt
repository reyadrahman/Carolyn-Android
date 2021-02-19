package com.siddhantkushwaha.carolyn.common

import android.app.Activity
import android.os.Bundle
import android.util.Log


class ActivityTracker {

    companion object {

        private val tag = "ActivityTracker"

        private var currentActivityClass: String? = null
        private var currentActivityExtras: Bundle? = null

        public fun setInfo(activity: Activity) {
            currentActivityClass = activity::class.java.toString()
            currentActivityExtras = activity.intent.extras

            Log.d(tag, "Activity info set for $currentActivityClass")
            val keys = currentActivityExtras?.keySet()
            keys?.forEach { key ->
                Log.d(tag, "$currentActivityClass, $key, ${currentActivityExtras?.get(key)}")
            }
        }

        public fun resetInfo() {
            Log.d(tag, "Activity info reset for $currentActivityClass")

            currentActivityClass = null
            currentActivityExtras = null
        }

        public fun getActivityName(): String? {
            return currentActivityClass
        }

        public fun getActivityExtras(): Bundle? {
            return currentActivityExtras
        }
    }
}