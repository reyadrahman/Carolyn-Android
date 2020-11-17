package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey


open class MessageThread : RealmObject() {

    var user1: String? = null

    @PrimaryKey
    var user2: String? = null

    var lastMessage: Message? = null
    var user2DisplayName: String? = null

    var inContacts: Boolean? = null

    fun classifyThread(): Boolean {
        return inContacts == false
    }
}