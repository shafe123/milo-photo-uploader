package com.shafe.milo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_records")
data class UploadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localUri: String,
    val azureBlobId: String,
    val confidence: Double,
    val isMiloDetected: Boolean,
    val iterationName: String,
    val timestamp: Long = System.currentTimeMillis()
)
