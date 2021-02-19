package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.ContactAdapter
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Contact
import io.realm.*
import kotlinx.android.synthetic.main.activity_send_new_message.*


class ActivitySendNewMessage : ActivityBase() {

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

        contacts = search("")

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

        search_contacts.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                contacts = search(query)
                updateUI(1)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                contacts = search(newText)
                updateUI(1)
                return true
            }
        })

        search_contacts.setOnCloseListener {
            contacts = search("")
            updateUI(1)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        contacts.removeAllChangeListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUI(2)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun updateUI(flag: Int) {
        when (flag) {
            1 -> contactAdapter.updateData(contacts)
            2 -> contactAdapter.notifyDataSetChanged()
        }
        contacts.addChangeListener(contactsChangeListener)
    }

    private fun search(query: String): RealmResults<Contact> {
        if (query == "") {
            return realm.where(Contact::class.java).isNotNull("number")
                .sort("name", Sort.ASCENDING)
                .findAllAsync()
        }

        return realm.where(Contact::class.java).isNotNull("number")
            .contains("name", query, Case.INSENSITIVE)
            .or()
            .contains("number", query, Case.INSENSITIVE)
            .sort("name", Sort.ASCENDING)
            .findAllAsync()
    }
}