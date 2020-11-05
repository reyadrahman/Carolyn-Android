package com.siddhantkushwaha.carolyn.index

import android.util.Log
import com.siddhantkushwaha.carolyn.activity.ActivityBase

class IndexTask(private val activity: ActivityBase) : Thread() {

    private val tag = "IndexTask"

    companion object {

        @JvmStatic
        private var inProgress = false

        @JvmStatic
        private var index: Index? = null

        @JvmStatic
        private var indexToFirebase: IndexToFirebase? = null
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

        if (indexToFirebase == null) {
            Log.d("${tag}-$taskId", "Initializing indexToFirebase object.")
            indexToFirebase = IndexToFirebase(activity)
        }
        indexToFirebase?.upload()

        inProgress = false

        Log.d("${tag}-$taskId", "Finished indexing process.")
    }
}