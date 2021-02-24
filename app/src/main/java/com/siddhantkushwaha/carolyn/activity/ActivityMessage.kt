package com.siddhantkushwaha.carolyn.activity

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums.MessageStatus
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.notification.NotificationSender
import com.siddhantkushwaha.carolyn.receiver.SMSStatusReceiver
import com.siddhantkushwaha.carolyn.tasks.IndexTask
import com.siddhantkushwaha.carolyn.tasks.MarkMessagesAsReadTask
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_message.*
import kotlinx.android.synthetic.main.activity_message.header_title
import kotlinx.android.synthetic.main.activity_message.root
import kotlinx.android.synthetic.main.activity_message.toolbar
import kotlinx.android.synthetic.main.activity_settings.*
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random


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

    private var isRecyclerViewScrolledToEnd = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        realm = RealmUtil.getCustomRealmInstance(this)

        user2 = intent.getStringExtra("user2")
            ?: throw Exception("This activity requires user2 field in intent extras.")
        showMessageType = intent.getStringExtra("view-type")

        dismissNotifications()

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
                var deleted = true

                val smsId = it.smsId
                if (smsId != null && smsId > 0) {
                    deleted = TelephonyUtil.deleteSMS(this, smsId)
                }
                val messageId = it.id
                if (messageId != null && deleted) {
                    realm.executeTransactionAsync { rt ->
                        DbHelper.getMessageObject(rt, messageId)?.deleteFromRealm()
                    }
                }
            },
            messageType = showMessageType
        )

        messagesChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            notifyAdapterAndUpdateScrollStatus()

            val markAsRead = MarkMessagesAsReadTask {
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

        recycler_view_messages.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            val canScrollDown = v.canScrollVertically(1)

            Log.d(tag, "Recycler view scrolling - $dy $canScrollDown")
            isRecyclerViewScrolledToEnd = !canScrollDown
        }

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
        notifyAdapterAndUpdateScrollStatus()
        messages.addChangeListener(messagesChangeListener)

        timer = Timer()
        val timerTask = object : TimerTask() {
            override fun run() {
                IndexTask(this@ActivityMessage).start()
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

    private fun pushMessageToUI(
        messageId: String,
        user1: String,
        messageTimestamp: Long,
        messageBody: String
    ) {
        val realmInThread = RealmUtil.getCustomRealmInstance(this)
        realmInThread.executeTransaction { realmT ->
            var message = DbHelper.getMessageObject(realmT, messageId)
            if (message == null) {
                message = DbHelper.createMessageObject(realmT, messageId)
            }
            message.user1 = user1

            val thread = DbHelper.getOrCreateThreadObject(realmT, user2)
            thread.contact = DbHelper.getContactObject(realmT, user2)
            if (thread.timestamp ?: 0 < messageTimestamp)
                thread.timestamp = messageTimestamp
            message.thread = thread

            message.timestamp = messageTimestamp
            message.body = messageBody
            message.status = MessageStatus.pending

            realmT.insertOrUpdate(message)
        }
        realmInThread.close()
    }

    private fun sendMessage(subId: Int, user1: String, messageTimestamp: Long, message: String) {

        SMSStatusReceiver.registerReceiver(this)

        // Send messages
        val messageId = DbHelper.getMessageId(messageTimestamp, message)

        Log.d(tag, "Sending message $messageId $message $messageTimestamp")

        pushMessageToUI(messageId, user1, messageTimestamp, message)

        val messageParts = ArrayList(message.chunked(100))
        val numParts = messageParts.size

        val scAddress = if (user1 == "UNKNOWN") null else user1
        val sentIntents = ArrayList(List<PendingIntent>(numParts) { index ->
            val intent = Intent(getString(R.string.action_message_status_sent))
            intent.putExtra("messageId", messageId)
            intent.putExtra("partIndex", index)
            intent.putExtra("numParts", numParts)
            intent.putExtra("subId", subId)

            val reqCode = Random.nextInt()
            PendingIntent.getBroadcast(this, reqCode, intent, 0)
        })
        val delIntents = ArrayList(List<PendingIntent>(numParts) { index ->
            val intent = Intent(getString(R.string.action_message_status_delivered))
            intent.putExtra("messageId", messageId)
            intent.putExtra("partIndex", index)
            intent.putExtra("numParts", numParts)
            intent.putExtra("subId", subId)

            val reqCode = Random.nextInt()
            PendingIntent.getBroadcast(this, reqCode, intent, 0)
        })

        val smsManager = SmsManager.getSmsManagerForSubscriptionId(subId)
        smsManager.sendMultipartTextMessage(
            user2,
            scAddress,
            messageParts,
            sentIntents,
            delIntents
        )
    }

    private fun pickDataFromUIAndSend() {

        if (!TelephonyUtil.isDefaultSmsApp(this)) {
            showStatus("Set Carolyn as Default SMS app from Dashboard.")
            return
        }

        if (senderSimIndex < 0) {
            return
        }

        val subId = subscriptions[senderSimIndex].subId
        val user1 = subscriptions[senderSimIndex].number
        val messageTimestamp = Instant.now().toEpochMilli()
        val message = edit_text_message.text.toString()

        edit_text_message.setText("")

        val th = Thread {
            sendMessage(subId, user1, messageTimestamp, message)
        }
        th.start()
    }

    private fun showStatus(message: String, modifySnackbar: ((Snackbar) -> Unit)? = null) {
        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
        modifySnackbar?.invoke(snackbar)
        snackbar.show()
    }

    private fun notifyAdapterAndUpdateScrollStatus() {
        messageAdapter.notifyDataSetChanged()
        if (isRecyclerViewScrolledToEnd) {
            recycler_view_messages.scrollToPosition(messages.size - 1)
        }
    }

    private fun dismissNotifications() {
        val ns = NotificationSender(this)
        ns.cancelNotificationByTag(user2)
    }
}