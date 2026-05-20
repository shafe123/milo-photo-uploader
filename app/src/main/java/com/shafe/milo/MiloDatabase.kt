package com.shafe.milo

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadRecordDao {
    @Query("SELECT * FROM upload_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<UploadRecord>>

    @Insert
    suspend fun insert(record: UploadRecord)
}

@Database(entities = [UploadRecord::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
