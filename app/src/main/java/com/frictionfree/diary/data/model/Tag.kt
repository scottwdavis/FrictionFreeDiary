package com.frictionfree.diary.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val name: String,
    val usageCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis()
)
