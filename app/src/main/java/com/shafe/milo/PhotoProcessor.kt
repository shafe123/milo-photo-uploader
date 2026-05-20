package com.shafe.milo

import android.content.Context
import android.net.Uri
import android.util.Log

class PhotoProcessor(private val context: Context) {

    private val config = AzureConfig(
        endpoint = BuildConfig.AZURE_ENDPOINT,
        projectId = BuildConfig.AZURE_PROJECT_ID,
        iterationName = BuildConfig.AZURE_ITERATION_NAME,
        predictionKey = BuildConfig.AZURE_PREDICTION_KEY,
        targetTags = setOf("milo", "both"),
        threshold = BuildConfig.AZURE_THRESHOLD,
    )

    private val visionClient = AzureCustomVisionClient(config)
    private val uploader = AzureBlobUploader(
        connectionString = BuildConfig.AZURE_STORAGE_CONNECTION_STRING,
        containerName = BuildConfig.AZURE_STORAGE_CONTAINER
    )
    private val database = MiloDatabase.getDatabase(context)
    private val recordDao = database.uploadRecordDao()

    suspend fun processPhoto(imageUri: Uri) {
        runCatching {
            val confidence = visionClient.getMiloConfidence(imageUri, context)
            val isMiloDetected = confidence >= config.threshold

            val blobId = uploader.uploadPhoto(
                photoUri = imageUri,
                context = context,
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
        }.onFailure {
            Log.e(TAG, "Failed to process photo: $imageUri", it)
        }
    }

    companion object {
        private const val TAG = "PhotoProcessor"
    }
}
