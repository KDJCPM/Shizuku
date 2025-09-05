package moe.shizuku.manager.adb

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import android.util.Log
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.accessibility.PairingCodeReceiver
import moe.shizuku.manager.accessibility.AccessibilityUtils
import rikka.core.ktx.unsafeLazy
import java.net.ConnectException

@TargetApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {

        const val notificationChannel = "adb_pairing"

        private const val tag = "AdbPairingService"

        private const val notificationId = 1
        private const val replyRequestId = 1
        private const val stopRequestId = 2
        private const val retryRequestId = 3
        private const val startAction = "start"
        private const val stopAction = "stop"
        private const val replyAction = "reply"
        private const val autoPairAction = "auto_pair"
        private const val remoteInputResultKey = "paring_code"
        private const val portKey = "paring_code"
        private const val autoPinKey = "auto_pin"

        fun startIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(startAction)
        }

        private fun stopIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(stopAction)
        }

        private fun replyIntent(context: Context, port: Int): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(replyAction).putExtra(portKey, port)
        }
    }

    private var adbMdns: AdbMdns? = null
    private var adbConnectMdns: AdbMdns? = null
    private var lastDiscoveredPort: Int = -1
    private var discoveredAdbPort: Int = -1
    private var pairingCodeReceiver: PairingCodeReceiver? = null
    private var isPairingInProgress = false
    private var lastPairingAttemptTime = 0L
    private val pairingCooldownMs = 3000L // 3 seconds between pairing attempts

    private val observer = Observer<Int> { port ->
        Log.i(tag, "Pairing service port: $port")
        if (port <= 0) return@Observer
        
        lastDiscoveredPort = port

        // Since the service could be killed before user finishing input,
        // we need to put the port into Intent
        val notification = createInputNotification(port)

        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
    
    private val adbConnectObserver = Observer<Int> { port ->
        Log.i(tag, "ADB connection port discovered: $port")
        if (port > 0) {
            discoveredAdbPort = port
        }
    }

    private var started = false

    override fun onCreate() {
        super.onCreate()

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                notificationChannel,
                getString(R.string.notification_channel_adb_pairing),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                setAllowBubbles(false)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = when (intent?.action) {
            startAction -> {
                onStart()
            }
            replyAction -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(remoteInputResultKey) ?: ""
                val port = intent.getIntExtra(portKey, -1)
                if (port != -1) {
                    onInput(code.toString(), port)
                } else {
                    onStart()
                }
            }
            autoPairAction -> {
                val pin = intent.getStringExtra(autoPinKey)
                if (!pin.isNullOrEmpty() && lastDiscoveredPort > 0) {
                    val currentTime = System.currentTimeMillis()
                    
                    // Check if pairing is already in progress
                    if (isPairingInProgress) {
                        Log.d(tag, "Pairing already in progress, ignoring auto-pair request")
                        return START_NOT_STICKY
                    }
                    
                    // Check cooldown period
                    if (currentTime - lastPairingAttemptTime < pairingCooldownMs) {
                        Log.d(tag, "Pairing cooldown active, ignoring auto-pair request")
                        return START_NOT_STICKY
                    }
                    
                    Log.i(tag, "Auto-pairing with PIN: $pin and port: $lastDiscoveredPort")
                    lastPairingAttemptTime = currentTime
                    isPairingInProgress = true
                    onInput(pin, lastDiscoveredPort)
                } else {
                    Log.w(tag, "Auto-pair action called but PIN or port not available")
                    onStart()
                }
            }
            stopAction -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                null
            }
            else -> {
                return START_NOT_STICKY
            }
        }
        if (notification != null) {
            try {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
            } catch (e: Throwable) {
                Log.e(tag, "startForeground failed", e)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && e is ForegroundServiceStartNotAllowedException) {
                    getSystemService(NotificationManager::class.java).notify(notificationId, notification)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (started) return
        started = true
        
        // Register receiver for auto-detected pairing codes
        if (pairingCodeReceiver == null) {
            pairingCodeReceiver = PairingCodeReceiver()
            val intentFilter = PairingCodeReceiver.createIntentFilter()
            
            // Use appropriate flags for Android 14+ (API 34+)
            if (Build.VERSION.SDK_INT >= 34) {
                registerReceiver(pairingCodeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pairingCodeReceiver, intentFilter)
            }
        }
        
        adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING, observer).apply { start() }
    }

    private fun stopSearch() {
        if (!started) return
        started = false
        
        // Unregister receiver
        pairingCodeReceiver?.let { 
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(tag, "Failed to unregister pairing code receiver", e)
            }
            pairingCodeReceiver = null
        }
        
        adbMdns?.stop()
        stopAdbPortDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSearch()
    }

    private fun onStart(): Notification {
        startSearch()
        return searchingNotification
    }

    private fun onInput(code: String, port: Int): Notification {
        GlobalScope.launch(Dispatchers.IO) {
            val host = "127.0.0.1"

            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                e.printStackTrace()
                return@launch
            }

            AdbPairingClient(host, port, code, key).runCatching {
                start()
            }.onFailure {
                handleResult(false, it)
            }.onSuccess {
                handleResult(it, null)
            }
        }

        return workingNotification
    }

    private fun handleResult(success: Boolean, exception: Throwable?) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val title: String
        val text: String?

        if (success) {
            Log.i(tag, "Pair succeed")
            isPairingInProgress = false // Reset pairing state on success

            title = "Pairing succeeded!"
            text = "Trying to auto-start Shizuku now..."

            stopSearch()
            
            // Auto-start Shizuku after successful pairing with delay to ensure readiness
            startShizukuDelayed()
        } else {
            isPairingInProgress = false // Reset pairing state on failure
            
            title = getString(R.string.notification_adb_pairing_failed_title)

            text = when (exception) {
                is ConnectException -> {
                    getString(R.string.cannot_connect_port)
                }
                is AdbInvalidPairingCodeException -> {
                    getString(R.string.paring_code_is_wrong)
                }
                is AdbKeyException -> {
                    getString(R.string.adb_error_key_store)
                }
                else -> {
                    exception?.let { Log.getStackTraceString(it) }
                }
            }

            if (exception != null) {
                Log.w(tag, "Pair failed", exception)
            } else {
                Log.w(tag, "Pair failed")
            }
        }

        getSystemService(NotificationManager::class.java).notify(
            notificationId,
            Notification.Builder(this, notificationChannel)
                .setColor(getColor(R.color.notification))
                .setSmallIcon(R.drawable.ic_system_icon)
                .setContentTitle(title)
                .setContentText(text)
                /*.apply {
                    if (!success) {
                        addAction(retryNotificationAction)
                    }
                }*/
                .build()
        )
        stopSelf()
    }

    private val stopNotificationAction by unsafeLazy {
        val pendingIntent = PendingIntent.getService(
            this,
            stopRequestId,
            stopIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_stop_searching),
            pendingIntent
        )
            .build()
    }

    private val retryNotificationAction by unsafeLazy {
        val pendingIntent = PendingIntent.getService(
            this,
            retryRequestId,
            startIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_retry),
            pendingIntent
        )
            .build()
    }

    private val replyNotificationAction by unsafeLazy {
        val remoteInput = RemoteInput.Builder(remoteInputResultKey).run {
            setLabel(getString(R.string.dialog_adb_pairing_paring_code))
            build()
        }

        val pendingIntent = PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, -1),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_input_paring_code),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun replyNotificationAction(port: Int): Notification.Action {
        // Ensure pending intent is created
        val action = replyNotificationAction

        PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, port),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return action
    }

    private val searchingNotification by unsafeLazy {
        Notification.Builder(this, notificationChannel)
            .setColor(getColor(R.color.notification))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(getString(R.string.notification_adb_pairing_searching_for_service_title))
            .addAction(stopNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification {
        val isAccessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        
        val title = if (isAccessibilityEnabled) {
            "Pairing service found - Auto-pairing enabled"
        } else {
            getString(R.string.notification_adb_pairing_service_found_title)
        }
        
        val builder = Notification.Builder(this, notificationChannel)
            .setColor(getColor(R.color.notification))
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_system_icon)
            
        if (isAccessibilityEnabled) {
            builder.setContentText("Trying to read PIN and connect to wireless debugging...")
        } else {
            builder.addAction(replyNotificationAction(port))
        }
        
        return builder.build()
    }

    private val workingNotification by unsafeLazy {
        Notification.Builder(this, notificationChannel)
            .setColor(getColor(R.color.notification))
            .setContentTitle(getString(R.string.notification_adb_pairing_working_title))
            .setSmallIcon(R.drawable.ic_system_icon)
            .build()
    }

    private fun startShizukuDelayed() {
        try {
            Log.i(tag, "Scheduling auto-start of Shizuku after successful pairing")
            
            // Start discovering ADB connection port
            startAdbPortDiscovery()
            
            // Use a handler to delay the start by 2 seconds to ensure ADB is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startShizuku()
            }, 2000L) // 2 second delay (reduced from 5)
        } catch (e: Exception) {
            Log.w(tag, "Failed to schedule auto-start of Shizuku", e)
        }
    }
    
    private fun startAdbPortDiscovery() {
        try {
            Log.i(tag, "Starting ADB port discovery for auto-start")
            adbConnectMdns = AdbMdns(this, AdbMdns.TLS_CONNECT, adbConnectObserver).apply { 
                start() 
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to start ADB port discovery", e)
        }
    }
    
    private fun stopAdbPortDiscovery() {
        try {
            adbConnectMdns?.stop()
            adbConnectMdns = null
        } catch (e: Exception) {
            Log.w(tag, "Failed to stop ADB port discovery", e)
        }
    }

    private fun startShizuku() {
        try {
            Log.i(tag, "Auto-starting Shizuku after successful pairing")
            
            // Stop ADB port discovery
            stopAdbPortDiscovery()
            
            // Try discovered ADB port first, then fallback to system property
            var adbPort = discoveredAdbPort
            if (adbPort <= 0) {
                adbPort = EnvironmentUtils.getAdbTcpPort()
                Log.i(tag, "Using system ADB TCP port: $adbPort")
            } else {
                Log.i(tag, "Using discovered ADB port: $adbPort")
            }
            
            if (adbPort <= 0) {
                Log.w(tag, "ADB TCP port not available, cannot auto-start Shizuku")
                return
            }
            
            // Launch StarterActivity with ADB mode
            val intent = Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                putExtra(StarterActivity.EXTRA_PORT, adbPort)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            
            // Send broadcast to dismiss tutorial after a short delay to ensure tutorial is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val notifyIntent = Intent("moe.shizuku.manager.SHIZUKU_AUTO_STARTED")
                    sendBroadcast(notifyIntent)
                    Log.i(tag, "Sent auto-start broadcast to dismiss tutorial (delayed)")
                } catch (e: Exception) {
                    Log.w(tag, "Failed to send delayed broadcast", e)
                }
            }, 1000L) // 1 second delay
            
        } catch (e: Exception) {
            Log.w(tag, "Failed to auto-start Shizuku", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
