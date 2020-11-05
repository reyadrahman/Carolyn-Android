package com.siddhantkushwaha.carolyn.common

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager

@SuppressLint("MissingPermission")
public fun getSubscriptions(context: Context): HashMap<Int, String>? {
    var subscriptions: HashMap<Int, String>? = null
    if (PermissionsUtil.checkPermissions(
            context,
            arrayOf(android.Manifest.permission.READ_PHONE_STATE)
        ).isEmpty()
    ) {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        subscriptions = HashMap()
        subscriptionManager.activeSubscriptionInfoList.forEach {
            subscriptions[it.subscriptionId] = normalizePhoneNumber(it.number ?: "Unknown")
        }
    }
    return subscriptions
}

@SuppressLint("MissingPermission")
public fun getAllSms(context: Context): ArrayList<Array<Any>>? {
    var messages: ArrayList<Array<Any>>? = null
    if (PermissionsUtil.checkPermissions(context, arrayOf(android.Manifest.permission.READ_SMS))
            .isEmpty()
    ) {
        messages = ArrayList()
        val c =
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
        val totalSMS: Int
        if (c != null) {
            totalSMS = c.count
            if (c.moveToFirst()) {
                for (j in 0 until totalSMS) {

                    val threadId = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))

                    val user2: String = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))

                    val body: String = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY))

                    /*
                        Epoch time
                    */
                    val date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    /*
                        1 - Received
                        2 - Sent
                    */
                    val type: Int = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                    val subId: Int =
                        if (c.columnNames.find { it == Telephony.Sms.SUBSCRIPTION_ID } != null) {
                            c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID))
                        } else {
                            c.getInt(c.getColumnIndex("sim_id"))
                        }

                    val message = Array<Any>(6) { 0 }
                    message[0] = threadId
                    message[1] = user2
                    message[2] = date
                    message[3] = body
                    message[4] = type
                    message[5] = subId
                    messages.add(message)

                    c.moveToNext()
                }
            }
            c.close()
        }
    }
    return messages
}

@SuppressLint("MissingPermission")
public fun getAllContacts(
    context: Context,
): HashMap<String, String>? {
    var contactsList: HashMap<String, String>? = null
    if (PermissionsUtil.checkPermissions(
            context,
            arrayOf(android.Manifest.permission.READ_CONTACTS)
        ).isEmpty()
    ) {
        contactsList = HashMap()
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = ContactsContract.CommonDataKinds.Contactables.CONTENT_URI
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                var phoneNumber: String =
                    cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val name: String =
                    cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                phoneNumber = normalizePhoneNumber(phoneNumber)
                contactsList[phoneNumber] = name
            }
            cursor.close()
        }
    }
    return contactsList
}