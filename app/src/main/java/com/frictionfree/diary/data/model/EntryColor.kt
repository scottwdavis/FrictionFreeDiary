package com.frictionfree.diary.data.model

import androidx.compose.ui.graphics.Color

/**
 * Curated calming palette for journal entry color coding.
 */
enum class EntryColor(val hex: String, val displayName: String, val colorValue: Long) {
    DEFAULT("#00000000", "None", 0x00000000),
    EMERALD("#2E7D32", "Emerald Green", 0xFF2E7D32),
    OCEAN("#1565C0", "Ocean Blue", 0xFF1565C0),
    SUNSET("#E65100", "Sunset Orange", 0xFFE65100),
    LAVENDER("#6A1B9A", "Lavender Purple", 0xFF6A1B9A),
    CHERRY("#C2185B", "Cherry Rose", 0xFFC2185B),
    AMBER("#F57F17", "Amber Gold", 0xFFF57F17),
    TEAL("#00695C", "Teal Calm", 0xFF00695C),
    SLATE("#37474F", "Slate Grey", 0xFF37474F);

    fun toComposeColor(): Color {
        return if (this == DEFAULT) Color.Transparent else Color(colorValue)
    }

    companion object {
        fun fromHex(hex: String?): EntryColor {
            if (hex == null) return DEFAULT
            return entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) } ?: DEFAULT
        }
    }
}
