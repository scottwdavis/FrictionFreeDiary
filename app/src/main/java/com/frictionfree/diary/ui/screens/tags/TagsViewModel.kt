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
    val selectedTag: String? = null,
    val matchingEntries: List<DiaryEntry> = emptyList()
)

class TagsViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _selectedTag = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TagsUiState> = _selectedTag.flatMapLatest { selected ->
        val entriesFlow = if (selected == null) {
            flowOf(emptyList())
        } else {
            diaryRepository.getEntriesByTag(selected)
        }

        combine(diaryRepository.getAllTags(), entriesFlow) { tags, entries ->
            TagsUiState(
                tags = tags,
                selectedTag = selected,
                matchingEntries = entries
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, TagsUiState())

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }
}
