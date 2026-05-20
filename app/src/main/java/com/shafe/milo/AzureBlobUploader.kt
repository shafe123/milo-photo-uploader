package com.shafe.milo

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobHttpHeaders

class AzureBlobUploader(
    private val connectionString: String,
    private val containerName: String,
) {
    fun uploadPhoto(
        photoUri: Uri,
        context: Context,
        isMiloPresent: Boolean,
        confidence: Double,
        iterationName: String
    ): String? {
        if (connectionString.isBlank()) {
            Log.w(TAG, "Azure Storage not configured, skipping upload.")
            return null
        }

        return runCatching {
            val blobServiceClient = BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()

            val containerClient = blobServiceClient.getBlobContainerClient(containerName)
            if (!containerClient.exists()) {
                containerClient.create()
            }

            val blobName = getFileName(context, photoUri) 
                ?: photoUri.lastPathSegment 
                ?: "photo_${System.currentTimeMillis()}.jpg"
            
            val blobClient = containerClient.getBlobClient(blobName)

            context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                val length = inputStream.available().toLong()
                blobClient.upload(inputStream, length, true)
            }

            // Set metadata matching the Python script
            val metadata = mapOf(
                "milo_detected" to isMiloPresent.toString(),
                "milo_confidence" to "%.4f".format(confidence),
                "milo_iteration" to iterationName
            )
            blobClient.setMetadata(metadata)

            // Set correct content type
            val contentType = context.contentResolver.getType(photoUri) ?: "image/jpeg"
            blobClient.setHttpHeaders(BlobHttpHeaders().setContentType(contentType))

            Log.i(TAG, "Uploaded $blobName to Azure with metadata: $metadata")
            blobName
        }.onFailure {
            Log.e(TAG, "Failed to upload photo to Azure", it)
        }.getOrNull()
    }

    fun uploadFeedbackSample(
        photoUri: Uri,
        context: Context,
        correctedLabel: String,
        originalBlobId: String,
        predictionDetectedMilo: Boolean,
        predictionConfidence: Double,
        predictionIteration: String,
    ): String? {
        if (connectionString.isBlank()) {
            Log.w(TAG, "Azure Storage not configured, skipping reinforcement upload.")
            return null
        }

        return runCatching {
            val blobServiceClient = BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
            val containerClient = blobServiceClient.getBlobContainerClient(containerName)
            if (!containerClient.exists()) {
                containerClient.create()
            }

            val fileName = getFileName(context, photoUri)
                ?: photoUri.lastPathSegment
                ?: "photo_${System.currentTimeMillis()}.jpg"
            val blobName = buildFeedbackBlobName(originalBlobId, fileName)
            val blobClient = containerClient.getBlobClient(blobName)

            context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                val length = inputStream.available().toLong()
                blobClient.upload(inputStream, length, true)
            }

            val metadata = mapOf(
                "corrected_label" to correctedLabel,
                "source_blob_id" to originalBlobId,
                "predicted_milo" to predictionDetectedMilo.toString(),
                "prediction_confidence" to "%.4f".format(predictionConfidence),
                "prediction_iteration" to predictionIteration,
            )
            blobClient.setMetadata(metadata)
            val contentType = context.contentResolver.getType(photoUri) ?: "image/jpeg"
            blobClient.setHttpHeaders(BlobHttpHeaders().setContentType(contentType))

            Log.i(TAG, "Uploaded reinforcement sample $blobName with metadata: $metadata")
            blobName
        }.onFailure {
            Log.e(TAG, "Failed to upload reinforcement sample", it)
        }.getOrNull()
    }

    internal fun buildFeedbackBlobName(originalBlobId: String, fileName: String): String {
        val normalizedSourceId = originalBlobId.trim('/').ifBlank { fileName }
        return "feedback/$normalizedSourceId"
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return cursor.getString(index)
                    }
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "AzureBlobUploader"
    }
}
