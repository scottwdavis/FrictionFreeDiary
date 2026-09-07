package com.frictionfree.diary.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class NotebookIconItem(
    val key: String,
    val label: String,
    val icon: ImageVector
)

object NotebookIcons {
    val items = listOf(
        NotebookIconItem("book", "Book", Icons.Default.Book),
        NotebookIconItem("lightbulb", "Idea", Icons.Default.Lightbulb),
        NotebookIconItem("spa", "Reflections", Icons.Default.Spa),
        NotebookIconItem("favorite", "Heart", Icons.Default.Favorite),
        NotebookIconItem("star", "Star", Icons.Default.Star),
        NotebookIconItem("work", "Work", Icons.Default.BusinessCenter),
        NotebookIconItem("school", "School", Icons.Default.School),
        NotebookIconItem("flight", "Travel", Icons.Default.Flight),
        NotebookIconItem("fitness_center", "Fitness", Icons.Default.FitnessCenter),
        NotebookIconItem("palette", "Art", Icons.Default.Palette),
        NotebookIconItem("home", "Home", Icons.Default.Home),
        NotebookIconItem("code", "Code", Icons.Default.Code),
        NotebookIconItem("music_note", "Music", Icons.Default.MusicNote),
        NotebookIconItem("pets", "Pets", Icons.Default.Pets),
        NotebookIconItem("shopping_cart", "Shopping", Icons.Default.ShoppingCart)
    )

    fun getIcon(key: String?): ImageVector {
        if (key.isNullOrBlank()) return Icons.Default.Book
        return items.firstOrNull { it.key.equals(key, ignoreCase = true) }?.icon ?: Icons.Default.Book
    }
}
