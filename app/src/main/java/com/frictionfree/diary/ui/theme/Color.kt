package com.frictionfree.diary.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.frictionfree.diary.data.repository.AppTheme

// OLED Black Theme
val OledColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003314),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFA5D6A7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF333333)
)

// Obsidian Charcoal Theme
val ObsidianColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFBBDEFB),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF444444)
)

// Paper White Theme
val PaperWhiteColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFE0E0E0)
)

// Warm Sepia Theme (Cozy Book-Reading Tone)
val WarmSepiaColorScheme = lightColorScheme(
    primary = Color(0xFF8D6E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7CCC8),
    onPrimaryContainer = Color(0xFF3E2723),
    background = Color(0xFFFBF0D9),
    onBackground = Color(0xFF2C221E),
    surface = Color(0xFFF5E6C8),
    onSurface = Color(0xFF2C221E),
    surfaceVariant = Color(0xFFEAD8B5),
    onSurfaceVariant = Color(0xFF5D4037),
    outline = Color(0xFFD7CCC8)
)

// Forest Pine Theme
val ForestPineColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E4F3E),
    onPrimaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFF121D16),
    onBackground = Color(0xFFE8F5E9),
    surface = Color(0xFF1B2A20),
    onSurface = Color(0xFFE8F5E9),
    surfaceVariant = Color(0xFF26382C),
    onSurfaceVariant = Color(0xFFA5D6A7),
    outline = Color(0xFF3B5242)
)

// Nordic Frost Theme
val NordicFrostColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF3B4252),
    onPrimaryContainer = Color(0xFFD8DEE9),
    background = Color(0xFF242933),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF2E3440),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF3B4252),
    onSurfaceVariant = Color(0xFFD8DEE9),
    outline = Color(0xFF4C566A)
)

// Rose Quartz Theme
val RoseQuartzColorScheme = lightColorScheme(
    primary = Color(0xFFAD1457),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8BBD0),
    onPrimaryContainer = Color(0xFF880E4F),
    background = Color(0xFFFFF0F5),
    onBackground = Color(0xFF3E2723),
    surface = Color(0xFFFFE4EC),
    onSurface = Color(0xFF3E2723),
    surfaceVariant = Color(0xFFF8D2DD),
    onSurfaceVariant = Color(0xFF6A1B9A),
    outline = Color(0xFFF48FB1)
)

fun getThemeColorScheme(theme: AppTheme): ColorScheme {
    return when (theme) {
        AppTheme.OLED_BLACK -> OledColorScheme
        AppTheme.OBSIDIAN -> ObsidianColorScheme
        AppTheme.PAPER_WHITE -> PaperWhiteColorScheme
        AppTheme.WARM_SEPIA -> WarmSepiaColorScheme
        AppTheme.FOREST_PINE -> ForestPineColorScheme
        AppTheme.NORDIC_FROST -> NordicFrostColorScheme
        AppTheme.ROSE_QUARTZ -> RoseQuartzColorScheme
        AppTheme.SYSTEM_DYNAMIC -> PaperWhiteColorScheme // Handled dynamically in Theme.kt
    }
}
