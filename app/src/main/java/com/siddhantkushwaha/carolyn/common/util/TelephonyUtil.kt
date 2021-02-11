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

    private val tag = javaClass.toString()

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
        if (checkPermissions(context, arrayOf(Manifest.permission.READ_PHONE_STATE)).isEmpty()) {
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

    public fun getAllSms(context: Context): ArrayList<SMSMessage>? {
        var messages: ArrayList<SMSMessage>? = null
        if (checkPermissions(context, arrayOf(Manifest.permission.READ_SMS)).isEmpty()) {
            messages = ArrayList()
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                null
            )
            if (cursor != null) {
                while (cursor.moveToNext()) {

                    val smsId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))

                    val threadId =
                        cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))

                    val user2: String =
                        cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                            ?: continue

                    val body: String =
                        cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))

                    // Epoch time
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    /*
                        1 - Received
                        2 - Sent
                    */
                    val type: Int =
                        cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                    val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ))

                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS))

                    val subId =
                        if (cursor.columnNames.find { it == Telephony.Sms.SUBSCRIPTION_ID } != null) {
                            cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID))
                        } else {
                            cursor.getInt(cursor.getColumnIndex("sim_id"))
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

                    // this list will have latest messages at the top
                    messages.add(message)
                }
                cursor.close()
            }
        }
        return messages
    }

    public fun saveSms(context: Context, smsMessage: SMSMessage): Int {
        val values = ContentValues()

        if (smsMessage.threadId > 0)
            values.put(Telephony.Sms.THREAD_ID, smsMessage.threadId)

        values.put(Telephony.Sms.ADDRESS, smsMessage.user2)
        values.put(Telephony.Sms.DATE, smsMessage.timestamp)
        values.put(Telephony.Sms.BODY, smsMessage.body)
        values.put(Telephony.Sms.TYPE, smsMessage.type)
        values.put(Telephony.Sms.SUBSCRIPTION_ID, smsMessage.subId)
        values.put(Telephony.Sms.READ, smsMessage.isRead)

        val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            ?: return 0

        val smsId = uri.toString().split("/").last().toInt()

        Log.d(tag, "Message added at URI: $uri $smsId")

        return smsId
    }

    public fun markMessageAsRead(context: Context, smsId: Int): Int {
        val uri = Uri.parse("${Telephony.Sms.CONTENT_URI}/$smsId")

        val values = ContentValues()
        values.put(Telephony.Sms.READ, 1)

        val numUpdated = context.contentResolver.update(
            uri,
            values,
            null,
            null
        )

        Log.d(tag, "Message updated at URI: $uri $smsId")

        return numUpdated
    }

    public fun getAllContacts(context: Context): HashMap<String, ContactInfo>? {
        var contactsList: HashMap<String, ContactInfo>? = null
        if (checkPermissions(context, arrayOf(Manifest.permission.READ_CONTACTS)).isEmpty()) {
            contactsList = HashMap()
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri = ContactsContract.CommonDataKinds.Contactables.CONTENT_URI
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val contactId =
                        cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.CONTACT_ID))
                    val phoneNumber: String =
                        cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val name: String =
                        cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                    val phoneNumberNormalized = CommonUtil.normalizePhoneNumber(phoneNumber)
                    if (phoneNumberNormalized != null) {
                        contactsList[phoneNumberNormalized] =
                            ContactInfo(contactId, phoneNumber, name)
                    }
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
        val uri = Uri.parse("${Telephony.Sms.CONTENT_URI}/$smsId")
        val numDeleted = context.contentResolver.delete(
            uri,
            null,
            null
        )
        return numDeleted > 0
    }

    public fun isDefaultSmsApp(context: Context): Boolean {
        return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }
}