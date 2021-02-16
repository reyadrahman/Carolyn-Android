package com.siddhantkushwaha.carolyn.adapter

import android.content.res.ColorStateList
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
import com.siddhantkushwaha.carolyn.common.Enums.MessageType
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.entity.Message
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter


class MessageAdapter(
    data: OrderedRealmCollection<Message>,
    autoUpdate: Boolean,
    private val longClickListener: (Message) -> Unit,
    public var messageType: String?
) : RealmRecyclerViewAdapter<Message, RecyclerView.ViewHolder>(data, autoUpdate) {

    private val TYPE_MESSAGE_SENT = 1
    private val TYPE_MESSAGE_RECEIVED = 2

    private val isMessageShownMap: HashMap<String, Boolean> = HashMap()

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

            messageBodyTextView.movementMethod = LinkMovementMethod.getInstance()
            messageBodyTextView.text = message.body

            messageTimestampTextView.text =
                CommonUtil.formatTimestamp(message.timestamp!!, "dd/MM/yy hh:mm a")

            sentViaSubscription.text = message.user1 ?: ""

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

            val messageId = message.id

            // honor value already present in map to maintain current status
            val showMessage: Boolean =
                isMessageShownMap[messageId]
                    ?: (messageType == "view-all" || messageType == message.type)

            bindMessage(message, showMessage = showMessage)

            messageBodyTextView.setOnClickListener {
                if (messageId != null) {
                    val currentShownStatus = isMessageShownMap[messageId] ?: false
                    bindMessage(message, !currentShownStatus)
                }
            }

            messageBodyTextView.setOnLongClickListener {
                longClickListener(message)
                true
            }
        }

        private fun bindMessage(message: Message, showMessage: Boolean) {
            val messageId = message.id
            if (messageId != null) {
                isMessageShownMap[messageId] = showMessage
            }

            val messageBodyTextView = itemView.findViewById<TextView>(R.id.textview_message_text)
            val messageTimestampTextView =
                itemView.findViewById<TextView>(R.id.textview_message_timestamp)
            val receivedOnNumberTextView =
                itemView.findViewById<TextView>(R.id.textview_message_subscription)
            val messageClassIcon = itemView.findViewById<ImageView>(R.id.image_view_message_class)

            // change states to hide message
            if (!showMessage) {
                messageBodyTextView.text = "Hidden."
                messageBodyTextView.setTextColor(Color.GRAY)
                messageBodyTextView.backgroundTintList =
                    ColorStateList.valueOf(Color.argb(255, 33, 33, 33))

                messageTimestampTextView.visibility = View.GONE
                receivedOnNumberTextView.visibility = View.GONE

                when (message.type) {
                    MessageType.otp -> messageClassIcon.setImageResource(R.drawable.icon_message_otp)
                    MessageType.transaction -> messageClassIcon.setImageResource(R.drawable.icon_message_transaction)
                    MessageType.update -> messageClassIcon.setImageResource(R.drawable.icon_message_update)
                    MessageType.spam -> messageClassIcon.setImageResource(R.drawable.icon_message_spam)
                    null -> messageClassIcon.setImageResource(R.drawable.icon_message_personal)
                }
                messageClassIcon.visibility = View.VISIBLE
            }

            // change states to shoe message
            else {
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
}