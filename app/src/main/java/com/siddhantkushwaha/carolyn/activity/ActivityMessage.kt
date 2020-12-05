package com.siddhantkushwaha.carolyn.activity

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.common.MessageStatus
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.common.Task
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.index.IndexTask
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_message.*
import java.util.*


class ActivityMessage : ActivityBase() {

    private lateinit var realm: Realm

    private lateinit var messages: RealmResults<Message>
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messagesChangeListener: OrderedRealmCollectionChangeListener<RealmResults<Message>>

    private lateinit var thread: MessageThread

    private var timer: Timer? = null
    private val delay = 1 * 1000L
    private val taskInterval = 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        realm = RealmUtil.getCustomRealmInstance(this)

        val user2 = intent.getStringExtra("user2")!!
        messages = realm.where(Message::class.java).equalTo("messageThread.user2", user2)
            .sort("timestamp", Sort.ASCENDING).findAllAsync()

        thread = realm.where(MessageThread::class.java).equalTo("user2", user2).findFirst()!!

        header_title.text = thread.getDisplayName()

        messageAdapter = MessageAdapter(messages, true)

        messagesChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            messageAdapter.notifyDataSetChanged()


            val markAsRead = Task {
                val realmLocal = RealmUtil.getCustomRealmInstance(this)

                realmLocal.executeTransaction { realmT ->
                    val messagesForThread =
                        realmT.where(Message::class.java).equalTo("messageThread.user2", user2)
                            .findAll()
                    messagesForThread.forEach { message ->
                        if (message.sent == false && message.status == MessageStatus.notRead) {
                            message.status = MessageStatus.read
                            realmT.insertOrUpdate(message)
                        }
                    }
                }

                realmLocal.close()
            }
            markAsRead.start()
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true

        recycler_view_messages.layoutManager = layoutManager
        recycler_view_messages.adapter = messageAdapter
    }

    override fun onResume() {
        super.onResume()

        // Update adapter for changes that were made while activity was paused
        messageAdapter.notifyDataSetChanged()
        messages.addChangeListener(messagesChangeListener)

        timer = Timer()
        val timerTask = object : TimerTask() {
            override fun run() {
                IndexTask(this@ActivityMessage, false).start()
            }
        }
        timer?.scheduleAtFixedRate(timerTask, delay, taskInterval)
    }

    override fun onPause() {
        super.onPause()
        messages.removeAllChangeListeners()

        timer?.cancel()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}