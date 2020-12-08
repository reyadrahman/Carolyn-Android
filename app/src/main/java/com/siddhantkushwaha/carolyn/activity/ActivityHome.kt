package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.ThreadAdapter
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.index.IndexTask
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_home.*
import java.util.*


class ActivityHome : ActivityBase() {

    private lateinit var realm: Realm

    private lateinit var threads: RealmResults<MessageThread>
    private lateinit var threadsAdapter: ThreadAdapter
    private lateinit var threadsChangeListener: OrderedRealmCollectionChangeListener<RealmResults<MessageThread>>

    private var timer: Timer? = null
    private val delay = 1 * 1000L
    private val taskInterval = 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        checkPermissions()

        FirebaseCrashlytics.getInstance()
            .setCustomKey("userEmail", mAuth.currentUser?.email ?: "null")

        realm = RealmUtil.getCustomRealmInstance(this)

        when (savedInstanceState?.getInt("selected_view")) {
            R.id.personal ->
                threads = getPersonalThreadsQuery()
            R.id.otp ->
                threads = getThreadsForTypeQuery(MessageType.otp)
            R.id.transaction ->
                threads = getThreadsForTypeQuery(MessageType.transaction)
            R.id.update ->
                threads = getThreadsForTypeQuery(MessageType.update)
            R.id.spam ->
                threads = getThreadsForTypeQuery(MessageType.spam)
            null -> {
                threads = getThreadsForTypeQuery(MessageType.otp)
                bottom_nav_filter.selectedItemId = R.id.otp
            }
        }

        threadsAdapter = ThreadAdapter(this, threads, true, itemClickListener = { _, th ->
            val messageActivityIntent = Intent(this, ActivityMessage::class.java)
            messageActivityIntent.putExtra("user2", th.user2)
            startActivity(messageActivityIntent)
        })

        threadsChangeListener = OrderedRealmCollectionChangeListener { _, _ ->
            threadsAdapter.notifyDataSetChanged()
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = false

        recycler_view_threads.layoutManager = layoutManager
        recycler_view_threads.adapter = threadsAdapter

        bottom_nav_filter.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.personal -> {
                    threads = getPersonalThreadsQuery()
                    updateUI(1)
                }
                R.id.otp -> {
                    threads = getThreadsForTypeQuery(MessageType.otp)
                    updateUI(1)
                }
                R.id.transaction -> {
                    threads = getThreadsForTypeQuery(MessageType.transaction)
                    updateUI(1)
                }
                R.id.update -> {
                    threads = getThreadsForTypeQuery(MessageType.update)
                    updateUI(1)
                }
                R.id.spam -> {
                    threads = getThreadsForTypeQuery(MessageType.spam)
                    updateUI(1)
                }
            }
            true
        }

        button_send_message.setOnLongClickListener {

            // ****** Experimental *******
            // Testing delete SMS and OTP feature :D

            Toast.makeText(this, "Clearing all OTPs and Spam.", Toast.LENGTH_LONG).show()

            val clearAllMessages = Thread {

                // clear all
                val realmL = RealmUtil.getCustomRealmInstance(this)
                realmL.where(Message::class.java).findAll().forEach { m ->
                    val smsId = m.smsId
                    if ((m.type == "spam" || m.type == "otp") && smsId != null) {
                        deleteSMS(this, smsId)
                    }
                }
                realmL.close()

                // re-index
                IndexTask(this, false).start()
            }

            clearAllMessages.start()

            true
        }
    }

    override fun onResume() {
        super.onResume()

        updateUI(2)

        timer = Timer()
        val timerTask = object : TimerTask() {
            override fun run() {
                IndexTask(this@ActivityHome, false).start()
            }
        }
        timer?.scheduleAtFixedRate(timerTask, delay, taskInterval)
    }

    override fun onPause() {
        super.onPause()
        threads.removeAllChangeListeners()

        timer?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_view", bottom_nav_filter.selectedItemId)
    }

    private fun checkPermissions() {
        PermissionsUtil.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_PHONE_STATE
            ),
            RequestCodes.REQUEST_CODE_PERMISSION_BASIC,
            requestPermissionCallbacks
        ) {
            IndexTask(this@ActivityHome, false).start()
        }
    }

    private fun getPersonalThreadsQuery(): RealmResults<MessageThread> {
        return realm
            .where(MessageThread::class.java)
            .isNotNull("lastMessage")
            .isNull("lastMessage.type")
            .sort("lastMessage.timestamp", Sort.DESCENDING).findAllAsync()
    }


    private fun getThreadsForTypeQuery(type: String): RealmResults<MessageThread> {
        return realm
            .where(MessageThread::class.java)
            .isNotNull("lastMessage")
            .equalTo("lastMessage.type", type)
            .sort("lastMessage.timestamp", Sort.DESCENDING).findAllAsync()
    }

    private fun updateUI(flag: Int) {
        when (flag) {
            1 -> threadsAdapter.updateData(threads)
            2 -> threadsAdapter.notifyDataSetChanged()
        }
        threads.addChangeListener(threadsChangeListener)
    }
}
