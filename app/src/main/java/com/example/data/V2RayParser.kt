package com.example.data

import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URLDecoder
import java.net.URLEncoder

object V2RayParser {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    data class ParsedNode(
        val protocol: String,
        val remarks: String,
        val address: String,
        val port: Int,
        val idOrPassword: String,
        val streamType: String = "tcp",
        val path: String = "",
        val security: String = "",
        val sni: String = "",
        val host: String = ""
    )

    fun parse(url: String): ParsedNode? {
        val cleanUrl = url.trim()
        return try {
            when {
                cleanUrl.startsWith("vmess://", ignoreCase = true) -> parseVMess(cleanUrl)
                cleanUrl.startsWith("vless://", ignoreCase = true) -> parseUriNode("VLESS", cleanUrl)
                cleanUrl.startsWith("trojan://", ignoreCase = true) -> parseUriNode("TROJAN", cleanUrl)
                cleanUrl.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(cleanUrl)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVMess(url: String): ParsedNode? {
        val base64Data = url.substring("vmess://".length)
        val decodedBytes = Base64.decode(base64Data, Base64.URL_SAFE or Base64.DEFAULT)
        val json = String(decodedBytes)
        
        // Use a simple map since VMess json fields can vary
        val type: java.lang.reflect.Type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any>>(type)
        val map = adapter.fromJson(json) ?: return null

        val address = map["add"]?.toString() ?: ""
        val port = (map["port"] as? Number)?.toInt() 
            ?: map["port"]?.toString()?.toIntOrNull() 
            ?: 443
        val remarks = map["ps"]?.toString() ?: "VMess Node"
        val id = map["id"]?.toString() ?: ""
        val net = map["net"]?.toString() ?: "tcp"
        val path = map["path"]?.toString() ?: ""
        val tls = map["tls"]?.toString() ?: ""
        val sni = map["sni"]?.toString() ?: ""
        val host = map["host"]?.toString() ?: ""

        return ParsedNode(
            protocol = "VMESS",
            remarks = remarks,
            address = address,
            port = port,
            idOrPassword = id,
            streamType = net,
            path = path,
            security = tls,
            sni = sni,
            host = host
        )
    }

    private fun parseUriNode(protocol: String, url: String): ParsedNode? {
        // vless://uuid@host:port?query#remarks
        val schemeLength = if (protocol == "VLESS") 8 else 9
        val mainPart = url.substring(schemeLength)
        
        // Separate remarks
        val hashIndex = mainPart.indexOf('#')
        val remarks = if (hashIndex != -1) {
            URLDecoder.decode(mainPart.substring(hashIndex + 1), "UTF-8")
        } else {
            "$protocol Node"
        }
        
        val connectionString = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart
        
        val atIndex = connectionString.indexOf('@')
        if (atIndex == -1) return null
        val idOrPassword = connectionString.substring(0, atIndex)
        
        val rest = connectionString.substring(atIndex + 1)
        val queryIndex = rest.indexOf('?')
        val authority = if (queryIndex != -1) rest.substring(0, queryIndex) else rest
        val queryStr = if (queryIndex != -1) rest.substring(queryIndex + 1) else ""
        
        val colonIndex = authority.lastIndexOf(':')
        if (colonIndex == -1) return null
        val address = authority.substring(0, colonIndex)
        val portStr = authority.substring(colonIndex + 1)
        val port = portStr.toIntOrNull() ?: 443
        
        // Parse Query parameters
        val queryParams = queryStr.split("&").associate {
            val parts = it.split("=")
            if (parts.size == 2) {
                parts[0] to URLDecoder.decode(parts[1], "UTF-8")
            } else {
                parts[0] to ""
            }
        }
        
        return ParsedNode(
            protocol = protocol,
            remarks = remarks,
            address = address,
            port = port,
            idOrPassword = idOrPassword,
            streamType = queryParams["type"] ?: "tcp",
            path = queryParams["path"] ?: "",
            security = queryParams["security"] ?: "",
            sni = queryParams["sni"] ?: "",
            host = queryParams["host"] ?: ""
        )
    }

    private fun parseShadowsocks(url: String): ParsedNode? {
        val prefix = "ss://"
        val mainPart = url.substring(prefix.length)
        
        // ss://base64(method:password)@host:port#remarks
        val hashIndex = mainPart.indexOf('#')
        val remarks = if (hashIndex != -1) {
            URLDecoder.decode(mainPart.substring(hashIndex + 1), "UTF-8")
        } else {
            "Shadowsocks Node"
        }
        val connectionString = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart
        
        val atIndex = connectionString.indexOf('@')
        if (atIndex != -1) {
            // Decoded style
            val credentialsEncoded = connectionString.substring(0, atIndex)
            val credentials = try {
                String(Base64.decode(credentialsEncoded, Base64.DEFAULT))
            } catch (e: Exception) {
                credentialsEncoded
            }
            val rest = connectionString.substring(atIndex + 1)
            val colonIndex = rest.indexOf(':')
            if (colonIndex == -1) return null
            val address = rest.substring(0, colonIndex)
            val port = rest.substring(colonIndex + 1).toIntOrNull() ?: 8388
            
            return ParsedNode(
                protocol = "SHADOWSOCKS",
                remarks = remarks,
                address = address,
                port = port,
                idOrPassword = credentials
            )
        } else {
            // Fully base64-encoded style ss://base64(method:password@host:port)
            val DecodedBytes = Base64.decode(connectionString, Base64.DEFAULT)
            val decodedStr = String(DecodedBytes)
            val innerAt = decodedStr.indexOf('@')
            if (innerAt == -1) return null
            val credentials = decodedStr.substring(0, innerAt)
            val rightSide = decodedStr.substring(innerAt + 1)
            val innerColon = rightSide.indexOf(':')
            if (innerColon == -1) return null
            val address = rightSide.substring(0, innerColon)
            val port = rightSide.substring(innerColon + 1).toIntOrNull() ?: 8388
            
            return ParsedNode(
                protocol = "SHADOWSOCKS",
                remarks = remarks,
                address = address,
                port = port,
                idOrPassword = credentials
            )
        }
    }

    fun buildConfig(
        node: ParsedNode,
        cleanIp: String = "",
        customSni: String = ""
    ): String {
        // Use cleanIp as server address if supplied, otherwise node's original server address
        val activeAddress = if (cleanIp.isNotBlank()) cleanIp else node.address
        // Use customSni if TLS/Security is enabled, otherwise node's original SNI / host name
        val activeSni = if (customSni.isNotBlank()) customSni else (if (node.sni.isNotBlank()) node.sni else node.host)
        val activeHost = if (customSni.isNotBlank()) customSni else node.host

        return when (node.protocol) {
            "VMESS" -> {
                val map = mutableMapOf<String, Any>(
                    "v" to "2",
                    "ps" to ("${node.remarks} [NetSentry spoofed]"),
                    "add" to activeAddress,
                    "port" to node.port,
                    "id" to node.idOrPassword,
                    "aid" to 0,
                    "scy" to "auto",
                    "net" to node.streamType,
                    "type" to "none",
                    "host" to activeHost,
                    "path" to node.path,
                    "tls" to if (activeSni.isNotBlank()) "tls" else node.security,
                    "sni" to activeSni
                )
                val type: java.lang.reflect.Type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val json = adapter.toJson(map)
                val encodedJson = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
                "vmess://$encodedJson"
            }
            "VLESS", "TROJAN" -> {
                val scheme = node.protocol.lowercase()
                val queryParams = mutableListOf<String>()
                if (node.streamType.isNotBlank()) queryParams.add("type=${node.streamType}")
                if (node.path.isNotBlank()) queryParams.add("path=${URLEncoder.encode(node.path, "UTF-8")}")
                if (activeSni.isNotBlank()) queryParams.add("sni=${URLEncoder.encode(activeSni, "UTF-8")}")
                if (activeHost.isNotBlank()) queryParams.add("host=${URLEncoder.encode(activeHost, "UTF-8")}")
                if (node.security.isNotBlank()) queryParams.add("security=${node.security}")
                else if (activeSni.isNotBlank()) queryParams.add("security=tls")
                
                val queryStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
                val remarksEncoded = URLEncoder.encode("${node.remarks} [NetSentry spoofed]", "UTF-8")
                
                "$scheme://${node.idOrPassword}@$activeAddress:${node.port}$queryStr#$remarksEncoded"
            }
            else -> {
                // Return simple ss config
                val credentialsEncoded = Base64.encodeToString(node.idOrPassword.toByteArray(), Base64.NO_WRAP)
                val remarksEncoded = URLEncoder.encode("${node.remarks} [NetSentry]", "UTF-8")
                "ss://$credentialsEncoded@$activeAddress:${node.port}#$remarksEncoded"
            }
        }
    }
}
