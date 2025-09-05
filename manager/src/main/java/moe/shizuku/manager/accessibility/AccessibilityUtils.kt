package moe.shizuku.manager.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AccessibilityUtils {
    
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        val targetService = "${context.packageName}/${PairingCodeAccessibilityService::class.java.name}"
        
        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.name == PairingCodeAccessibilityService::class.java.name &&
            service.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }
    
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    fun openAppAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        // Try to directly open the service settings if possible
        intent.putExtra(":settings:fragment_args_key", "${context.packageName}/${PairingCodeAccessibilityService::class.java.name}")
        context.startActivity(intent)
    }
}
