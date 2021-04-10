package com.anle.wirelessdebug

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.anle.wirelessdebug.databinding.ActivityWirelessDebugBinding
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.timerTask


class WirelessDebugActivity : AppCompatActivity() {
    lateinit var binding: ActivityWirelessDebugBinding
    var timer: Timer? = null

    private var isUSBDebuggingEnabled = false
    private var isWifiConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWirelessDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
        releaseTimer()
        timer = Timer().apply {
            schedule(timerTask {
                runOnUiThread {
                    updateDeviceStatus()
                    CPUChecker().start()
                }
            }, 0, 3000)
        }
    }

    override fun onPause() {
        super.onPause()
        releaseTimer()
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
        binding.layoutCommand.visibility =
                if (isWirelessConnectionReady) View.VISIBLE else View.INVISIBLE
        if (isWirelessConnectionReady) {
            val ip = Utils.getIPAddress(true)
            binding.tvConnectionCommand.text =
                    SpannableString(getString(R.string.command_adb_connect, ip)).apply {
                        setSpan(
                                StyleSpan(Typeface.BOLD),
                                length - ip.length,
                                length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
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
                setTextColor(ContextCompat.getColor(this@WirelessDebugActivity, R.color.colorPrimary))
                visibility = if (wifiSSID.isNullOrBlank() || wifiSSID == "<unknown ssid>") View.GONE else View.VISIBLE
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
}
