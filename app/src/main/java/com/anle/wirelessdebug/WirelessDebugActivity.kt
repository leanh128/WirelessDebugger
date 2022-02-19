package com.anle.wirelessdebug

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.anle.wirelessdebug.databinding.ActivityWirelessDebugBinding
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.timerTask


class WirelessDebugActivity : AppCompatActivity() {
    private var statusTrackerJob: Job? = null
    lateinit var binding: ActivityWirelessDebugBinding
    var timer: Timer? = null

    private var isUSBDebuggingEnabled = false
    private var isWifiConnected = false
    private lateinit var commandFragment: CommandFragment
    private lateinit var aboutFragment: AboutFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWirelessDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        commandFragment = CommandFragment()
        aboutFragment = AboutFragment()
        binding.pager.adapter = MyPagerAdapter(this)
        binding.btnRefresh.setOnClickListener {
            updateDeviceStatus()
        }
        binding.btnGetSsid.setOnClickListener {
            if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION))
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 10002)
        }
        binding.layoutWifi.setOnClickListener {

            val targetActivity =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Settings.ACTION_WIFI_ADD_NETWORKS
                else
                    Settings.ACTION_WIFI_SETTINGS
            startActivity(Intent(targetActivity).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        binding.layoutUsbDebug.setOnClickListener {
            when {
                !Utils.isDeveloperOptionsEnabled(this@WirelessDebugActivity) ->
                    showAlertTurnOnDevelopOptions()
                !Utils.isUSBDebuggingEnabled(this@WirelessDebugActivity) ->
                    showAlertTurnOnUSBDebugging()
                Utils.isUSBDebuggingEnabled(this@WirelessDebugActivity) ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10002) {
            updateDeviceStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        cancelStatusTracker()
        startStatusTracker()
    }

    override fun onPause() {
        super.onPause()
        cancelStatusTracker()
    }

    private fun isPermissionGranted(permission: String) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                ||
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED

    private fun updateDeviceStatus() {
        checkWifiConnection()
        checkUSBDebugging()
        val isWirelessConnectionReady = isUSBDebuggingEnabled && isWifiConnected
        kotlin.runCatching {
            commandFragment.setVisibility(isWirelessConnectionReady)
            if (isWirelessConnectionReady) {
                commandFragment.setCommand()
            }
        }
    }

    private fun checkUSBDebugging() {
        isUSBDebuggingEnabled = Utils.isUSBDebuggingEnabled(this)

        binding.tvUsbDebugStatus.apply {
            text =
                getString(if (isUSBDebuggingEnabled) android.R.string.ok else R.string.status_fail)
            setTextColor(
                ContextCompat.getColor(
                    this@WirelessDebugActivity,
                    if (Utils.isUSBDebuggingEnabled(this@WirelessDebugActivity)) R.color.colorPrimary else R.color.colorDarkOrange
                )
            )
        }
    }

    private fun showAlertTurnOnUSBDebugging() {
        AlertDialog.Builder(this).apply {
            title = getString(R.string.title_dialog_alert_usb_debugging_disabled)
            setMessage(R.string.message_dialog_alert_usb_debugging_disabled)
            setPositiveButton(
                R.string.button_go
            ) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }.show()
    }

    private fun showAlertTurnOnDevelopOptions() {
        AlertDialog.Builder(this).apply {
            title = getString(R.string.title_dialog_alert_develop_options_disabled)
            setMessage(R.string.message_dialog_alert_develop_options_disabled)
            setPositiveButton(
                R.string.button_go
            ) { _, _ ->
                startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }.show()
    }


    private fun checkWifiConnection() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo = wifiManager.connectionInfo
        isWifiConnected = wifiInfo.supplicantState == SupplicantState.COMPLETED
        if (isWifiConnected) {
            var wifiSSID = wifiInfo.ssid
            Log.d("leon", "wifi SSID: $wifiSSID")
            if (wifiSSID.startsWith("\"")) {
                wifiSSID = wifiSSID.substring(1, wifiSSID.length - 1)
            }
            binding.tvWifiConnectionStatus.apply {
                setTextColor(
                    ContextCompat.getColor(
                        this@WirelessDebugActivity,
                        R.color.colorPrimary
                    )
                )
                visibility =
                    if (wifiSSID.isNullOrBlank() || wifiSSID == "<unknown ssid>") View.GONE else View.VISIBLE
                text = wifiSSID
            }
            binding.btnGetSsid.visibility =
                if (wifiSSID.isNullOrBlank() || wifiSSID == "<unknown ssid>") View.VISIBLE else View.GONE

        } else {
            binding.tvWifiConnectionStatus.setTextColor(
                ContextCompat.getColor(this@WirelessDebugActivity, R.color.light_orange)
            )
            binding.tvWifiConnectionStatus.visibility = View.VISIBLE
            binding.btnGetSsid.visibility = View.GONE
            binding.tvWifiConnectionStatus.text = getString(R.string.status_wifi_not_connected)
        }
    }

    private fun startTrackingCPU() {
        releaseTimer()
        timer = Timer().apply {
            scheduleAtFixedRate(
                timerTask {
                    CPUChecker().start()
                }, 100L, 1000L
            )
        }
    }

    private fun releaseTimer() {
        timer?.run {
            cancel()
            purge()
        }
        timer = null
    }


    private fun startStatusTracker() {
        cancelStatusTracker()
        statusTrackerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(2000)
                runOnUiThread {
                    updateDeviceStatus()
                }
            }
        }
    }

    private fun cancelStatusTracker() {
        statusTrackerJob?.cancel()
        statusTrackerJob = null
    }

    fun swipeLeft() {
        binding.pager.currentItem = 1
    }

    inner class CPUChecker : Thread() {
        override fun run() {
            super.run()
            try {
//                val DATA = arrayOf("/system/bin/cat", "/proc/cpuinfo")
                val DATA = arrayOf("ls", "/proc/st")
                val processBuilder = ProcessBuilder(*DATA)
                val process = processBuilder.start()
                val inputStream: InputStream = process.inputStream
                val byteArry = ByteArray(1024)
                var output = ""
                while (inputStream.read(byteArry) != -1) {
                    output += String(byteArry)
                }
                inputStream.close()

                Log.d("leon", "CPU_INFO: ${output}")
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
    inner class MyPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> commandFragment
                else -> aboutFragment
            }
        }

        override fun getItemCount() = 2
    }
}
