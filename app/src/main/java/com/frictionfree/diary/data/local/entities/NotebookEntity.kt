package com.frictionfree.diary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.frictionfree.diary.data.model.Notebook

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "book",
    val colorHex: String = "#2E7D32",
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
) {
    fun toDomain(): Notebook {
        return Notebook(
            id = id,
            name = name,
            description = description,
            icon = icon,
            colorHex = colorHex,
            createdAt = createdAt,
            isDefault = isDefault
        )
    }

    companion object {
        fun fromDomain(notebook: Notebook): NotebookEntity {
            return NotebookEntity(
                id = notebook.id,
                name = notebook.name,
                description = notebook.description,
                icon = notebook.icon,
                colorHex = notebook.colorHex,
                createdAt = notebook.createdAt,
                isDefault = notebook.isDefault
            )
        }
    }
}
