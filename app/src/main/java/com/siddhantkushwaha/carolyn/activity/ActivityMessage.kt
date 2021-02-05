package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.common.Enums.MessageStatus
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TaskUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.entity.Contact
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

    private var initialSenderSimIndex = -1
    private val subscriptions: ArrayList<TelephonyUtil.SubscriptionInfo> = ArrayList()
    private val subColors = arrayOf(
        R.color.color3,
        R.color.color6,
        R.color.color7
    )

    private var timer: Timer? = null
    private val delay = 1 * 1000L
    private val taskInterval = 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        realm = RealmUtil.getCustomRealmInstance(this)

        val user2 = intent.getStringExtra("user2")
            ?: throw Exception("This activity requires user2 field in intent extras.")
        messages = realm.where(Message::class.java).equalTo("messageThread.user2", user2)
            .sort("timestamp", Sort.ASCENDING).findAllAsync()

        var threadL = realm.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
        if (threadL == null) {
            realm.executeTransaction { realmT ->
                threadL = realmT.createObject(MessageThread::class.java, user2)
                    ?: throw Exception("Realm Error")
                val contact = realmT.where(Contact::class.java).equalTo("number", user2).findFirst()
                threadL!!.contact = contact
                realmT.insertOrUpdate(threadL)
            }
        }

        thread = threadL!!

        header_title.text = thread.getDisplayName()

        messageAdapter = MessageAdapter(
            messages,
            true,
            clickListener = {

            },
            longClickListener = {

                // TODO ******* Experimental *********
                val smsId = it.smsId
                if (smsId != null) {
                    TelephonyUtil.deleteSMS(this, smsId)
                }

                IndexTask(this, false).start()
            }
        )

        messagesChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            messageAdapter.notifyDataSetChanged()

            val markAsRead = TaskUtil {
                val realmLocal = RealmUtil.getCustomRealmInstance(this)

                realmLocal.executeTransaction { realmT ->

                    val messagesForThread =
                        realmT.where(Message::class.java).equalTo("messageThread.user2", user2)
                            .findAll()

                    messagesForThread.forEach { message ->
                        if (message.smsType == Telephony.Sms.MESSAGE_TYPE_INBOX && message.status == MessageStatus.notRead) {
                            message.status = MessageStatus.read

                            // mark as read in db
                            realmT.insertOrUpdate(message)

                            // mark as read in os db
                            val smsId = message.smsId
                            if (TelephonyUtil.isDefaultSmsApp(this) && smsId != null) {
                                TelephonyUtil.markMessageAsRead(this, smsId)
                            }
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

        populateSimInfo()

        header_title.setOnClickListener {
            val intent = Intent(this, ActivityProfile::class.java)
            intent.putExtra("user2", user2)
            startActivity(intent)
        }

        button_send_message.setOnClickListener {
            /*val messageText = edit_text_message.text.toString()
            sendMessage(messageText)
            edit_text_message.setText("")*/
        }
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

    private fun populateSimInfo() {
        button_sim_picker.setOnClickListener {
            initialSenderSimIndex += 1
            initialSenderSimIndex %= subscriptions.size
            updateEditTextHint(initialSenderSimIndex)
        }

        // Even if no sim card available, user should be allowed to see the messages
        // instead of throwing an exception
        val subscriptionSet = TelephonyUtil.getSubscriptions(this)
        if (subscriptionSet != null) {
            subscriptions.addAll(subscriptionSet.values)
        }

        initialSenderSimIndex =
            if (subscriptions.size > 0) {
                1
            } else
                -1

        updateEditTextHint(initialSenderSimIndex)
    }

    private fun updateEditTextHint(idx: Int) {
        if (idx < 0 || idx >= subscriptions.size)
            return

        val info = subscriptions[idx]

        val carrierName = info.carrierName.toLowerCase().capitalize()

        edit_text_message.hint =
            "Send via $carrierName ${info.number}"

        button_sim_picker.backgroundTintList =
            ContextCompat.getColorStateList(this, subColors[initialSenderSimIndex])
    }

    private fun sendMessage(message: String) {
        if (initialSenderSimIndex < 0) {
            return
        }

        val subId = subscriptions[initialSenderSimIndex].subId

        val scAddress = null

        val sentIntent = null
        val delIntent = null

        val smsManager = SmsManager.getSmsManagerForSubscriptionId(subId)
        smsManager.sendTextMessage(
            thread.user2, scAddress,
            message, sentIntent, delIntent
        )
    }
}