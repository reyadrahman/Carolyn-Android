package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey


open class Message : RealmObject() {

    @PrimaryKey
    var id: String? = null

    var timestamp: Long? = null
    var body: String? = null
    var sent: Boolean? = null
    var type: String? = null

    var language: String? = null

    var messageThread: MessageThread? = null
}