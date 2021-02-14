package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.util.Log


class IndexTask(private val context: Context, private val optimized: Boolean) : Thread() {

    companion object {
        @JvmStatic
        private var inProgress = false

        @JvmStatic
        private var index: Index? = null
    }

    override fun run() {
        if (inProgress)
            return

        inProgress = true

        if (index == null)
            index = Index(optimized)

        val startTime = System.nanoTime()

        index?.run(context)

        val endTime = System.nanoTime()
        val duration = endTime - startTime

        Log.d("IndexTask", "Indexing took ${duration / 1000000} ms.")

        inProgress = false
    }
}