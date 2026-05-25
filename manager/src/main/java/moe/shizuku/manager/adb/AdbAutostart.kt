package moe.shizuku.manager.adb

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.starter.Starter
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AdbAutostart {

    private val running = AtomicBoolean(false)

    fun canAutostart(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
                && ShizukuSettings.getLastLaunchMode() == LaunchMethod.ADB
                && !Shizuku.pingBinder()
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun start(context: Context, initialDelayMs: Long = 0L, onFinished: (() -> Unit)? = null) {
        if (!running.compareAndSet(false, true)) {
            Log.i(AppConstants.TAG, "adbAutostart: already running, skipping")
            onFinished?.invoke()
            return
        }

        if (!isWifiConnected(context)) {
            Log.i(AppConstants.TAG, "adbAutostart: WiFi not connected, deferring to scheduler")
            running.set(false)
            onFinished?.invoke()
            return
        }

        Log.i(AppConstants.TAG, "adbAutostart: enabling wireless adb")
        val cr = context.contentResolver
        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (initialDelayMs > 0) {
                    Thread.sleep(initialDelayMs)
                }

                val maxAttempts = 3
                val timeoutPerAttempt = 5L // seconds
                var connected = false

                for (attempt in 1..maxAttempts) {
                    if (connected) break
                    if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) != 1) {
                        Log.w(AppConstants.TAG, "adbAutostart: adb_wifi_enabled is not 1 on attempt $attempt, re-enabling")
                        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                        Thread.sleep(2000)
                    }

                    Log.i(AppConstants.TAG, "adbAutostart: mDNS discovery attempt $attempt/$maxAttempts")
                    val latch = CountDownLatch(1)
                    val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
                        if (port <= 0) return@AdbMdns
                        Log.i(AppConstants.TAG, "adbAutostart: found adb service on port $port")
                        try {
                            val keystore = PreferenceAdbKeyStore(ShizukuSettings.getPreferences())
                            val key = AdbKey(keystore, "shizuku")
                            val client = AdbClient("127.0.0.1", port, key)
                            client.connect()
                            client.shellCommand(Starter.internalCommand, null)
                            client.close()
                            connected = true
                            Log.i(AppConstants.TAG, "adbAutostart: successfully started shizuku service")
                        } catch (e: Exception) {
                            Log.w(AppConstants.TAG, "adbAutostart: failed to connect on port $port", e)
                        }
                        latch.countDown()
                    }
                    adbMdns.start()
                    latch.await(timeoutPerAttempt, TimeUnit.SECONDS)
                    adbMdns.stop()

                    if (!connected && attempt < maxAttempts) {
                        Log.i(AppConstants.TAG, "adbAutostart: attempt $attempt timed out, retrying...")
                        Thread.sleep(2000)
                    }
                }

                if (!connected) {
                    Log.w(AppConstants.TAG, "adbAutostart: all attempts exhausted, could not start shizuku")
                }
            } finally {
                running.set(false)
                onFinished?.invoke()
            }
        }
    }
}
