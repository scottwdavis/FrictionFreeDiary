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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Archive
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.ui.components.ColorPickerRow
import com.frictionfree.diary.ui.components.FullScreenPhotoViewer
import com.frictionfree.diary.ui.components.MarkdownEditorToolbar
import com.frictionfree.diary.ui.components.MarkdownFormatter
import com.frictionfree.diary.ui.components.MarkdownInlineText
import com.frictionfree.diary.ui.components.MarkdownRenderer
import com.frictionfree.diary.utils.DateFormatters
import com.frictionfree.diary.utils.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    initialEntryId: String? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val allEntries by viewModel.allEntries.collectAsState()

    var showNotebookMenu by remember { mutableStateOf(false) }
    var viewingPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var activeViewerPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewingExternalPhotoUrl by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Target entry ID to display initially
    val targetId = initialEntryId ?: uiState.entryId

    // Build the list of entries for horizontal paging
    val displayEntries = remember(allEntries, targetId) {
        if (allEntries.isEmpty()) {
            if (uiState.entryId.isNotBlank()) listOf(uiState.toDiaryEntry()) else emptyList()
        } else {
            val containsCurrent = allEntries.any { it.id == targetId }
            if (!containsCurrent && targetId.isNotBlank()) {
                listOf(uiState.toDiaryEntry()) + allEntries
            } else {
                allEntries
            }
        }
    }

    // Determine target initial page
    val targetIndex = remember(displayEntries, targetId) {
        val idx = displayEntries.indexOfFirst { it.id == targetId }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = targetIndex,
        pageCount = { displayEntries.size }
    )

    // Re-align to target page when full entries list arrives
    LaunchedEffect(displayEntries.size, targetId) {
        if (displayEntries.isNotEmpty()) {
            val target = displayEntries.indexOfFirst { it.id == targetId }
            if (target >= 0 && pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }
    }

    // Automatically hide keyboard and clear focus when scrolling starts
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Synchronize settled page with ViewModel state
    LaunchedEffect(pagerState.settledPage, displayEntries) {
        val settledIdx = pagerState.settledPage
        if (settledIdx in displayEntries.indices) {
            val settledEntry = displayEntries[settledIdx]
            if (settledEntry.id != uiState.entryId) {
                viewModel.saveEntry()
                viewModel.selectEntry(settledEntry)
            }
        }
    }

    val markdownImageRegex = remember { Regex("""!\[([^\]]*)\]\(((?:<[^>]+>)|(?:[^\s)]+))\)""") }

    // Content text field state with cursor position tracking for Markdown toolbar
    var contentFieldValue by remember(uiState.entryId) {
        mutableStateOf(
            TextFieldValue(
                text = uiState.content,
                selection = TextRange(uiState.content.length)
            )
        )
    }

    LaunchedEffect(uiState.content) {
        if (uiState.content != contentFieldValue.text) {
            contentFieldValue = contentFieldValue.copy(text = uiState.content)
        }
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

    // Automatically request focus and show software keyboard when entering edit mode
    LaunchedEffect(uiState.isPreviewMode) {
        if (!uiState.isPreviewMode) {
            delay(150)
            contentFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val currentVisibleEntry = displayEntries.getOrNull(pagerState.currentPage) ?: uiState.toDiaryEntry()
    val selectedNotebook = notebooks.firstOrNull { it.id == currentVisibleEntry.notebookId }

    BackHandler {
        viewModel.saveEntry()
        onNavigateBack()
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
                        viewModel.saveEntry()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Previous & Next entry buttons
                    if (displayEntries.size > 1) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (pagerState.currentPage > 0) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            enabled = pagerState.currentPage > 0
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous entry",
                                tint = if (pagerState.currentPage > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (pagerState.currentPage < displayEntries.size - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            enabled = pagerState.currentPage < displayEntries.size - 1
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next entry",
                                tint = if (pagerState.currentPage < displayEntries.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }
                    }

                    // Pin toggle
                    IconButton(onClick = { viewModel.togglePinned() }) {
                        Icon(
                            imageVector = if (uiState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin entry",
                            tint = if (uiState.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Archive toggle
                    IconButton(onClick = { viewModel.toggleArchived() }) {
                        Icon(
                            imageVector = if (uiState.isArchived) Icons.Filled.Archive else Icons.Outlined.Archive,
                            contentDescription = if (uiState.isArchived) "Unarchive entry" else "Archive entry",
                            tint = if (uiState.isArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // View / Edit toggle: Floppy disc icon when editing -> save & view; Edit pencil when in view mode -> edit
                    IconButton(onClick = { viewModel.togglePreviewMode() }) {
                        Icon(
                            imageVector = if (uiState.isPreviewMode) Icons.Default.Edit else Icons.Default.Save,
                            contentDescription = if (uiState.isPreviewMode) "Edit Mode" else "Save & View Mode"
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
                            onAction = { action ->
                                val updatedValue = MarkdownFormatter.applyAction(contentFieldValue, action)
                                contentFieldValue = updatedValue
                                viewModel.updateContent(updatedValue.text)
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val availableHeight = maxHeight
            val cardMinHeight = (availableHeight - 16.dp).coerceAtLeast(100.dp)

            if (displayEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    key = { index -> displayEntries.getOrNull(index)?.id ?: index },
                    userScrollEnabled = displayEntries.size > 1,
                    beyondViewportPageCount = if (displayEntries.size > 1) 1 else 0,
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val entry = displayEntries.getOrNull(page) ?: return@HorizontalPager
                    val isCurrentPage = page == pagerState.currentPage
                    val isThisEntryActive = entry.id == uiState.entryId

                    val pageEntry = if (isThisEntryActive) {
                        DiaryEntry(
                            id = uiState.entryId,
                            title = uiState.title,
                            content = uiState.content,
                            notebookId = uiState.selectedNotebookId,
                            colorHex = uiState.selectedColorHex,
                            createdAt = uiState.createdAt,
                            updatedAt = System.currentTimeMillis(),
                            latitude = uiState.latitude,
                            longitude = uiState.longitude,
                            locationName = uiState.locationName,
                            mediaUris = uiState.mediaUris,
                            isPinned = uiState.isPinned,
                            isFavorite = uiState.isFavorite,
                            isArchived = uiState.isArchived
                        )
                    } else {
                        entry
                    }

                    EntryPageContent(
                        entry = pageEntry,
                        isCurrentPage = isCurrentPage,
                        isEditing = isCurrentPage && isThisEntryActive && !uiState.isPreviewMode,
                        isFetchingLocation = isThisEntryActive && uiState.isFetchingLocation,
                        contentFieldValue = if (isThisEntryActive) contentFieldValue else null,
                        onContentFieldValueChange = {
                            contentFieldValue = it
                            viewModel.updateContent(it.text)
                        },
                        contentFocusRequester = contentFocusRequester,
                        onTitleChange = { viewModel.updateTitle(it) },
                        onRemovePhoto = { viewModel.removePhoto(it) },
                        onClearLocation = { viewModel.clearLocation() },
                        onPhotoClick = { allPhotos, photoIdx ->
                            activeViewerPhotos = allPhotos
                            viewingPhotoIndex = photoIdx
                        },
                        onExternalPhotoClick = { url ->
                            viewingExternalPhotoUrl = url
                        },
                        cardMinHeight = cardMinHeight,
                        markdownImageRegex = markdownImageRegex
                    )
                }
            }
        }
    }

    if (viewingPhotoIndex != null && activeViewerPhotos.isNotEmpty()) {
        FullScreenPhotoViewer(
            photos = activeViewerPhotos,
            initialIndex = viewingPhotoIndex ?: 0,
            onDismiss = {
                viewingPhotoIndex = null
                viewingExternalPhotoUrl = null
            }
        )
    } else if (viewingExternalPhotoUrl != null) {
        FullScreenPhotoViewer(
            photos = listOf(viewingExternalPhotoUrl!!),
            initialIndex = 0,
            onDismiss = { viewingExternalPhotoUrl = null }
        )
    }
}

@Composable
private fun EntryPageContent(
    entry: DiaryEntry,
    isCurrentPage: Boolean,
    isEditing: Boolean,
    isFetchingLocation: Boolean,
    contentFieldValue: TextFieldValue?,
    onContentFieldValueChange: (TextFieldValue) -> Unit,
    contentFocusRequester: FocusRequester,
    onTitleChange: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onClearLocation: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
    onExternalPhotoClick: (String) -> Unit,
    cardMinHeight: Dp,
    markdownImageRegex: Regex
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val allEntryPhotos = remember(entry.mediaUris, entry.content) {
        val markdownImages = markdownImageRegex.findAll(entry.content).map {
            it.groupValues[2].trim().removeSurrounding("<", ">")
        }.toList()
        (entry.mediaUris + markdownImages).distinct()
    }

    val nonContentHeight = if (entry.mediaUris.isNotEmpty()) 240.dp else 140.dp
    val contentMinHeight = (cardMinHeight - nonContentHeight).coerceAtLeast(180.dp)

    val entryColor = EntryColor.fromHex(entry.colorHex)
    val hasColor = entryColor != EntryColor.DEFAULT
    val accentColor = entryColor.toComposeColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 3.dp,
            tonalElevation = 1.dp,
            color = if (hasColor) {
                accentColor.copy(alpha = 0.06f).compositeOver(MaterialTheme.colorScheme.surface)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = if (hasColor) 1.5.dp else 1.dp,
                color = if (hasColor) {
                    accentColor.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = cardMinHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isEditing) {
                        contentFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = cardMinHeight)
                    .height(IntrinsicSize.Min)
            ) {
                if (hasColor) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .fillMaxHeight()
                            .background(
                                accentColor,
                                RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = cardMinHeight)
                        .padding(
                            start = if (hasColor) 14.dp else 18.dp,
                            end = 18.dp,
                            top = 16.dp,
                            bottom = 16.dp
                        )
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
                            text = DateFormatters.formatFullDate(entry.createdAt) + " • " + DateFormatters.formatTime(entry.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isFetchingLocation) {
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
                        } else if (entry.locationName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(start = 8.dp, end = if (isEditing) 4.dp else 8.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = entry.locationName ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isEditing) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove location",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .clickable { onClearLocation() }
                                    )
                                }
                            }
                        }
                    }

                    // Attached Photos gallery
                    if (entry.mediaUris.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            entry.mediaUris.forEachIndexed { index, path ->
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val targetIndex = allEntryPhotos.indexOf(path).takeIf { it >= 0 } ?: index
                                            onPhotoClick(allEntryPhotos, targetIndex)
                                        }
                                ) {
                                    AsyncImage(
                                        model = if (path.startsWith("http://") || path.startsWith("https://") ||
                                            path.startsWith("content://") || path.startsWith("file://")
                                        ) path else File(path),
                                        contentDescription = "Attached photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Remove image button (only in editing mode)
                                    if (isEditing) {
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
                                                onClick = { onRemovePhoto(path) },
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
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!isEditing) {
                        // PREVIEW / VIEW MODE (120fps read layout)
                        if (entry.title.isNotBlank()) {
                            MarkdownInlineText(
                                text = entry.title,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        MarkdownRenderer(
                            markdownText = entry.content.ifBlank { "*Nothing written yet. Tap the edit icon to write!*" },
                            modifier = Modifier.fillMaxWidth(),
                            onImageClick = { imageUrl ->
                                val cleanUrl = imageUrl.removePrefix("file://")
                                val idx = allEntryPhotos.indexOfFirst {
                                    it == imageUrl || it == cleanUrl ||
                                    try { File(it).absolutePath == File(cleanUrl).absolutePath } catch (_: Exception) { false }
                                }
                                if (idx >= 0) {
                                    onPhotoClick(allEntryPhotos, idx)
                                } else {
                                    onExternalPhotoClick(imageUrl)
                                }
                            }
                        )
                    } else {
                        // EDIT MODE
                        BasicTextField(
                            value = entry.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (entry.title.isEmpty()) {
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

                        val fieldValue = contentFieldValue ?: TextFieldValue(entry.content)
                        BasicTextField(
                            value = fieldValue,
                            onValueChange = onContentFieldValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = contentMinHeight)
                                .focusRequester(contentFocusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (fieldValue.text.isEmpty()) {
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
        }
    }
}
