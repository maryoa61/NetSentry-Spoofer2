package com.ns.appframework

import android.app.*
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

class XrayVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "netsentry_vpn_channel"
        private const val NOTIFICATION_ID = 2026
        const val ACTION_CONNECT = "com.ns.appframework.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.ns.appframework.ACTION_DISCONNECT"
        const val EXTRA_CONFIG_JSON = "com.ns.appframework.EXTRA_CONFIG_JSON"
        const val EXTRA_REMARKS = "com.ns.appframework.EXTRA_REMARKS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CONNECT) {
            val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: ""
            val remarks = intent.getStringExtra(EXTRA_REMARKS) ?: "Unknown"
            
            // اصلاح: استفاده از startForeground با نوع مشخص شده برای اندروید ۱۴+
            val notification = createNotification("در حال برقراری اتصال امن...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            startVpn(configJson, remarks)
        } else if (action == ACTION_DISCONNECT) {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(configJson: String, remarks: String) {
        xrayJob = serviceScope.launch {
            try {
                setupTunInterface()
                val success = XrayCoreWrapper.startXray(configJson)
                if (success) {
                    updateNotification("متصل شد | $remarks")
                } else {
                    stopVpn()
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطای بحرانی در هسته: ${e.message}")
                stopVpn()
            }
        }
    }

    private fun setupTunInterface() {
        val builder = Builder()
        builder.setMtu(1500)
        builder.addAddress("26.26.26.1", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("1.1.1.1")
        builder.setSession("NetSentry VPN")
        
        // جلوگیری از کرش در صورت عدم دسترسی
        vpnInterface = builder.establish() ?: throw IllegalStateException("TUN Allocation Failed")
    }

    private fun stopVpn() {
        xrayJob?.cancel()
        XrayCoreWrapper.stopXray()
        vpnInterface?.close()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, 
            packageManager.getLaunchIntentForPackage(packageName), 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetSentry VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // حتما یک آیکون معتبر بگذار
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
