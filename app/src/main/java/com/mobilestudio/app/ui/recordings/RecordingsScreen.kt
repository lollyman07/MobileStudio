package com.mobilestudio.app.ui.recordings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobilestudio.app.data.StudioRepository
import com.mobilestudio.app.data.db.RecordingEntity
import com.mobilestudio.app.ui.theme.StudioSurface
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen() {
    val context = LocalContext.current
    val repo = remember { StudioRepository(context) }
    val recordings by repo.observeRecordings().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Recordings") }) }) { padding ->
        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No recordings yet. Recordings you make will appear here.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recordings, key = { it.id }) { rec ->
                RecordingRow(
                    recording = rec,
                    onPlay = {
                        val file = File(rec.filePath)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onShare = {
                        val file = File(rec.filePath)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share recording"))
                        }
                    },
                    onDelete = {
                        scope.launch {
                            File(rec.filePath).delete()
                            repo.deleteRecording(rec.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(recording: RecordingEntity, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(recording.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "${recording.width}x${recording.height} • ${recording.fps}fps • ${formatDuration(recording.durationMs)} • ${formatSize(recording.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(dateFmt.format(Date(recording.createdAtMs)), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play") }
        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
}
