package com.siddhantkushwaha.carolyn.common.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.siddhantkushwaha.carolyn.common.util.CommonUtil.checkPermissions
import java.io.InputStream


object TelephonyUtil {

    data class SMSMessage(
        val id: Int,
        val threadId: Int,
        val user2: String,
        val timestamp: Long,
        val body: String,
        val type: Int,
        val subId: Int,
        val isRead: Boolean
    )

    data class ContactInfo(
        val id: Long,
        val number: String,
        val name: String
    )

    data class SubscriptionInfo(
        val subId: Int,
        val number: String,
        val carrierName: String
    )

    @SuppressLint("MissingPermission")
    public fun getSubscriptions(context: Context): HashMap<Int, SubscriptionInfo>? {
        var subscriptions: HashMap<Int, SubscriptionInfo>? = null
        if (checkPermissions(
                context,
                arrayOf(Manifest.permission.READ_PHONE_STATE)
            ).isEmpty()
        ) {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            subscriptions = HashMap()
            subscriptionManager.activeSubscriptionInfoList.forEach {
                subscriptions[it.subscriptionId] =
                    SubscriptionInfo(
                        subId = it.subscriptionId,
                        number = CommonUtil.normalizePhoneNumber(it.number ?: "Unknown")
                            ?: "Unknown",
                        carrierName = it.carrierName.toString()
                    )
            }
        }
        return subscriptions
    }

    @SuppressLint("MissingPermission")
    public fun getAllSms(context: Context): ArrayList<SMSMessage>? {
        var messages: ArrayList<SMSMessage>? = null
        if (checkPermissions(
                context,
                arrayOf(Manifest.permission.READ_SMS)
            ).isEmpty()
        ) {
            messages = ArrayList()
            val c = context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
            if (c != null) {
                val totalSMS = c.count
                if (c.moveToFirst()) {
                    for (j in 0 until totalSMS) {

                        val smsId = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms._ID))

                        val threadId = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))

                        val user2: String =
                            c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))

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

                        val isRead = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.READ))

                        val subId =
                            if (c.columnNames.find { it == Telephony.Sms.SUBSCRIPTION_ID } != null) {
                                c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID))
                            } else {
                                c.getInt(c.getColumnIndex("sim_id"))
                            }

                        val message = SMSMessage(
                            threadId = threadId,
                            id = smsId,
                            user2 = user2,
                            timestamp = date,
                            body = body,
                            type = type,
                            subId = subId,
                            isRead = isRead == 1
                        )

                        Log.d("TelephonyUtil", "$message")

                        // this list will have latest messages at the top
                        messages.add(message)

                        c.moveToNext()
                    }
                }
                c.close()
            }
        }
        return messages
    }

    public fun saveSms(
        context: Context,
        smsMessage: SMSMessage
    ) {
        val values = ContentValues()

        if (smsMessage.threadId > 0) {
            values.put(Telephony.Sms.THREAD_ID, smsMessage.threadId)
        }
        values.put(Telephony.Sms.ADDRESS, smsMessage.user2)
        values.put(Telephony.Sms.DATE, smsMessage.timestamp)
        values.put(Telephony.Sms.BODY, smsMessage.body)
        values.put(Telephony.Sms.TYPE, smsMessage.type)
        values.put(Telephony.Sms.SUBSCRIPTION_ID, smsMessage.subId)
        values.put(Telephony.Sms.READ, smsMessage.isRead)

        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    @SuppressLint("MissingPermission")
    public fun getAllContacts(
        context: Context,
    ): HashMap<String, ContactInfo>? {
        var contactsList: HashMap<String, ContactInfo>? = null
        if (checkPermissions(
                context,
                arrayOf(Manifest.permission.READ_CONTACTS)
            ).isEmpty()
        ) {
            contactsList = HashMap()
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri = ContactsContract.CommonDataKinds.Contactables.CONTENT_URI
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val contactId =
                        cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.CONTACT_ID))
                    var phoneNumber: String =
                        cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val name: String =
                        cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                    phoneNumber = CommonUtil.normalizePhoneNumber(phoneNumber) ?: "Unknown"
                    contactsList[phoneNumber] = ContactInfo(contactId, phoneNumber, name)
                }
                cursor.close()
            }
        }
        return contactsList
    }

    public fun openContactPhoto(
        context: Context,
        contactId: Long,
        preferHighRes: Boolean
    ): InputStream? {
        val contactUri =
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        return ContactsContract.Contacts.openContactPhotoInputStream(
            context.contentResolver,
            contactUri,
            preferHighRes
        )
    }

    public fun deleteSMS(context: Context, smsId: Int): Boolean {
        try {
            val numDeleted = context.contentResolver.delete(
                Uri.parse("${Telephony.Sms.CONTENT_URI}/$smsId"),
                null,
                null
            )
            return numDeleted > 0
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return false
    }

    public fun isDefaultSmsApp(context: Context): Boolean {
        return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }
}