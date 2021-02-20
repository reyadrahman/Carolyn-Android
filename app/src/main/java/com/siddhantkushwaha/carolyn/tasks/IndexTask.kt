package com.siddhantkushwaha.carolyn.tasks

import android.content.Context
import android.util.Log
import com.siddhantkushwaha.carolyn.index.Index


class IndexTask(private val context: Context) : Thread() {

    companion object {
        @JvmStatic
        private var inProgress = false

        @JvmStatic
        private val index = Index(false)
    }

    override fun run() {
        if (inProgress)
            return

        inProgress = true

        val startTime = System.nanoTime()

        index.run(context)

        val endTime = System.nanoTime()
        val duration = endTime - startTime

        Log.d("IndexTask", "Indexing took ${duration / 1000000} ms.")

        inProgress = false
    }
}