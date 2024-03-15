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


class Clock : Service() {

    private lateinit var screenStateReceiver: BroadcastReceiver
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener{ prefs, key ->
        if (key == SETTING_GLITCH_ANIMATION)
            DivergenceWidget.glitch_animation = prefs.getBoolean(key, false)
        startClocks()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true

    private fun doRunnableLogic(runnable: Runnable, time: Int) {
        if (!screenOn) return

        val updateMinutes = time == CLOCK_MINUTES
        val delay = time - (System.currentTimeMillis() % time)

        // Glitch animation logic
        if (DivergenceWidget.glitch_animation) {
            val (glitchDelay, animation) = prepareGlitch(delay)
            Timber.d("Next glitch in $glitchDelay ms")
            handler.postDelayed(
                { startGlitch(animation, updateMinutes = updateMinutes) },
                glitchDelay
            )
        }

        UpdateWidgets.using(application, updateMinutes = updateMinutes)

        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, delay)
    }

    private val clockParts = listOf(
        object : Runnable {
            override fun run() = doRunnableLogic(this, CLOCK_SECONDS)
        },
        object : Runnable {
            override fun run() = doRunnableLogic(this, CLOCK_MINUTES)
        }
    )
    private fun startClocks() = clockParts.map { handler.post(it) }

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
                        startClocks()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        // Update clocks and restart animations depending on preferences toggles
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(preferencesListener)

        startClocks()
    }

    override fun onDestroy() {
        super.onDestroy()
        startClocks()
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(p0: Intent?): IBinder? = null

    /** Glitch Animations **/

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            WIDGET_CLICK_ACTION -> {
                val (_, animation) = prepareGlitch(0)
                val layout = intent.getIntExtra(LAYOUT, R.layout.divergence_widget_small)
                startGlitch(animation, Pair(
                    intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
                    layout
                ), layout == R.layout.divergence_widget_small, true)
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

    private fun createClockGlitchRunnable(animation: List<Long>, glitchTargetAppWidget: Pair<Int, Int>?, updateMinutes: Boolean, override: Boolean): Runnable {
        return object : Runnable {
            private var animation = animation.toMutableList()
            fun stop() {
                handler.removeCallbacks(this)
                UpdateWidgets.using(application, updateMinutes = updateMinutes)
            }
            override fun run() {
                if (this.animation.isEmpty()) {
                    stop()
                } else {
                    if (override || DivergenceWidget.glitch_animation)
                        clockGlitch(glitchTargetAppWidget, updateMinutes)
                    handler.postDelayed(
                        this,
                        this.animation.removeAt(this.animation.size - 1)
                    )
                }
            }
        }
    }

    private fun prepareGlitch(initialDelay: Long): Pair<Long, List<Long>> {
        val delays: List<Long> = List((CLOCK_ANIMATION_ITER_MIN..CLOCK_ANIMATION_ITER_MAX).random()) {
            (CLOCK_ANIMATION_DELAY_MIN..CLOCK_ANIMATION_DELAY_MAX).random().toLong()
        }
        return Pair(
            (initialDelay - delays.sum()).coerceAtLeast(0L),
            delays
        )
    }

    private fun startGlitch(animation: List<Long>, glitchTargetAppWidget: Pair<Int, Int>? = null, updateMinutes: Boolean = false, override: Boolean = false) {
        handler.post(createClockGlitchRunnable(animation, glitchTargetAppWidget, updateMinutes, override))
    }

    private fun clockGlitch(glitchTargetAppWidget: Pair<Int, Int>? = null, updateMinutes: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(this)

        val largeWidgetPairs = appWidgetManager.getAppWidgetIds(
            ComponentName(this, DivergenceWidgetLarge::class.java)
        ).map {it to R.layout.divergence_widget}
        val smallWidgetPairs = appWidgetManager.getAppWidgetIds(
            ComponentName(this, DivergenceWidgetSmall::class.java)
        ).map {it to R.layout.divergence_widget_small}

        var pairs = if (updateMinutes) largeWidgetPairs + smallWidgetPairs else largeWidgetPairs

        // List of only the target if specified
        glitchTargetAppWidget?. let { pairs = listOf(it) }

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
