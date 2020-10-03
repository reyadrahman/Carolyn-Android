package com.siddhantkushwaha.carolyn.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.entity.MessageThread
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class ThreadAdapter(
    data: OrderedRealmCollection<MessageThread>,
    autoUpdate: Boolean,
    private val itemClickListener: (View, MessageThread) -> Unit
) : RealmRecyclerViewAdapter<MessageThread, RecyclerView.ViewHolder>(data, autoUpdate) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return ThreadViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val thread = data!![position]
        (holder as ThreadViewHolder).bind(thread, itemClickListener)
    }

    private class ThreadViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(messageThread: MessageThread, itemClickListener: (View, MessageThread) -> Unit) {

            val threadImageView = itemView.findViewById<ImageView>(R.id.icon_user)
            val threadTitleTextView = itemView.findViewById<TextView>(R.id.text_thread)
            val lastMessageTextView = itemView.findViewById<TextView>(R.id.text_message)
            val timestampTextView = itemView.findViewById<TextView>(R.id.text_timestamp)
            val threadClassImageView = itemView.findViewById<ImageView>(R.id.image_view_thread_class)

            threadTitleTextView.text = messageThread.user2DisplayName
            lastMessageTextView.text = messageThread.lastMessage?.body ?: "No messages."

            val timeZoneId = TimeZone.getDefault().toZoneId()
            val timestamp = messageThread.lastMessage?.timestamp
            if (timestamp == null) {
                timestampTextView.visibility = View.GONE
            } else {
                val date = Instant.ofEpochMilli(timestamp).atZone(timeZoneId)
                val formattedDate = DateTimeFormatter.ofPattern("dd/mm/yy hh:mm a").format(date)
                timestampTextView.visibility = View.VISIBLE
                timestampTextView.text = formattedDate
            }

            val messageType = messageThread.lastMessage?.type
            if (messageType == null) {
                threadClassImageView.visibility = View.GONE
            } else {
                threadClassImageView.visibility = View.VISIBLE
                when (messageType) {
                    "otp" -> threadClassImageView.setImageResource(R.drawable.icon_message_otp)
                    "transaction" -> threadClassImageView.setImageResource(R.drawable.icon_message_transaction)
                    "update" -> threadClassImageView.setImageResource(R.drawable.icon_message_update)
                    "spam" -> threadClassImageView.setImageResource(R.drawable.icon_message_spam)
                    else -> threadClassImageView.visibility = View.GONE
                }
            }

            itemView.setOnClickListener { view ->
                itemClickListener(view, messageThread)
            }
        }
    }
}