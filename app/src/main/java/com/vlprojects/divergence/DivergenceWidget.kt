package com.vlprojects.divergence

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.vlprojects.divergence.logic.*
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter


abstract class DivergenceWidget : android.appwidget.AppWidgetProvider() {

    companion object {
        var glitch_animation = false
    }

    abstract var layout: Int
    abstract var timePattern24: String
    abstract var timePattern12: String
    private var timePattern = timePattern24

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startForegroundService(Intent(context, Clock::class.java))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        val settings = PreferenceManager.getDefaultSharedPreferences(context)

        timePattern = if (settings.getBoolean(SETTING_TIME_FORMAT, true)) timePattern24 else timePattern12
        glitch_animation = settings.getBoolean(SETTING_GLITCH_ANIMATION, false)

        // Listen to click action for each app widget
        appWidgetIds.forEach {
            Timber.d("Queued update for: $it")
            val intent = Intent(context, Clock::class.java).apply {
                action = WIDGET_CLICK_ACTION
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, it)
                putExtra(LAYOUT, layout)
            }
            val pendingIntent = PendingIntent.getService(context, it, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            updateAppWidget(context.packageName, pendingIntent, appWidgetManager, it)
        }

        context.startForegroundService(Intent(context, Clock::class.java))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateAppWidget(
        packageName: String,
        pendingIntent: PendingIntent,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(packageName, layout).apply {
            setOnClickPendingIntent(R.id.tubes, pendingIntent)
        }

        val currentTime = LocalTime.now()
        val formattedTime = currentTime.format(DateTimeFormatter.ofPattern(timePattern))

        // Convert the formatted time string to a list of integers
        val divergenceDigits = formattedTime.map { char ->
            when (char) {
                ':' -> 10
                else -> char.toString().toInt()
            }
        }
        // Setting numbers in place
        for (i in timePattern.indices) {
            views.setImageViewResource(
                tubeIds[i],
                nixieNumberDrawables[divergenceDigits[i]]
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class DivergenceWidgetLarge: DivergenceWidget() {
    companion object {
        var layout = R.layout.divergence_widget
    }

    override var layout = DivergenceWidgetLarge.layout
    override var timePattern24 = "HH:mm:ss"
    override var timePattern12 = "hh:mm:ss"
}

class DivergenceWidgetSmall: DivergenceWidget() {
    companion object {
        var layout = R.layout.divergence_widget_small
    }

    override var layout = DivergenceWidgetSmall.layout
    override var timePattern24 = "HH:mm:ss"
    override var timePattern12 = "hh:mm:ss"
}
