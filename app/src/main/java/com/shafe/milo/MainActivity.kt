package com.shafe.milo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = MiloDatabase.getDatabase(applicationContext)
        val recordDao = database.uploadRecordDao()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UploadHistoryScreen(recordDao)
                }
            }
        }
    }
}

@Composable
fun UploadHistoryScreen(recordDao: UploadRecordDao) {
    val records by recordDao.getAllRecords().collectAsState(initial = emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val data = Data.Builder()
                    .putStringArray(ManualUploadWorker.KEY_URIS, uris.map { it.toString() }.toTypedArray())
                    .build()
                val request = OneTimeWorkRequestBuilder<ManualUploadWorker>()
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Upload History",
                style = MaterialTheme.typography.headlineMedium
            )
            Row {
                Button(onClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text("Select Photos")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val request = OneTimeWorkRequestBuilder<PhotoScanWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                }) {
                    Text("Scan Now")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(records) { record ->
                UploadRecordItem(
                    record = record,
                    onCorrectionSelected = { correctedLabel ->
                        scope.launch {
                            recordDao.setCorrection(record.id, correctedLabel)
                        }
                        val request = OneTimeWorkRequestBuilder<ReinforcementFeedbackWorker>()
                            .setInputData(
                                Data.Builder()
                                    .putLong(ReinforcementFeedbackWorker.KEY_RECORD_ID, record.id)
                                    .putString(ReinforcementFeedbackWorker.KEY_CORRECTED_LABEL, correctedLabel)
                                    .build()
                            )
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "reinforcement_${record.id}",
                            ExistingWorkPolicy.REPLACE,
                            request,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun UploadRecordItem(
    record: UploadRecord,
    onCorrectionSelected: (String) -> Unit = {},
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormat.format(Date(record.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = Uri.parse(record.localUri),
                contentDescription = "Uploaded photo",
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (record.isMiloDetected) "Milo Detected" else "No Milo",
                    color = if (record.isMiloDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Confidence: ${"%.2f".format(record.confidence * 100)}%", style = MaterialTheme.typography.bodySmall)
                Text(text = "Blob ID: ${record.azureBlobId}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Iteration: ${record.iterationName}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Date: $dateString", style = MaterialTheme.typography.bodySmall)
                if (!record.correctedLabel.isNullOrBlank()) {
                    Text(
                        text = "Corrected Label: ${record.correctedLabel}",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(text = "Reinforcement: ${record.reinforcementStatus}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onCorrectionSelected("milo") },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Mark Milo")
                    }
                    FilledTonalButton(
                        onClick = { onCorrectionSelected("not_milo") },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Mark Not Milo")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UploadRecordItemPreview() {
    MaterialTheme {
        UploadRecordItem(
            record = UploadRecord(
                id = 1,
                localUri = "content://media/external/images/media/1",
                azureBlobId = "milo_123.jpg",
                confidence = 0.95,
                isMiloDetected = true,
                iterationName = "milo-v1",
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
