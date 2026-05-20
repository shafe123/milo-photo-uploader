package com.shafe.milo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureCustomVisionClientTest {

    private val client = AzureCustomVisionClient(
        predictionUrl = "https://example.com/predict",
        predictionKey = "dummy",
        targetTag = "milo",
        minimumProbability = 0.8
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

        assertTrue(client.isTargetCat(response))
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

        assertFalse(client.isTargetCat(response))
    }
}
