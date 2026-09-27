package com.mobilestudio.app.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.mobilestudio.app.ui.studio.StudioViewModel
import com.mobilestudio.app.ui.theme.StudioAccent
import com.mobilestudio.app.ui.theme.StudioSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesScreen(viewModel: StudioViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scenes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "New scene") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.scenes.sortedBy { it.order }, key = { it.id }) { scene ->
                val selected = scene.id == state.currentSceneId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) StudioAccent.copy(alpha = 0.18f) else StudioSurface, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).clickable { viewModel.selectScene(scene.id) }) {
                        Text(scene.name, style = MaterialTheme.typography.titleMedium)
                        Text("${scene.sources.size} source(s)", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { renameTarget = scene.id; renameText = scene.name }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { viewModel.duplicateScene(scene.id) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                    }
                    IconButton(onClick = { viewModel.deleteScene(scene.id) }, enabled = state.scenes.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("New Scene") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                TextButton(onClick = { viewModel.createScene(name); showCreateDialog = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
            title = { Text("New scene") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Scene name") }) }
        )
    }

    renameTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(onClick = { viewModel.renameScene(id, renameText); renameTarget = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
            title = { Text("Rename scene") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Scene name") }) }
        )
    }
}

