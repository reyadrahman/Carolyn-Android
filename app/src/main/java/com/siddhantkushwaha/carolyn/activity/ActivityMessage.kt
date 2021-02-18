package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums.MessageStatus
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TaskUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.index.IndexTask
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_message.*
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList


class ActivityMessage : ActivityBase() {

    private lateinit var realm: Realm

    private lateinit var messages: RealmResults<Message>
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messagesChangeListener: OrderedRealmCollectionChangeListener<RealmResults<Message>>

    private lateinit var user2: String
    private var showMessageType: String? = null

    private var senderSimIndex = -1
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

        user2 = intent.getStringExtra("user2")
            ?: throw Exception("This activity requires user2 field in intent extras.")
        showMessageType = intent.getStringExtra("view-type")

        messages = realm.where(Message::class.java).equalTo("thread.user2", user2)
            .sort("timestamp", Sort.ASCENDING).findAllAsync()

        realm.executeTransaction { realmT ->
            val thread = DbHelper.getThreadObject(realmT, user2)
            val contact = thread?.contact ?: DbHelper.getContactObject(realmT, user2)
            header_title.text = contact?.name ?: contact?.number ?: thread?.getDisplayName()
        }

        messageAdapter = MessageAdapter(
            messages,
            true,
            longClickListener = {
                // TODO, TEST FEATURE, ******* Experimental *********
                val smsId = it.smsId
                if (smsId != null) {
                    TelephonyUtil.deleteSMS(this, smsId)
                }

                val messageId = it.id
                if (messageId != null) {
                    realm.executeTransactionAsync { rt ->
                        DbHelper.getMessageObject(rt, messageId)?.deleteFromRealm()
                    }
                }
            },
            messageType = showMessageType
        )

        messagesChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            messageAdapter.notifyDataSetChanged()

            val markAsRead = TaskUtil {
                val realmLocal = RealmUtil.getCustomRealmInstance(this)

                realmLocal.executeTransaction { realmT ->

                    val messagesForThread =
                        realmT.where(Message::class.java).equalTo("thread.user2", user2)
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
            pickDataFromUIAndSend()
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
            senderSimIndex += 1
            senderSimIndex %= subscriptions.size
            updateSendViaSimUI()
        }

        // Even if no sim card available, user should be allowed to see the messages
        // instead of throwing an exception
        val subscriptionSet = TelephonyUtil.getSubscriptions(this)
        if (subscriptionSet != null) {
            subscriptions.addAll(subscriptionSet.values)
        }

        senderSimIndex = if (subscriptions.size > 0) 1 else -1

        val defaultSmsSimSubId = TelephonyUtil.getDefaultSMSSubscriptionId()
        val defaultSimIndex = subscriptions.indexOfFirst { it.subId == defaultSmsSimSubId }
        if (defaultSimIndex > -1) {
            senderSimIndex = defaultSimIndex
        }

        updateSendViaSimUI()
    }

    private fun updateSendViaSimUI() {
        if (senderSimIndex < 0 || senderSimIndex >= subscriptions.size)
            return

        val info = subscriptions[senderSimIndex]

        edit_text_message.hint = "Send via ${info.carrierDisplayName} ${info.number}"
        button_sim_picker.backgroundTintList =
            ContextCompat.getColorStateList(this, subColors[senderSimIndex])
    }


    private fun pushMessageToUI(user1: String, messageTimestamp: Long, messageBody: String) {
        val messageId = DbHelper.getMessageId(messageTimestamp, messageBody)
        realm.executeTransaction { realmT ->
            var message = realmT.where(Message::class.java).equalTo("id", messageId).findFirst()
            if (message == null) {
                message = DbHelper.createMessageObject(realm, messageId)
            }
            message.user1 = user1
            message.thread = DbHelper.getOrCreateThreadObject(realmT, user2)
            message.timestamp = messageTimestamp
            message.body = messageBody
            message.status = MessageStatus.pending
            realmT.insertOrUpdate(message)
        }
    }

    private fun sendMessage(subId: Int, user1: String, messageTimestamp: Long, message: String) {

        pushMessageToUI(user1, messageTimestamp, message)

        // val messageParts = message.chunked(150)
        // val numParts = messageParts.size
//
//        val scAddress = if (user1 == "UNKNOWN") null else user1
//        val sentIntent = null
//        val delIntent = null

//        val smsManager = SmsManager.getSmsManagerForSubscriptionId(subId)
//        smsManager.sendTextMessage(
//            user2,
//            scAddress,
//            message,
//            sentIntent,
//            delIntent
//        )
    }

    private fun pickDataFromUIAndSend() {
        if (senderSimIndex < 0) {
            return
        }

        val subId = subscriptions[senderSimIndex].subId

        val user1 = subscriptions[senderSimIndex].number

        val messageTimestamp = Instant.now().toEpochMilli()

        val message = edit_text_message.text.toString()
        edit_text_message.setText("")

        sendMessage(subId, user1, messageTimestamp, message)
    }
}