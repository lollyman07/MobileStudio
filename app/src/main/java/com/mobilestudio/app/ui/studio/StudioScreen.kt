package com.mobilestudio.app.ui.studio

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mobilestudio.app.capture.ScreenCaptureManager
import com.mobilestudio.app.model.SourceType
import com.mobilestudio.app.ui.components.MicLevelMeter
import com.mobilestudio.app.ui.components.PerformancePanel
import com.mobilestudio.app.ui.components.PreviewCanvas
import com.mobilestudio.app.ui.components.RecordingBadge
import com.mobilestudio.app.ui.theme.StudioAccent
import com.mobilestudio.app.ui.theme.StudioRec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onOpenScenes: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val screenCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val metrics = context.resources.displayMetrics
            viewModel.beginScreenCapture(result.resultCode, result.data!!, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(state.errorMessage ?: "") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mobile Studio") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Box {
                PreviewCanvas(
                    frame = state.previewFrame,
                    sources = viewModel.currentScene()?.sources ?: emptyList(),
                    selectedSourceId = state.selectedSourceId,
                    onSelect = { viewModel.selectSource(it) },
                    onTransformChange = { id, t -> viewModel.updateTransform(id, t) }
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    RecordingBadge(
                        isRecording = state.isRecording,
                        isPaused = state.isPaused,
                        seconds = state.recordingSeconds,
                        resolutionLabel = "${state.videoSettings.customWidth}x${state.videoSettings.customHeight}",
                        fps = state.videoSettings.fps
                    )
                }
                if (state.isStreaming) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("● LIVE  ${state.streamClients} viewer(s)", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Scene: ${viewModel.currentScene()?.name ?: "—"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                state.scenes.sortedBy { it.order }.forEach { scene ->
                    FilterChip(
                        selected = scene.id == state.currentSceneId,
                        onClick = { viewModel.selectScene(scene.id) },
                        label = { Text(scene.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onOpenScenes) { Text("Scenes") }
                OutlinedButton(onClick = onOpenSources) { Text("Sources") }
                OutlinedButton(onClick = {
                    if (state.isStreaming) viewModel.stopVirtualCamera() else viewModel.startVirtualCamera()
                }) { Text(if (state.isStreaming) "Stop Cam" else "Virtual Cam") }
            }

            Spacer(Modifier.height(12.dp))
            PerformancePanel(fps = state.fps, status = performanceStatus(state.fps, state.videoSettings.fps))

            Spacer(Modifier.height(12.dp))
            MicLevelMeter(levelDb = state.micLevelDb, muted = state.micMuted)
            Row {
                TextButton(onClick = { viewModel.setMicMuted(!state.micMuted) }) {
                    Text(if (state.micMuted) "Unmute mic" else "Mute mic")
                }
                Spacer(Modifier.weight(1f))
                if (!state.isCapturingScreen) {
                    TextButton(onClick = { screenCaptureLauncher.launch(ScreenCaptureManager(context).requestIntent()) }) {
                        Text("Add screen capture")
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (state.isRecording) viewModel.stopRecording() else viewModel.startRecording()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) StudioRec else StudioAccent
                )
            ) {
                Text(if (state.isRecording) "STOP RECORDING" else "START RECORDING", style = MaterialTheme.typography.titleMedium)
            }
            if (state.isRecording) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { if (state.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() }) {
                        Text(if (state.isPaused) "Resume" else "Pause")
                    }
                }
            }
        }
    }
}

private fun performanceStatus(actualFps: Float, targetFps: Int): String = when {
    actualFps <= 0f -> "Starting…"
    actualFps >= targetFps * 0.9f -> "Good"
    actualFps >= targetFps * 0.6f -> "Fair"
    else -> "Struggling"
}
