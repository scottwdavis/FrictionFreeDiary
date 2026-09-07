package com.frictionfree.diary.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.frictionfree.diary.data.model.DiaryEntry

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [
        Index(value = ["notebookId"]),
        Index(value = ["createdAt"]),
        Index(value = ["colorHex"]),
        Index(value = ["isArchived"])
    ]
)
data class DiaryEntryEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val notebookId: String = "default_personal",
    val colorHex: String = "#00000000",
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val mediaUrisJson: String = "[]",
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val moodEmoji: String? = null,
    val isArchived: Boolean = false
) {
    fun toDomain(tags: List<String> = emptyList(), mediaList: List<String> = emptyList()): DiaryEntry {
        return DiaryEntry(
            id = id,
            title = title,
            content = content,
            notebookId = notebookId,
            colorHex = colorHex,
            createdAt = createdAt,
            updatedAt = updatedAt,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            mediaUris = mediaList,
            tags = tags,
            isPinned = isPinned,
            isFavorite = isFavorite,
            moodEmoji = moodEmoji,
            isArchived = isArchived
        )
    }

    companion object {
        fun fromDomain(entry: DiaryEntry, mediaJson: String): DiaryEntryEntity {
            return DiaryEntryEntity(
                id = entry.id,
                title = entry.title,
                content = entry.content,
                notebookId = entry.notebookId,
                colorHex = entry.colorHex,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                latitude = entry.latitude,
                longitude = entry.longitude,
                locationName = entry.locationName,
                mediaUrisJson = mediaJson,
                isPinned = entry.isPinned,
                isFavorite = entry.isFavorite,
                moodEmoji = entry.moodEmoji,
                isArchived = entry.isArchived
            )
        }
    }
}
