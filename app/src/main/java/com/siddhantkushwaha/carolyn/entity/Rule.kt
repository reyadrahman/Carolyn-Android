package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Rule : RealmObject() {

    @PrimaryKey
    var user2: String? = null
    var type: String? = null
}