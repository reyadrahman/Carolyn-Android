package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.LinkingObjects
import io.realm.annotations.PrimaryKey


open class MessageThread : RealmObject() {

    @PrimaryKey
    var user2: String? = null

    var user2NotNormalized: String? = null

    var contact: Contact? = null

    var timestamp: Long? = null

    var latestPersonalMessageTimestamp: Long? = null
    var latestOtpMessageTimestamp: Long? = null
    var latestTransactionMessageTimestamp: Long? = null
    var latestUpdateMessageTimestamp: Long? = null
    var latestSpamMessageTimestamp: Long? = null

    @LinkingObjects("thread")
    val messages: RealmResults<Message>? = null

    fun classifyThread(): Boolean {
        return contact == null;
    }

    fun getDisplayName(): String {
        return contact?.name ?: contact?.number ?: user2NotNormalized ?: "Anonymous"
    }

    fun numMessages(): Int {
        return messages?.size ?: 0
    }
}