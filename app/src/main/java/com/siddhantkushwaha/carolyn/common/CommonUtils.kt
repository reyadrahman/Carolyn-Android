package com.siddhantkushwaha.carolyn.common

import android.content.Context
import android.provider.Settings
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*


fun getDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

public fun getHash(data: String, algorithm: String = "SHA-256"): String {
    return MessageDigest.getInstance(algorithm).digest(data.toByteArray())
        .fold("", { str, it -> str + "%02x".format(it) })
}

public fun normalizePhoneNumber(number: String): String? {
    return try {
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val parsedPhone = phoneNumberUtil.parse(number, "IN")
        phoneNumberUtil.format(parsedPhone, PhoneNumberUtil.PhoneNumberFormat.E164)
    } catch (exception: NumberParseException) {
        null
    } catch (exception: Exception) {
        exception.printStackTrace()
        null
    }
}

public fun containsDigit(s: String): Boolean {
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

public fun cleanText(text: String): String {
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

public fun formatTimestamp(timestamp: Long, format: String): String {
    val timeZoneId = TimeZone.getDefault().toZoneId()
    val date = Instant.ofEpochMilli(timestamp).atZone(timeZoneId)
    return DateTimeFormatter.ofPattern(format).format(date)
}

public fun getStringForTimestamp(timestamp: Long): String {
    val currTimestamp = Instant.now().toEpochMilli()

    return when ((currTimestamp - timestamp) / (24 * 3600000)) {
        0L -> {
            formatTimestamp(timestamp, "hh:mm a")
        }

        1L -> {
            "Yesterday"
        }

        2L, 3L, 4L, 5L, 6L, 7L -> {
            formatTimestamp(timestamp, "EEE")
        }

        else -> {
            formatTimestamp(timestamp, "dd/MM/yy")
        }
    }
}