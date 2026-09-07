package com.frictionfree.diary.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImportProgress(
    val isImporting: Boolean = false,
    val progress: Float? = null,
    val title: String = "",
    val detail: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

object ImportManager {
    private val _progressState = MutableStateFlow<ImportProgress?>(null)
    val progressState: StateFlow<ImportProgress?> = _progressState.asStateFlow()

    fun updateProgress(
        isImporting: Boolean,
        progress: Float? = null,
        title: String,
        detail: String = "",
        isComplete: Boolean = false,
        error: String? = null
    ) {
        _progressState.value = if (!isImporting && !isComplete && error == null) {
            null
        } else {
            ImportProgress(
                isImporting = isImporting,
                progress = progress,
                title = title,
                detail = detail,
                isComplete = isComplete,
                error = error
            )
        }
    }

    fun dismiss() {
        _progressState.value = null
    }
}
