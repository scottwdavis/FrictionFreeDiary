package com.frictionfree.diary.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimelineViewMode {
    LIST,
    WEEK
}

data class TimelineUiState(
    val entries: List<DiaryEntry> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val selectedNotebookId: String? = null,
    val selectedTag: String? = null,
    val searchQuery: String = "",
    val viewMode: TimelineViewMode = TimelineViewMode.LIST,
    val selectedWeekOffset: Int = 0, // 0 = current week, -1 = last week, etc.
    val isArchiveMode: Boolean = false,
    val selectedEntryIds: Set<String> = emptySet()
)

class TimelineViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedNotebookId = MutableStateFlow<String?>(null)
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _viewMode = MutableStateFlow(TimelineViewMode.LIST)
    private val _weekOffset = MutableStateFlow(0)
    private val _isArchiveMode = MutableStateFlow(false)
    private val _selectedEntryIds = MutableStateFlow<Set<String>>(emptySet())

    val notebooks: StateFlow<List<Notebook>> = diaryRepository.getAllNotebooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Active entries flow for current archive mode.
    // Maintained in memory so notebook switching, tag filtering, and searches are 100% instant without re-querying disk.
    private val allEntriesFlow: Flow<List<DiaryEntry>> = _isArchiveMode
        .flatMapLatest { isArchive -> diaryRepository.getAllEntries(isArchive) }
        .flowOn(Dispatchers.IO)

    private data class FilterParams(
        val query: String,
        val notebookId: String?,
        val tag: String?,
        val mode: TimelineViewMode,
        val weekOffset: Int,
        val isArchive: Boolean
    )

    private val filterParams: Flow<FilterParams> = combine(
        _searchQuery,
        _selectedNotebookId,
        _selectedTag,
        _viewMode,
        _weekOffset
    ) { query, notebookId, tag, mode, weekOffset ->
        Tuple5(query, notebookId, tag, mode, weekOffset)
    }.combine(_isArchiveMode) { t5, isArchive ->
        FilterParams(
            query = t5.v1,
            notebookId = t5.v2,
            tag = t5.v3,
            mode = t5.v4,
            weekOffset = t5.v5,
            isArchive = isArchive
        )
    }

    val uiState: StateFlow<TimelineUiState> = combine(
        allEntriesFlow,
        notebooks,
        filterParams,
        _selectedEntryIds
    ) { allEntries, nbs, params, selectedIds ->
        val (start, end) = if (params.mode == TimelineViewMode.WEEK) getWeekRange(params.weekOffset) else Pair(0L, 0L)
        val queryLower = params.query.trim().lowercase()
        val tagLower = params.tag?.lowercase()

        // Lightning-fast in-memory filtering: 0ms lag when switching notebooks
        val filtered = allEntries.filter { entry ->
            // 1. Notebook filter
            if (params.notebookId != null && entry.notebookId != params.notebookId) return@filter false
            // 2. Tag filter
            if (tagLower != null && !entry.tags.any { it.equals(tagLower, ignoreCase = true) }) return@filter false
            // 3. Week filter
            if (params.mode == TimelineViewMode.WEEK && (entry.createdAt < start || entry.createdAt > end)) return@filter false
            // 4. Search query
            if (queryLower.isNotEmpty()) {
                val matchTitle = entry.title.lowercase().contains(queryLower)
                val matchContent = entry.content.lowercase().contains(queryLower)
                val matchTag = entry.tags.any { it.lowercase().contains(queryLower) }
                if (!matchTitle && !matchContent && !matchTag) return@filter false
            }
            true
        }

        val validSelected = selectedIds.filter { id -> filtered.any { it.id == id } }.toSet()

        TimelineUiState(
            entries = filtered,
            notebooks = nbs,
            selectedNotebookId = params.notebookId,
            selectedTag = params.tag,
            searchQuery = params.query,
            viewMode = params.mode,
            selectedWeekOffset = params.weekOffset,
            isArchiveMode = params.isArchive,
            selectedEntryIds = validSelected
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, TimelineUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectNotebook(notebookId: String?) {
        _selectedNotebookId.value = notebookId
        if (_selectedTag.value != null) {
            _selectedTag.value = null
        }
        clearSelection()
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
        clearSelection()
    }

    fun setViewMode(mode: TimelineViewMode) {
        _viewMode.value = mode
        clearSelection()
    }

    fun changeWeek(delta: Int) {
        _weekOffset.value += delta
        clearSelection()
    }

    fun toggleArchiveMode() {
        _isArchiveMode.value = !_isArchiveMode.value
        clearSelection()
    }

    fun toggleEntrySelection(entryId: String) {
        val current = _selectedEntryIds.value.toMutableSet()
        if (current.contains(entryId)) {
            current.remove(entryId)
        } else {
            current.add(entryId)
        }
        _selectedEntryIds.value = current
    }

    fun selectAllEntries() {
        val allIds = uiState.value.entries.map { it.id }.toSet()
        _selectedEntryIds.value = allIds
    }

    fun clearSelection() {
        _selectedEntryIds.value = emptySet()
    }

    fun archiveSelectedEntries() {
        val idsToArchive = _selectedEntryIds.value.toList()
        if (idsToArchive.isEmpty()) return
        val targetArchiveState = !uiState.value.isArchiveMode
        viewModelScope.launch {
            diaryRepository.archiveEntries(idsToArchive, targetArchiveState)
            clearSelection()
        }
    }

    fun moveSelectedEntriesToNotebook(notebookId: String) {
        val idsToMove = _selectedEntryIds.value.toList()
        if (idsToMove.isEmpty()) return
        viewModelScope.launch {
            diaryRepository.moveEntriesToNotebook(idsToMove, notebookId)
            clearSelection()
        }
    }

    fun deleteSelectedEntries() {
        val idsToDelete = _selectedEntryIds.value.toList()
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            diaryRepository.deleteEntries(idsToDelete)
            clearSelection()
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val v1: A, val v2: B, val v3: C, val v4: D, val v5: E
    )

    private fun getWeekRange(offsetWeeks: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, offsetWeeks)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return Pair(start, end)
    }
}
