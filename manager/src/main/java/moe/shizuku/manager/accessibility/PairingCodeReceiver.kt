package moe.shizuku.manager.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import moe.shizuku.manager.adb.AdbPairingService

class PairingCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PairingCodeReceiver"
        const val ACTION_PAIRING_CODE_FOUND = "moe.shizuku.manager.PAIRING_CODE_FOUND"
        const val EXTRA_PIN = "pin"
        
        fun createIntentFilter(): IntentFilter {
            return IntentFilter(ACTION_PAIRING_CODE_FOUND)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == ACTION_PAIRING_CODE_FOUND) {
            val pin = intent.getStringExtra(EXTRA_PIN)
            if (!pin.isNullOrEmpty() && pin.length == 6 && pin.all { it.isDigit() }) {
                Log.i(TAG, "Received valid PIN from accessibility service")
                
                // Create intent to send PIN to ADB pairing service
                val serviceIntent = Intent(context, AdbPairingService::class.java).apply {
                    action = "auto_pair"
                    putExtra("auto_pin", pin)
                }
                
                try {
                    context.startForegroundService(serviceIntent)
                    Log.i(TAG, "Started ADB pairing service with auto-detected PIN")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start ADB pairing service with PIN", e)
                }
            } else {
                Log.w(TAG, "Invalid PIN received: $pin")
            }
        }
    }
}
