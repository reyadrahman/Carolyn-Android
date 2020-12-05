package com.siddhantkushwaha.carolyn.common


class Task(private val task: () -> Unit) : Thread() {

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