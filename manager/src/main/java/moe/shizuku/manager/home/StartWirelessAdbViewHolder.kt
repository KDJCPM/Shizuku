package moe.shizuku.manager.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemProperties
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeStartWirelessAdbBinding
import moe.shizuku.manager.home.WadbNotEnabledDialogFragment
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.core.content.asActivity
import rikka.html.text.HtmlCompat
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import java.net.Inet4Address

class StartWirelessAdbViewHolder(private val binding: HomeStartWirelessAdbBinding, root: View) :
    BaseViewHolder<ServiceStatus?>(root) {

    companion object {
        val CREATOR = Creator<ServiceStatus?> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeStartWirelessAdbBinding.inflate(inflater, outer.root, true)
            StartWirelessAdbViewHolder(inner, outer.root)
        }
    }

    init {
        binding.button1.setOnClickListener { v: View ->
            onAdbClicked(v.context)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.button3.setOnClickListener { v: View ->
                CustomTabsHelper.launchUrlOrCopy(v.context, Helps.ADB_ANDROID11.get())
            }
            binding.button2.setOnClickListener { v: View ->
                onPairClicked(v.context)
            }
            binding.text1.movementMethod = LinkMovementMethod.getInstance()
            binding.text1.text = context.getString(R.string.home_wireless_adb_description)
                .toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
        } else {
            binding.text1.text = context.getString(R.string.home_wireless_adb_description_pre_11)
                .toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
            binding.button2.isVisible = false
            binding.button3.isVisible = false
        }
    }

    override fun onBind(payloads: MutableList<Any>) {
        super.onBind(payloads)
        
        // Update button states based on Shizuku status
        val isShizukuRunning = data?.isRunning == true
        Log.d("StartWirelessAdb", "onBind called - isShizukuRunning: $isShizukuRunning, status: ${data}")
        Log.d("StartWirelessAdb", "Button state before: enabled=${binding.button1.isEnabled}, alpha=${binding.button1.alpha}")
        
        // Disable start button if Shizuku is already running
        binding.button1.isEnabled = !isShizukuRunning
        binding.button1.alpha = if (isShizukuRunning) 0.5f else 1.0f
        
        Log.d("StartWirelessAdb", "Button state after: enabled=${binding.button1.isEnabled}, alpha=${binding.button1.alpha}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Keep pairing button enabled even if Shizuku is running
            // (user might want to pair for other purposes)
            binding.button2.isEnabled = true
            binding.button3.isEnabled = true
        }
    }

    private fun onAdbClicked(context: Context) {
        // Check if Shizuku is already running
        if (data?.isRunning == true) {
            Toast.makeText(context, R.string.home_status_service_is_running, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AdbDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
            return
        }

        val port = EnvironmentUtils.getAdbTcpPort()
        if (port > 0) {
            val host = "127.0.0.1"
            val intent = Intent(context, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_HOST, host)
                putExtra(StarterActivity.EXTRA_PORT, port)
            }
            context.startActivity(intent)
        } else {
            WadbNotEnabledDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun onPairClicked(context: Context) {
        if ((context.display?.displayId ?: -1) > 0) {
            // Running in a multi-display environment (e.g., Windows Subsystem for Android),
            // pairing dialog can be displayed simultaneously with Shizuku.
            // Input from notification is harder to use under this situation.
            AdbPairDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
        } else {
            context.startActivity(Intent(context, AdbPairingTutorialActivity::class.java))
        }
    }
}
