package com.ns.appframework.data

import android.util.Log

/**
 * Pipeline to automatically match raw nodes, determine cleanest available latency IP,
 * select the optimal fake host SNI spoofer for specific ISPs, and build the final JSON format config.
 */
object ConfigAutomator {
    private const val TAG = "ConfigAutomator"

    /**
     * Maps user operator selection into robust SNI spoofer domains configured for bypass.
     */
    fun getFakeSniForOperator(operatorName: String): String {
        val op = operatorName.uppercase()
        return when {
            op.contains("MCI") || op.contains("همراه") -> "tg.mci.ir"
            op.contains("IRANCELL") || op.contains("ایرانسل") -> "app.snapp.ir"
            op.contains("RIGHTEL") || op.contains("رایتل") -> "my.rightel.ir"
            op.contains("WIFI") || op.contains("مخابرات") -> "tci.ir"
            else -> "tg.mci.ir" // Standard high-availability fallback
        }
    }

    /**
     * Filters through scanned IPs to locate the lowest latency (best-performing) IP for the requested ISP.
     */
    fun selectBestCleanIp(
        cleanIps: List<CleanIpEntity>,
        operatorName: String
    ): String? {
        if (cleanIps.isEmpty()) return null
        
        val op = operatorName.uppercase()
        val mciMatch = op.contains("MCI") || op.contains("همراه")
        val irancellMatch = op.contains("IRANCELL") || op.contains("ایرانسل")
        val rightelMatch = op.contains("RIGHTEL") || op.contains("رایتل")
        val wifiMatch = op.contains("WIFI") || op.contains("مخابرات")

        // 1. Prioritize filtering by operator Name matching
        val matchingIps = cleanIps.filter { ip ->
            val dbOp = ip.operatorName.uppercase()
            when {
                mciMatch && (dbOp.contains("MCI") || dbOp.contains("همراه")) -> true
                irancellMatch && (dbOp.contains("IRANCELL") || dbOp.contains("ایرانسل")) -> true
                rightelMatch && (dbOp.contains("RIGHTEL") || dbOp.contains("رایتل")) -> true
                wifiMatch && (dbOp.contains("WIFI") || dbOp.contains("مخابرات")) -> true
                else -> dbOp.contains(op)
            }
        }.sortedBy { it.latencyMs }

        if (matchingIps.isNotEmpty()) {
            return matchingIps.first().ipAddress
        }

        // 2. Generic fallback if ISP-specific IP isn't scanned: get overall lowest latency clean IP
        return cleanIps.firstOrNull()?.ipAddress
    }

    data class AutomationResult(
        val configJson: String,
        val cleanIp: String,
        val fakeSni: String,
        val nodeRemarks: String
    )

    /**
     * Automatically completes parsing, IP lookup, host spoof selection and config synthesis.
     */
    fun automate(
        node: V2RayNodeEntity,
        cleanIps: List<CleanIpEntity>,
        operatorName: String
    ): AutomationResult? {
        val parsed = V2RayParser.parse(node.rawConfig) ?: return null

        // 1. Resolve optimal IP dynamically
        val resolvedIp = selectBestCleanIp(cleanIps, operatorName) ?: parsed.address

        // 2. Resolve target bypass spoof domain
        val resolvedSni = getFakeSniForOperator(operatorName)

        // 3. Build the Xray JSON schema configuration
        Log.i(TAG, "ConfigAutomator: Auto-pipeline complete. IP=$resolvedIp, SNI=$resolvedSni, operator=$operatorName")
        val generatedJson = XrayConfigGenerator.generateJson(parsed, resolvedIp, resolvedSni)

        return AutomationResult(
            configJson = generatedJson,
            cleanIp = resolvedIp,
            fakeSni = resolvedSni,
            nodeRemarks = "${node.name} (Auto-$operatorName)"
        )
    }
}
