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
import com.anle.wirelessdebug.databinding.ActivityWirelessDebugBinding
import java.io.BufferedReader
import java.io.InputStreamReader


class WirelessDebugActivity : AppCompatActivity() {
    companion object {
        const val ADBD_PORT = 5555
    }

    lateinit var binding: ActivityWirelessDebugBinding

    private var isRootedDevice = false
    private var isUSBDebuggingEnabled = false
    private var isADBPortOpened = false
    private var isWifiConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWirelessDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 10001)
            }
        }

    }


    override fun onResume() {
        super.onResume()
        updateDeviceStatus()
    }

    private fun updateDeviceStatus() {
        checkWifiConnection()
        checkRooted()
        checkUSBDebugging()
        val isWirelessConnectionReady = isUSBDebuggingEnabled && isADBPortOpened && isWifiConnected
        binding.layoutCommand.visibility =
                if (isWirelessConnectionReady) View.VISIBLE else View.INVISIBLE
        if (isWirelessConnectionReady) {
            val ip = Utils.getIPAddress(false)
            binding.tvConnectionCommand.text = getString(R.string.command_adb_connect, ip)
        }
    }

    private fun checkUSBDebugging() {
        isUSBDebuggingEnabled = Utils.isUSBDebuggingEnabled(this)

        binding.tvUsbDebugStatus.apply {
            text =
                    getString(if (isUSBDebuggingEnabled) R.string.status_good else R.string.status_fail)
            setOnClickListener {
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

    private fun checkRooted() {
        isRootedDevice = CommonUtils.isRooted(this)
        binding.tvDeviceType.apply {
            text =
                    getString(if (isRootedDevice) R.string.device_type_rooted else R.string.device_type_normal)
            setTextColor(
                    ContextCompat.getColor(
                            this@WirelessDebugActivity,
                            if (isRootedDevice) R.color.colorDarkOrange else R.color.white

                    )
            )
        }

    }

    private fun checkWifiConnection() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        val wifiInfo: WifiInfo = wifiManager.connectionInfo

        binding.tvWifiConnectionStatus.apply {
            isWifiConnected = wifiInfo.supplicantState == SupplicantState.COMPLETED

            text =
                    if (isWifiConnected) wifiInfo.ssid.toString() else getString(R.string.status_wifi_not_connected)
            setTextColor(
                    ContextCompat.getColor(
                            this@WirelessDebugActivity,
                            if (isWifiConnected) R.color.colorPrimary else R.color.light_orange
                    )
            )
            setOnClickListener {
                val targetActivity =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            Settings.ACTION_WIFI_ADD_NETWORKS
                        else
                            Settings.ACTION_WIFI_SETTINGS
                startActivity(Intent(targetActivity).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }
}
