package com.siddhantkushwaha.carolyn.common

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.siddhantkushwaha.carolyn.activity.ActivityBase
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import io.realm.Realm
import java.util.*


object Helper {

    public fun setAsDefault(
        activity: ActivityBase,
        rootView: View,
        resultCallback: (Int) -> Unit,
    ) {
        if (TelephonyUtil.isDefaultSmsApp(activity)) {
            showStatus(rootView, "Already set as default.")
            resultCallback(2)
            return
        }

        val requestCode = RequestCodes.REQUEST_CHANGE_DEFAULT
        val callback: (Intent?) -> Unit = {
            if (TelephonyUtil.isDefaultSmsApp(activity))
                resultCallback(1)
            else
                resultCallback(0)
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            activity.startActivityForResult(roleRequestIntent, requestCode, callback)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
            activity.startActivityForResult(intent, requestCode, callback)
        }
    }

    public fun showStatus(
        view: View,
        message: String,
        modifySnackbar: ((Snackbar) -> Unit)? = null
    ) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        modifySnackbar?.invoke(snackbar)
        snackbar.show()
    }

    public fun normalizeUser2(user2NotNormalized: String): String {
        return CommonUtil.normalizePhoneNumber(user2NotNormalized)
            ?: user2NotNormalized.replace("-", "")
                .toLowerCase(Locale.getDefault())
    }

    public fun fetchOtpFromText(text: String): String? {
        return "[0-9]+".toRegex().findAll(text)
            .filter { it.value.length in 4..6 }.firstOrNull()?.value
    }

    public fun deleteMessage(
        context: Context,
        realm: Realm,
        smsId: Int?,
        messageId: String?
    ): Boolean {
        var deleted = true
        if (smsId != null && smsId > 0) {
            deleted = TelephonyUtil.deleteSMS(context, smsId)
        }
        if (messageId != null && deleted) {
            // single sync op on UI thread should be OK
            realm.executeTransaction { rt ->
                DbHelper.getMessageObject(rt, messageId)?.deleteFromRealm()
            }
        }
        return deleted
    }
}