package com.frictionfree.diary.ui.screens.editor

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.DiaryApplication
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.data.repository.SettingsRepository
import com.frictionfree.diary.utils.LocationHelper
import com.frictionfree.diary.utils.ShareIntentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class EntryNavInfo(
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val previousId: String? = null,
    val nextId: String? = null,
    val currentIndex: Int = -1,
    val totalCount: Int = 0
)

data class EditorUiState(
    val entryId: String = "",
    val title: String = "",
    val content: String = "",
    val selectedNotebookId: String = "default_personal",
    val selectedColorHex: String = EntryColor.DEFAULT.hex,
    val mediaUris: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val isFetchingLocation: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isPreviewMode: Boolean = false,
    val isSaved: Boolean = false,
    val isArchived: Boolean = false
) {
    fun toDiaryEntry(): DiaryEntry {
        return DiaryEntry(
            id = entryId,
            title = title.trim(),
            content = content.trim(),
            notebookId = selectedNotebookId,
            colorHex = selectedColorHex,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            mediaUris = mediaUris,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isArchived = isArchived
        )
    }
}

class EditorViewModel(
    application: Application,
    private val diaryRepository: DiaryRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState(entryId = UUID.randomUUID().toString()))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val notebooks: StateFlow<List<Notebook>> = diaryRepository.getAllNotebooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isArchivedMode = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allEntries: StateFlow<List<DiaryEntry>> = _isArchivedMode
        .flatMapLatest { diaryRepository.getAllEntries(isArchived = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val entryNavInfo: StateFlow<EntryNavInfo> = combine(allEntries, _uiState) { list, state ->
        val idx = list.indexOfFirst { it.id == state.entryId }
        if (idx >= 0) {
            EntryNavInfo(
                hasPrevious = idx > 0,
                hasNext = idx < list.size - 1,
                previousId = if (idx > 0) list[idx - 1].id else null,
                nextId = if (idx < list.size - 1) list[idx + 1].id else null,
                currentIndex = idx,
                totalCount = list.size
            )
        } else {
            EntryNavInfo(totalCount = list.size)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, EntryNavInfo())

    private var lastSavedSnapshot: EditorUiState? = null

    fun selectEntry(entry: DiaryEntry) {
        if (entry.isArchived != _isArchivedMode.value) {
            _isArchivedMode.value = entry.isArchived
        }
        val newState = EditorUiState(
            entryId = entry.id,
            title = entry.title,
            content = entry.content,
            selectedNotebookId = entry.notebookId,
            selectedColorHex = entry.colorHex,
            mediaUris = entry.mediaUris,
            isPinned = entry.isPinned,
            isFavorite = entry.isFavorite,
            latitude = entry.latitude,
            longitude = entry.longitude,
            locationName = entry.locationName,
            createdAt = entry.createdAt,
            isPreviewMode = entry.content.isNotBlank(),
            isArchived = entry.isArchived
        )
        _uiState.value = newState
        lastSavedSnapshot = newState
    }

    fun goToNextEntry(): Boolean {
        saveEntry()
        val nextId = entryNavInfo.value.nextId
        if (nextId != null) {
            loadEntry(nextId)
            return true
        }
        return false
    }

    fun goToPreviousEntry(): Boolean {
        saveEntry()
        val prevId = entryNavInfo.value.previousId
        if (prevId != null) {
            loadEntry(prevId)
            return true
        }
        return false
    }

    fun loadEntry(id: String) {
        val cached = allEntries.value.firstOrNull { it.id == id }
        if (cached != null) {
            selectEntry(cached)
            return
        }
        viewModelScope.launch {
            val existing = diaryRepository.getEntryByIdDirect(id)
            if (existing != null) {
                if (existing.isArchived != _isArchivedMode.value) {
                    _isArchivedMode.value = existing.isArchived
                }
                selectEntry(existing)
            }
        }
    }

    fun isGeotaggingEnabled(): Boolean = settingsRepository.geotaggingEnabled.value

    fun initNewEntry(initialTitle: String = "", initialContent: String = "", initialMedia: List<String> = emptyList()) {
        val newState = EditorUiState(
            entryId = UUID.randomUUID().toString(),
            title = initialTitle,
            content = initialContent,
            mediaUris = initialMedia,
            createdAt = System.currentTimeMillis(),
            isPreviewMode = false
        )
        _uiState.value = newState
        lastSavedSnapshot = newState
        if (isGeotaggingEnabled()) {
            fetchLocation()
        }
    }

    fun fetchLocation(force: Boolean = false) {
        if (!force && !isGeotaggingEnabled()) return
        val context = getApplication<Application>()
        if (!LocationHelper.hasLocationPermission(context)) return

        _uiState.value = _uiState.value.copy(isFetchingLocation = true)
        viewModelScope.launch {
            try {
                val location = LocationHelper.getCurrentLocation(context)
                if (location != null) {
                    val placeName = LocationHelper.getPlaceName(context, location.latitude, location.longitude)
                    _uiState.value = _uiState.value.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        locationName = placeName,
                        isFetchingLocation = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isFetchingLocation = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isFetchingLocation = false)
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
    }

    fun updateContent(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
    }

    fun setNotebook(notebookId: String) {
        _uiState.value = _uiState.value.copy(selectedNotebookId = notebookId)
        saveEntry()
    }

    fun setColor(color: EntryColor) {
        _uiState.value = _uiState.value.copy(selectedColorHex = color.hex)
        saveEntry()
    }

    fun togglePinned() {
        _uiState.value = _uiState.value.copy(isPinned = !_uiState.value.isPinned)
        saveEntry()
    }

    fun togglePreviewMode() {
        val willBePreview = !_uiState.value.isPreviewMode
        if (willBePreview) {
            saveEntry()
        }
        _uiState.value = _uiState.value.copy(isPreviewMode = willBePreview)
    }

    fun addPhoto(uri: Uri) {
        val path = ShareIntentHelper.copyMediaToInternal(getApplication(), uri)
        if (path != null) {
            val current = _uiState.value.mediaUris.toMutableList()
            current.add(path)
            _uiState.value = _uiState.value.copy(mediaUris = current)
            saveEntry()
        }
    }

    fun removePhoto(path: String) {
        val current = _uiState.value.mediaUris.toMutableList()
        current.remove(path)
        _uiState.value = _uiState.value.copy(mediaUris = current)
        saveEntry()
    }

    fun setLocation(lat: Double, lon: Double, name: String? = null) {
        _uiState.value = _uiState.value.copy(
            latitude = lat,
            longitude = lon,
            locationName = name ?: "%.4f, %.4f".format(lat, lon)
        )
        saveEntry()
    }

    fun clearLocation() {
        _uiState.value = _uiState.value.copy(latitude = null, longitude = null, locationName = null)
        saveEntry()
    }

    fun saveEntry(onComplete: (() -> Unit)? = null) {
        val state = _uiState.value
        // Only save if title or content or media is not blank
        if (state.title.isBlank() && state.content.isBlank() && state.mediaUris.isEmpty()) {
            onComplete?.invoke()
            return
        }

        // Avoid writing to disk if unchanged from last saved snapshot
        val snapshot = lastSavedSnapshot
        if (snapshot != null &&
            state.entryId == snapshot.entryId &&
            state.title == snapshot.title &&
            state.content == snapshot.content &&
            state.selectedNotebookId == snapshot.selectedNotebookId &&
            state.selectedColorHex == snapshot.selectedColorHex &&
            state.mediaUris == snapshot.mediaUris &&
            state.isPinned == snapshot.isPinned &&
            state.isFavorite == snapshot.isFavorite &&
            state.isArchived == snapshot.isArchived &&
            state.locationName == snapshot.locationName &&
            state.latitude == snapshot.latitude &&
            state.longitude == snapshot.longitude
        ) {
            onComplete?.invoke()
            return
        }

        lastSavedSnapshot = state

        val appScope = (getApplication<Application>() as? DiaryApplication)?.applicationScope
        val scope = appScope ?: viewModelScope

        scope.launch {
            val entry = state.toDiaryEntry()
            diaryRepository.saveEntry(entry)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isSaved = true)
                onComplete?.invoke()
            }
        }
    }

    fun toggleArchived() {
        _uiState.value = _uiState.value.copy(isArchived = !_uiState.value.isArchived)
        saveEntry()
    }

    fun deleteEntry(onComplete: () -> Unit) {
        val appScope = (getApplication<Application>() as? DiaryApplication)?.applicationScope
        val scope = appScope ?: viewModelScope
        scope.launch {
            diaryRepository.deleteEntry(_uiState.value.entryId)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}
