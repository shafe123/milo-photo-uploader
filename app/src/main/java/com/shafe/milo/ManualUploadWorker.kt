package com.shafe.milo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ManualUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uriStrings = inputData.getStringArray(KEY_URIS) ?: return Result.failure()
        val processor = PhotoProcessor(applicationContext)

        uriStrings.forEach { uriString ->
            val uri = Uri.parse(uriString)
            // Persist access to the URI if needed (for background work)
            runCatching {
                applicationContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure {
                Log.w(TAG, "Could not take persistable permission for $uri", it)
            }
            
            processor.processPhoto(uri)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "ManualUploadWorker"
        const val KEY_URIS = "uris"
    }
}
