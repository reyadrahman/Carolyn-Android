package com.siddhantkushwaha.carolyn.tasks


class MarkMessagesAsReadTask(private val task: () -> Unit) : Thread() {

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