package com.siddhantkushwaha.carolyn.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class Contact : RealmObject() {

    @PrimaryKey
    var number: String? = null

    var name: String? = null
}