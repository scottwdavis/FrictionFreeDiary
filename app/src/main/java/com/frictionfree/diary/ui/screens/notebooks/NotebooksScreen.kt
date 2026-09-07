package com.frictionfree.diary.ui.screens.notebooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.ui.components.EntryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksScreen(
    viewModel: NotebooksViewModel,
    onNavigateToEditor: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var notebookToEdit by remember { mutableStateOf<Notebook?>(null) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectedNotebook != null) uiState.selectedNotebook!!.name else "Notebooks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (uiState.selectedNotebook != null) {
                        IconButton(onClick = { viewModel.selectNotebook(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Notebooks")
                        }
                    }
                },
                actions = {
                    if (uiState.selectedNotebook != null) {
                        IconButton(onClick = { notebookToEdit = uiState.selectedNotebook }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Notebook")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedNotebook == null) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Notebook")
                }
            } else {
                FloatingActionButton(
                    onClick = { onNavigateToEditor(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Entry in Notebook")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.selectedNotebook == null) {
                // List of all notebooks
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.notebooks, key = { it.id }) { notebook ->
                        val count = uiState.entryCounts[notebook.id] ?: 0
                        NotebookItemCard(
                            notebook = notebook,
                            entryCount = count,
                            onClick = { viewModel.selectNotebook(notebook) },
                            onEdit = { notebookToEdit = notebook },
                            onDelete = { notebookToDelete = notebook }
                        )
                    }
                }
            } else {
                // Entries within the selected notebook
                if (uiState.notebookEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "This notebook is empty.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap + to add your first thought here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.notebookEntries, key = { it.id }) { entry ->
                            EntryCard(
                                entry = entry,
                                notebookName = uiState.selectedNotebook?.name,
                                onClick = { onNavigateToEditor(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isProcessing) {
        AlertDialog(
            onDismissRequest = { /* prevent dismiss while processing */ },
            confirmButton = {},
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Processing notebook...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        )
    }

    if (showCreateDialog) {
        NotebookDialog(
            title = "New Notebook",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, color ->
                viewModel.createNotebook(name, desc, color)
                showCreateDialog = false
            }
        )
    }

    notebookToEdit?.let { notebook ->
        NotebookDialog(
            title = "Edit Notebook",
            confirmLabel = "Save",
            initialName = notebook.name,
            initialDescription = notebook.description,
            initialColor = notebook.colorHex,
            onDismiss = { notebookToEdit = null },
            onConfirm = { name, desc, color ->
                viewModel.updateNotebook(notebook, name, desc, color)
                notebookToEdit = null
            }
        )
    }

    notebookToDelete?.let { notebook ->
        val count = uiState.entryCounts[notebook.id] ?: 0
        if (count > 0) {
            DeleteNotebookWithOptionsDialog(
                notebook = notebook,
                entryCount = count,
                candidateNotebooks = uiState.notebooks.filter { it.id != notebook.id },
                onDismiss = { notebookToDelete = null },
                onConfirm = { deleteEntries, targetNotebookId ->
                    viewModel.deleteNotebook(
                        notebookId = notebook.id,
                        deleteEntries = deleteEntries,
                        targetNotebookId = targetNotebookId
                    )
                    notebookToDelete = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { notebookToDelete = null },
                title = { Text("Delete Notebook") },
                text = { Text("Are you sure you want to delete “${notebook.name}”? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteNotebook(notebook.id)
                            notebookToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { notebookToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun NotebookItemCard(
    notebook: Notebook,
    entryCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(notebook.colorHex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = notebook.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (notebook.description.isNotBlank()) {
                        Text(
                            text = notebook.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$entryCount ${if (entryCount == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit notebook",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                if (!notebook.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete notebook",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

private enum class DeleteNotebookOption {
    MOVE,
    DELETE_ALL
}

@Composable
private fun DeleteNotebookWithOptionsDialog(
    notebook: Notebook,
    entryCount: Int,
    candidateNotebooks: List<Notebook>,
    onDismiss: () -> Unit,
    onConfirm: (deleteEntries: Boolean, targetNotebookId: String?) -> Unit
) {
    val defaultTarget = candidateNotebooks.firstOrNull { it.isDefault } ?: candidateNotebooks.firstOrNull()
    var selectedOption by remember { mutableStateOf(DeleteNotebookOption.MOVE) }
    var selectedTargetId by remember { mutableStateOf(defaultTarget?.id ?: "default_personal") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “${notebook.name}”?") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This notebook contains $entryCount ${if (entryCount == 1) "entry" else "entries"}. What would you like to do with them?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Move entries to another notebook
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedOption == DeleteNotebookOption.MOVE),
                            onClick = { selectedOption = DeleteNotebookOption.MOVE }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedOption == DeleteNotebookOption.MOVE),
                        onClick = { selectedOption = DeleteNotebookOption.MOVE }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Move entries to another notebook",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Dropdown for target notebook
                if (selectedOption == DeleteNotebookOption.MOVE && candidateNotebooks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
                    ) {
                        val targetNb = candidateNotebooks.find { it.id == selectedTargetId } ?: candidateNotebooks.first()
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = targetNb.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select target notebook"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            candidateNotebooks.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.name) },
                                    onClick = {
                                        selectedTargetId = target.id
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Delete entries permanently
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedOption == DeleteNotebookOption.DELETE_ALL),
                            onClick = { selectedOption = DeleteNotebookOption.DELETE_ALL }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedOption == DeleteNotebookOption.DELETE_ALL),
                        onClick = { selectedOption = DeleteNotebookOption.DELETE_ALL }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Delete all entries permanently",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "All $entryCount ${if (entryCount == 1) "entry" else "entries"} will be deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedOption == DeleteNotebookOption.DELETE_ALL,
                        if (selectedOption == DeleteNotebookOption.MOVE) selectedTargetId else null
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedOption == DeleteNotebookOption.DELETE_ALL) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (selectedOption == DeleteNotebookOption.DELETE_ALL) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            ) {
                Text(if (selectedOption == DeleteNotebookOption.DELETE_ALL) "Delete Everything" else "Move & Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NotebookDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialDescription: String = "",
    initialColor: String = "#2E7D32",
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    val colors = listOf(
        "#2E7D32", "#1565C0", "#6A1B9A", "#E65100", "#C2185B",
        "#00695C", "#4527A0", "#37474F", "#B71C1C", "#827717"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Notebook Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelMedium
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.chunked(5).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowColors.forEach { hex ->
                                val isSelected = hex.equals(selectedColor, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
