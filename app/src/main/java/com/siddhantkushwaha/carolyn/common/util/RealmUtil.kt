package com.siddhantkushwaha.carolyn.common.util

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration


object RealmUtil {

    public fun getCustomRealmInstance(context: Context): Realm {
        Realm.init(context)
        val config = RealmConfiguration.Builder()
            .name("realm_custom.realm")
            .deleteRealmIfMigrationNeeded()
            .build()
        return Realm.getInstance(config)
    }

    public fun clearData(realm: Realm) {
        realm.executeTransaction { realmL: Realm -> realmL.deleteAll() }
    }
}