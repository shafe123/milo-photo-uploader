package com.shafe.milo

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadRecordDao {
    @Query("SELECT * FROM upload_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<UploadRecord>>

    @Insert
    suspend fun insert(record: UploadRecord)

    @Query("SELECT * FROM upload_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: Long): UploadRecord?

    @Query(
        "UPDATE upload_records " +
            "SET correctedLabel = :correctedLabel, reinforcementStatus = :status, reinforcementSyncedAt = NULL " +
            "WHERE id = :recordId"
    )
    suspend fun setCorrection(recordId: Long, correctedLabel: String, status: String = ReinforcementStatus.PENDING)

    @Query(
        "UPDATE upload_records " +
            "SET reinforcementStatus = :status, reinforcementSyncedAt = :syncedAt " +
            "WHERE id = :recordId"
    )
    suspend fun setReinforcementSyncStatus(recordId: Long, status: String, syncedAt: Long?)
}

@Database(entities = [UploadRecord::class], version = 2, exportSchema = false)
abstract class MiloDatabase : RoomDatabase() {
    abstract fun uploadRecordDao(): UploadRecordDao

    companion object {
        @Volatile
        private var INSTANCE: MiloDatabase? = null

        fun getDatabase(context: Context): MiloDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MiloDatabase::class.java,
                    "milo_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE upload_records ADD COLUMN correctedLabel TEXT")
                db.execSQL("ALTER TABLE upload_records ADD COLUMN reinforcementStatus TEXT NOT NULL DEFAULT '${ReinforcementStatus.NONE}'")
                db.execSQL("ALTER TABLE upload_records ADD COLUMN reinforcementSyncedAt INTEGER")
            }
        }
    }
}
