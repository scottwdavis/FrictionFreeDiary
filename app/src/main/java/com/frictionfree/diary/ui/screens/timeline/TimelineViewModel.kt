package com.frictionfree.diary.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

    val uiState: StateFlow<TimelineUiState> = combine<Any?, Tuple7<String, String?, String?, TimelineViewMode, Int, Boolean, List<Notebook>>>(
        _searchQuery,
        _selectedNotebookId,
        _selectedTag,
        _viewMode,
        _weekOffset,
        _isArchiveMode,
        notebooks
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        Tuple7(
            args[0] as String,
            args[1] as String?,
            args[2] as String?,
            args[3] as TimelineViewMode,
            args[4] as Int,
            args[5] as Boolean,
            args[6] as List<Notebook>
        )
    }.flatMapLatest { (query, notebookId, tag, mode, weekOffset, isArchive, nbs) ->
        val entriesFlow = when {
            query.isNotBlank() -> diaryRepository.searchEntries(query, isArchive)
            tag != null -> diaryRepository.getEntriesByTag(tag, isArchive)
            notebookId != null -> diaryRepository.getEntriesByNotebook(notebookId, isArchive)
            mode == TimelineViewMode.WEEK -> {
                val (start, end) = getWeekRange(weekOffset)
                diaryRepository.getEntriesInRange(start, end, isArchive)
            }
            else -> diaryRepository.getAllEntries(isArchive)
        }

        combine(entriesFlow, _selectedEntryIds) { entries, selectedIds ->
            val validSelected = selectedIds.filter { id -> entries.any { it.id == id } }.toSet()
            TimelineUiState(
                entries = entries,
                notebooks = nbs,
                selectedNotebookId = notebookId,
                selectedTag = tag,
                searchQuery = query,
                viewMode = mode,
                selectedWeekOffset = weekOffset,
                isArchiveMode = isArchive,
                selectedEntryIds = validSelected
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, TimelineUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectNotebook(notebookId: String?) {
        _selectedNotebookId.value = notebookId
        _selectedTag.value = null
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
        val ids = _selectedEntryIds.value.toList()
        if (ids.isEmpty()) return
        val targetArchive = !_isArchiveMode.value
        viewModelScope.launch {
            diaryRepository.archiveEntries(ids, targetArchive)
            clearSelection()
        }
    }

    fun moveSelectedEntriesToNotebook(notebookId: String) {
        val ids = _selectedEntryIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            diaryRepository.moveEntriesToNotebook(ids, notebookId)
            clearSelection()
        }
    }

    fun deleteSelectedEntries() {
        val ids = _selectedEntryIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            diaryRepository.deleteEntries(ids)
            clearSelection()
        }
    }

    private data class Tuple7<A, B, C, D, E, F, G>(
        val v1: A, val v2: B, val v3: C, val v4: D, val v5: E, val v6: F, val v7: G
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
