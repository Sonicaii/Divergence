package com.vlprojects.divergence

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.vlprojects.divergence.logic.*
import timber.log.Timber
import kotlin.random.Random
import kotlin.random.nextInt


class Clock : Service() {

    private lateinit var screenStateReceiver: BroadcastReceiver
    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true

    private val updateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (screenOn) {
                // Glitch animation logic
                if (!glitchRunning && DivergenceWidget.glitch_animation) {
                    glitchRunning = true
                    glitchIteration = Random.nextInt(3..5)
                    val delay = 1000 - (System.currentTimeMillis() % 1000) - glitchIteration * 70
                    if (delay > 0) // Make sure there is enough time left for animation
                        handler.postDelayed(clockGlitchRunnable, delay)
                    else
                        handler.postDelayed(clockGlitchRunnable, delay + 1000)
                }
                // Rest of widget update
                updateWidgets()
                handler.removeCallbacks(this)
                handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000))
            }
        }
    }

    private fun updateWidgets() {
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, DivergenceWidget::class.java)
        )
        if (ids.isEmpty()) return
        val intentUpdate = Intent(this, DivergenceWidget::class.java)
        intentUpdate.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT)
        pendingIntent.send()
    }

    override fun onCreate() {
        super.onCreate()

        // Need to send a notification about the clock to allow it to persist
        createNotificationChannel()
        startForeground(CLOCK_ID, NotificationCompat.Builder(this, CLOCK_NOTIFICATION_CHANNEL)
            .setContentTitle("Nixie clock in operation")
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_notification)
            .build())

        val settings = PreferenceManager.getDefaultSharedPreferences(this)

        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // Disable updates depending on setting
                        screenOn = !settings.getBoolean(SETTING_POWER_SAVING, true)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Resume operation when screen is activated
                        screenOn = true
                        handler.post(updateRunnable)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CLOCK_NOTIFICATION_CHANNEL,
                "Divergence Clock Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private var glitchIteration = 0
    private var glitchRunning = false
    private val clockGlitchRunnable: Runnable = object : Runnable {
        override fun run() {
            if (glitchIteration <= 0) {
                glitchRunning = false
                handler.removeCallbacks(this)
                updateWidgets()
            } else {
                glitchIteration -= 1
                clockGlitch()
                handler.postDelayed(
                    this,
                    Random.nextInt(CLOCK_ANIMATION_DELAY_MIN..CLOCK_ANIMATION_DELAY_MAX).toLong()
                )
            }
        }
    }

    private fun clockGlitch() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds =
            appWidgetManager.getAppWidgetIds(ComponentName(this, DivergenceWidget::class.java))
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(packageName, R.layout.divergence_widget)
            val divergenceDigits = List(tubeIds.size) { (0..9).random() }
            Timber.d("$divergenceDigits")
            tubeIds.forEachIndexed { i, tube ->
                if (i % 3 != 2) {
                    views.setImageViewResource(
                        tube,
                        nixieNumberDrawables[divergenceDigits[i]]
                    )
                }
            }
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }
}


