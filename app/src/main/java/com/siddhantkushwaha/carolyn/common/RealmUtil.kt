package com.siddhantkushwaha.carolyn.common

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration

object RealmUtil {
    fun getCustomRealmInstance(context: Context?): Realm {
        Realm.init(context)
        val config = RealmConfiguration.Builder()
            .name("raven_realm.realm")
            .deleteRealmIfMigrationNeeded()
            .build()
        return Realm.getInstance(config)
    }

    fun clearData(realm: Realm) {
        realm.executeTransaction { realmL: Realm -> realmL.deleteAll() }
    }
}