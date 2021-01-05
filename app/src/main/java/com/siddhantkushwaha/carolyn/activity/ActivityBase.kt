package com.siddhantkushwaha.carolyn.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.util.CommonUtil.checkPermissions


open class ActivityBase : AppCompatActivity() {

    protected lateinit var TAG: String
    protected lateinit var mAuth: FirebaseAuth

    // saves callbacks to be used in onRequestPermissionsResult
    private val requestPermissionCallbacks = HashMap<Int, (Boolean) -> Unit>()
    private val startActivityForResultCallbacks = HashMap<Int, (Intent?) -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TAG = this::class.java.toString()

        mAuth = FirebaseAuth.getInstance()
    }

    override fun onResume() {
        super.onResume()
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
        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
            callback?.invoke(true)
        else
            callback?.invoke(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val callback = startActivityForResultCallbacks[requestCode]
        callback?.invoke(data)
    }

    protected fun requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        callback: (Boolean) -> Unit
    ) {
        val requiredPermissions = checkPermissions(this, permissions)
        if (requiredPermissions.isNotEmpty()) {
            requestPermissionCallbacks[requestCode] = callback
            ActivityCompat.requestPermissions(this, requiredPermissions, requestCode)
        } else {
            callback(true)
        }
    }

    protected fun startActivityForResult(
        intent: Intent,
        requestCode: Int,
        callback: (Intent?) -> Unit
    ) {
        startActivityForResultCallbacks[requestCode] = callback
        startActivityForResult(intent, requestCode)
    }

    protected fun moveToLoginActivity() {

        if (this::class.java == ActivityLogin::class.java)
            return

        val intent = Intent(this, ActivityLogin::class.java)
        startActivity(intent)
        finish()
    }

    protected fun moveToHomeActivity() {

        if (this::class.java == ActivityHome::class.java)
            return

        val intent = Intent(this, ActivityHome::class.java)
        startActivity(intent)
        finish()
    }

    protected fun logout() {
        mAuth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut()
    }
}
