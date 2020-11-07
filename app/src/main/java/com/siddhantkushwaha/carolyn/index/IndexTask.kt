package com.siddhantkushwaha.carolyn.index

import android.content.Context

class IndexTask(private val context: Context) : Thread() {

    companion object {
        @JvmStatic
        private var inProgress = false
    }

    override fun run() {
        if (inProgress)
            return

        Index(context).run()
        IndexToFirebase(context).upload()

        inProgress = false
    }
}