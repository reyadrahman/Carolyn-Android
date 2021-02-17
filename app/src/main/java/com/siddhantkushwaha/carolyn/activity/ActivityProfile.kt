package com.siddhantkushwaha.carolyn.activity

import android.os.Bundle
import android.util.Log
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums.MessageType
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_profile.*


class ActivityProfile : ActivityBase() {

    private lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val user2 = intent.getStringExtra("user2")
            ?: throw Exception("This activity requires user2 field in intent extras.")

        realm = RealmUtil.getCustomRealmInstance(this)

        val thread = DbHelper.getThreadObject(realm, user2)
        val contact = DbHelper.getContactObject(realm, user2)

        header_title.text = contact?.name ?: contact?.number ?: thread?.getDisplayName()

        val rule = DbHelper.getRuleObject(realm, user2)
        Log.d("ActivityProfile", "Rule fetched for $user2: ${rule?.type}")
        when (rule?.type) {
            MessageType.otp -> group_message_type.check(R.id.button_type_otp)
            MessageType.update -> group_message_type.check(R.id.button_type_update)
            MessageType.spam -> group_message_type.check(R.id.button_type_spam)
            MessageType.transaction -> group_message_type.check(R.id.button_type_transaction)
            null -> {
                if (rule == null)
                    group_message_type.check(R.id.button_type_default)
                else
                    group_message_type.check(R.id.button_type_personal)
            }
        }

        group_message_type.setOnCheckedChangeListener { _, checkedId ->
            realm.executeTransactionAsync { realmA ->
                var ruleA = DbHelper.getRuleObject(realm, user2)
                if (checkedId == R.id.button_type_default) {
                    ruleA?.deleteFromRealm()
                } else {
                    if (ruleA == null) ruleA = DbHelper.createRuleObject(realm, user2)
                    when (checkedId) {
                        R.id.button_type_personal -> ruleA.type = null
                        R.id.button_type_otp -> ruleA.type = MessageType.otp
                        R.id.button_type_transaction -> ruleA.type = MessageType.transaction
                        R.id.button_type_update -> ruleA.type = MessageType.update
                        R.id.button_type_spam -> ruleA.type = MessageType.spam
                    }
                    realmA.insertOrUpdate(ruleA)
                }
                Log.d("ActivityProfile", "Rule updated for $user2")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}