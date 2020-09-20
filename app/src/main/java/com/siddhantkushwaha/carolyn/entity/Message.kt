package com.siddhantkushwaha.carolyn.entity

import com.siddhantkushwaha.carolyn.common.getHash
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Message : RealmObject() {

    @PrimaryKey
    var id: String? = null

    var timestamp: Long? = null
    var body: String? = null
    var sent: Boolean? = null
    var type: String? = null

    var messageThread:MessageThread? = null

    public fun buildId() {
        id = getHash("${timestamp!!}, ${body!!}, ${sent!!}")
    }
}