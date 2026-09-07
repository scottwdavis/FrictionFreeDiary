package com.frictionfree.diary.ui.screens.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import com.frictionfree.diary.ui.components.NotebookIcons
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frictionfree.diary.ui.components.EntryCard
import com.frictionfree.diary.utils.DateFormatters
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToTags: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    val isSelectionMode = uiState.selectedEntryIds.isNotEmpty()
    val selectedCount = uiState.selectedEntryIds.size

    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Contextual Selection TopAppBar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection")
                        }
                    },
                    title = {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        val allSelected = selectedCount == uiState.entries.size && uiState.entries.isNotEmpty()
                        IconButton(onClick = {
                            if (allSelected) viewModel.clearSelection() else viewModel.selectAllEntries()
                        }) {
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all"
                            )
                        }

                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move to notebook")
                        }

                        IconButton(onClick = { viewModel.archiveSelectedEntries() }) {
                            Icon(
                                if (uiState.isArchiveMode) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = if (uiState.isArchiveMode) "Unarchive selected" else "Archive selected"
                            )
                        }

                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.isArchiveMode) "Archived Notes" else "FrictionFree Diary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        // Switch between List and Week views
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            SegmentedButton(
                                selected = uiState.viewMode == TimelineViewMode.LIST,
                                onClick = { viewModel.setViewMode(TimelineViewMode.LIST) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = { Icon(Icons.Default.ViewList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("List")
                            }
                            SegmentedButton(
                                selected = uiState.viewMode == TimelineViewMode.WEEK,
                                onClick = { viewModel.setViewMode(TimelineViewMode.WEEK) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = { Icon(Icons.Default.CalendarViewWeek, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("Week")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToEditor(null) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Write") },
                    text = { Text("Write") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search your entries or #tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Filter row: Active tag (if any), Notebooks, and Archive Mode chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active tag chip
                if (uiState.selectedTag != null) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.selectTag(null) },
                        label = { Text("#${uiState.selectedTag}") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove tag filter",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                // All Notes chip
                FilterChip(
                    selected = uiState.selectedNotebookId == null,
                    onClick = { viewModel.selectNotebook(null) },
                    label = { Text("All Notes") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ViewList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.padding(end = 6.dp)
                )

                // Notebook chips
                uiState.notebooks.forEach { notebook ->
                    FilterChip(
                        selected = uiState.selectedNotebookId == notebook.id,
                        onClick = {
                            if (uiState.selectedNotebookId == notebook.id) {
                                viewModel.selectNotebook(null)
                            } else {
                                viewModel.selectNotebook(notebook.id)
                            }
                        },
                        label = { Text(notebook.name) },
                        leadingIcon = {
                            Icon(
                                NotebookIcons.getIcon(notebook.icon),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                // Archive mode toggle chip
                FilterChip(
                    selected = uiState.isArchiveMode,
                    onClick = { viewModel.toggleArchiveMode() },
                    label = { Text(if (uiState.isArchiveMode) "Archived" else "Archive") },
                    leadingIcon = {
                        Icon(
                            if (uiState.isArchiveMode) Icons.Filled.Archive else Icons.Outlined.Archive,
                            contentDescription = "Archive mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Archive Mode Banner
            if (uiState.isArchiveMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val scopeName = uiState.notebooks.firstOrNull { it.id == uiState.selectedNotebookId }?.name ?: "All Notes"
                            Text(
                                text = "Viewing Archive for $scopeName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = { viewModel.toggleArchiveMode() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Exit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Week selector bar (when in Week mode)
            if (uiState.viewMode == TimelineViewMode.WEEK) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.changeWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
                    }

                    Text(
                        text = if (uiState.selectedWeekOffset == 0) "This Week"
                        else if (uiState.selectedWeekOffset == -1) "Last Week"
                        else "${uiState.selectedWeekOffset} weeks away",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(onClick = { viewModel.changeWeek(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                    }
                }
            }

            // Main entries list
            if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.isArchiveMode) "No archived thoughts in this view." else "No thoughts recorded yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.isArchiveMode) "Archived notes for this notebook will appear here." else "Tap 'Write' to quickly jot down your ideas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        val notebook = uiState.notebooks.firstOrNull { it.id == entry.notebookId }
                        val isSelected = uiState.selectedEntryIds.contains(entry.id)
                        EntryCard(
                            entry = entry,
                            notebookName = notebook?.name,
                            isSelected = isSelected,
                            isInSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleEntrySelection(entry.id)
                                } else {
                                    onNavigateToEditor(entry.id)
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleEntrySelection(entry.id)
                            },
                            onTagClick = { tag -> viewModel.selectTag(tag) }
                        )
                    }
                }
            }
        }
    }

    // Move to Notebook Dialog
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move $selectedCount entries to") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    uiState.notebooks.forEach { nb ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.moveSelectedEntriesToNotebook(nb.id)
                                    showMoveDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            try {
                                                Color(android.graphics.Color.parseColor(nb.colorHex))
                                            } catch (_: Exception) {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = nb.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $selectedCount entries?") },
            text = { Text("Are you sure you want to permanently delete these entries? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedEntries()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
