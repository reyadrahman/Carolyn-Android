package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.adapter.ThreadAdapter
import com.siddhantkushwaha.carolyn.ai.MessageClassifierTask
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.common.RequestCodes
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.index.Index
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_home.*


class ActivityHome : ActivityBase() {

    private lateinit var realm: Realm

    private lateinit var threads: RealmResults<MessageThread>
    private lateinit var threadsAdapter: ThreadAdapter
    private lateinit var threadsChangeListener: OrderedRealmCollectionChangeListener<RealmResults<MessageThread>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        FirebaseCrashlytics.getInstance()
            .setCustomKey("userEmail", mAuth.currentUser?.email ?: "null")

        requestPermissions(
            arrayOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_PHONE_STATE
            ), RequestCodes.REQUEST_PERMISSION_BASIC
        )

        realm = RealmUtil.getCustomRealmInstance(this)

        threads = realm.where(MessageThread::class.java).isNotNull("lastMessage")
            .sort("lastMessage.timestamp", Sort.DESCENDING).findAllAsync()

        threadsAdapter = ThreadAdapter(threads, true, itemClickListener = { _, th ->
            val messageActivityIntent = Intent(this, ActivityMessage::class.java)
            messageActivityIntent.putExtra("user2", th.user2)
            startActivity(messageActivityIntent)
        })

        threadsChangeListener = OrderedRealmCollectionChangeListener { _, _ ->

            addMessagesToClassifier()

            threadsAdapter.notifyDataSetChanged()
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = false

        recycler_view_threads.layoutManager = layoutManager
        recycler_view_threads.adapter = threadsAdapter

        // start indexing process
        Thread { Index(this).initIndex() }.start()
    }

    override fun onResume() {
        super.onResume()
        threads.addChangeListener(threadsChangeListener)
    }

    override fun onPause() {
        super.onPause()
        threads.removeAllChangeListeners()
    }

    private fun addMessagesToClassifier() {
        val messagesToClassify = ArrayList<Pair<String, String>>()
        threads.forEach { mt ->
            val mId = mt.lastMessage?.id
            val mBody = mt.lastMessage?.body
            val mType = mt.lastMessage?.type
            if (mId != null && mBody != null && mType == null) {
                messagesToClassify.add(Pair(mId, mBody))
            }
        }

        if (messagesToClassify.size > 0) {
            MessageClassifierTask(this, messagesToClassify).start()
        }
    }
}
