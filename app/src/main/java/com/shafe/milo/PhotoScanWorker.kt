package com.shafe.milo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PhotoScanWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = MiloDatabase.getDatabase(applicationContext)
        val scrapeLogDao = database.scrapeLogDao()

        if (!hasReadPermission()) {
            val msg = "Skipping scan; media permission not granted"
            Log.w(TAG, msg)
            scrapeLogDao.insert(ScrapeLog(photosFound = 0, photosUploaded = 0, status = "SKIPPED", message = msg))
            return Result.success()
        }

        if (!isAzureConfigured()) {
            val msg = "Skipping scan; Azure Custom Vision is not configured"
            Log.w(TAG, msg)
            scrapeLogDao.insert(ScrapeLog(photosFound = 0, photosUploaded = 0, status = "SKIPPED", message = msg))
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastScanSeconds = prefs.getLong(KEY_LAST_SCAN_SECONDS, 0L)

        // Hard limit: only look back 30 days
        val thirtyDaysAgoSeconds = (System.currentTimeMillis() / 1000) - (30L * 24 * 60 * 60)
        val effectiveStartSeconds = maxOf(lastScanSeconds, thirtyDaysAgoSeconds)

        val images = queryRecentPhotos(effectiveStartSeconds)
        if (images.isEmpty()) {
            val rangeMsg = if (lastScanSeconds == 0L) "in the last 30 days" else "since last scan"
            scrapeLogDao.insert(
                ScrapeLog(
                    photosFound = 0,
                    photosUploaded = 0,
                    status = "SUCCESS",
                    message = "No new photos found $rangeMsg"
                )
            )
            return Result.success()
        }

        val processor = PhotoProcessor(applicationContext)

        var uploadedCount = 0
        images.forEach { imageUri ->
            processor.processPhoto(imageUri)
            uploadedCount++
        }

        scrapeLogDao.insert(
            ScrapeLog(
                photosFound = images.size,
                photosUploaded = uploadedCount,
                status = "SUCCESS"
            )
        )

        // Only update if we successfully processed something
        val mostRecentSeconds = queryLatestPhotoTimestamp()
        if (mostRecentSeconds > lastScanSeconds) {
            prefs.edit { putLong(KEY_LAST_SCAN_SECONDS, mostRecentSeconds) }
        }

        return Result.success()
    }

    private fun queryLatestPhotoTimestamp(): Long {
        val projection = arrayOf(MediaStore.Images.Media.DATE_ADDED)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
            } else 0L
        } ?: 0L
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAzureConfigured(): Boolean {
        return BuildConfig.AZURE_ENDPOINT.isNotBlank() &&
            BuildConfig.AZURE_PREDICTION_KEY.isNotBlank()
    }

    private fun queryRecentPhotos(lastScanSeconds: Long): List<Uri> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(lastScanSeconds.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val result = mutableListOf<Uri>()

        applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                result += Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }

        return result
    }

    companion object {
        private const val TAG = "PhotoScanWorker"
        private const val PREFS = "milo-photo-scan"
        private const val KEY_LAST_SCAN_SECONDS = "last_scan_seconds"
    }
}
