package com.shafe.milo

import android.content.Context
import android.net.Uri
import android.util.Log
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

            val blobName = photoUri.lastPathSegment ?: "photo_${System.currentTimeMillis()}.jpg"
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

            // Set content type to image/jpeg
            blobClient.setHttpHeaders(BlobHttpHeaders().setContentType("image/jpeg"))

            Log.i(TAG, "Uploaded $blobName to Azure with metadata: $metadata")
            blobName
        }.onFailure {
            Log.e(TAG, "Failed to upload photo to Azure", it)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AzureBlobUploader"
    }
}
