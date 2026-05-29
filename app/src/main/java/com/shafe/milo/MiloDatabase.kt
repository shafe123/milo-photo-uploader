package com.shafe.milo

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: UploadRecord)

    @Query("SELECT * FROM upload_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: Long): UploadRecord?

    @Query("SELECT * FROM upload_records WHERE localUri = :uri LIMIT 1")
    suspend fun getRecordByUri(uri: String): UploadRecord?

    @Query(
        "UPDATE upload_records " +
            "SET correctedLabel = :correctedLabel, reinforcementStatus = :status, reinforcementSyncedAt = NULL " +
            "WHERE id = :recordId"
    )
    suspend fun setCorrection(recordId: Long, correctedLabel: String, status: String)

    @Query(
        "UPDATE upload_records " +
            "SET reinforcementStatus = :status, reinforcementSyncedAt = :syncedAt " +
            "WHERE id = :recordId"
    )
    suspend fun setReinforcementSyncStatus(recordId: Long, status: String, syncedAt: Long?)
}

@Dao
interface ScrapeLogDao {
    @Query("SELECT * FROM scrape_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ScrapeLog>>

    @Insert
    suspend fun insert(log: ScrapeLog)
}

@Database(entities = [UploadRecord::class, ScrapeLog::class], version = 5, exportSchema = false)
abstract class MiloDatabase : RoomDatabase() {
    abstract fun uploadRecordDao(): UploadRecordDao
    abstract fun scrapeLogDao(): ScrapeLogDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS scrape_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "photosFound INTEGER NOT NULL, " +
                        "photosUploaded INTEGER NOT NULL, " +
                        "status TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Delete duplicate records, keeping the one with the highest ID
                db.execSQL(
                    "DELETE FROM upload_records " +
                        "WHERE id NOT IN (" +
                        "  SELECT MAX(id) FROM upload_records GROUP BY localUri" +
                        ")"
                )
                // Now we can safely create the unique index
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_records_localUri ON upload_records(localUri)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scrape_logs ADD COLUMN message TEXT")
            }
        }
    }
}
