package com.siddhantkushwaha.carolyn.common.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import java.io.InputStream

object TelephonyUtil {

    data class SMSMessage(
        val id: Int,
        val user2: String,
        val timestamp: Long,
        val body: String,
        val sent: Boolean,
        val subId: Int,
        val isRead: Boolean
    )

    data class ContactInfo(
        val id: Long,
        val number: String,
        val name: String
    )

    @SuppressLint("MissingPermission")
    public fun getSubscriptions(context: Context): HashMap<Int, String>? {
        var subscriptions: HashMap<Int, String>? = null
        if (PermissionsUtil.checkPermissions(
                context,
                arrayOf(Manifest.permission.READ_PHONE_STATE)
            ).isEmpty()
        ) {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            subscriptions = HashMap()
            subscriptionManager.activeSubscriptionInfoList.forEach {
                subscriptions[it.subscriptionId] =
                    CommonUtil.normalizePhoneNumber(it.number ?: "Unknown") ?: "Unknown"
            }
        }
        return subscriptions
    }

    @SuppressLint("MissingPermission")
    public fun getAllSms(context: Context): ArrayList<SMSMessage>? {
        var messages: ArrayList<SMSMessage>? = null
        if (PermissionsUtil.checkPermissions(
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

                        var smsId = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms._ID))

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
                            id = smsId,
                            user2 = user2,
                            timestamp = date,
                            body = body,
                            sent = type == 2,
                            subId = subId,
                            isRead = isRead == 1
                        )

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

    @SuppressLint("MissingPermission")
    public fun getAllContacts(
        context: Context,
    ): HashMap<String, ContactInfo>? {
        var contactsList: HashMap<String, ContactInfo>? = null
        if (PermissionsUtil.checkPermissions(
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
            context.contentResolver.delete(
                Uri.parse("${Telephony.Sms.CONTENT_URI}/$smsId"),
                null,
                null
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return false;
    }

    public fun isDefaultSmsApp(context: Context): Boolean {
        return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }
}