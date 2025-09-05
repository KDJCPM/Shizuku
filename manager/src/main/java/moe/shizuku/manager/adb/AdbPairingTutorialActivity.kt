package moe.shizuku.manager.adb

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.accessibility.AccessibilityUtils
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AdbPairingTutorialActivityBinding
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppBarActivity() {

    private lateinit var binding: AdbPairingTutorialActivityBinding

    private var notificationEnabled: Boolean = false
    private var accessibilityEnabled: Boolean = false
    
    private val shizukuAutoStartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i("AdbPairingTutorial", "Broadcast received in tutorial: ${intent?.action}")
            if (intent?.action == "moe.shizuku.manager.SHIZUKU_AUTO_STARTED") {
                Log.i("AdbPairingTutorial", "Shizuku auto-started, finishing tutorial")
                try {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Log.i("AdbPairingTutorial", "Tutorial activity finishing normally")
                            finish()
                        } else {
                            Log.w("AdbPairingTutorial", "Tutorial activity already finishing/destroyed")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdbPairingTutorial", "Error finishing tutorial", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this

        binding = AdbPairingTutorialActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        notificationEnabled = isNotificationEnabled()
        accessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)

        if (notificationEnabled) {
            startPairingService()
        }
        
        // Register receiver for Shizuku auto-start notifications
        val filter = IntentFilter("moe.shizuku.manager.SHIZUKU_AUTO_STARTED")
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(shizukuAutoStartReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(shizukuAutoStartReceiver, filter)
        }
        Log.i("AdbPairingTutorial", "Auto-start receiver registered for tutorial auto-exit")

        binding.apply {
            syncNotificationEnabled()
            syncAccessibilityEnabled()

            if (DeviceCompatibility.isMiui()) {
                miui.isVisible = true
            }

            developerOptions.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                intent.putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                }
            }

            notificationOptions.setOnClickListener {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                }
            }
            
            accessibilityOptions.setOnClickListener {
                showAccessibilityPermissionDialog()
            }
        }
    }

    private fun syncNotificationEnabled() {
        binding.apply {
            step1.isVisible = notificationEnabled
            step2.isVisible = notificationEnabled
            step3.isVisible = notificationEnabled
            network.isVisible = notificationEnabled
            notification.isVisible = notificationEnabled
            notificationDisabled.isGone = notificationEnabled
        }
    }
    
    private fun syncAccessibilityEnabled() {
        binding.apply {
            accessibilityEnabled.isVisible = this@AdbPairingTutorialActivity.accessibilityEnabled
            accessibilityDisabled.isGone = this@AdbPairingTutorialActivity.accessibilityEnabled
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val context = this

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.notificationChannel)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun onResume() {
        super.onResume()

        val newNotificationEnabled = isNotificationEnabled()
        val newAccessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        
        if (newNotificationEnabled != notificationEnabled) {
            notificationEnabled = newNotificationEnabled
            syncNotificationEnabled()

            if (newNotificationEnabled) {
                startPairingService()
            }
        }
        
        if (newAccessibilityEnabled != accessibilityEnabled) {
            accessibilityEnabled = newAccessibilityEnabled
            syncAccessibilityEnabled()
        }
    }

    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(this)
        try {
            startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(this, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                startService(intent)
            }
        }
    }
    
    private fun showAccessibilityPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_permission_required_title)
            .setMessage(R.string.accessibility_permission_required_message)
            .setPositiveButton(R.string.accessibility_permission_button) { _, _ ->
                AccessibilityUtils.openAppAccessibilitySettings(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(shizukuAutoStartReceiver)
        } catch (e: Exception) {
            Log.w("AdbPairingTutorial", "Failed to unregister receiver", e)
        }
    }
}
