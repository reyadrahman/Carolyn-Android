package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class GlobalParam:RealmObject() {

    @PrimaryKey
    var attrName: String? = null

    var attrVal: String? = null
}