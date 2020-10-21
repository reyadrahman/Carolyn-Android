package com.siddhantkushwaha.carolyn.index

import android.util.Log
import com.siddhantkushwaha.carolyn.activity.ActivityBase

class IndexTask(private val activity: ActivityBase) : Thread() {

    companion object {
        private val tag = "IndexTask"

        @JvmStatic
        private var inProgress = false

        @JvmStatic
        private var index: Index? = null
    }

    private val taskId = ('a'..'z').shuffled().take(6).joinToString("")

    override fun run() {

        if (inProgress) {
            Log.d("${tag}-$taskId", "Another instance of this task is running, skip.")
            return
        }

        Log.d("${tag}-$taskId", "Start indexing process.")

        if (index == null) {
            Log.d("${tag}-$taskId", "Initializing index object.")
            index = Index(activity)
        }
        index?.initIndex()

        inProgress = false

        Log.d("${tag}-$taskId", "Finished indexing process.")
    }
}