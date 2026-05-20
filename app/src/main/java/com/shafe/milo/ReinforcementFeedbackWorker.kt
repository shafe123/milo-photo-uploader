package com.shafe.milo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReinforcementFeedbackWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val recordId = inputData.getLong(KEY_RECORD_ID, -1L)
        val correctedLabel = inputData.getString(KEY_CORRECTED_LABEL).orEmpty().lowercase()
        if (recordId <= 0L || correctedLabel.isBlank()) {
            return Result.failure()
        }
        if (correctedLabel !in ALLOWED_LABELS) {
            return Result.failure()
        }

        val dao = MiloDatabase.getDatabase(applicationContext).uploadRecordDao()
        val record = dao.getRecordById(recordId) ?: return Result.failure()
        val uploader = AzureBlobUploader(
            connectionString = BuildConfig.AZURE_STORAGE_CONNECTION_STRING,
            containerName = BuildConfig.AZURE_FEEDBACK_CONTAINER,
        )

        val uploaded = uploader.uploadFeedbackSample(
            photoUri = Uri.parse(record.localUri),
            context = applicationContext,
            correctedLabel = correctedLabel,
            originalBlobId = record.azureBlobId,
            predictionDetectedMilo = record.isMiloDetected,
            predictionConfidence = record.confidence,
            predictionIteration = record.iterationName,
        )

        if (uploaded == null) {
            dao.setReinforcementSyncStatus(recordId, ReinforcementStatus.FAILED, null)
            Log.w(TAG, "Failed to upload reinforcement sample for record $recordId")
            return Result.retry()
        }

        // Also update the original blob metadata with the corrected milo_detected value
        val primaryUploader = AzureBlobUploader(
            connectionString = BuildConfig.AZURE_STORAGE_CONNECTION_STRING,
            containerName = BuildConfig.AZURE_STORAGE_CONTAINER,
        )
        
        val miloDetected = when (correctedLabel) {
            "milo", "both" -> "true"
            "emilio", "neither" -> "false"
            else -> record.isMiloDetected.toString() // Fallback
        }

        val metadataUpdated = primaryUploader.updateBlobMetadata(
            blobName = record.azureBlobId,
            metadata = mapOf("milo_detected" to miloDetected)
        )

        return if (metadataUpdated) {
            dao.setReinforcementSyncStatus(recordId, ReinforcementStatus.SYNCED, System.currentTimeMillis())
            Result.success()
        } else {
            // If metadata update fails, we retry the whole worker
            // Note: This might result in duplicate feedback uploads, which is acceptable 
            // compared to out-of-sync metadata.
            Log.w(TAG, "Failed to update original blob metadata for record $recordId, retrying...")
            Result.retry()
        }
    }

    companion object {
        const val KEY_RECORD_ID = "record_id"
        const val KEY_CORRECTED_LABEL = "corrected_label"
        private const val TAG = "ReinforcementFeedback"
        private val ALLOWED_LABELS = setOf("milo", "emilio", "both", "neither")
    }
}
