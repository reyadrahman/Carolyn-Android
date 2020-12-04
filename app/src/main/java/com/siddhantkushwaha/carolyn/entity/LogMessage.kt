package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class LogMessage: RealmObject() {

    @PrimaryKey
    var timestamp: Long? = null
    var log:String? = null
}