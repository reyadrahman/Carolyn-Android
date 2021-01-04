package com.siddhantkushwaha.carolyn.common.util


class TaskUtil(private val task: () -> Unit) : Thread() {

    companion object {
        @JvmStatic
        private var inProgress = false
    }

    override fun run() {

        if (inProgress)
            return

        inProgress = true

        task()

        inProgress = false
    }
}