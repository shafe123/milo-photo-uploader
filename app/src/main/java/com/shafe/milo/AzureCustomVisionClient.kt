package com.shafe.milo

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AzureCustomVisionClient(private val config: AzureConfig) {

    fun getMiloConfidence(photoUri: Uri, context: Context): Double {
        if (config.endpoint.isBlank() || config.projectId.isBlank() || config.predictionKey.isBlank()) {
            Log.w(TAG, "Azure Custom Vision not fully configured, assuming match.")
            return 1.0
        }

        return runCatching {
            val imageBytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() } ?: return 0.0
            val connection = URL(config.predictionUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Prediction-Key", config.predictionKey)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            connection.outputStream.use { it.write(imageBytes) }

            val responseCode = connection.responseCode
            if (responseCode !in (200..299)) {
                Log.w(TAG, "Azure prediction failed with HTTP $responseCode")
                1.0
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseMaxConfidence(body)
            }
        }.onFailure {
            Log.e(TAG, "Azure prediction failed", it)
        }.getOrDefault(1.0)
    }

    internal fun parseMaxConfidence(responseBody: String): Double {
        val predictions = JSONObject(responseBody).optJSONArray("predictions") ?: return 0.0
        var maxConfidence = 0.0
        for (i in 0 until predictions.length()) {
            val prediction = predictions.optJSONObject(i) ?: continue
            val tagName = prediction.optString("tagName", "").lowercase()
            val probability = prediction.optDouble("probability", 0.0)
            if (tagName in config.targetTags) {
                maxConfidence = maxOf(maxConfidence, probability)
            }
        }
        return maxConfidence
    }

    companion object {
        private const val TAG = "AzureCustomVision"
    }
}

data class AzureConfig(
    val endpoint: String,
    val projectId: String,
    val iterationName: String,
    val predictionKey: String,
    val targetTags: Set<String>,
    val threshold: Double,
) {
    val predictionUrl: String
        get() = "${endpoint.trimEnd('/')}/customvision/v3.0/Prediction/$projectId/classify/iterations/$iterationName/image"
}
