package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

class TailGuardTypeConverters {
    @TypeConverter
    fun fromMatchMode(mode: MatchMode): String = mode.name

    @TypeConverter
    fun toMatchMode(value: String): MatchMode =
        runCatching { MatchMode.valueOf(value) }.getOrDefault(MatchMode.FINGERPRINT)

    @TypeConverter
    fun fromEventType(type: TransitionEventType): String = type.name

    @TypeConverter
    fun toEventType(value: String): TransitionEventType =
        runCatching { TransitionEventType.valueOf(value) }.getOrDefault(TransitionEventType.MANUAL_TOGGLE)
}

@Dao
interface TrustedNetworkDao {
    @Query("SELECT * FROM trusted_networks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrustedNetworkEntity>>

    @Query("SELECT * FROM trusted_networks WHERE isEnabled = 1")
    suspend fun getEnabledNetworks(): List<TrustedNetworkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: TrustedNetworkEntity): Long

    @Update
    suspend fun update(network: TrustedNetworkEntity)

    @Query("DELETE FROM trusted_networks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TransitionLogDao {
    @Query("SELECT * FROM connection_transition_logs ORDER BY timestamp DESC LIMIT 300")
    fun observeRecentLogs(): Flow<List<TransitionLogEntity>>

    @Query("SELECT * FROM connection_transition_logs ORDER BY timestamp DESC LIMIT 500")
    suspend fun getAllForExport(): List<TransitionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TransitionLogEntity)

    @Query("DELETE FROM connection_transition_logs")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM connection_transition_logs 
        WHERE id NOT IN (
            SELECT id FROM connection_transition_logs ORDER BY timestamp DESC LIMIT 500
        )
        """
    )
    suspend fun pruneOldEntries()
}

@Database(
    entities = [TrustedNetworkEntity::class, TransitionLogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TailGuardTypeConverters::class)
abstract class TailGuardDatabase : RoomDatabase() {
    abstract fun trustedNetworkDao(): TrustedNetworkDao
    abstract fun transitionLogDao(): TransitionLogDao

    companion object {
        @Volatile
        private var INSTANCE: TailGuardDatabase? = null

        fun getInstance(context: Context): TailGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TailGuardDatabase::class.java,
                    "tailguard_local.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
