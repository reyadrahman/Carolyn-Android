package com.siddhantkushwaha.carolyn.activity

import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.ai.MessageClassifierTask
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.index.IndexTask
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_message.*
import java.util.*
import kotlin.collections.ArrayList

class ActivityMessage : ActivityBase() {

    private lateinit var realm: Realm

    private lateinit var messages: RealmResults<Message>
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messagesChangeListener: OrderedRealmCollectionChangeListener<RealmResults<Message>>

    private lateinit var thread: MessageThread

    private var timer: Timer? = null
    private var timerTaskClassify: TimerTask? = null
    private var timerTaskIndexing: TimerTask? = null

    private val taskInterval = 15 * 1000L

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

        header_title.text = thread.user2DisplayName

        messageAdapter = MessageAdapter(messages, true)

        messagesChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            messageAdapter.notifyDataSetChanged()
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
        timerTaskClassify = object : TimerTask() {
            override fun run() {
                Log.d(TAG, "MessageClassifierTask called.")
                runOnUiThread {
                    addMessagesToClassifier()
                }
            }
        }
        timerTaskIndexing = object : TimerTask() {
            override fun run() {
                Log.d(TAG, "IndexTask called.")
                IndexTask(this@ActivityMessage).start()
            }
        }
        timer!!.scheduleAtFixedRate(timerTaskIndexing!!, taskInterval, taskInterval)
        timer!!.scheduleAtFixedRate(timerTaskClassify!!, taskInterval + 5, taskInterval)
    }

    override fun onPause() {
        super.onPause()
        messages.removeAllChangeListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun addMessagesToClassifier() {
        val messagesToClassify = ArrayList<Pair<String, String>>()
        messages.forEach { ml ->
            val mId = ml.id
            val mBody = ml.body
            val mType = ml.type
            val mSent = ml.sent
            if (thread.classifyThread() && mId != null && mBody != null && mType == null && mSent == false)
                messagesToClassify.add(Pair(mId, mBody))
        }

        if (messagesToClassify.size > 0) {
            MessageClassifierTask(this, messagesToClassify).start()
        }
    }
}