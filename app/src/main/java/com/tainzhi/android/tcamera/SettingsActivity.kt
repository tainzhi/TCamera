package com.tainzhi.android.tcamera

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.finish()
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<Preference>(this@SettingsFragment.requireContext().getString(R.string.settings_key_photo_zsl))?.apply {
                onPreferenceChangeListener = this@SettingsFragment
            }
            findPreference<Preference>(getString(R.string.settings_key_version_name))?.apply {
                val pkgName = context.packageName
                val pkgInfo = context.packageManager.getPackageInfo(pkgName, 0)
                val versionName = pkgInfo.versionName
                title = "version $versionName"
            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            val key = preference.key
            return true
        }

        companion object {
            private val TAG = SettingsFragment::class.java.simpleName
        }
    }
}