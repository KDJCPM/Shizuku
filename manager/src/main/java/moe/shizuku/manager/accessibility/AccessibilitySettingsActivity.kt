package moe.shizuku.manager.accessibility

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity

class AccessibilitySettingsActivity : AppBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.accessibility_settings_activity)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, AccessibilitySettingsFragment())
                .commit()
        }
    }
    
    class AccessibilitySettingsFragment : PreferenceFragmentCompat() {
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.accessibility_preferences, rootKey)
            
            val enabledPref = findPreference<SwitchPreference>("accessibility_enabled")
            enabledPref?.summary = getString(R.string.accessibility_service_setting_description)
        }
    }
}
