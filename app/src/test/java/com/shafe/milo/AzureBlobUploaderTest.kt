package com.shafe.milo

import org.junit.Assert.assertEquals
import org.junit.Test

class AzureBlobUploaderTest {

    private val uploader = AzureBlobUploader(
        connectionString = "",
        containerName = "container",
    )

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
}
