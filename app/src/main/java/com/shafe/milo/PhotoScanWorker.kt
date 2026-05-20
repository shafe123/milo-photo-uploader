package com.shafe.milo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PhotoScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!hasReadPermission()) {
            Log.w(TAG, "Skipping scan; media permission not granted")
            return Result.success()
        }

        if (BuildConfig.AZURE_CUSTOM_VISION_PREDICTION_URL.isBlank() || BuildConfig.AZURE_CUSTOM_VISION_PREDICTION_KEY.isBlank()) {
            Log.w(TAG, "Skipping scan; Azure Custom Vision is not configured")
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastScanSeconds = prefs.getLong(KEY_LAST_SCAN_SECONDS, 0L)
        val nowSeconds = System.currentTimeMillis() / 1000

        val images = queryRecentPhotos(lastScanSeconds)
        val visionClient = AzureCustomVisionClient(
            predictionUrl = BuildConfig.AZURE_CUSTOM_VISION_PREDICTION_URL,
            predictionKey = BuildConfig.AZURE_CUSTOM_VISION_PREDICTION_KEY,
            targetTag = BuildConfig.AZURE_TARGET_TAG,
            minimumProbability = BuildConfig.AZURE_TARGET_PROBABILITY
        )

        images.forEach { imageUri ->
            if (visionClient.containsTargetCat(imageUri, applicationContext)) {
                Log.i(TAG, "Detected target cat in photo: $imageUri")
            }
        }

        prefs.edit().putLong(KEY_LAST_SCAN_SECONDS, nowSeconds).apply()
        return Result.success()
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
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
