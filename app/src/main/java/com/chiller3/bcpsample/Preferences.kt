package com.chiller3.bcpsample

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class Preferences(private val context: Context) {
    companion object {
        const val PREF_ENABLED = "enabled"
        const val PREF_AUDIO_FILE = "audio_file"
        const val PREF_VERSION = "version"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Whether call audio playback is enabled.
     */
    var isEnabled: Boolean
        get() = prefs.getBoolean(PREF_ENABLED, false)
        set(enabled) = prefs.edit { putBoolean(PREF_ENABLED, enabled) }

    var audioFile: Uri?
        get() = prefs.getString(PREF_AUDIO_FILE, null)?.let { Uri.parse(it) }
        set(uri) {
            val oldUri = audioFile
            if (oldUri == uri) {
                // URI is the same as before or both are null
                return
            }

            prefs.edit {
                if (uri != null) {
                    // Persist permissions for the new URI first
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    putString(PREF_AUDIO_FILE, uri.toString())
                } else {
                    remove(PREF_AUDIO_FILE)
                }
            }

            // Release persisted permissions on the old directory only after the new URI is set to
            // guarantee atomicity
            if (oldUri != null) {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
}