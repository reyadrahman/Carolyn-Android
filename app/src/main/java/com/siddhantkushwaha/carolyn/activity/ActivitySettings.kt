package com.siddhantkushwaha.carolyn.activity

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.gms.tasks.Tasks
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.storage.FirebaseStorage
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.RequestCodes
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.entity.GlobalParam
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.index.IndexTask
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import kotlinx.android.synthetic.main.activity_settings.*


class ActivitySettings : ActivityBase() {

    private lateinit var firebaseStorage: FirebaseStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        firebaseStorage = FirebaseStorage.getInstance()

        checkbox_enable_unsaved_number_classification.isChecked =
            DbHelper.getUnsavedNumberClassificationRule(this)

        button_download_assets.setOnClickListener {
            downloadAssetsUpdateUi()
        }

        button_delete_spam_otp.setOnClickListener {
            deleteSpamOtpUpdateUi()
        }

        checkbox_enable_unsaved_number_classification.setOnCheckedChangeListener { _, isChecked ->
            DbHelper.setUnsavedNumberClassificationRule(this, isChecked)
        }

        button_default_sms_app.setOnClickListener {
            setAsDefault()
        }

        populatePie()
        populateContributors()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun populatePie() {
        val th = Thread {
            val realm = RealmUtil.getCustomRealmInstance(this@ActivitySettings)

            val messages = realm.where(Message::class.java).findAll()
            val distribution = messages.groupingBy { it.type }.eachCount()

            val values = ArrayList<PieEntry>()
            distribution.forEach {
                val share = it.value.toFloat() / messages.size
                val label = it.key ?: "personal"
                values.add(PieEntry(share, label))
            }

            val dataSet = PieDataSet(values, "")
            dataSet.setColors(
                ContextCompat.getColor(this, R.color.color1),
                ContextCompat.getColor(this, R.color.color2),
                ContextCompat.getColor(this, R.color.color3),
                ContextCompat.getColor(this, R.color.color4),
                ContextCompat.getColor(this, R.color.color5)
            )

            val data = PieData(dataSet)
            data.setDrawValues(false)

            pie_chart_type_distribution.data = data
            val holeColor = ContextCompat.getColor(this, R.color.colorPrimaryDarkLighter)
            pie_chart_type_distribution.setHoleColor(holeColor)

            pie_chart_type_distribution.legend.textColor = Color.WHITE
            pie_chart_type_distribution.legend.form = Legend.LegendForm.CIRCLE
            pie_chart_type_distribution.legend.horizontalAlignment =
                Legend.LegendHorizontalAlignment.CENTER
            pie_chart_type_distribution.legend.orientation = Legend.LegendOrientation.HORIZONTAL

            pie_chart_type_distribution.description.isEnabled = false

            pie_chart_type_distribution.setDrawEntryLabels(false)
            pie_chart_type_distribution.setTransparentCircleAlpha(0)

            realm.close()
        }
        th.start()
    }

    private fun populateContributors() {
        val th = Thread {
            try {
                val realm = RealmUtil.getCustomRealmInstance(this)
                realm.executeTransaction {
                    val ayuRef = "contributors/ayushi.jpg"
                    var ayuGP =
                        realm.where(GlobalParam::class.java).equalTo("attrName", ayuRef).findFirst()
                    val sidRef = "contributors/sid.jpg"
                    var sidGP =
                        realm.where(GlobalParam::class.java).equalTo("attrName", sidRef).findFirst()

                    var ayuUri = ayuGP?.attrVal
                    var sidUri = sidGP?.attrVal
                    runOnUiThread {
                        Glide.with(this).load(ayuUri).error(R.drawable.icon_user)
                            .circleCrop()
                            .into(c5)
                        Glide.with(this).load(sidUri).error(R.drawable.icon_user)
                            .circleCrop()
                            .into(c6)
                    }


                    if (ayuGP == null) {
                        ayuGP = realm.createObject(GlobalParam::class.java, ayuRef)
                            ?: throw Exception("Error")
                    }
                    if (sidGP == null) {
                        sidGP = realm.createObject(GlobalParam::class.java, sidRef)
                            ?: throw Exception("Error")
                    }

                    ayuGP.attrVal =
                        Tasks.await(firebaseStorage.getReference(ayuRef).downloadUrl)?.toString()
                    sidGP.attrVal =
                        Tasks.await(firebaseStorage.getReference(sidRef).downloadUrl)?.toString()

                    ayuUri = ayuGP.attrVal
                    sidUri = sidGP.attrVal
                    runOnUiThread {
                        Glide.with(this).load(ayuUri).error(R.drawable.icon_user)
                            .circleCrop()
                            .into(c5)
                        Glide.with(this).load(sidUri).error(R.drawable.icon_user)
                            .circleCrop()
                            .into(c6)
                    }

                    realm.insertOrUpdate(ayuGP)
                    realm.insertOrUpdate(sidGP)
                }
                realm.close()
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
        th.start()
    }

    private fun downloadAssetsUpdateUi() {
        MessageClassifier.refreshAssets(this) { status ->
            val message =
                if (status)
                    "Assets download succeeded."
                else
                    "Download failed."
            runOnUiThread {
                showStatus(message)
            }
        }
    }

    private fun deleteSpamOtpUpdateUi() {
        if (!TelephonyUtil.isDefaultSmsApp(this)) {
            showStatus("Carolyn is not the default SMS app.") { snackbar ->
                snackbar.setAction("MAKE DEFAULT") {
                    setAsDefault()
                }
                snackbar.setActionTextColor(Color.WHITE)
            }
            return
        }

        showStatus("Clearing all OTPs and Spam.")

        val clearAllMessages = Thread {

            // clear all
            val realmL = RealmUtil.getCustomRealmInstance(this)
            val allOtpMessages =
                realmL.where(Message::class.java).equalTo("type", Enums.MessageType.otp).findAll()
            val allSpamMessages =
                realmL.where(Message::class.java).equalTo("type", Enums.MessageType.spam).findAll()

            var deleted = true

            for (m in allOtpMessages) {
                val smsId = m.smsId
                if (smsId != null) {
                    deleted = TelephonyUtil.deleteSMS(this, smsId)
                    if (!deleted) break
                }
            }

            for (m in allSpamMessages) {
                val smsId = m.smsId
                if (smsId != null) {
                    deleted = TelephonyUtil.deleteSMS(this, smsId)
                    if (!deleted) break
                }
            }

            if (!deleted) {
                showStatus("Failed to delete all spam and OTPs.")
            }

            realmL.close()

            // re index
            IndexTask(this, false).start()
        }

        clearAllMessages.start()
    }

    private fun setAsDefault() {
        if (TelephonyUtil.isDefaultSmsApp(this)) {
            showStatus("Already set as default.")
            return
        }

        val requestCode = RequestCodes.REQUEST_CHANGE_DEFAULT
        val callback: (Intent?) -> Unit = {
            if (TelephonyUtil.isDefaultSmsApp(this)) {
                showStatus("Carolyn is now default SMS app.")
            }
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val roleManager = getSystemService(RoleManager::class.java)
            val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            startActivityForResult(roleRequestIntent, requestCode, callback)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivityForResult(intent, requestCode, callback)
        }
    }

    private fun showStatus(message: String, modifySnackbar: ((Snackbar) -> Unit)? = null) {
        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
        modifySnackbar?.invoke(snackbar)
        snackbar.show()
    }
}