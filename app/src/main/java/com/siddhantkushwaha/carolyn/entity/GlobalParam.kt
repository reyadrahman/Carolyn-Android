package com.siddhantkushwaha.carolyn.entity

import android.content.Context
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey


open class GlobalParam : RealmObject() {

    companion object {
        fun setGlobalParam(context: Context, attrName: String, attrVal: String) {
            val th = Thread {
                val realm = RealmUtil.getCustomRealmInstance(context)

                realm.executeTransaction { realmT ->
                    val rule = DbHelper.getOrCreateGlobalParamObject(realmT, attrName)
                    rule.attrVal = attrVal
                    realmT.insertOrUpdate(rule)
                }

                realm.close()
            }
            th.start()
        }

        fun getGlobalParam(context: Context, attrName: String): String? {
            val realm = RealmUtil.getCustomRealmInstance(context)

            val globalParam: GlobalParam? = DbHelper.getGlobalParamObject(realm, attrName)
            val attrVal = globalParam?.attrVal

            realm.close()
            return attrVal
        }
    }

    @PrimaryKey
    var attrName: String? = null

    var attrVal: String? = null
}