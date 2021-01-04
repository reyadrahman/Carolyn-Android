package com.siddhantkushwaha.carolyn.entity

import android.content.Context
import com.siddhantkushwaha.carolyn.common.RealmUtil
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class GlobalParam : RealmObject() {

    companion object {
        fun setGlobalParam(context: Context, attrName: String, attrVal: String) {
            val th = Thread {
                val realm = RealmUtil.getCustomRealmInstance(context)

                realm.executeTransaction { realmT ->
                    var rule =
                        realmT.where(GlobalParam::class.java).equalTo("attrName", attrName)
                            .findFirst()
                    if (rule == null) {
                        rule = realmT.createObject(GlobalParam::class.java, attrName)
                            ?: throw Exception("Couldn't create GlobalParam object.")
                    }
                    rule.attrVal = attrVal
                    realmT.insertOrUpdate(rule)
                }

                realm.close()
            }
            th.start()
        }

        fun getGlobalParam(context: Context, attrName: String): String? {
            val realm = RealmUtil.getCustomRealmInstance(context)

            val rule = realm.where(GlobalParam::class.java)
                .equalTo("attrName", attrName).findFirst()
            val attrVal = rule?.attrVal

            realm.close()
            return attrVal
        }
    }

    @PrimaryKey
    var attrName: String? = null

    var attrVal: String? = null
}