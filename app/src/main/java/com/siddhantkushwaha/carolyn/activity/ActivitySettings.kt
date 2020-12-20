package com.siddhantkushwaha.carolyn.activity

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Message
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_settings.*

class ActivitySettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
                ContextCompat.getColor(this, R.color.colorDist1),
                ContextCompat.getColor(this, R.color.colorDist2),
                ContextCompat.getColor(this, R.color.colorDist3),
                ContextCompat.getColor(this, R.color.colorDist4),
                ContextCompat.getColor(this, R.color.colorDist5)
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
}