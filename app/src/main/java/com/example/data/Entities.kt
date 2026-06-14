package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clean_ips")
data class CleanIpEntity(
    @PrimaryKey val ipAddress: String,
    val latencyMs: Int,
    val provider: String, // Cloudflare, Akamai, Fastly, Cloudfront, Gcore
    val operatorName: String, // MCI (همراه اول), Irancell (ایرانسل), Rightel (رایتل), WiFi (مخابرات/سایر)
    val status: String, // CLEAN, OPTIMAL, SLOW, BLOCKED
    val testTimestamp: Long = System.currentTimeMillis(),
    val successRate: Int = 100 // Connection success rate percentage, e.g. 100, 66, 33, 0
)

@Entity(tableName = "v2ray_nodes")
data class V2RayNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rawConfig: String, // raw vmess://, vless://, trojan://, ss://
    val protocol: String, // VMESS, VLESS, TROJAN, SHADOWSOCKS
    val serverHost: String,
    val port: Int,
    val pingMs: Int = -1, // -1 means untested
    val customSni: String = "", // e.g. fake host
    val assignedCleanIp: String = "", // Associated custom clean IP for SNI Spoofer injection
    val remarks: String = "",
    val lastTested: Long = 0,
    val downloadSpeedMbps: Double = 0.0,
    val ratingStars: Int = 0
)

@Dao
interface CleanIpDao {
    @Query("SELECT * FROM clean_ips ORDER BY latencyMs ASC")
    fun getAllCleanIps(): Flow<List<CleanIpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCleanIp(ip: CleanIpEntity)

    @Update
    suspend fun updateCleanIp(ip: CleanIpEntity)

    @Delete
    suspend fun deleteCleanIp(ip: CleanIpEntity)

    @Query("DELETE FROM clean_ips")
    suspend fun deleteAllCleanIps()
}

@Dao
interface V2RayNodeDao {
    @Query("SELECT * FROM v2ray_nodes ORDER BY id DESC")
    fun getAllNodes(): Flow<List<V2RayNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: V2RayNodeEntity)

    @Update
    suspend fun updateNode(node: V2RayNodeEntity)

    @Delete
    suspend fun deleteNode(node: V2RayNodeEntity)

    @Query("DELETE FROM v2ray_nodes")
    suspend fun deleteAllNodes()
}

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileName: String,
    val preferredOperator: String,
    val selectedProvidersJson: String, // e.g. "Cloudflare,Akamai"
    val targetHost: String,
    val fakeSni: String,
    val customSniForBuilder: String,
    val isAutoTestEnabled: Boolean = false,
    val isActive: Boolean = false
)

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()
}

@Entity(tableName = "vpn_subscriptions")
data class VpnSubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val providerName: String,
    val subscriptionUrl: String,
    val apiKey: String = "",
    val importTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface VpnSubscriptionDao {
    @Query("SELECT * FROM vpn_subscriptions ORDER BY id DESC")
    fun getAllSubscriptions(): Flow<List<VpnSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(sub: VpnSubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(sub: VpnSubscriptionEntity)
}
