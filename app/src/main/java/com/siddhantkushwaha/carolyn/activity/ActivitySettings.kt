package com.siddhantkushwaha.carolyn.activity

import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.storage.FirebaseStorage
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.Helper
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import com.siddhantkushwaha.carolyn.tasks.IndexTask
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
            Helper.setAsDefault(this, root) {
                when (it) {
                    0 -> Helper.showStatus(root, "Not changed to default SMS app.")
                    1 -> Helper.showStatus(root, "Changed to default SMS app.")
                    2 -> Helper.showStatus(root, "Already set as default.")
                }
            }
        }

        populatePie()
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

    private fun downloadAssetsUpdateUi() {
        MessageClassifier.refreshAssets(this) { status ->
            val message =
                if (status)
                    "Assets download succeeded."
                else
                    "Download failed."
            runOnUiThread {
                Helper.showStatus(root, message)
            }
        }
    }

    private fun deleteSpamOtpUpdateUi() {
        if (!TelephonyUtil.isDefaultSmsApp(this)) {
            Helper.showStatus(root, "Set as default SMS app.") { snackbar ->
                snackbar.setAction("MAKE DEFAULT") {
                    Helper.setAsDefault(this, root) {
                        when (it) {
                            // not need to handle other cases here
                            0 -> Helper.showStatus(root, "Not changed to default SMS app.")
                        }
                    }
                }
                snackbar.setActionTextColor(Color.WHITE)
            }
            return
        }

        Helper.showStatus(root, "Clearing all OTPs and Spam.")

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
                Helper.showStatus(root, "Failed to delete all spam and OTPs.")
            }

            realmL.close()

            // re index
            IndexTask(this).start()
        }

        clearAllMessages.start()
    }
}