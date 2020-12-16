package com.vlprojects.divergence

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import kotlin.math.absoluteValue
import kotlin.random.Random

private const val SHARED_FILENAME = "divergence_widget"
private const val SHARED_DIVERGENCE = "divergence_value"
private const val ALPHA_WORLDLINE = 0
private const val BETA_WORLDLINE = 1_000_000
private const val OMEGA_WORLDLINE = -1_000_000
private const val DIVERGENCE_CHANGE_STEP = 100_000
private const val MAX_DIVERGENCE_COEFICIENT = DIVERGENCE_CHANGE_STEP / 4

class DivergenceWidget : android.appwidget.AppWidgetProvider() {

    // first launch action
    // TODO: make it work from the first launch
    override fun onEnabled(context: Context?) {
        val prefs = context!!.getSharedPreferences(SHARED_FILENAME, 0)
        with(prefs.edit()) {
            putInt(
                SHARED_DIVERGENCE,
                Random.nextInt(OMEGA_WORLDLINE, BETA_WORLDLINE * 2)
            )
            apply()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Preferences usage HERE
        val prefs = context.getSharedPreferences(SHARED_FILENAME, 0)
        val newDiv = generateRandomDivergence(
            prefs.getInt(
                SHARED_DIVERGENCE,
                // Shitcode: initial divergence generation
                OMEGA_WORLDLINE
            )
        )
        val newDivDigits = splitIntegerToDigits(newDiv)

        appWidgetIds.forEach {
            updateAppWidget(context, appWidgetManager, it, newDivDigits)
        }

        with(prefs.edit()) {
            putInt(SHARED_DIVERGENCE, newDiv)
            apply()
        }
    }

    fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        divergenceDigits: IntArray
    ) {
        val nixie = context.resources.obtainTypedArray(R.array.nixieImage)
        val tube = context.resources.obtainTypedArray(R.array.widgetTube)

        // Construct the RemoteViews object
        val views = RemoteViews(context.packageName, R.layout.divergence_widget)
        views.setImageViewResource(R.id.tubeDot, R.drawable.nixie_dot)

        // Main logic
        for (i in 0..6) {
            views.setImageViewResource(
                tube.getResourceId(i, 0),
                if (divergenceDigits[i] >= 0)
                    nixie.getResourceId(divergenceDigits[i], 0)
                else
                    R.drawable.nixie_minus
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)

        nixie.recycle()
        tube.recycle()
    }

    fun generateRandomDivergence(currentDiv: Int): Int {
        /* Coefficient needed to lower the chance of going to new wordline
         * How it works:
         *  - equalize divergence to range [0;1_000_000)
         *  - subtract half the maximum divergence to put in range [-500_000;+500_000)
         *  - divide by specific number to create adding coefficient of half the step */
        val coef = (when {
            currentDiv >= BETA_WORLDLINE -> -BETA_WORLDLINE
            currentDiv < ALPHA_WORLDLINE -> +BETA_WORLDLINE
            else -> 0
        } + currentDiv - 500_000) / -(BETA_WORLDLINE / 2 / MAX_DIVERGENCE_COEFICIENT)

        var newDiv = currentDiv + Random.nextInt(-DIVERGENCE_CHANGE_STEP + coef, DIVERGENCE_CHANGE_STEP + coef)
        // println("$currentDiv + (${coef - DIVERGENCE_CHANGE_STEP} ; ${coef + DIVERGENCE_CHANGE_STEP})")

        while (newDiv < OMEGA_WORLDLINE || newDiv > 2_000_000)
            if (newDiv < OMEGA_WORLDLINE)
                newDiv += Random.nextInt(DIVERGENCE_CHANGE_STEP)
            else if (newDiv > 2_000_000)
                newDiv -= Random.nextInt(DIVERGENCE_CHANGE_STEP)

        return newDiv
    }

    // Shitcode: Unfixable
    fun splitIntegerToDigits(int: Int): IntArray {
        val digits = IntArray(7)
        var integer = int.absoluteValue

        for (i in digits.indices) {
            digits[i] = (integer % 10)
            integer /= 10
        }
        if (int <= 0)
            digits[6] = -1

        return digits
    }
}