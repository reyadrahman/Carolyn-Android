package com.siddhantkushwaha.carolyn.activity


import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Message
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_message.*

class ActivityMessage : ActivityBase() {

    private lateinit var realm: Realm

    private lateinit var messages: RealmResults<Message>
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messagesChangeListener: OrderedRealmCollectionChangeListener<RealmResults<Message>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        setActionBar(toolbar)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        realm = RealmUtil.getCustomRealmInstance(this)

        val user2 = "thread_id"
        messages = realm.where(Message::class.java).equalTo("messageThread.user2", user2)
            .sort("timestamp", Sort.ASCENDING).findAllAsync()

        messageAdapter = MessageAdapter(messages, true)

        messagesChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            messageAdapter.notifyDataSetChanged()
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true

        recycler_view_messages.layoutManager = layoutManager
        recycler_view_messages.adapter = messageAdapter
    }

    override fun onStart() {
        super.onStart()
        messages.addChangeListener(messagesChangeListener)
    }

    override fun onPause() {
        super.onPause()
        messages.removeAllChangeListeners()
    }
}