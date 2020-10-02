package com.siddhantkushwaha.carolyn.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.siddhantkushwaha.carolyn.activity.ActivityBase
import java.io.File
import java.security.MessageDigest

fun getHash(data: String, algorithm: String = "SHA-256"): String {
    return MessageDigest.getInstance(algorithm).digest(data.toByteArray())
        .fold("", { str, it -> str + "%02x".format(it) })
}

fun normalizePhoneNumber(number: String): String {
    return try {
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val parsedPhone = phoneNumberUtil.parse(number, "IN")
        phoneNumberUtil.format(parsedPhone, PhoneNumberUtil.PhoneNumberFormat.E164)
    } catch (exception: NumberParseException) {
        number
    } catch (exception: Exception) {
        exception.printStackTrace()
        number
    }
}

@SuppressLint("MissingPermission")
fun getSubscriptions(activity: ActivityBase, callback: (HashMap<Int, String>) -> Unit) {
    activity.requestPermission(
        android.Manifest.permission.READ_PHONE_STATE,
        RequestCodes.REQUEST_PERMISSION_READ_PHONE_STATE
    ) { granted ->
        if (granted) {
            val subscriptionManager = activity.getSystemService(SubscriptionManager::class.java)
            val subscriptions = HashMap<Int, String>()
            subscriptionManager.activeSubscriptionInfoList.forEach {
                subscriptions[it.subscriptionId] = normalizePhoneNumber(it.number ?: "Unknown")
            }
            callback(subscriptions)
        }
    }
}

@SuppressLint("MissingPermission")
fun getAllSms(activity: ActivityBase, callback: (ArrayList<Array<Any>>) -> Unit) {
    activity.requestPermission(
        android.Manifest.permission.READ_SMS,
        RequestCodes.REQUEST_PERMISSION_READ_SMS
    ) { granted ->
        if (granted) {

            val messages = ArrayList<Array<Any>>()

            val c =
                activity.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
            val totalSMS: Int
            if (c != null) {
                totalSMS = c.count
                if (c.moveToFirst()) {
                    for (j in 0 until totalSMS) {

                        val threadId = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))

                        val user2: String =
                            c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))

                        val body: String =
                            c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY))

                        /*
                            Epoch time
                        */
                        val date =
                            c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))

                        /*
                            1 - Received
                            2 - Sent
                        */
                        val type: Int =
                            c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))

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
            callback(messages)
        }
    }
}

@SuppressLint("MissingPermission")
fun getAllContacts(activity: ActivityBase, callback: (HashMap<String, String>) -> Unit) {
    activity.requestPermission(
        android.Manifest.permission.READ_CONTACTS,
        RequestCodes.REQUEST_PERMISSION_READ_CONTACTS
    ) { granted ->
        if (granted) {
            val contactsList: HashMap<String, String> = HashMap()
            val contentResolver: ContentResolver = activity.contentResolver
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
            callback(contactsList)
        }
    }
}

fun containsDigit(s: String): Boolean {
    var containsDigit = false
    if (s.isNotEmpty()) {
        for (c in s.toCharArray()) {
            if (Character.isDigit(c).also { containsDigit = it }) {
                break
            }
        }
    }
    return containsDigit
}

fun cleanText(text: String): String {
    val textBuilder = StringBuilder()
    for (word in text.split(" ")) {

        // remove links and emails
        if (word.contains('/') && word.contains('.')) {
            continue
        } else if (word.contains(".com") || word.contains(".me")) {
            continue
        } else if (word.contains('@') && word.contains('.')) {
            continue
        }

        // remove all tokens with numbers
        else if (containsDigit(word)) {
            textBuilder.append(" #")
        }

        // otherwise clean and add
        else {
            var cleanedWord = word.toLowerCase()
            cleanedWord = Regex("[^A-Za-z0-9 ]").replace(cleanedWord, " ")
            textBuilder.append(" $cleanedWord")
        }
    }

    val textBuilder2 = StringBuilder()
    for (word in textBuilder.split(" ")) {
        if (word.length > 1 || word == "#") {
            textBuilder2.append(" $word")
        }
    }

    return textBuilder2.toString().trim()
}

fun writeFile(activity: Activity, data: String, fileName: String) {
    val path = activity.getExternalFilesDir(null)
    println("Writing file: $path")
    val file = File(path, fileName)
    try {
        file.outputStream().write(data.toByteArray())
        println("File written successfully.")
    } catch (exp: Exception) {
        exp.printStackTrace()
    }
}