package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey


open class MessageThread : RealmObject() {

    var user1: String? = null

    @PrimaryKey
    var user2: String? = null

    var lastMessage: Message? = null

    var user2DisplayName: String? = null

    var contact: Contact? = null

    fun classifyThread(): Boolean {
        return contact == null;
    }

    fun getDisplayName(): String {
        return contact?.name ?: contact?.number ?: user2DisplayName ?: "Anonymous"
    }
}