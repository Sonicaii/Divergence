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
import java.util.Calendar
import kotlin.random.Random
import kotlin.random.nextInt


class Clock : Service() {

    private lateinit var screenStateReceiver: BroadcastReceiver
    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true

    private fun doRunnableLogic(runnable: Runnable, time: Int) {
        Timber.d("Tick %d", System.currentTimeMillis())
        if (screenOn) {
            val ignoreSmall = time == CLOCK_SECONDS
            val delay = time - (System.currentTimeMillis() % time)

            // Glitch animation logic
            if (DivergenceWidget.glitch_animation)
                handler.postDelayed(
                    {
                        val (iterations, glitchDelay) = prepareGlitch(time)
                        startGlitch(iterations, glitchDelay, ignoreSmall = ignoreSmall)
                    },
                    (time - CLOCK_ANIMATION_DELAY_MAX * CLOCK_ANIMATION_ITER_MAX).toLong()
                )

            UpdateWidgets.using(application, ignoreSmall)

            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, delay)
        }
    }

    private val secondsRunnable: Runnable = object : Runnable {
        override fun run() = doRunnableLogic(this, CLOCK_SECONDS)
    }

    private val minutesRunnable: Runnable = object : Runnable {
        override fun run() = doRunnableLogic(this, CLICK_MINUTES)
    }

    override fun onCreate() {
        super.onCreate()

        // Need to send a notification about the clock to allow it to persist when phone screen is off
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
                        handler.post(secondsRunnable)
                        handler.post(minutesRunnable)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
        handler.post(secondsRunnable)
        handler.post(minutesRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(secondsRunnable)
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    /* Glitch Animations */

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            WIDGET_CLICK_ACTION -> {
                val (iterations, delay) = prepareGlitch(0)
                startGlitch(iterations, delay, Pair(
                    intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
                    intent.getIntExtra(LAYOUT, R.layout.divergence_widget_small)
                ))
            }
        }
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

    abstract class GlitchRunnable : Runnable {
        abstract fun stop()
    }

    private var runningAnimations = mutableListOf<GlitchRunnable>()
    private fun createClockGlitchRunnable(iterations: Int, glitchTargetAppWidget: Pair<Int, Int>?, ignoreSmall: Boolean): GlitchRunnable {
        return object : GlitchRunnable() {
            private var glitchIteration = iterations
            override fun stop() {
                handler.removeCallbacks(this)
                runningAnimations.remove(this)
                UpdateWidgets.using(application)
            }
            override fun run() {
                if (this.glitchIteration <= 0) {
                    stop()
                } else {
                    this.glitchIteration -= 1
                    clockGlitch(glitchTargetAppWidget, ignoreSmall)
                    handler.postDelayed(
                        this,
                        Random.nextInt(CLOCK_ANIMATION_DELAY_MIN..CLOCK_ANIMATION_DELAY_MAX).toLong()
                    )
                }
            }
        }
    }

    private fun prepareGlitch(initialDelay: Int): Pair<Int, Long> {
        // Cancel any currently running animations
        runningAnimations.map {
            it.stop()
        }
        if (runningAnimations.isNotEmpty()) UpdateWidgets.using(application)
        runningAnimations.clear()
        return Pair(
            Random.nextInt(CLOCK_ANIMATION_ITER_MIN..CLOCK_ANIMATION_ITER_MAX),
            (initialDelay - Random.nextInt(CLOCK_ANIMATION_ITER_MIN..CLOCK_ANIMATION_ITER_MAX) * CLOCK_ANIMATION_DELAY_MAX)
                .toLong()
                .coerceAtLeast(0L)
        )
    }

    private fun startGlitch(iterations: Int = 0, delay: Long = 0, glitchTargetAppWidget: Pair<Int, Int>? = null, ignoreSmall: Boolean = true) {
        val clockGlitchRunnable = createClockGlitchRunnable(iterations, glitchTargetAppWidget, ignoreSmall)
        runningAnimations.add(clockGlitchRunnable)
        handler.postDelayed(clockGlitchRunnable, delay)
    }

    private fun clockGlitch(glitchTargetAppWidget: Pair<Int, Int>? = null, ignoreSmall: Boolean) {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val appWidgetManager = AppWidgetManager.getInstance(this)

        val largeWidgetPairs = appWidgetManager.getAppWidgetIds(
            ComponentName(this, DivergenceWidgetLarge::class.java)
        ).map {it to R.layout.divergence_widget}
        val smallWidgetPairs = appWidgetManager.getAppWidgetIds(
            ComponentName(this, DivergenceWidgetSmall::class.java)
        ).map {it to R.layout.divergence_widget_small}

        var pairs = if (!ignoreSmall) largeWidgetPairs + smallWidgetPairs else largeWidgetPairs

        glitchTargetAppWidget?. let {
            if (!settings.getBoolean(SETTING_GLITCH_AFFECTS_ALL, false)) {
                pairs = listOf(it)
            }
        }

        for ((appWidgetId, layout) in pairs) {
            val views = RemoteViews(packageName, layout)
            val divergenceDigits = List(tubeIds.size) { (0..9).random() }
            tubeIds.forEachIndexed { i, tube ->
                // Don't set the periods
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
