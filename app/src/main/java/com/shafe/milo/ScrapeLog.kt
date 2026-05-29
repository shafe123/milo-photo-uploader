package com.shafe.milo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scrape_logs")
data class ScrapeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val photosFound: Int,
    val photosUploaded: Int,
    val status: String = "SUCCESS",
    val message: String? = null
)
