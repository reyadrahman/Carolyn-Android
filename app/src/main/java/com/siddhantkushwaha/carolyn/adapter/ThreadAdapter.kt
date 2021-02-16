package com.siddhantkushwaha.carolyn.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.Enums.MessageStatus
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.entity.MessageThread
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter


class ThreadAdapter(
    val context: Context,
    data: OrderedRealmCollection<MessageThread>,
    autoUpdate: Boolean,
    private val clickListener: (View, MessageThread) -> Unit,
    public var messageType: String?
) : RealmRecyclerViewAdapter<MessageThread, RecyclerView.ViewHolder>(data, autoUpdate) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return ThreadViewHolder(context, view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val thread = data!![position]
        (holder as ThreadViewHolder).bind(thread)
    }

    private inner class ThreadViewHolder(val context: Context, itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(messageThread: MessageThread) {

            val threadImageView = itemView.findViewById<ImageView>(R.id.icon_user)
            val threadTitleTextView = itemView.findViewById<TextView>(R.id.text_thread)
            val lastMessageTextView = itemView.findViewById<TextView>(R.id.text_message)
            val timestampTextView = itemView.findViewById<TextView>(R.id.text_timestamp)

            threadTitleTextView.text = messageThread.getDisplayName()

            val lastMessage = messageThread.messages?.filter { m -> m.type == messageType }
                ?.maxBy { m -> m.timestamp ?: 0 }
            lastMessageTextView.text = lastMessage?.body ?: "No messages."
            if (lastMessage != null && lastMessage?.smsType != Telephony.Sms.MESSAGE_TYPE_INBOX) {
                lastMessageTextView.text = "You: ${lastMessageTextView.text}"
            }

            val timestamp = lastMessage?.timestamp
            if (timestamp == null) {
                timestampTextView.visibility = View.GONE
            } else {
                timestampTextView.visibility = View.VISIBLE
                timestampTextView.text = CommonUtil.getStringForTimestamp(timestamp)
            }

            itemView.setOnClickListener { view ->
                clickListener(view, messageThread)
            }

            val photoUri = messageThread.contact?.photoUri

            if (photoUri != null) {
                Glide
                    .with(context)
                    .load(photoUri)
                    .error(R.drawable.icon_user)
                    .circleCrop()
                    .into(threadImageView)
            } else {
                threadImageView.setImageResource(R.drawable.icon_user)
            }

            if (lastMessage?.smsType == Telephony.Sms.MESSAGE_TYPE_INBOX && lastMessage.status == MessageStatus.notRead) {
                threadTitleTextView.setTypeface(null, Typeface.BOLD)
                lastMessageTextView.setTypeface(null, Typeface.BOLD)
                lastMessageTextView.setTextColor(Color.WHITE)
            } else {
                threadTitleTextView.setTypeface(null, Typeface.NORMAL)
                lastMessageTextView.setTypeface(null, Typeface.NORMAL)
                lastMessageTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.secondary_text_dark
                    )
                )
            }
        }
    }
}