package com.shafe.milo

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AzureCustomVisionClient(
    private val predictionUrl: String,
    private val predictionKey: String,
    private val targetTag: String,
    private val minimumProbability: Double
) {
    fun containsTargetCat(photoUri: Uri, context: Context): Boolean {
        return runCatching {
            val imageBytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() } ?: return false
            val connection = URL(predictionUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Prediction-Key", predictionKey)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(imageBytes)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "Azure prediction request failed with HTTP $responseCode")
                false
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                isTargetCat(body)
            }
        }.onFailure {
            Log.e(TAG, "Azure prediction failed", it)
        }.getOrDefault(false)
    }

    internal fun isTargetCat(responseBody: String): Boolean {
        val predictions = JSONObject(responseBody).optJSONArray("predictions") ?: return false

        for (i in 0 until predictions.length()) {
            val prediction = predictions.optJSONObject(i) ?: continue
            val tagName = prediction.optString("tagName", "")
            val probability = prediction.optDouble("probability", 0.0)

            if (tagName.equals(targetTag, ignoreCase = true) && probability >= minimumProbability) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val TAG = "AzureCustomVision"
    }
}
