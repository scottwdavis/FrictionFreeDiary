package com.frictionfree.diary.ui.screens.editor

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.data.repository.SettingsRepository
import com.frictionfree.diary.utils.LocationHelper
import com.frictionfree.diary.utils.ShareIntentHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

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
    val isSaved: Boolean = false
)

class EditorViewModel(
    application: Application,
    private val diaryRepository: DiaryRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState(entryId = UUID.randomUUID().toString()))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val notebooks: StateFlow<List<Notebook>> = diaryRepository.getAllNotebooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadEntry(id: String) {
        viewModelScope.launch {
            val existing = diaryRepository.getEntryByIdDirect(id)
            if (existing != null) {
                _uiState.value = EditorUiState(
                    entryId = existing.id,
                    title = existing.title,
                    content = existing.content,
                    selectedNotebookId = existing.notebookId,
                    selectedColorHex = existing.colorHex,
                    mediaUris = existing.mediaUris,
                    isPinned = existing.isPinned,
                    isFavorite = existing.isFavorite,
                    latitude = existing.latitude,
                    longitude = existing.longitude,
                    locationName = existing.locationName,
                    createdAt = existing.createdAt,
                    isPreviewMode = existing.content.isNotBlank()
                )
            }
        }
    }

    fun isGeotaggingEnabled(): Boolean = settingsRepository.geotaggingEnabled.value

    fun initNewEntry(initialTitle: String = "", initialContent: String = "", initialMedia: List<String> = emptyList()) {
        _uiState.value = EditorUiState(
            entryId = UUID.randomUUID().toString(),
            title = initialTitle,
            content = initialContent,
            mediaUris = initialMedia,
            createdAt = System.currentTimeMillis()
        )
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
    }

    fun setColor(color: EntryColor) {
        _uiState.value = _uiState.value.copy(selectedColorHex = color.hex)
    }

    fun togglePinned() {
        _uiState.value = _uiState.value.copy(isPinned = !_uiState.value.isPinned)
    }

    fun togglePreviewMode() {
        _uiState.value = _uiState.value.copy(isPreviewMode = !_uiState.value.isPreviewMode)
    }

    fun addPhoto(uri: Uri) {
        val path = ShareIntentHelper.copyMediaToInternal(getApplication(), uri)
        if (path != null) {
            val current = _uiState.value.mediaUris.toMutableList()
            current.add(path)
            _uiState.value = _uiState.value.copy(mediaUris = current)
        }
    }

    fun removePhoto(path: String) {
        val current = _uiState.value.mediaUris.toMutableList()
        current.remove(path)
        _uiState.value = _uiState.value.copy(mediaUris = current)
    }

    fun setLocation(lat: Double, lon: Double, name: String? = null) {
        _uiState.value = _uiState.value.copy(
            latitude = lat,
            longitude = lon,
            locationName = name ?: "%.4f, %.4f".format(lat, lon)
        )
    }

    fun clearLocation() {
        _uiState.value = _uiState.value.copy(latitude = null, longitude = null, locationName = null)
    }

    fun saveEntry(onComplete: (() -> Unit)? = null) {
        val state = _uiState.value
        // Only save if title or content or media is not blank
        if (state.title.isBlank() && state.content.isBlank() && state.mediaUris.isEmpty()) {
            onComplete?.invoke()
            return
        }

        viewModelScope.launch {
            val entry = DiaryEntry(
                id = state.entryId,
                title = state.title.trim(),
                content = state.content.trim(),
                notebookId = state.selectedNotebookId,
                colorHex = state.selectedColorHex,
                createdAt = state.createdAt,
                updatedAt = System.currentTimeMillis(),
                latitude = state.latitude,
                longitude = state.longitude,
                locationName = state.locationName,
                mediaUris = state.mediaUris,
                isPinned = state.isPinned,
                isFavorite = state.isFavorite
            )
            diaryRepository.saveEntry(entry)
            _uiState.value = _uiState.value.copy(isSaved = true)
            onComplete?.invoke()
        }
    }

    fun deleteEntry(onComplete: () -> Unit) {
        viewModelScope.launch {
            diaryRepository.deleteEntry(_uiState.value.entryId)
            onComplete()
        }
    }
}
