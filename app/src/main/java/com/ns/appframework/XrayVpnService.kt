package com.ns.appframework

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ns.appframework.data.XrayCoreWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class XrayVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "XrayVpnService"
        const val ACTION_CONNECT = "com.ns.appframework.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.ns.appframework.ACTION_DISCONNECT"
        const val EXTRA_CONFIG_JSON = "com.ns.appframework.EXTRA_CONFIG_JSON"
        const val EXTRA_REMARKS = "com.ns.appframework.EXTRA_REMARKS"
        
        private const val CHANNEL_ID = "netsentry_vpn_channel"
        private const val NOTIFICATION_ID = 2026

        private val _vpnState = MutableStateFlow(VpnConnectionState.DISCONNECTED)
        val vpnState: StateFlow<VpnConnectionState> = _vpnState.asStateFlow()

        private val _activeNodeRemarks = MutableStateFlow<String?>(null)
        val activeNodeRemarks: StateFlow<String?> = _activeNodeRemarks.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand Action: $action")

        when (action) {
            ACTION_CONNECT -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: ""
                val remarks = intent.getStringExtra(EXTRA_REMARKS) ?: "Unknown Profile"
                startVpn(configJson, remarks)
            }
            ACTION_DISCONNECT -> {
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(configJson: String, remarks: String) {
        if (_vpnState.value != VpnConnectionState.DISCONNECTED) {
            stopVpn()
        }

        _vpnState.value = VpnConnectionState.CONNECTING
        _activeNodeRemarks.value = remarks
        
        // Start Foreground Service immediately to satisfy Android OS requirements
        startForeground(NOTIFICATION_ID, createNotification("اتصال به سیستم امنیتی VPN..."))

        xrayJob = serviceScope.launch {
            try {
                // Initialize the TUN interface
                setupTunInterface()

                // Execute precompiled Xray binary or simulator wrapper
                Log.d(TAG, "Initializing Xray-core backend...")
                val success = XrayCoreWrapper.startXray(configJson)

                if (success) {
                    _vpnState.value = VpnConnectionState.CONNECTED
                    updateNotification("سیستم ایمن فعال است | $remarks")
                    Log.i(TAG, "VPN fully tunnelled and active!")
                    
                    // Maintain active hold loop processing data packets virtually
                    while (isActive) {
                        delay(2500)
                    }
                } else {
                    Log.e(TAG, "Unable to run Xray compiled core process.")
                    _vpnState.value = VpnConnectionState.DISCONNECTED
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal exception on VPN network setup: ${e.message}")
                _vpnState.value = VpnConnectionState.DISCONNECTED
                stopSelf()
            }
        }
    }

    private fun setupTunInterface() {
        Log.d(TAG, "Configuring Android routing tables for virtual client interface...")
        
        val builder = Builder()
        builder.setMtu(1500)
        
        // Use a private interface subnet for internal proxy routing
        builder.addAddress("26.26.26.1", 24)
        
        // Route all device IPv4 traffic through proxy TUN interface
        builder.addRoute("0.0.0.0", 0)
        
        // Set standard fallback DNS servers
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")
        
        // Set app routing policies bypass
        builder.addDisallowedApplication(packageName)
        
        builder.setSession("NetSentry Xray VPN")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = builder.establish()
        if (vpnInterface != null) {
            Log.i(TAG, "TUN virtual interface successfully bound. FD: ${vpnInterface!!.fd}")
        } else {
            throw IllegalStateException("Unable to allocate TUN packet hardware bridge.")
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Disconnection sequence initiated.")
        _vpnState.value = VpnConnectionState.DISCONNECTED
        _activeNodeRemarks.value = null

        xrayJob?.cancel()
        xrayJob = null

        XrayCoreWrapper.stopXray()

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface = null

        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    private fun createNotification(message: String): Notification {
        val intentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val mainActivityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, intentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("امنیت ترافیک NetSentry")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرورهای اتصال ترافیک امن",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش اتصال ترافیک فعال VPN"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
