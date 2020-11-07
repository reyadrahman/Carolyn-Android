package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.ThreadAdapter
import com.siddhantkushwaha.carolyn.common.PermissionsUtil
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.common.RequestCodes
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
    private var timerTask: TimerTask? = null

    private val taskInterval = 15 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        checkPermissions()

        FirebaseCrashlytics.getInstance()
            .setCustomKey("userEmail", mAuth.currentUser?.email ?: "null")

        realm = RealmUtil.getCustomRealmInstance(this)

        threads = realm.where(MessageThread::class.java).isNotNull("lastMessage")
            .sort("lastMessage.timestamp", Sort.DESCENDING).findAllAsync()

        threadsAdapter = ThreadAdapter(threads, true, itemClickListener = { _, th ->
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
    }

    override fun onResume() {
        super.onResume()

        // Update adapter for changes that were made while activity was paused
        threadsAdapter.notifyDataSetChanged()
        threads.addChangeListener(threadsChangeListener)

        timer = Timer()

        timerTask = object : TimerTask() {
            override fun run() {
                IndexTask(this@ActivityHome).start()
            }
        }
        timer!!.scheduleAtFixedRate(timerTask!!, 0, taskInterval)
    }

    override fun onPause() {
        super.onPause()
        threads.removeAllChangeListeners()

        timer!!.cancel()
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
            IndexTask(this@ActivityHome).start()
        }
    }
}
