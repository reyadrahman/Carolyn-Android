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

class ThreadAdapter(
    data: OrderedRealmCollection<MessageThread>,
    autoUpdate: Boolean
) : RealmRecyclerViewAdapter<MessageThread, RecyclerView.ViewHolder>(data, autoUpdate) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return ThreadViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val thread = data!![position]
        (holder as ThreadViewHolder).bind(thread)
    }

    private class ThreadViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(messageThread: MessageThread) {

            val threadImageView = itemView.findViewById<ImageView>(R.id.icon_user)
            val threadTitleTextView = itemView.findViewById<TextView>(R.id.text_thread)
            val lastMessageTextView = itemView.findViewById<TextView>(R.id.text_message)
            val timestampTextView = itemView.findViewById<TextView>(R.id.text_timestamp)

            threadTitleTextView.text = messageThread.user2DisplayName
            lastMessageTextView.text = messageThread.lastMessage?.body ?: "No messages."
            timestampTextView.text = "${messageThread.lastMessage?.timestamp ?: 1}"
        }
    }
}