package com.ns.appframework.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CleanIpEntity::class, V2RayNodeEntity::class, UserProfileEntity::class, VpnSubscriptionEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cleanIpDao(): CleanIpDao
    abstract fun v2rayNodeDao(): V2RayNodeDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun vpnSubscriptionDao(): VpnSubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netsentry_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
