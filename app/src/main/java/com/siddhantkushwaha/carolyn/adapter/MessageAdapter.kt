package com.siddhantkushwaha.carolyn.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.MessageType
import com.siddhantkushwaha.carolyn.common.formatTimestamp
import com.siddhantkushwaha.carolyn.entity.Message
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter


class MessageAdapter(
    data: OrderedRealmCollection<Message>,
    autoUpdate: Boolean
) : RealmRecyclerViewAdapter<Message, RecyclerView.ViewHolder>(data, autoUpdate) {

    private val TYPE_MESSAGE_SENT = 1
    private val TYPE_MESSAGE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (data!![position].sent != false) TYPE_MESSAGE_SENT else TYPE_MESSAGE_RECEIVED
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

    private class SentMessageViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            val messageBodyTextView = itemView.findViewById<TextView>(R.id.textview_message_text)
            val messageTimestampTextView =
                itemView.findViewById<TextView>(R.id.textview_message_timestamp)
            val sentViaSubscription =
                itemView.findViewById<TextView>(R.id.textview_message_subscription)

            messageBodyTextView.text = message.body

            messageTimestampTextView.text = formatTimestamp(message.timestamp!!, "dd/MM/yy hh:mm a")

            sentViaSubscription.text = message.messageThread?.user1 ?: ""
        }
    }

    private class ReceivedMessageViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            val messageBodyTextView = itemView.findViewById<TextView>(R.id.textview_message_text)
            val messageClassIcon = itemView.findViewById<ImageView>(R.id.image_view_message_class)
            val messageTimestampTextView =
                itemView.findViewById<TextView>(R.id.textview_message_timestamp)
            val receivedOnNumberTextView =
                itemView.findViewById<TextView>(R.id.textview_message_subscription)

            messageBodyTextView.text = message.body
            when (message.type) {
                MessageType.otp -> messageClassIcon.setImageResource(R.drawable.icon_message_otp)
                MessageType.transaction -> messageClassIcon.setImageResource(R.drawable.icon_message_transaction)
                MessageType.update -> messageClassIcon.setImageResource(R.drawable.icon_message_update)
                MessageType.spam -> messageClassIcon.setImageResource(R.drawable.icon_message_spam)
                null -> messageClassIcon.setImageResource(R.drawable.icon_message_personal)
            }

            messageTimestampTextView.text = formatTimestamp(message.timestamp!!, "dd/MM/yy hh:mm a")

            receivedOnNumberTextView.text = message.messageThread?.user1 ?: ""
        }
    }
}