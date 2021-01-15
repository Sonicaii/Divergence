package com.vlprojects.divergence

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vlprojects.divergence.DivergenceMeter.getDivergenceValuesOrGenerate
import com.vlprojects.divergence.DivergenceMeter.saveDivergence
import com.vlprojects.divergence.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: 0.x.0 improve design in app
        val prefs = getSharedPreferences(SHARED_FILENAME, 0)
        val (currentDiv, nextDiv) = prefs.getDivergenceValuesOrGenerate()

        binding.currentDivergence.text = "%.6f".format(currentDiv / MILLION.toFloat())
        binding.nextDivergence.text = "%.6f".format(nextDiv / MILLION.toFloat())

        binding.changeDivergenceButton.setOnClickListener { changeDivergence(prefs) }
    }

    private fun changeDivergence(prefs: SharedPreferences) {
        val userDiv = binding.userDivergence.text.toString()
        if (userDiv.isBlank())
            return

        val userDivNumber = (userDiv.toDouble() * MILLION).toInt()
        if (userDivNumber !in ALL_RANGE) {
            Toast.makeText(this, "Wrong value. Should be in (-1.000000;2.000000)", Toast.LENGTH_LONG).show()
            return
        }

        prefs.saveDivergence(nextDiv = userDivNumber)

        updateWidget()

        Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun updateWidget() {
        val intentUpdate = Intent(this, DivergenceWidget::class.java)
        intentUpdate.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, DivergenceWidget::class.java)
        )
        intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

        val pendingIntent = PendingIntent.getBroadcast(this, 0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT)
        pendingIntent.send()
    }
}
