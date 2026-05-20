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
        if (!hasReadPermission()) {
            Log.w(TAG, "Skipping scan; media permission not granted")
            return Result.success()
        }

        if (!isAzureConfigured()) {
            Log.w(TAG, "Skipping scan; Azure Custom Vision is not configured")
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastScanSeconds = prefs.getLong(KEY_LAST_SCAN_SECONDS, 0L)
        val nowSeconds = System.currentTimeMillis() / 1000

        val images = queryRecentPhotos(lastScanSeconds)
        val config = AzureConfig(
            endpoint = BuildConfig.AZURE_ENDPOINT,
            projectId = BuildConfig.AZURE_PROJECT_ID,
            iterationName = BuildConfig.AZURE_ITERATION_NAME,
            predictionKey = BuildConfig.AZURE_PREDICTION_KEY,
            targetTags = setOf("milo", "both"),
            threshold = BuildConfig.AZURE_THRESHOLD,
        )
        val visionClient = AzureCustomVisionClient(config)
        val uploader = AzureBlobUploader(
            connectionString = BuildConfig.AZURE_STORAGE_CONNECTION_STRING,
            containerName = BuildConfig.AZURE_STORAGE_CONTAINER
        )
        val database = MiloDatabase.getDatabase(applicationContext)
        val recordDao = database.uploadRecordDao()

        images.forEach { imageUri ->
            val confidence = visionClient.getMiloConfidence(imageUri, applicationContext)
            val isMiloDetected = confidence >= config.threshold

            // Upload the photo and set metadata (just like the Python script)
            val blobId = uploader.uploadPhoto(
                photoUri = imageUri,
                context = applicationContext,
                isMiloPresent = isMiloDetected,
                confidence = confidence,
                iterationName = config.iterationName
            )

            if (blobId != null) {
                val record = UploadRecord(
                    localUri = imageUri.toString(),
                    azureBlobId = blobId,
                    confidence = confidence,
                    isMiloDetected = isMiloDetected,
                    iterationName = config.iterationName
                )
                recordDao.insert(record)
            }
        }

        prefs.edit { putLong(KEY_LAST_SCAN_SECONDS, nowSeconds) }
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
