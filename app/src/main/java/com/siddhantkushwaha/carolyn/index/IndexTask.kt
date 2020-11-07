package com.siddhantkushwaha.carolyn.index

import android.content.Context

class IndexTask(private val context: Context) : Thread() {

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

        if (index == null)
            index = Index(context)
        index?.run()

        if (indexToFirebase == null)
            indexToFirebase = IndexToFirebase(context)
        indexToFirebase?.upload()

        inProgress = false
    }
}