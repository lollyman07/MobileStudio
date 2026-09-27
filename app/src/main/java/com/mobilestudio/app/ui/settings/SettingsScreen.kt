package com.mobilestudio.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilestudio.app.model.AppThemeMode
import com.mobilestudio.app.model.QualityPreset
import com.mobilestudio.app.model.ResolutionPreset
import com.mobilestudio.app.ui.studio.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: StudioViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val vs = state.videoSettings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            SectionTitle("Video")
            Text("Quality preset", style = MaterialTheme.typography.labelMedium)
            Row {
                QualityPreset.values().forEach { preset ->
                    FilterChip(
                        selected = vs.preset == preset,
                        onClick = { applyPreset(viewModel, preset) },
                        label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Resolution: ${vs.customWidth}x${vs.customHeight}", style = MaterialTheme.typography.labelMedium)
            Row {
                ResolutionPreset.values().filter { it != ResolutionPreset.CUSTOM }.forEach { res ->
                    FilterChip(
                        selected = vs.customWidth == res.width && vs.customHeight == res.height,
                        onClick = { viewModel.updateVideoSettings { it.copy(customWidth = res.width, customHeight = res.height, preset = QualityPreset.CUSTOM) } },
                        label = { Text(res.label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("FPS: ${vs.fps}")
            Slider(value = vs.fps.toFloat(), onValueChange = { viewModel.updateVideoSettings { s -> s.copy(fps = it.toInt(), preset = QualityPreset.CUSTOM) } }, valueRange = 15f..60f, steps = 8)
            Text("Bitrate: ${vs.bitrateKbps} kbps")
            Slider(value = vs.bitrateKbps.toFloat(), onValueChange = { viewModel.updateVideoSettings { s -> s.copy(bitrateKbps = it.toInt(), preset = QualityPreset.CUSTOM) } }, valueRange = 500f..12000f)
            Text("Codec: ${vs.codec} (device H.264 hardware encoder)", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(20.dp))
            SectionTitle("Recording")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Record microphone audio")
                Spacer(Modifier.weight(1f))
                Switch(checked = vs.audioEnabled, onCheckedChange = { viewModel.updateVideoSettings { s -> s.copy(audioEnabled = it) } })
            }
            Text("Saved to: Movies/MobileStudio (via MediaStore)", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(20.dp))
            SectionTitle("Appearance")
            Row {
                AppThemeMode.values().forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Virtual Camera")
            Text(
                "Android does not allow ordinary apps to register as a system-wide camera device. " +
                "Mobile Studio instead streams the composited scene over your local network as an " +
                "MJPEG source — see the Virtual Camera panel for the live URL, and the README for details.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

private fun applyPreset(viewModel: StudioViewModel, preset: QualityPreset) {
    viewModel.updateVideoSettings {
        when (preset) {
            QualityPreset.LOW -> it.copy(preset = preset, customWidth = 854, customHeight = 480, fps = 24, bitrateKbps = 1500)
            QualityPreset.MEDIUM -> it.copy(preset = preset, customWidth = 1280, customHeight = 720, fps = 30, bitrateKbps = 4000)
            QualityPreset.HIGH -> it.copy(preset = preset, customWidth = 1920, customHeight = 1080, fps = 30, bitrateKbps = 8000)
            QualityPreset.CUSTOM -> it.copy(preset = preset)
        }
    }
}
