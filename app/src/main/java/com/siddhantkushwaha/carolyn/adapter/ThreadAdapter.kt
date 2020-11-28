package com.siddhantkushwaha.carolyn.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.getStringForTimestamp
import com.siddhantkushwaha.carolyn.entity.MessageThread
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter


class ThreadAdapter(
    val context: Context,
    data: OrderedRealmCollection<MessageThread>,
    autoUpdate: Boolean,
    private val itemClickListener: (View, MessageThread) -> Unit
) : RealmRecyclerViewAdapter<MessageThread, RecyclerView.ViewHolder>(data, autoUpdate) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return ThreadViewHolder(context, view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val thread = data!![position]
        (holder as ThreadViewHolder).bind(thread, itemClickListener)
    }

    private class ThreadViewHolder(val context: Context, itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(messageThread: MessageThread, itemClickListener: (View, MessageThread) -> Unit) {

            val threadImageView = itemView.findViewById<ImageView>(R.id.icon_user)
            val threadTitleTextView = itemView.findViewById<TextView>(R.id.text_thread)
            val lastMessageTextView = itemView.findViewById<TextView>(R.id.text_message)
            val timestampTextView = itemView.findViewById<TextView>(R.id.text_timestamp)
            // val threadClassImageView = itemView.findViewById<ImageView>(R.id.image_view_thread_class)

            threadTitleTextView.text = messageThread.getDisplayName()
            lastMessageTextView.text = messageThread.lastMessage?.body ?: "No messages."

            val timestamp = messageThread.lastMessage?.timestamp
            if (timestamp == null) {
                timestampTextView.visibility = View.GONE
            } else {
                timestampTextView.visibility = View.VISIBLE
                timestampTextView.text = getStringForTimestamp(timestamp)
            }

            itemView.setOnClickListener { view ->
                itemClickListener(view, messageThread)
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
        }
    }
}