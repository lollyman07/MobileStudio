package com.mobilestudio.app.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilestudio.app.model.CameraFacing
import com.mobilestudio.app.model.SourceModel
import com.mobilestudio.app.model.SourceType
import com.mobilestudio.app.ui.studio.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEditSheet(source: SourceModel, viewModel: StudioViewModel, onDismiss: () -> Unit) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setImageForSource(source.id, it) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setVideoForSource(source.id, it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            var name by remember { mutableStateOf(source.name) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; viewModel.renameSource(source.id, it) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            when (source.type) {
                SourceType.CAMERA -> {
                    Text("Camera", style = MaterialTheme.typography.titleSmall)
                    Row {
                        FilterChip(selected = source.cameraFacing == CameraFacing.BACK, onClick = { viewModel.switchCameraFacing(source.id, CameraFacing.BACK) }, label = { Text("Back") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = source.cameraFacing == CameraFacing.FRONT, onClick = { viewModel.switchCameraFacing(source.id, CameraFacing.FRONT) }, label = { Text("Front") })
                    }
                    Spacer(Modifier.height(8.dp))
                    var torch by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Torch")
                        Switch(checked = torch, onCheckedChange = { torch = it; viewModel.toggleTorch(source.id, it) })
                    }
                }
                SourceType.IMAGE -> {
                    Text("Image", style = MaterialTheme.typography.titleSmall)
                    Button(onClick = { imagePicker.launch("image/*") }) { Text("Choose image") }
                }
                SourceType.VIDEO -> {
                    Text("Video", style = MaterialTheme.typography.titleSmall)
                    Button(onClick = { videoPicker.launch("video/*") }) { Text("Choose video") }
                }
                SourceType.TEXT -> {
                    Text("Text", style = MaterialTheme.typography.titleSmall)
                    var text by remember { mutableStateOf(source.textContent) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; viewModel.updateTextProperties(source.id, content = it) },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    var size by remember { mutableStateOf(source.textSizeSp) }
                    Text("Font size: ${size.toInt()}sp")
                    Slider(value = size, onValueChange = { size = it; viewModel.updateTextProperties(source.id, sizeSp = it) }, valueRange = 12f..96f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bold")
                        var bold by remember { mutableStateOf(source.textBold) }
                        Switch(checked = bold, onCheckedChange = { bold = it; viewModel.updateTextProperties(source.id, bold = it) })
                    }
                    Row {
                        listOf("left", "center", "right").forEach { align ->
                            FilterChip(
                                selected = source.textAlign == align,
                                onClick = { viewModel.updateTextProperties(source.id, align = align) },
                                label = { Text(align) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
                SourceType.COLOR -> {
                    Text("Color", style = MaterialTheme.typography.titleSmall)
                    val presets = listOf(0xFF7C5CFF, 0xFFFF4757, 0xFF2ED573, 0xFFFFA502, 0xFF1E90FF, 0xFF000000, 0xFFFFFFFF)
                    Row {
                        presets.forEach { c ->
                            IconButton(onClick = { viewModel.updateColor(source.id, c) }) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .background(androidx.compose.ui.graphics.Color(c.toInt()), androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }
                }
                SourceType.WEB -> {
                    Text("Web page", style = MaterialTheme.typography.titleSmall)
                    var url by remember { mutableStateOf(source.webUrl ?: "https://") }
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.setWebUrlForSource(source.id, url) }, modifier = Modifier.padding(top = 8.dp)) { Text("Load") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Note: Web sources preview live but standard Android can't capture an " +
                        "off-screen WebView, so this layer won't appear in recordings or the virtual-camera stream.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                SourceType.SCREEN_CAPTURE -> {
                    Text("Screen capture is managed from the Studio screen's \"Add screen capture\" action.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(20.dp))
            var opacity by remember { mutableStateOf(source.transform.opacity) }
            Text("Opacity: ${(opacity * 100).toInt()}%")
            Slider(value = opacity, onValueChange = {
                opacity = it
                viewModel.updateTransform(source.id, source.transform.copy(opacity = it))
            })

            var rotation by remember { mutableStateOf(source.transform.rotationDeg) }
            Text("Rotation: ${rotation.toInt()}°")
            Slider(value = rotation, onValueChange = {
                rotation = it
                viewModel.updateTransform(source.id, source.transform.copy(rotationDeg = it))
            }, valueRange = 0f..360f)

            Spacer(Modifier.height(24.dp))
        }
    }
}
