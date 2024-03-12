package com.vlprojects.divergence

import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.vlprojects.divergence.logic.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DivergenceWidget : android.appwidget.AppWidgetProvider() {

    companion object {
        var glitch_animation = false
    }

    private var timePattern = "HH:mm:ss"

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startService(Intent(context, Clock::class.java))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        val settings = PreferenceManager.getDefaultSharedPreferences(context)

        timePattern = if (settings.getBoolean(SETTING_TIME_FORMAT, true)) "HH:mm:ss" else "hh:mm:ss"
        glitch_animation = settings.getBoolean(SETTING_GLITCH_ANIMATION, false)

        // Firstly, apply saved next divergence to the widgets,
        // so that the divergence can be updated to a specific number
        appWidgetIds.forEach {
            updateAppWidget(context.packageName, appWidgetManager, it)
        }

        context.startForegroundService(Intent(context, Clock::class.java))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateAppWidget(
        packageName: String,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(packageName, R.layout.divergence_widget)

        val currentTime = LocalTime.now()
        val formattedTime = currentTime.format(DateTimeFormatter.ofPattern(timePattern))

        // Convert the formatted time string to a list of integers
        val divergenceDigits = formattedTime.map { char ->
            when (char) {
                ':' -> 10
                '.' -> 10
                else -> char.toString().toInt()
            }
        }
        // Setting numbers in place
        for (i in 0..7) {
            views.setImageViewResource(
                tubeIds[i],
                if (divergenceDigits[i] >= 0)
                    nixieNumberDrawables[divergenceDigits[i]]
                else
                    R.drawable.nixie_minus
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}