package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey


open class Contact : RealmObject() {

    @PrimaryKey
    var number: String? = null

    var contactId:Long? = null
    var name: String? = null
}