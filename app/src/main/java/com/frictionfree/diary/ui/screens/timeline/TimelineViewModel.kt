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
    val selectedWeekOffset: Int = 0 // 0 = current week, -1 = last week, etc.
)

class TimelineViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedNotebookId = MutableStateFlow<String?>(null)
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _viewMode = MutableStateFlow(TimelineViewMode.LIST)
    private val _weekOffset = MutableStateFlow(0)

    val notebooks: StateFlow<List<Notebook>> = diaryRepository.getAllNotebooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val uiState: StateFlow<TimelineUiState> = combine<Any?, Tuple6<String, String?, String?, TimelineViewMode, Int, List<Notebook>>>(
        _searchQuery,
        _selectedNotebookId,
        _selectedTag,
        _viewMode,
        _weekOffset,
        notebooks
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        Tuple6(
            args[0] as String,
            args[1] as String?,
            args[2] as String?,
            args[3] as TimelineViewMode,
            args[4] as Int,
            args[5] as List<Notebook>
        )
    }.flatMapLatest { (query, notebookId, tag, mode, weekOffset, nbs) ->
        val entriesFlow = when {
            query.isNotBlank() -> diaryRepository.searchEntries(query)
            tag != null -> diaryRepository.getEntriesByTag(tag)
            notebookId != null -> diaryRepository.getEntriesByNotebook(notebookId)
            mode == TimelineViewMode.WEEK -> {
                val (start, end) = getWeekRange(weekOffset)
                diaryRepository.getEntriesInRange(start, end)
            }
            else -> diaryRepository.getAllEntries()
        }

        entriesFlow.map { entries ->
            TimelineUiState(
                entries = entries,
                notebooks = nbs,
                selectedNotebookId = notebookId,
                selectedTag = tag,
                searchQuery = query,
                viewMode = mode,
                selectedWeekOffset = weekOffset
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, TimelineUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectNotebook(notebookId: String?) {
        _selectedNotebookId.value = notebookId
        _selectedTag.value = null
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun setViewMode(mode: TimelineViewMode) {
        _viewMode.value = mode
    }

    fun changeWeek(delta: Int) {
        _weekOffset.value += delta
    }

    private data class Tuple6<A, B, C, D, E, F>(
        val v1: A, val v2: B, val v3: C, val v4: D, val v5: E, val v6: F
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
