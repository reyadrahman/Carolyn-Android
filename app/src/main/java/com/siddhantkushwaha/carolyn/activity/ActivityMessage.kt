package com.siddhantkushwaha.carolyn.activity

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.MessageAdapter
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums.MessageStatus
import com.siddhantkushwaha.carolyn.common.Helper
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
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
import java.net.URLDecoder
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

        user2 =
            if (intent.action == Intent.ACTION_SENDTO) {
                // when activity opened via other apps
                val decodedData = URLDecoder.decode(intent.dataString, "UTF-8")
                val user2NotNormalized = decodedData
                    .split(":").last()
                    .replace(" ", "")
                CommonUtil.normalizePhoneNumber(user2NotNormalized)
                    ?: throw Exception("Invalid user2 field was sent intent data: $user2NotNormalized")
            } else {
                // when activity opened via app
                intent.getStringExtra("user2")
                    ?: throw Exception("This activity requires user2 field in intent extras.")
            }

        realm = RealmUtil.getCustomRealmInstance(this)

        showMessageType = intent.getStringExtra("view-type")

        messages = when (showMessageType) {
            "all" -> {
                realm.where(Message::class.java).equalTo("thread.user2", user2)
                    .sort("timestamp", Sort.ASCENDING).findAllAsync()
            }
            null -> {
                realm.where(Message::class.java).equalTo("thread.user2", user2)
                    .isNull("type")
                    .sort("timestamp", Sort.ASCENDING).findAllAsync()
            }
            else -> {
                realm.where(Message::class.java).equalTo("thread.user2", user2)
                    .equalTo("type", showMessageType)
                    .sort("timestamp", Sort.ASCENDING).findAllAsync()
            }
        }

        realm.executeTransaction { realmT ->
            val thread = DbHelper.getThreadObject(realmT, user2)
            val contact = thread?.contact ?: DbHelper.getContactObject(realmT, user2)
            header_title.text =
                contact?.name ?: contact?.number ?: thread?.getDisplayName() ?: user2
        }

        messageAdapter = MessageAdapter(
            messages,
            true,
            longClickListener = {
                deleteMessage(realm, it.smsId, it.id)
            }
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

        toggleSenderDoesNotSupportReplies()
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

        dismissNotifications()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_message, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_all -> {
                deleteAll()
            }
        }

        // handle back button case separately
        return super.onOptionsItemSelected(item)
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
        smsId: Int,
        user1: String,
        messageTimestamp: Long,
        messageBody: String
    ) {
        // scroll to end
        // notifyAdapterAndUpdateScrollStatus will do the job
        scrollToEnd()

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
            message.smsId = smsId
            message.smsType = Telephony.Sms.MESSAGE_TYPE_OUTBOX

            realmT.insertOrUpdate(message)
        }
        realmInThread.close()
    }

    private fun pushMessageToSMSProvider(
        subId: Int,
        messageTimestamp: Long,
        messageBody: String
    ): Int {
        val smsMessage = TelephonyUtil.SMSMessage(
            id = 0,
            threadId = 0,
            user2 = user2,
            body = messageBody,
            timestamp = messageTimestamp,
            type = Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            subId = subId,
            isRead = false
        )
        return TelephonyUtil.saveSms(this, smsMessage)
    }

    private fun sendMessage(subId: Int, user1: String, messageTimestamp: Long, message: String) {

        SMSStatusReceiver.registerReceiver(this)

        // Send messages
        val messageId = DbHelper.getMessageId(messageTimestamp, message)

        Log.d(tag, "Sending message $messageId $message $messageTimestamp")

        val smsId = pushMessageToSMSProvider(subId, messageTimestamp, message)
        pushMessageToUI(messageId, smsId, user1, messageTimestamp, message)

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
            Helper.showStatus(root, "Set as default SMS app to send messages.") { snackbar ->
                snackbar.setAction("MAKE DEFAULT") {
                    Helper.setAsDefault(this, root) {
                        when (it) {
                            // no need to handle other cases here
                            0 -> Helper.showStatus(root, "Not changed to default SMS app.")
                        }
                    }
                }
                snackbar.setActionTextColor(Color.WHITE)
            }
            return
        }

        if (senderSimIndex < 0) {
            return
        }

        val subId = subscriptions[senderSimIndex].subId
        val user1 = subscriptions[senderSimIndex].number
        val message = edit_text_message.text.toString()
        val messageTimestamp = Instant.now().toEpochMilli()

        edit_text_message.setText("")

        val th = Thread {
            sendMessage(subId, user1, messageTimestamp, message)
        }
        th.start()
    }

    private fun notifyAdapterAndUpdateScrollStatus() {
        messageAdapter.notifyDataSetChanged()
        if (isRecyclerViewScrolledToEnd)
            scrollToEnd()
    }

    private fun scrollToEnd() {
        runOnUiThread {
            recycler_view_messages.scrollToPosition(messages.size - 1)
        }
    }

    private fun dismissNotifications() {
        val ns = NotificationSender(this)
        ns.cancelNotificationByTag(user2)
    }

    private fun deleteMessage(realm: Realm, smsId: Int?, messageId: String?) {
        var deleted = true
        if (smsId != null && smsId > 0) {
            deleted = TelephonyUtil.deleteSMS(this, smsId)
        }
        if (messageId != null && deleted) {
            // single sync op on UI thread should be OK
            realm.executeTransaction { rt ->
                DbHelper.getMessageObject(rt, messageId)?.deleteFromRealm()
            }
        }
    }

    private fun deleteAll() {
        val messagesL = realm.copyFromRealm(messages)
        val clearAllTask = Thread {
            val realm = RealmUtil.getCustomRealmInstance(this)
            messagesL.forEach {
                deleteMessage(realm, it.smsId, it.id)
            }
            realm.close()
        }
        clearAllTask.start()
    }

    private fun toggleSenderDoesNotSupportReplies() {
        val enableSetting = DbHelper.getSenderSupportsReplyRule(this)

        val hide = if (enableSetting) {
            val isValidPhoneNumber = CommonUtil.isValidPhoneNumber(user2)
            !isValidPhoneNumber
        } else
            false

        if (hide) {
            section_send_message.visibility = View.GONE
            section_reply_not_supported.visibility = View.VISIBLE
        } else {
            section_send_message.visibility = View.VISIBLE
            section_reply_not_supported.visibility = View.GONE
        }
    }
}