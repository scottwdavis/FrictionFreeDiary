package com.frictionfree.diary.ui.screens.editor

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.ui.components.ColorPickerRow
import com.frictionfree.diary.ui.components.MarkdownEditorToolbar
import com.frictionfree.diary.ui.components.MarkdownInlineText
import com.frictionfree.diary.ui.components.MarkdownRenderer
import com.frictionfree.diary.utils.DateFormatters
import com.frictionfree.diary.utils.LocationHelper
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()

    var showNotebookMenu by remember { mutableStateOf(false) }

    // Content text field state with cursor position tracking for Markdown toolbar
    var contentFieldValue by remember(uiState.content) {
        mutableStateOf(
            TextFieldValue(
                text = uiState.content,
                selection = TextRange(uiState.content.length)
            )
        )
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addPhoto(uri)
        }
    }

    val context = LocalContext.current

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.fetchLocation(force = true)
        }
    }

    // Auto-request location permissions and fetch location if geotagging is enabled on an unlocated entry
    LaunchedEffect(uiState.entryId) {
        if (viewModel.isGeotaggingEnabled() && uiState.locationName == null && !uiState.isFetchingLocation) {
            if (LocationHelper.hasLocationPermission(context)) {
                viewModel.fetchLocation()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    val contentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically request focus and show software keyboard when entering edit mode
    LaunchedEffect(uiState.isPreviewMode) {
        if (!uiState.isPreviewMode) {
            delay(150)
            contentFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val selectedNotebook = notebooks.firstOrNull { it.id == uiState.selectedNotebookId }

    BackHandler {
        viewModel.saveEntry { onNavigateBack() }
    }

    // Automatically save entry whenever navigating away (e.g. tapping bottom nav bar items)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveEntry()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Notebook selector pill
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showNotebookMenu = true },
                            label = {
                                Text(
                                    selectedNotebook?.name ?: "Personal",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = showNotebookMenu,
                            onDismissRequest = { showNotebookMenu = false }
                        ) {
                            notebooks.forEach { notebook ->
                                DropdownMenuItem(
                                    text = { Text(notebook.name) },
                                    onClick = {
                                        viewModel.setNotebook(notebook.id)
                                        showNotebookMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveEntry { onNavigateBack() }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Pin toggle
                    IconButton(onClick = { viewModel.togglePinned() }) {
                        Icon(
                            imageVector = if (uiState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin entry",
                            tint = if (uiState.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Markdown Preview toggle
                    IconButton(onClick = { viewModel.togglePreviewMode() }) {
                        Icon(
                            imageVector = if (uiState.isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (uiState.isPreviewMode) "Edit Mode" else "Preview Markdown"
                        )
                    }

                    // Delete entry (if existing)
                    if (uiState.title.isNotBlank() || uiState.content.isNotBlank()) {
                        IconButton(onClick = { viewModel.deleteEntry { onNavigateBack() } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (!uiState.isPreviewMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Formatting tools
                        MarkdownEditorToolbar(
                            onInsertText = { insertText, cursorOffset ->
                                val currentText = contentFieldValue.text
                                val start = contentFieldValue.selection.min
                                val end = contentFieldValue.selection.max
                                val newText = currentText.replaceRange(start, end, insertText)
                                val newCursorPos = start + cursorOffset
                                val updatedValue = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(newCursorPos)
                                )
                                contentFieldValue = updatedValue
                                viewModel.updateContent(newText)
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Bottom action row: Color picker, photo attach, location
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Color code picker
                            ColorPickerRow(
                                selectedColorHex = uiState.selectedColorHex,
                                onColorSelected = { viewModel.setColor(it) },
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // Location toggle / fetch
                            IconButton(onClick = {
                                if (uiState.locationName != null) {
                                    viewModel.clearLocation()
                                } else {
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        viewModel.fetchLocation(force = true)
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (uiState.locationName != null) Icons.Default.LocationOn else Icons.Outlined.LocationOn,
                                    contentDescription = if (uiState.locationName != null) "Remove location" else "Add location",
                                    tint = if (uiState.locationName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Add Photo
                            IconButton(onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add photo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 3.dp,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!uiState.isPreviewMode) {
                            contentFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    // Timestamp and geotag indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = DateFormatters.formatFullDate(uiState.createdAt) + " • " + DateFormatters.formatTime(uiState.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.isFetchingLocation) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Locating...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (uiState.locationName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(start = 8.dp, end = if (!uiState.isPreviewMode) 4.dp else 8.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uiState.locationName ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!uiState.isPreviewMode) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove location",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.clearLocation() }
                                    )
                                }
                            }
                        }
                    }

                    // Attached Photos gallery
                    if (uiState.mediaUris.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.mediaUris.forEach { path ->
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = "Attached photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Remove image button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.removePhoto(path) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (uiState.isPreviewMode) {
                        if (uiState.title.isNotBlank()) {
                            MarkdownInlineText(
                                text = uiState.title,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        MarkdownRenderer(
                            markdownText = uiState.content.ifBlank { "*Nothing written yet. Tap the edit icon to write!*" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Title input
                        BasicTextField(
                            value = uiState.title,
                            onValueChange = { viewModel.updateTitle(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (uiState.title.isEmpty()) {
                                    Text(
                                        text = "Title (optional)",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Content input
                        BasicTextField(
                            value = contentFieldValue,
                            onValueChange = {
                                contentFieldValue = it
                                viewModel.updateContent(it.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 240.dp)
                                .focusRequester(contentFocusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (contentFieldValue.text.isEmpty()) {
                                    Text(
                                        text = "What's on your mind? Type thoughts, markdown, or #hashtags...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
