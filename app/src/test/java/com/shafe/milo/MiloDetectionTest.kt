package com.shafe.milo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.io.File
import java.io.FileInputStream

class MiloDetectionTest {

    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setup() {
        logMock = mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.i(any(String::class.java), any(String::class.java)) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(any(String::class.java), any(String::class.java)) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any(String::class.java), any(String::class.java), any(Throwable::class.java)) }.thenReturn(0)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun `verify Milo is detected in the provided image`() {
        val imageFile = File("src/test/resources/milo.jpg")
        if (!imageFile.exists()) {
            println("Test image not found at ${imageFile.absolutePath}. Please place the image there and rename it to milo.jpg")
            return
        }

        val config = AzureConfig(
            endpoint = BuildConfig.AZURE_ENDPOINT,
            projectId = BuildConfig.AZURE_PROJECT_ID,
            iterationName = BuildConfig.AZURE_ITERATION_NAME,
            predictionKey = BuildConfig.AZURE_PREDICTION_KEY,
            targetTags = setOf("milo", "both"),
            threshold = BuildConfig.AZURE_THRESHOLD,
        )

        val client = AzureCustomVisionClient(config)
        val context = mock(Context::class.java)
        val contentResolver = mock(ContentResolver::class.java)
        val uri = mock(Uri::class.java)

        `when`(context.contentResolver).thenReturn(contentResolver)
        `when`(contentResolver.openInputStream(uri)).thenReturn(FileInputStream(imageFile))

        println("Running detection on ${imageFile.name}...")
        val confidence = client.getMiloConfidence(uri, context)
        val isMiloDetected = confidence >= config.threshold
        
        println("Detection result: $isMiloDetected (confidence: $confidence)")
        assertTrue("Milo should be detected in the image", isMiloDetected)
    }
}
