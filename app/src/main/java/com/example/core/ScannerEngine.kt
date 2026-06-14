package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object ScannerEngine {

    // این تابع یک آی‌پی را با پورت مشخص چک می‌کند
    suspend fun checkIp(ip: String, port: Int, timeout: Int = 2000): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeout)
                    true // اتصال موفق بود (آی‌پی تمیز است)
                }
            } catch (e: Exception) {
                false // اتصال شکست خورد
            }
        }
    }
}
