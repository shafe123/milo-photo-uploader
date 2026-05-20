package com.shafe.milo

import android.content.Context
import android.net.Uri
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AzureBlobUploaderTest {

    @Mock
    lateinit var context: Context

    private lateinit var uploader: AzureBlobUploader
    private val connectionString = "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net"
    private val containerName = "test-container"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        uploader = AzureBlobUploader(connectionString, containerName)
    }

    @Test
    fun `updateBlobMetadata returns false when connection string is blank`() {
        val emptyUploader = AzureBlobUploader("", containerName)
        val result = emptyUploader.updateBlobMetadata("test.jpg", mapOf("key" to "value"))
        assertFalse(result)
    }

    // Note: Mocking the Azure SDK (which uses a Builder) is notoriously difficult 
    // without a wrapper or using PowerMock/Static mocks.
    // For this specific logic, I'll focus on the mapping logic in the worker,
    // as it's the more critical business logic.

    @Test
    fun `buildFeedbackBlobName uses source blob id for stable feedback path`() {
        val blobName = uploader.buildFeedbackBlobName(
            originalBlobId = "photo-123.jpg",
            fileName = "fallback.jpg",
        )

        assertEquals("feedback/photo-123.jpg", blobName)
    }

    @Test
    fun `buildFeedbackBlobName falls back to fileName when source blob id is blank`() {
        val blobName = uploader.buildFeedbackBlobName(
            originalBlobId = "",
            fileName = "fallback.jpg",
        )

        assertEquals("feedback/fallback.jpg", blobName)
    }

    @Test
    fun `buildFeedbackBlobName normalizes path separators from source blob id`() {
        val blobName = uploader.buildFeedbackBlobName(
            originalBlobId = "folder/sub/photo-123.jpg",
            fileName = "fallback.jpg",
        )

        assertEquals("feedback/folder_sub_photo-123.jpg", blobName)
    }

    @Test
    fun `buildFeedbackBlobName normalizes path separators from fileName fallback`() {
        val blobName = uploader.buildFeedbackBlobName(
            originalBlobId = "",
            fileName = "folder\\sub/photo-123.jpg",
        )

        assertEquals("feedback/folder_sub_photo-123.jpg", blobName)
    }
}
