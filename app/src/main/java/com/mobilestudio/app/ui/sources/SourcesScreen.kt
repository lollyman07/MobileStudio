package com.mobilestudio.app.ui.sources

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.mobilestudio.app.model.SourceModel
import com.mobilestudio.app.model.SourceType
import com.mobilestudio.app.ui.studio.StudioViewModel
import com.mobilestudio.app.ui.theme.StudioSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(viewModel: StudioViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val scene = viewModel.currentScene()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAddMenu by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SourceModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources — ${scene?.name ?: ""}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "Add source") }
                        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                            SourceType.values().forEach { type ->
                                DropdownMenuItem(text = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }, onClick = {
                                    viewModel.addSource(type, lifecycleOwner)
                                    showAddMenu = false
                                })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val sources = scene?.sources?.sortedByDescending { it.zOrder } ?: emptyList()
        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No sources yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(modifier = Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    selected = source.id == state.selectedSourceId,
                    onSelect = { viewModel.selectSource(source.id) },
                    onToggleVisible = { viewModel.toggleVisibility(source.id) },
                    onToggleLock = { viewModel.toggleLock(source.id) },
                    onMoveUp = { viewModel.moveLayer(source.id, up = true) },
                    onMoveDown = { viewModel.moveLayer(source.id, up = false) },
                    onDelete = { viewModel.deleteSource(source.id) },
                    onEdit = { editingSource = source }
                )
            }
        }
    }

    editingSource?.let { source ->
        SourceEditSheet(source = source, viewModel = viewModel, onDismiss = { editingSource = null })
    }
}

@Composable
private fun SourceRow(
    source: SourceModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface, RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onToggleVisible) {
            Icon(if (source.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Toggle visibility")
        }
        IconButton(onClick = onToggleLock) {
            Icon(if (source.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, contentDescription = "Toggle lock")
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(source.name, style = MaterialTheme.typography.titleSmall)
            Text(source.type.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Tune, contentDescription = "Edit properties") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
    }
}
