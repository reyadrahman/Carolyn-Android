package com.siddhantkushwaha.carolyn.common

object Enums {

    object SMSType {
        const val all = 0
        const val inbox = 1
        const val sent = 2
        const val draft = 3
        const val outbox = 4
        const val failed = 5
        const val queued = 6
    }

    object MessageType {
        const val otp = "otp"
        const val transaction = "transaction"
        const val update = "update"
        const val spam = "spam"
    }

    object MessageStatus {
        const val read = "read"
        const val notRead = "not-read"
        const val sent = "sent"
        const val notSent = "not-sent"
    }

    object SourceType {
        const val rule = "rule"
        const val contact = "contact"
        const val unsavedNumber = "unsaved-num"
        const val sentMessage = "sent-message"
        const val model = "model"
    }

    object LanguageType {
        const val en = "en"
    }
}