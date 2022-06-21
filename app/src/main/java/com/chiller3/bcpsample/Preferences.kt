package com.chiller3.bcpsample

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class Preferences(context: Context) {
    companion object {
        const val PREF_ENABLED = "enabled"
        const val PREF_VERSION = "version"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Whether call audio playback is enabled.
     */
    var isEnabled: Boolean
        get() = prefs.getBoolean(PREF_ENABLED, false)
        set(enabled) = prefs.edit { putBoolean(PREF_ENABLED, enabled) }
}