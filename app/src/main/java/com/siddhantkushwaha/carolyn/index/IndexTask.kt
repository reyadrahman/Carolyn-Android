package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.util.Log


class IndexTask(private val context: Context, private val optimized: Boolean) : Thread() {

    companion object {
        @JvmStatic
        private var inProgress = false

        @JvmStatic
        private var index: Index? = null

        @JvmStatic
        private var indexToFirebase: IndexToFirebase? = null
    }

    override fun run() {
        if (inProgress)
            return

        inProgress = true

        if (index == null)
            index = Index(context, optimized)

        if (indexToFirebase == null)
            indexToFirebase = IndexToFirebase(context)

        val startTime = System.nanoTime()

        index?.run()

        val endTime = System.nanoTime()
        val duration = endTime - startTime

        Log.d("IndexTask", "Indexing took ${duration / 1000000} ms.")

        indexToFirebase?.upload()

        inProgress = false
    }
}