package com.frictionfree.diary.ui.screens.notebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class NotebooksUiState(
    val notebooks: List<Notebook> = emptyList(),
    val selectedNotebook: Notebook? = null,
    val notebookEntries: List<DiaryEntry> = emptyList(),
    val entryCounts: Map<String, Int> = emptyMap()
)

class NotebooksViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _selectedNotebook = MutableStateFlow<Notebook?>(null)

    val uiState: StateFlow<NotebooksUiState> = _selectedNotebook.flatMapLatest { selected ->
        val entriesFlow = if (selected == null) flowOf(emptyList()) else diaryRepository.getEntriesByNotebook(selected.id)
        combine(
            diaryRepository.getAllNotebooks(),
            entriesFlow,
            diaryRepository.getNotebookEntryCounts()
        ) { notebooks, entries, counts ->
            NotebooksUiState(
                notebooks = notebooks,
                selectedNotebook = selected,
                notebookEntries = entries,
                entryCounts = counts
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, NotebooksUiState())

    fun selectNotebook(notebook: Notebook?) {
        _selectedNotebook.value = notebook
    }

    fun createNotebook(name: String, description: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val notebook = Notebook(
                id = "nb_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                description = description.trim(),
                colorHex = colorHex,
                isDefault = false
            )
            diaryRepository.saveNotebook(notebook)
        }
    }

    fun deleteNotebook(notebookId: String, deleteEntries: Boolean = false, targetNotebookId: String? = null) {
        viewModelScope.launch {
            diaryRepository.deleteNotebook(notebookId, deleteEntries, targetNotebookId)
            if (_selectedNotebook.value?.id == notebookId) {
                _selectedNotebook.value = null
            }
        }
    }
}
