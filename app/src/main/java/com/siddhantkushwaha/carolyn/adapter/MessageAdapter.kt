package com.siddhantkushwaha.carolyn.adapter

import android.graphics.Color
import android.provider.Telephony
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.entity.Message
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter


class MessageAdapter(
    data: OrderedRealmCollection<Message>,
    autoUpdate: Boolean,
    private val longClickListener: (Message) -> Unit
) : RealmRecyclerViewAdapter<Message, RecyclerView.ViewHolder>(data, autoUpdate) {

    private val TYPE_MESSAGE_SENT = 1
    private val TYPE_MESSAGE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (data!![position].smsType != Telephony.Sms.MESSAGE_TYPE_INBOX) TYPE_MESSAGE_SENT else TYPE_MESSAGE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_MESSAGE_SENT -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_message_sent, parent, false)
                return SentMessageViewHolder(view)
            }
            TYPE_MESSAGE_RECEIVED -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_message_received, parent, false)
                return ReceivedMessageViewHolder(view)
            }
            else -> {
                throw Exception("Message Adapter Error: Message type not supported.")
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = data!![position]
        when (holder.itemViewType) {
            TYPE_MESSAGE_SENT -> {
                (holder as SentMessageViewHolder).bind(message)
            }
            TYPE_MESSAGE_RECEIVED -> {
                (holder as ReceivedMessageViewHolder).bind(message)
            }
            else -> {
                throw Exception("Message Adapter Error: Message type not supported.")
            }
        }
    }

    private inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            val messageBodyTextView = itemView.findViewById<TextView>(R.id.textview_message_text)
            val messageTimestampTextView =
                itemView.findViewById<TextView>(R.id.textview_message_timestamp)
            val sentViaSubscription =
                itemView.findViewById<TextView>(R.id.textview_message_subscription)
            val messageStatusImageView =
                itemView.findViewById<ImageView>(R.id.image_view_message_status)

            messageBodyTextView.movementMethod = LinkMovementMethod.getInstance()
            messageBodyTextView.text = message.body

            messageTimestampTextView.text =
                CommonUtil.formatTimestamp(message.timestamp!!, "dd/MM/yy hh:mm a")

            sentViaSubscription.text = message.user1 ?: ""

            when (message.status) {
                Enums.MessageStatus.delivered -> messageStatusImageView.setImageResource(R.drawable.message_status_delivered)
                Enums.MessageStatus.sent -> messageStatusImageView.setImageResource(R.drawable.message_status_sent)
                Enums.MessageStatus.pending -> messageStatusImageView.setImageResource(R.drawable.message_status_pending)
                Enums.MessageStatus.notSent -> messageStatusImageView.setImageResource(R.drawable.message_status_failed)
                else -> messageStatusImageView.setImageResource(R.drawable.message_status_sent)
            }

            messageBodyTextView.setOnClickListener {

            }

            messageBodyTextView.setOnLongClickListener {
                longClickListener(message)
                true
            }
        }
    }

    private inner class ReceivedMessageViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            val messageBodyTextView = itemView.findViewById<TextView>(R.id.textview_message_text)

            bindMessage(message)

            messageBodyTextView.setOnClickListener {

            }

            messageBodyTextView.setOnLongClickListener {
                longClickListener(message)
                true
            }
        }

        private fun bindMessage(message: Message) {
            val messageBodyTextView = itemView.findViewById<TextView>(R.id.textview_message_text)
            val messageTimestampTextView =
                itemView.findViewById<TextView>(R.id.textview_message_timestamp)
            val receivedOnNumberTextView =
                itemView.findViewById<TextView>(R.id.textview_message_subscription)
            val messageClassIcon = itemView.findViewById<ImageView>(R.id.image_view_message_class)

            messageBodyTextView.text = message.body
            messageBodyTextView.setTextColor(Color.WHITE)
            messageBodyTextView.backgroundTintList = null
            messageBodyTextView.movementMethod = LinkMovementMethod.getInstance()

            messageTimestampTextView.text =
                CommonUtil.formatTimestamp(message.timestamp!!, "dd/MM/yy hh:mm a")
            messageTimestampTextView.visibility = View.VISIBLE

            receivedOnNumberTextView.text = message.user1 ?: ""
            receivedOnNumberTextView.visibility = View.VISIBLE

            messageClassIcon.visibility = View.GONE
        }
    }
}