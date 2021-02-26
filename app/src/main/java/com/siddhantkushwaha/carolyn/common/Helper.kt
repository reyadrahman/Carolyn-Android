package com.siddhantkushwaha.carolyn.common

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.siddhantkushwaha.carolyn.activity.ActivityBase
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil


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
}