package com.frictionfree.diary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.frictionfree.diary.data.model.Tag

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val name: String,
    val usageCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Tag {
        return Tag(
            name = name,
            usageCount = usageCount,
            lastUsedAt = lastUsedAt
        )
    }

    companion object {
        fun fromDomain(tag: Tag): TagEntity {
            return TagEntity(
                name = tag.name,
                usageCount = tag.usageCount,
                lastUsedAt = tag.lastUsedAt
            )
        }
    }
}
