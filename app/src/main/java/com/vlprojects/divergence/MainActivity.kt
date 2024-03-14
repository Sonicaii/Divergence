package com.vlprojects.divergence

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.viewbinding.BuildConfig
import com.vlprojects.divergence.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG)
            Timber.plant(Timber.DebugTree())

//        PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        binding.addWidgetLarge.setOnClickListener {
            val context = it.context
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val myProvider = ComponentName(context, DivergenceWidgetLarge::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            }
        }
        binding.addWidgetSmall.setOnClickListener {
            val context = it.context
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val myProvider = ComponentName(context, DivergenceWidgetSmall::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            }
        }

        UpdateWidgets.using(application)
    }

    /** Menu **/

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings_menu -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

object UpdateWidgets {
    fun using(application: Context, ignoreSmall: Boolean = false, specific: Pair<Int, Class<*>?>? = null) {
        val toUpdate = mutableListOf<Class<*>>(specific?.second ?: DivergenceWidgetLarge::class.java)
        if (!ignoreSmall)
            toUpdate.add(DivergenceWidgetSmall::class.java)
        for (widget in toUpdate) {
            val ids = if (specific == null)
                AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, widget))
            else
                intArrayOf(specific.first)
            if (ids.isEmpty()) continue
            val intentUpdate = Intent(application, widget)
            intentUpdate.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

            val pendingIntent =
                PendingIntent.getBroadcast(application, 0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT)
            pendingIntent.send()
        }
    }
}
