package com.frictionfree.diary.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Notebook(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "book",
    val colorHex: String = "#2E7D32",
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
)
