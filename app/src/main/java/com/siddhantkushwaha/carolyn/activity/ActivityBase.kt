package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth

open class ActivityBase : AppCompatActivity() {

    lateinit var TAG: String
    lateinit var mAuth: FirebaseAuth

    // saves callbacks to be used in onRequestPermissionsResult
    private val requestPermissionCallbacks = HashMap<Int, (Boolean) -> Unit>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TAG = this::class.java.toString()

        mAuth = FirebaseAuth.getInstance()
    }

    override fun onStart() {
        super.onStart()
        if (mAuth.currentUser == null) {
            moveToLoginActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val callback = requestPermissionCallbacks[requestCode]
        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            Log.i(TAG, "invoking callback")
            callback?.invoke(true)
        } else
            callback?.invoke(false)
    }  

    fun moveToLoginActivity() {

        if (this::class.java == ActivityLogin::class.java)
            return

        val intent = Intent(this, ActivityLogin::class.java)
        startActivity(intent)
        finish()
    }

    fun moveToHomeActivity() {

        if (this::class.java == ActivityHome::class.java)
            return

        val intent = Intent(this, ActivityHome::class.java)
        startActivity(intent)
        finish()
    }

    fun moveToMessageActivity() {

        if (this::class.java == ActivityMessage::class.java)
            return

        val intent = Intent(this, ActivityMessage::class.java)
        startActivity(intent)
        finish()
    }

    fun requestPermission(permission: String, requestCode: Int, callback: (Boolean) -> Unit) {
        requestPermissionCallbacks[requestCode] = callback
        val permissionGrantedFlag = ActivityCompat.checkSelfPermission(this, permission)
        if (permissionGrantedFlag != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        else
            callback(true)
    }
}
