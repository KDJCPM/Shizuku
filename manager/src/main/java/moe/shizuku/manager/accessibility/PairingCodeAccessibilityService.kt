package moe.shizuku.manager.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.util.regex.Pattern

@SuppressLint("AccessibilityPolicy")
@RequiresApi(Build.VERSION_CODES.R)
class PairingCodeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PairingAccessibility"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        
        // Pattern to match 6-digit pairing codes
        private val PIN_PATTERN = Pattern.compile("\\b\\d{6}\\b")
        
        // Possible text patterns that might contain the PIN
        private val PIN_KEYWORDS = arrayOf(
            "pairing code",
            "pair device",
            "wireless debugging",
            "adb",
            "debugging",
            "code",
            "pairing",
            "pair with device"
        )
        
        // Rate limiting and duplicate prevention
        private const val PIN_SEND_COOLDOWN_MS = 2000L // 2 seconds between sends
        private const val MAX_SAME_PIN_SENDS = 3 // Max times to send same PIN
    }
    
    private var lastPinSent: String? = null
    private var lastPinSentTime = 0L
    private var samePinSendCount = 0
    private var lastProcessedEventTime = 0L
    private var isProcessingPin = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || isProcessingPin) return

        // Only process events from system UI and Settings
        val packageName = event.packageName?.toString()
        if (packageName != SYSTEM_UI_PACKAGE && packageName != SETTINGS_PACKAGE) {
            return
        }

        // Throttle event processing to reduce spam
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedEventTime < 500) { // 500ms throttle
            return
        }
        lastProcessedEventTime = currentTime

        Log.d(TAG, "Processing accessibility event from $packageName, type: ${event.eventType}")

        // Focus on content change and window state change events
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Check if this is a window state change that might indicate dialog closed
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val className = event.className?.toString()
                    Log.d(TAG, "Window state changed: $className")
                    
                    // Reset PIN tracking when dialogs are closed
                    if (className?.contains("Dialog") == false && className?.contains("Activity") == true) {
                        Log.d(TAG, "Dialog likely closed, resetting PIN tracking")
                        resetPinTracking()
                    }
                }
                
                searchForPairingCode(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                searchForPairingCode(event)
            }
        }
    }
    
    private fun resetPinTracking() {
        lastPinSent = null
        lastPinSentTime = 0L
        samePinSendCount = 0
        isProcessingPin = false
        Log.d(TAG, "PIN tracking reset")
    }

    private fun searchForPairingCode(event: AccessibilityEvent) {
        if (isProcessingPin) return
        
        try {
            isProcessingPin = true
            Log.d(TAG, "Searching for pairing code in event")
            
            // Search for PIN in the current event text first
            event.text?.forEach { text ->
                if (!text.isNullOrEmpty()) {
                    Log.d(TAG, "Event text: $text")
                    val pin = extractPinFromText(text.toString())
                    if (pin != null) {
                        Log.i(TAG, "Found PIN in event text: $pin")
                        sendPinToAdbService(pin)
                        return
                    }
                }
            }

            // Search recursively through all nodes
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                Log.d(TAG, "Searching through node tree")
                searchNodeTreeForPin(rootNode)
            } else {
                Log.d(TAG, "No root node available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for pairing code", e)
        } finally {
            isProcessingPin = false
        }
    }

    private fun searchNodeTreeForPin(node: AccessibilityNodeInfo?) {
        if (node == null) return

        try {
            // Check current node text
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrEmpty()) {
                val pin = extractPinFromText(nodeText)
                if (pin != null) {
                    Log.i(TAG, "Found PIN in node text: $pin")
                    sendPinToAdbService(pin)
                    return
                }
            }

            // Check content description
            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrEmpty()) {
                val pin = extractPinFromText(contentDesc)
                if (pin != null) {
                    Log.i(TAG, "Found PIN in content description: $pin")
                    sendPinToAdbService(pin)
                    return
                }
            }

            // Recursively check child nodes
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    searchNodeTreeForPin(child)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching node tree", e)
        }
    }

    private fun extractPinFromText(text: String): String? {
        if (text.isEmpty()) return null

        Log.d(TAG, "Analyzing text for PIN: '$text'")

        // Look for 6-digit patterns
        val matcher = PIN_PATTERN.matcher(text)
        while (matcher.find()) {
            val match = matcher.group()
            
            // First check if the text contains any PIN-related keywords
            val lowerText = text.lowercase()
            val containsKeyword = PIN_KEYWORDS.any { lowerText.contains(it) }
            
            if (containsKeyword) {
                Log.i(TAG, "Found PIN with keyword context: $match")
                return match
            }
            
            // Also check for standalone 6-digit numbers in dialog contexts
            // Verify it's likely a PIN (not a time, phone number, etc.)
            val beforeChar = if (matcher.start() > 0) text[matcher.start() - 1] else ' '
            val afterChar = if (matcher.end() < text.length) text[matcher.end()] else ' '
            
            // If it's exactly 6 digits and surrounded by spaces, punctuation, or at boundaries
            if (!beforeChar.isDigit() && !afterChar.isDigit()) {
                // Additional heuristic: avoid common false positives like times (125959)
                val matchInt = match.toIntOrNull()
                if (matchInt != null && matchInt >= 100000 && matchInt <= 999999) {
                    // Avoid sequences that look like times (hours > 23 are unlikely to be time)
                    val firstTwo = matchInt / 10000
                    val middleTwo = (matchInt / 100) % 100
                    val lastTwo = matchInt % 100
                    
                    // If it doesn't look like HHMMSS format, it's probably a PIN
                    if (firstTwo > 23 || middleTwo > 59 || lastTwo > 59) {
                        Log.i(TAG, "Found potential PIN: $match")
                        return match
                    }
                }
            }
        }

        return null
    }

    private fun sendPinToAdbService(pin: String) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Check if this is the same PIN we recently sent
            if (pin == lastPinSent) {
                // Check cooldown period
                if (currentTime - lastPinSentTime < PIN_SEND_COOLDOWN_MS) {
                    return
                }
                
                // Check max send count for same PIN
                if (samePinSendCount >= MAX_SAME_PIN_SENDS) {
                    return
                }
                
                samePinSendCount++
            } else {
                // New PIN, reset counters
                samePinSendCount = 1
                lastPinSent = pin
            }
            
            lastPinSentTime = currentTime
            
            Log.i(TAG, "Sending PIN to ADB service: $pin (attempt #$samePinSendCount)")
            
            // Find the port from ADB service if it's running
            // For now, we'll use a broadcast to communicate the PIN
            val intent = Intent("moe.shizuku.manager.PAIRING_CODE_FOUND").apply {
                putExtra("pin", pin)
                setPackage(packageName)
            }
            sendBroadcast(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending PIN to ADB service", e)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }
}
