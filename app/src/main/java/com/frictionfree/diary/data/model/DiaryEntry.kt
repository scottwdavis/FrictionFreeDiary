package com.frictionfree.diary.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DiaryEntry(
    val id: String,
    val title: String = "",
    val content: String = "",
    val notebookId: String = "default_personal",
    val colorHex: String = EntryColor.DEFAULT.hex,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val mediaUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val moodEmoji: String? = null
)
