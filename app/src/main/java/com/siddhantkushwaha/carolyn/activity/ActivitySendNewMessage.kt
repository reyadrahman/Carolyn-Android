package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.ContactAdapter
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Contact
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_send_new_message.*


class ActivitySendNewMessage : AppCompatActivity() {

    private lateinit var realm: Realm
    private lateinit var contacts: RealmResults<Contact>
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contactsChangeListener: OrderedRealmCollectionChangeListener<RealmResults<Contact>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_new_message)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        realm = RealmUtil.getCustomRealmInstance(this)

        contacts = realm
            .where(Contact::class.java)
            .isNotNull("number")
            .sort("name", Sort.ASCENDING).findAllAsync()

        contactAdapter = ContactAdapter(this, contacts, true, clickListener = { _, th ->
            val intent = Intent(this, ActivityMessage::class.java)
            intent.putExtra("user2", th.number)
            startActivity(intent)
        })

        contactsChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            contactAdapter.notifyDataSetChanged()
        }


        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = false

        recycler_view_contacts.layoutManager = layoutManager
        recycler_view_contacts.adapter = contactAdapter

        contacts.addChangeListener(contactsChangeListener)
    }

    override fun onPause() {
        super.onPause()
        contacts.removeAllChangeListeners()
    }

    override fun onResume() {
        super.onResume()
        contactAdapter.notifyDataSetChanged()
        contacts.addChangeListener(contactsChangeListener)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

}