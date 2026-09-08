package com.frictionfree.diary.ui.screens.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.Tag
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val matchingEntries: List<DiaryEntry> = emptyList()
) {
    val displayedTags: List<Tag>
        get() = if (searchQuery.isBlank()) {
            tags
        } else {
            val q = searchQuery.trim().removePrefix("#")
            tags.filter { it.name.contains(q, ignoreCase = true) }
        }
}

class TagsViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<TagsUiState> = combine(_selectedTag, _searchQuery) { selected, query ->
        Pair(selected, query)
    }.flatMapLatest { (selected, query) ->
        val entriesFlow = if (selected == null) {
            flowOf(emptyList())
        } else {
            diaryRepository.getEntriesByTag(selected)
        }

        combine(diaryRepository.getAllTags(), entriesFlow) { tags, entries ->
            val sorted = tags.sortedWith(
                compareByDescending<Tag> { it.usageCount }.thenBy { it.name }
            )
            TagsUiState(
                tags = sorted,
                searchQuery = query,
                selectedTag = selected,
                matchingEntries = entries
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, TagsUiState())

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
