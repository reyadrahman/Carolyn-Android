package com.siddhantkushwaha.carolyn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder


class SendMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? {

        return null
    }
}