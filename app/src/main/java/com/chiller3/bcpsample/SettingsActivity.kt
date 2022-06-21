package com.chiller3.bcpsample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        setTitle(R.string.app_name_full)
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {
        private lateinit var prefs: Preferences
        private lateinit var prefEnabled: SwitchPreferenceCompat
        private lateinit var prefVersion: Preference

        private val requestPermissionRequired =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                // Playback can still be enabled if optional permissions were not granted
                if (granted.all { it.key !in Permissions.REQUIRED || it.value }) {
                    prefEnabled.isChecked = true
                } else {
                    startActivity(Permissions.getAppInfoIntent(requireContext()))
                }
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val context = requireContext()

            prefs = Preferences(context)

            // If the desired state is enabled, set to disabled if runtime permissions have been
            // denied. The user will have to grant permissions again to re-enable the features.

            prefEnabled = findPreference(Preferences.PREF_ENABLED)!!
            if (prefEnabled.isChecked && !Permissions.haveRequired(context)) {
                prefEnabled.isChecked = false
            }
            prefEnabled.onPreferenceChangeListener = this

            prefVersion = findPreference(Preferences.PREF_VERSION)!!
            prefVersion.onPreferenceClickListener = this
            prefVersion.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})"
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            // No need to validate runtime permissions when disabling a feature
            if (newValue == false) {
                return true
            }

            val context = requireContext()

            when (preference) {
                prefEnabled -> if (Permissions.haveRequired(context)) {
                    return true
                } else {
                    // Ask for optional permissions the first time only
                    requestPermissionRequired.launch(Permissions.REQUIRED)
                }
            }

            return false
        }

        override fun onPreferenceClick(preference: Preference): Boolean {
            when (preference) {
                prefVersion -> {
                    val uri = Uri.parse(BuildConfig.PROJECT_URL_AT_COMMIT)
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
            }

            return false
        }
    }
}