package com.frictionfree.diary.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val app: String = "FrictionFreeDiary",
    val exportedAt: Long = System.currentTimeMillis(),
    val entries: List<DiaryEntry> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val tags: List<Tag> = emptyList()
)
