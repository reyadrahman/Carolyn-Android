package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey


open class Message : RealmObject() {

    @PrimaryKey
    var id: String? = null

    var smsId: Int? = null

    var timestamp: Long? = null
    var body: String? = null
    var smsType: Int? = null

    var status: String? = null

    var type: String? = null
    var language: String? = null

    var messageThread: MessageThread? = null

    var classificationSource: String? = null

    var user1: String? = null
}