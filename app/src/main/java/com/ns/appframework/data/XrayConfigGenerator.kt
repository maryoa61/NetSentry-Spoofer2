package com.ns.appframework.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object XrayConfigGenerator {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /**
     * Converts a base ParsedNode into a fully configured, production-ready
     * client-side JSON format utilized by the Xray-core engine.
     */
    fun generateJson(
        node: V2RayParser.ParsedNode,
        cleanIp: String = "",
        customSni: String = ""
    ): String {
        val serverAddress = if (cleanIp.isNotBlank()) cleanIp else node.address
        val serverSni = if (customSni.isNotBlank()) customSni else (if (node.sni.isNotBlank()) node.sni else node.host)
        val serverHost = if (customSni.isNotBlank()) customSni else node.host

        // Base Xray client configurations structure
        val logConfig = mapOf(
            "loglevel" to "warning"
        )

        val dnsConfig = mapOf(
            "servers" to listOf("1.1.1.1", "8.8.8.8", "localhost")
        )

        // Local listen ports (inbounds) for client proxy loopback
        val inboundsConfig = listOf(
            mapOf(
                "tag" to "socks-in",
                "port" to 10808,
                "protocol" to "socks",
                "settings" to mapOf(
                    "auth" to "noauth",
                    "udp" to true,
                    "ip" to "127.0.0.1"
                )
            ),
            mapOf(
                "tag" to "http-in",
                "port" to 10809,
                "protocol" to "http",
                "settings" to mapOf(
                    "auth" to "noauth",
                    "ip" to "127.0.0.1"
                )
            )
        )

        // Dynamic Outbound node configuration based on client protocol
        val proxyOutbound = when (node.protocol.uppercase()) {
            "VMESS" -> buildVMessOutbound(node, serverAddress, serverSni, serverHost)
            "VLESS" -> buildVLessOutbound(node, serverAddress, serverSni, serverHost)
            "TROJAN" -> buildTrojanOutbound(node, serverAddress, serverSni, serverHost)
            "SHADOWSOCKS" -> buildShadowsocksOutbound(node, serverAddress)
            else -> buildDirectOutbound() // Fallback direct
        }

        val outboundsConfig = listOf(
            proxyOutbound,
            buildDirectOutbound(),
            mapOf(
                "protocol" to "blackhole",
                "tag" to "blocked",
                "settings" to emptyMap<String, Any>()
            )
        )

        // Routing logic to capture direct private networks
        val routingConfig = mapOf(
            "domainStrategy" to "IPIfNonMatch",
            "rules" to listOf(
                mapOf(
                    "type" to "field",
                    "ip" to listOf("0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24", "192.88.99.0/24", "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4", "::1/128", "fc00::/7", "fe80::/10"),
                    "outboundTag" to "direct"
                )
            )
        )

        val xrayConfigMap = mapOf(
            "log" to logConfig,
            "dns" to dnsConfig,
            "inbounds" to inboundsConfig,
            "outbounds" to outboundsConfig,
            "routing" to routingConfig
        )

        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any>>(type)
        return adapter.indent("  ").toJson(xrayConfigMap)
    }

    private fun buildVMessOutbound(
        node: V2RayParser.ParsedNode,
        address: String,
        sni: String,
        host: String
    ): Map<String, Any> {
        val userMap = mapOf(
            "id" to node.idOrPassword,
            "alterId" to 0,
            "security" to "auto"
        )

        val serverMap = mapOf(
            "address" to address,
            "port" to node.port,
            "users" to listOf(userMap)
        )

        return mapOf(
            "protocol" to "vmess",
            "tag" to "proxy",
            "settings" to mapOf(
                "vnext" to listOf(serverMap)
            ),
            "streamSettings" to buildStreamSettings(node, sni, host)
        )
    }

    private fun buildVLessOutbound(
        node: V2RayParser.ParsedNode,
        address: String,
        sni: String,
        host: String
    ): Map<String, Any> {
        val userMap = mapOf(
            "id" to node.idOrPassword,
            "encryption" to "none"
        )

        val serverMap = mapOf(
            "address" to address,
            "port" to node.port,
            "users" to listOf(userMap)
        )

        return mapOf(
            "protocol" to "vless",
            "tag" to "proxy",
            "settings" to mapOf(
                "vnext" to listOf(serverMap)
            ),
            "streamSettings" to buildStreamSettings(node, sni, host)
        )
    }

    private fun buildTrojanOutbound(
        node: V2RayParser.ParsedNode,
        address: String,
        sni: String,
        host: String
    ): Map<String, Any> {
        val serverMap = mapOf(
            "address" to address,
            "port" to node.port,
            "password" to node.idOrPassword
        )

        return mapOf(
            "protocol" to "trojan",
            "tag" to "proxy",
            "settings" to mapOf(
                "servers" to listOf(serverMap)
            ),
            "streamSettings" to buildStreamSettings(node, sni, host)
        )
    }

    private fun buildShadowsocksOutbound(
        node: V2RayParser.ParsedNode,
        address: String
    ): Map<String, Any> {
        // SS formats standard credentials method:password
        val parts = node.idOrPassword.split(":", limit = 2)
        val method = if (parts.size == 2) parts[0] else "aes-256-gcm"
        val password = if (parts.size == 2) parts[1] else node.idOrPassword

        val serverMap = mapOf(
            "address" to address,
            "port" to node.port,
            "method" to method,
            "password" to password
        )

        return mapOf(
            "protocol" to "shadowsocks",
            "tag" to "proxy",
            "settings" to mapOf(
                "servers" to listOf(serverMap)
            )
        )
    }

    private fun buildDirectOutbound(): Map<String, Any> {
        return mapOf(
            "protocol" to "freedom",
            "tag" to "direct",
            "settings" to emptyMap<String, Any>()
        )
    }

    private fun buildStreamSettings(
        node: V2RayParser.ParsedNode,
        sni: String,
        host: String
    ): Map<String, Any> {
        val streamSettingsMap = mutableMapOf<String, Any>()
        
        // Transports ws, grpc, etc.
        val networkType = if (node.streamType.isNotBlank()) node.streamType.lowercase() else "tcp"
        streamSettingsMap["network"] = networkType

        // Transport headers path/host
        when (networkType) {
            "ws" -> {
                val wsMap = mutableMapOf<String, Any>()
                if (node.path.isNotBlank()) wsMap["path"] = node.path
                if (host.isNotBlank()) wsMap["headers"] = mapOf("Host" to host)
                streamSettingsMap["wsSettings"] = wsMap
            }
            "grpc" -> {
                val grpcMap = mutableMapOf<String, Any>()
                if (node.path.isNotBlank()) grpcMap["serviceName"] = node.path
                streamSettingsMap["grpcSettings"] = grpcMap
            }
        }

        // Security settings (TLS / REALITY)
        val securityType = if (node.security.isNotBlank()) node.security.lowercase() else (if (sni.isNotBlank()) "tls" else "none")
        streamSettingsMap["security"] = securityType

        if (securityType == "tls" && sni.isNotBlank()) {
            streamSettingsMap["tlsSettings"] = mapOf(
                "serverName" to sni,
                "allowInsecure" to true
            )
        }

        return streamSettingsMap
    }
}
