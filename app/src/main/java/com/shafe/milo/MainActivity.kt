package com.shafe.milo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
        val scrapeLogDao = database.scrapeLogDao()

        setContent {
            var selectedTab by remember { mutableIntStateOf(0) }
            val snackbarHostState = remember { SnackbarHostState() }

            MaterialTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Uploads") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.List, contentDescription = null) },
                                label = { Text("Scrapes") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (selectedTab) {
                            0 -> UploadHistoryScreen(recordDao, snackbarHostState)
                            1 -> ScrapeHistoryScreen(scrapeLogDao)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadHistoryScreen(recordDao: UploadRecordDao, snackbarHostState: SnackbarHostState) {
    val records by recordDao.getAllRecords().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

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
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Upload History",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Media Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "The app needs access to your photos to scan for Milo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { permissionLauncher.launch(permission) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        Row {
            Button(onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text("Select Photos")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val request = OneTimeWorkRequestBuilder<PhotoScanWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                },
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            context.getSharedPreferences("milo-photo-scan", Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            scope.launch {
                                snackbarHostState.showSnackbar("Scanner reset! Scanning everything...")
                            }
                            val request = OneTimeWorkRequestBuilder<PhotoScanWorker>().build()
                            WorkManager.getInstance(context).enqueue(request)
                        }
                    )
                }
            ) {
                Text("Scan Now")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No photos processed yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap 'Scan Now' to look for Milo!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "(Long-press 'Scan Now' to re-scan all photos)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(records) { record ->
                    UploadRecordItem(
                        record = record,
                        onCorrectionSelected = { correctedLabel ->
                            val normalizedLabel = correctedLabel.lowercase()
                            scope.launch {
                                recordDao.setCorrection(record.id, normalizedLabel, ReinforcementStatus.PENDING)
                                val request = OneTimeWorkRequestBuilder<ReinforcementFeedbackWorker>()
                                    .setInputData(
                                        Data.Builder()
                                            .putLong(ReinforcementFeedbackWorker.KEY_RECORD_ID, record.id)
                                            .putString(ReinforcementFeedbackWorker.KEY_CORRECTED_LABEL, normalizedLabel)
                                            .build()
                                    )
                                    .build()
                                WorkManager.getInstance(context).enqueueUniqueWork(
                                    "reinforcement_${record.id}",
                                    ExistingWorkPolicy.REPLACE,
                                    request,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ScrapeHistoryScreen(scrapeLogDao: ScrapeLogDao) {
    val logs by scrapeLogDao.getAllLogs().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Scrape History",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No scan records yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Background scans will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(logs) { log ->
                    ScrapeLogItem(log)
                }
            }
        }
    }
}

@Composable
fun ScrapeLogItem(log: ScrapeLog) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormat.format(Date(log.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Scrape Date: $dateString", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Photos Found: ${log.photosFound}")
            Text(text = "Photos Processed: ${log.photosUploaded}")
            if (!log.message.isNullOrBlank()) {
                Text(text = "Note: ${log.message}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Status: ${log.status}",
                color = when (log.status) {
                    "SUCCESS" -> MaterialTheme.colorScheme.primary
                    "SKIPPED" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = { onCorrectionSelected("milo") },
                            modifier = Modifier
                                .height(32.dp)
                                .weight(1f)
                        ) {
                            Text("Milo")
                        }
                        FilledTonalButton(
                            onClick = { onCorrectionSelected("emilio") },
                            modifier = Modifier
                                .height(32.dp)
                                .weight(1f)
                        ) {
                            Text("Emilio")
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = { onCorrectionSelected("both") },
                            modifier = Modifier
                                .height(32.dp)
                                .weight(1f)
                        ) {
                            Text("Both")
                        }
                        FilledTonalButton(
                            onClick = { onCorrectionSelected("neither") },
                            modifier = Modifier
                                .height(32.dp)
                                .weight(1f)
                        ) {
                            Text("Neither")
                        }
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
