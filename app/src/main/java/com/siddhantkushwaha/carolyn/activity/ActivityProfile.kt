package com.siddhantkushwaha.carolyn.activity

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.MessageType
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.entity.Rule
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_profile.*


class ActivityProfile : AppCompatActivity() {

    private lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val user2 = intent.getStringExtra("user2")
            ?: throw Exception("This activity requires user2 field in intent extras.")

        realm = RealmUtil.getCustomRealmInstance(this)

        val thread = realm.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
        val contact = realm.where(Contact::class.java).equalTo("number", user2).findFirst()

        // thread could be null if no message exists for this user
        // so fetch contact and check
        header_title.text = thread?.getDisplayName() ?: contact?.name ?: contact?.number ?: user2

        val rule = realm.where(Rule::class.java).equalTo("user2", user2).findFirst()
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
                var ruleA = realmA.where(Rule::class.java).equalTo("user2", user2).findFirst()
                if (checkedId == R.id.button_type_default) {
                    ruleA?.deleteFromRealm()
                } else {
                    if (ruleA == null)
                        ruleA = realmA.createObject(Rule::class.java, user2)
                            ?: throw Exception("Failed to create rule object.")
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