package com.siddhantkushwaha.carolyn.common.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat


object PermissionsUtil {

    fun checkPermissions(context: Context, permissions: Array<String>): Array<String> {
        return permissions.filter { permission ->
            ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun requestPermissions(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int,
        requestPermissionCallbacks: HashMap<Int, (Boolean) -> Unit>?,
        callback: (Boolean) -> Unit

    ) {
        val requiredPermissions = checkPermissions(activity, permissions)
        if (requiredPermissions.isNotEmpty()) {
            requestPermissionCallbacks?.set(requestCode, callback)
            ActivityCompat.requestPermissions(activity, requiredPermissions, requestCode)
        } else {
            callback(true)
        }
    }
}

