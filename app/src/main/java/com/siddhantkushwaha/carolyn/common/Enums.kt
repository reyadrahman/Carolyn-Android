package com.siddhantkushwaha.carolyn.common

object Enums {

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
        const val pending = "pending"
        const val notSent = "not-sent"
        const val delivered = "delivered"
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