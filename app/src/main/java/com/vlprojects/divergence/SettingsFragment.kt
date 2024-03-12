package com.vlprojects.divergence

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import androidx.preference.*

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Set EditText to show only numbers
        val changeCooldownPreference = preferenceManager.findPreference<EditTextPreference>("attractor_change_cooldown")
        changeCooldownPreference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.filters += InputFilter.LengthFilter(6)
        }

        // Match app's pref to system's 24-hour setting
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.contains("SETTING_TIME_FORMAT")) {
            val is24HourFormat = android.text.format.DateFormat.is24HourFormat(context)
            val editor = prefs.edit()
            editor.putBoolean("SETTING_TIME_FORMAT", is24HourFormat)
            editor.apply()

            val timeFormatPreference: SwitchPreference? = findPreference("time_format")
            timeFormatPreference?.isChecked = is24HourFormat
        }
    }
}