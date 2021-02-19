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
import com.siddhantkushwaha.carolyn.entity.Contact
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter


class ContactAdapter(
    val context: Context,
    data: OrderedRealmCollection<Contact>,
    autoUpdate: Boolean,
    private val clickListener: (View, Contact) -> Unit
) : RealmRecyclerViewAdapter<Contact, RecyclerView.ViewHolder>(data, autoUpdate) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactHolder(context, view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val contact = data!![position]
        (holder as ContactHolder).bind(contact, clickListener)
    }

    private class ContactHolder(val context: Context, itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(contact: Contact, clickListener: (View, Contact) -> Unit) {
            val contactName = itemView.findViewById<TextView>(R.id.contact_name)
            val contactImage = itemView.findViewById<ImageView>(R.id.icon_contact)
            val contactNumber = itemView.findViewById<TextView>(R.id.contact_number)

            contactName.text = contact.name
            contactNumber.text = contact.number

            itemView.setOnClickListener { view ->
                clickListener(view, contact)
            }

            val photoUri = contact.photoUri
            if (photoUri != null) {
                Glide
                    .with(context)
                    .load(photoUri)
                    .error(R.drawable.icon_user)
                    .circleCrop()
                    .into(contactImage)
            } else {
                contactImage.setImageResource(R.drawable.icon_user)
            }
        }
    }
}