package com.shafe.milo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureCustomVisionClientTest {

    private val client = AzureCustomVisionClient(
        AzureConfig(
            endpoint = "https://example.com",
            projectId = "project-id",
            iterationName = "iteration-1",
            predictionKey = "dummy",
            targetTags = setOf("milo", "both"),
            threshold = 0.8,
        )
    )

    @Test
    fun `matches target tag above threshold`() {
        val response = """
            {
              "predictions": [
                {"tagName": "other", "probability": 0.95},
                {"tagName": "milo", "probability": 0.81}
              ]
            }
        """.trimIndent()

        val confidence = client.parseMaxConfidence(response)
        assertTrue(confidence >= 0.8)
    }

    @Test
    fun `does not match when below threshold`() {
        val response = """
            {
              "predictions": [
                {"tagName": "milo", "probability": 0.79}
              ]
            }
        """.trimIndent()

        val confidence = client.parseMaxConfidence(response)
        assertFalse(confidence >= 0.8)
    }
}
