package com.siddhantkushwaha.carolyn.ai

import android.app.Activity


class ModelDownloadTask(private val activity: Activity) : Thread() {

    companion object {
        @JvmStatic
        private var inProgress = false
    }

    override fun run() {
        if (inProgress)
            return

        inProgress = true

        if (!MessageClassifier.isAssetsDownloaded(activity))
            MessageClassifier.getInstance(activity, forceDownload = true)

        inProgress = false
    }
}